"""Hyperparameters and metadata ranges for ReSACO (Table II, Section V-A/V-B)."""

from contextlib import contextmanager

# ----------------------------------------------------------------------------
# Device / Edge / Cloud tier configuration (Section V-A-2, matches
# EdgeCloudSim's scripts/three_tier/config/default_config.properties and
# edge_devices.xml so the trained policy is consistent with the simulator).
# ----------------------------------------------------------------------------
#
# The *_VM_CORES values below are descriptive only: they record the paper's
# device configuration (and mirror edge_devices.xml / default_config.properties)
# but nothing in this package consumes them, because every app profile's
# required_core is 1, so core count never binds admission. env.py admits on
# VM *utilization* instead. They are kept so the mirror stays complete --
# don't go looking for where they are read.
NUM_EDGE_SERVERS = 10
VMS_PER_EDGE_SERVER = 8

MOBILE_VM_MIPS = 4000
MOBILE_VM_CORES = 1

EDGE_VM_MIPS = 10000
EDGE_VM_CORES = 2

CLOUD_VM_COUNT = 4
CLOUD_VM_MIPS = 100000
CLOUD_VM_CORES = 4

WLAN_BANDWIDTH_MBPS = 200
WAN_BANDWIDTH_MBPS = 15
MAN_BANDWIDTH_MBPS = 200
WAN_PROPAGATION_DELAY = 0.1
LAN_INTERNAL_DELAY = 0.005

# Per-concurrent-client bandwidth contention (env._bandwidth): each link's
# capacity is divided by (1 + this * concurrently-active clients sharing
# it), the lightweight analogue of EdgeCloudSim's per-client MM1 bandwidth
# division -- it's what makes a 2,000-device scenario actually see longer
# network delays than a 200-device one (paper Fig. 6(c)).
BANDWIDTH_CONTENTION_PER_CLIENT = 0.02

# Delay threshold used by the network failure indicator F_network (Eq. 2).
TMAX_SECONDS = 5.0

# Fixed per-decision inference latency (seconds) added to every task's
# service time, in the toy env (env.step) and the real simulation alike --
# the ACT round trip (TCP + torch forward + online update) that deployment
# pays per decision but the simulation otherwise treats as a free oracle.
# 0.0 (default) keeps the historical free-decision behavior. Mirrored by
# ReSACOStateBuilder.RESACO_DECISION_LATENCY_SECONDS (change both);
# ReSACOBridgeClient logs measured ACT round-trip stats per scenario so a
# realistic value can be chosen from data instead of guessed.
DECISION_LATENCY_SECONDS = 0.0

# Reward weighting by app delay sensitivity: reward is scaled by
# (DELAY_SENSITIVITY_BASE + delay_sensitivity), so a delay-sensitive app
# (AR, 0.9 -> x1.4) penalizes slow service ~2.3x harder than an
# insensitive one (HEAVY_COMP, 0.1 -> x0.6). The base keeps even the most
# insensitive app's reward non-degenerate (completion still matters).
# Mirrored on the Java side by ReSACOStateBuilder.RESACO_DELAY_SENSITIVITY_BASE
# (the bridge's OUTCOME rewards must be on the same scale the policy was
# trained on) -- if you change one, change both.
#
# DELAY_SENSITIVITY_REWARD = False recovers the paper's Eq. (9) exactly
# (reward = -T / -(Tmax+1), unweighted). It too is mirrored in Java
# (ReSACOStateBuilder.RESACO_DELAY_SENSITIVITY_REWARD) -- flip both or
# neither, then retrain: a policy trained on one reward scale must not be
# served/online-adapted against the other.
DELAY_SENSITIVITY_REWARD = True
DELAY_SENSITIVITY_BASE = 0.5

# ----------------------------------------------------------------------------
# Industry-direction objectives (all deviations from the paper's pure-latency
# Eq. (9); each has an off switch, and all three are mirrored in Java by
# ReSACOStateBuilder.industryPenalty() -- change both sides or neither, then
# retrain). Reward becomes
#   r = -(w * T + ENERGY_REWARD_WEIGHT * E + COST_REWARD_WEIGHT * C
#         + SLA_PENALTY_WEIGHT * max(0, T - deadline))          on success,
# with E in joules and C in EdgeCloudSim-style abstract cost units, so the
# weights express "seconds-equivalent per joule / per cost unit".
# ----------------------------------------------------------------------------

# --- Energy (green computing / the AI power crunch) ---
# Device-side: active compute power for local execution, radio TX power for
# the WLAN leg of any offload. Infrastructure-side: active per-VM power at
# the edge (micro-DCs run hot per unit of work) vs. the cloud (hyperscaler
# efficiency, but WAN transport energy per Mbit on top).
ENERGY_AWARE_REWARD = True
ENERGY_REWARD_WEIGHT = 0.02          # seconds-equivalent per joule
MOBILE_COMPUTE_POWER_W = 4.0
WLAN_TX_POWER_W = 1.3
EDGE_VM_POWER_W = 30.0
CLOUD_VM_POWER_W = 90.0
WAN_TRANSPORT_ENERGY_PER_MBIT = 0.05  # core-network transport, J/Mbit

# --- Cost (FinOps) ---
# The device is the user's own hardware (free); edge VM-seconds are priced
# like edge_devices.xml's costPerSec, cloud VM-seconds cheaper (economies of
# scale) but cloud results pay the classic per-MB WAN egress fee on the
# download leg.
COST_AWARE_REWARD = True
COST_REWARD_WEIGHT = 0.05            # seconds-equivalent per cost unit
EDGE_COST_PER_CPU_SEC = 3.0          # matches edge_devices.xml costPerSec
CLOUD_COST_PER_CPU_SEC = 1.0
WAN_EGRESS_COST_PER_MB = 0.1

# --- SLA / SLO (per-app deadlines) ---
# Apps carry a max_delay_requirement (applications*.xml's optional field,
# mirrored on AppProfile); a task that *succeeds* but blows its deadline is
# still delivered (not failed) yet counts as an SLA violation and pays a
# penalty proportional to how late it was. Weight 0 turns the deadline into
# a pure metric.
SLA_PENALTY_WEIGHT = 1.0

# ----------------------------------------------------------------------------
# Metadata ranges used to build randomized training scenarios.
#
# TABLE_II_RANGES reproduces the paper's Table II exactly ("each scenario is
# generated by randomizing these parameters within the specified intervals"),
# including the stated coupling vm_utilization_on_edge = 10 x
# vm_utilization_on_cloud (so only the cloud range is sampled and edge is
# derived). scenario.py samples synthetic app profiles from these ranges for
# the paper-faithful scenario source, alongside the four real
# applications.xml profiles (APP_PROFILES) the deployment simulation actually
# runs -- see scenario.py's SCENARIO_SOURCES for how the two mix.
# number_of_mobile_devices applies to every source: it's a genuinely free
# environmental condition no config file fixes.
# ----------------------------------------------------------------------------
TABLE_II_RANGES = {
    "usage_percentage": (10, 30),
    "poisson_interarrival": (5, 20),
    "delay_sensitivity": (0.0, 1.0),
    "active_period": (10, 60),
    "idle_period": (10, 60),
    "data_upload": (25, 2500),
    "data_download": (25, 2500),
    "task_length": (1500, 10000),
    # required_core is fixed at 1 (Table II)
    "vm_utilization_on_cloud": (0.2, 2.0),   # edge = 10 x cloud (Section V-A-1)
    "vm_utilization_on_mobile": (10, 50),
}

METADATA_RANGES = {
    "number_of_mobile_devices": (200, 2000),
}

# ----------------------------------------------------------------------------
# Continuous environment randomization (scenario.sample_randomized_env_profile).
#
# Section IV-A-1 describes each meta-training scenario as varying "task
# arrival rates, network bandwidths, or edge server capabilities", but Table
# II only randomizes the *task* side; the network/hardware side here was
# previously limited to the ten fixed ENV_PROFILES presets. These ranges let
# a scenario draw its physical environment continuously instead -- spanning
# (and interpolating between) every preset: WLAN from the SDV rural radio to
# mmWave, WAN from CONGESTED_WAN to MMWAVE_5G, edge MIPS from WEAK_EDGE to
# GPU_EDGE, propagation from terrestrial to NTN, dwell from SDV_HIGHWAY to
# pedestrian. Rate-like quantities are sampled log-uniformly (a 3->6 Mbps
# step matters as much as 25->50), the cloud linearly.
#
# EDGE_HETEROGENEITY_RANGE makes the ten edge servers *individually*
# different (each server's MIPS = the scenario's edge MIPS x its own
# log-uniform factor): Section V-A-2's ten identical servers make the
# per-server actions 1..N interchangeable, whereas real edge fleets are
# mixed-generation. The factors are NOT in the state vector -- which server
# is fast is a scenario-specific latent fact the policy has to *adapt* to,
# exactly the structure meta-initialization is supposed to exploit.
#
# Training-side only: no single Java preset can mirror a continuous range,
# so these scenarios are excluded from test_config_mirror by construction
# (EnvProfile name "RANDOMIZED", never in ENV_PROFILES). Off unless a caller
# passes env_randomization > 0 (train_meta.py --env-randomization).
# ----------------------------------------------------------------------------
ENV_RANDOMIZATION_RANGES = {
    "wlan_bandwidth_mbps": (100.0, 1000.0),     # log-uniform
    "man_bandwidth_mbps": (100.0, 400.0),       # log-uniform
    "wan_bandwidth_mbps": (3.0, 50.0),          # log-uniform
    "edge_vm_mips": (2500.0, 40000.0),          # log-uniform
    "cloud_vm_mips": (50000.0, 150000.0),       # uniform
    "wan_propagation_delay": (0.05, 0.6),       # log-uniform
    "mobility_dwell_time": (8.0, 300.0),        # log-uniform, when mobile
}
# Probability a randomized environment has mobile devices at all (otherwise
# dwell = inf, the paper's two-failure-mode model).
ENV_RANDOMIZATION_MOBILITY_PROB = 0.5
# Per-edge-server MIPS multiplier range (log-uniform); (1.0, 1.0) disables.
EDGE_HETEROGENEITY_RANGE = (0.5, 2.0)

# ----------------------------------------------------------------------------
# State / action space (Section IV-A-2)
# state = {L, U, D, mu_d, mu_e1..mu_eN, mu_c, b_wlan, b_man, b_wan}
# action in {0, ..., N+1}: 0 = device, 1..N = edge servers, N+1 = cloud
# ----------------------------------------------------------------------------
STATE_DIM = 4 + NUM_EDGE_SERVERS + 1 + 3  # L,U,D,mu_d + mu_e(N) + mu_c + bwlan,bman,bwan
ACTION_DIM = NUM_EDGE_SERVERS + 2

# ----------------------------------------------------------------------------
# SAC / Reptile hyperparameters (Section V-B-1)
# ----------------------------------------------------------------------------
META_LR = 0.001          # alpha: Reptile meta learning rate
DISCOUNT_GAMMA = 0.99
ENTROPY_TAU = 0.2        # initial entropy temperature (learned online when AUTO_ENTROPY_TUNING)

# Automatic entropy-temperature tuning (Haarnoja et al. 2018's learned-alpha
# extension, discrete form per Christodoulou 2019). The paper's fixed
# tau=0.2 let the policy collapse onto a single action once the critic
# briefly favored it (see README "Convergence"); tuning tau to hold policy
# entropy near TARGET_ENTROPY_SCALE * ln(ACTION_DIM) pushes back exactly
# when the policy gets too deterministic, and backs off as it explores.
AUTO_ENTROPY_TUNING = True
ALPHA_LR = 3e-4
TARGET_ENTROPY_SCALE = 0.6  # target entropy = this * ln(ACTION_DIM) nats
REPLAY_BUFFER_SIZE = 100_000
BATCH_SIZE = 64
TARGET_SOFT_UPDATE_RHO = 0.995
CRITIC_LR = 3e-4
ACTOR_LR = 3e-4

NUM_META_SCENARIOS = 10   # M
NUM_OUTER_ITERATIONS = 300  # K
NUM_INNER_SAC_UPDATES = 50  # N

HIDDEN_SIZES = (128, 128)

# ----------------------------------------------------------------------------
# Baseline-specific hyperparameters (Section V-C). Only ReSACO and the
# SAC-no-meta-init baseline actually use SAC-Update, so only they use the
# SAC hyperparameters above; DDPG and A2C/A3C get their own learning rates
# tuned to what's typical for each algorithm family, instead of blindly
# reusing SAC's ACTOR_LR/CRITIC_LR.
# ----------------------------------------------------------------------------
# DDPG's critic conventionally learns faster than its actor (Lillicrap et
# al. 2015 uses 1e-3 critic / 1e-4 actor) so the critic can track a
# deterministic, faster-moving target.
DDPG_ACTOR_LR = 1e-4
DDPG_CRITIC_LR = 1e-3

# A2C/A3C are on-policy, single-short-rollout-per-update methods (no replay
# buffer to average noise out over) -- a single higher shared actor/critic
# LR is the typical choice for both (used here by both A2C and the A2CAgent
# A3C's global model gets wrapped into).
A2C_LR = 7e-4


# ----------------------------------------------------------------------------
# Temporary overrides (used by scripts/run_ablations.py)
# ----------------------------------------------------------------------------
@contextmanager
def override_config(**overrides):
    """Temporarily replace module-level constants; originals are restored
    when the block exits, exception included.

    Works because every consumer reads these values as `config.X` attribute
    lookups at call time (never `from config import X` -- verified across
    the repo), so a setattr on this module is immediately visible. One
    caveat: values captured at object-construction time (e.g. SACAgent
    reads AUTO_ENTROPY_TUNING/ENTROPY_TAU in __init__) reflect an override
    only for objects constructed INSIDE the with-block.

    Unknown names are rejected up front (before anything is modified) so a
    typo can't silently ablate nothing.
    """
    import sys
    module = sys.modules[__name__]
    unknown = [k for k in overrides if not hasattr(module, k)]
    if unknown:
        raise AttributeError(f"override_config(): unknown config name(s) {unknown}")
    saved = {k: getattr(module, k) for k in overrides}
    try:
        for k, v in overrides.items():
            setattr(module, k, v)
        yield
    finally:
        for k, v in saved.items():
            setattr(module, k, v)
