"""Hyperparameters and metadata ranges for ReSACO (Table II, Section V-A/V-B)."""

# ----------------------------------------------------------------------------
# Device / Edge / Cloud tier configuration (Section V-A-2, matches
# EdgeCloudSim's scripts/three_tier/config/default_config.properties and
# edge_devices.xml so the trained policy is consistent with the simulator).
# ----------------------------------------------------------------------------
#
# The *_VM_CORES values below are descriptive only: they record the paper's
# device configuration (and mirror edge_devices.xml / default_config.properties)
# but nothing in this package consumes them, because every app profile's
# required_core is 1, so core count never binds admission. env.py admits on
# VM *utilization* instead. They are kept so the mirror stays complete --
# don't go looking for where they are read.
NUM_EDGE_SERVERS = 10
VMS_PER_EDGE_SERVER = 8

MOBILE_VM_MIPS = 4000
MOBILE_VM_CORES = 1

EDGE_VM_MIPS = 10000
EDGE_VM_CORES = 2

CLOUD_VM_COUNT = 4
CLOUD_VM_MIPS = 100000
CLOUD_VM_CORES = 4

WLAN_BANDWIDTH_MBPS = 200
WAN_BANDWIDTH_MBPS = 15
MAN_BANDWIDTH_MBPS = 200
WAN_PROPAGATION_DELAY = 0.1
LAN_INTERNAL_DELAY = 0.005

# Per-concurrent-client bandwidth contention (env._bandwidth): each link's
# capacity is divided by (1 + this * concurrently-active clients sharing
# it), the lightweight analogue of EdgeCloudSim's per-client MM1 bandwidth
# division -- it's what makes a 2,000-device scenario actually see longer
# network delays than a 200-device one (paper Fig. 6(c)).
BANDWIDTH_CONTENTION_PER_CLIENT = 0.02

# Delay threshold used by the network failure indicator F_network (Eq. 2).
TMAX_SECONDS = 5.0

# Reward weighting by app delay sensitivity: reward is scaled by
# (DELAY_SENSITIVITY_BASE + delay_sensitivity), so a delay-sensitive app
# (AR, 0.9 -> x1.4) penalizes slow service ~2.3x harder than an
# insensitive one (HEAVY_COMP, 0.1 -> x0.6). The base keeps even the most
# insensitive app's reward non-degenerate (completion still matters).
# Mirrored on the Java side by ReSACOStateBuilder.RESACO_DELAY_SENSITIVITY_BASE
# (the bridge's OUTCOME rewards must be on the same scale the policy was
# trained on) -- if you change one, change both.
#
# DELAY_SENSITIVITY_REWARD = False recovers the paper's Eq. (9) exactly
# (reward = -T / -(Tmax+1), unweighted). It too is mirrored in Java
# (ReSACOStateBuilder.RESACO_DELAY_SENSITIVITY_REWARD) -- flip both or
# neither, then retrain: a policy trained on one reward scale must not be
# served/online-adapted against the other.
DELAY_SENSITIVITY_REWARD = True
DELAY_SENSITIVITY_BASE = 0.5

# ----------------------------------------------------------------------------
# Industry-direction objectives (all deviations from the paper's pure-latency
# Eq. (9); each has an off switch, and all three are mirrored in Java by
# ReSACOStateBuilder.industryPenalty() -- change both sides or neither, then
# retrain). Reward becomes
#   r = -(w * T + ENERGY_REWARD_WEIGHT * E + COST_REWARD_WEIGHT * C
#         + SLA_PENALTY_WEIGHT * max(0, T - deadline))          on success,
# with E in joules and C in EdgeCloudSim-style abstract cost units, so the
# weights express "seconds-equivalent per joule / per cost unit".
# ----------------------------------------------------------------------------

# --- Energy (green computing / the AI power crunch) ---
# Device-side: active compute power for local execution, radio TX power for
# the WLAN leg of any offload. Infrastructure-side: active per-VM power at
# the edge (micro-DCs run hot per unit of work) vs. the cloud (hyperscaler
# efficiency, but WAN transport energy per Mbit on top).
ENERGY_AWARE_REWARD = True
ENERGY_REWARD_WEIGHT = 0.02          # seconds-equivalent per joule
MOBILE_COMPUTE_POWER_W = 4.0
WLAN_TX_POWER_W = 1.3
EDGE_VM_POWER_W = 30.0
CLOUD_VM_POWER_W = 90.0
WAN_TRANSPORT_ENERGY_PER_MBIT = 0.05  # core-network transport, J/Mbit

# --- Cost (FinOps) ---
# The device is the user's own hardware (free); edge VM-seconds are priced
# like edge_devices.xml's costPerSec, cloud VM-seconds cheaper (economies of
# scale) but cloud results pay the classic per-MB WAN egress fee on the
# download leg.
COST_AWARE_REWARD = True
COST_REWARD_WEIGHT = 0.05            # seconds-equivalent per cost unit
EDGE_COST_PER_CPU_SEC = 3.0          # matches edge_devices.xml costPerSec
CLOUD_COST_PER_CPU_SEC = 1.0
WAN_EGRESS_COST_PER_MB = 0.1

# --- SLA / SLO (per-app deadlines) ---
# Apps carry a max_delay_requirement (applications*.xml's optional field,
# mirrored on AppProfile); a task that *succeeds* but blows its deadline is
# still delivered (not failed) yet counts as an SLA violation and pays a
# penalty proportional to how late it was. Weight 0 turns the deadline into
# a pure metric.
SLA_PENALTY_WEIGHT = 1.0

# ----------------------------------------------------------------------------
# Metadata ranges used to build randomized training scenarios.
#
# TABLE_II_RANGES reproduces the paper's Table II exactly ("each scenario is
# generated by randomizing these parameters within the specified intervals"),
# including the stated coupling vm_utilization_on_edge = 10 x
# vm_utilization_on_cloud (so only the cloud range is sampled and edge is
# derived). scenario.py samples synthetic app profiles from these ranges for
# the paper-faithful scenario source, alongside the four real
# applications.xml profiles (APP_PROFILES) the deployment simulation actually
# runs -- see scenario.py's SCENARIO_SOURCES for how the two mix.
# number_of_mobile_devices applies to every source: it's a genuinely free
# environmental condition no config file fixes.
# ----------------------------------------------------------------------------
TABLE_II_RANGES = {
    "usage_percentage": (10, 30),
    "poisson_interarrival": (5, 20),
    "delay_sensitivity": (0.0, 1.0),
    "active_period": (10, 60),
    "idle_period": (10, 60),
    "data_upload": (25, 2500),
    "data_download": (25, 2500),
    "task_length": (1500, 10000),
    # required_core is fixed at 1 (Table II)
    "vm_utilization_on_cloud": (0.2, 2.0),   # edge = 10 x cloud (Section V-A-1)
    "vm_utilization_on_mobile": (10, 50),
}

METADATA_RANGES = {
    "number_of_mobile_devices": (200, 2000),
}

# ----------------------------------------------------------------------------
# State / action space (Section IV-A-2)
# state = {L, U, D, mu_d, mu_e1..mu_eN, mu_c, b_wlan, b_man, b_wan}
# action in {0, ..., N+1}: 0 = device, 1..N = edge servers, N+1 = cloud
# ----------------------------------------------------------------------------
STATE_DIM = 4 + NUM_EDGE_SERVERS + 1 + 3  # L,U,D,mu_d + mu_e(N) + mu_c + bwlan,bman,bwan
ACTION_DIM = NUM_EDGE_SERVERS + 2

# ----------------------------------------------------------------------------
# SAC / Reptile hyperparameters (Section V-B-1)
# ----------------------------------------------------------------------------
META_LR = 0.001          # alpha: Reptile meta learning rate
DISCOUNT_GAMMA = 0.99
ENTROPY_TAU = 0.2        # initial entropy temperature (learned online when AUTO_ENTROPY_TUNING)

# Automatic entropy-temperature tuning (Haarnoja et al. 2018's learned-alpha
# extension, discrete form per Christodoulou 2019). The paper's fixed
# tau=0.2 let the policy collapse onto a single action once the critic
# briefly favored it (see README "Convergence"); tuning tau to hold policy
# entropy near TARGET_ENTROPY_SCALE * ln(ACTION_DIM) pushes back exactly
# when the policy gets too deterministic, and backs off as it explores.
AUTO_ENTROPY_TUNING = True
ALPHA_LR = 3e-4
TARGET_ENTROPY_SCALE = 0.6  # target entropy = this * ln(ACTION_DIM) nats
REPLAY_BUFFER_SIZE = 100_000
BATCH_SIZE = 64
TARGET_SOFT_UPDATE_RHO = 0.995
CRITIC_LR = 3e-4
ACTOR_LR = 3e-4

NUM_META_SCENARIOS = 10   # M
NUM_OUTER_ITERATIONS = 300  # K
NUM_INNER_SAC_UPDATES = 50  # N

HIDDEN_SIZES = (128, 128)

# ----------------------------------------------------------------------------
# Baseline-specific hyperparameters (Section V-C). Only ReSACO and the
# SAC-no-meta-init baseline actually use SAC-Update, so only they use the
# SAC hyperparameters above; DDPG and A2C/A3C get their own learning rates
# tuned to what's typical for each algorithm family, instead of blindly
# reusing SAC's ACTOR_LR/CRITIC_LR.
# ----------------------------------------------------------------------------
# DDPG's critic conventionally learns faster than its actor (Lillicrap et
# al. 2015 uses 1e-3 critic / 1e-4 actor) so the critic can track a
# deterministic, faster-moving target.
DDPG_ACTOR_LR = 1e-4
DDPG_CRITIC_LR = 1e-3

# A2C/A3C are on-policy, single-short-rollout-per-update methods (no replay
# buffer to average noise out over) -- a single higher shared actor/critic
# LR is the typical choice for both (used here by both A2C and the A2CAgent
# A3C's global model gets wrapped into).
A2C_LR = 7e-4
