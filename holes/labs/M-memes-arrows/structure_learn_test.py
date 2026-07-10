#!/usr/bin/env python3
"""structure_learn_test.py — the structure-learning recovery test (Joe's experiment).

We authored 3 patterns characterizing the backward (compile) + forward (search) fold
solutions as a FAMILY (structure-learned-patterns/cascade-fold-*.flexiarg). The AIF
process had said the language was under-determined for the fold sorry. So: PLANT them in
the 1000-pattern library and ask — can the method now RECOVER them (top + peak above null)?
If yes, structure-learning closes the loop. If even bullseye patterns don't peak, the
method's discrimination is the limit, not the language.

Run: cd ~/code/futon3a && .venv/bin/python3 holes/labs/M-memes-arrows/structure_learn_test.py
"""
import re, glob, math, random
from pathlib import Path
from alexandrian_aif import parse, SORRY, LIB, LAB
from harden_pragmatic import WANT_HEADS, toks
random.seed(20260622)

NEW_DIR = LAB/"structure-learned-patterns"
def is_new(pid): return pid.startswith("cascade-fold/")

def main():
    files = glob.glob(str(LIB/"**"/"*.flexiarg"), recursive=True) + glob.glob(str(NEW_DIR/"*.flexiarg"))
    pats = [p for p in (parse(f) for f in files) if p["ifhow"] and p["action"]]
    n_new = sum(is_new(p["id"]) for p in pats)
    print(f"library+planted: {len(pats)} patterns ({n_new} planted cascade-fold/*)")
    then_toks = [toks(p["then"]) for p in pats]

    from sentence_transformers import SentenceTransformer
    import numpy as np
    model = SentenceTransformer("all-MiniLM-L6-v2")
    enc = lambda xs: np.array(model.encode(xs, normalize_embeddings=True, batch_size=128))
    tk = list(SORRY["tensions"])
    P_if = enc([p["ifhow"] for p in pats]); P_ac = enc([p["action"] for p in pats])
    T_if = enc([SORRY["tensions"][k]["if_however"] for k in tk])
    T_wt = enc([SORRY["tensions"][k]["want"] for k in tk])
    epi = T_if @ P_if.T; pra = T_wt @ P_ac.T
    soft = epi + pra
    pool = sorted(set().union(*then_toks))

    def adm_rank(mask, i):
        idx=[j for j in range(len(pats)) if mask[j]]
        if not idx: return (0,None,0.0,0.0,0.0)
        idx.sort(key=lambda j:-epi[i][j])
        sc=[float(epi[i][j]) for j in idx]; top1=sc[0]; top2=sc[1] if len(sc)>1 else top1-1
        m=max(sc); es=[math.exp((s-m)/0.10) for s in sc]; Z=sum(es); ps=[e/Z for e in es]
        eff=math.exp(-sum(p*math.log(p) for p in ps if p>0))
        return (len(idx), pats[idx[0]]["id"], top1, top1-top2, eff)

    print("\n=== SOFT channel (cos epi+pra over WHOLE set): where do the planted patterns rank? ===")
    soft_recovered=0
    for i,k in enumerate(tk):
        order=list(np.argsort(-soft[i]))
        rank_new=next((r for r,j in enumerate(order) if is_new(pats[j]["id"])), None)
        top=pats[order[0]]["id"]
        if is_new(top): soft_recovered+=1
        print(f"  [{k:14}] top={top:46} | best planted rank={rank_new}")

    print("\n=== HARDENED channel (endpoint cut + permutation null): do the planted patterns PEAK? ===")
    NPERM=200; committed=[]
    for i,k in enumerate(tk):
        wh=WANT_HEADS[k]
        mask=[len(then_toks[j]&wh)>0 for j in range(len(pats))]
        n,top,top1,gap,eff = adm_rank(mask,i)
        nulls=[]
        for _ in range(NPERM):
            rh=set(random.sample(pool,len(wh)))
            nm=[len(then_toks[j]&rh)>0 for j in range(len(pats))]
            nulls.append(adm_rank(nm,i)[3])
        nulls.sort(); margin=nulls[int(0.95*NPERM)-1]
        peak=(n>=1) and (gap>margin) and (eff<=2.0)
        new_top=is_new(top) if top else False
        if peak and new_top: committed.append((k,top))
        print(f"  [{k:14}] top={str(top):46} new={new_top} gap={gap:.3f} null95={margin:.3f} beats={gap>margin} eff={eff:.2f} commit={peak}")

    print(f"\n=== VERDICT ===")
    print(f"  SOFT: planted pattern is #1 for {soft_recovered}/{len(tk)} tensions")
    print(f"  HARDENED commit on a planted pattern: {len(committed)}/{len(tk)}  {[c[0] for c in committed]}")
    if committed:
        print("  -> STRUCTURE-LEARNING CLOSES THE LOOP: authoring the missing resolver patterns lets")
        print("     the method recover+commit them above null. The earlier abstain was a real language gap.")
    elif soft_recovered:
        print("  -> PARTIAL: the method RANKS the planted patterns top (recovery in the soft channel) but the")
        print("     hardened+null gate still won't peak — discrimination limit is the metric, not the language.")
    else:
        print("  -> NEGATIVE: even bullseye planted patterns don't surface — the method's discrimination is the limit.")

if __name__ == "__main__":
    main()
