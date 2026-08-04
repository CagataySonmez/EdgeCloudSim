"""Tests for the online-learning persistence added to DeploymentAgent
(autosave-every-N-updates, manual save(), resume-friendly no-op when no
save_path is configured) and FrozenPolicyAgent's always-a-no-op save()."""

import os

from mec_core import config
from baselines.a2c import A2CAgent
from resaco.deploy import DeploymentAgent, FrozenPolicyAgent
from resaco.sac import SACAgent


def _drive_transitions(wrapper, n):
    state = [0.5] * config.STATE_DIM
    next_state = [0.6] * config.STATE_DIM
    for i in range(n):
        request_id = f"r{i}"
        wrapper.select_action(state, request_id=request_id)
        wrapper.report_outcome(request_id, -1.0, next_state, False)


def test_deployment_agent_autosaves_after_threshold(tmp_path):
    save_path = str(tmp_path / "adapted.pt")
    agent = DeploymentAgent(SACAgent(), save_path=save_path, autosave_every=3)

    # update() (and so autosave counting) only starts firing once the
    # replay buffer reaches BATCH_SIZE -- push well past that plus the
    # autosave threshold.
    _drive_transitions(agent, config.BATCH_SIZE + 5)

    assert os.path.exists(save_path)


def test_deployment_agent_without_save_path_is_a_no_op():
    agent = DeploymentAgent(SACAgent())
    assert agent.save() is False


def test_deployment_agent_manual_save(tmp_path):
    save_path = str(tmp_path / "manual.pt")
    agent = DeploymentAgent(SACAgent(), save_path=save_path, autosave_every=10_000)
    assert agent.save() is True
    assert os.path.exists(save_path)


def test_deployment_agent_reset_restores_initial_params_and_clears_state():
    import torch

    source = SACAgent()
    params = source.get_params()
    agent = DeploymentAgent(SACAgent(), params=params)

    # adapt online well past batch size so weights genuinely move
    _drive_transitions(agent, config.BATCH_SIZE + 5)
    drifted = agent.state_dict()
    assert any(not torch.equal(params["actor"][k], drifted["actor"][k])
               for k in params["actor"]), "online updates never moved the params"

    assert agent.reset() is True
    restored = agent.state_dict()
    assert all(torch.equal(params["actor"][k], restored["actor"][k])
               for k in params["actor"]), "reset did not restore theta*"
    assert len(agent.agent.replay_buffer) == 0
    assert not agent._pending


def test_deployment_agent_reset_without_params_returns_false():
    agent = DeploymentAgent(SACAgent())  # served randomly-initialized: nothing to reset to
    assert agent.reset() is False


def test_frozen_agent_reset_is_safe():
    frozen = FrozenPolicyAgent(A2CAgent())
    frozen.select_action([0.5] * config.STATE_DIM, request_id="r1")
    assert frozen.reset() is True
    assert frozen.report_outcome("r1", -1.0, [0.5] * config.STATE_DIM) is None  # correlation dropped


def test_deployment_agent_report_outcome_unknown_request_returns_none():
    agent = DeploymentAgent(SACAgent())
    assert agent.report_outcome("never-seen", -1.0, [0.0] * config.STATE_DIM) is None


def test_deployment_agent_report_outcome_duplicate_is_ignored():
    agent = DeploymentAgent(SACAgent())
    state = [0.5] * config.STATE_DIM
    agent.select_action(state, request_id="r1")
    first = agent.report_outcome("r1", -1.0, state, False)
    second = agent.report_outcome("r1", -1.0, state, False)
    assert first is not None
    assert second is None


def test_frozen_policy_agent_never_saves():
    agent = FrozenPolicyAgent(A2CAgent())
    assert agent.save() is False


def test_frozen_policy_agent_report_outcome_is_a_no_op():
    agent = FrozenPolicyAgent(A2CAgent())
    state = [0.5] * config.STATE_DIM
    action = agent.select_action(state, request_id="r1")
    assert 0 <= action < config.ACTION_DIM

    result = agent.report_outcome("r1", -1.0, state, False)
    assert result == {"recorded": False, "update": None}
    # the request was consumed by the first report -- reporting again is unknown
    assert agent.report_outcome("r1", -1.0, state, False) is None
