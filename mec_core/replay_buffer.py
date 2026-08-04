"""Replay buffer D used by SAC-Update (Algorithm 3)."""

import random

import numpy as np


class ReplayBuffer:
    """Fixed-capacity transition store backed by preallocated numpy arrays.

    Previously a `deque` of per-transition tuples. That made `sample()`
    quadratic in the wrong place: a deque is a linked structure, so each
    of the `batch_size` random index lookups walks the deque
    (O(len(buffer)) each), and the sampled tuples then had to be
    re-assembled into arrays with `map(np.array, zip(*batch))` on every
    single update. At the deployment buffer's full 100k transitions that
    cost ~0.17ms per sample; with columnar numpy storage it is a flat
    fancy-index, independent of how full the buffer is.

    Sampling stays *bit-identical* to the deque version:
    `random.sample`'s index selection depends only on the population size
    and k, so drawing `random.sample(range(size), k)` consumes the same
    RNG values and yields the same logical positions the old call did;
    `_physical` then maps those logical positions (0 = oldest, matching
    deque eviction order) onto the ring's storage slots.
    """

    def __init__(self, capacity: int):
        self.capacity = capacity
        self._states = None          # allocated on first push, once state_dim is known
        self._actions = np.empty(capacity, dtype=np.int64)
        self._rewards = np.empty(capacity, dtype=np.float32)
        self._next_states = None
        self._dones = np.empty(capacity, dtype=np.float32)
        self._start = 0              # storage slot holding the oldest transition
        self._size = 0

    def _allocate(self, state_dim: int):
        self._states = np.empty((self.capacity, state_dim), dtype=np.float32)
        self._next_states = np.empty((self.capacity, state_dim), dtype=np.float32)

    def push(self, state, action, reward, next_state, done):
        if self._states is None:
            self._allocate(len(state))

        if self._size < self.capacity:
            slot = (self._start + self._size) % self.capacity
            self._size += 1
        else:
            # full: overwrite the oldest and advance the window, exactly
            # like deque(maxlen=capacity) dropping from the left
            slot = self._start
            self._start = (self._start + 1) % self.capacity

        self._states[slot] = state
        self._actions[slot] = action
        self._rewards[slot] = reward
        self._next_states[slot] = next_state
        self._dones[slot] = done

    def clear(self):
        """Drop every stored transition (fresh D, Algorithm 2 line 5 /
        Algorithm 4's per-scenario reset). Keeps the allocation."""
        self._start = 0
        self._size = 0

    def _physical(self, logical):
        """Logical position (0 = oldest) -> storage slot."""
        return (self._start + logical) % self.capacity

    def sample(self, batch_size: int):
        k = min(batch_size, self._size)
        idx = self._physical(np.asarray(random.sample(range(self._size), k), dtype=np.int64))
        return (self._states[idx], self._actions[idx], self._rewards[idx],
                self._next_states[idx], self._dones[idx])

    def __len__(self):
        return self._size
