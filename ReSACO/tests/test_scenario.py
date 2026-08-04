"""Tests for APP_PROFILES / Scenario -- the fix that replaced sampling a
single homogeneous task profile from Table II's abstract ranges with
picking one of the four real app types (matching
scripts/{ReSACO,three_tier}/config/applications.xml exactly), weighted by
usage_percentage, mirroring how EdgeCloudSim's IdleActiveLoadGenerator
actually assigns an app type per device."""

import random
from collections import Counter

from mec_core import config
from mec_core.scenario import (APP_PROFILES, DEFAULT_ENV_PROFILE, ENV_PROFILES,
                              Scenario, sample_scenario, sample_scenario_pool,
                              sample_table2_profile)


def test_four_app_profiles_matching_applications_xml():
    names = {p.name for p in APP_PROFILES}
    assert names == {"AUGMENTED_REALITY", "HEALTH_APP", "HEAVY_COMP_APP", "INFOTAINMENT_APP"}


def test_usage_percentages_sum_to_100():
    assert sum(p.usage_percentage for p in APP_PROFILES) == 100


def test_heavy_comp_app_task_length_exceeds_old_table_ii_range():
    # The old Table II-sampled range topped out at 10000 -- HEAVY_COMP_APP's
    # real mean (45000) was never reachable before this fix, understating
    # exactly the kind of long, expensive task the paper's own env's
    # applications.xml actually generates.
    heavy = next(p for p in APP_PROFILES if p.name == "HEAVY_COMP_APP")
    assert heavy.task_length == 45000


def test_sample_scenario_picks_one_of_the_real_profiles():
    scenario = sample_scenario(random.Random(1), source="apps")
    assert scenario.app_profile in APP_PROFILES
    assert 200 <= scenario.number_of_mobile_devices <= 2000


def test_table2_profile_samples_within_paper_ranges_with_edge_cloud_coupling():
    rng = random.Random(3)
    r = config.TABLE_II_RANGES
    for _ in range(200):
        p = sample_table2_profile(rng)
        assert p.name == "TABLE_II"
        assert r["usage_percentage"][0] <= p.usage_percentage <= r["usage_percentage"][1]
        assert r["poisson_interarrival"][0] <= p.poisson_interarrival <= r["poisson_interarrival"][1]
        assert r["delay_sensitivity"][0] <= p.delay_sensitivity <= r["delay_sensitivity"][1]
        assert r["active_period"][0] <= p.active_period <= r["active_period"][1]
        assert r["idle_period"][0] <= p.idle_period <= r["idle_period"][1]
        assert r["data_upload"][0] <= p.data_upload <= r["data_upload"][1]
        assert r["data_download"][0] <= p.data_download <= r["data_download"][1]
        assert r["task_length"][0] <= p.task_length <= r["task_length"][1]
        assert p.required_core == 1
        assert r["vm_utilization_on_cloud"][0] <= p.vm_utilization_on_cloud <= r["vm_utilization_on_cloud"][1]
        assert r["vm_utilization_on_mobile"][0] <= p.vm_utilization_on_mobile <= r["vm_utilization_on_mobile"][1]
        # Section V-A-1: "we fix vm_utilization_on_edge to be ten times
        # vm_utilization_on_cloud"
        assert abs(p.vm_utilization_on_edge - 10.0 * p.vm_utilization_on_cloud) < 1e-9


def test_scenario_sources_select_the_right_profile_kind():
    rng = random.Random(4)
    assert sample_scenario(rng, source="table2").app_profile.name == "TABLE_II"
    assert sample_scenario(rng, source="apps").app_profile in APP_PROFILES


def test_mixed_pool_contains_both_table2_and_real_app_scenarios():
    scenarios = sample_scenario_pool(200, seed=5, source="mixed")
    names = {s.app_profile.name for s in scenarios}
    assert "TABLE_II" in names
    assert names & {p.name for p in APP_PROFILES}


def test_env_profiles_cover_the_deployment_environments():
    names = {e.name for e in ENV_PROFILES}
    assert names == {"DEFAULT", "HIGH_MOBILITY", "CONGESTED_WAN", "WEAK_EDGE",
                     "MMWAVE_5G", "NTN_BACKHAUL", "GPU_EDGE",
                     "SDV_HIGHWAY", "SDV_RURAL_ROAD", "SDV_URBAN"}
    # each named preset must actually differ from DEFAULT on its defining axis
    by_name = {e.name: e for e in ENV_PROFILES}
    assert by_name["HIGH_MOBILITY"].mobility_dwell_time < by_name["DEFAULT"].mobility_dwell_time
    assert by_name["CONGESTED_WAN"].wan_bandwidth_mbps < by_name["DEFAULT"].wan_bandwidth_mbps
    assert by_name["WEAK_EDGE"].edge_vm_mips < by_name["DEFAULT"].edge_vm_mips
    assert by_name["MMWAVE_5G"].wlan_bandwidth_mbps > by_name["DEFAULT"].wlan_bandwidth_mbps
    assert by_name["MMWAVE_5G"].mobility_dwell_time < by_name["HIGH_MOBILITY"].mobility_dwell_time
    assert by_name["NTN_BACKHAUL"].wan_propagation_delay > by_name["DEFAULT"].wan_propagation_delay
    assert by_name["GPU_EDGE"].edge_vm_mips > by_name["DEFAULT"].edge_vm_mips
    # Eq. 2-4 comparability: mobility failures exist only where they're the
    # profile's point (see the DEFAULT profile comment)
    for name in ("DEFAULT", "CONGESTED_WAN", "WEAK_EDGE", "NTN_BACKHAUL", "GPU_EDGE"):
        assert by_name[name].mobility_dwell_time == float("inf")
    # SDV driving contexts: highway hands off fastest, rural slowest of the
    # three; urban MEC is the strongest edge, rural roadside the weakest
    highway, rural, urban = (by_name["SDV_HIGHWAY"], by_name["SDV_RURAL_ROAD"],
                             by_name["SDV_URBAN"])
    assert highway.mobility_dwell_time < urban.mobility_dwell_time < rural.mobility_dwell_time
    assert rural.edge_vm_mips < by_name["DEFAULT"].edge_vm_mips < urban.edge_vm_mips
    assert rural.wan_bandwidth_mbps < by_name["DEFAULT"].wan_bandwidth_mbps
    assert highway.wlan_bandwidth_mbps > by_name["DEFAULT"].wlan_bandwidth_mbps


def test_sdv_profiles_mirror_applications_sdv_xml():
    from mec_core.scenario import SDV_APP_PROFILES

    names = {p.name for p in SDV_APP_PROFILES}
    assert names == {"ADAS_PERCEPTION", "HD_MAP_UPDATE", "DRIVER_MONITORING",
                     "OTA_SOFTWARE_UPDATE"}
    assert sum(p.usage_percentage for p in SDV_APP_PROFILES) == 100
    by_name = {p.name: p for p in SDV_APP_PROFILES}
    for p in SDV_APP_PROFILES:
        assert 0.0 <= p.delay_sensitivity <= 1.0
        assert p.required_core == 1
        assert abs(p.vm_utilization_on_edge - 10.0 * p.vm_utilization_on_cloud) < 1e-9
        assert p.max_delay_requirement != float("inf")  # every SDV workload carries an SLA
    # the workload shape sanity: ADAS is the hard-SLA continuous stream,
    # OTA the delay-tolerant download burst
    assert by_name["ADAS_PERCEPTION"].max_delay_requirement <= 1.0
    assert by_name["OTA_SOFTWARE_UPDATE"].delay_sensitivity < 0.1
    assert by_name["OTA_SOFTWARE_UPDATE"].data_download > by_name["OTA_SOFTWARE_UPDATE"].data_upload
    assert by_name["HD_MAP_UPDATE"].data_download > by_name["HD_MAP_UPDATE"].data_upload


def test_sdv_scenario_source():
    from mec_core.scenario import SDV_APP_PROFILES

    rng = random.Random(7)
    assert sample_scenario(rng, source="sdv").app_profile in SDV_APP_PROFILES
    pool = sample_scenario_pool(300, seed=7, source="mixed")
    names = {s.app_profile.name for s in pool}
    assert names & {p.name for p in SDV_APP_PROFILES}, "mixed pool never drew an SDV workload"


def test_nextgen_profiles_mirror_applications_nextgen_xml():
    from mec_core.scenario import NEXTGEN_APP_PROFILES

    names = {p.name for p in NEXTGEN_APP_PROFILES}
    assert names == {"GENAI_INFERENCE", "XR_STREAMING", "V2X_PERCEPTION",
                     "SMART_CITY_ANALYTICS"}
    assert sum(p.usage_percentage for p in NEXTGEN_APP_PROFILES) == 100
    for p in NEXTGEN_APP_PROFILES:
        assert 0.0 <= p.delay_sensitivity <= 1.0
        assert p.required_core == 1
        # the edge = 10 x cloud utilization convention holds here too
        assert abs(p.vm_utilization_on_edge - 10.0 * p.vm_utilization_on_cloud) < 1e-9
    genai = next(p for p in NEXTGEN_APP_PROFILES if p.name == "GENAI_INFERENCE")
    heavy = next(p for p in APP_PROFILES if p.name == "HEAVY_COMP_APP")
    assert genai.task_length > heavy.task_length  # GenAI is the new heaviest workload


def test_nextgen_scenario_source():
    from mec_core.scenario import NEXTGEN_APP_PROFILES

    rng = random.Random(6)
    assert sample_scenario(rng, source="nextgen").app_profile in NEXTGEN_APP_PROFILES
    pool = sample_scenario_pool(300, seed=6, source="mixed")
    names = {s.app_profile.name for s in pool}
    assert names & {p.name for p in NEXTGEN_APP_PROFILES}, "mixed pool never drew a nextgen workload"


def test_scenario_env_profile_defaults_to_default_profile():
    # Backward compat: everything that builds a Scenario without naming an
    # env profile (tests, compare_algorithms' replace()) gets DEFAULT.
    scenario = Scenario(app_profile=APP_PROFILES[0], number_of_mobile_devices=500)
    assert scenario.env_profile is DEFAULT_ENV_PROFILE


def test_sample_scenario_pool_env_mix_roughly_matches_weights():
    scenarios = sample_scenario_pool(2000, seed=2)
    counts = Counter(s.env_profile.name for s in scenarios)
    total_weight = sum(e.weight for e in ENV_PROFILES)
    for profile in ENV_PROFILES:
        observed_pct = 100 * counts.get(profile.name, 0) / len(scenarios)
        expected_pct = 100 * profile.weight / total_weight
        assert abs(observed_pct - expected_pct) < 5, (
            f"{profile.name}: expected ~{expected_pct}%, got {observed_pct:.1f}%"
        )


def test_sample_scenario_pool_app_mix_roughly_matches_usage_percentage():
    scenarios = sample_scenario_pool(2000, seed=1, source="apps")
    counts = Counter(s.app_profile.name for s in scenarios)
    total = sum(counts.values())
    for profile in APP_PROFILES:
        observed_pct = 100 * counts.get(profile.name, 0) / total
        # generous tolerance -- this is a statistical sanity check, not a
        # precise distribution match
        assert abs(observed_pct - profile.usage_percentage) < 5, (
            f"{profile.name}: expected ~{profile.usage_percentage}%, got {observed_pct:.1f}%"
        )
