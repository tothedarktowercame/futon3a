#!/usr/bin/env python3
"""derive_coherence_probe.py — focused DERIVE question (not open exploration).

THE question (pins down the load-bearing claim of the G(pi) design): does a cascade's co_app-weighted INTERNAL
COHERENCE discriminate ALIVE from MESS missions? If yes, G(pi) needs a cascade-internal (non-additive) coherence
term — which is what makes G genuinely cascade-level rather than a per-pattern bag-sum (the thing slice-2a showed
fails). NON-CIRCULAR: co_app edge weights come from cross-mission co-application, independent of the scope-tree
alive/mess label. To remove single-mission self-contribution we also report the CORROBORATED variant (co_app
weight>=2, i.e. the pair co-occurs beyond this one mission).

Read-only. Run: cd ~/code/futon3a && python3 holes/labs/M-memes-arrows/derive_coherence_probe.py
"""
import re, json, random, itertools, statistics as st
from pathlib import Path
from collections import defaultdict

DATA = Path("/home/joe/code/futon6/data")
CLASS = dict(re.findall(r':mission "M-([^"]+)" :class :(\w+)', (DATA/"mission-wholeness.edn").read_text()))
APPLIED = {m: re.findall(r'"([^"]+)"', body) for m, body in
          re.findall(r':mission "M-([^"]+)" :applied \[([^\]]*)\]', (DATA/"mission-pattern-scopes.edn").read_text())}
phylo = json.load(open(DATA/"pattern-phylogeny-edges.json"))

# weighted co_app adjacency over bare names
W = defaultdict(dict)
for a, b, w in phylo["co_app"]:
    a, b = a.split("/")[-1], b.split("/")[-1]
    if a == b: continue
    W[a][b] = max(W[a].get(b, 0), w); W[b][a] = max(W[b].get(a, 0), w)

def coherence(applied, min_w=1):
    """Internal co_app coherence DENSITY: fraction of within-cascade pattern-pairs that share a co_app edge
    of weight >= min_w. Density (per possible pair) controls for cascade size."""
    pats = [p for p in set(applied)]
    if len(pats) < 2: return None
    pairs = list(itertools.combinations(pats, 2))
    hit = sum(1 for x, y in pairs if W.get(x, {}).get(y, 0) >= min_w)
    return hit / len(pairs)

def auc(pos, neg):
    """P(random pos > random neg) — Mann-Whitney AUC."""
    if not pos or not neg: return float("nan")
    wins = sum((p > n) + 0.5*(p == n) for p in pos for n in neg)
    return wins / (len(pos)*len(neg))

for MINW, tag in ((1, "raw (incl. this mission's own contribution)"),
                  (2, "CORROBORATED (co_app weight>=2 — beyond this single mission) [the clean test]")):
    alive = [coherence(APPLIED[m], MINW) for m in CLASS if CLASS[m]=="alive" and m in APPLIED]
    mess  = [coherence(APPLIED[m], MINW) for m in CLASS if CLASS[m]=="mess"  and m in APPLIED]
    alive = [x for x in alive if x is not None]; mess = [x for x in mess if x is not None]
    a = auc(alive, mess)
    # shuffle null: permute alive/mess labels over the union, recompute AUC
    pool = [(x,"a") for x in alive] + [(x,"m") for x in mess]
    rng = random.Random(20260623); nulls=[]
    for _ in range(2000):
        labs=[c for _,c in pool]; rng.shuffle(labs)
        pa=[x for (x,_),c in zip(pool,labs) if c=="a"]; pm=[x for (x,_),c in zip(pool,labs) if c=="m"]
        nulls.append(auc(pa,pm))
    null_mu, null_sd = st.mean(nulls), st.pstdev(nulls)
    z = (a-0.5)/null_sd if null_sd else float("nan")
    print(f"=== coherence density, {tag} ===")
    print(f"  alive n={len(alive)} mean={st.mean(alive):.3f} | mess n={len(mess)} mean={st.mean(mess):.3f}")
    print(f"  AUC(alive>mess) = {a:.3f}   shuffle-null AUC mean={null_mu:.3f} sd={null_sd:.3f}   z={z:+.2f}")
    print(f"  -> {'DISCRIMINATES' if a>0.5 and z>2 else 'does NOT clearly discriminate'} "
          f"(coherence {'higher' if st.mean(alive)>st.mean(mess) else 'lower'} for alive)\n")
