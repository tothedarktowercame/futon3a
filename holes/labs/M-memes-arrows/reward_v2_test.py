"""Tests for reward_v2.py.

Run from any cwd:
  cd /tmp && /home/joe/code/futon3a/.venv/bin/python3 /home/joe/code/futon3a/holes/labs/M-memes-arrows/reward_v2_test.py
"""
from __future__ import annotations

import math
import sys
from pathlib import Path

LAB = Path(__file__).parent
sys.path.insert(0, str(LAB))

import numpy as np

from fold_ground_truth import load_records
from reward_v1 import RewardV1, hard_checks_v1, loo_s3_v1
from reward_v2 import (RewardV2, TAU_GRID, UNIFORM_TOLERANCE,
                       choose_tau_inner_loo, hard_checks_v2, lomo_auc,
                       loo_s3_v2)


def test_tau_infinity_recovers_v1_scores_all_records():
    records = load_records()
    v1 = RewardV1(records)
    v2 = RewardV2(records, tau=math.inf)
    diffs = [
        abs(v1.score(r["used"], r["problem"]) - v2.score(r["used"], r["problem"]))
        for r in records
    ]
    assert max(diffs) <= UNIFORM_TOLERANCE, max(diffs)


def test_v2_hard_checks_pass():
    records = load_records()
    rw = RewardV2(records)
    hc = hard_checks_v2(rw, records)
    assert hc["trivial_dead"], hc
    assert hc["bloated_dead"], hc
    assert hc["degeneracy_check"], hc


def test_v1_hard_checks_still_pass():
    records = load_records()
    rw = RewardV1(records)
    hc = hard_checks_v1(rw, records)
    assert hc["trivial_dead"], hc
    assert hc["bloated_dead"], hc
    assert hc["degeneracy_check"], hc


def test_lomo_runs_and_chooses_tau_from_grid():
    records = load_records()
    real, scores, labels, folds = lomo_auc(records, model="v2")
    assert 0.0 <= real <= 1.0
    assert len(scores) == len(labels) == len(records)
    assert folds
    assert all(f["tau"] in TAU_GRID for f in folds)


def test_inner_tau_selection_deterministic():
    records = load_records()[:12]
    tau1, rows1 = choose_tau_inner_loo(records)
    tau2, rows2 = choose_tau_inner_loo(records)
    assert tau1 == tau2
    assert rows1 == rows2


def test_s3_v2_reported():
    records = load_records()
    real, n95, scores = loo_s3_v2(records)
    assert 0.0 <= real <= 1.0
    assert 0.0 <= n95 <= 1.0
    assert len(scores) == len(records)
    v1_real, _, _ = loo_s3_v1(records)
    assert np.isfinite(v1_real)


if __name__ == "__main__":
    test_tau_infinity_recovers_v1_scores_all_records()
    print("PASS: test_tau_infinity_recovers_v1_scores_all_records")
    test_v2_hard_checks_pass()
    print("PASS: test_v2_hard_checks_pass")
    test_v1_hard_checks_still_pass()
    print("PASS: test_v1_hard_checks_still_pass")
    test_lomo_runs_and_chooses_tau_from_grid()
    print("PASS: test_lomo_runs_and_chooses_tau_from_grid")
    test_inner_tau_selection_deterministic()
    print("PASS: test_inner_tau_selection_deterministic")
    test_s3_v2_reported()
    print("PASS: test_s3_v2_reported")
    print("\nAll reward_v2 tests passed.")
