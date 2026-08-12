"""P3-2 acceptance: the exported ONNX actor must produce the same
pre-softmax logits as the torch actor (tolerance 1e-5) on raw wire-format
states, including the baked-in normalization."""

import os
import sys

import numpy as np
import pytest
import torch

pytest.importorskip("onnxruntime")

sys.path.insert(0, os.path.join(
    os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "scripts"))

from mec_core import config
from resaco.sac import SACAgent
from export_onnx import EQUIVALENCE_TOLERANCE, export, verify


@pytest.fixture()
def checkpoint(tmp_path):
    torch.manual_seed(2024)
    path = tmp_path / "actor_ckpt.pt"
    torch.save(SACAgent().get_params(), path)
    return str(path)


def test_export_verify_roundtrip(checkpoint, tmp_path):
    out = str(tmp_path / "actor.onnx")
    export(checkpoint, out)
    max_diff = verify(checkpoint, out)
    assert max_diff < EQUIVALENCE_TOLERANCE


def test_onnx_greedy_matches_select_action(checkpoint, tmp_path):
    """End to end: argmax over the ONNX logits on a RAW state must equal
    SACAgent.select_action(greedy=True), which normalizes internally --
    i.e. the graph's baked-in normalization is the real one."""
    import onnxruntime as ort

    out = str(tmp_path / "actor.onnx")
    export(checkpoint, out)

    agent = SACAgent()
    agent.load_params(torch.load(checkpoint, map_location="cpu"))
    session = ort.InferenceSession(out, providers=["CPUExecutionProvider"])

    rng = np.random.default_rng(7)
    from mec_core.normalize import _SCALE
    for _ in range(50):
        raw = (rng.random(config.STATE_DIM) * _SCALE * 1.5).astype(np.float32)
        (logits,) = session.run(["logits"], {"raw_state": raw[None, :]})
        assert int(np.argmax(logits)) == agent.select_action(raw, greedy=True)
