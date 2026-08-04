"""Fixed (not learned) per-feature normalization for the raw physical
state vector s_t = (L, U, D, mu_d, mu_e1..mu_eN, mu_c, b_wlan, b_man, b_wan).

Without this, features spanning wildly different physical scales (task
length ~1500-10000 MI vs. utilization 0-150% vs. bandwidth 0-200 Mbps) fed
directly into a plain MLP, whose first layer's weighted sum -- and whose
gradient magnitudes during backprop -- ended up dominated by whichever raw
feature happened to have the largest numeric scale. In practice this showed
up as policy collapse: the actor locked onto one or two actions almost
independent of the actual state, including never learning to route to the
device tier even in scenarios where it was clearly the better choice (see
ReSACO/README.md's Known limitations).

Every agent (SACAgent, DDPGAgent, A2CAgent/A3C) calls normalize_state()
right before constructing a network input tensor, whether the raw state
came from resaco/env.py during training or from the Java bridge
(bridge/inference_server.py) during serving -- the scale factors are fixed
constants derived from Table II's metadata ranges and the physical tier
parameters, not fit from data, so behavior is identical and stateless
between training and serving, and the wire protocol's raw physical values
are unaffected.
"""

import numpy as np

from . import config

# Divides each feature down to a roughly comparable O(1) range. Not tight
# bounds (e.g. task.length can jitter up to 1.3x its Table II max, and
# background-saturated mu_edge/mu_cloud can reach env._SATURATION_CEILING
# = 150) -- normalization only needs comparable *scale*, not a strict
# [0, 1] clamp.
_SCALE = np.array(
    [10000.0, 2500.0, 2500.0, 100.0]        # L, U, D, mu_d
    + [100.0] * config.NUM_EDGE_SERVERS      # mu_e1..mu_eN
    + [100.0, config.WLAN_BANDWIDTH_MBPS, config.MAN_BANDWIDTH_MBPS, config.WAN_BANDWIDTH_MBPS],
    # mu_c,   b_wlan,                       b_man,                   b_wan
    dtype=np.float32,
)
assert _SCALE.shape[0] == config.STATE_DIM


def normalize_state(state):
    """Divides a raw physical state (or a batch of them, shape (B, STATE_DIM))
    by the fixed per-feature scale above. Plain numpy broadcasting handles
    both a single (STATE_DIM,) vector and a (B, STATE_DIM) batch."""
    return np.asarray(state, dtype=np.float32) / _SCALE
