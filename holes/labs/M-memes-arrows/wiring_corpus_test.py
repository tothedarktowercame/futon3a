"""wiring_corpus_test.py — tests for R1 (wireability prior from fold corpus).

Run: cd /home/joe/code/futon3a && .venv/bin/python3 -m pytest holes/labs/M-memes-arrows/wiring_corpus_test.py -v
     OR: .venv/bin/python3 holes/labs/M-memes-arrows/wiring_corpus_test.py
"""
import sys
from pathlib import Path

LAB = Path(__file__).parent
sys.path.insert(0, str(LAB))

from wiring_corpus import parse_deposit, extract_wiring_pairs, build_corpus, pair_affinity, load_corpus

DEPOSITS = Path("/home/joe/code/futon6/data/fold-turns")


def test_ft_autoclock_in_002_known_wiring():
    """Hand-verified counts for ft-autoclock-in-002: b1→b2 seq, b2→b3 seq,
    b3→b4 copar, b3→b5 copar, b4→b1 copar."""
    dep = parse_deposit(DEPOSITS / "ft-autoclock-in-002.edn")
    pairs = extract_wiring_pairs(dep)

    # 5 edges, 5 pairs (all distinct)
    assert len(pairs) == 5, f"expected 5 wired pairs, got {len(pairs)}"

    # Verify specific known edges
    pair_set = {(p["pair"][0], p["pair"][1], p["connective"]) for p in pairs}
    assert ("aif/scheduled-observer-entrypoint", "futon-theory/event-protocol", "seq") in pair_set
    assert ("futon-theory/event-protocol", "iching/hexagram-17-sui", "seq") in pair_set
    assert ("iching/hexagram-17-sui", "iching/hexagram-43-guai", "copar") in pair_set
    assert ("iching/hexagram-17-sui", "iching/hexagram-57-xun", "copar") in pair_set
    assert ("aif/scheduled-observer-entrypoint", "iching/hexagram-43-guai", "copar") in pair_set

    # No non-contributors (all 5 patterns got boxes)
    assert len(dep["non_contributors"]) == 0


def test_aif_cluster_wired_4x():
    """The AIF cluster has pairs wired 4× (the mission doc's IDENTIFY evidence)."""
    corpus = build_corpus(DEPOSITS)
    pos = corpus["positive_pairs"]

    # At least one AIF pair with total count >= 4
    aif_recurring = [
        (k, v) for k, v in pos.items()
        if "aif/" in k.lower() and sum(v.values()) >= 4
    ]
    assert len(aif_recurring) >= 1, f"expected AIF pair wired 4+, got {aif_recurring}"


def test_total_counts_match_mission_doc():
    """Mission doc says 132 realized edges over 118 distinct pattern-pairs."""
    corpus = build_corpus(DEPOSITS)
    assert corpus["stats"]["total_positive_edges"] == 132
    assert corpus["stats"]["n_positive_pairs"] == 118


def test_runs_from_any_cwd():
    """The module must import and run from a different cwd."""
    import os
    old_cwd = os.getcwd()
    try:
        os.chdir("/tmp")
        from wiring_corpus import build_corpus, pair_affinity
        corpus = build_corpus()
        assert corpus["stats"]["n_deposits"] >= 21
        # pair_affinity should work
        aff = pair_affinity(("aif/scheduled-observer-entrypoint", "futon-theory/event-protocol"), corpus)
        assert aff > 0  # this pair was wired (seq)
    finally:
        os.chdir(old_cwd)


def test_negative_pairs_exist():
    """Negative pairs (co-proposed but not wired) exist from non-contributors."""
    corpus = build_corpus(DEPOSITS)
    assert corpus["stats"]["n_negative_pairs"] > 0


def test_pair_affinity_neutral_for_unseen():
    """Unseen pair should be neutral (0.0)."""
    corpus = build_corpus(DEPOSITS)
    aff = pair_affinity(("nonexistent/pattern-a", "nonexistent/pattern-b"), corpus)
    assert abs(aff) < 1e-10, f"unseen pair should be neutral, got {aff}"


def test_pair_affinity_positive_for_wired():
    """Positively-wired pair should have positive affinity."""
    corpus = build_corpus(DEPOSITS)
    aff = pair_affinity(("aif/scheduled-observer-entrypoint", "futon-theory/event-protocol"), corpus)
    assert aff > 0, f"wired pair should be positive, got {aff}"


def test_pair_affinity_negative_for_anti_wired():
    """Anti-wired pair (co-proposed, not wired) should be negative."""
    corpus = build_corpus(DEPOSITS)
    # Find a negative pair
    neg = corpus["negative_pairs"]
    if neg:
        key = next(iter(neg))
        pair = tuple(key.split(" | "))
        aff = pair_affinity(pair, corpus)
        assert aff < 0, f"negative pair should be < 0, got {aff}"


if __name__ == "__main__":
    test_ft_autoclock_in_002_known_wiring()
    print("PASS: test_ft_autoclock_in_002_known_wiring")
    test_aif_cluster_wired_4x()
    print("PASS: test_aif_cluster_wired_4x")
    test_total_counts_match_mission_doc()
    print("PASS: test_total_counts_match_mission_doc")
    test_runs_from_any_cwd()
    print("PASS: test_runs_from_any_cwd")
    test_negative_pairs_exist()
    print("PASS: test_negative_pairs_exist")
    test_pair_affinity_neutral_for_unseen()
    print("PASS: test_pair_affinity_neutral_for_unseen")
    test_pair_affinity_positive_for_wired()
    print("PASS: test_pair_affinity_positive_for_wired")
    test_pair_affinity_negative_for_anti_wired()
    print("PASS: test_pair_affinity_negative_for_anti_wired")
    print("\nAll R1 tests passed.")
