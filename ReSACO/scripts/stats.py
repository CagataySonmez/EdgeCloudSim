"""Aggregation statistics for multi-seed RL results: interquartile mean
(IQM) and bootstrap confidence intervals, after Agarwal et al. 2021,
"Deep Reinforcement Learning at the Edge of the Statistical Precipice"
(NeurIPS). Single-run point estimates ("-1.95 vs -1.92") carry no
information about run-to-run variance; every multi-seed report in this
repo aggregates with these two functions instead.

numpy-only by design -- no scipy/rliable dependency.
"""

import numpy as np


def iqm(x):
    """Interquartile mean: the mean of the middle 50% of the sorted values.

    More robust than the mean (ignores the best/worst quarter of seeds --
    outlier runs don't dominate) and more statistically efficient than the
    median. Trims floor(n * 0.25) values from each end (scipy
    trim_mean(proportiontocut=0.25) semantics), so for n < 4 nothing is
    trimmed and this degrades gracefully to the plain mean.
    """
    x = np.sort(np.asarray(x, dtype=float).ravel())
    if x.size == 0:
        raise ValueError("iqm() of an empty array")
    k = int(x.size * 0.25)
    return float(x[k: x.size - k].mean())


def bootstrap_ci(x, statistic=iqm, n_resamples=10_000, alpha=0.05, seed=0):
    """Percentile-bootstrap (1 - alpha) confidence interval for
    `statistic` over the samples in `x`. Returns (ci_low, ci_high).

    Resamples x with replacement `n_resamples` times, evaluates
    `statistic` on each resample, and returns the alpha/2 and 1 - alpha/2
    quantiles of the resulting distribution. Deterministic by default
    (seed=0) and uses a local numpy Generator, so it never touches -- or
    is affected by -- global RNG state.
    """
    x = np.asarray(x, dtype=float).ravel()
    if x.size == 0:
        raise ValueError("bootstrap_ci() of an empty array")
    rng = np.random.default_rng(seed)
    idx = rng.integers(0, x.size, size=(n_resamples, x.size))
    resampled = np.apply_along_axis(statistic, 1, x[idx])
    lo, hi = np.quantile(resampled, [alpha / 2.0, 1.0 - alpha / 2.0])
    return float(lo), float(hi)
