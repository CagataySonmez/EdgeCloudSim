"""One place to make a run reproducible.

The scripts all take a `--seed` and thread it into scenario sampling and
each environment's `random.Random(seed)`, which made runs *look* seeded.
Two other sources of randomness were never covered, so two runs of the
same command produced different `theta_star.pt` files:

- `ReplayBuffer.sample()` draws its minibatch indices from the **global**
  `random` module, which Python seeds from OS entropy at import;
- torch's global RNG drives network initialization and the actor's
  action sampling (`Categorical.sample`).

`seed_everything()` pins both. Verified: with it, the 30x50 meta-training
benchmark reproduces bit-for-bit across processes; without it, the same
command gave a different result every time.
"""

import random

import numpy as np
import torch


def seed_everything(seed: int) -> None:
    """Seeds every RNG the training/serving pipeline actually draws from.

    Note this seeds *global* state on purpose: the replay buffer and
    torch's default generator are both global, and threading explicit
    generators through every agent/network would be a far larger change
    for the same guarantee.
    """
    random.seed(seed)
    np.random.seed(seed % (2**32))
    torch.manual_seed(seed)
