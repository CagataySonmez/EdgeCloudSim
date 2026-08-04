from dataclasses import replace

from mec_core import config
from mec_core.env import MECOffloadEnv, _SATURATION_CEILING
from mec_core.scenario import DEFAULT_ENV_PROFILE, AppProfile, Scenario


def _make_scenario(number_of_mobile_devices=1, poisson_interarrival=10.0,
                    vm_utilization_on_mobile=10.0, vm_utilization_on_edge=10.0,
                    vm_utilization_on_cloud=1.0, task_length=3000.0,
                    delay_sensitivity=0.5, env_profile=None):
    profile = AppProfile(
        name="TEST_APP", usage_percentage=100.0,
        poisson_interarrival=poisson_interarrival,
        delay_sensitivity=delay_sensitivity, active_period=30.0, idle_period=30.0,
        data_upload=200.0, data_download=200.0, task_length=task_length,
        required_core=1,
        vm_utilization_on_edge=vm_utilization_on_edge,
        vm_utilization_on_cloud=vm_utilization_on_cloud,
        vm_utilization_on_mobile=vm_utilization_on_mobile,
    )
    # A no-mobility variant of DEFAULT keeps every pre-existing test's
    # behavior deterministic w.r.t. mobility (dwell=inf disables it).
    if env_profile is None:
        env_profile = replace(DEFAULT_ENV_PROFILE, mobility_dwell_time=float("inf"))
    return Scenario(app_profile=profile, number_of_mobile_devices=number_of_mobile_devices,
                    env_profile=env_profile)


def test_reset_returns_correct_state_dim():
    env = MECOffloadEnv(_make_scenario(), seed=1)
    state = env.reset()
    assert state.shape == (config.STATE_DIM,)


def test_device_tier_has_zero_network_delay():
    # A single-device scenario (no background contention) with low mobile
    # utilization per task should never vm_fail on the device tier either,
    # isolating the delay component.
    env = MECOffloadEnv(_make_scenario(number_of_mobile_devices=1, vm_utilization_on_mobile=5.0), seed=2)
    env.reset()
    _, reward, _, info = env.step(0)  # action 0 = device
    assert not info["failed"]
    assert info["delay"] == 0.0


def test_device_tier_reward_is_pure_processing_time():
    env = MECOffloadEnv(_make_scenario(number_of_mobile_devices=1, vm_utilization_on_mobile=5.0,
                                        task_length=4000.0), seed=3)
    env.reset()
    # _sample_task() draws task_length from an exponential distribution
    # around the app profile's mean, so read back the actual sampled
    # length step() will use rather than assuming it equals the profile's
    # mean task_length exactly.
    actual_task_length = env.current_task.length
    _, reward, _, info = env.step(0)
    expected_process_time = actual_task_length / config.MOBILE_VM_MIPS
    assert abs(info["service_time"] - expected_process_time) < 1e-6
    # reward = -(w * T + energy penalty); local execution has no cost and
    # no SLA deadline (inf), so only the compute-energy term joins T
    expected_weight = config.DELAY_SENSITIVITY_BASE + 0.5
    expected_energy = config.MOBILE_COMPUTE_POWER_W * expected_process_time
    expected_reward = -(expected_weight * info["service_time"]
                        + config.ENERGY_REWARD_WEIGHT * expected_energy)
    assert abs(reward - expected_reward) < 1e-6


def test_reward_scales_with_delay_sensitivity(monkeypatch):
    # Two envs identical except delay_sensitivity, same seed -> identical
    # task draws -> the reward difference is exactly the weight ratio.
    # Industry terms are sensitivity-independent, so switch them off to
    # isolate the delay-sensitivity scaling.
    monkeypatch.setattr(config, "ENERGY_AWARE_REWARD", False)
    monkeypatch.setattr(config, "COST_AWARE_REWARD", False)
    monkeypatch.setattr(config, "SLA_PENALTY_WEIGHT", 0.0)
    insensitive = MECOffloadEnv(_make_scenario(delay_sensitivity=0.1), seed=7)
    sensitive = MECOffloadEnv(_make_scenario(delay_sensitivity=0.9), seed=7)
    insensitive.reset()
    sensitive.reset()
    _, r_insensitive, _, info_i = insensitive.step(0)
    _, r_sensitive, _, info_s = sensitive.step(0)

    assert not info_i["failed"] and not info_s["failed"]
    assert info_i["service_time"] == info_s["service_time"]  # same seed, same draws
    w_i = config.DELAY_SENSITIVITY_BASE + 0.1
    w_s = config.DELAY_SENSITIVITY_BASE + 0.9
    assert abs(r_sensitive / r_insensitive - w_s / w_i) < 1e-6
    assert r_sensitive < r_insensitive  # same slowness hurts the sensitive app more


def test_failure_penalty_scales_with_delay_sensitivity():
    # vm_utilization_on_mobile > 100 makes the device tier always vm_fail.
    env = MECOffloadEnv(_make_scenario(vm_utilization_on_mobile=150.0,
                                        delay_sensitivity=0.9), seed=8)
    env.reset()
    _, reward, _, info = env.step(0)
    assert info["failed"] and info["vm_fail"]
    expected_weight = config.DELAY_SENSITIVITY_BASE + 0.9
    assert abs(reward - (-expected_weight * (config.TMAX_SECONDS + 1.0))) < 1e-9


def test_background_contention_scales_with_device_count():
    low = MECOffloadEnv(_make_scenario(number_of_mobile_devices=2, poisson_interarrival=5.0), seed=4)
    high = MECOffloadEnv(_make_scenario(number_of_mobile_devices=2000, poisson_interarrival=5.0), seed=4)
    low.reset()
    high.reset()
    for _ in range(20):
        low.step(0)
        high.step(0)
    assert sum(high.mu_edge) > sum(low.mu_edge)
    assert high.mu_cloud > low.mu_cloud


def test_background_contention_never_exceeds_saturation_ceiling():
    env = MECOffloadEnv(_make_scenario(number_of_mobile_devices=2000, poisson_interarrival=1.0), seed=5)
    env.reset()
    for _ in range(50):
        env.step(0)
    assert all(mu <= _SATURATION_CEILING for mu in env.mu_edge)
    assert env.mu_cloud <= _SATURATION_CEILING


def test_high_mobility_fails_offloaded_tasks_but_never_local_ones():
    fast = replace(DEFAULT_ENV_PROFILE, mobility_dwell_time=0.001)  # ~always moves mid-task
    env = MECOffloadEnv(_make_scenario(env_profile=fast, vm_utilization_on_edge=1.0), seed=9)
    env.reset()

    _, _, _, edge_info = env.step(1)  # edge: round trip outlasts the dwell
    assert edge_info["failed"] and edge_info["mobility_fail"]

    _, _, _, device_info = env.step(0)  # local execution never leaves the WLAN
    assert not device_info.get("mobility_fail", False)


def test_infinite_dwell_means_no_mobility_failures():
    static = replace(DEFAULT_ENV_PROFILE, mobility_dwell_time=float("inf"))
    env = MECOffloadEnv(_make_scenario(env_profile=static, vm_utilization_on_edge=1.0), seed=10)
    env.reset()
    for _ in range(30):
        _, _, _, info = env.step(1)
        assert not info.get("mobility_fail", False)


def test_env_profile_drives_bandwidth_and_edge_mips():
    congested = replace(DEFAULT_ENV_PROFILE, wan_bandwidth_mbps=3.0,
                        mobility_dwell_time=float("inf"))
    env = MECOffloadEnv(_make_scenario(env_profile=congested), seed=11)
    env.reset()
    # _bandwidth() degrades by at most 50%, so wan can never exceed the cap
    for _ in range(20):
        wlan, man, wan = env._bandwidth()
        assert wan <= 3.0
        assert wlan <= config.WLAN_BANDWIDTH_MBPS

    strong = replace(DEFAULT_ENV_PROFILE, mobility_dwell_time=float("inf"))
    weak = replace(strong, edge_vm_mips=2500.0)
    strong_env = MECOffloadEnv(_make_scenario(env_profile=strong, vm_utilization_on_edge=1.0), seed=12)
    weak_env = MECOffloadEnv(_make_scenario(env_profile=weak, vm_utilization_on_edge=1.0), seed=12)
    strong_env.reset()
    weak_env.reset()
    _, _, _, strong_info = strong_env.step(1)
    _, _, _, weak_info = weak_env.step(1)
    assert not strong_info["failed"] and not weak_info["failed"]
    # same seed -> same task; a quarter of the MIPS -> 4x the processing time
    strong_process = strong_info["service_time"] - strong_info["delay"]
    weak_process = weak_info["service_time"] - weak_info["delay"]
    assert abs(weak_process / strong_process - config.EDGE_VM_MIPS / 2500.0) < 1e-6


def test_active_idle_cycle_skips_the_idle_window():
    # active=5s, idle=95s: any arrival landing inside the idle window must
    # fast-forward to the next active window, so the clock position within
    # the 100s cycle always stays inside [0, active).
    scenario = _make_scenario(poisson_interarrival=3.0)
    scenario.app_profile.active_period = 5.0
    scenario.app_profile.idle_period = 95.0
    env = MECOffloadEnv(scenario, seed=13)
    env.reset()
    cycle = 100.0
    for _ in range(50):
        env.step(0)
        assert env.clock % cycle < 5.0 + 1e-9


def test_background_rate_honors_duty_cycle():
    # Same profile except idle_period: a device that idles most of the time
    # generates proportionally less background load.
    # device count low enough that neither variant saturates at the
    # _SATURATION_CEILING, so the rate difference stays observable
    busy = _make_scenario(number_of_mobile_devices=150, poisson_interarrival=5.0)
    busy.app_profile.active_period = 60.0
    busy.app_profile.idle_period = 0.0
    lazy = _make_scenario(number_of_mobile_devices=150, poisson_interarrival=5.0)
    lazy.app_profile.active_period = 6.0
    lazy.app_profile.idle_period = 54.0  # 10% duty cycle

    busy_env = MECOffloadEnv(busy, seed=14)
    lazy_env = MECOffloadEnv(lazy, seed=14)
    busy_env.reset()
    lazy_env.reset()
    for _ in range(10):
        busy_env.step(0)
        lazy_env.step(0)
    assert sum(lazy_env.mu_edge) < sum(busy_env.mu_edge)


def test_usage_percentage_scales_background_only_for_table2_profiles():
    # low device count so the unscaled variant stays below the saturation
    # ceiling and the 10x rate difference is visible in mu_edge
    base = _make_scenario(number_of_mobile_devices=150, poisson_interarrival=5.0)
    base.app_profile.active_period = 60.0
    base.app_profile.idle_period = 0.0

    # A TABLE_II profile at 10% usage loads the pools far less than the same
    # profile as a "real" app (usage_percentage is a mix weight there, not a
    # participation fraction).
    table2_profile = replace(base.app_profile, name="TABLE_II", usage_percentage=10.0)
    table2 = Scenario(app_profile=table2_profile,
                      number_of_mobile_devices=150, env_profile=base.env_profile)

    app_env = MECOffloadEnv(base, seed=15)
    table2_env = MECOffloadEnv(table2, seed=15)
    app_env.reset()
    table2_env.reset()
    for _ in range(10):
        app_env.step(0)
        table2_env.step(0)
    assert sum(table2_env.mu_edge) < sum(app_env.mu_edge)


def test_wan_propagation_comes_from_env_profile():
    # NTN-style backhaul: same seed -> identical draws, so the cloud-path
    # delay difference is exactly the propagation-constant difference.
    terrestrial = replace(DEFAULT_ENV_PROFILE, mobility_dwell_time=float("inf"))
    satellite = replace(terrestrial, wan_propagation_delay=0.6)
    t_env = MECOffloadEnv(_make_scenario(env_profile=terrestrial, vm_utilization_on_cloud=1.0), seed=16)
    s_env = MECOffloadEnv(_make_scenario(env_profile=satellite, vm_utilization_on_cloud=1.0), seed=16)
    t_env.reset()
    s_env.reset()
    cloud_action = config.NUM_EDGE_SERVERS + 1
    _, _, _, t_info = t_env.step(cloud_action)
    _, _, _, s_info = s_env.step(cloud_action)
    assert not t_info["failed"] and not s_info["failed"]
    assert abs((s_info["delay"] - t_info["delay"])
               - (0.6 - config.WAN_PROPAGATION_DELAY)) < 1e-9


def test_local_execution_energy_and_cost():
    env = MECOffloadEnv(_make_scenario(vm_utilization_on_mobile=5.0), seed=17)
    env.reset()
    task_length = env.current_task.length
    _, _, _, info = env.step(0)
    assert not info["failed"]
    process_time = task_length / config.MOBILE_VM_MIPS
    # local: CPU power only, no radio, no monetary cost (user's own device)
    assert abs(info["energy_j"] - config.MOBILE_COMPUTE_POWER_W * process_time) < 1e-9
    assert info["cost"] == 0.0


def test_edge_offload_costs_vm_seconds_and_cloud_pays_egress():
    env_e = MECOffloadEnv(_make_scenario(vm_utilization_on_edge=1.0), seed=18)
    env_e.reset()
    _, _, _, edge_info = env_e.step(1)
    assert not edge_info["failed"]
    edge_process = edge_info["service_time"] - edge_info["delay"]
    assert abs(edge_info["cost"] - config.EDGE_COST_PER_CPU_SEC * edge_process) < 1e-9

    env_c = MECOffloadEnv(_make_scenario(vm_utilization_on_cloud=1.0), seed=18)
    env_c.reset()
    download_kb = env_c.current_task.data_download
    _, _, _, cloud_info = env_c.step(config.NUM_EDGE_SERVERS + 1)
    assert not cloud_info["failed"]
    cloud_process = cloud_info["service_time"] - cloud_info["delay"]
    expected = (config.CLOUD_COST_PER_CPU_SEC * cloud_process
                + config.WAN_EGRESS_COST_PER_MB * download_kb / 1000.0)
    assert abs(cloud_info["cost"] - expected) < 1e-9
    assert cloud_info["energy_j"] > 0


def test_sla_violation_flag_and_penalty():
    # deadline well below any achievable local process time -> violated
    scenario = _make_scenario(vm_utilization_on_mobile=5.0, task_length=8000.0)
    scenario.app_profile.max_delay_requirement = 1e-6
    env = MECOffloadEnv(scenario, seed=19)
    env.reset()
    _, reward_violated, _, info = env.step(0)
    assert not info["failed"]
    assert info["sla_violated"]

    # identical draws with no deadline: the reward difference is exactly
    # the SLA penalty for the overshoot
    relaxed = _make_scenario(vm_utilization_on_mobile=5.0, task_length=8000.0)
    env2 = MECOffloadEnv(relaxed, seed=19)
    env2.reset()
    _, reward_ok, _, info2 = env2.step(0)
    assert not info2["sla_violated"]
    overshoot = info["service_time"] - 1e-6
    assert abs((reward_ok - reward_violated) - config.SLA_PENALTY_WEIGHT * overshoot) < 1e-6


def test_industry_flags_off_recover_pure_latency_reward(monkeypatch):
    monkeypatch.setattr(config, "ENERGY_AWARE_REWARD", False)
    monkeypatch.setattr(config, "COST_AWARE_REWARD", False)
    monkeypatch.setattr(config, "SLA_PENALTY_WEIGHT", 0.0)
    env = MECOffloadEnv(_make_scenario(vm_utilization_on_mobile=5.0), seed=20)
    env.reset()
    _, reward, _, info = env.step(0)
    expected_weight = config.DELAY_SENSITIVITY_BASE + 0.5
    assert abs(reward - (-expected_weight * info["service_time"])) < 1e-9


def test_zero_background_devices_is_a_no_op():
    env = MECOffloadEnv(_make_scenario(number_of_mobile_devices=1), seed=6)
    assert env._background_devices == 0
    env.reset()
    for _ in range(20):
        env.step(0)  # device tier never touches mu_edge/mu_cloud
    assert sum(env.mu_edge) == 0.0
    assert env.mu_cloud == 0.0
