#!/usr/bin/env python3
"""cascade_semilattice.py — SLICE 2a structural core (claude-1 build / claude-2 review).

Spec: futon2/holes/M-G-over-cascades.md "Slice-2a — re-scoped build spec". Unit = a CONNECTED SUB-PATH
of the stack semilattice (pattern constellations P0-P17 + co_app/descent pattern edges); a mission's
:applied set is a window onto the one stack-wide cascade.

This is the PURE core (no I/O, no model) so the structural pieces are unit-testable deterministically:
  - membership_map  : bare-name -> constellation (handles the bare-name collisions)
  - move_graph      : adjacency over co_app + descent (the move graph)
  - anchor          : the dominant constellation of a sub-path
  - management_anchored : the corrected discriminator (anchor in the stack-meta region, NOT raw breadth)
  - train_logodds   : the discharge-trained move-prior direction (alive vs mess), the R2/R3 return channel
  - beam_rollout    : want-directed beam keeping top-B admissible (connected) partial sub-paths

VERIFIED grounding (from the pre-existing clustering, so non-circular): constellations {1,2,4,6} are the
stack-meta/management region (futon-theory/coordination, aif/agent, ai4ci/f6, structure/invariant);
constellation 13 is math/technique. alive cascades anchor in {1,2,4,6}; mess anchor in 13 (MAP finding).
"""
import math
from collections import Counter

MGMT_CONSTELLATIONS = frozenset({1, 2, 4, 6})   # the stack-meta region (verified, non-circular)
MATH_CONSTELLATION = 13


def slug(pattern):
    """Bare name: the last path component of a (possibly namespaced) pattern id."""
    return pattern.split("/")[-1]


def membership_map(pattern_membership):
    """bare-name -> constellation. Returns (slug2c, collisions).

    pattern_membership: list of {"pattern": "ns/name", "cluster": int}. On a bare-name collision
    (same bare name in >1 constellation) we resolve deterministically to the most-populated constellation
    for that name (ties -> smallest id) and record the collision so it is reported, never silent."""
    by_name = {}
    for r in pattern_membership:
        by_name.setdefault(slug(r["pattern"]), Counter())[r["cluster"]] += 1
    slug2c, collisions = {}, {}
    for name, counts in by_name.items():
        if len(counts) > 1:
            collisions[name] = dict(counts)
        # most-populated cluster, ties broken by smallest id (deterministic)
        slug2c[name] = sorted(counts.items(), key=lambda kv: (-kv[1], kv[0]))[0][0]
    return slug2c, collisions


def move_graph(phylo, keys=("co_app", "descent")):
    """Undirected adjacency (bare-name -> set of bare-name neighbours) over the phylogeny edge sets.

    phylo: {"co_app": [[a,b,w],...], "descent": [...]}. Edges are already bare-name slugs."""
    g = {}
    for k in keys:
        for e in phylo.get(k, []):
            a, b = slug(e[0]), slug(e[1])
            if a == b:
                continue
            g.setdefault(a, set()).add(b)
            g.setdefault(b, set()).add(a)
    return g


def anchor(subpath, slug2c):
    """The dominant constellation of a sub-path (mode of its patterns' constellations; ties -> smallest).

    Returns None for an empty sub-path or one whose patterns have no known constellation."""
    cs = Counter(slug2c[p] for p in subpath if p in slug2c)
    if not cs:
        return None
    return sorted(cs.items(), key=lambda kv: (-kv[1], kv[0]))[0][0]


def management_anchored(subpath, slug2c):
    """The corrected MAP discriminator: is the sub-path ANCHORED in the stack-meta region?

    (anchor in {1,2,4,6}) — NOT 'touches management somewhere' (82% of mess cascades touch it) and NOT
    raw breadth (alive/mess have similar breadth volume-controlled). Anchor is the volume-robust signal."""
    return anchor(subpath, slug2c) in MGMT_CONSTELLATIONS


def train_logodds(alive_sets, mess_sets, alpha=0.5):
    """Discharge-trained move-prior: smoothed log document-frequency odds, alive vs mess.

    Positive => the pattern is enriched in ALIVE missions' :applied sets (up-weight the move);
    negative => enriched in MESS (down-weight). Document-frequency (fraction of class-missions that
    applied the pattern), Laplace-smoothed. This is the R2/R3 return channel from recorded discharge.
    Trained on the TRAIN split only — never on held-out (the caller enforces the split)."""
    a, m = Counter(), Counter()
    for s in alive_sets:
        a.update(set(s))
    for s in mess_sets:
        m.update(set(s))
    na, nm = max(1, len(alive_sets)), max(1, len(mess_sets))
    lo = {}
    for p in set(a) | set(m):
        p_alive = (a[p] + alpha) / (na + 2 * alpha)
        p_mess = (m[p] + alpha) / (nm + 2 * alpha)
        lo[p] = math.log(p_alive) - math.log(p_mess)
    return lo


def neighbours(subpath, graph):
    """All graph-neighbours of a sub-path that are not already in it (the admissible extensions)."""
    pset = set(subpath)
    out = set()
    for n in subpath:
        out |= graph.get(n, set())
    return out - pset


def admissible(subpath, move, graph):
    """LEGAL-MOVE FILTER for sub-paths: a move is admissible iff it keeps the sub-path CONNECTED —
    i.e. it is a graph-neighbour of some pattern already in the sub-path. (Empty sub-path: any seed.)"""
    if not subpath:
        return True
    return move in neighbours(subpath, graph)


def beam_rollout(seeds, graph, score_fn, B=12, K=5):
    """Want-directed beam over the move graph: keep the top-B admissible (connected) partial sub-paths,
    extend each by its admissible neighbours, prune to top-B, up to depth K. Returns the best sub-path.

    seeds    : starting bare-names (each a length-1 sub-path) — the retrieval entry points.
    score_fn : sub-path -> float (the move-prior + want/anchor bias; higher = better).
    Connectivity (admissible) is enforced by only ever extending to neighbours of the current sub-path,
    so every beam entry is a connected sub-path of the semilattice."""
    beams = [[s] for s in dict.fromkeys(seeds)]   # dedup, preserve order
    if not beams:
        return []
    scored = sorted(((score_fn(p), p) for p in beams), key=lambda x: -x[0])[:B]
    beams = [p for _, p in scored]
    best = scored[0]
    for _ in range(K - 1):
        cand = []
        for p in beams:
            for nb in sorted(neighbours(p, graph)):   # deterministic tie-break (set order is PYTHONHASHSEED-dependent)
                cand.append((score_fn(p + [nb]), p + [nb]))
        if not cand:
            break
        cand.sort(key=lambda x: -x[0])
        beams = [p for _, p in cand[:B]]
        if cand[0][0] > best[0]:
            best = cand[0]
    return best[1]
