"""Scenario (metadata) data model: a versioned JSON form of a scenario pool.

The paper's Meta-Learning Phase is defined over a metadata set
S = {S_1, ..., S_M} (Algorithm 1's input). Until now that set only ever
existed implicitly -- re-sampled from a seed inside every script -- so the
exact data a theta* was meta-trained on could not be inspected, diffed,
shared, or reused by another tool without re-deriving the sampling code
path. This module makes S a first-class artifact:

    save_scenarios(path, scenarios, metadata={...})   # write
    scenarios, metadata = load_scenarios(path)        # read back, exactly

The document carries a schema version, free-form provenance metadata
(seed, source, ...), and every field of every scenario -- the task profile
(Table II's columns + the SLA deadline), the device count, and the full
physical environment including per-edge-server MIPS factors. Infinite
values (no SLA deadline, no mobility) are written as the string "inf" so
the file stays strict JSON.
"""

import json
import math
from dataclasses import asdict, fields

from .scenario import AppProfile, EnvProfile, Scenario

SCHEMA_VERSION = 1

_APP_FIELDS = [f.name for f in fields(AppProfile)]
_ENV_FIELDS = [f.name for f in fields(EnvProfile)]


def _encode(value):
    if isinstance(value, float) and math.isinf(value):
        return "inf" if value > 0 else "-inf"
    if isinstance(value, tuple):
        return [_encode(v) for v in value]
    return value


def _decode_float(value):
    if value == "inf":
        return float("inf")
    if value == "-inf":
        return float("-inf")
    return value


def scenario_to_dict(scenario: Scenario) -> dict:
    app = {k: _encode(v) for k, v in asdict(scenario.app_profile).items()}
    env = {k: _encode(getattr(scenario.env_profile, k)) for k in _ENV_FIELDS}
    return {
        "app_profile": app,
        "number_of_mobile_devices": scenario.number_of_mobile_devices,
        "env_profile": env,
    }


def scenario_from_dict(data: dict) -> Scenario:
    app_raw = data["app_profile"]
    unknown = set(app_raw) - set(_APP_FIELDS)
    if unknown:
        raise ValueError(f"unknown app_profile field(s) {sorted(unknown)}")
    app = AppProfile(**{k: _decode_float(v) for k, v in app_raw.items()})

    env_raw = data["env_profile"]
    unknown = set(env_raw) - set(_ENV_FIELDS)
    if unknown:
        raise ValueError(f"unknown env_profile field(s) {sorted(unknown)}")
    env_kwargs = {k: _decode_float(v) for k, v in env_raw.items()}
    if "edge_mips_factors" in env_kwargs:
        env_kwargs["edge_mips_factors"] = tuple(env_kwargs["edge_mips_factors"])
    env = EnvProfile(**env_kwargs)

    return Scenario(app_profile=app,
                    number_of_mobile_devices=int(data["number_of_mobile_devices"]),
                    env_profile=env)


def save_scenarios(path: str, scenarios, metadata: dict = None) -> None:
    doc = {
        "schema_version": SCHEMA_VERSION,
        "metadata": metadata or {},
        "scenarios": [scenario_to_dict(s) for s in scenarios],
    }
    with open(path, "w", encoding="utf-8") as f:
        json.dump(doc, f, indent=2)
        f.write("\n")


def load_scenarios(path: str):
    """Returns (scenarios, metadata). Rejects documents from a newer schema
    rather than half-reading them."""
    with open(path, encoding="utf-8") as f:
        doc = json.load(f)
    version = doc.get("schema_version")
    if version != SCHEMA_VERSION:
        raise ValueError(f"{path}: unsupported scenario schema_version {version!r} "
                         f"(this code reads version {SCHEMA_VERSION})")
    return [scenario_from_dict(d) for d in doc["scenarios"]], doc.get("metadata", {})


def describe_scenarios(scenarios) -> dict:
    """Coverage summary of a pool -- what the meta-learner actually sees:
    counts per task-profile name and per environment name, plus the range
    of every continuous axis that varies. Used by train_meta.py's startup
    printout and saved alongside the pool."""
    def span(values):
        finite = [v for v in values if not math.isinf(v)]
        return [min(finite), max(finite)] if finite else None

    apps, envs = {}, {}
    for s in scenarios:
        apps[s.app_profile.name] = apps.get(s.app_profile.name, 0) + 1
        envs[s.env_profile.name] = envs.get(s.env_profile.name, 0) + 1
    return {
        "num_scenarios": len(scenarios),
        "app_profiles": apps,
        "env_profiles": envs,
        "ranges": {
            "number_of_mobile_devices": span([s.number_of_mobile_devices for s in scenarios]),
            "poisson_interarrival": span([s.app_profile.poisson_interarrival for s in scenarios]),
            "task_length": span([s.app_profile.task_length for s in scenarios]),
            "delay_sensitivity": span([s.app_profile.delay_sensitivity for s in scenarios]),
            "wlan_bandwidth_mbps": span([s.env_profile.wlan_bandwidth_mbps for s in scenarios]),
            "wan_bandwidth_mbps": span([s.env_profile.wan_bandwidth_mbps for s in scenarios]),
            "edge_vm_mips": span([s.env_profile.edge_vm_mips for s in scenarios]),
            "mobility_dwell_time": span([s.env_profile.mobility_dwell_time for s in scenarios]),
        },
    }
