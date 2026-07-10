#!/usr/bin/env python3
"""alexandrian_aif.py — the AIF-robust forward fold (claude-1-ratified operationalization).

Runs the cascade→sorry→wiring process again from the top, with the AIF conditions baked in:
  - WIDEN: search the WHOLE library (not the topical 10) — relax the low-precision topical prior.
  - per-FAILURE-MODE tensions (finer than the 3 blobs).
  - TWO channels, summed at equal footing (claude-1 Q2): epistemic = cos(tension, pattern IF+HOWEVER);
    pragmatic = cos(want, pattern THEN+conclusion) — the cheap grounded endpoint/"does the THEN move
    toward the WANT" proxy. NO free weight (a weight would be an outer-loop hyperprior).
  - score is typed HONESTLY as a PROXY, NOT G (claude-1 hard req): g-grain = epistemic+pragmatic proxies.
  - PRECISION (the knob): top1, top1-top2 gap in RAW score space (tau-invariant), eff-support=exp(entropy).
  - ABSTAIN is a first-class scored policy (the off-continuity null), in the distribution.
  - COMMIT: per-tension peak = a progress-marker prefix; COMMITTED wiring needs the JOINT to be peaked
    AND admissible (the committed resolvers cohere), not merely all-marginals-peaked.
  - :outer stores prediction(backward pipeline)-vs-observation(forward), not a verdict boolean (R8).
  - emits E-fold-engine-wiring-ALEXANDRIAN-AIF.edn with :inner (this fold) / :outer (structure-learning).

Run: cd ~/code/futon3a && .venv/bin/python3 holes/labs/M-memes-arrows/alexandrian_aif.py
"""
import json, re, glob, math
from pathlib import Path

LAB = Path("/home/joe/code/futon3a/holes/labs/M-memes-arrows")
LIB = Path("/home/joe/code/futon3/library")
PHYLO = Path("/home/joe/code/futon6/data/pattern-phylogeny-edges.json")

SORRY = {
 "want": "an executable fold engine that turns a cascade of patterns into a wiring diagram plus surfaced policy-holes",
 "tensions": {
   "compose":        {"if_however":"A cascade of patterns expresses how to fill a hole, however there is no executable procedure to compose them into one construction.",
                      "want":"a mechanism that composes multiple pattern contributions into one coherent construction"},
   "select":         {"if_however":"Many candidate patterns could each contribute, however there is no principled criterion for which actually apply to this problem.",
                      "want":"a selection criterion that admits applicable patterns and rejects irrelevant ones"},
   "topology":       {"if_however":"The selected contributions must be wired in some order, however nothing derives which feeds which.",
                      "want":"a derivation of the composition order and dependency topology among the contributions"},
   "terminate":      {"if_however":"The construction must stop, however there is no rule for when it is complete.",
                      "want":"a termination or done criterion that halts the construction"},
   "verify-surface": {"if_however":"The construction must be checkable and some decisions are undecidable, however nothing surfaces those as explicit holes.",
                      "want":"a mechanism that surfaces undecidable obligations as explicit policy-holes instead of hiding them"},
 }}

# the BACKWARD prediction (the hand-derived box-warrants) — recorded as a prediction for :outer
BACKWARD = ["devmap-coherence/prototype-structure-checklist","math-strategy/constraint-tension-resolution",
            "math-formalization/tactic-algebra-interference","math-informal/parametric-tension-dissolution",
            "math-strategy/route-exploration-and-pivot","devmap-coherence/next-steps-to-done",
            "devmap-coherence/prototype-alignment-role","devmap-coherence/prototype-alignment-tension"]

def block(text, start, ends):
    m = re.search(r"(?im)^\s*\+?\s*" + re.escape(start) + r"\s*:?", text)
    if not m: return ""
    rest = text[m.end():]; cut = len(rest)
    for e in ends:
        em = re.search(r"(?im)^\s*\+?\s*" + re.escape(e) + r"\s*:?", rest)
        if em: cut = min(cut, em.start())
    return re.sub(r"\s+"," ", rest[:cut]).strip()

def parse(path):
    t = Path(path).read_text(errors="ignore")
    mid = re.search(r"@flexiarg\s+(\S+)", t)
    pid = mid.group(1) if mid else f"{Path(path).parent.name}/{Path(path).stem}"
    ctx = block(t,"context",["HOWEVER","THEN"]); how = block(t,"HOWEVER",["THEN","QUALITY-CRITERIA"])
    then = block(t,"THEN",["QUALITY-CRITERIA","BECAUSE","context"])
    cm = re.search(r"(?is)!\s*conclusion\s*:(.*?)(?:\+\s*context|$)", t)
    concl = re.sub(r"\s+"," ", cm.group(1)).strip() if cm else ""
    return {"id":pid, "ifhow":(ctx+" "+how).strip(), "action":(then+" "+concl).strip(), "then":then}

def softmax(xs, tau):
    m = max(xs); es = [math.exp((x-m)/tau) for x in xs]; s = sum(es); return [e/s for e in es]
def entropy(ps): return -sum(p*math.log(p) for p in ps if p>0)

def main():
    files = glob.glob(str(LIB/"**"/"*.flexiarg"), recursive=True)
    pats = [p for p in (parse(f) for f in files) if p["ifhow"] and p["action"]]
    print(f"whole-library search: {len(pats)}/{len(files)} patterns with readable IF+HOWEVER+THEN")

    from sentence_transformers import SentenceTransformer
    import numpy as np
    model = SentenceTransformer("all-MiniLM-L6-v2")
    enc = lambda xs: np.array(model.encode(xs, normalize_embeddings=True, batch_size=128))
    P_if = enc([p["ifhow"] for p in pats])      # epistemic side
    P_ac = enc([p["action"] for p in pats])     # pragmatic side
    tk = list(SORRY["tensions"])
    T_if = enc([SORRY["tensions"][k]["if_however"] for k in tk])
    T_wt = enc([SORRY["tensions"][k]["want"] for k in tk])

    epi = T_if @ P_if.T            # (tension x pattern) recognition
    pra = T_wt @ P_ac.T            # (tension x pattern) does-THEN-move-toward-want
    score = epi + pra             # equal footing, no free weight (claude-1 Q2)

    alls = score.flatten()
    floor = float(alls.mean() + 0.5*alls.std())   # data-driven ABSTAIN/commit floor (not magic)
    delta = float(0.5*alls.std())                 # gap threshold (raw score space, tau-invariant)
    tau = 0.10                                    # UNCALIBRATED knob (flagged)

    # phylogeny edges for real topology among committed resolvers
    edges = set()
    try:
        pe = json.load(open(PHYLO))
        for e in (pe.get("edges") or pe if isinstance(pe,list) else pe.get("edges",[])):
            a,b = (e[0],e[1]) if isinstance(e,(list,tuple)) else (e.get("from"),e.get("to"))
            if a and b: edges.add((a,b))
    except Exception as ex:
        print("  (phylogeny load skipped:", ex, ")")

    inner_tensions = {}
    committed = []
    for i,k in enumerate(tk):
        row = score[i]
        order = list(np.argsort(-row))
        top1, top2 = float(row[order[0]]), float(row[order[1]])
        # abstain is a scored policy in the distribution (off-continuity null at `floor`)
        with_abstain = [float(row[j]) for j in order[:8]] + [floor]
        post = softmax(with_abstain, tau)
        eff = math.exp(entropy(post))
        gap = top1 - top2
        peaked = (top1 >= floor) and (gap >= delta)
        abstain_wins = post[-1] >= max(post[:-1])
        best = pats[order[0]]
        decision = "commit" if (peaked and not abstain_wins) else "abstain/widen"
        if decision == "commit":
            committed.append((k, best))
        inner_tensions[k] = {
            "decision": decision,
            "top": best["id"], "top1": round(top1,3), "gap": round(gap,3),
            "eff_support": round(eff,2), "abstain_p": round(post[-1],3),
            "epistemic": round(float(epi[i][order[0]]),3), "pragmatic": round(float(pra[i][order[0]]),3),
            "runner_up": pats[order[1]]["id"],
            "then": best["then"][:160],
        }

    # JOINT commit (not conjunction-of-marginals): all tensions committed AND the committed set coheres
    coh = None
    if len(committed) >= 2:
        idx = [pats.index(b) for _,b in committed]
        sub = P_ac[idx]; M = sub @ sub.T
        coh = float((M.sum()-len(idx))/(len(idx)*(len(idx)-1)))   # mean off-diagonal action coherence
    joint_admissible = (coh is not None and coh >= floor*0.5)
    all_committed = len(committed) == len(tk)
    wiring_state = "committed" if (all_committed and joint_admissible) else "provisional"

    # boxes = committed resolvers' THENs; topology from phylogeny among them (real, not fiat)
    cids = [b["id"] for _,b in committed]
    topo = [[a,b] for a in cids for b in cids if a!=b and ((a,b) in edges)]
    boxes = [{"tension":k, "pattern":b["id"], "then":b["then"][:140]} for k,b in committed]

    # :outer — prediction (backward) vs observation (forward), NOT a verdict (claude-1 Q4.4 / R8)
    observed = set(cids)
    predicted = set(BACKWARD)
    overlap = sorted(observed & predicted)
    # NOT degenerate-true: structure-learning needs an ACTUAL peaked-but-divergent commit,
    # not an empty committed set. Empty commit = abstain = precision too low to license anything.
    divergence_persists = (len(committed) > 0) and (len(overlap) <= 1)
    if len(committed) == 0:
        sl = ("NOT licensed — ABSTAINED on all tensions: the posterior is flat (low decision precision; "
              "gaps << gap-delta, eff-support near-uniform). cosine over IF+HOWEVER cannot peak the posterior. "
              "Widen/sharpen the RETRIEVAL CHANNEL (e.g. the sigil/lens/structural readings) BEFORE any commit "
              "or structure-learning — committing or restructuring now would be acting on a flat field.")
    elif divergence_persists:
        sl = "licensed: a widened, PEAKED match still diverges from the backward prediction"
    else:
        sl = ("NOT licensed: committed resolvers overlap the backward prediction (backward was not confabulated) "
              "or the match was not peaked — would be chasing noise")

    out = {
      "id": "wiring/fold-engine-alexandrian-aif",
      "for-sorry": "sorry/fold-engine-cascade-to-wiring",
      "g-grain": "PROXY-not-G: epistemic=cos(tension,IF+HOWEVER) + pragmatic=cos(want,THEN+concl); equal footing; "
                 "NOT real EFE (no forward model); abstain=off-continuity-null; tau UNCALIBRATED",
      "scope": {"searched": "whole-library", "patterns": len(pats), "tau": tau,
                "commit-floor": round(floor,3), "gap-delta": round(delta,3),
                "note":"floor/delta are data-driven (mean+0.5std / 0.5std of all scores), not magic"},
      "inner": {
        "tensions": inner_tensions,
        "committed-prefix": cids,
        "joint": {"all-tensions-committed": all_committed,
                  "committed-set-coherence": (round(coh,3) if coh is not None else None),
                  "joint-admissible": joint_admissible},
        "wiring-state": wiring_state,
        "boxes": boxes,
        "topology-from-phylogeny": topo,
        "abstained-tensions": [k for k in tk if inner_tensions[k]["decision"]!="commit"],
      },
      "outer": {
        "prediction-backward": sorted(predicted),
        "observation-forward": sorted(observed),
        "overlap": overlap,
        "divergence-persists-under-widened-peaked-match": divergence_persists,
        "structure-learning": sl,
        "timescale": "OUTER (slow) — model/pattern update; do not fuse with the inner fold",
      },
      "open-sorry": {"id":"sorry/cosine-is-not-info-gain", "kind":":prototyping-forward",
                     "note":"epistemic channel uses cosine as a PROXY; true epistemic value = uncertainty-reduction "
                            "over the rest of the wiring (claude-1 Q4.2). Replace when a forward model exists."},
      "provenance": {"derived-by":"alexandrian_aif.py; claude-1-ratified operationalization (whistle 2026-06-22)",
                     "supersedes-proxy-of":"E-fold-engine-wiring-ALEXANDER.edn (first-pass, topical-only, epistemic-only)"},
    }
    (LAB/"E-fold-engine-wiring-ALEXANDRIAN-AIF.edn").write_text(_edn(out))
    print("\n=== INNER (this fold) ===")
    for k in tk:
        d = inner_tensions[k]
        print(f"  [{k:14}] {d['decision']:13} top={d['top']:48} top1={d['top1']} gap={d['gap']} eff={d['eff_support']} abst={d['abstain_p']}  (epi {d['epistemic']}/pra {d['pragmatic']})")
    print(f"\n  wiring-state: {wiring_state}  | committed {len(committed)}/{len(tk)} | joint-coherence {coh if coh is None else round(coh,3)} (floor*0.5={round(floor*0.5,3)})")
    print(f"  abstained: {out['inner']['abstained-tensions']}")
    print("\n=== OUTER (structure-learning, slow timescale) ===")
    print(f"  backward predicted: {sorted(predicted)}")
    print(f"  forward observed  : {sorted(observed)}")
    print(f"  overlap: {overlap}  | divergence-persists: {divergence_persists}")
    print(f"  -> {out['outer']['structure-learning']}")
    print("\nwrote E-fold-engine-wiring-ALEXANDRIAN-AIF.edn")

def _edn(x, ind=0):
    sp = " "*ind
    if isinstance(x, dict):
        items = "\n".join(f"{sp} {_k(k)} {_edn(v, ind+2)}" for k,v in x.items())
        return "{\n"+items+"}"
    if isinstance(x, list):
        if not x: return "[]"
        return "["+ " ".join(_edn(v, ind) for v in x) +"]"
    if isinstance(x, bool): return "true" if x else "false"
    if x is None: return "nil"
    if isinstance(x, (int,float)): return str(x)
    return json.dumps(str(x))
def _k(k):
    return ":"+k if re.match(r"^[a-zA-Z][\w/-]*$", str(k)) else json.dumps(str(k))

if __name__ == "__main__":
    main()
