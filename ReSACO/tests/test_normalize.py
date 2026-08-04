import numpy as np

from mec_core import config
from mec_core.normalize import normalize_state


def test_scale_matches_state_dim():
    from mec_core.normalize import _SCALE
    assert _SCALE.shape == (config.STATE_DIM,)


def test_single_state_normalized_to_comparable_range():
    # A state near the top of each feature's typical physical range should
    # land within roughly [0, ~2] after normalization -- not still spanning
    # 1500-10000 vs. 0-200 like the raw physical values do.
    raw = np.array(
        [10000.0, 2500.0, 2500.0, 100.0] + [100.0] * config.NUM_EDGE_SERVERS
        + [100.0, 200.0, 200.0, 15.0],
        dtype=np.float32,
    )
    normalized = normalize_state(raw)
    assert normalized.shape == raw.shape
    assert np.all(normalized >= 0)
    assert np.all(normalized <= 2.0)


def test_batch_normalization_matches_per_row_normalization():
    raw_single = np.random.uniform(0, 100, size=config.STATE_DIM).astype(np.float32)
    batch = np.stack([raw_single, raw_single * 2], axis=0)
    normalized_batch = normalize_state(batch)
    assert normalized_batch.shape == (2, config.STATE_DIM)
    np.testing.assert_allclose(normalized_batch[0], normalize_state(raw_single))


def test_normalize_accepts_plain_list():
    raw = [0.0] * config.STATE_DIM
    normalized = normalize_state(raw)
    np.testing.assert_array_equal(normalized, np.zeros(config.STATE_DIM, dtype=np.float32))
