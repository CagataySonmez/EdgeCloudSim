"""DDPG baseline (Deep Deterministic Policy Gradient), adapted to the
discrete offloading action space, used as one of the comparison baselines
in Section V-C of the ReSACO paper ("DDPG for handling continuous action
spaces").

Discrete adaptation: the actor outputs a deterministic preference vector
over actions; its softmax is treated as a continuous relaxation of the
action and fed to the critic (Q(s, softmax(actor(s)))) so the actor
objective stays differentiable, exactly the standard trick used to apply
DDPG to discrete control problems. Execution picks the greedy action
(argmax); exploration during training uses epsilon-greedy over the
discrete action set (DDPG's usual continuous action noise doesn't apply
to a discrete space).
"""

import copy
import random

import torch
import torch.nn as nn
import torch.nn.functional as F

from mec_core import config
from mec_core.networks import Actor
from mec_core.normalize import normalize_state
from mec_core.replay_buffer import ReplayBuffer


class QCritic(nn.Module):
    """Q(s, a) where a is a continuous (softmax) relaxation of the discrete action."""

    def __init__(self, state_dim, action_dim, hidden_sizes=config.HIDDEN_SIZES):
        super().__init__()
        layers = []
        last = state_dim + action_dim
        for h in hidden_sizes:
            layers.append(nn.Linear(last, h))
            layers.append(nn.ReLU())
            last = h
        layers.append(nn.Linear(last, 1))
        self.net = nn.Sequential(*layers)

    def forward(self, state, action_vec):
        return self.net(torch.cat([state, action_vec], dim=-1)).squeeze(-1)


class DDPGAgent:
    def __init__(self, state_dim=config.STATE_DIM, action_dim=config.ACTION_DIM,
                 hidden_sizes=config.HIDDEN_SIZES, device="cpu",
                 epsilon_start=1.0, epsilon_end=0.05, epsilon_decay_steps=5000):
        self.device = torch.device(device)
        self.action_dim = action_dim

        self.actor = Actor(state_dim, action_dim, hidden_sizes).to(self.device)
        self.critic = QCritic(state_dim, action_dim, hidden_sizes).to(self.device)
        self.target_actor = copy.deepcopy(self.actor)
        self.target_critic = copy.deepcopy(self.critic)

        self.actor_optim = torch.optim.Adam(self.actor.parameters(), lr=config.DDPG_ACTOR_LR)
        self.critic_optim = torch.optim.Adam(self.critic.parameters(), lr=config.DDPG_CRITIC_LR)

        self.gamma = config.DISCOUNT_GAMMA
        self.rho = config.TARGET_SOFT_UPDATE_RHO
        self.replay_buffer = ReplayBuffer(config.REPLAY_BUFFER_SIZE)

        self.epsilon = epsilon_start
        self.epsilon_end = epsilon_end
        self.epsilon_decay = (epsilon_start - epsilon_end) / max(epsilon_decay_steps, 1)

    # ------------------------------------------------------------------
    def get_params(self):
        return {
            "actor": copy.deepcopy(self.actor.state_dict()),
            "critic": copy.deepcopy(self.critic.state_dict()),
        }

    def load_params(self, params):
        self.actor.load_state_dict(params["actor"])
        self.critic.load_state_dict(params["critic"])
        self.target_actor.load_state_dict(params["actor"])
        self.target_critic.load_state_dict(params["critic"])

    # ------------------------------------------------------------------
    def select_action(self, state, greedy: bool = False) -> int:
        if not greedy and random.random() < self.epsilon:
            return random.randrange(self.action_dim)
        state_t = torch.as_tensor(normalize_state(state), dtype=torch.float32, device=self.device).unsqueeze(0)
        with torch.no_grad():
            action = self.actor.act_greedy(state_t)
        return int(action.item())

    def _decay_epsilon(self):
        self.epsilon = max(self.epsilon_end, self.epsilon - self.epsilon_decay)

    # ------------------------------------------------------------------
    def update(self, batch_size: int = config.BATCH_SIZE):
        if len(self.replay_buffer) < batch_size:
            return None

        state, action, reward, next_state, done = self.replay_buffer.sample(batch_size)
        state = torch.as_tensor(normalize_state(state), dtype=torch.float32, device=self.device)
        action = torch.as_tensor(action, dtype=torch.long, device=self.device)
        reward = torch.as_tensor(reward, dtype=torch.float32, device=self.device)
        next_state = torch.as_tensor(normalize_state(next_state), dtype=torch.float32, device=self.device)
        done = torch.as_tensor(done, dtype=torch.float32, device=self.device)

        # --- critic update ---
        with torch.no_grad():
            next_action_vec = F.softmax(self.target_actor.logits(next_state), dim=-1)
            target_q = self.target_critic(next_state, next_action_vec)
            y = reward + self.gamma * (1.0 - done) * target_q

        action_one_hot = F.one_hot(action, num_classes=self.action_dim).float()
        q = self.critic(state, action_one_hot)
        critic_loss = F.mse_loss(q, y)

        self.critic_optim.zero_grad()
        critic_loss.backward()
        self.critic_optim.step()

        # --- actor update: maximize Q(s, softmax(actor(s))) ---
        action_vec = F.softmax(self.actor.logits(state), dim=-1)
        actor_loss = -self.critic(state, action_vec).mean()

        self.actor_optim.zero_grad()
        actor_loss.backward()
        self.actor_optim.step()

        # --- soft-update targets ---
        with torch.no_grad():
            for tp, p in zip(self.target_actor.parameters(), self.actor.parameters()):
                tp.mul_(self.rho).add_(p, alpha=1 - self.rho)
            for tp, p in zip(self.target_critic.parameters(), self.critic.parameters()):
                tp.mul_(self.rho).add_(p, alpha=1 - self.rho)

        self._decay_epsilon()
        return {"critic_loss": float(critic_loss.item()), "actor_loss": float(actor_loss.item())}

    # ------------------------------------------------------------------
    def train_loop(self, env, num_transitions: int):
        state = env.reset()
        stats = []
        for _ in range(num_transitions):
            action = self.select_action(state, greedy=False)
            next_state, reward, done, info = env.step(action)
            self.replay_buffer.push(state, action, reward, next_state, float(done))
            result = self.update()
            if result is not None:
                stats.append(result)
            state = next_state
        return stats
