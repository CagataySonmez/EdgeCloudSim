"""train_meta.py's --preset mechanism: a preset fills every flag the user
left unset, and an explicitly given flag always wins over it."""

import os
import sys

import pytest

sys.path.insert(0, os.path.join(
    os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "scripts"))

from mec_core import config
from train_meta import PRESETS, build_parser, resolve_preset


def _parse(*argv):
    return resolve_preset(build_parser().parse_args(list(argv)))


def test_default_is_the_paper_exact_preset():
    args = _parse()
    assert args.preset == "paper"
    assert args.meta_lr == config.META_LR
    assert args.meta_lr_schedule == "constant"
    assert args.meta_batch == 1
    assert args.workers == 1
    assert args.env_randomization == 0.0


@pytest.mark.parametrize("preset", sorted(PRESETS))
def test_every_preset_fills_every_controlled_flag(preset):
    args = _parse("--preset", preset)
    for key, value in PRESETS[preset].items():
        assert getattr(args, key) == value


def test_explicit_flags_override_the_preset():
    args = _parse("--preset", "fast", "--meta-lr", "0.25", "--workers", "3",
                  "--env-randomization", "0")
    assert args.meta_lr == 0.25
    assert args.workers == 3
    assert args.env_randomization == 0.0  # an explicit 0 is honored, not treated as unset
    assert args.meta_lr_schedule == PRESETS["fast"]["meta_lr_schedule"]


def test_presets_control_the_same_flag_set():
    keys = {frozenset(p) for p in PRESETS.values()}
    assert len(keys) == 1, "presets disagree on which flags they control"
