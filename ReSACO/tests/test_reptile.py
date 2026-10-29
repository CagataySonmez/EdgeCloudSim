"""End-to-end regression test for the Reptile Outer Loop: with the
sac_update_loop warm-up fix in place, running the Outer Loop for even a
handful of iterations should measurably move theta away from its random
initialization. Before the fix, theta_k always came back byte-identical
to theta (see test_sac.py), so this would have been a no-op no matter how
many outer iterations ran.
"""

import functools
import math
import operator

import pytest
import torch

from resaco.reptile import (_schedule_alpha, outer_loop, residual_init_fraction,
                            sample_heldout_scenarios)
from resaco.sac import SACAgent
from mec_core.scenario import sample_scenario_pool
from mec_core.seeding import seed_everything


def test_outer_loop_moves_theta_away_from_random_init():
    # Two independently-constructed SACAgents already differ by virtue of
    # being two different random draws, regardless of whether any training
    # happens -- that would make this test pass trivially even with the bug
    # present. Reseeding torch identically before both constructions makes
    # theta_init and outer_loop's internal starting theta bit-identical, so
    # any difference afterward can only come from real gradient updates.
    scenarios = sample_scenario_pool(2, seed=1)

    torch.manual_seed(1234)
    theta_init = SACAgent().get_params()

    torch.manual_seed(1234)
    theta_star = outer_loop(
        scenarios,
        num_outer_iterations=5,
        num_inner_updates=10,
        seed=1,
        progress_every=0,
    )

    actor_changed = any(
        not torch.equal(theta_init["actor"][k], theta_star["actor"][k])
        for k in theta_init["actor"]
    )
    assert actor_changed, "theta_star is identical to its random init -- Reptile Outer Loop did nothing"


def test_outer_loop_with_persistent_buffers_still_trains():
    scenarios = sample_scenario_pool(2, seed=2)

    torch.manual_seed(99)
    theta_init = SACAgent().get_params()

    torch.manual_seed(99)
    theta_star = outer_loop(
        scenarios,
        num_outer_iterations=5,
        num_inner_updates=10,
        seed=2,
        progress_every=0,
        persist_buffers=True,
    )

    actor_changed = any(
        not torch.equal(theta_init["actor"][k], theta_star["actor"][k])
        for k in theta_init["actor"]
    )
    assert actor_changed


def test_eval_every_thins_the_metrics_log():
    scenarios = sample_scenario_pool(2, seed=3)
    metrics_log = []
    outer_loop(
        scenarios,
        num_outer_iterations=6,
        num_inner_updates=5,
        seed=3,
        progress_every=0,
        metrics_log=metrics_log,
        eval_every=3,
    )
    # evaluated only at iterations 3 and 6
    assert [m["iteration"] for m in metrics_log] == [3, 6]


def test_heldout_scenarios_disjoint_from_training_pool():
    # "apps" draws from only 4 fixed profiles x 10 env profiles x an integer
    # device count, so collisions with the pool are actually plausible --
    # the rejection logic has to do real work here, unlike with table2's
    # continuous sampling.
    training = sample_scenario_pool(30, seed=5, source="apps")
    heldout = sample_heldout_scenarios(10, training, seed=5, source="apps")

    assert len(heldout) == 10
    for s in heldout:
        assert s not in training, f"held-out scenario also in the training pool: {s}"
    # and internally duplicate-free
    for i, s in enumerate(heldout):
        assert s not in heldout[:i]


def test_heldout_set_is_deterministic_given_seed():
    training = sample_scenario_pool(5, seed=6)
    assert (sample_heldout_scenarios(4, training, seed=6)
            == sample_heldout_scenarios(4, training, seed=6))


def test_eval_scenarios_are_observational_only():
    """Enabling the held-out eval must not perturb training itself: with
    identical logging settings, a run with eval_scenarios must yield
    bit-identical theta* to one without (greedy held-out rollouts consume
    no torch RNG, and the per-scenario eval seeds come from their own
    stream, never the training one). This is the property that makes eval
    curves comparable across runs that did/didn't log them."""
    import random

    scenarios = sample_scenario_pool(2, seed=11)
    heldout = sample_heldout_scenarios(3, scenarios, seed=11)

    # ReplayBuffer draws its minibatch indices from the *global* random
    # module (pinned by seed_everything() in the CLI scripts), so each arm
    # must restart that stream too, not just torch's.
    random.seed(4242)
    torch.manual_seed(77)
    theta_plain = outer_loop(scenarios, num_outer_iterations=3,
                             num_inner_updates=5, seed=11, progress_every=0,
                             metrics_log=[], eval_every=2)

    random.seed(4242)
    torch.manual_seed(77)
    metrics_log = []
    theta_evaled = outer_loop(scenarios, num_outer_iterations=3,
                              num_inner_updates=5, seed=11, progress_every=0,
                              metrics_log=metrics_log, eval_every=2,
                              eval_scenarios=heldout)

    for group in theta_plain:
        for key in theta_plain[group]:
            assert torch.equal(theta_plain[group][key], theta_evaled[group][key]), (
                f"theta[{group}][{key}] changed when held-out eval was enabled")

    # and the metrics rows actually carry the new columns
    assert metrics_log and all(
        "eval_reward_mean" in m and "eval_reward_std" in m for m in metrics_log)


def test_residual_init_fraction_constant_matches_closed_form():
    assert math.isclose(residual_init_fraction(0.001, 300), (1 - 0.001) ** 300)
    assert math.isclose(residual_init_fraction(0.01, 300), (1 - 0.01) ** 300)
    # the number the whole Phase-1 diagnosis hangs on: at Table II defaults,
    # 74.1% of theta* is its random initialization
    assert round(residual_init_fraction(0.001, 300), 3) == 0.741


def test_linear_schedule_anneals_to_zero():
    K = 10
    alphas = [_schedule_alpha(0.5, k, K, "linear") for k in range(1, K + 1)]
    assert alphas[0] == 0.5                    # first iteration: exactly meta_lr
    assert math.isclose(alphas[-1], 0.5 / K)   # final iteration: meta_lr/K -> 0 as K grows
    assert all(a > 0 for a in alphas)          # no iteration is a discarded no-op
    assert all(a > b for a, b in zip(alphas, alphas[1:]))  # strictly decreasing

    # residual under linear == the explicit product of its per-step factors
    expected = functools.reduce(operator.mul, (1 - a for a in alphas), 1.0)
    assert math.isclose(residual_init_fraction(0.5, K, "linear"), expected)
    # and annealing keeps more late-run signal: with the same starting lr,
    # linear's residual is strictly larger than constant's
    assert residual_init_fraction(0.5, K, "linear") > residual_init_fraction(0.5, K)


def test_empty_eval_scenarios_disables_heldout_eval():
    scenarios = sample_scenario_pool(2, seed=13)
    metrics_log = []
    outer_loop(scenarios, num_outer_iterations=2, num_inner_updates=3, seed=13,
               progress_every=0, metrics_log=metrics_log, eval_every=1,
               eval_scenarios=[])
    assert metrics_log and all("eval_reward_mean" not in m for m in metrics_log)


def test_constant_schedule_is_the_default_and_identity():
    for k in (1, 5, 300):
        assert _schedule_alpha(0.001, k, 300, "constant") == 0.001


def test_unknown_schedule_rejected():
    with pytest.raises(ValueError):
        _schedule_alpha(0.1, 1, 10, "cosine")
    with pytest.raises(ValueError):
        residual_init_fraction(0.1, 10, "cosine")


# ------------------------------------------------------------ batched Reptile
def _theta_equal(a, b):
    return all(torch.equal(a[g][k], b[g][k]) for g in a for k in a[g])


def _batched_theta(scenarios, workers, batch=2, iters=3):
    torch.manual_seed(77)
    return outer_loop(scenarios, num_outer_iterations=iters, num_inner_updates=5,
                      seed=77, progress_every=0, meta_batch_size=batch,
                      num_workers=workers)


def test_batched_theta_is_identical_for_any_worker_count():
    # each inner loop reseeds from its own seed, so where it runs must not
    # matter -- parallelism is a pure wall-clock knob
    scenarios = sample_scenario_pool(3, seed=21)
    serial = _batched_theta(scenarios, workers=1)
    parallel = _batched_theta(scenarios, workers=2)
    assert _theta_equal(serial, parallel)


def test_batched_outer_loop_trains():
    scenarios = sample_scenario_pool(3, seed=22)
    torch.manual_seed(77)
    theta_init = SACAgent().get_params()
    theta = _batched_theta(scenarios, workers=1)
    assert not _theta_equal(theta_init, theta)


def test_batch_of_one_on_one_worker_is_the_paper_path():
    # meta_batch_size=1, num_workers=1 must run Algorithm 1's original code
    # path -- not the reseeding batched one -- so default runs are unchanged
    # (seed_everything, not just torch: the paper path's replay buffer
    # samples from the global `random` module)
    scenarios = sample_scenario_pool(2, seed=23)
    seed_everything(5)
    default = outer_loop(scenarios, num_outer_iterations=3, num_inner_updates=5,
                         seed=5, progress_every=0)
    seed_everything(5)
    explicit = outer_loop(scenarios, num_outer_iterations=3, num_inner_updates=5,
                          seed=5, progress_every=0, meta_batch_size=1, num_workers=1)
    assert _theta_equal(default, explicit)


def test_average_params_identity_and_mean():
    from resaco.reptile import _average_params
    a = SACAgent().get_params()
    assert _theta_equal(_average_params([a]), a)
    avg = _average_params([a, a])
    assert all(torch.allclose(avg[g][k], a[g][k]) for g in a for k in a[g])
    b = SACAgent().get_params()
    mid = _average_params([a, b])
    key = next(iter(a["actor"]))
    assert torch.allclose(mid["actor"][key], (a["actor"][key] + b["actor"][key]) / 2)


@pytest.mark.parametrize("kwargs", [
    {"meta_batch_size": 0},
    {"num_workers": 0},
    {"persist_buffers": True, "num_workers": 2},
])
def test_invalid_batched_settings_rejected(kwargs):
    with pytest.raises(ValueError):
        outer_loop(sample_scenario_pool(2, seed=24), num_outer_iterations=1,
                   num_inner_updates=5, seed=1, progress_every=0, **kwargs)


def test_config_overrides_reach_spawned_workers():
    # override_config (run_ablations.py) mutates the parent's module; spawned
    # workers re-import config from disk, so outer_loop must ship the
    # snapshot. With rewards scaled 1000x the trained critic differs.
    from mec_core import config
    scenarios = sample_scenario_pool(2, seed=25)
    base = _batched_theta(scenarios, workers=2, iters=1)
    with config.override_config(TMAX_SECONDS=1000.0, DELAY_SENSITIVITY_BASE=500.0):
        serial = _batched_theta(scenarios, workers=1, iters=1)
        parallel = _batched_theta(scenarios, workers=2, iters=1)
    assert _theta_equal(serial, parallel)
    assert not _theta_equal(base, parallel)


def test_heldout_sampler_accepts_env_randomization():
    from mec_core.scenario import RANDOMIZED_ENV_NAME
    pool = sample_scenario_pool(5, seed=26, env_randomization=1.0)
    heldout = sample_heldout_scenarios(5, pool, seed=26, env_randomization=1.0)
    assert {s.env_profile.name for s in heldout} == {RANDOMIZED_ENV_NAME}
    assert not any(s in pool for s in heldout)
