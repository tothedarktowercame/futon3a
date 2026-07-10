#!/usr/bin/env python3
"""pattern_curvature.py — VERIFY hook (1) for M-G-over-cascades (claude-1 build / claude-2 review).

Produce a non-degenerate Ollivier-Ricci kappa TENSION-FIELD over the PATTERN graph for the mission corpus.
Per the DERIVE/ARGUE synthesis (futon2/holes/M-G-over-cascades.md): Axis-2 tension = NEGATIVE Ollivier-Ricci
curvature; G(pi) = expected tension-discharge across scales. Our cascades live on PATTERNS, so we move the
rung-3 file-graph curvature onto the pattern graph.

REUSE: the Ollivier-Ricci core (bfs / support / sinkhorn / kappa = 1 - W/hop) and the LOCKED params are
copied verbatim from futon5a/holes/tech-notes/misfit_rung3_curvature_demo.py (scipy/numpy Sinkhorn-OT, no
networkx/POT). Only the GRAPH changes (file-co-mission -> pattern graph).

COMBINING-METHODS-AS-DIAGNOSTIC: build the pattern graph two ways and report both —
  (a) pattern-co-mission : nodes=patterns, edge weight = #missions co-applying them (mission-pattern-scopes :applied)
  (b) co_app pattern graph: co_app weights from pattern-phylogeny-edges.json
Their (dis)agreement on negative-kappa patterns is itself the diagnostic.

TEMPORAL kappa: a dkappa/dt over commit windows (the real "discharge") is NOT feasible now — Ollivier-Ricci
is validated in isolation but not wired to the live Poly substrate (the [open] Poly<->curvature seam,
futon5a/.../TN-joe-dt-explainer.md). STATIC kappa is the VERIFY deliverable; temporal is INSTANTIATE/live.

OUTPUT: futon3a/holes/labs/M-memes-arrows/pattern-curvature.json — per-graph node->kappa + edge kappa +
stats + named cross-constellation bridges, for claude-2's G-eval harness to consume node->kappa directly.

Run: cd ~/code/futon3a && .venv/bin/python3 holes/labs/M-memes-arrows/pattern_curvature.py
"""
import json, subprocess
from pathlib import Path
from collections import defaultdict, deque, Counter
import numpy as np
from cascade_semilattice import slug, membership_map

ROOT = Path("/home/joe/code")
# --- LOCKED params, verbatim from rung-3 ---
ALPHA, EPS, SITERS, CUT, CAP, WMIN = 0.5, 0.5, 50, 4, 30, 2


def load_edn(path):
    code = ('(require (quote [cheshire.core :as json])) '
            f'(print (json/generate-string (clojure.edn/read-string (slurp "{path}"))))')
    return json.loads(subprocess.run(["bb", "-e", code], capture_output=True, text=True, check=True).stdout)


# ---------- pattern graph builders (the only thing that differs from rung-3) ----------

def co_mission_graph(applied_sets, wmin=WMIN):
    """Nodes = patterns; edge weight = # missions that co-applied the pair. Threshold weight>=wmin."""
    pair_w = defaultdict(int)
    for s in applied_sets:
        fs = sorted(set(s))
        for i in range(len(fs)):
            for j in range(i + 1, len(fs)):
                pair_w[(fs[i], fs[j])] += 1
    G = defaultdict(dict)
    for (a, b), w in pair_w.items():
        if w >= wmin:
            G[a][b] = w
            G[b][a] = w
    return G


def co_app_graph(phylo, wmin=WMIN):
    """The co_app pattern graph (bare-name slugs); edge weight = co_app weight. Threshold weight>=wmin."""
    G = defaultdict(dict)
    for e in phylo.get("co_app", []):
        a, b, w = slug(e[0]), slug(e[1]), e[2]
        if a == b or w < wmin:
            continue
        G[a][b] = w
        G[b][a] = w
    return G


# ---------- Ollivier-Ricci core (verbatim from rung-3, locked params) ----------

def _bfs(G, src):
    dist = {src: 0}; q = deque([src])
    while q:
        u = q.popleft()
        if dist[u] >= CUT:
            continue
        for v in G[u]:
            if v not in dist:
                dist[v] = dist[u] + 1; q.append(v)
    return dist


def _support(G, x):
    # top-CAP neighbours by weight; ties broken by NAME for determinism (rung-3 lesson: set/sort order)
    nbrs = sorted(G[x].items(), key=lambda kv: (-kv[1], kv[0]))[:CAP]
    supp = [x] + [n for n, _ in nbrs]
    mass = np.array([ALPHA] + [(1 - ALPHA) / len(nbrs)] * len(nbrs)) if nbrs else np.array([1.0])
    return supp, mass


def _sinkhorn(a, b, C):
    K = np.exp(-C / EPS) + 1e-300
    u = np.ones(len(a)); v = np.ones(len(b))
    for _ in range(SITERS):
        u = a / (K @ v + 1e-300); v = b / (K.T @ u + 1e-300)
    P = u[:, None] * K * v[None, :]
    return float((P * C).sum())


def curvature(G):
    """Per-edge Ollivier-Ricci kappa = 1 - W_sinkhorn / hop, and per-node kappa = mean of incident edges."""
    nodes = sorted(G)
    D = {n: _bfs(G, n) for n in nodes}
    def hop(a, b):
        return D[a].get(b, CUT + 1)
    edge_kappa = {}
    for x in nodes:
        sx, mx = _support(G, x)
        for y in sorted(G[x]):
            if (y, x) in edge_kappa:
                continue
            sy, my = _support(G, y)
            C = np.array([[hop(p, q) for q in sy] for p in sx], float)
            edge_kappa[(x, y)] = 1.0 - _sinkhorn(mx, my, C) / max(hop(x, y), 1)
    incident = defaultdict(list)
    for (a, b), k in edge_kappa.items():
        incident[a].append(k); incident[b].append(k)
    node_kappa = {n: float(np.mean(v)) for n, v in incident.items()}
    return edge_kappa, node_kappa, len(nodes)


def stats(ks):
    ks = np.array(ks)
    return dict(min=round(float(ks.min()), 4), med=round(float(np.median(ks)), 4),
                mean=round(float(ks.mean()), 4), max=round(float(ks.max()), 4),
                frac_neg=round(float((ks < 0).mean()), 4), n=len(ks))


def main():
    scopes = load_edn(ROOT / "futon6/data/mission-pattern-scopes.edn")["missions"]
    phylo = json.load(open(ROOT / "futon6/data/pattern-phylogeny-edges.json"))
    semi = load_edn(ROOT / "futon3c/holes/excursions/pipeline-semilattice-clusters.edn")
    slug2c, _ = membership_map(semi["pattern-membership"])
    # human-readable constellation label = dominant namespace in that cluster
    cl_ns = defaultdict(Counter)
    for r in semi["pattern-membership"]:
        cl_ns[r["cluster"]][r["pattern"].split("/")[0]] += 1
    clabel = {c: f"P{c}:{ns.most_common(1)[0][0]}" for c, ns in cl_ns.items()}
    def constell(p):
        return clabel.get(slug2c.get(p), "P?:unknown")

    applied = [[slug(p) for p in m["applied"]] for m in scopes]
    graphs = {"co_mission": co_mission_graph(applied), "co_app": co_app_graph(phylo)}

    out = {"params": dict(alpha=ALPHA, eps=EPS, siters=SITERS, cut=CUT, cap=CAP, wmin=WMIN),
           "temporal_kappa": ("STATIC only. dkappa/dt over commit windows (the real discharge) is an "
                              "INSTANTIATE/live-substrate dependency: Ollivier-Ricci is validated in isolation "
                              "but not wired to the live Poly substrate ([open] Poly<->curvature seam, "
                              "TN-joe-dt-explainer.md). Static kappa is the VERIFY deliverable."),
           "graphs": {}}
    neg_nodes = {}
    for name, G in graphs.items():
        ek, nk, nn = curvature(G)
        out["graphs"][name] = {
            "n_nodes": nn, "n_edges": len(ek),
            "kappa_edge_stats": stats(list(ek.values())),
            "kappa_node_stats": stats(list(nk.values())) if nk else None,
            "node_kappa": {k: round(v, 5) for k, v in nk.items()},
            "edge_kappa": [[a, b, round(k, 5)] for (a, b), k in ek.items()],
        }
        # most-negative edges = candidate cross-concern bridges
        ranked = sorted(ek.items(), key=lambda kv: kv[1])
        bridges = [{"k": round(k, 3), "a": a, "b": b, "ca": constell(a), "cb": constell(b),
                    "cross": constell(a) != constell(b)} for (a, b), k in ranked[:10]]
        out["graphs"][name]["top_bridges"] = bridges
        out["graphs"][name]["bridges_cross_constellation"] = f"{sum(b['cross'] for b in bridges)}/10"
        neg_nodes[name] = {n for n, v in nk.items() if v < 0}
        print(f"\n=== {name} pattern graph ===")
        es = out["graphs"][name]["kappa_edge_stats"]
        print(f"  {nn} nodes, {len(ek)} edges | edge kappa: min={es['min']} med={es['med']} "
              f"mean={es['mean']} max={es['max']} frac_neg={es['frac_neg']}")
        print(f"  cross-constellation among 10 most-negative edges: "
              f"{out['graphs'][name]['bridges_cross_constellation']}")
        for b in bridges[:6]:
            print(f"    k={b['k']:+.3f} [{'CROSS' if b['cross'] else 'same '}] "
                  f"{b['a']} ({b['ca']})  <->  {b['b']} ({b['cb']})")

    # combining-methods-as-diagnostic: do the two graphs agree on negative-kappa patterns?
    both = neg_nodes["co_mission"] & neg_nodes["co_app"]
    union = neg_nodes["co_mission"] | neg_nodes["co_app"]
    shared = sorted(both)[:12]
    out["combining_methods"] = {
        "neg_nodes_co_mission": len(neg_nodes["co_mission"]),
        "neg_nodes_co_app": len(neg_nodes["co_app"]),
        "neg_nodes_both": len(both),
        "jaccard": round(len(both) / len(union), 4) if union else 0.0,
        "agreed_negative_examples": shared,
    }
    print(f"\n=== combining-methods (negative-kappa agreement) ===")
    print(f"  neg nodes: co_mission={len(neg_nodes['co_mission'])} co_app={len(neg_nodes['co_app'])} "
          f"both={len(both)} jaccard={out['combining_methods']['jaccard']}")
    print(f"  agreed-negative (both graphs): {shared[:8]}")

    path = ROOT / "futon3a/holes/labs/M-memes-arrows/pattern-curvature.json"
    json.dump(out, open(path, "w"), indent=1)
    print(f"\nwrote {path}")
    print("TEMPORAL: static only; dkappa/dt = INSTANTIATE/live-substrate dependency ([open] Poly<->curvature seam).")


if __name__ == "__main__":
    main()
