"""A3C baseline (Asynchronous Advantage Actor-Critic), used as one of the
comparison baselines in Section V-C ("A3C as widely recognized on-policy
baseline").

Classic A3C structure: a shared global actor/critic pair is updated
asynchronously ("Hogwild!"-style, no locking around the forward/backward
pass) by several worker threads, each interacting with its own copy of the
environment. Every worker periodically syncs its local network from the
global one, collects a short rollout with the local copy, computes
gradients locally, and applies them directly to the global parameters
before taking an optimizer step.

Implemented with Python threads rather than `torch.multiprocessing`: since
threads share process memory, no `share_memory()`/IPC plumbing is needed,
and it avoids the spawn-related fragility of multiprocessing on Windows.
The GIL serializes the actual gradient-apply critical section (guarded by
a lock below anyway, matching how most practical A3C implementations
serialize the parameter update to avoid corrupting the optimizer's
running moments) -- so this reproduces A3C's algorithmic structure
(many asynchronous workers feeding one shared model) rather than true
multi-core parallelism.
"""

import copy
import random
import threading

import torch

from mec_core import config
from mec_core.env import MECOffloadEnv
from .a2c import A2CAgent, ValueCritic
from mec_core.networks import Actor
from mec_core.normalize import normalize_state


class A3CTrainer:
    def __init__(self, state_dim=config.STATE_DIM, action_dim=config.ACTION_DIM,
                 hidden_sizes=config.HIDDEN_SIZES, rollout_len: int = 20,
                 entropy_coef: float = 0.01):
        self.state_dim = state_dim
        self.action_dim = action_dim
        self.hidden_sizes = hidden_sizes
        self.rollout_len = rollout_len
        self.entropy_coef = entropy_coef
        self.gamma = config.DISCOUNT_GAMMA

        # the global model workers pull from / push gradients to
        self.global_actor = Actor(state_dim, action_dim, hidden_sizes)
        self.global_critic = ValueCritic(state_dim, hidden_sizes)
        self.actor_optim = torch.optim.Adam(self.global_actor.parameters(), lr=config.A2C_LR)
        self.critic_optim = torch.optim.Adam(self.global_critic.parameters(), lr=config.A2C_LR)
        self._lock = threading.Lock()

    # ------------------------------------------------------------------
    def _worker(self, worker_id, scenarios, num_updates, seed, log_every, progress):
        rng = random.Random(seed)
        scenario = rng.choice(scenarios)
        env = MECOffloadEnv(scenario, seed=seed)
        state = env.reset()

        local_actor = Actor(self.state_dim, self.action_dim, self.hidden_sizes)
        local_critic = ValueCritic(self.state_dim, self.hidden_sizes)

        for update_idx in range(num_updates):
            # sync local <- global
            local_actor.load_state_dict(self.global_actor.state_dict())
            local_critic.load_state_dict(self.global_critic.state_dict())

            states, actions, rewards, dones = [], [], [], []
            for _ in range(self.rollout_len):
                state_t = torch.as_tensor(normalize_state(state), dtype=torch.float32).unsqueeze(0)
                with torch.no_grad():
                    action = local_actor.sample(state_t)
                action = int(action.item())
                next_state, reward, done, info = env.step(action)
                states.append(state)
                actions.append(action)
                rewards.append(reward)
                dones.append(done)
                state = next_state

            states_t = torch.as_tensor(normalize_state(states), dtype=torch.float32)
            actions_t = torch.as_tensor(actions, dtype=torch.long)
            rewards_t = torch.as_tensor(rewards, dtype=torch.float32)
            dones_t = torch.as_tensor(dones, dtype=torch.float32)
            next_states_t = torch.cat([
                states_t[1:], torch.as_tensor(normalize_state(state), dtype=torch.float32).unsqueeze(0)
            ], dim=0)

            with torch.no_grad():
                next_values = local_critic(next_states_t)
                td_target = rewards_t + self.gamma * (1.0 - dones_t) * next_values

            values = local_critic(states_t)
            advantage = td_target - values
            critic_loss = advantage.pow(2).mean()

            probs = local_actor.action_probs(states_t)
            log_probs = torch.log(probs.gather(1, actions_t.unsqueeze(1)).squeeze(1) + 1e-8)
            entropy = -(probs * torch.log(probs + 1e-8)).sum(dim=-1).mean()
            actor_loss = -(log_probs * advantage.detach()).mean() - self.entropy_coef * entropy

            local_actor.zero_grad()
            actor_loss.backward()
            local_critic.zero_grad()
            critic_loss.backward()

            # apply locally-computed gradients to the shared global model
            with self._lock:
                for g_param, l_param in zip(self.global_actor.parameters(), local_actor.parameters()):
                    g_param.grad = l_param.grad.clone() if l_param.grad is not None else None
                self.actor_optim.step()
                self.actor_optim.zero_grad()

                for g_param, l_param in zip(self.global_critic.parameters(), local_critic.parameters()):
                    g_param.grad = l_param.grad.clone() if l_param.grad is not None else None
                self.critic_optim.step()
                self.critic_optim.zero_grad()

            if progress is not None and (update_idx + 1) % log_every == 0:
                progress.append((worker_id, update_idx + 1, float(actor_loss.item())))

    # ------------------------------------------------------------------
    def train(self, scenarios, num_workers: int = 4, updates_per_worker: int = 200,
               seed: int = None, log_every: int = 50, progress_log=None):
        rng = random.Random(seed)
        threads = []
        for w in range(num_workers):
            t = threading.Thread(
                target=self._worker,
                args=(w, scenarios, updates_per_worker, rng.randint(0, 2**31), log_every, progress_log),
                daemon=True,
            )
            threads.append(t)
            t.start()
        for t in threads:
            t.join()

    # ------------------------------------------------------------------
    def as_agent(self) -> A2CAgent:
        """Wraps the trained global actor/critic in an A2CAgent-compatible
        object (same select_action/get_params/load_params interface) so
        downstream comparison code can treat every baseline uniformly."""
        agent = A2CAgent(self.state_dim, self.action_dim, self.hidden_sizes,
                          rollout_len=self.rollout_len, entropy_coef=self.entropy_coef)
        agent.actor.load_state_dict(self.global_actor.state_dict())
        agent.critic.load_state_dict(self.global_critic.state_dict())
        return agent


def train_a3c(scenarios, num_workers: int = 4, updates_per_worker: int = 200,
              seed: int = None, **kwargs) -> A2CAgent:
    trainer = A3CTrainer(**kwargs)
    trainer.train(scenarios, num_workers=num_workers, updates_per_worker=updates_per_worker, seed=seed)
    return trainer.as_agent()
