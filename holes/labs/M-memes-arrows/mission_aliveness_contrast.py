#!/usr/bin/env python3
"""mission_aliveness_contrast.py — SLICE-2a MAP step (cheap descriptive alive-vs-mess pattern contrast).

Joe approved this as part of MAP: before building the discharge-trained beam rollout, just JOIN the patterns a
mission actually applied (mission-pattern-scopes.edn :applied) with its Salingaros class (mission-wholeness.edn
:class) and ask which patterns are ENRICHED in alive missions vs mess ones. This (a) confirms the discharge
signal is actually present in pattern usage, and (b) gives the MAP facts (coverage, |applied| distribution) that
decide slice-2a's realizable shape.

NON-CIRCULAR: the :class label is computed over scope-tree STRUCTURE (L=T*H), not over patterns — so a pattern
skewing alive/mess is a real finding, not a tautology.

Read-only. Run: cd ~/code/futon3a && python3 holes/labs/M-memes-arrows/mission_aliveness_contrast.py
"""
import re, math
from pathlib import Path
from collections import defaultdict

DATA = Path("/home/joe/code/futon6/data")
WHOLE = (DATA / "mission-wholeness.edn").read_text()
SCOPES = (DATA / "mission-pattern-scopes.edn").read_text()

# --- parse labels: {:mission "M-x" :class :alive :L 82.0 ...} ---
CLASS, LIFE = {}, {}
for m, cls, L in re.findall(r':mission "M-([^"]+)" :class :(\w+) :L ([\d.]+)', WHOLE):
    CLASS[m] = cls
    LIFE[m] = float(L)

# --- parse applied patterns: {:mission "M-x" :applied ["a" "b" ...] ...} ---
APPLIED = {}
for m, body in re.findall(r':mission "M-([^"]+)" :applied \[([^\]]*)\]', SCOPES):
    APPLIED[m] = re.findall(r'"([^"]+)"', body)

missions = sorted(set(CLASS) & set(APPLIED))
alive = [m for m in missions if CLASS[m] == "alive"]
mess  = [m for m in missions if CLASS[m] == "mess"]
pipe  = [m for m in missions if CLASS[m] == "pipeline"]
stub  = [m for m in missions if CLASS[m] == "stub"]

# ---------- MAP facts ----------
print("=== MAP facts (join of mission-wholeness.edn x mission-pattern-scopes.edn) ===")
print(f"  missions with BOTH label and applied-list: {len(missions)}")
print(f"    alive={len(alive)}  mess={len(mess)}  pipeline={len(pipe)}  stub={len(stub)}")
for label, grp in (("alive", alive), ("mess", mess), ("pipeline", pipe)):
    sizes = [len(APPLIED[m]) for m in grp]
    nz = [s for s in sizes if s > 0]
    if not sizes:
        continue
    cov = len(nz) / len(sizes)
    avg = sum(sizes) / len(sizes)
    multi = sum(1 for s in sizes if s > 1) / len(sizes)
    print(f"  {label:9}: |applied| avg={avg:.1f}  coverage(>=1)={cov:.0%}  multi(|U|>1)={multi:.0%}  max={max(sizes)}")
print("  (|U|>1 being the norm = slice-1's 'chaining inert' edge resolves on this corpus)")

# ---------- the contrast: alive vs mess only (the labelled +/- institutions) ----------
nA, nM = len(alive), len(mess)
useA, useM = defaultdict(int), defaultdict(int)
for m in alive:
    for p in set(APPLIED[m]):
        useA[p] += 1
for m in mess:
    for p in set(APPLIED[m]):
        useM[p] += 1

pats = set(useA) | set(useM)
SUPPORT = 3            # pattern must appear in >=3 labelled (alive|mess) missions
a = 0.5                # Laplace smoothing
rows = []
for p in pats:
    A, M = useA[p], useM[p]
    if A + M < SUPPORT:
        continue
    # log2 lift of alive-rate vs mess-rate (smoothed), + alive share among users
    rateA = (A + a) / (nA + a)
    rateM = (M + a) / (nM + a)
    lift = math.log2(rateA / rateM)
    share = A / (A + M)
    rows.append((lift, p, A, M, share))

rows.sort(reverse=True)
print(f"\n=== patterns ENRICHED IN ALIVE (alive n={nA}, mess n={nM}; support>={SUPPORT}) ===")
print(f"  {'log2lift':>8}  {'A':>3} {'M':>3}  {'aliveshare':>10}  pattern")
for lift, p, A, M, share in rows[:20]:
    print(f"  {lift:+8.2f}  {A:3d} {M:3d}  {share:10.0%}  {p}")

print(f"\n=== patterns ENRICHED IN MESS ===")
for lift, p, A, M, share in rows[-15:][::-1]:
    print(f"  {lift:+8.2f}  {A:3d} {M:3d}  {share:10.0%}  {p}")

# ---------- aggregate: do alive missions simply use MORE / different-breadth patterns? ----------
print("\n=== aggregate signal (is it composition, or just volume?) ===")
def stat(grp):
    sizes = [len(APPLIED[m]) for m in grp]
    uniq = len({p for m in grp for p in APPLIED[m]})
    return (sum(sizes) / len(grp) if grp else 0), uniq
aa, au = stat(alive); ma, mu = stat(mess)
print(f"  alive: mean |applied|={aa:.1f}, distinct patterns touched={au}")
print(f"  mess : mean |applied|={ma:.1f}, distinct patterns touched={mu}")
print("  (if mean |applied| is similar but the enriched-pattern lists differ, the signal is COMPOSITIONAL,")
print("   not mere volume — which is the slice-2a thesis: G is over the composition, not the count.)")
