"""Lightweight three-layer MEC offloading environment.

This reimplements the state/action/reward/failure model described in the
ReSACO paper (Section III "System Model" and Section IV-A-2 "Inner Loop")
as a compact, self-contained simulator. It is used to train (meta-train and
adapt) the SAC agent quickly without needing the full CloudSim event engine.
The physical tier parameters (VM MIPS/cores, bandwidths) mirror
EdgeCloudSim's scripts/three_tier config so behaviour stays consistent with
the Java simulator this agent will later be plugged into; per-scenario
deviations from those defaults (degraded WAN, weak edge hardware, fast-
moving devices) come from the scenario's EnvProfile (scenario.py), each of
which mirrors one config preset under scripts/ReSACO/config/.

State:  s_t = (L, U, D, mu_d, mu_e1..mu_eN, mu_c, b_wlan, b_man, b_wan)
Action: a_t in {0..N+1}: 0 = device, 1..N = edge servers, N+1 = cloud
Reward: r_t = -w*T on success (Eq. 9), -w*(Tmax+1) on failure, where
        w = DELAY_SENSITIVITY_BASE + the app's delay_sensitivity -- see
        the weighting comment in step().
"""

import math
import random
from dataclasses import dataclass

import numpy as np

from . import config
from .scenario import Scenario


# Ceiling background-load injection saturates a tier's utilization at, so
# a heavily-contended scenario reads as "clearly over capacity" without the
# raw state feature growing unbounded as device count climbs into the
# thousands (see _inject_background_load).
_SATURATION_CEILING = 150.0


@dataclass
class Task:
    length: float          # L, million instructions (MI)
    data_upload: float     # U, Kb
    data_download: float   # D, Kb
    required_core: int


class MECOffloadEnv:
    """One "MD" generating tasks against N edge servers + 1 cloud + itself.

    Tier utilization is tracked as the *average* VM utilization (0-100)
    across that tier node's VMs, matching what EdgeCloudSim's
    getAvgUtilization()/per-host averages report and what
    ReSACOStateBuilder sends at deployment. A task needs
    vm_utilization_on_X percent of ONE VM, so admitting it raises a
    server's average by required/VMS_PER_EDGE_SERVER (cloud:
    required/CLOUD_VM_COUNT) for the duration of its processing time --
    i.e. each edge server's aggregate budget is 8 VMs x 100% and the
    cloud's is 4 x 100%, per the paper's Section V-A-2 device
    configuration, with least-loaded VM placement approximated by
    admitting whenever the average leaves room for the per-VM
    requirement. (Previously each server modeled a single VM, silently
    shrinking edge capacity 8x and cloud capacity 4x versus the paper.)
    The single-VM mobile device is unchanged.
    """

    def __init__(self, scenario: Scenario, num_edge_servers: int = config.NUM_EDGE_SERVERS,
                 seed: int = None):
        self.scenario = scenario
        self.n_edge = num_edge_servers
        self.rng = random.Random(seed)

        # utilization[0] = single mobile VM, utilization[1..N] = one
        # representative VM per edge server (least-loaded proxy), utilization[-1] = cloud
        self.mu_mobile = 0.0
        self.mu_edge = [0.0] * self.n_edge
        self.mu_cloud = 0.0

        # (utilization_delta, release_time) queues so occupied capacity is
        # freed once a task's processing time elapses.
        self.clock = 0.0
        self._pending_release = []  # list of (release_time, layer, index, delta)

        self.current_task: Task = None
        self._current_state = None

        # Background contention: the scenario's other (number_of_mobile_devices - 1)
        # devices also send tasks into the same shared edge/cloud utilization
        # pools this env's single agent-controlled stream targets. Without
        # this, number_of_mobile_devices would only ever affect training
        # through the poisson_interarrival scaling scripts/compare_algorithms.py
        # applies (which just polls the agent faster -- it never actually
        # raises shared-pool utilization on its own), so a low- and a
        # high-device-count scenario would look almost identical to the
        # agent. Injected every step as a Poisson-sampled batch of admitted
        # background tasks (see _inject_background_load), so mu_edge/mu_cloud
        # -- which the agent does observe in its state -- rise with device
        # count exactly the way real contention would.
        self._background_devices = max(self.scenario.number_of_mobile_devices - 1, 0)

        # The edge server whose WLAN cell this device currently sits in
        # (drawn per episode in reset()): offloading there needs only the
        # WLAN leg; any *other* edge server additionally relays over the MAN
        # (EdgeCloudSim's neighbor-edge path). The serving index is
        # deliberately NOT in the state -- the real deployment's state
        # vector doesn't carry it either, so the policy must learn
        # placement robust to that blindness rather than exploit an
        # unavailable signal.
        self.serving_edge = 0

        # Empirical mix of the orchestrator's own recent decisions
        # (device/edge/cloud), used to split background devices' offloads
        # instead of an invented fixed 80/20: the paper's orchestrator
        # decides for *every* MD, so background traffic should follow the
        # same policy the agent is currently executing. Seeded with an
        # edge-priority-style prior so the very first steps aren't
        # degenerate; real decisions quickly dominate.
        self._action_mix = {"device": 0.0, "edge": 8.0, "cloud": 2.0}

    # ------------------------------------------------------------------
    def _sample_task(self) -> Task:
        # Exponentially distributed around this scenario's app_profile
        # means, matching EdgeCloudSim's IdleActiveLoadGenerator exactly
        # (ExponentialDistribution(mean) for input size / output size /
        # task length) -- not a bounded uniform jitter, which understates
        # the real long-tailed variance real task sizes have.
        p = self.scenario.app_profile
        length = self.rng.expovariate(1.0 / max(p.task_length, 1.0))
        upload = self.rng.expovariate(1.0 / max(p.data_upload, 1.0))
        download = self.rng.expovariate(1.0 / max(p.data_download, 1.0))
        return Task(length=max(length, 1.0), data_upload=max(upload, 1.0),
                    data_download=max(download, 1.0), required_core=p.required_core)

    def _active_device_estimate(self) -> float:
        """Expected number of devices concurrently in their active window,
        honoring the profile's duty cycle and (for Table II profiles) its
        usage_percentage participation fraction -- the same scaling
        _inject_background_load applies to compute contention."""
        p = self.scenario.app_profile
        cycle = p.active_period + p.idle_period
        duty_cycle = (p.active_period / cycle) if cycle > 0 else 1.0
        usage_fraction = p.usage_percentage / 100.0 if p.name == "TABLE_II" else 1.0
        return self.scenario.number_of_mobile_devices * duty_cycle * usage_fraction

    def _bandwidth(self) -> tuple:
        """Sample current available bandwidth (Mbps), degraded by load.

        Base capacities come from the scenario's environment profile (e.g.
        CONGESTED_WAN caps the WAN link); each link is then divided by a
        contention factor that grows with the number of concurrently
        active devices sharing it -- the same per-client bandwidth
        division EdgeCloudSim's MM1 network model produces, so a
        2,000-device scenario genuinely sees longer network delays than a
        200-device one (paper Fig. 6(c)). WLAN contention is per access
        point (active devices spread over n_edge cells); the WAN is one
        shared uplink loaded by the cloud-bound fraction of decisions;
        the MAN carries only inter-edge relays, so it scales at half the
        per-AP rate. The residual [0.5, 1.0] jitter models short-term
        fading on top of the load-driven mean.
        """
        ep = self.scenario.env_profile
        active = self._active_device_estimate()
        per_ap = active / max(self.n_edge, 1)
        mix_total = sum(self._action_mix.values())
        cloud_frac = self._action_mix["cloud"] / mix_total if mix_total > 0 else 0.2
        c = config.BANDWIDTH_CONTENTION_PER_CLIENT
        wlan_divisor = 1.0 + c * per_ap
        man_divisor = 1.0 + c * per_ap * 0.5
        wan_divisor = 1.0 + c * active * cloud_frac
        wlan = ep.wlan_bandwidth_mbps / wlan_divisor * (1.0 - 0.5 * self.rng.random())
        man = ep.man_bandwidth_mbps / man_divisor * (1.0 - 0.5 * self.rng.random())
        wan = ep.wan_bandwidth_mbps / wan_divisor * (1.0 - 0.5 * self.rng.random())
        return wlan, man, wan

    def _release_expired(self):
        still_pending = []
        for release_time, layer, index, delta in self._pending_release:
            if release_time <= self.clock:
                if layer == "mobile":
                    self.mu_mobile = max(0.0, self.mu_mobile - delta)
                elif layer == "edge":
                    self.mu_edge[index] = max(0.0, self.mu_edge[index] - delta)
                elif layer == "cloud":
                    self.mu_cloud = max(0.0, self.mu_cloud - delta)
            else:
                still_pending.append((release_time, layer, index, delta))
        self._pending_release = still_pending

    def _build_state(self, task: Task, bw) -> np.ndarray:
        wlan, man, wan = bw
        return np.array(
            [task.length, task.data_upload, task.data_download, self.mu_mobile]
            + list(self.mu_edge)
            + [self.mu_cloud, wlan, man, wan],
            dtype=np.float32,
        )

    def reset(self) -> np.ndarray:
        self.clock = 0.0
        self.mu_mobile = 0.0
        self.mu_edge = [0.0] * self.n_edge
        self.mu_cloud = 0.0
        self._pending_release = []
        self.serving_edge = self.rng.randrange(self.n_edge) if self.n_edge > 0 else 0
        self.current_task = self._sample_task()
        bw = self._bandwidth()
        self._current_bw = bw
        self._current_state = self._build_state(self.current_task, bw)
        return self._current_state

    # ------------------------------------------------------------------
    def step(self, action: int):
        """Apply the offloading decision for the current task, return
        (next_state, reward, done, info)."""
        self._release_expired()
        task = self.current_task
        p = self.scenario.app_profile
        ep = self.scenario.env_profile
        wlan, man, wan = self._current_bw

        if action == 0:
            layer, index = "mobile", 0
            # the mobile device is a single VM: the task's per-VM requirement
            # is charged (and admission-checked) at full value
            mu_delta = p.vm_utilization_on_mobile
            mu_current = self.mu_mobile
            mips = config.MOBILE_VM_MIPS
            delay = 0.0  # local execution: no data ever leaves the device
            self._action_mix["device"] += 1.0
        elif 1 <= action <= self.n_edge:
            layer, index = "edge", action - 1
            # per-VM requirement spread over the server's VMS_PER_EDGE_SERVER
            # VMs: mu_edge tracks the server's average VM utilization, so
            # admission fails only when the whole server (8 x 100%) is out of
            # room for one more per-VM slot -- see the class docstring
            mu_delta = p.vm_utilization_on_edge / config.VMS_PER_EDGE_SERVER
            mu_current = self.mu_edge[index]
            mips = ep.edge_vm_mips
            # WLAN reaches the serving edge directly; any other edge server
            # is a neighbor relay that additionally crosses the MAN
            # (EdgeCloudSim's REQUEST_RECEIVED_BY_EDGE_DEVICE_TO_RELAY path).
            # WLAN/MAN carry no WAN propagation constant -- the MAN leg pays
            # the internal LAN delay instead (Section III-A: WLAN to edge;
            # the WAN's multi-segment latency belongs to the cloud path).
            delay = self._transfer_delay(task, wlan, propagation=0.0)
            if index != self.serving_edge:
                delay += self._transfer_delay(task, man, propagation=config.LAN_INTERNAL_DELAY)
            self._action_mix["edge"] += 1.0
        else:
            layer, index = "cloud", 0
            mu_delta = p.vm_utilization_on_cloud / config.CLOUD_VM_COUNT
            mu_current = self.mu_cloud
            mips = ep.cloud_vm_mips
            # WAN propagation comes from the env profile: terrestrial
            # backhaul pays the 0.1s default, an NTN/satellite backhaul
            # (NTN_BACKHAUL profile) pays its own much larger constant.
            delay = (self._transfer_delay(task, wlan, propagation=0.0)
                     + self._transfer_delay(task, wan, propagation=ep.wan_propagation_delay))
            self._action_mix["cloud"] += 1.0

        network_fail = delay > config.TMAX_SECONDS
        vm_fail = (mu_current + mu_delta) > 100.0

        # Delay-sensitivity reward weighting: an AR task (sensitivity 0.9)
        # is hurt by a slow tier ~2.3x more than a HEAVY_COMP batch job
        # (0.1), so the same service time should cost proportionally more
        # reward. Both the success and failure branches scale by the same
        # weight, preserving each app's internal success-vs-failure ordering
        # while letting delay-sensitive apps dominate the gradient exactly
        # where tier choice matters most. Mirrored in Java by
        # ReSACOMobileDeviceManager.reportReSACOOutcome() -- the bridge's
        # online-learning rewards must be on the training scale.
        # DELAY_SENSITIVITY_REWARD=False recovers the paper's Eq. (9) exactly.
        if config.DELAY_SENSITIVITY_REWARD:
            weight = config.DELAY_SENSITIVITY_BASE + p.delay_sensitivity
        else:
            weight = 1.0

        if network_fail or vm_fail:
            reward = -weight * (config.TMAX_SECONDS + 1.0)
            done_info = {"failed": True, "network_fail": network_fail, "vm_fail": vm_fail,
                         "mobility_fail": False}
            service_time = None
        else:
            process_time = task.length / mips  # T_process(i) ~= L_i / mu*  (paper Eq. after (1))
            service_time = process_time + delay
            # Mobility failure (offloaded tiers only): the device leaves its
            # serving WLAN cell before the result comes back with probability
            # 1 - exp(-window/dwell) -- the exponential-dwell analogue of
            # EdgeCloudSim's failedDueToMobility check (serving WLAN at
            # download time != WLAN at submission). The chosen VM still burns
            # its capacity for the full process time: the work happened, only
            # the delivery failed -- exactly like the real simulator, where
            # the cloudlet completes and the download is what gets dropped.
            mobility_fail = (
                layer != "mobile"
                and ep.mobility_dwell_time != float("inf")
                and self.rng.random() < 1.0 - math.exp(-service_time / ep.mobility_dwell_time)
            )
            self._occupy(layer, index, mu_delta, process_time)
            if mobility_fail:
                reward = -weight * (config.TMAX_SECONDS + 1.0)
                done_info = {"failed": True, "network_fail": False, "vm_fail": False,
                             "mobility_fail": True}
                service_time = None
            else:
                reward = -weight * service_time

                # Industry-direction objectives (see config.py's block):
                # energy (green computing), monetary cost (FinOps), and SLA
                # deadline adherence enter the reward as seconds-equivalent
                # penalties on top of the (delay-weighted) service time.
                # Mirrored in Java by ReSACOStateBuilder.industryPenalty().
                energy_j = self._task_energy(layer, task, process_time, wlan)
                cost = self._task_cost(layer, task, process_time)
                sla_violated = service_time > p.max_delay_requirement
                if config.ENERGY_AWARE_REWARD:
                    reward -= config.ENERGY_REWARD_WEIGHT * energy_j
                if config.COST_AWARE_REWARD:
                    reward -= config.COST_REWARD_WEIGHT * cost
                if config.SLA_PENALTY_WEIGHT and sla_violated:
                    reward -= config.SLA_PENALTY_WEIGHT * (service_time - p.max_delay_requirement)

                done_info = {"failed": False, "service_time": service_time, "delay": delay,
                             "energy_j": energy_j, "cost": cost, "sla_violated": sla_violated}

        # advance the environment clock by the per-task Poisson inter-arrival time
        elapsed = self.rng.expovariate(1.0 / max(p.poisson_interarrival, 0.1))
        self.clock += elapsed

        # Active/idle cycling (Table II's active_period/idle_period, matching
        # EdgeCloudSim's IdleActiveLoadGenerator): the device only generates
        # tasks during its active window. If this arrival would land inside
        # the idle window, fast-forward the clock to the next active window --
        # capacity keeps draining (release times are absolute) and background
        # devices keep loading the pools for the full skipped span, so the
        # agent's next decision sees a genuinely "rested" own device against
        # background contention that never slept.
        cycle = p.active_period + p.idle_period
        if p.idle_period > 0 and cycle > 0:
            pos = self.clock % cycle
            if pos >= p.active_period:
                idle_skip = cycle - pos
                self.clock += idle_skip
                elapsed += idle_skip

        self._inject_background_load(elapsed)

        self.current_task = self._sample_task()
        self._current_bw = self._bandwidth()
        next_state = self._build_state(self.current_task, self._current_bw)
        self._current_state = next_state

        return next_state, reward, False, done_info

    def _inject_background_load(self, elapsed: float):
        """Admits a Poisson-sampled batch of background tasks (from the
        scenario's other devices) straight into the edge/cloud pools,
        occupying capacity for their own process time exactly like an
        agent-admitted task. Batched into at most `n_edge + 1` _occupy()
        calls per step (not one call per background device) so cost stays
        independent of device count; the batch size itself still scales
        with it, which is what actually drives up mu_edge/mu_cloud.

        Background offloads follow the orchestrator's own current policy:
        the split between device/edge/cloud comes from the empirical mix of
        the agent's recent decisions (self._action_mix) -- the paper's
        orchestrator decides for every MD, so the other devices' traffic
        should look like the policy being executed, not an invented fixed
        80/20. Background tasks the mix assigns to "device" run on their
        own devices and never touch the shared pools; the edge share is
        spread evenly across the N edge servers so no single server absorbs
        every other device's load.

        Uses this scenario's own app_profile as a stand-in for "typical"
        background-device characteristics (poisson_interarrival, task
        length, vm utilization) rather than a population-wide weighted
        average across all four APP_PROFILES -- a simplification, since
        real background devices would run a mix of app types too, not all
        the same one as the controlled device.
        """
        if self._background_devices <= 0 or elapsed <= 0:
            return
        p = self.scenario.app_profile
        # Background devices also honor the profile's active/idle duty cycle
        # (a device that idles 2/3 of the time generates 1/3 of the tasks) --
        # matching IdleActiveLoadGenerator, where every device alternates
        # active/idle windows rather than generating tasks continuously.
        cycle = p.active_period + p.idle_period
        duty_cycle = (p.active_period / cycle) if cycle > 0 else 1.0
        # Table II's usage_percentage ("how broadly tasks are utilized by
        # MDs"): for a synthetic Table II profile only that fraction of the
        # other devices runs this task type at all. The four real app
        # profiles instead use usage_percentage as their population mix
        # weight (every real device runs *some* app -- see scenario.py), so
        # scaling by it there would undercount total background load.
        usage_fraction = p.usage_percentage / 100.0 if p.name == "TABLE_II" else 1.0
        effective_devices = self._background_devices * usage_fraction
        rate = effective_devices * duty_cycle / max(p.poisson_interarrival, 0.1)

        mix_total = sum(self._action_mix.values())
        edge_frac = self._action_mix["edge"] / mix_total
        cloud_frac = self._action_mix["cloud"] / mix_total

        # Only arrivals still *in flight* at the decision instant occupy
        # capacity: a background task arriving more than its own process
        # time before "now" has already completed and released. So each
        # tier's injection window is min(elapsed, that tier's process
        # time) -- Little's-law-consistent in-flight load (rate x
        # min(window, service time)) instead of dumping the entire elapsed
        # window's arrivals at once, which over-counted precisely when the
        # active/idle fast-forward made `elapsed` span a long idle gap.
        ep = self.scenario.env_profile

        # A real, already-saturated server rejects excess arrivals rather
        # than piling up unbounded utilization debt -- cap injected load at
        # SATURATION_CEILING so a heavily-contended tier reads as "clearly
        # over capacity" without further inflating the raw state feature
        # (and destabilizing the critic) as device count climbs into the
        # thousands.
        if edge_frac > 0 and self.n_edge > 0:
            process_time = p.task_length / ep.edge_vm_mips
            window = min(elapsed, process_time)
            edge_share = self._poisson_sample(rate * edge_frac * window)
            if edge_share > 0:
                per_server = edge_share / self.n_edge
                # each background task raises its server's *average* VM
                # utilization by required/VMS_PER_EDGE_SERVER, same as an
                # agent-admitted task (see the class docstring)
                desired = per_server * p.vm_utilization_on_edge / config.VMS_PER_EDGE_SERVER
                for index in range(self.n_edge):
                    room = max(0.0, _SATURATION_CEILING - self.mu_edge[index])
                    delta = min(desired, room)
                    if delta > 0:  # zero-delta occupies only bloat the release list
                        self._occupy("edge", index, delta, process_time)

        if cloud_frac > 0:
            process_time = p.task_length / ep.cloud_vm_mips
            window = min(elapsed, process_time)
            cloud_share = self._poisson_sample(rate * cloud_frac * window)
            if cloud_share > 0:
                desired = cloud_share * p.vm_utilization_on_cloud / config.CLOUD_VM_COUNT
                room = max(0.0, _SATURATION_CEILING - self.mu_cloud)
                delta = min(desired, room)
                if delta > 0:
                    self._occupy("cloud", 0, delta, process_time)

    def _poisson_sample(self, lam: float) -> int:
        """Knuth's algorithm for small lambda; normal approximation for
        large lambda (avoids the exponential-time blowup Knuth's algorithm
        hits once lam gets into the hundreds/thousands, which happens at
        the paper's upper device-count range)."""
        if lam <= 0:
            return 0
        if lam > 30:
            return max(0, int(round(self.rng.gauss(lam, math.sqrt(lam)))))
        limit = math.exp(-lam)
        k, p = 0, 1.0
        while True:
            k += 1
            p *= self.rng.random()
            if p <= limit:
                return k - 1

    def _occupy(self, layer, index, mu_required, process_time):
        release_time = self.clock + process_time
        self._pending_release.append((release_time, layer, index, mu_required))
        if layer == "mobile":
            self.mu_mobile += mu_required
        elif layer == "edge":
            self.mu_edge[index] += mu_required
        elif layer == "cloud":
            self.mu_cloud += mu_required

    def _task_energy(self, layer: str, task: Task, process_time: float,
                     wlan_mbps: float) -> float:
        """Joules attributable to this task: local execution burns the
        device CPU; an offload burns device radio TX for the WLAN leg plus
        the chosen tier's active per-VM power for the processing window
        (edge micro-DCs cost more energy per unit of work than hyperscaler
        cloud VMs), and a cloud task additionally pays core-network WAN
        transport energy per Mbit."""
        if layer == "mobile":
            return config.MOBILE_COMPUTE_POWER_W * process_time
        wlan_time = self._transfer_delay(task, wlan_mbps, propagation=0.0)
        data_mbit = (task.data_upload + task.data_download) / 1000.0 * 8.0
        energy = config.WLAN_TX_POWER_W * wlan_time
        if layer == "edge":
            energy += config.EDGE_VM_POWER_W * process_time
        else:
            energy += (config.CLOUD_VM_POWER_W * process_time
                       + config.WAN_TRANSPORT_ENERGY_PER_MBIT * data_mbit)
        return energy

    @staticmethod
    def _task_cost(layer: str, task: Task, process_time: float) -> float:
        """Monetary cost (abstract units, FinOps view): the device is the
        user's own hardware, edge VM-seconds are priced per
        edge_devices.xml's costPerSec, cloud VM-seconds are cheaper but the
        result download pays the classic per-MB WAN egress fee."""
        if layer == "mobile":
            return 0.0
        if layer == "edge":
            return config.EDGE_COST_PER_CPU_SEC * process_time
        return (config.CLOUD_COST_PER_CPU_SEC * process_time
                + config.WAN_EGRESS_COST_PER_MB * task.data_download / 1000.0)

    @staticmethod
    def _transfer_delay(task: Task, bandwidth_mbps: float, propagation: float = 0.0) -> float:
        """Transfer time for the task's upload+download over one link plus
        that link's fixed propagation constant -- 0 for WLAN (EdgeCloudSim
        adds none), LAN_INTERNAL_DELAY for MAN relays, and
        WAN_PROPAGATION_DELAY for the WAN leg only. (A previous version
        charged the 0.1s WAN constant on every link, taxing edge offloads
        with 20x the LAN constant and double-counting it on the cloud
        path.)"""
        if bandwidth_mbps <= 0:
            return float("inf")
        data_mbit = (task.data_upload + task.data_download) / 1000.0 * 8.0
        return propagation + data_mbit / bandwidth_mbps
