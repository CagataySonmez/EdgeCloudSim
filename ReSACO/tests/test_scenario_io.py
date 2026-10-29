"""The scenario data model (mec_core/scenario_io.py): the meta-training
metadata set S must round-trip through JSON exactly, so a theta* can be
re-trained -- or a control trained -- on the identical pool."""

import json
import math

import pytest

from mec_core.scenario import sample_scenario_pool
from mec_core.scenario_io import (SCHEMA_VERSION, describe_scenarios, load_scenarios,
                                  save_scenarios, scenario_from_dict, scenario_to_dict)


def _mixed_pool():
    # presets (inf dwell / inf deadlines), Table II synthetics, and
    # continuously randomized environments with per-server factors
    return sample_scenario_pool(25, seed=13, env_randomization=0.5)


def test_round_trip_is_exact(tmp_path):
    pool = _mixed_pool()
    path = tmp_path / "pool.json"
    save_scenarios(str(path), pool, metadata={"seed": 13})
    loaded, meta = load_scenarios(str(path))
    assert loaded == pool
    assert meta == {"seed": 13}


def test_file_is_strict_json_with_inf_encoded_as_string(tmp_path):
    pool = _mixed_pool()
    path = tmp_path / "pool.json"
    save_scenarios(str(path), pool)
    text = path.read_text(encoding="utf-8")
    assert "Infinity" not in text  # Python's non-standard JSON extension
    doc = json.loads(text, parse_constant=lambda c: pytest.fail(f"non-strict JSON constant {c}"))
    assert doc["schema_version"] == SCHEMA_VERSION
    assert any(s["app_profile"]["max_delay_requirement"] == "inf" for s in doc["scenarios"])


def test_tuple_factors_survive_as_tuples():
    s = next(s for s in _mixed_pool() if s.env_profile.edge_mips_factors)
    back = scenario_from_dict(json.loads(json.dumps(scenario_to_dict(s))))
    assert isinstance(back.env_profile.edge_mips_factors, tuple)
    assert back == s
    hash(back.env_profile)  # frozen EnvProfile must stay hashable


def test_unknown_fields_rejected():
    d = scenario_to_dict(_mixed_pool()[0])
    d["env_profile"]["bogus"] = 1
    with pytest.raises(ValueError, match="bogus"):
        scenario_from_dict(d)


def test_other_schema_versions_rejected(tmp_path):
    path = tmp_path / "future.json"
    path.write_text(json.dumps({"schema_version": SCHEMA_VERSION + 1, "scenarios": []}))
    with pytest.raises(ValueError, match="schema_version"):
        load_scenarios(str(path))


def test_describe_counts_and_ranges():
    pool = _mixed_pool()
    summary = describe_scenarios(pool)
    assert summary["num_scenarios"] == len(pool)
    assert sum(summary["app_profiles"].values()) == len(pool)
    assert sum(summary["env_profiles"].values()) == len(pool)
    lo, hi = summary["ranges"]["number_of_mobile_devices"]
    assert lo == min(s.number_of_mobile_devices for s in pool)
    assert hi == max(s.number_of_mobile_devices for s in pool)
    # dwell span ignores inf (static environments)
    dwell = summary["ranges"]["mobility_dwell_time"]
    assert dwell is None or all(not math.isinf(v) for v in dwell)
