#!/usr/bin/env python3
"""derive_axis_independence_probe.py — focused DERIVE question (serves the G design, not open exploration).

THE question: is the STACK-WIDE coherence signal (Axis 2) orthogonal to the alive/mess label (Axis 1 proxy)?
- orthogonal  -> the two scales are genuinely distinct; G needs both as separate terms, and Axis 2 needs its OWN
  ground truth (alive/mess, a scope-tree-internal metric, cannot supply it).
- co-varying  -> Axis 2 is partly already inside the label; one coupled term may suffice.

Generalises (Joe, 2026-06-23) to 'coherence across SCALES' (Alexander, A Pattern Language: Towns/Buildings/
Construction). Mission+stack is the 2-scale demo; a proof would be inference/proof/theory. Here we test the two
demo scales for independence.

Axis-1 proxy (mission-specific, VALIDATED): management-anchored (anchor in stack-meta constellations {1,2,4,6}).
Axis-2 proxies (stack-wide, the thing under test):
  - c5_any : applies >=1 constellation-5 pattern (stack-coherence/devmap lives here) -- SEMANTIC.
  - reach  : mean cross-mission usage of the applied patterns (how stack-wide the vocabulary is) -- STRUCTURAL.

Read-only. Run: cd ~/code/futon3a && python3 holes/labs/M-memes-arrows/derive_axis_independence_probe.py
"""
import re, statistics as st
from pathlib import Path
from collections import Counter, defaultdict

DATA = Path("/home/joe/code/futon6/data")
SEMI = Path("/home/joe/code/futon3c/holes/excursions/pipeline-semilattice-clusters.edn").read_text()
CLASS = dict(re.findall(r':mission "M-([^"]+)" :class :(\w+)', (DATA/"mission-wholeness.edn").read_text()))
APPLIED = {m: re.findall(r'"([^"]+)"', body) for m, body in
          re.findall(r':mission "M-([^"]+)" :applied \[([^\]]*)\]', (DATA/"mission-pattern-scopes.edn").read_text())}

# slug -> constellation (most-populated on collision)
by = defaultdict(Counter)
for full, cl in re.findall(r':pattern "([^"]+)"\s*:cluster (\d+)', SEMI):
    by[full.split("/")[-1]][int(cl)] += 1
S2C = {n: sorted(c.items(), key=lambda kv: (-kv[1], kv[0]))[0][0] for n, c in by.items()}
MGMT = {1, 2, 4, 6}

# cross-mission usage (DF over all missions with an applied set) = stack-wide usage of a pattern
DF = Counter()
for m, ap in APPLIED.items():
    DF.update(set(ap))

def feats(m):
    ap = [p for p in APPLIED[m]]
    cs = [S2C[p] for p in ap if p in S2C]
    if not cs:
        return None
    anchor = sorted(Counter(cs).items(), key=lambda kv: (-kv[1], kv[0]))[0][0]
    return dict(
        mgmt_anchored = int(anchor in MGMT),                       # Axis 1 (validated)
        c5_any = int(any(c == 5 for c in cs)),                     # Axis 2 semantic
        reach = st.mean(DF[p] for p in ap),                        # Axis 2 structural
        breadth = len(set(cs)),
    )

rows = {m: feats(m) for m in CLASS if CLASS[m] in ("alive", "mess") and m in APPLIED}
rows = {m: f for m, f in rows.items() if f}
alive = [m for m in rows if CLASS[m] == "alive"]
mess  = [m for m in rows if CLASS[m] == "mess"]

def auc(key):
    pos = [rows[m][key] for m in alive]; neg = [rows[m][key] for m in mess]
    wins = sum((p > n) + 0.5*(p == n) for p in pos for n in neg)
    return wins/(len(pos)*len(neg)), st.mean(pos), st.mean(neg)

print(f"alive n={len(alive)}  mess n={len(mess)}\n")
print("=== does each signal DISCRIMINATE alive vs mess? (AUC; 0.5 = no signal) ===")
for k in ("mgmt_anchored", "c5_any", "reach", "breadth"):
    a, mp, mn = auc(k)
    tag = "AXIS-1 (validated)" if k == "mgmt_anchored" else ("AXIS-2" if k in ("c5_any", "reach") else "")
    print(f"  {k:14} AUC={a:.3f}  alive_mean={mp:.2f} mess_mean={mn:.2f}   {tag}")

# orthogonality: does Axis-2 vary INDEPENDENTLY of Axis-1 (mgmt_anchored)?
print("\n=== ORTHOGONALITY: Axis-2 conditioned on Axis-1 (mgmt_anchored) ===")
for k in ("c5_any", "reach"):
    for av in (1, 0):
        grp = [rows[m][k] for m in rows if rows[m]["mgmt_anchored"] == av]
        print(f"  {k:7} | mgmt_anchored={av}: mean={st.mean(grp):.2f} (n={len(grp)})")
# phi/correlation between the two binary axes
def phi(x, y):
    xs = [rows[m][x] for m in rows]; ys = [rows[m][y] for m in rows]
    mx, my = st.mean(xs), st.mean(ys)
    cov = st.mean((a-mx)*(b-my) for a, b in zip(xs, ys))
    sx, sy = st.pstdev(xs), st.pstdev(ys)
    return cov/(sx*sy) if sx and sy else float("nan")
print(f"\n  corr(c5_any, mgmt_anchored) = {phi('c5_any','mgmt_anchored'):+.2f}")
print(f"  corr(c5_any, alive-label)   = {st.mean([rows[m]['c5_any'] for m in alive])-st.mean([rows[m]['c5_any'] for m in mess]):+.2f}  (alive-minus-mess rate)")
print("\nREAD: if an Axis-2 signal has AUC≈0.5 (doesn't track alive/mess) AND low corr with Axis-1,")
print("      the scales are ORTHOGONAL -> G needs both terms + Axis-2 needs its own ground truth.")
