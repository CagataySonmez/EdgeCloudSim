"""Shared MEC offloading infrastructure.

Everything here is algorithm-agnostic and used by *all* offloading
learners in this repo -- ReSACO (../ReSACO) and the comparison baselines
(../baselines) alike:

  config.py         tier/network/reward constants + the paper's Table II ranges
  scenario.py       AppProfile / EnvProfile / Scenario sampling
  env.py            the lightweight three-tier offloading environment
  networks.py       the discrete Actor / Critic MLPs
  normalize.py      the fixed per-feature state normalization
  replay_buffer.py  the columnar replay buffer D
  seeding.py        seed_everything(), so a --seed actually pins a run

Keeping it out of ReSACO/ is deliberate: ReSACO/ holds only the ReSACO
algorithm itself (SAC-Update, the Reptile loops, the Deployment Phase),
so nothing in it has to be touched to add or compare another algorithm.
"""

from . import config, env, networks, normalize, replay_buffer, scenario, seeding

__all__ = ["config", "env", "networks", "normalize", "replay_buffer",
           "scenario", "seeding"]
