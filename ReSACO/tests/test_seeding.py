"""Regression tests for reproducibility.

Every script takes a `--seed` and threads it into scenario sampling and
each env's `random.Random(seed)`, which made runs look seeded -- but the
replay buffer draws its minibatch indices from the *global* `random`
module and torch's global RNG drives network init and action sampling,
neither of which was ever pinned. Two runs of the same command therefore
produced different `theta_star.pt` files. `seed_everything()` closes both
holes; these tests fail if either reopens.
"""

import random

import torch

from resaco.reptile import outer_loop
from resaco.sac import SACAgent
from mec_core.scenario import sample_scenario_pool
from mec_core.seeding import seed_everything


def _tiny_training_run(seed):
    seed_everything(seed)
    scenarios = sample_scenario_pool(2, seed=seed)
    return outer_loop(scenarios, num_outer_iterations=3, num_inner_updates=5,
                      seed=seed, progress_every=0)


def _same_params(a, b):
    return all(torch.equal(a[group][key], b[group][key])
               for group in a for key in a[group])


def test_same_seed_reproduces_identical_meta_parameters():
    first = _tiny_training_run(7)
    second = _tiny_training_run(7)
    assert _same_params(first, second), (
        "same seed produced different theta -- some RNG is unpinned again"
    )


def test_different_seeds_produce_different_meta_parameters():
    # guards against the opposite failure: a "reproducible" run that is
    # reproducible because it ignores the seed entirely
    assert not _same_params(_tiny_training_run(7), _tiny_training_run(8))


def test_seed_everything_pins_the_rngs_the_replay_buffer_and_torch_use():
    seed_everything(123)
    py_first = [random.random() for _ in range(5)]
    torch_first = torch.rand(5)
    init_first = SACAgent().get_params()["actor"]

    seed_everything(123)
    assert [random.random() for _ in range(5)] == py_first   # replay-buffer sampling
    assert torch.equal(torch.rand(5), torch_first)           # action sampling
    init_second = SACAgent().get_params()["actor"]
    assert all(torch.equal(init_first[k], init_second[k]) for k in init_first)  # network init
