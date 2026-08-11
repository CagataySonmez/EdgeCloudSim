"""P2-1 acceptance: the MULTITASK joint-pretraining control must consume
EXACTLY the same number of environment interactions as ReSACO's
meta-training (the fairness premise of the whole comparison), and its
checkpoint must round-trip through SACAgent like every other SAC-family
checkpoint the bridge serves."""

import os
import sys

import torch

sys.path.insert(0, os.path.join(
    os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "scripts"))

from mec_core import config
from mec_core.env import MECOffloadEnv
from mec_core.scenario import sample_scenario_pool
from resaco.reptile import outer_loop
from resaco.sac import SACAgent
from train_baselines import train_multitask


def test_multitask_env_steps_equal_resaco(monkeypatch):
    calls = {"n": 0}
    orig_step = MECOffloadEnv.step

    def counting_step(self, action):
        calls["n"] += 1
        return orig_step(self, action)

    monkeypatch.setattr(MECOffloadEnv, "step", counting_step)

    scenarios = sample_scenario_pool(2, seed=31)
    K, N = 2, 5

    calls["n"] = 0
    outer_loop(scenarios, num_outer_iterations=K, num_inner_updates=N,
               seed=31, progress_every=0)
    resaco_steps = calls["n"]

    calls["n"] = 0
    _, reported = train_multitask(scenarios, num_outer_iterations=K,
                                  num_inner_updates=N, seed=31,
                                  progress_every=0)
    assert reported == calls["n"], "train_multitask's own step accounting is wrong"
    assert reported == resaco_steps == K * (config.BATCH_SIZE + N)


def test_multitask_checkpoint_roundtrips_into_sacagent(tmp_path):
    scenarios = sample_scenario_pool(1, seed=32)
    agent, _ = train_multitask(scenarios, num_outer_iterations=1,
                               num_inner_updates=2, seed=32, progress_every=0)
    path = tmp_path / "multitask.pt"
    torch.save(agent.get_params(), path)
    SACAgent().load_params(torch.load(path, map_location="cpu"))
