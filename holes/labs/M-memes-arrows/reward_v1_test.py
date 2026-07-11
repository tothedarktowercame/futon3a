"""reward_v1_test.py — tests for R2 v1.1 (reward_v0 + wireability + size-mismatch).

Run: cd /home/joe/code/futon3a && .venv/bin/python3 holes/labs/M-memes-arrows/reward_v1_test.py
"""
import sys, io, contextlib
from pathlib import Path

LAB = Path(__file__).parent
sys.path.insert(0, str(LAB))

import numpy as np
from reward_v0 import RewardV0, hard_checks as hard_checks_v0, loo_s3 as loo_s3_v0
from reward_v1 import (RewardV1, features_v1, wireability_score, obligation_count,
                        size_mismatch, fit_weights_v1, loo_s3_v1,
                        hard_checks_v1, greedy_argmax, V1_FIXED_W,
                        run_canary, MISSION_WANTS)
from fold_ground_truth import load_records
from cascade_rollout import salient
from aliveness_v3_gate2 import DROP
from wiring_corpus import load_corpus


def test_v0_hard_checks_pass_under_v1():
    records = load_records()
    rw = RewardV1(records)
    hc = hard_checks_v1(rw, records)
    assert hc["trivial_dead"], f"trivial should be dead: {hc}"
    assert hc["bloated_dead"], f"bloated should be dead: {hc}"


def test_degeneracy_check_passes():
    records = load_records()
    rw = RewardV1(records)
    hc = hard_checks_v1(rw, records)
    assert hc["degeneracy_check"], f"degeneracy check should pass: {hc}"


def test_obligation_count_basic():
    text = "WANT: something\nHUNGRY-FOR: persistence; reuse; queryability; scope; the store IS the prior\nHAVE: stuff"
    n = obligation_count(text)
    assert n >= 3, f"expected >= 3 obligations, got {n}"


def test_obligation_count_no_hungry():
    n = obligation_count("WANT: just a simple want with some tokens")
    assert n >= 1


def test_wireability_neutral_single():
    assert wireability_score(["single/pattern"], {}) == 0.0


def test_wireability_positive_for_wired_pair():
    corpus = load_corpus()
    aff = wireability_score(["aif/scheduled-observer-entrypoint", "futon-theory/event-protocol"], corpus)
    assert aff > 0, f"wired pair should be positive, got {aff}"


def test_features_v1_has_4_dimensions():
    records = load_records()
    rw = RewardV1(records)
    f = rw._feat(records[0])
    assert len(f) == 4, f"expected 4 features, got {len(f)}"


def test_size_mismatch_zero_for_matched():
    assert size_mismatch(3, 3) == 0.0


def test_size_mismatch_positive_for_unmatched():
    assert size_mismatch(1, 5) == 4.0
    assert size_mismatch(10, 3) == 7.0


def test_greedy_argmax_returns_composition():
    records = load_records()
    rw = RewardV1(records)
    want = "WANT: a system with persistence and queryability and scope\nHUNGRY-FOR: persistence; queryability; scope\nHAVE: nothing"
    patterns = ["aif/scheduled-observer-entrypoint", "futon-theory/event-protocol",
                "iching/hexagram-17-sui", "iching/hexagram-43-guai"]
    best_set, best_score = greedy_argmax(patterns, want, rw)
    assert len(best_set) >= 1, "greedy should return at least 1 pattern"
    assert np.isfinite(best_score), f"score should be finite, got {best_score}"


def test_s3_loo_reported():
    records = load_records()
    real, n95, scores = loo_s3_v1(records)
    assert 0.0 <= real <= 1.0, f"S3 should be in [0,1], got {real}"
    assert 0.0 <= n95 <= 1.0, f"null should be in [0,1], got {n95}"
    assert len(scores) == len(records)


def test_v1_extends_v0_not_edits():
    records = load_records()
    rw0 = RewardV0(records)
    s0 = rw0.score(records[0]["used"], salient(records[0]["problem"], DROP))
    assert np.isfinite(s0)
    real0, n950, _ = loo_s3_v0(records)
    assert 0.0 <= real0 <= 1.0


def test_canary_entrypoint_exists():
    f = io.StringIO()
    with contextlib.redirect_stdout(f):
        results = run_canary(["M-pattern-mining"], pool_mode="proposals")
    assert "M-pattern-mining" in results


def test_junk_beyond_k_not_raised():
    """Junk-beyond-k: adding low-reliability patterns beyond k must NOT raise
    the reliability term, and the bloated-shell hard check must remain dead
    against a junk-padded set.

    Top-k's known failure mode is bloat-tolerance; prove the other gates
    (size_mismatch + bloated hard check) catch it.
    """
    records = load_records()
    rw = RewardV1(records, agg="topk")

    # Find a multi-pattern success record with known obligation count
    multi = [r for r in records if r["success"] and len(r["used"]) >= 2]
    assert multi, "need a multi-pattern success record for this test"
    base = multi[0]
    want_tokens = salient(base["problem"], DROP)
    n_obj = obligation_count(base["problem"])

    # Score the base set
    f_base = features_v1(base["used"], want_tokens, rw.ab, corpus=rw.corpus, agg="topk")
    rel_base = f_base[0]

    # Add junk patterns (patterns not in the training data = Beta(2,1) prior = lowest reliability)
    junk = ["nonexistent/junk-a", "nonexistent/junk-b", "nonexistent/junk-c"]
    padded = base["used"] + junk
    f_padded = features_v1(padded, want_tokens, rw.ab, corpus=rw.corpus, agg="topk")
    rel_padded = f_padded[0]

    # Reliability must NOT increase from adding junk beyond k
    assert rel_padded <= rel_base + 1e-10, \
        f"top-k reliability should not increase from junk: base={rel_base:.4f}, padded={rel_padded:.4f}"

    # The bloated-shell hard check must still catch a junk-padded set
    # (size_mismatch should penalize the padding)
    sm_base = f_base[3]
    sm_padded = f_padded[3]
    assert sm_padded > sm_base, \
        f"size_mismatch should increase from junk padding: base={sm_base}, padded={sm_padded}"

    # Full hard check: the bloated set (20 patterns) must score below substantive
    hc = hard_checks_v1(rw, records)
    assert hc["bloated_dead"], f"bloated hard check must remain dead: {hc}"


if __name__ == "__main__":
    test_v0_hard_checks_pass_under_v1()
    print("PASS: test_v0_hard_checks_pass_under_v1")
    test_degeneracy_check_passes()
    print("PASS: test_degeneracy_check_passes")
    test_obligation_count_basic()
    print("PASS: test_obligation_count_basic")
    test_obligation_count_no_hungry()
    print("PASS: test_obligation_count_no_hungry")
    test_wireability_neutral_single()
    print("PASS: test_wireability_neutral_single")
    test_wireability_positive_for_wired_pair()
    print("PASS: test_wireability_positive_for_wired_pair")
    test_features_v1_has_4_dimensions()
    print("PASS: test_features_v1_has_4_dimensions")
    test_size_mismatch_zero_for_matched()
    print("PASS: test_size_mismatch_zero_for_matched")
    test_size_mismatch_positive_for_unmatched()
    print("PASS: test_size_mismatch_positive_for_unmatched")
    test_greedy_argmax_returns_composition()
    print("PASS: test_greedy_argmax_returns_composition")
    test_s3_loo_reported()
    print("PASS: test_s3_loo_reported")
    test_v1_extends_v0_not_edits()
    print("PASS: test_v1_extends_v0_not_edits")
    test_canary_entrypoint_exists()
    print("PASS: test_canary_entrypoint_exists")
    test_junk_beyond_k_not_raised()
    print("PASS: test_junk_beyond_k_not_raised")
    print("\nAll R2 v1.2 tests passed.")
