"""Non-ReSACO offloading learners: the four comparison baselines from
Section V-C of the ReSACO paper (SAC without meta-initialization reuses
ReSACO's own SACAgent, so only DDPG/A2C/A3C live here).

They share the environment, networks and replay buffer with ReSACO via
the `mec_core` package, but nothing here imports the resaco algorithm
package and nothing in resaco/ imports this. The bridge and comparison
scripts import both, so serving or comparing a new baseline means adding
a module in this package plus registering it there (the bridge's
ALGO_REGISTRY, compare_algorithms' ALGORITHMS, train_baselines).
"""

from .a2c import A2CAgent
from .a3c import A3CTrainer, train_a3c
from .ddpg import DDPGAgent

__all__ = ["DDPGAgent", "A2CAgent", "A3CTrainer", "train_a3c"]
