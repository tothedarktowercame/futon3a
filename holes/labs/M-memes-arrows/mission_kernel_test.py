"""Tests for mission_kernel.py.

Run from any cwd:
  cd /tmp && /home/joe/code/futon3a/.venv/bin/python3 /home/joe/code/futon3a/holes/labs/M-memes-arrows/mission_kernel_test.py
"""
from __future__ import annotations

import math
import sys
from pathlib import Path

LAB = Path(__file__).parent
sys.path.insert(0, str(LAB))

import numpy as np

from fold_ground_truth import load_records
from mission_kernel import (embed_records, kernel_weights, mission_group,
                            pairwise_similarity_report)


def test_uniform_limit_exact():
    records = load_records()[:7]
    emb = embed_records(records)
    w = kernel_weights(records[0]["problem"], records, emb, tau=math.inf)
    expected = np.full(len(records), 1.0 / len(records))
    assert np.array_equal(w, expected), f"uniform limit not exact: {w}"


def test_deterministic_weights():
    records = load_records()[:9]
    emb = embed_records(records)
    w1 = kernel_weights(records[3]["problem"], records, emb, tau=0.25)
    w2 = kernel_weights(records[3]["problem"], records, emb, tau=0.25)
    assert np.allclose(w1, w2, atol=0.0, rtol=0.0), "kernel weights must be deterministic"


def test_lomo_group_count_current_labels():
    records = load_records()
    groups = {mission_group(r["scope"]) for r in records}
    assert len(records) == 39
    assert len(groups) == 22


def test_kill_test_shape():
    records = load_records()
    emb = embed_records(records)
    report = pairwise_similarity_report(records, emb)
    assert report["within"], "need within-group pairs"
    assert report["cross"], "need cross-group pairs"
    assert 0.0 <= report["rank_auc_within_gt_cross"] <= 1.0


if __name__ == "__main__":
    test_uniform_limit_exact()
    print("PASS: test_uniform_limit_exact")
    test_deterministic_weights()
    print("PASS: test_deterministic_weights")
    test_lomo_group_count_current_labels()
    print("PASS: test_lomo_group_count_current_labels")
    test_kill_test_shape()
    print("PASS: test_kill_test_shape")
    print("\nAll mission_kernel tests passed.")
