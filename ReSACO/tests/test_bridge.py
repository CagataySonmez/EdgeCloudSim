"""Tests for bridge/inference_server.py's checkpoint loading and
online-learning persistence: resume-from-adapted-checkpoint preference,
missing-checkpoint fallback, and save_all_agents()."""

import torch

import bridge.inference_server as srv
from baselines.a2c import A2CAgent
from baselines.ddpg import DDPGAgent
from resaco.sac import SACAgent


def _write_fake_checkpoints(tmp_path):
    torch.save(SACAgent().get_params(), tmp_path / "theta_star.pt")
    torch.save(SACAgent().get_params(), tmp_path / "sac_no_meta.pt")
    torch.save(DDPGAgent().get_params(), tmp_path / "ddpg.pt")
    torch.save(A2CAgent().get_params(), tmp_path / "a2c.pt")
    torch.save(A2CAgent().get_params(), tmp_path / "a3c.pt")


def test_adapted_path_helper():
    assert srv._adapted_path("/x/y/theta_star.pt") == "/x/y/theta_star_adapted.pt"


def test_load_agents_loads_originals_when_no_adapted_checkpoint_exists(tmp_path):
    _write_fake_checkpoints(tmp_path)
    loaded, resumed, missing = srv.load_agents(str(tmp_path))
    assert set(loaded) == set(srv.ALGO_REGISTRY.keys())
    assert resumed == []
    assert missing == []


def test_load_agents_prefers_adapted_checkpoint_when_present(tmp_path):
    _write_fake_checkpoints(tmp_path)
    torch.save(SACAgent().get_params(), tmp_path / "theta_star_adapted.pt")

    loaded, resumed, missing = srv.load_agents(str(tmp_path))
    assert "RESACO" in resumed
    assert "RESACO" not in loaded
    assert "SAC_BASELINE" in loaded  # no adapted file for this one -- falls back to original


def test_load_agents_missing_checkpoint_falls_back_to_random_init(tmp_path):
    loaded, resumed, missing = srv.load_agents(str(tmp_path))
    assert set(missing) == set(srv.ALGO_REGISTRY.keys())
    assert loaded == []
    assert resumed == []


def test_persist_capable_agent_gets_correct_adapted_save_path(tmp_path):
    _write_fake_checkpoints(tmp_path)
    srv.load_agents(str(tmp_path), autosave_every=7)

    resaco_agent = srv._agents["RESACO"]
    assert resaco_agent.save_path == str(tmp_path / "theta_star_adapted.pt")
    assert resaco_agent.autosave_every == 7

    frozen_agent = srv._agents["A2C_BASELINE"]
    assert frozen_agent.save() is False  # on-policy: nothing to persist


def test_reset_command_restores_loaded_checkpoint(tmp_path):
    _write_fake_checkpoints(tmp_path)
    srv.load_agents(str(tmp_path))
    original = torch.load(tmp_path / "theta_star.pt", map_location="cpu")

    agent = srv._agents["RESACO"]
    # drift theta_adapt away from theta* via online outcomes
    state = [0.5] * 18
    for i in range(70):
        rid = f"reset-test-{i}"
        agent.select_action(state, request_id=rid)
        agent.report_outcome(rid, -1.0, state, False)
    drifted = agent.state_dict()
    assert any(not torch.equal(original["actor"][k], drifted["actor"][k])
               for k in original["actor"])

    assert agent.reset() is True
    restored = agent.state_dict()
    assert all(torch.equal(original["actor"][k], restored["actor"][k])
               for k in original["actor"])


def test_reset_on_random_init_agent_reports_error(tmp_path):
    # no checkpoints on disk -> agents serve random init -> nothing to reset to
    srv.load_agents(str(tmp_path))
    assert srv._agents["RESACO"].reset() is False


def test_save_all_agents_writes_adapted_files_for_persist_capable_algos_only(tmp_path):
    _write_fake_checkpoints(tmp_path)
    srv.load_agents(str(tmp_path))

    saved = srv.save_all_agents()

    assert set(saved) == {"RESACO", "SAC_BASELINE", "DDPG_BASELINE"}
    assert (tmp_path / "theta_star_adapted.pt").exists()
    assert (tmp_path / "sac_no_meta_adapted.pt").exists()
    assert (tmp_path / "ddpg_adapted.pt").exists()
    assert not (tmp_path / "a2c_adapted.pt").exists()
    assert not (tmp_path / "a3c_adapted.pt").exists()


def test_each_algo_has_its_own_independent_lock():
    """Regression test: a single shared lock would serialize every algo's
    ACT/OUTCOME behind e.g. RESACO's blocking autosave disk write, even
    though the algos share no state that needs protecting together. Locks
    must be distinct objects, one per ALGO_REGISTRY entry."""
    assert set(srv._locks.keys()) == set(srv.ALGO_REGISTRY.keys())
    lock_ids = {id(lock) for lock in srv._locks.values()}
    assert len(lock_ids) == len(srv.ALGO_REGISTRY), "some algos share the same lock object"


def test_locking_one_algo_does_not_block_another():
    resaco_lock = srv._locks["RESACO"]
    other_lock = srv._locks["SAC_BASELINE"]
    with resaco_lock:
        acquired = other_lock.acquire(blocking=False)
    assert acquired, "SAC_BASELINE's lock was blocked by RESACO holding its own lock"
    other_lock.release()
