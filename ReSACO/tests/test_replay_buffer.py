import random
from collections import deque

import numpy as np

from mec_core.replay_buffer import ReplayBuffer


def test_push_and_len():
    buf = ReplayBuffer(capacity=100)
    assert len(buf) == 0
    for i in range(10):
        buf.push(state=[i] * 4, action=i % 3, reward=-1.0, next_state=[i + 1] * 4, done=0.0)
    assert len(buf) == 10


def test_capacity_evicts_oldest():
    buf = ReplayBuffer(capacity=5)
    for i in range(8):
        buf.push(state=[i], action=0, reward=0.0, next_state=[i], done=0.0)
    assert len(buf) == 5


def test_sample_shapes():
    buf = ReplayBuffer(capacity=100)
    for i in range(20):
        buf.push(state=[i, i, i], action=i % 4, reward=float(i), next_state=[i + 1] * 3, done=0.0)
    state, action, reward, next_state, done = buf.sample(8)
    assert state.shape == (8, 3)
    assert action.shape == (8,)
    assert reward.shape == (8,)
    assert next_state.shape == (8, 3)
    assert done.shape == (8,)
    assert isinstance(state, np.ndarray)


def test_sample_caps_at_buffer_length():
    buf = ReplayBuffer(capacity=100)
    for i in range(3):
        buf.push(state=[i], action=0, reward=0.0, next_state=[i], done=0.0)
    state, *_ = buf.sample(64)
    assert state.shape[0] == 3


def test_wrapped_buffer_holds_exactly_the_newest_transitions():
    """The columnar ring buffer replaced a deque(maxlen=capacity); once it
    wraps, the surviving transitions (and their oldest-first order) must
    still match what the deque would have kept."""
    capacity = 5
    buf = ReplayBuffer(capacity=capacity)
    reference = deque(maxlen=capacity)
    for i in range(13):  # wraps twice, landing mid-ring
        buf.push(state=[float(i)], action=i % 3, reward=float(i), next_state=[float(i)], done=0.0)
        reference.append(i)

    assert len(buf) == capacity
    stored = [buf._states[buf._physical(pos)][0] for pos in range(len(buf))]
    assert stored == [float(i) for i in reference]
    # the per-column arrays must stay aligned with the state column
    actions = [buf._actions[buf._physical(pos)] for pos in range(len(buf))]
    assert actions == [i % 3 for i in reference]


def test_sample_draws_the_same_transitions_a_deque_would_have():
    """Sampling stays bit-identical to the old deque implementation:
    random.sample's index choice depends only on population size and k, so
    seeding identically must select the same logical positions -- including
    after the ring has wrapped."""
    capacity, batch = 64, 8
    buf = ReplayBuffer(capacity=capacity)
    reference = deque(maxlen=capacity)
    for i in range(200):  # well past capacity, so the ring is wrapped
        buf.push(state=[float(i)], action=0, reward=float(i), next_state=[float(i)], done=0.0)
        reference.append(float(i))

    random.seed(1234)
    sampled_state, _, sampled_reward, _, _ = buf.sample(batch)
    random.seed(1234)
    expected = random.sample(list(reference), batch)

    assert [s[0] for s in sampled_state] == expected
    assert list(sampled_reward) == expected


def test_clear_resets_without_leaking_old_transitions():
    buf = ReplayBuffer(capacity=4)
    for i in range(6):  # wrap first, so _start is non-zero
        buf.push(state=[float(i)], action=0, reward=0.0, next_state=[float(i)], done=0.0)
    buf.clear()
    assert len(buf) == 0

    buf.push(state=[99.0], action=1, reward=2.0, next_state=[99.0], done=1.0)
    state, action, reward, _, done = buf.sample(4)
    assert state.shape == (1, 1)
    assert state[0][0] == 99.0 and action[0] == 1 and reward[0] == 2.0 and done[0] == 1.0
