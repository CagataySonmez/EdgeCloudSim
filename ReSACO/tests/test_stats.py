"""Unit tests for scripts/stats.py: IQM and bootstrap CI must track their
analytic counterparts on known inputs (P2-2 acceptance)."""

import os
import sys

import numpy as np
import pytest

sys.path.insert(0, os.path.join(
    os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "scripts"))

from stats import bootstrap_ci, iqm


def test_iqm_known_values():
    # n=8: trims 2 from each end -> mean(3,4,5,6)
    assert iqm([1, 2, 3, 4, 5, 6, 7, 8]) == 4.5
    # order must not matter
    assert iqm([8, 1, 7, 2, 6, 3, 5, 4]) == 4.5


def test_iqm_ignores_extreme_outliers():
    # n=4: trims 1 from each end -> mean(2, 3); the 1000 outlier vanishes
    assert iqm([1, 2, 3, 1000]) == 2.5


def test_iqm_small_n_degrades_to_mean():
    # n=3: floor(3*0.25)=0 trimmed -> plain mean
    assert iqm([1.0, 2.0, 6.0]) == pytest.approx(3.0)


def test_iqm_empty_rejected():
    with pytest.raises(ValueError):
        iqm([])


def test_bootstrap_ci_tracks_analytic_normal_ci():
    rng = np.random.default_rng(123)
    x = rng.normal(loc=5.0, scale=2.0, size=200)

    lo, hi = bootstrap_ci(x, statistic=np.mean)
    # CI must bracket the sample mean, and its width must be in the same
    # ballpark as the analytic 95% CI width 2 * 1.96 * sigma / sqrt(n)
    assert lo < x.mean() < hi
    analytic_width = 2 * 1.96 * x.std(ddof=1) / np.sqrt(x.size)
    assert (hi - lo) == pytest.approx(analytic_width, rel=0.25)


def test_bootstrap_ci_deterministic_and_isolated():
    x = [1.0, 3.0, 2.0, 5.0, 4.0]
    state = np.random.get_state()[1].tobytes()
    assert bootstrap_ci(x) == bootstrap_ci(x)
    # local Generator only: global numpy RNG state untouched
    assert np.random.get_state()[1].tobytes() == state


def test_bootstrap_ci_constant_array_collapses():
    lo, hi = bootstrap_ci([7.0] * 10)
    assert lo == hi == 7.0
