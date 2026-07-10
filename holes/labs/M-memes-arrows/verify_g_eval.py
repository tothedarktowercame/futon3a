#!/usr/bin/env python3
"""verify_g_eval.py — VERIFY hooks (2)+(3) for M-G-over-cascades (claude-2).

G(pi) = expected TENSION-DISCHARGE across scales; tension = max(0,-kappa), kappa = Ollivier-Ricci curvature
from hook (1) (claude-1: pattern-curvature.json, REAL kappa on the pattern graph; co_mission + co_app).

Hook (2) DISCRIMINATION: does a tension-based G separate ALIVE from MESS cascades, beating a label-shuffle
  null? NO train/test split — tension is LABEL-INDEPENDENT (the Axis-2 point: the signal the alive label
  cannot see). Run on BOTH graphs (combining-methods-as-diagnostic).
Hook (3) SPECIFICITY: is G a property of the ACTUAL cascade vs random same-size sets? (node-kappa only;
  TRUE path-breaking degradation needs edge-kappa + the graph — see verify_g_path.py / future.)

Read-only. Run: cd ~/code/futon3a && python3 holes/labs/M-memes-arrows/verify_g_eval.py
"""
import re, json, random, statistics as st
from pathlib import Path
from collections import defaultdict

ROOT = Path("/home/joe/code"); DATA = ROOT/"futon6/data"; LAB = ROOT/"futon3a/holes/labs/M-memes-arrows"
CLASS = dict(re.findall(r':mission "M-([^"]+)" :class :(\w+)', (DATA/"mission-wholeness.edn").read_text()))
APPLIED = {m: [p.split("/")[-1] for p in re.findall(r'"([^"]+)"', body)] for m, body in
          re.findall(r':mission "M-([^"]+)" :applied \[([^\]]*)\]', (DATA/"mission-pattern-scopes.edn").read_text())}

CURV = json.load(open(LAB/"pattern-curvature.json")) if (LAB/"pattern-curvature.json").exists() else None

def load_kappa(graph):
    if CURV and graph in CURV.get("graphs", {}):
        nk = CURV["graphs"][graph]["node_kappa"]
        return {k.split("/")[-1]: float(v) for k, v in nk.items()}, f"REAL kappa ({graph})"
    return {}, f"MISSING ({graph})"

def tension(p, K):           # strain at a node = max(0, -kappa)
    return max(0.0, -K.get(p, 0.0))

def G_engage(cas, K):        # mean tension the cascade sits on ("goes to tension")
    ks = [tension(p, K) for p in cas if p in K]
    return st.mean(ks) if ks else 0.0

def G_span(cas, K):          # spread of tension across the cascade (bridges strain gradients)
    ks = [tension(p, K) for p in cas if p in K]
    return st.pstdev(ks) if len(ks) > 1 else 0.0

GFNS = {"G_engage": G_engage, "G_span": G_span}
alive = [m for m in CLASS if CLASS[m] == "alive" and m in APPLIED and APPLIED[m]]
mess  = [m for m in CLASS if CLASS[m] == "mess"  and m in APPLIED and APPLIED[m]]

def auc(pos, neg):
    if not pos or not neg: return float("nan")
    return sum((p > n) + 0.5*(p == n) for p in pos for n in neg)/(len(pos)*len(neg))

def eval_graph(graph):
    K, src = load_kappa(graph)
    if not K:
        print(f"\n##### {graph}: {src} — skipped #####"); return
    allpats = sorted({p for m in APPLIED for p in APPLIED[m] if p in K})
    cov = st.mean(sum(1 for p in APPLIED[m] if p in K)/len(APPLIED[m]) for m in alive+mess)
    print(f"\n##### {graph}: {src} | nodes={len(K)} | cascade coverage={cov:.0%} #####")
    rng = random.Random(20260623)
    print("  -- HOOK (2) discrimination (alive vs mess, vs label-shuffle null) --")
    for name, gf in GFNS.items():
        pos = [gf(APPLIED[m], K) for m in alive]; neg = [gf(APPLIED[m], K) for m in mess]
        a = auc(pos, neg); pool = pos + neg; nulls = []
        for _ in range(2000):
            rng.shuffle(pool); nulls.append(auc(pool[:len(pos)], pool[len(pos):]))
        mu, sd = st.mean(nulls), st.pstdev(nulls); z = (a-mu)/sd if sd else float("nan")
        print(f"    {name:9} AUC={a:.3f}  alive={st.mean(pos):.3f} mess={st.mean(neg):.3f}  "
              f"null={mu:.2f}±{sd:.2f} z={z:+.2f}  {'** SIGNAL' if abs(z)>2 else 'no clear signal'}")
    print("  -- HOOK (3) specificity (real cascade vs random same-size) --")
    for name, gf in GFNS.items():
        real, rand = [], []
        for m in alive+mess:
            cas = APPLIED[m]; real.append(gf(cas, K))
            rand.append(st.mean(gf(rng.sample(allpats, min(len(cas), len(allpats))), K) for _ in range(20)))
        print(f"    {name:9} G(real)={st.mean(real):.3f}  G(random)={st.mean(rand):.3f}  diff={st.mean(r-x for r,x in zip(real,rand)):+.3f}")

print(f"alive={len(alive)} mess={len(mess)} | curvature file: {'loaded' if CURV else 'MISSING'}")
for g in ("co_mission", "co_app"):
    eval_graph(g)
print("\nNOTE: hook (3) is specificity (node-kappa); true path-breaking degradation needs edge-kappa + graph.")
