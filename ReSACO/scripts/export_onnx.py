"""Exports a trained actor to ONNX for in-process (bridge-free) inference
(P3-2). The exported graph takes the RAW 18-value physical state exactly as
ReSACOStateBuilder sends it over the wire -- the fixed per-feature
normalization (mec_core/normalize.py) is baked into the graph as a constant
divide -- and returns the pre-softmax logits, so a consumer only needs an
argmax to reproduce `SACAgent.select_action(greedy=True)`.

Scope note: this covers the model-artifact half of P3-2. Wiring a
`ReSACOLocalPolicy` into the Java orchestrator additionally requires adding
the ONNX Runtime Java jar (~100 MB of native libraries) to lib/ -- a
dependency decision deliberately left to the repo owner; until then the TCP
bridge stays the only (and default) serving path, and ONNX-served policies
would be frozen (Algorithm 4's online adaptation needs the Python side).

Usage:
    python scripts/export_onnx.py [--checkpoint checkpoints/theta_star.pt]
                                  [--out checkpoints/theta_star.onnx]

The export always ends with a built-in equivalence check: 512 random raw
states through the torch actor vs. the ONNX session, max |logit diff| must
be < 1e-5 or the script exits nonzero.
"""

import argparse
import os
import sys

import numpy as np
import torch

_RESACO_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, _RESACO_DIR)                      # ReSACO/  -> `resaco`, `bridge`
sys.path.insert(0, os.path.dirname(_RESACO_DIR))     # repo root -> `mec_core`, `baselines`

from mec_core import config
from mec_core.networks import Actor
from mec_core.normalize import _SCALE

EQUIVALENCE_TOLERANCE = 1e-5


class RawStateActorLogits(torch.nn.Module):
    """normalize_state + actor logits as ONE graph over the raw wire state,
    so the consumer needs no knowledge of the normalization constants."""

    def __init__(self, actor: Actor):
        super().__init__()
        self.actor = actor
        self.register_buffer("scale", torch.as_tensor(_SCALE, dtype=torch.float32))

    def forward(self, raw_state):
        return self.actor.logits(raw_state / self.scale)


def export(checkpoint_path: str, out_path: str) -> None:
    params = torch.load(checkpoint_path, map_location="cpu")
    actor = Actor(config.STATE_DIM, config.ACTION_DIM, config.HIDDEN_SIZES)
    actor.load_state_dict(params["actor"])
    actor.eval()

    wrapper = RawStateActorLogits(actor).eval()
    dummy = torch.zeros(1, config.STATE_DIM, dtype=torch.float32)
    torch.onnx.export(
        wrapper, (dummy,), out_path,
        input_names=["raw_state"], output_names=["logits"],
        dynamic_axes={"raw_state": {0: "batch"}, "logits": {0: "batch"}},
        dynamo=False,
    )
    print(f"Exported {checkpoint_path} -> {out_path}")


def verify(checkpoint_path: str, out_path: str, num_states: int = 512,
           seed: int = 0) -> float:
    """Torch-vs-ONNX equivalence on random raw states (pre-softmax logits).
    Returns the max absolute difference; raises if it exceeds tolerance."""
    import onnxruntime as ort

    params = torch.load(checkpoint_path, map_location="cpu")
    actor = Actor(config.STATE_DIM, config.ACTION_DIM, config.HIDDEN_SIZES)
    actor.load_state_dict(params["actor"])
    actor.eval()
    wrapper = RawStateActorLogits(actor).eval()

    # raw-scale states: each feature drawn up to ~1.5x its normalization
    # scale, covering the saturated-utilization / jittered-length regions
    rng = np.random.default_rng(seed)
    raw = (rng.random((num_states, config.STATE_DIM), dtype=np.float32)
           * (_SCALE * 1.5))

    with torch.no_grad():
        torch_logits = wrapper(torch.as_tensor(raw)).numpy()

    session = ort.InferenceSession(out_path, providers=["CPUExecutionProvider"])
    (onnx_logits,) = session.run(["logits"], {"raw_state": raw})

    max_diff = float(np.max(np.abs(torch_logits - onnx_logits)))
    if max_diff >= EQUIVALENCE_TOLERANCE:
        raise AssertionError(
            f"ONNX logits diverge from torch by {max_diff:.2e} "
            f"(tolerance {EQUIVALENCE_TOLERANCE:.0e})")
    greedy_match = np.mean(
        np.argmax(torch_logits, axis=1) == np.argmax(onnx_logits, axis=1))
    print(f"Equivalence OK over {num_states} states: max |logit diff| = "
          f"{max_diff:.2e}, greedy-action agreement = {greedy_match:.1%}")
    return max_diff


def main():
    checkpoints = os.path.join(_RESACO_DIR, "checkpoints")
    parser = argparse.ArgumentParser(
        description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--checkpoint", type=str,
                        default=os.path.join(checkpoints, "theta_star.pt"))
    parser.add_argument("--out", type=str, default=None,
                        help="default: the checkpoint path with .onnx extension")
    args = parser.parse_args()

    if not os.path.exists(args.checkpoint):
        print(f"ERROR: {args.checkpoint} not found. Run train_meta.py first.")
        sys.exit(1)
    out = args.out or os.path.splitext(args.checkpoint)[0] + ".onnx"

    export(args.checkpoint, out)
    verify(args.checkpoint, out)


if __name__ == "__main__":
    main()
