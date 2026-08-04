"""Scenario (metadata) sampling.

A Scenario now mirrors how EdgeCloudSim's IdleActiveLoadGenerator actually
assigns work: every mobile device is given exactly ONE app type for the
whole simulation (weighted by that app's usage_percentage), and that
device's tasks are then drawn from exponential distributions parameterized
by that single app's own poisson_interarrival/data_upload/data_download/
task_length -- never a different app type per task, and never the single
homogeneous "one profile for everything" distribution this module used to
sample from Table II's abstract ranges. See APP_PROFILES below.
"""

from dataclasses import dataclass
import random

from . import config


@dataclass
class AppProfile:
    """One application type's task-generation profile -- mirrors one
    <application> entry in scripts/{ReSACO,three_tier}/config/applications.xml
    exactly (both files are identical). Kept in sync by hand here rather
    than parsed from the XML at runtime: simpler, and a long training run
    shouldn't silently start reading a different config file mid-run if
    someone edits applications.xml later. If you change one, change both.
    """

    name: str
    usage_percentage: float
    poisson_interarrival: float
    delay_sensitivity: float
    active_period: float
    idle_period: float
    data_upload: float
    data_download: float
    task_length: float
    required_core: int
    vm_utilization_on_edge: float
    vm_utilization_on_cloud: float
    vm_utilization_on_mobile: float
    # SLA deadline (seconds): applications*.xml's optional
    # max_delay_requirement. A task that succeeds later than this is still
    # delivered but counts as an SLA violation (env.py's sla_violated /
    # config.SLA_PENALTY_WEIGHT). inf = no deadline (e.g. Table II
    # synthetic profiles -- the paper's metadata has no such field).
    max_delay_requirement: float = float("inf")


# Mirrors scripts/ReSACO/config/applications.xml (== scripts/three_tier's,
# verified identical) -- the actual, fixed task mix every real simulation
# run uses, as opposed to Table II's abstract per-scenario ranges this
# module used to sample a single homogeneous task profile from.
APP_PROFILES = [
    AppProfile(
        name="AUGMENTED_REALITY", usage_percentage=30, poisson_interarrival=2,
        delay_sensitivity=0.9, active_period=40, idle_period=20,
        data_upload=1500, data_download=25, task_length=9000, required_core=1,
        vm_utilization_on_edge=6, vm_utilization_on_cloud=0.6, vm_utilization_on_mobile=20, max_delay_requirement=2.0,
    ),
    AppProfile(
        name="HEALTH_APP", usage_percentage=20, poisson_interarrival=3,
        delay_sensitivity=0.7, active_period=45, idle_period=90,
        data_upload=20, data_download=1250, task_length=3000, required_core=1,
        vm_utilization_on_edge=2, vm_utilization_on_cloud=0.2, vm_utilization_on_mobile=10, max_delay_requirement=4.0,
    ),
    AppProfile(
        name="HEAVY_COMP_APP", usage_percentage=20, poisson_interarrival=20,
        delay_sensitivity=0.1, active_period=60, idle_period=120,
        data_upload=2500, data_download=200, task_length=45000, required_core=1,
        vm_utilization_on_edge=30, vm_utilization_on_cloud=3, vm_utilization_on_mobile=50, max_delay_requirement=60.0,
    ),
    AppProfile(
        name="INFOTAINMENT_APP", usage_percentage=30, poisson_interarrival=7,
        delay_sensitivity=0.3, active_period=30, idle_period=45,
        data_upload=25, data_download=1000, task_length=15000, required_core=1,
        vm_utilization_on_edge=10, vm_utilization_on_cloud=1, vm_utilization_on_mobile=25, max_delay_requirement=8.0,
    ),
]


@dataclass(frozen=True)
class EnvProfile:
    """One deployment *environment's* physical conditions -- the axis of
    scenario diversity orthogonal to the app mix: how good the network
    links are, how strong each tier's hardware is, and how fast devices
    move between WLAN cells. Mirrors what the real EdgeCloudSim run
    configures through {<name>.properties, edge_devices*.xml}
    (scripts/ReSACO/config/ has one preset per profile here -- if you
    change one side, change the other).

    mobility_dwell_time is the mean time (seconds, exponentially
    distributed -- matching NomadicMobility's attractiveness-based dwell
    times) a device stays inside one WLAN cell. A task offloaded to
    edge/cloud whose upload+process+download window outlasts the dwell
    fails "due to mobility" exactly like EdgeCloudSim's serving-WLAN check
    at download time; local (device) execution is immune. float("inf")
    disables movement entirely.
    """

    name: str
    weight: float               # sampling weight in sample_scenario()
    wlan_bandwidth_mbps: float
    man_bandwidth_mbps: float
    wan_bandwidth_mbps: float
    edge_vm_mips: float
    cloud_vm_mips: float
    mobility_dwell_time: float
    # fixed propagation of the WAN leg to the cloud (seconds) -- the
    # terrestrial default is config.WAN_PROPAGATION_DELAY; a satellite
    # (NTN) backhaul pays an order of magnitude more regardless of
    # bandwidth. Mirrored by the wan_propagation_delay property in the
    # matching scripts/ReSACO/config preset.
    wan_propagation_delay: float = config.WAN_PROPAGATION_DELAY


# Mirrors scripts/ReSACO/config/: DEFAULT <-> default_config.properties +
# edge_devices.xml; the rest <-> their same-named .properties preset (see
# that directory and simulation.list). DEFAULT's dwell ~= the mean of the
# default attractiveness L1/L2/L3 waiting times (480/300/120s); at 30s of
# simulated time that makes movement rare, which is exactly why it gets a
# dedicated HIGH_MOBILITY profile instead of a per-scenario random range.
ENV_PROFILES = [
    # DEFAULT disables mobility failures entirely (dwell=inf): the paper's
    # failure model (Eq. 2-4) has exactly two failure modes, so runs under
    # the default environment stay directly comparable to it. Mobility is
    # opted into via HIGH_MOBILITY, where it's the whole point.
    EnvProfile(name="DEFAULT", weight=40,
               wlan_bandwidth_mbps=config.WLAN_BANDWIDTH_MBPS,
               man_bandwidth_mbps=config.MAN_BANDWIDTH_MBPS,
               wan_bandwidth_mbps=config.WAN_BANDWIDTH_MBPS,
               edge_vm_mips=config.EDGE_VM_MIPS, cloud_vm_mips=config.CLOUD_VM_MIPS,
               mobility_dwell_time=float("inf")),
    # Fast-moving devices (vehicles, commuters): WLAN cell changes every
    # ~30s on average, so any edge/cloud round trip risks a mobility fail.
    EnvProfile(name="HIGH_MOBILITY", weight=20,
               wlan_bandwidth_mbps=config.WLAN_BANDWIDTH_MBPS,
               man_bandwidth_mbps=config.MAN_BANDWIDTH_MBPS,
               wan_bandwidth_mbps=config.WAN_BANDWIDTH_MBPS,
               edge_vm_mips=config.EDGE_VM_MIPS, cloud_vm_mips=config.CLOUD_VM_MIPS,
               mobility_dwell_time=30.0),
    # Degraded WAN uplink (rural/overloaded backhaul): the cloud is still
    # compute-rich but nearly unreachable for data-heavy tasks.
    EnvProfile(name="CONGESTED_WAN", weight=20,
               wlan_bandwidth_mbps=config.WLAN_BANDWIDTH_MBPS,
               man_bandwidth_mbps=config.MAN_BANDWIDTH_MBPS,
               wan_bandwidth_mbps=3.0,
               edge_vm_mips=config.EDGE_VM_MIPS, cloud_vm_mips=config.CLOUD_VM_MIPS,
               mobility_dwell_time=float("inf")),
    # Under-provisioned edge tier (cheap micro-datacenters): edge VMs at a
    # quarter of the default MIPS, so raw compute favors device/cloud while
    # network delay still favors edge -- a genuine trade-off.
    EnvProfile(name="WEAK_EDGE", weight=10,
               wlan_bandwidth_mbps=config.WLAN_BANDWIDTH_MBPS,
               man_bandwidth_mbps=config.MAN_BANDWIDTH_MBPS,
               wan_bandwidth_mbps=config.WAN_BANDWIDTH_MBPS,
               edge_vm_mips=2500.0, cloud_vm_mips=config.CLOUD_VM_MIPS,
               mobility_dwell_time=float("inf")),
    # 5G mmWave small-cell MEC: an order of magnitude more radio bandwidth
    # (5x WLAN, ~3x WAN backhaul), but tiny cell footprints mean handoffs
    # every ~10s -- huge pipes that punish any long-running offload, the
    # defining tension of mmWave MEC deployments.
    EnvProfile(name="MMWAVE_5G", weight=15,
               wlan_bandwidth_mbps=1000.0,
               man_bandwidth_mbps=config.MAN_BANDWIDTH_MBPS,
               wan_bandwidth_mbps=50.0,
               edge_vm_mips=config.EDGE_VM_MIPS, cloud_vm_mips=config.CLOUD_VM_MIPS,
               mobility_dwell_time=10.0),
    # Non-terrestrial (LEO satellite) backhaul, 3GPP NTN-style rural
    # deployment: usable WAN bandwidth but ~0.6s of propagation to the
    # cloud regardless -- the cloud's compute is fine, its *latency* is
    # structurally bad, so the edge tier carries delay-sensitive work.
    EnvProfile(name="NTN_BACKHAUL", weight=10,
               wlan_bandwidth_mbps=config.WLAN_BANDWIDTH_MBPS,
               man_bandwidth_mbps=config.MAN_BANDWIDTH_MBPS,
               wan_bandwidth_mbps=30.0,
               edge_vm_mips=config.EDGE_VM_MIPS, cloud_vm_mips=config.CLOUD_VM_MIPS,
               mobility_dwell_time=float("inf"),
               wan_propagation_delay=0.6),
    # GPU/NPU-accelerated edge (AI-first micro-datacenters): edge VMs at
    # 4x the default MIPS, narrowing the edge-vs-cloud compute gap -- the
    # inverse of WEAK_EDGE, and the hardware trend GenAI inference at the
    # edge is driving.
    EnvProfile(name="GPU_EDGE", weight=5,
               wlan_bandwidth_mbps=config.WLAN_BANDWIDTH_MBPS,
               man_bandwidth_mbps=config.MAN_BANDWIDTH_MBPS,
               wan_bandwidth_mbps=config.WAN_BANDWIDTH_MBPS,
               edge_vm_mips=40000.0, cloud_vm_mips=config.CLOUD_VM_MIPS,
               mobility_dwell_time=float("inf")),
    # --- SDV (software-defined vehicle) driving environments. The radio
    # tier reads as C-V2X/5G roadside units (RSUs); "edge" is the roadside
    # MEC the vehicle is currently attached to. The three profiles span
    # the canonical driving contexts: ---
    # Highway: 100+ km/h past sparse RSU corridors -- decent per-RSU radio
    # (300 Mbps) and fiber backhaul along the road (WAN 30), but coverage
    # dwell of only ~8s per RSU, so any long round trip risks the handoff.
    EnvProfile(name="SDV_HIGHWAY", weight=10,
               wlan_bandwidth_mbps=300.0,
               man_bandwidth_mbps=config.MAN_BANDWIDTH_MBPS,
               wan_bandwidth_mbps=30.0,
               edge_vm_mips=config.EDGE_VM_MIPS, cloud_vm_mips=config.CLOUD_VM_MIPS,
               mobility_dwell_time=8.0),
    # Rural/arterial road: moderate speed (dwell ~45s) but thin
    # infrastructure -- weaker radio (100 Mbps), a poor backhaul (WAN 8)
    # and half-strength roadside edge compute. The hard case for ADAS
    # SLAs: neither the edge nor the cloud is comfortably fast.
    EnvProfile(name="SDV_RURAL_ROAD", weight=10,
               wlan_bandwidth_mbps=100.0,
               man_bandwidth_mbps=config.MAN_BANDWIDTH_MBPS,
               wan_bandwidth_mbps=8.0,
               edge_vm_mips=5000.0, cloud_vm_mips=config.CLOUD_VM_MIPS,
               mobility_dwell_time=45.0),
    # Dense urban: stop-and-go traffic under dense small cells -- strong
    # radio (400 Mbps) and well-provisioned city MEC (2x edge MIPS), but
    # small-cell footprints still mean ~25s dwells, and the device-count
    # axis piles vehicles+pedestrians onto the same shared cells.
    EnvProfile(name="SDV_URBAN", weight=10,
               wlan_bandwidth_mbps=400.0,
               man_bandwidth_mbps=config.MAN_BANDWIDTH_MBPS,
               wan_bandwidth_mbps=config.WAN_BANDWIDTH_MBPS,
               edge_vm_mips=20000.0, cloud_vm_mips=config.CLOUD_VM_MIPS,
               mobility_dwell_time=25.0),
]

DEFAULT_ENV_PROFILE = ENV_PROFILES[0]


# Next-generation edge workloads (2025-era trends), mirroring
# scripts/ReSACO/config/applications_nextgen.xml exactly (same
# keep-in-sync-by-hand contract as APP_PROFILES <-> applications.xml).
# These are NOT mixed into APP_PROFILES: the classic four stay the default
# deployment workload, and the nextgen set is its own scenario source /
# its own simulation.list rows, so results remain separable.
NEXTGEN_APP_PROFILES = [
    # On-device-triggered GenAI inference offload (LLM/diffusion serving at
    # the edge): compact prompt/context upload, small result, but the
    # heaviest compute of any profile -- barely runnable on the device.
    AppProfile(
        name="GENAI_INFERENCE", usage_percentage=25, poisson_interarrival=5,
        delay_sensitivity=0.6, active_period=45, idle_period=30,
        data_upload=300, data_download=60, task_length=60000, required_core=1,
        vm_utilization_on_edge=40, vm_utilization_on_cloud=4, vm_utilization_on_mobile=80, max_delay_requirement=10.0,
    ),
    # XR / metaverse streaming: bidirectionally heavy (pose+video up,
    # rendered frames down), continuous, and maximally delay-sensitive --
    # motion-to-photon budgets leave no slack.
    AppProfile(
        name="XR_STREAMING", usage_percentage=30, poisson_interarrival=2,
        delay_sensitivity=1.0, active_period=60, idle_period=15,
        data_upload=1800, data_download=2500, task_length=12000, required_core=1,
        vm_utilization_on_edge=15, vm_utilization_on_cloud=1.5, vm_utilization_on_mobile=35, max_delay_requirement=1.5,
    ),
    # V2X cooperative perception: near-continuous sensor-frame uploads
    # (camera/lidar crops) needing instant fused results back; pairs
    # naturally with HIGH_MOBILITY/MMWAVE_5G environments.
    AppProfile(
        name="V2X_PERCEPTION", usage_percentage=25, poisson_interarrival=1,
        delay_sensitivity=1.0, active_period=120, idle_period=5,
        data_upload=1500, data_download=100, task_length=6000, required_core=1,
        vm_utilization_on_edge=8, vm_utilization_on_cloud=0.8, vm_utilization_on_mobile=25, max_delay_requirement=1.0,
    ),
    # Smart-city video/IoT analytics: batched detections and aggregations,
    # throughput-oriented and delay-tolerant -- the batch counterweight
    # that keeps the nextgen mix from being uniformly latency-critical.
    AppProfile(
        name="SMART_CITY_ANALYTICS", usage_percentage=20, poisson_interarrival=10,
        delay_sensitivity=0.2, active_period=60, idle_period=60,
        data_upload=900, data_download=40, task_length=20000, required_core=1,
        vm_utilization_on_edge=12, vm_utilization_on_cloud=1.2, vm_utilization_on_mobile=30, max_delay_requirement=60.0,
    ),
]


def sample_table2_profile(rng: random.Random = random) -> AppProfile:
    """Samples one synthetic app profile from the paper's Table II ranges
    ("each scenario is generated by randomizing these parameters within the
    specified intervals"), honoring Section V-A-1's stated coupling
    vm_utilization_on_edge = 10 x vm_utilization_on_cloud (only the cloud
    range is sampled; edge is derived). required_core is fixed at 1.

    This is the paper's own scenario diversity: continuous per-scenario
    parameter variation, as opposed to APP_PROFILES' four fixed real app
    types. Both matter -- Table II covers the space between/around the real
    apps (e.g. any delay_sensitivity in [0,1], any task size in its range),
    while APP_PROFILES guarantees the exact conditions the deployment
    simulation generates (including HEAVY_COMP_APP's task_length=45000,
    which Table II's own range never reaches). See SCENARIO_SOURCE_WEIGHTS.
    """
    r = config.TABLE_II_RANGES
    vm_cloud = rng.uniform(*r["vm_utilization_on_cloud"])
    return AppProfile(
        name="TABLE_II",
        usage_percentage=rng.uniform(*r["usage_percentage"]),
        poisson_interarrival=rng.uniform(*r["poisson_interarrival"]),
        delay_sensitivity=rng.uniform(*r["delay_sensitivity"]),
        active_period=rng.uniform(*r["active_period"]),
        idle_period=rng.uniform(*r["idle_period"]),
        data_upload=rng.uniform(*r["data_upload"]),
        data_download=rng.uniform(*r["data_download"]),
        task_length=rng.uniform(*r["task_length"]),
        required_core=1,
        vm_utilization_on_edge=10.0 * vm_cloud,
        vm_utilization_on_cloud=vm_cloud,
        vm_utilization_on_mobile=rng.uniform(*r["vm_utilization_on_mobile"]),
    )


# SDV (software-defined vehicle) workloads, mirroring
# scripts/ReSACO/config/applications_sdv.xml (same keep-in-sync-by-hand
# contract). One vehicle runs one dominant workload per scenario --
# usage_percentage is the fleet mix. Designed to pair with the SDV_*
# environment profiles, though sampling keeps the axes independent.
SDV_APP_PROFILES = [
    # Sensor-fusion offload for ADAS/AD: continuous camera/lidar crops up,
    # fused objects back, hard sub-second SLA -- the workload that decides
    # whether roadside MEC is viable at all.
    AppProfile(
        name="ADAS_PERCEPTION", usage_percentage=35, poisson_interarrival=1,
        delay_sensitivity=1.0, active_period=300, idle_period=10,
        data_upload=1200, data_download=80, task_length=7000, required_core=1,
        vm_utilization_on_edge=8, vm_utilization_on_cloud=0.8, vm_utilization_on_mobile=30,
        max_delay_requirement=1.0,
    ),
    # HD-map tile refresh + localization assist: download-dominated,
    # moderately delay-sensitive (stale tiles degrade, not crash).
    AppProfile(
        name="HD_MAP_UPDATE", usage_percentage=25, poisson_interarrival=5,
        delay_sensitivity=0.6, active_period=60, idle_period=30,
        data_upload=100, data_download=2000, task_length=4000, required_core=1,
        vm_utilization_on_edge=5, vm_utilization_on_cloud=0.5, vm_utilization_on_mobile=15,
        max_delay_requirement=3.0,
    ),
    # In-cabin driver monitoring analytics: small frames, light compute --
    # the profile most plausibly served on the vehicle itself.
    AppProfile(
        name="DRIVER_MONITORING", usage_percentage=20, poisson_interarrival=3,
        delay_sensitivity=0.8, active_period=300, idle_period=10,
        data_upload=200, data_download=20, task_length=2500, required_core=1,
        vm_utilization_on_edge=3, vm_utilization_on_cloud=0.3, vm_utilization_on_mobile=12,
        max_delay_requirement=2.0,
    ),
    # OTA software/feature updates -- the defining SDV workload: rare,
    # download-massive, nearly delay-insensitive background bursts.
    AppProfile(
        name="OTA_SOFTWARE_UPDATE", usage_percentage=20, poisson_interarrival=30,
        delay_sensitivity=0.05, active_period=20, idle_period=300,
        data_upload=50, data_download=2500, task_length=8000, required_core=1,
        vm_utilization_on_edge=6, vm_utilization_on_cloud=0.6, vm_utilization_on_mobile=20,
        max_delay_requirement=120.0,
    ),
]


# How sample_scenario picks each scenario's task profile:
#   "table2"  -- a fresh Table II-sampled synthetic profile (the paper's own
#                metadata construction, Section V-A-1)
#   "apps"    -- one of the four real applications.xml profiles, weighted by
#                usage_percentage (what the deployment simulation actually runs)
#   "nextgen" -- one of the NEXTGEN_APP_PROFILES (GenAI/XR/V2X/smart-city),
#                weighted by usage_percentage, mirroring
#                applications_nextgen.xml
#   "sdv"     -- one of the SDV_APP_PROFILES (ADAS/HD-map/DMS/OTA vehicle
#                workloads), weighted by usage_percentage, mirroring
#                applications_sdv.xml
# The default mix trains on all four: paper-faithful continuous diversity,
# the classic deployment workload, the next-generation extremes, and the
# vehicular fleet mix.
SCENARIO_SOURCE_WEIGHTS = {"table2": 35, "apps": 35, "nextgen": 15, "sdv": 15}


@dataclass
class Scenario:
    """A single deployment condition sampled for the Outer Loop: which of
    the four real app types this simulated device runs (app_profile), how
    many other devices (number_of_mobile_devices) are contending for the
    same shared edge/cloud pools (see env.py's background-load injection),
    and which physical environment (env_profile: network quality, tier
    hardware, device mobility) it all runs in. Note the env_profile is
    deliberately NOT part of the agent's state vector -- it's a
    scenario-level latent condition, exactly the kind of environmental
    variation Reptile is supposed to meta-learn across and Algorithm 4's
    online adaptation is supposed to specialize to."""

    app_profile: AppProfile
    number_of_mobile_devices: int
    env_profile: EnvProfile = DEFAULT_ENV_PROFILE


def sample_scenario(rng: random.Random = random, source: str = "mixed",
                    env_names=None) -> Scenario:
    """Samples one meta-training scenario: a task profile (see `source`),
    an EnvProfile weighted by its own weight, and a random device count
    (Table II's range).

    source: "table2" (paper's Table II randomization), "apps" (the four
    real applications.xml profiles weighted by usage_percentage),
    "nextgen" (the NEXTGEN_APP_PROFILES trend workloads), "sdv" (the
    SDV_APP_PROFILES vehicle workloads), or "mixed" (default -- picks per
    scenario via SCENARIO_SOURCE_WEIGHTS).

    env_names: optionally restrict the environment pool to these profile
    names (e.g. ["DEFAULT"] for a paper-exact run where scenarios vary
    only Table II's parameters, never the physical environment).
    """
    r = config.METADATA_RANGES
    if source == "mixed":
        names = list(SCENARIO_SOURCE_WEIGHTS)
        source = rng.choices(names, weights=[SCENARIO_SOURCE_WEIGHTS[n] for n in names], k=1)[0]
    if source == "table2":
        app_profile = sample_table2_profile(rng)
    elif source == "apps":
        weights = [p.usage_percentage for p in APP_PROFILES]
        app_profile = rng.choices(APP_PROFILES, weights=weights, k=1)[0]
    elif source == "nextgen":
        weights = [p.usage_percentage for p in NEXTGEN_APP_PROFILES]
        app_profile = rng.choices(NEXTGEN_APP_PROFILES, weights=weights, k=1)[0]
    elif source == "sdv":
        weights = [p.usage_percentage for p in SDV_APP_PROFILES]
        app_profile = rng.choices(SDV_APP_PROFILES, weights=weights, k=1)[0]
    else:
        raise ValueError(f"unknown scenario source {source!r} (use table2/apps/nextgen/sdv/mixed)")
    env_pool = ENV_PROFILES
    if env_names is not None:
        env_pool = [e for e in ENV_PROFILES if e.name in set(env_names)]
        if not env_pool:
            raise ValueError(f"no ENV_PROFILES match {env_names!r}")
    env_profile = rng.choices(env_pool, weights=[e.weight for e in env_pool], k=1)[0]
    return Scenario(
        app_profile=app_profile,
        number_of_mobile_devices=int(rng.uniform(*r["number_of_mobile_devices"])),
        env_profile=env_profile,
    )


def sample_scenario_pool(num_scenarios: int, seed: int = None, source: str = "mixed",
                         env_names=None):
    rng = random.Random(seed)
    return [sample_scenario(rng, source=source, env_names=env_names)
            for _ in range(num_scenarios)]
