"""Non-ReSACO offloading learners: the four comparison baselines from
Section V-C of the ReSACO paper (SAC without meta-initialization reuses
ReSACO's own SACAgent, so only DDPG/A2C/A3C live here).

They share the environment, networks and replay buffer with ReSACO via
the `mec_core` package, but nothing here imports ReSACO and nothing in
ReSACO imports this -- adding another baseline means adding a module in
this package only.
"""

from .a2c import A2CAgent
from .a3c import A3CTrainer, train_a3c
from .ddpg import DDPGAgent

__all__ = ["DDPGAgent", "A2CAgent", "A3CTrainer", "train_a3c"]
