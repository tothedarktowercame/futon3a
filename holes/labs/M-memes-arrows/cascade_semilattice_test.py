#!/usr/bin/env python3
"""Tests for cascade_semilattice.py — the pure slice-2a structural core (no I/O, deterministic).

Acceptance bar (claude-2 handoff): pytest green for the pure pieces — semilattice map (collisions),
anchor predicate, trained-prior direction, beam admissibility.

Run: cd ~/code/futon3a && /usr/bin/python3 -m pytest holes/labs/M-memes-arrows/cascade_semilattice_test.py -q
"""
from cascade_semilattice import (slug, membership_map, move_graph, anchor, management_anchored,
                                 train_logodds, neighbours, admissible, beam_rollout,
                                 MGMT_CONSTELLATIONS, MATH_CONSTELLATION)


# ---- membership map (bare-name join + collision handling) ----

def test_membership_map_bare_name_join():
    pm = [{"pattern": "futon-theory/single-source-of-truth", "cluster": 1},
          {"pattern": "math-informal/unfold-the-definition", "cluster": 13}]
    s2c, coll = membership_map(pm)
    assert s2c["single-source-of-truth"] == 1
    assert s2c["unfold-the-definition"] == 13
    assert coll == {}


def test_membership_map_resolves_and_reports_collision():
    pm = [{"pattern": "a/dup", "cluster": 3}, {"pattern": "b/dup", "cluster": 3},
          {"pattern": "c/dup", "cluster": 8}]  # 'dup' collides: cluster 3 (x2) vs 8 (x1)
    s2c, coll = membership_map(pm)
    assert s2c["dup"] == 3                      # resolves to most-populated (deterministic)
    assert "dup" in coll and coll["dup"] == {3: 2, 8: 1}   # collision reported, not silent


# ---- move graph ----

def test_move_graph_undirected_bare_name():
    phylo = {"co_app": [["ns/a", "ns/b", 2]], "descent": [["x/b", "y/c", 1]]}
    g = move_graph(phylo)
    assert g["a"] == {"b"} and g["c"] == {"b"} and g["b"] == {"a", "c"}


# ---- anchor + the corrected management-anchored discriminator ----

def test_anchor_is_dominant_constellation():
    s2c = {"p": 2, "q": 2, "r": 13}
    assert anchor(["p", "q", "r"], s2c) == 2          # mode = 2 (stack-meta)
    assert anchor([], s2c) is None


def test_management_anchored_vs_math_anchored():
    s2c = {"mgmt1": 1, "mgmt2": 2, "math1": 13, "math2": 13}
    # anchored in the stack-meta region -> management_anchored True
    assert management_anchored(["mgmt1", "mgmt2", "math1"], s2c) is True
    # math-dominated (the 'lost in detail' shape) -> False, even though it TOUCHES management
    assert management_anchored(["math1", "math2", "mgmt1"], s2c) is False
    assert anchor(["math1", "math2", "mgmt1"], s2c) == MATH_CONSTELLATION


# ---- discharge-trained prior DIRECTION ----

def test_train_logodds_direction():
    alive = [["mgmt", "x"], ["mgmt", "y"], ["mgmt", "z"]]   # 'mgmt' in every alive
    mess = [["tech", "x"], ["tech", "y"], ["tech", "z"]]    # 'tech' in every mess
    lo = train_logodds(alive, mess)
    assert lo["mgmt"] > 0      # alive-enriched -> up-weight
    assert lo["tech"] < 0      # mess-enriched -> down-weight
    assert abs(lo["x"]) < 1e-9 # neutral (equal in both) -> ~0


def test_train_logodds_handles_unseen_and_empty():
    lo = train_logodds([["a"]], [])
    assert lo["a"] > 0                      # appears only in alive
    assert train_logodds([], []) == {}      # no data -> empty, no crash


# ---- beam admissibility (connectivity) + want-direction ----

def test_admissible_requires_connectivity():
    graph = {"a": {"b"}, "b": {"a", "c"}, "c": {"b"}, "orphan": set()}
    assert admissible([], "a", graph) is True            # any seed allowed
    assert admissible(["a"], "b", graph) is True         # b neighbours a
    assert admissible(["a"], "c", graph) is False        # c not adjacent to {a}
    assert admissible(["a", "b"], "c", graph) is True     # c neighbours b -> incremental reach
    assert neighbours(["a"], graph) == {"b"}


def test_beam_keeps_connected_path_and_follows_score():
    # chain a-b-c-d; an orphan with huge standalone score must NOT appear (disconnected)
    graph = {"a": {"b"}, "b": {"a", "c"}, "c": {"b", "d"}, "d": {"c"}, "orphan": set()}
    score = {"a": 1.0, "b": 1.0, "c": 1.0, "d": 1.0, "orphan": 99.0}
    sf = lambda path: sum(score.get(p, 0.0) for p in path)
    best = beam_rollout(["a"], graph, sf, B=4, K=4)
    assert best[0] == "a"
    assert "orphan" not in best                  # disconnected, never admissible
    assert set(best) == {"a", "b", "c", "d"}     # follows the connected chain
    # connectivity holds at every prefix
    for i in range(1, len(best)):
        assert admissible(best[:i], best[i], graph)


def test_beam_want_directed_prefers_management_anchor():
    # two candidate seeds with EQUAL base score: one management-anchored, one math-anchored.
    # the want-directed anchor bias must break the tie toward the management-anchored sub-path.
    graph = {"mgmt": set(), "math": set()}
    s2c = {"mgmt": 1, "math": 13}
    base = {"mgmt": 0.5, "math": 0.5}
    sf = lambda path: sum(base[p] for p in path) + 0.5 * (1 if management_anchored(path, s2c) else 0)
    best = beam_rollout(["mgmt", "math"], graph, sf, B=4, K=1)
    assert best == ["mgmt"]   # +bias on the management-anchored seed wins the otherwise-tie
