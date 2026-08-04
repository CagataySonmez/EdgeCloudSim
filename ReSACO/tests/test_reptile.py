"""End-to-end regression test for the Reptile Outer Loop: with the
sac_update_loop warm-up fix in place, running the Outer Loop for even a
handful of iterations should measurably move theta away from its random
initialization. Before the fix, theta_k always came back byte-identical
to theta (see test_sac.py), so this would have been a no-op no matter how
many outer iterations ran.
"""

import torch

from resaco.reptile import outer_loop
from resaco.sac import SACAgent
from mec_core.scenario import sample_scenario_pool


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
