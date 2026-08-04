"""Actor / Critic networks for discrete-action SAC (Fig. 3 of the paper).

Because the offloading action space is discrete ({device} u {edge servers} u
{cloud}), we use the discrete-SAC formulation: the actor outputs a
categorical distribution over actions, and the critics output a Q-value
per action (rather than taking the action as an input). This lets the
soft Bellman target and entropy terms in Eq. (10)-(12) be computed exactly
as an expectation over the categorical policy instead of via sampling.
"""

import torch
import torch.nn as nn


def _mlp(input_dim, output_dim, hidden_sizes):
    layers = []
    last = input_dim
    for h in hidden_sizes:
        layers.append(nn.Linear(last, h))
        layers.append(nn.ReLU())
        last = h
    layers.append(nn.Linear(last, output_dim))
    return nn.Sequential(*layers)


class Actor(nn.Module):
    """Policy network pi_phi(a | s): state -> categorical action distribution."""

    def __init__(self, state_dim, action_dim, hidden_sizes=(128, 128)):
        super().__init__()
        self.net = _mlp(state_dim, action_dim, hidden_sizes)

    def logits(self, state):
        return self.net(state)

    def action_probs(self, state):
        logits = self.logits(state)
        return torch.softmax(logits, dim=-1)

    def sample(self, state):
        """Draws an action from pi(.|s). Returns the action only: every
        caller (SACAgent/A2CAgent/A3C's workers) discarded the probs and
        log-probs this used to also return, and the update paths that do
        need them recompute them on the full batch anyway -- so computing
        them here was one wasted log() per environment step."""
        probs = self.action_probs(state)
        dist = torch.distributions.Categorical(probs=probs)
        return dist.sample()

    def act_greedy(self, state):
        probs = self.action_probs(state)
        return torch.argmax(probs, dim=-1)


class Critic(nn.Module):
    """Q_psi(s, a): state -> Q-value for every discrete action."""

    def __init__(self, state_dim, action_dim, hidden_sizes=(128, 128)):
        super().__init__()
        self.net = _mlp(state_dim, action_dim, hidden_sizes)

    def forward(self, state):
        return self.net(state)
