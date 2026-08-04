"""Deployment Phase (Algorithm 4): rapid adaptation of a trained parameter
to a live environment, plus a "frozen" (inference-only) variant for
on-policy baselines that don't have a well-defined single-transition
online-update rule.

These wrap the object the Java-side inference bridge
(bridge/inference_server.py) uses at runtime -- one instance per served
algorithm (ReSACO, SAC baseline, DDPG baseline, A2C baseline, A3C baseline).
"""

import copy

import torch

from mec_core import config

# In-flight decision correlation entries whose OUTCOME never arrives (e.g.
# tasks still airborne when a simulation run's clock is cut off) would
# otherwise accumulate forever in a long-lived bridge process; past this
# many, the oldest are evicted (dicts preserve insertion order).
_MAX_PENDING = 20_000


class DeploymentAgent:
    """Wraps any off-policy agent -- anything exposing
    select_action(state, greedy), a .replay_buffer with .push(...), and
    .update() (SACAgent and DDPGAgent both qualify) -- for live serving
    with online learning (Algorithm 4): copy trained params in, then keep
    adapting from real reported outcomes via an incremental update per
    transition.

    Without `save_path`, theta_adapt only ever lives in memory -- every bit
    of online adaptation is lost the moment the bridge process restarts,
    which defeats the point of Algorithm 4 being "online" at all. Passing
    `save_path` (+ `autosave_every`) makes report_outcome() persist
    theta_adapt back to disk every N successful updates, and `save()` can
    also be called directly (e.g. from a shutdown handler) to flush
    whatever's been learned so far.
    """

    def __init__(self, agent, params: dict = None, save_path: str = None,
                 autosave_every: int = 50):
        self.agent = agent
        if params is not None:
            self.agent.load_params(params)  # theta* -> theta_adapt (line 1)
        # kept so reset() can re-run Algorithm 4 line 1 ("copy theta* into
        # theta_adapt") for a *new* scenario S_new without a process restart
        self._initial_params = copy.deepcopy(params) if params is not None else None
        self._pending = {}  # correlate an in-flight decision with its later outcome
        self.save_path = save_path
        self.autosave_every = autosave_every
        self._updates_since_save = 0

    def select_action(self, state, request_id, greedy: bool = False) -> int:
        action = self.agent.select_action(state, greedy=greedy)
        self._pending[request_id] = (state, action)
        while len(self._pending) > _MAX_PENDING:
            self._pending.pop(next(iter(self._pending)))
        return action

    def reset(self) -> bool:
        """Algorithm 4 line 1 for a new scenario: reload the original
        trained parameter (theta_star as loaded at construction), drop all
        accumulated online adaptation, the replay buffer, and in-flight
        correlation state. Returns False when the agent was constructed
        without params (nothing to reset back to)."""
        if self._initial_params is None:
            return False
        self.agent.load_params(copy.deepcopy(self._initial_params))
        self.agent.replay_buffer.clear()
        self._pending.clear()
        self._updates_since_save = 0
        return True

    def report_outcome(self, request_id, reward: float, next_state, done: bool = False,
                        min_buffer_before_update: int = config.BATCH_SIZE):
        """Called once a task's real outcome (success/failure, service time)
        is known. Stores the transition and triggers an incremental
        SAC-Update-style step, i.e. the online part of Algorithm 4. Every
        `autosave_every` updates (if `save_path` was given), the adapted
        parameters are flushed to disk so a crash or restart only loses at
        most that many updates' worth of progress instead of all of it.

        Returns None if request_id is unknown (nothing to do -- e.g. this
        decision was never actually made through select_action, or its
        outcome was already reported once). Otherwise returns a dict with
        "recorded": True and an "update" key holding the update result (or
        None if the replay buffer isn't full enough yet to update) --
        callers must check "recorded", not truthiness of the whole result,
        since a recorded-but-not-yet-updated outcome is still real work done.
        """
        if request_id not in self._pending:
            return None
        state, action = self._pending.pop(request_id)
        self.agent.replay_buffer.push(state, action, reward, next_state, float(done))
        update_result = None
        if len(self.agent.replay_buffer) >= min_buffer_before_update:
            update_result = self.agent.update()
            self._updates_since_save += 1
            if self.save_path and self.autosave_every and self._updates_since_save >= self.autosave_every:
                self.save()
        return {"recorded": True, "update": update_result}

    def save(self) -> bool:
        """Flushes theta_adapt to `self.save_path`. Returns False (no-op)
        if no save_path was configured."""
        if not self.save_path:
            return False
        torch.save(self.agent.get_params(), self.save_path)
        self._updates_since_save = 0
        return True

    def state_dict(self):
        return self.agent.get_params()


class FrozenPolicyAgent:
    """Wraps an on-policy agent (A2CAgent, including the A3C-trained
    global network, which is saved in A2CAgent-compatible form) for
    inference-only serving. On-policy methods don't have a natural
    single-transition online-update rule the way off-policy methods do
    (their gradient estimator needs an on-policy rollout, not an
    arbitrarily-delayed, possibly-out-of-order outcome callback from the
    simulator), so report_outcome here is a no-op: the served policy stays
    exactly as trained by scripts/train_baselines.py.
    """

    def __init__(self, agent, params: dict = None):
        self.agent = agent
        if params is not None:
            self.agent.load_params(params)
        self._seen = set()  # request ids we actually decided, for accurate IGNORED reporting

    def select_action(self, state, request_id, greedy: bool = True) -> int:
        if len(self._seen) > _MAX_PENDING:
            # orphaned ids from cut-off simulation runs; sets are unordered,
            # so shed them wholesale (worst case: a few stale OUTCOMEs answer
            # IGNORED, which is what they deserve anyway)
            self._seen.clear()
        self._seen.add(request_id)
        return self.agent.select_action(state, greedy=greedy)

    def reset(self) -> bool:
        """A frozen policy has no online adaptation to roll back -- only
        the request-correlation state is dropped. Present so callers can
        treat every agent uniformly (mirrors DeploymentAgent.reset())."""
        self._seen.clear()
        return True

    def report_outcome(self, request_id, reward: float, next_state, done: bool = False):
        if request_id not in self._seen:
            return None
        self._seen.discard(request_id)
        return {"recorded": False, "update": None}

    def save(self) -> bool:
        """Never anything to persist -- the served policy never changes
        after training. Present only so callers can treat every agent
        uniformly (e.g. a shutdown handler calling .save() on all of them)."""
        return False

    def state_dict(self):
        return self.agent.get_params()
