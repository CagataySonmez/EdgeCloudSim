"""The ReSACO algorithm itself: SAC-Update (Algorithm 3), the Reptile
Outer/Inner meta-training loops (Algorithms 1-2), and the Deployment
Phase (Algorithm 4).

Everything algorithm-agnostic that this builds on -- the environment,
scenarios, networks, replay buffer, normalization, config, seeding --
lives in the shared `mec_core` package at the repo root; the non-ReSACO
comparison learners live in `baselines`.
"""

from . import deploy, reptile, sac

__all__ = ["deploy", "reptile", "sac"]
