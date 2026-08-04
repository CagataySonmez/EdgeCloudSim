"""Soft Actor-Critic agent implementing SAC-Update (Algorithm 3).

Discrete-action SAC: since the offloading action space is finite, the
expectations in the soft Bellman target (Eq. 10) and the actor objective
(Eq. 12) are computed exactly as probability-weighted sums over the twin
critics' Q-value vectors, instead of Monte-Carlo sampling.

The entropy temperature (the paper's fixed tau) is learned online when
config.AUTO_ENTROPY_TUNING is set: log(alpha) is optimized to hold the
policy's entropy near a target (config.TARGET_ENTROPY_SCALE * ln|A|),
the standard learned-temperature extension (Haarnoja et al. 2018, discrete
form per Christodoulou 2019). With the fixed tau, the policy could collapse
onto one action and stay there -- once collapsed, the entropy bonus is a
constant the actor gradient can't recover from; a learned alpha instead
grows whenever entropy dips below target, actively re-flattening the policy.
"""

import copy
import math

import torch
import torch.nn.functional as F

from mec_core import config
from mec_core.networks import Actor, Critic
from mec_core.normalize import normalize_state
from mec_core.replay_buffer import ReplayBuffer


class SACAgent:
    def __init__(self, state_dim=config.STATE_DIM, action_dim=config.ACTION_DIM,
                 hidden_sizes=config.HIDDEN_SIZES, device="cpu"):
        self.device = torch.device(device)
        self.state_dim = state_dim
        self.action_dim = action_dim

        self.actor = Actor(state_dim, action_dim, hidden_sizes).to(self.device)
        self.critic1 = Critic(state_dim, action_dim, hidden_sizes).to(self.device)
        self.critic2 = Critic(state_dim, action_dim, hidden_sizes).to(self.device)
        self.target_critic1 = copy.deepcopy(self.critic1)
        self.target_critic2 = copy.deepcopy(self.critic2)

        # NB: torch.optim.Adam(foreach=True) was measured here and is both
        # slower on CPU (7.5s vs 4.6s on the 30x50 training benchmark --
        # multi-tensor kernels don't pay off for these small MLPs) and
        # numerically different (max |delta| 5e-5 on the critic weights).
        # The single-tensor default is deliberate; don't "optimize" it.
        self.actor_optim = torch.optim.Adam(self.actor.parameters(), lr=config.ACTOR_LR)
        self.critic_optim = torch.optim.Adam(
            list(self.critic1.parameters()) + list(self.critic2.parameters()),
            lr=config.CRITIC_LR,
        )

        self.gamma = config.DISCOUNT_GAMMA
        self.rho = config.TARGET_SOFT_UPDATE_RHO

        # Parameter lists for the target soft update (Eq. 13), materialized
        # once: load_params()/load_state_dict() write into these same tensors
        # in place, so the references stay valid for the agent's lifetime.
        self._critic_params = (list(self.critic1.parameters())
                               + list(self.critic2.parameters()))
        self._target_params = (list(self.target_critic1.parameters())
                               + list(self.target_critic2.parameters()))

        # Entropy temperature (paper's tau), parameterized as log(alpha) so
        # gradient steps can never push it negative. Learned when
        # AUTO_ENTROPY_TUNING; otherwise stays at its ENTROPY_TAU init.
        self.auto_entropy = config.AUTO_ENTROPY_TUNING
        self.target_entropy = config.TARGET_ENTROPY_SCALE * math.log(action_dim)
        self.log_alpha = torch.tensor(
            math.log(config.ENTROPY_TAU), dtype=torch.float32, device=self.device,
            requires_grad=self.auto_entropy,
        )
        self.alpha_optim = (
            torch.optim.Adam([self.log_alpha], lr=config.ALPHA_LR) if self.auto_entropy else None
        )

        self.replay_buffer = ReplayBuffer(config.REPLAY_BUFFER_SIZE)

    @property
    def alpha(self) -> float:
        return float(self.log_alpha.detach().exp().item())

    # ------------------------------------------------------------------
    # Parameter (de)serialization -- used by the Reptile Outer Loop to copy
    # theta -> theta_k and to apply theta <- theta + alpha*(theta_k - theta)
    # ------------------------------------------------------------------
    def get_params(self):
        return {
            "actor": copy.deepcopy(self.actor.state_dict()),
            "critic1": copy.deepcopy(self.critic1.state_dict()),
            "critic2": copy.deepcopy(self.critic2.state_dict()),
            # log_alpha rides along in the same nested dict shape so the
            # Reptile interpolation (theta + alpha_meta*(theta_k - theta))
            # meta-learns the temperature exactly like every other parameter.
            "alpha": {"log_alpha": self.log_alpha.detach().clone()},
        }

    def load_params(self, params):
        self.actor.load_state_dict(params["actor"])
        self.critic1.load_state_dict(params["critic1"])
        self.critic2.load_state_dict(params["critic2"])
        self.target_critic1.load_state_dict(params["critic1"])
        self.target_critic2.load_state_dict(params["critic2"])
        # Checkpoints saved before auto entropy tuning existed have no
        # "alpha" group -- keep loading them (log_alpha stays at its init).
        if "alpha" in params:
            with torch.no_grad():
                self.log_alpha.copy_(params["alpha"]["log_alpha"])

    # ------------------------------------------------------------------
    def select_action(self, state, greedy: bool = False) -> int:
        state_t = torch.as_tensor(normalize_state(state), dtype=torch.float32, device=self.device).unsqueeze(0)
        with torch.no_grad():
            if greedy:
                action = self.actor.act_greedy(state_t)
            else:
                action = self.actor.sample(state_t)
        return int(action.item())

    # ------------------------------------------------------------------
    def update(self, batch_size: int = config.BATCH_SIZE):
        """One SAC-Update step (Algorithm 3, lines 8-12): sample a
        mini-batch, update critic, update actor, soft-update targets."""
        if len(self.replay_buffer) < batch_size:
            return None

        state, action, reward, next_state, done = self.replay_buffer.sample(batch_size)
        state = torch.as_tensor(normalize_state(state), dtype=torch.float32, device=self.device)
        action = torch.as_tensor(action, dtype=torch.long, device=self.device)
        reward = torch.as_tensor(reward, dtype=torch.float32, device=self.device)
        next_state = torch.as_tensor(normalize_state(next_state), dtype=torch.float32, device=self.device)
        done = torch.as_tensor(done, dtype=torch.float32, device=self.device)

        critic_loss = self._update_critic(state, action, reward, next_state, done)
        # _update_actor hands back the (detached) policy it just scored, so
        # the temperature update reuses that forward pass instead of running
        # the actor over the same batch a third time. This is also the
        # reference formulation (Haarnoja et al. 2018 computes log pi once
        # per update and shares it between the policy and temperature
        # losses); the alpha target is defined against the policy the
        # actor loss was measured on.
        actor_loss, entropy, log_probs, probs = self._update_actor(state)
        alpha_loss = self._update_alpha(probs, log_probs) if self.auto_entropy else None
        self._soft_update_targets()

        return {"critic_loss": critic_loss, "actor_loss": actor_loss,
                "alpha_loss": alpha_loss, "alpha": self.alpha, "entropy": entropy}

    def _update_critic(self, state, action, reward, next_state, done):
        with torch.no_grad():
            next_probs = self.actor.action_probs(next_state)
            next_log_probs = torch.log(next_probs + 1e-8)
            q1_next = self.target_critic1(next_state)
            q2_next = self.target_critic2(next_state)
            q_next = torch.min(q1_next, q2_next)
            # E_{a' ~ pi}[Q(s',a') - alpha * log pi(a'|s')], exact discrete expectation
            alpha = self.log_alpha.exp()
            v_next = (next_probs * (q_next - alpha * next_log_probs)).sum(dim=-1)
            # Eq. (10). The (1-done) factor generalizes it to episodic
            # termination; the paper's continuing task stream never
            # terminates (env.py always returns done=False, and the Java
            # bridge reports done=0), so the factor is inert there but keeps
            # the update correct for any episodic env plugged in later.
            target = reward + self.gamma * (1.0 - done) * v_next

        q1 = self.critic1(state).gather(1, action.unsqueeze(1)).squeeze(1)
        q2 = self.critic2(state).gather(1, action.unsqueeze(1)).squeeze(1)
        loss = F.mse_loss(q1, target) + F.mse_loss(q2, target)  # Eq. (11)

        self.critic_optim.zero_grad()
        loss.backward()
        self.critic_optim.step()
        return float(loss.item())

    def _update_actor(self, state):
        probs = self.actor.action_probs(state)
        log_probs = torch.log(probs + 1e-8)
        with torch.no_grad():
            q1 = self.critic1(state)
            q2 = self.critic2(state)
            q = torch.min(q1, q2)
        # maximize E_{a~pi}[Q(s,a) - alpha*log pi(a|s)]  ==  minimize -(...)  (Eq. 12)
        alpha = self.log_alpha.exp().detach()
        actor_loss = (probs * (alpha * log_probs - q)).sum(dim=-1).mean()

        self.actor_optim.zero_grad()
        actor_loss.backward()
        self.actor_optim.step()
        probs = probs.detach()
        log_probs = log_probs.detach()
        entropy = float(-(probs * log_probs).sum(dim=-1).mean().item())
        return float(actor_loss.item()), entropy, log_probs, probs

    def _update_alpha(self, probs, log_probs):
        """One gradient step on log(alpha): the loss reduces to
        log_alpha * (H_pi - H_target) in expectation, so alpha grows while the
        policy is more deterministic than the target entropy and shrinks once
        it explores more than needed. `probs`/`log_probs` come detached from
        the actor update's forward pass -- only log_alpha carries gradient
        here, so no actor graph is needed."""
        alpha_loss = (probs * (-self.log_alpha * (log_probs + self.target_entropy))).sum(dim=-1).mean()

        self.alpha_optim.zero_grad()
        alpha_loss.backward()
        self.alpha_optim.step()
        return float(alpha_loss.item())

    def _soft_update_targets(self):
        # Eq. (13), psi' <- rho*psi' + (1-rho)*psi, as two batched
        # multi-tensor kernels over both critics' parameters instead of a
        # mul_/add_ pair per tensor (24 kernel launches per update before).
        # Same elementwise ops in the same order, so bit-identical --
        # verified against the pre-change snapshot. Note _foreach_lerp_
        # would NOT be identical (it rounds differently).
        with torch.no_grad():
            torch._foreach_mul_(self._target_params, self.rho)
            torch._foreach_add_(self._target_params, self._critic_params, alpha=1 - self.rho)

    # ------------------------------------------------------------------
    def sac_update_loop(self, env, num_transitions: int, greedy_action: bool = False,
                         batch_size: int = config.BATCH_SIZE):
        """Runs the full SAC-Update transition-collection loop (Algorithm 3):
        interact with `env` for `num_transitions` steps, storing transitions
        and performing one gradient update per step.

        `num_transitions` is meant to be "N inner SAC-Update iterations"
        (Algorithm 2's N) -- i.e. N real gradient steps. update() is a no-op
        until the replay buffer holds at least `batch_size` transitions, so
        a fresh agent (buffer starting at 0, as the Reptile Inner Loop
        creates every outer iteration) needs to collect `batch_size`
        transitions before the very first update can fire. Without this
        warm-up, a small N (e.g. N=50 < batch_size=64) would mean update()
        never fires at all during the whole call -- theta_k would come back
        byte-for-byte identical to theta, silently turning meta-training
        into a no-op. So the warm-up transitions here are collected but not
        counted against N, guaranteeing all N counted steps below actually
        perform a gradient update.
        """
        state = env.reset()
        # Warm-up uses uniform-random actions, matching the paper's "D is
        # initially empty, so the orchestrator collects initial transitions
        # by allowing exploratory actions before it begins sampling
        # minibatches" -- and skipping the actor forward pass makes the
        # warm-up (64 steps repeated every Reptile inner loop) markedly
        # cheaper than sampling from the untrained policy, with better
        # initial action-space coverage to boot.
        warmup_rng = getattr(env, "rng", None)  # env's own seeded RNG keeps runs reproducible
        while len(self.replay_buffer) < batch_size:
            if warmup_rng is not None:
                action = warmup_rng.randrange(self.action_dim)
            else:
                action = int(torch.randint(self.action_dim, (1,)).item())
            next_state, reward, done, info = env.step(action)
            self.replay_buffer.push(state, action, reward, next_state, float(done))
            state = next_state

        stats = []
        for _ in range(num_transitions):
            action = self.select_action(state, greedy=greedy_action)
            next_state, reward, done, info = env.step(action)
            self.replay_buffer.push(state, action, reward, next_state, float(done))
            result = self.update(batch_size=batch_size)
            if result is not None:
                stats.append(result)
            state = next_state
        return stats
