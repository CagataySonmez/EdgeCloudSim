"""Guards every hand-maintained mirror between the Python training side
and the Java/EdgeCloudSim simulation side.

Several values exist in two (sometimes three) places on purpose -- the
Python trainer can't read EdgeCloudSim's XML at runtime, and the Java
orchestrator can't import resaco/config.py -- so they are kept in sync by
hand, with "if you change one, change both" comments. Comments don't fail
a build; these tests do:

- every `<application>` in applications*.xml vs its AppProfile
- every ENV_PROFILE vs its scripts/ReSACO/config/*.properties preset
  (and the edge_devices*.xml the preset is paired with in simulation.list)
- the reward/state constants duplicated in ReSACOStateBuilder.java
- the task-lookup-table column indices ReSACOStateBuilder uses vs the
  attribute order SimSettings.java actually parses
- simulation.list referencing files that exist
"""

import os
import re
import xml.etree.ElementTree as ET

import pytest

from mec_core import config
from mec_core.scenario import (APP_PROFILES, ENV_PROFILES, NEXTGEN_APP_PROFILES,
                              SDV_APP_PROFILES)

RESACO_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
REPO = os.path.dirname(RESACO_DIR)
CONFIG_DIR = os.path.join(REPO, "scripts", "ReSACO", "config")
JAVA_DIR = os.path.join(REPO, "src", "edu", "boun", "edgecloudsim", "applications", "resaco")
SIM_SETTINGS = os.path.join(REPO, "src", "edu", "boun", "edgecloudsim", "core", "SimSettings.java")

# app-profile source <-> the applications XML it mirrors
APP_XML_MIRRORS = [
    (APP_PROFILES, "applications.xml"),
    (NEXTGEN_APP_PROFILES, "applications_nextgen.xml"),
    (SDV_APP_PROFILES, "applications_sdv.xml"),
]

# AppProfile field -> XML element name
APP_FIELDS = {
    "usage_percentage": "usage_percentage",
    "poisson_interarrival": "poisson_interarrival",
    "delay_sensitivity": "delay_sensitivity",
    "active_period": "active_period",
    "idle_period": "idle_period",
    "data_upload": "data_upload",
    "data_download": "data_download",
    "task_length": "task_length",
    "required_core": "required_core",
    "vm_utilization_on_edge": "vm_utilization_on_edge",
    "vm_utilization_on_cloud": "vm_utilization_on_cloud",
    "vm_utilization_on_mobile": "vm_utilization_on_mobile",
    "max_delay_requirement": "max_delay_requirement",
}


def _read_properties(name):
    path = os.path.join(CONFIG_DIR, name)
    values = {}
    with open(path, encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if line and not line.startswith("#") and "=" in line:
                key, _, value = line.partition("=")
                values[key.strip()] = value.strip()
    return values


def _edge_vm_mips(edge_devices_xml):
    """The per-VM MIPS every edge datacenter in this XML is configured with
    (all are identical in every preset here)."""
    root = ET.parse(os.path.join(CONFIG_DIR, edge_devices_xml)).getroot()
    mips = {int(vm.find("mips").text) for vm in root.iter("VM")}
    assert len(mips) == 1, f"{edge_devices_xml} mixes VM MIPS values: {mips}"
    return mips.pop()


def _simulation_list():
    rows = []
    with open(os.path.join(REPO, "scripts", "ReSACO", "simulation.list"), encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if line:
                scenario, edge_xml, apps_xml = line.split(";")
                rows.append((scenario, edge_xml, apps_xml))
    return rows


def _java_source(filename):
    with open(os.path.join(JAVA_DIR, filename), encoding="utf-8") as f:
        return f.read()


def _java_constant(source, name):
    m = re.search(rf"\b{name}\s*=\s*([^;]+);", source)
    assert m, f"constant {name} not found in the Java source"
    return m.group(1).strip()


# ---------------------------------------------------------------- app XMLs
@pytest.mark.parametrize("profiles,xml_name", APP_XML_MIRRORS,
                          ids=[x[1] for x in APP_XML_MIRRORS])
def test_app_profiles_match_their_applications_xml(profiles, xml_name):
    root = ET.parse(os.path.join(CONFIG_DIR, xml_name)).getroot()
    xml_apps = {app.get("name"): app for app in root.findall("application")}
    assert set(xml_apps) == {p.name for p in profiles}, (
        f"{xml_name} and its Python profile list disagree on which apps exist"
    )
    for profile in profiles:
        app = xml_apps[profile.name]
        for field, element in APP_FIELDS.items():
            node = app.find(element)
            assert node is not None, f"{xml_name}:{profile.name} is missing <{element}>"
            assert float(node.text) == pytest.approx(getattr(profile, field)), (
                f"{xml_name}:{profile.name}.{element} = {node.text} but "
                f"AppProfile.{field} = {getattr(profile, field)}"
            )


# ------------------------------------------------------- environment presets
# ENV_PROFILE name -> its .properties preset (DEFAULT is default_config)
ENV_PRESETS = {
    "DEFAULT": "default_config.properties",
    "HIGH_MOBILITY": "high_mobility.properties",
    "CONGESTED_WAN": "congested_wan.properties",
    "WEAK_EDGE": "weak_edge.properties",
    "MMWAVE_5G": "mmwave_5g.properties",
    "NTN_BACKHAUL": "ntn_backhaul.properties",
    "GPU_EDGE": "gpu_edge.properties",
    "SDV_HIGHWAY": "sdv_highway.properties",
    "SDV_RURAL_ROAD": "sdv_rural_road.properties",
    "SDV_URBAN": "sdv_urban.properties",
}


def test_every_env_profile_has_a_preset_and_vice_versa():
    assert {e.name for e in ENV_PROFILES} == set(ENV_PRESETS), (
        "an ENV_PROFILE was added/removed without its scripts/ReSACO/config preset"
    )
    for preset in ENV_PRESETS.values():
        assert os.path.exists(os.path.join(CONFIG_DIR, preset)), f"missing preset {preset}"


@pytest.mark.parametrize("profile", ENV_PROFILES, ids=[e.name for e in ENV_PROFILES])
def test_env_profile_network_values_match_its_preset(profile):
    props = _read_properties(ENV_PRESETS[profile.name])
    assert float(props["wlan_bandwidth"]) == pytest.approx(profile.wlan_bandwidth_mbps)
    assert float(props["wan_bandwidth"]) == pytest.approx(profile.wan_bandwidth_mbps)
    assert float(props["wan_propagation_delay"]) == pytest.approx(profile.wan_propagation_delay)


@pytest.mark.parametrize("profile", ENV_PROFILES, ids=[e.name for e in ENV_PROFILES])
def test_env_profile_edge_mips_matches_the_xml_its_scenario_uses(profile):
    """The edge VM MIPS an ENV_PROFILE trains against must equal the MIPS in
    whichever edge_devices*.xml simulation.list pairs that scenario with."""
    scenario = os.path.splitext(ENV_PRESETS[profile.name])[0]
    rows = {row[0]: row for row in _simulation_list()}
    if scenario not in rows:
        pytest.skip(f"{scenario} is not run by simulation.list")
    _, edge_xml, _ = rows[scenario]
    assert _edge_vm_mips(edge_xml) == pytest.approx(profile.edge_vm_mips), (
        f"{profile.name} trains on edge_vm_mips={profile.edge_vm_mips} but "
        f"{scenario} runs against {edge_xml}"
    )


def test_simulation_list_references_existing_files():
    for scenario, edge_xml, apps_xml in _simulation_list():
        for name in (f"{scenario}.properties", edge_xml, apps_xml):
            assert os.path.exists(os.path.join(CONFIG_DIR, name)), (
                f"simulation.list row '{scenario}' references missing config/{name}"
            )


def test_paper_edge_server_count_matches_the_state_layout():
    """Ten edge datacenters (paper Section V-A-2) mapping 1:1 onto the
    policy's per-edge state slots and edge actions."""
    for _, edge_xml, _ in _simulation_list():
        root = ET.parse(os.path.join(CONFIG_DIR, edge_xml)).getroot()
        assert len(root.findall("datacenter")) == config.NUM_EDGE_SERVERS, (
            f"{edge_xml} defines a different number of edge datacenters than "
            f"config.NUM_EDGE_SERVERS = {config.NUM_EDGE_SERVERS}"
        )


# --------------------------------------------------------- Java mirrors
JAVA_MIRRORED_CONSTANTS = [
    ("RESACO_TMAX_SECONDS", "TMAX_SECONDS"),
    ("RESACO_DELAY_SENSITIVITY_BASE", "DELAY_SENSITIVITY_BASE"),
    ("RESACO_ENERGY_REWARD_WEIGHT", "ENERGY_REWARD_WEIGHT"),
    ("RESACO_MOBILE_COMPUTE_POWER_W", "MOBILE_COMPUTE_POWER_W"),
    ("RESACO_WLAN_TX_POWER_W", "WLAN_TX_POWER_W"),
    ("RESACO_EDGE_VM_POWER_W", "EDGE_VM_POWER_W"),
    ("RESACO_CLOUD_VM_POWER_W", "CLOUD_VM_POWER_W"),
    ("RESACO_WAN_TRANSPORT_ENERGY_PER_MBIT", "WAN_TRANSPORT_ENERGY_PER_MBIT"),
    ("RESACO_COST_REWARD_WEIGHT", "COST_REWARD_WEIGHT"),
    ("RESACO_EDGE_COST_PER_CPU_SEC", "EDGE_COST_PER_CPU_SEC"),
    ("RESACO_CLOUD_COST_PER_CPU_SEC", "CLOUD_COST_PER_CPU_SEC"),
    ("RESACO_WAN_EGRESS_COST_PER_MB", "WAN_EGRESS_COST_PER_MB"),
    ("RESACO_SLA_PENALTY_WEIGHT", "SLA_PENALTY_WEIGHT"),
]


@pytest.mark.parametrize("java_name,python_name", JAVA_MIRRORED_CONSTANTS,
                          ids=[j for j, _ in JAVA_MIRRORED_CONSTANTS])
def test_java_reward_constants_mirror_config_py(java_name, python_name):
    source = _java_source("ReSACOStateBuilder.java")
    assert float(_java_constant(source, java_name)) == pytest.approx(getattr(config, python_name)), (
        f"ReSACOStateBuilder.{java_name} and config.{python_name} have drifted -- "
        f"the bridge would then adapt the policy against a different reward scale "
        f"than it was trained on"
    )


@pytest.mark.parametrize("java_name,python_name", [
    ("RESACO_ENERGY_AWARE_REWARD", "ENERGY_AWARE_REWARD"),
    ("RESACO_COST_AWARE_REWARD", "COST_AWARE_REWARD"),
    ("RESACO_DELAY_SENSITIVITY_REWARD", "DELAY_SENSITIVITY_REWARD"),
])
def test_java_reward_toggles_mirror_config_py(java_name, python_name):
    source = _java_source("ReSACOStateBuilder.java")
    java_value = _java_constant(source, java_name) == "true"
    assert java_value is bool(getattr(config, python_name)), (
        f"ReSACOStateBuilder.{java_name} and config.{python_name} disagree"
    )


def test_java_edge_slot_count_matches_state_dim():
    source = _java_source("ReSACOStateBuilder.java")
    slots = int(_java_constant(source, "RESACO_NUM_EDGE_SLOTS"))
    assert slots == config.NUM_EDGE_SERVERS
    # L,U,D,mu_d + mu_e(N) + mu_c + bwlan,bman,bwan -- the wire length the
    # bridge validates every ACT against
    assert 4 + slots + 1 + 3 == config.STATE_DIM


def test_java_task_lookup_indices_match_simsettings_parse_order():
    """ReSACOStateBuilder reads applications.xml values out of
    SimSettings.getTaskLookUpTable() by hard-coded column index; those
    columns are defined by the order SimSettings parses its mandatory then
    optional attributes."""
    with open(SIM_SETTINGS, encoding="utf-8") as f:
        settings_source = f.read()

    def attribute_list(var_name):
        block = re.search(rf"String {var_name}\[\]\s*=\s*\{{(.*?)\}};",
                          settings_source, re.S)
        assert block, f"could not find {var_name} in SimSettings.java"
        return re.findall(r'"([a-z_]+)"', block.group(1))

    columns = attribute_list("mandatoryAttributes") + attribute_list("optionalAttributes")
    expected = {name: i for i, name in enumerate(columns)}

    source = _java_source("ReSACOStateBuilder.java")
    for java_const, attribute in [
        ("TASK_PROPERTY_DATA_UPLOAD", "data_upload"),
        ("TASK_PROPERTY_DATA_DOWNLOAD", "data_download"),
        ("TASK_PROPERTY_TASK_LENGTH", "task_length"),
        ("TASK_PROPERTY_DELAY_SENSITIVITY", "delay_sensitivity"),
        ("TASK_PROPERTY_MAX_DELAY_REQUIREMENT", "max_delay_requirement"),
    ]:
        assert int(_java_constant(source, java_const)) == expected[attribute], (
            f"{java_const} points at column {_java_constant(source, java_const)} but "
            f"SimSettings parses '{attribute}' into column {expected[attribute]}"
        )
