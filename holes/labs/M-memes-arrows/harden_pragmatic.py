#!/usr/bin/env python3
"""harden_pragmatic.py — step (ii), claude-1-ratified: replace the SOFT pragmatic cosine with a
HARD structural endpoint/type-match (an admissibility cut that zeroes non-composers), add a
PERMUTATION NULL for the decision margin (not 0.5·std), and apply the 3-part commit criterion:
  commit a tension iff  gap(top1-top2) > null-margin  AND  eff-support → 1–2  AND  ≥1 admissible.
If the HARD cut is ALSO flat → that flatness is real signal → structure-learning licensed.

Reuses parse()/SORRY from alexandrian_aif. Re-emits E-fold-engine-wiring-ALEXANDRIAN-AIF.edn
with :channel hardened-pragmatic + the null stats.

Run: cd ~/code/futon3a && .venv/bin/python3 holes/labs/M-memes-arrows/harden_pragmatic.py
"""
import re, glob, math, random
from pathlib import Path
from alexandrian_aif import parse, SORRY, BACKWARD, _edn, LAB, LIB

random.seed(20260622)

# want-HEADS: the type/output tokens each tension's resolution must PRODUCE (the hard endpoint).
# A pattern is pragmatically admissible for a tension iff its THEN actually emits toward these.
WANT_HEADS = {
 "compose":        {"compose","composition","combine","assemble","integrate","fold","merge","construct","wire"},
 "select":         {"select","choose","applicable","criterion","admit","reject","filter","prioritize","triage","relevant","candidate"},
 "topology":       {"order","sequence","dependency","depend","before","after","topology","arrange","structure","architecture"},
 "terminate":      {"stop","terminate","done","halt","complete","finish","saturate","fixpoint","converge","checkpoint","next"},
 "verify-surface": {"surface","hole","undecidable","open","explicit","record","flag","witness","check","verify","gap","sorry"},
}
def toks(s): return set(re.findall(r"[a-z]+", (s or "").lower()))

def main():
    files = glob.glob(str(LIB/"**"/"*.flexiarg"), recursive=True)
    pats = [p for p in (parse(f) for f in files) if p["ifhow"] and p["action"]]
    then_toks = [toks(p["then"]) for p in pats]
    print(f"whole-library: {len(pats)} patterns")

    from sentence_transformers import SentenceTransformer
    import numpy as np
    model = SentenceTransformer("all-MiniLM-L6-v2")
    enc = lambda xs: np.array(model.encode(xs, normalize_embeddings=True, batch_size=128))
    tk = list(SORRY["tensions"])
    P_if = enc([p["ifhow"] for p in pats])
    T_if = enc([SORRY["tensions"][k]["if_however"] for k in tk])
    epi = T_if @ P_if.T                       # epistemic (recognition), unchanged

    # global token pool for the permutation null (random want-head sets of matched size)
    pool = sorted(set().union(*then_toks))

    def rank_admissible(admit_mask, i):
        """given a boolean admissible mask over patterns, return (n, top1, gap, eff_support) by epistemic."""
        idx = [j for j in range(len(pats)) if admit_mask[j]]
        if not idx: return (0, 0.0, 0.0, 0.0)
        scores = sorted((float(epi[i][j]) for j in idx), reverse=True)
        top1 = scores[0]; top2 = scores[1] if len(scores)>1 else (top1-1.0)
        # eff-support over admissible epistemic posterior (tau=0.10)
        m=max(scores); es=[math.exp((s-m)/0.10) for s in scores]; Z=sum(es); ps=[e/Z for e in es]
        eff = math.exp(-sum(p*math.log(p) for p in ps if p>0))
        return (len(idx), top1, top1-top2, eff)

    NPERM=200
    inner={}; committed=[]
    for i,k in enumerate(tk):
        wh = WANT_HEADS[k]
        admit = [len(then_toks[j] & wh) > 0 for j in range(len(pats))]   # HARD endpoint cut
        n, top1, gap, eff = rank_admissible(admit, i)
        # permutation null: random want-head sets of the same size → null gap distribution
        null_gaps=[]
        for _ in range(NPERM):
            rh = set(random.sample(pool, len(wh)))
            am = [len(then_toks[j] & rh) > 0 for j in range(len(pats))]
            null_gaps.append(rank_admissible(am, i)[2])
        null_gaps.sort()
        null_margin = null_gaps[int(0.95*NPERM)-1]
        # 3-part commit criterion
        peaked = (n >= 1) and (gap > null_margin) and (eff <= 2.0)
        best = None
        if n:
            bj = max((j for j in range(len(pats)) if admit[j]), key=lambda j: epi[i][j])
            best = pats[bj]
        decision = "commit" if peaked else "abstain"
        if peaked and best: committed.append((k,best))
        inner[k]={"decision":decision,"n_admissible":n,"top1":round(top1,3),"gap":round(gap,3),
                  "null_margin_95":round(null_margin,3),"gap_beats_null":bool(gap>null_margin),
                  "eff_support":round(eff,2),"collapsed":bool(eff<=2.0),
                  "top":(best["id"] if best else None),"then":(best["then"][:140] if best else None)}

    cids=[b["id"] for _,b in committed]
    all_committed = len(committed)==len(tk)
    wiring_state = "committed" if all_committed else ("partial" if committed else "provisional")
    # exit-abstain verdict (claude-1's clean fork)
    if committed:
        verdict = f"COMMIT exit: {len(committed)}/{len(tk)} tensions peaked above null under the hard cut"
    else:
        verdict = ("STRUCTURE-LEARNING exit: the HARD pragmatic cut is ALSO flat (no tension peaks above null) "
                   "— not the metric now, but genuine under-determination of the sorry: the pattern language is "
                   "missing a resolver for these tensions. (claude-1: this flatness, unlike soft-cosine flatness, is real signal.)")

    out={"id":"wiring/fold-engine-alexandrian-aif","for-sorry":"sorry/fold-engine-cascade-to-wiring",
         "channel":"hardened-pragmatic (structural endpoint/type cut + permutation null)",
         "g-grain":"PROXY: epistemic=cos(IF+HOWEVER) ranked WITHIN a hard pragmatic admissibility set "
                   "(THEN∩want-heads); decision margin = permutation-null 95pct (NOT 0.5·std). Still not real G "
                   "(no forward model), but the pragmatic term is now HARD not soft.",
         "inner":{"tensions":inner,"committed":cids,"wiring-state":wiring_state},
         "outer":{"prediction-backward":sorted(set(BACKWARD)),"observation-forward":sorted(set(cids)),
                  "verdict":verdict,
                  "timescale":"OUTER (slow) — only fires on the structure-learning exit"},
         "supersedes":"the soft-cosine pass (all-abstain, flat — signal-without-separation per claude-1)",
         "provenance":{"derived-by":"harden_pragmatic.py; claude-1 ratification whistle 2026-06-22 (step ii)"}}
    (LAB/"E-fold-engine-wiring-ALEXANDRIAN-AIF.edn").write_text(_edn(out))

    print("\n=== HARDENED pragmatic cut + permutation null ===")
    for k in tk:
        d=inner[k]
        print(f"  [{k:14}] {d['decision']:8} n_adm={d['n_admissible']:4} gap={d['gap']:.3f} null95={d['null_margin_95']:.3f} "
              f"beats-null={d['gap_beats_null']} eff={d['eff_support']} collapsed={d['collapsed']}  top={d['top']}")
    print(f"\n  wiring-state: {wiring_state} | committed {len(committed)}/{len(tk)}")
    print(f"  VERDICT: {verdict}")
    print("\nwrote E-fold-engine-wiring-ALEXANDRIAN-AIF.edn (channel: hardened-pragmatic)")

if __name__ == "__main__":
    main()
