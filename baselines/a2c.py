"""A2C baseline (synchronous Advantage Actor-Critic), used as one of the
comparison baselines in Section V-C ("A2C/A3C as widely recognized
on-policy baselines").

On-policy: collects a short rollout with the current policy, computes a
1-step TD advantage A_t = r_t + gamma*V(s_{t+1}) - V(s_t), and updates the
actor (policy-gradient with the advantage) and critic (state-value
regression) from that rollout, then discards it -- no replay buffer.
"""

import copy

import torch
import torch.nn as nn

from mec_core import config
from mec_core.networks import Actor
from mec_core.normalize import normalize_state


class ValueCritic(nn.Module):
    """V(s): state -> scalar state value."""

    def __init__(self, state_dim, hidden_sizes=config.HIDDEN_SIZES):
        super().__init__()
        layers = []
        last = state_dim
        for h in hidden_sizes:
            layers.append(nn.Linear(last, h))
            layers.append(nn.ReLU())
            last = h
        layers.append(nn.Linear(last, 1))
        self.net = nn.Sequential(*layers)

    def forward(self, state):
        return self.net(state).squeeze(-1)


class A2CAgent:
    def __init__(self, state_dim=config.STATE_DIM, action_dim=config.ACTION_DIM,
                 hidden_sizes=config.HIDDEN_SIZES, device="cpu",
                 rollout_len: int = 20, entropy_coef: float = 0.01):
        self.device = torch.device(device)
        self.actor = Actor(state_dim, action_dim, hidden_sizes).to(self.device)
        self.critic = ValueCritic(state_dim, hidden_sizes).to(self.device)

        self.actor_optim = torch.optim.Adam(self.actor.parameters(), lr=config.A2C_LR)
        self.critic_optim = torch.optim.Adam(self.critic.parameters(), lr=config.A2C_LR)

        self.gamma = config.DISCOUNT_GAMMA
        self.rollout_len = rollout_len
        self.entropy_coef = entropy_coef

    # ------------------------------------------------------------------
    def get_params(self):
        return {
            "actor": copy.deepcopy(self.actor.state_dict()),
            "critic": copy.deepcopy(self.critic.state_dict()),
        }

    def load_params(self, params):
        self.actor.load_state_dict(params["actor"])
        self.critic.load_state_dict(params["critic"])

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
    def _rollout(self, env, state):
        states, actions, rewards, dones = [], [], [], []
        for _ in range(self.rollout_len):
            action = self.select_action(state, greedy=False)
            next_state, reward, done, info = env.step(action)
            states.append(state)
            actions.append(action)
            rewards.append(reward)
            dones.append(done)
            state = next_state
        return states, actions, rewards, dones, state

    def _update_from_rollout(self, states, actions, rewards, dones, final_state):
        states_t = torch.as_tensor(normalize_state(states), dtype=torch.float32, device=self.device)
        actions_t = torch.as_tensor(actions, dtype=torch.long, device=self.device)
        rewards_t = torch.as_tensor(rewards, dtype=torch.float32, device=self.device)
        dones_t = torch.as_tensor(dones, dtype=torch.float32, device=self.device)
        next_states_t = torch.cat([
            states_t[1:],
            torch.as_tensor(normalize_state(final_state), dtype=torch.float32, device=self.device).unsqueeze(0),
        ], dim=0)

        with torch.no_grad():
            next_values = self.critic(next_states_t)
            td_target = rewards_t + self.gamma * (1.0 - dones_t) * next_values

        values = self.critic(states_t)
        advantage = td_target - values

        critic_loss = advantage.pow(2).mean()
        self.critic_optim.zero_grad()
        critic_loss.backward()
        self.critic_optim.step()

        probs = self.actor.action_probs(states_t)
        log_probs = torch.log(probs.gather(1, actions_t.unsqueeze(1)).squeeze(1) + 1e-8)
        entropy = -(probs * torch.log(probs + 1e-8)).sum(dim=-1).mean()
        actor_loss = -(log_probs * advantage.detach()).mean() - self.entropy_coef * entropy

        self.actor_optim.zero_grad()
        actor_loss.backward()
        self.actor_optim.step()

        return {"critic_loss": float(critic_loss.item()), "actor_loss": float(actor_loss.item())}

    # ------------------------------------------------------------------
    def train_loop(self, env, num_transitions: int):
        state = env.reset()
        stats = []
        num_rollouts = max(1, num_transitions // self.rollout_len)
        for _ in range(num_rollouts):
            states, actions, rewards, dones, state = self._rollout(env, state)
            result = self._update_from_rollout(states, actions, rewards, dones, state)
            stats.append(result)
        return stats
