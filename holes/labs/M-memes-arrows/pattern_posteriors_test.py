#!/usr/bin/env python3
"""Direct tests for self-graded pattern posterior v0."""

from __future__ import annotations

import tempfile
from pathlib import Path

import cascade_construct
import pattern_posteriors as pp


def test_extracts_success_and_drop_log() -> None:
    index = pp.PatternIndex(
        ids={"math-informal/construct-an-explicit-witness"},
        basename_to_ids={"construct-an-explicit-witness": ["math-informal/construct-an-explicit-witness"]},
    )
    with tempfile.TemporaryDirectory() as d:
        path = Path(d) / "M-sample.md"
        path.write_text(
            """# Mission

## PUR
- Pattern: `math-informal/construct-an-explicit-witness`
- Actions: built the witness
- Outcome: success — verified by tests
- Prediction error: low

## PUR missing field
- Pattern: missing-pattern
- Outcome: success
- Prediction error: low
""",
            encoding="utf-8",
        )
        records, drops = pp.extract_records([path], index)
    assert len(records) == 1
    assert records[0]["pattern_id"] == "math-informal/construct-an-explicit-witness"
    assert records[0]["outcome"] == "success"
    assert records[0]["grading"] == "self-graded"
    assert any(d["reason"] == "pattern-not-in-library" for d in drops)


def test_partial_updates_beta_fractionally() -> None:
    index = pp.PatternIndex(ids={"p/a"}, basename_to_ids={"a": ["p/a"]})
    doc = pp.build_posteriors(
        [{"pattern_id": "p/a", "outcome": "partial", "evidence_ref": "M.md:1", "grading": "self-graded"}],
        index,
    )
    row = doc["patterns"]["p/a"]
    assert row["alpha"] == 1.5
    assert row["beta"] == 1.5
    assert row["n"] == 1
    assert row["mean"] == 0.5
    assert row["label"] == "self-graded"


def test_cascade_posterior_multiplier_is_compositional() -> None:
    table = {
        "label": "self-graded",
        "patterns": {
            "good": {"mean": 0.75, "n": 3},
            "bad": {"mean": 0.25, "n": 3},
        },
    }
    assert cascade_construct.posterior_multiplier("good", table, 0.4) == 1.1
    assert cascade_construct.posterior_multiplier("bad", table, 0.4) == 0.9
    assert cascade_construct.posterior_multiplier("unknown", table, 0.4) == 1.0
    assert cascade_construct.posterior_multiplier("good", table, 0.0) == 1.0


def test_v0_credit_assignment_does_not_cross_grains() -> None:
    pur_credit = pp.assign_v0_credit({"kind": "pur-outcome"})
    rollout_credit = pp.assign_v0_credit({"kind": "rollout-realized-g"})

    assert pur_credit["consumer"] == "pattern-posteriors"
    assert pur_credit["pattern_credit"] == 1.0
    assert pur_credit["move_credit"] == 0.0

    assert rollout_credit["consumer"] == "move-r2"
    assert rollout_credit["pattern_credit"] == 0.0
    assert rollout_credit["move_credit"] == 1.0


def test_grounded_peradam_split_is_escrowed_and_conservative() -> None:
    stub = pp.grounded_peradam_split_stub()

    assert stub["status"] == ":escrowed"
    assert stub["blocked_on"] == "M-peradam-grounding"
    assert stub["implemented"] is False
    assert "<= 1.0" in stub["conservation_rule"]


def run() -> None:
    test_extracts_success_and_drop_log()
    test_partial_updates_beta_fractionally()
    test_cascade_posterior_multiplier_is_compositional()
    test_v0_credit_assignment_does_not_cross_grains()
    test_grounded_peradam_split_is_escrowed_and_conservative()
    print("pattern_posteriors_test: 5 tests passed")


if __name__ == "__main__":
    run()
