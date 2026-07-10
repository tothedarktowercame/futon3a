#!/usr/bin/env python3
"""Tests for cascade_rollout.py — the pure legal-move filter (no model, deterministic).

Acceptance bar (claude-2 handoff): admissible_step must DISCRIMINATE — a chaining move admits, a
type-broken move rejects — and the INCREMENTAL case (a move blocked at the empty cascade becomes
admissible once an earlier move produces its input) must hold, since that is the genuinely-new piece.

Run: cd ~/code/futon3a && .venv/bin/python3 -m pytest holes/labs/M-memes-arrows/cascade_rollout_test.py -q
"""
from cascade_rollout import (salient, move_interface, frontier_of,
                             admissible_step, want_coverage, rollout)


def _mv(pid, consumes, produces):
    return {"id": pid, "consumes": set(consumes), "produces": set(produces)}


# ---- move interface extraction ----

def test_salient_drops_stopwords_and_short():
    s = salient("Compose the patterns into one construction of the fold")
    assert "compose" in s and "construction" in s and "fold" in s
    assert "the" not in s and "of" not in s and "one" not in s  # stop / short


def test_move_interface_splits_consumes_produces():
    m = move_interface("ns/p", ifhow="we hold a cascade however nothing composes it",
                       then="compose the contributions into a wiring")
    assert "cascade" in m["consumes"] and "composes" in m["consumes"]
    assert "compose" in m["produces"] and "wiring" in m["produces"]
    assert m["id"] == "ns/p"


# ---- the legal-move filter: chaining admits, broken rejects ----

def test_admissible_chaining_move_admits():
    have = {"cascade", "hole"}
    move = _mv("ns/a", consumes={"cascade"}, produces={"wiring"})
    assert admissible_step(have, [], move) is True


def test_admissible_type_broken_move_rejects():
    have = {"cascade", "hole"}
    move = _mv("ns/x", consumes={"lebesgue", "manifold"}, produces={"theorem"})
    assert admissible_step(have, [], move) is False  # disjoint from frontier -> rejected


# ---- THE new piece: incremental terminal-chaining ----

def test_admissible_is_incremental():
    """A move blocked at the empty cascade becomes admissible once an earlier move produces its input."""
    have = {"sorry"}
    first = _mv("ns/first", consumes={"sorry"}, produces={"tensions"})
    second = _mv("ns/second", consumes={"tensions"}, produces={"wiring"})
    # second is NOT admissible against the bare HAVE...
    assert admissible_step(have, [], second) is False
    # ...but first IS, and after first runs, the frontier carries `tensions`, admitting second.
    assert admissible_step(have, [], first) is True
    assert admissible_step(have, [first], second) is True
    assert frontier_of(have, [first]) == {"sorry", "tensions"}


# ---- proxy discharge (terminals-match in token form) ----

def test_want_coverage_rewards_emitting_toward_want():
    want = {"wiring", "policy", "holes"}
    good = [_mv("ns/g", {"cascade"}, {"wiring", "policy", "holes"})]
    bad = [_mv("ns/b", {"cascade"}, {"continuous", "linear", "operator"})]  # cosine artifact shape
    assert want_coverage(good, want) == 1.0
    assert want_coverage(bad, want) == 0.0


# ---- rollout uses the filter + state-conditioning ----

def test_rollout_respects_admissibility_and_chains():
    have = {"sorry"}
    moves = [
        _mv("ns/first", {"sorry"}, {"tensions"}),
        _mv("ns/second", {"tensions"}, {"wiring"}),   # only reachable after first
        _mv("ns/orphan", {"galaxies"}, {"stars"}),    # never admissible
    ]
    prior = {"ns/first": 0.9, "ns/second": 0.8, "ns/orphan": 0.99}
    casc = rollout(have, want={"wiring"}, moves=moves, prior=prior,
                   phylo_neighbors={}, K=3, cover_stop=0.6)
    ids = [m["id"] for m in casc]
    assert ids[0] == "ns/first"          # orphan rejected despite highest prior
    assert "ns/second" in ids            # admitted only after first chained the frontier
    assert "ns/orphan" not in ids


def test_rollout_phylogeny_bonus_breaks_ties_via_state():
    have = {"sorry"}
    moves = [
        _mv("ns/first", {"sorry"}, {"x"}),
        _mv("ns/near", {"x"}, {"done"}),
        _mv("ns/far", {"x"}, {"done"}),
    ]
    prior = {"ns/first": 0.9, "ns/near": 0.5, "ns/far": 0.5}  # near/far tie on retrieval
    # phylogeny says `first` co-occurs with `near` -> state-conditioned bonus should pick near
    casc = rollout(have, want={"unreachable"}, moves=moves, prior=prior,
                   phylo_neighbors={"first": {"near"}}, K=2, lam=0.2)
    assert [m["id"] for m in casc] == ["ns/first", "ns/near"]
