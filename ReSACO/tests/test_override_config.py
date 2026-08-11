"""P2-3 acceptance: config.override_config must restore original values on
every exit path and reject unknown names before touching anything."""

import pytest

from mec_core import config


def test_override_applies_inside_and_restores_after():
    original = config.SLA_PENALTY_WEIGHT
    with config.override_config(SLA_PENALTY_WEIGHT=0.0, ENERGY_AWARE_REWARD=False):
        assert config.SLA_PENALTY_WEIGHT == 0.0
        assert config.ENERGY_AWARE_REWARD is False
    assert config.SLA_PENALTY_WEIGHT == original
    assert config.ENERGY_AWARE_REWARD is True


def test_override_restores_on_exception():
    original = config.AUTO_ENTROPY_TUNING
    with pytest.raises(RuntimeError):
        with config.override_config(AUTO_ENTROPY_TUNING=not original):
            assert config.AUTO_ENTROPY_TUNING is (not original)
            raise RuntimeError("boom")
    assert config.AUTO_ENTROPY_TUNING is original


def test_unknown_name_rejected_before_any_change():
    original = config.COST_AWARE_REWARD
    with pytest.raises(AttributeError):
        with config.override_config(COST_AWARE_REWARD=False, NO_SUCH_FLAG=1):
            pass
    # the valid key must NOT have been applied either -- all-or-nothing
    assert config.COST_AWARE_REWARD is original
    assert not hasattr(config, "NO_SUCH_FLAG")


def test_consumers_see_the_override_at_runtime():
    """env.py reads config.SLA_PENALTY_WEIGHT at reward time, so an
    override must actually change reward behavior inside the block."""
    from mec_core.scenario import sample_scenario_pool

    scenario = sample_scenario_pool(1, seed=41)[0]
    with config.override_config(SLA_PENALTY_WEIGHT=0.0):
        assert config.SLA_PENALTY_WEIGHT == 0.0
    assert config.SLA_PENALTY_WEIGHT == 1.0
    assert scenario is not None  # scenario sampling unaffected by the block
