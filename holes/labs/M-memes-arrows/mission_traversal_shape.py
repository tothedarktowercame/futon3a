#!/usr/bin/env python3
"""mission_traversal_shape.py — SLICE-2a STRUCTURAL phase: the cascade traversal-shape over the semilattice.

Reuses the existing hierarchical clustering (futon3c .../pipeline-semilattice-clusters.edn, E-pipeline-
pipecleaner). A cascade = a path through the semilattice; cascade-FORMATION shape = how a mission's :applied
patterns are distributed across the pattern-constellations (P0..P17). Hypothesis (Joe): ALIVE missions traverse
CROSS-CUTTING (spread across the stack-meta constellations P1/P2/P4/P6); MESS missions are STUCK in one
constellation (P13 math). The volume confound is controlled: breadth/|applied| and top-cluster concentration are
volume-robust, so a difference there is structural, not "alive just applies more."

Read-only. Run: cd ~/code/futon3a && python3 holes/labs/M-memes-arrows/mission_traversal_shape.py
"""
import re
from pathlib import Path
from collections import defaultdict, Counter
import statistics as st

CODE = Path("/home/joe/code"); DATA = CODE / "futon6/data"
SEMI = (CODE / "futon3c/holes/excursions/pipeline-semilattice-clusters.edn").read_text()
CLASS = dict(re.findall(r':mission "M-([^"]+)" :class :(\w+)', (DATA/"mission-wholeness.edn").read_text()))
APPLIED = {m: re.findall(r'"([^"]+)"', body) for m, body in
          re.findall(r':mission "M-([^"]+)" :applied \[([^\]]*)\]', (DATA/"mission-pattern-scopes.edn").read_text())}

# pattern-membership: "category/name" -> cluster. mission :applied uses BARE name -> strip category.
PCLUSTER, collisions = {}, 0
for full, cl in re.findall(r':pattern "([^"]+)"\s*:cluster (\d+)', SEMI):
    bare = full.split("/")[-1]
    if bare in PCLUSTER and PCLUSTER[bare] != int(cl): collisions += 1
    PCLUSTER[bare] = int(cl)

LABEL = {0:"collab/writing", 1:"futon-theory/coord", 2:"aif/agent", 3:"training/equity", 4:"f6/ai4ci/hdm",
         5:"devmap/code", 6:"invariant/structure", 7:"scan/workflow", 8:"agency/musn/realtime", 9:"iching",
         10:"vsat", 11:"liberation", 12:"exotic", 13:"MATH", 14:"enrichment", 15:"campaign", 16:"sidecar",
         17:"iiching/exotype"}
MGMT_REGION = {1,2,4,6}

def clusters_of(m):
    return [PCLUSTER[p] for p in APPLIED[m] if p in PCLUSTER]

def shape(m):
    cs = clusters_of(m)
    if not cs: return None
    n = len(cs); distinct = len(set(cs))
    top = Counter(cs).most_common(1)[0][1]
    return dict(n=n, distinct=distinct, norm_breadth=distinct/n, concentration=top/n,
                in_mgmt=bool(set(cs)&MGMT_REGION), in_math=13 in cs, dom=Counter(cs).most_common(1)[0][0])

groups = {c:[m for m in CLASS if CLASS[m]==c and m in APPLIED] for c in ("alive","mess","pipeline")}
resolvable = sum(1 for m in APPLIED for p in APPLIED[m] if p in PCLUSTER)
total = sum(len(APPLIED[m]) for m in APPLIED)
print(f"pattern->cluster map: {len(PCLUSTER)} patterns ({collisions} bare-name collisions); "
      f"applied-pattern resolvability {resolvable}/{total} = {resolvable/total:.0%}\n")

print("=== traversal shape by class (mean over missions with >=1 resolvable pattern) ===")
print(f"  {'class':9} {'n':>4} {'|app|':>6} {'distinct':>9} {'norm_breadth':>13} {'concentration':>14} {'%math-dom':>10}")
for c, grp in groups.items():
    S = [shape(m) for m in grp]; S = [s for s in S if s]
    if not S: continue
    mathdom = sum(1 for s in S if s["dom"]==13)/len(S)
    print(f"  {c:9} {len(S):>4} {st.mean(s['n'] for s in S):>6.1f} {st.mean(s['distinct'] for s in S):>9.1f} "
          f"{st.mean(s['norm_breadth'] for s in S):>13.2f} {st.mean(s['concentration'] for s in S):>14.2f} {mathdom:>9.0%}")
print("  norm_breadth = distinct-clusters / |applied| (1.0 = every pattern a different cluster; volume-robust)")
print("  concentration = largest single-cluster share (1.0 = all patterns in ONE cluster; volume-robust)")

print("\n=== VOLUME CONTROL: same, restricted to |applied|>=3 (so 'alive applies more' can't explain it) ===")
for c, grp in groups.items():
    S = [shape(m) for m in grp]; S = [s for s in S if s and s["n"]>=3]
    if not S: continue
    print(f"  {c:9} n={len(S):>3}  distinct={st.mean(s['distinct'] for s in S):.1f}  "
          f"norm_breadth={st.mean(s['norm_breadth'] for s in S):.2f}  concentration={st.mean(s['concentration'] for s in S):.2f}  "
          f"math-dom={sum(1 for s in S if s['dom']==13)/len(S):.0%}")

print("\n=== cluster occupancy: where do each class's applied patterns LAND? (share of pattern-instances) ===")
for c, grp in groups.items():
    cnt = Counter()
    for m in grp: cnt.update(clusters_of(m))
    tot = sum(cnt.values()) or 1
    top = cnt.most_common(6)
    print(f"  {c:9} (tot={tot}): " + "  ".join(f"P{k}:{LABEL[k]}={v/tot:.0%}" for k,v in top))

print("\n=== clean subsets (the training core) ===")
def show(grp, name):
    S = [shape(m) for m in grp]; S=[s for s in S if s]
    if not S: print(f"  {name}: none"); return
    print(f"  {name} (n={len(S)}): distinct={st.mean(s['distinct'] for s in S):.1f}  "
          f"norm_breadth={st.mean(s['norm_breadth'] for s in S):.2f}  concentration={st.mean(s['concentration'] for s in S):.2f}  "
          f"in_mgmt={sum(s['in_mgmt'] for s in S)/len(S):.0%}  math-dom={sum(1 for s in S if s['dom']==13)/len(S):.0%}")
MGMT={"expected-free-energy-scorecard","structured-observation-vector","candidate-pattern-action-space",
      "single-source-of-truth","par-as-obligation","unresolved-tensions-at-closure","what-problem-is-this-actually-solving",
      "world-is-hypergraph","all-or-nothing","aif-as-environment-not-instruction","term-to-channel-traceability",
      "evidence-precision-registry","policy-precision-commitment-temperature","interest-event-vocabulary",
      "mission-interface-signature","task-shape-validation","negative-space-duality"}
pos=[m for m in groups["alive"] if len(set(APPLIED[m]))>=3 and (MGMT&set(APPLIED[m]))]
neg=[m for m in groups["mess"]  if len(set(APPLIED[m]))>=3 and not (MGMT&set(APPLIED[m]))]
show(pos,"clean POSITIVES (alive,rich,mgmt) ")
show(neg,"clean NEGATIVES (mess,rich,no-mgmt)")
