"""Tests for mark_labels.py.

Run from any cwd:
  cd /tmp && /home/joe/code/futon3a/.venv/bin/python3 /home/joe/code/futon3a/holes/labs/M-memes-arrows/mark_labels_test.py
"""
from __future__ import annotations

import tempfile
from pathlib import Path
import sys

LAB = Path(__file__).parent
sys.path.insert(0, str(LAB))

from fold_ground_truth import load_records
from mark_labels import labels_from_l1, load_mark_labels, write_edn


def l1(mark_type, glyph, ref_id="ft-autoclock-in-002", confidence="high"):
    return {"mark": {"evidence-id": f"e-{mark_type}", "type": mark_type, "glyph": glyph},
            "referent": {"kind": "fold-deposit", "id": ref_id},
            "confidence": confidence}


def test_flag_off_identity():
    assert load_records() == load_records(include_mark_labels=False)


def test_approval_and_correction_mapping():
    labels = labels_from_l1([l1("approval", "✓"), l1("correction", "✘")])
    assert len(labels) == 2
    assert labels[0]["success"] is True
    assert labels[1]["success"] is False
    assert all(r["grain"] == "operator-mark" for r in labels)


def test_idea_excluded():
    labels = labels_from_l1([l1("idea", "💡")])
    assert labels == []


def test_loader_round_trip_shape():
    labels = labels_from_l1([l1("approval", "✓")])
    assert labels and labels[0]["used"] and labels[0]["problem"]
    with tempfile.TemporaryDirectory() as d:
        path = Path(d) / "mark-labels.edn"
        write_edn(labels, path)
        loaded = load_mark_labels(path)
    assert loaded == labels


def test_load_records_flag_on_merges_mark_labels():
    labels = labels_from_l1([l1("approval", "✓")])
    with tempfile.TemporaryDirectory() as d:
        path = Path(d) / "mark-labels.edn"
        write_edn(labels, path)
        import fold_ground_truth
        old = fold_ground_truth.MARK_LABELS
        try:
            fold_ground_truth.MARK_LABELS = path
            base = fold_ground_truth.load_records()
            merged = fold_ground_truth.load_records(include_mark_labels=True)
        finally:
            fold_ground_truth.MARK_LABELS = old
    assert len(merged) == len(base) + 1
    assert merged[-1]["grain"] == "operator-mark"
    assert {"scope", "success", "used", "problem"} <= set(merged[-1])


if __name__ == "__main__":
    test_flag_off_identity()
    print("PASS: test_flag_off_identity")
    test_approval_and_correction_mapping()
    print("PASS: test_approval_and_correction_mapping")
    test_idea_excluded()
    print("PASS: test_idea_excluded")
    test_loader_round_trip_shape()
    print("PASS: test_loader_round_trip_shape")
    test_load_records_flag_on_merges_mark_labels()
    print("PASS: test_load_records_flag_on_merges_mark_labels")
    print("\nSUMMARY L2 5 tests, 0 failures")
