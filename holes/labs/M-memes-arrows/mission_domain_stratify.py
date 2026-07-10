#!/usr/bin/env python3
"""mission_domain_stratify.py — SLICE-2a MAP cut (a): does the alive/mess pattern signal survive WITHIN domain?

The contrast (mission_aliveness_contrast.py) is confounded: mess is dominated by math/proof missions. Cut (a)
removes that confound by STRATIFYING on domain (math vs stack), then re-running the contrast inside each stratum.
If, among STACK missions alone, alive ones still differ from mess ones in pattern usage (and a FRESH within-stack
contrast still surfaces management-ish patterns), the signal is QUALITY, not domain. If it vanishes, the label is
mostly a domain marker and slice-2a must be scoped per-domain.

Domain is classified by mission NAME keywords (auditable, printed below) — deliberately NOT by patterns (which
would be circular with what we're testing).

Read-only. Run: cd ~/code/futon3a && python3 holes/labs/M-memes-arrows/mission_domain_stratify.py
"""
import re, glob, math
from pathlib import Path
from collections import defaultdict

CODE = Path("/home/joe/code"); DATA = CODE / "futon6/data"
WHOLE = (DATA / "mission-wholeness.edn").read_text()
SCOPES = (DATA / "mission-pattern-scopes.edn").read_text()
CLASS = dict(re.findall(r':mission "M-([^"]+)" :class :(\w+)', WHOLE))
APPLIED = {m: re.findall(r'"([^"]+)"', body)
           for m, body in re.findall(r':mission "M-([^"]+)" :applied \[([^\]]*)\]', SCOPES)}

# --- domain by name keyword (NON-circular: name, not patterns) ---
MATH_KW = ("rational-reconstruction", "frontiermath", "prelim", "prior-mathematics",
           "differentiable-math", "prover", "proof", "-math", "diagramprover")
def is_math(name):
    n = name.lower()
    return any(k in n for k in MATH_KW)

missions = sorted(set(CLASS) & set(APPLIED))
math_m  = [m for m in missions if is_math(m)]
stack_m = [m for m in missions if not is_math(m)]
print("=== domain split (by name keyword — audit this list) ===")
print(f"  MATH-domain ({len(math_m)}): {', '.join(sorted(math_m))}")
print(f"  STACK-domain: {len(stack_m)} missions")

def contrast(grp, label, support=3, a=0.5, top=12):
    alive = [m for m in grp if CLASS[m] == "alive"]
    mess  = [m for m in grp if CLASS[m] == "mess"]
    nA, nM = len(alive), len(mess)
    print(f"\n=== {label}: alive n={nA}, mess n={nM} ===")
    if nA == 0 or nM == 0:
        print("   (a class is empty in this stratum — contrast undefined)"); return
    useA, useM = defaultdict(int), defaultdict(int)
    for m in alive:
        for p in set(APPLIED[m]): useA[p] += 1
    for m in mess:
        for p in set(APPLIED[m]): useM[p] += 1
    rows = []
    for p in set(useA) | set(useM):
        A, M = useA[p], useM[p]
        if A + M < support: continue
        lift = math.log2(((A + a)/(nA + a)) / ((M + a)/(nM + a)))
        rows.append((lift, p, A, M))
    rows.sort(reverse=True)
    print(f"  enriched in ALIVE (support>={support}):")
    for lift, p, A, M in rows[:top]:
        print(f"    {lift:+5.2f}  A={A:2d} M={M:2d}  {p}")
    if not rows[:top]: print("    (none clear)")
    print(f"  enriched in MESS:")
    for lift, p, A, M in rows[-6:][::-1]:
        if lift < 0: print(f"    {lift:+5.2f}  A={A:2d} M={M:2d}  {p}")
    # breadth (volume) within stratum
    bA = sum(len(APPLIED[m]) for m in alive)/nA
    bM = sum(len(APPLIED[m]) for m in mess)/nM
    print(f"  mean |applied|: alive={bA:.1f}  mess={bM:.1f}  (is it composition or volume?)")

contrast(stack_m, "STACK-domain contrast (THE decisive test — math removed)")
contrast(math_m,  "MATH-domain contrast (does mgmt-absence track mess even among math missions?)")

# --- within each stratum: do alive missions carry the management patterns more than mess? ---
MGMT = {"expected-free-energy-scorecard","structured-observation-vector","candidate-pattern-action-space",
        "single-source-of-truth","par-as-obligation","unresolved-tensions-at-closure",
        "what-problem-is-this-actually-solving","whose-question-is-this","world-is-hypergraph",
        "task-shape-validation","mission-interface-signature","negative-space-duality",
        "deep-storage-to-active-graph","term-to-channel-traceability","evidence-precision-registry"}
print("\n=== management-pattern presence within strata (MGMT set from full-corpus contrast) ===")
for grp, gl in ((stack_m, "STACK"), (math_m, "MATH")):
    for cls in ("alive", "mess"):
        sub = [m for m in grp if CLASS[m] == cls]
        if not sub: continue
        rate = sum(1 for m in sub if MGMT & set(APPLIED[m]))/len(sub)
        print(f"  {gl:5} {cls:5} (n={len(sub):2d}): applies >=1 MGMT pattern in {rate:.0%}")
print("  read: if STACK alive >> STACK mess, the management signal is real WITHIN domain (not just math-vs-stack).")
