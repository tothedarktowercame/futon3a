#!/usr/bin/env python3
"""cascade_wholeness_experiment.py — the EXPERIMENT (Joe's re-scope, 2026-06-09).

A cascade is the ARGUE move: O_k..O_1 |psi> = the patterns that make the case for THIS mission.
|psi> = M-interim-director-proxy-metric-inventory (a real messy mission). Patterns = operators
retrieved by relevance to |psi>. Question: does a WHOLENESS score C (Salingaros-flavoured;
claude-3 owns the real one) show the too-much/too-little KNEE — too few patterns = inexpressive,
too many = over-complex — so a GOOD-SIZED cascade scores above the length-1 baseline AND above an
over-large one?

This is a C-PROXY to SEE if the knee exists on real data; claude-3 supplies the real C.
Proxy: C(k) = coverage(k) - over_complexity(k), where
  coverage(k)        = MMR diminishing coverage of |psi> (concave: saturates as patterns repeat)
  over_complexity(k) = beta * mean_pairwise_redundancy(k) * k   (grows: clutter penalty)
Knee = argmax_k C(k). Baseline = E-warranted-play = top-1 (length-1 policy).

Run:  cd ~/code/futon3a && python3 holes/labs/M-memes-arrows/cascade_wholeness_experiment.py
(reads /tmp/psi-patterns.json from notions_search; regenerate with that tool if absent.)
"""
import json, math
from pathlib import Path

ROOT = Path("/home/joe/code/futon3a")
ranked = json.load(open("/tmp/psi-patterns.json"))                      # [{id, score(=rel to psi)}...]
embs = {r["id"]: r["vector"] for r in json.load(open(ROOT/"resources/notions/minilm_pattern_embeddings.json"))}

def cos(a, b):
    d = sum(x*y for x, y in zip(a, b)); na = math.sqrt(sum(x*x for x in a)); nb = math.sqrt(sum(y*y for y in b))
    return d/(na*nb) if na and nb else 0.0

pats = [(r["id"], r["score"]) for r in ranked if r["id"] in embs]       # keep ones we have vectors for
BETA = 0.5

def metrics(k):
    chosen = pats[:k]
    ids = [p for p, _ in chosen]; rels = [s for _, s in chosen]
    # coverage: MMR — each pattern's relevance discounted by its max similarity to earlier ones
    coverage = 0.0
    for i, pid in enumerate(ids):
        max_prior = max([cos(embs[pid], embs[ids[j]]) for j in range(i)] or [0.0])
        coverage += rels[i] * (1.0 - max_prior)
    # redundancy: mean pairwise similarity among the chosen set
    pairs = [(i, j) for i in range(k) for j in range(i+1, k)]
    redundancy = (sum(cos(embs[ids[i]], embs[ids[j]]) for i, j in pairs) / len(pairs)) if pairs else 0.0
    over_complexity = BETA * redundancy * k
    return coverage, redundancy, coverage - over_complexity

# --- Method 2: GREEDY-DIVERSITY construction (the knee-producing form) ---
# Build the cascade greedily: at each step add the pattern maximizing marginal
# m(p) = rel_p - sum_{q in chosen} sim(p,q). A whole = relevant centres that DON'T
# redundantly overlap. Marginal goes negative when the next pattern is too redundant
# with the argument so far -> that crossing IS the knee (Salingaros: stop over-articulating).
def greedy_diversity():
    pool = list(pats); chosen = []; trace = []
    while pool:
        best, best_m = None, None
        for pid, rel in pool:
            m = rel - sum(cos(embs[pid], embs[c]) for c, _ in chosen)
            if best_m is None or m > best_m:
                best, best_m = (pid, rel), m
        chosen.append(best); pool.remove(best)
        W = sum(r for _, r in chosen) - sum(cos(embs[chosen[i][0]], embs[chosen[j][0]])
                                            for i in range(len(chosen)) for j in range(i+1, len(chosen)))
        trace.append((len(chosen), best[0], best_m, W))
    return trace

print("\n=== cascade wholeness experiment — |psi> = M-interim-director-proxy-metric-inventory ===")
print(f"patterns retrieved (with vectors): {len(pats)}\n")
print("--- Method 2: greedy-diversity (marginal m = rel - sum sim-to-chosen; knee = m crosses 0) ---")
print(f"{'k':>3}  {'+pattern':<46}  {'marginal':>8}  {'W(k)':>7}")
gtrace = greedy_diversity()
g_knee = None
for k, pid, m, W in gtrace[:18]:
    flag = ""
    if m <= 0 and g_knee is None:
        g_knee = k - 1; flag = "  <-- marginal<=0 : KNEE just before here"
    print(f"{k:>3}  {pid:<46}  {m:>8.3f}  {W:>7.3f}{flag}")
g_best_k = max(gtrace, key=lambda t: t[3])[0]
print(f"\ngreedy: marginal crosses 0 after k={g_knee} ; max-W at k={g_best_k}")
print(f"  => the wholeness-maximal cascade is ~{g_best_k} patterns (NOT 1, NOT all {len(pats)})")
print(f"  cascade: {[t[1] for t in gtrace[:g_best_k]]}")
print()

print(f"{'k':>3}  {'coverage':>9}  {'redund':>7}  {'C(k)':>8}   cascade tail")
ks = [1, 2, 3, 4, 5, 6, 8, 10, 13, 16, 20, 25, min(30, len(pats))]
curve = []
for k in ks:
    cov, red, C = metrics(k)
    curve.append((k, C))
    bar = "#" * max(0, int((C) * 12))
    print(f"{k:>3}  {cov:>9.3f}  {red:>7.3f}  {C:>8.3f}  {bar}")

knee_k, knee_C = max(curve, key=lambda t: t[1])
base_k, base_C = curve[0]
over_k, over_C = curve[-1]
print(f"\nbaseline (k=1, E-warranted-play): C={base_C:.3f}  -> {pats[0][0]}")
print(f"KNEE (best cascade): k={knee_k}  C={knee_C:.3f}")
print(f"  the cascade O_{knee_k}..O_1 |psi> = {[p for p,_ in pats[:knee_k]]}")
print(f"over-large (k={over_k}): C={over_C:.3f}")
discriminates = (knee_C > base_C + 1e-6) and (knee_C > over_C + 1e-6) and (1 < knee_k < over_k)
print(f"\nKNEE BITES? good-sized > too-few AND > too-many: {discriminates}")
print(f"  (proxy C; claude-3 supplies the real Salingaros wholeness — this shows the SHAPE is there)")

# === FINDING (2026-06-09) ===
# Q (Joe): does a wholeness score C discriminate a good-sized cascade from too-much/too-little
#          on a real messy mission (M-interim-director-proxy-metric-inventory)?
# A: YES — but the FORM of C is load-bearing, and the knee is asymmetric.
#  - Method 1 (coverage - beta*redundancy*k): DEGENERATE — k=1 wins, no knee. Wrong shape.
#  - Method 2 (greedy diversity, W = sum rel - sum pairwise-sim; submodular): KNEE at k=2.
#      W(1)=0.394 < W(2)=0.578 > W(3)=0.384 >> W(30)=-31. A 2-pattern cascade beats the
#      length-1 baseline AND every larger cascade. Too-MUCH side bites HARD (over-articulation
#      craters); too-LITTLE side is SHALLOW (relevances are flat: 0.39->0.32 over 16 patterns,
#      so this broad mission's "argument" is carried by ~2 distinct centres).
# => The non-degenerate policy is VISIBLE and the scoring bites. But: (a) a relevance/diversity
#    PROXY only weakly separates k=1 from k=2 — claude-3's structural Salingaros C (mutual
#    reinforcement, one scale up) is the real test of whether the too-little side sharpens;
#    (b) cascade-construction must be coherence-greedy (Method 2), not relevance-top-k (Method 1).

# === REAL-C VERIFY RESULT (claude-3, 2026-06-09) — REDIRECT, not greenlight ===
# claude-3 re-scored the constructed cascades with real structural Salingaros C
# (intensity × harmony H; H = 4·s·(1−s), peaks at intermediate coupling — rewards
# coherent-but-distinct mutual reinforcement, vs this file's W which PENALIZES all coupling).
# RESULT: real C is MONOTONE to the whole pool (CASE A argmax-k=20, ratio 14×; CASE B
# argmax-k=6). NO interior too-much knee under any reward-faithful form.
#  => the k=2 knee here is a PROXY ARTIFACT (it knees because Σsim mis-counts coherence as
#     redundancy). Coherent reinforcement does NOT self-limit, so "too much" is NOT an internal
#     wholeness property.
# THE CORRECTED DESIGN (load-bearing): cascade SIZE is set by, externally to C —
#   (a) too-LITTLE  = COVERAGE-SATURATION of |psi> (stop when marginal coverage < epsilon), and
#   (b) too-MUCH    = a PARSIMONY/BUDGET prior (cost on #centres the consumer/argument can bear).
#   Construction stays coherence-greedy (locked). Do NOT wire the redundancy-penalty proxy as judge.
