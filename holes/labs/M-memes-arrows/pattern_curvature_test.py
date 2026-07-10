#!/usr/bin/env python3
"""Tests for pattern_curvature.py pure helpers (graph builders + node-kappa aggregation + Sinkhorn).

Run: cd ~/code/futon3a && /usr/bin/python3 -m pytest holes/labs/M-memes-arrows/pattern_curvature_test.py -q
(needs numpy; if system python lacks it use .venv/bin/python3 -m pytest)
"""
import numpy as np
from pattern_curvature import co_mission_graph, co_app_graph, curvature, _sinkhorn


def test_co_mission_pair_weights_and_threshold():
    applied = [["a", "b", "c"], ["a", "b"], ["a", "c"]]   # ab=2, ac=2, bc=1
    G = co_mission_graph(applied, wmin=2)
    assert G["a"]["b"] == 2 and G["a"]["c"] == 2
    assert "c" not in G["b"]            # bc weight 1 < 2 -> pruned
    assert "b" in G["a"] and "a" in G["b"]   # symmetric


def test_co_app_threshold_and_bare_name():
    phylo = {"co_app": [["ns/a", "ns/b", 3], ["x/a", "y/c", 1]]}
    G = co_app_graph(phylo, wmin=2)
    assert G["a"]["b"] == 3
    assert "c" not in G                 # weight 1 pruned


def test_node_kappa_is_mean_of_incident_edges():
    # triangle a-b-c (all weight 2); node kappa must equal the mean of its incident edge kappas
    G = {"a": {"b": 2, "c": 2}, "b": {"a": 2, "c": 2}, "c": {"a": 2, "b": 2}}
    ek, nk, n = curvature(G)
    assert n == 3 and len(ek) == 3
    def incident(node):
        return [k for (x, y), k in ek.items() if node in (x, y)]
    for node in "abc":
        assert abs(nk[node] - np.mean(incident(node))) < 1e-9


def test_sinkhorn_zero_cost():
    a = np.array([0.5, 0.5]); b = np.array([0.5, 0.5])
    assert abs(_sinkhorn(a, b, np.zeros((2, 2)))) < 1e-6   # zero cost -> zero transport cost
