#!/usr/bin/env python3
"""mlearning_foldability_probe.py — WHY does M-learning-loop abstain, and what
would crack it? No cherry-picking: embed the mission's real IDENTIFY ψ, rank the
WHOLE pattern library by relevance, mark which patterns the fold-engine v1 can
actually fold (its RULES table), and show where the foldable ones land vs the
argmax-F cascade that was actually chosen.

Answers: is M-learning-loop's neighborhood foldless (=> must write patterns/rules),
or are foldable patterns present but out-ranked/epsilon-starved (=> resize cascade)?
"""
import sys, json
sys.path.insert(0, "/home/joe/code/futon3a/holes/labs/M-memes-arrows")
import cascade_construct as cc
from offramp_cascade import identify_psi

DOC = "/home/joe/code/futon5a/holes/missions/M-learning-loop.md"

# The fold-engine v1 RULES table (foldable pattern stems -> box role), transcribed
# from fold_engine.clj so the probe needs no Clojure runtime.
RULES = {
    "prototype-structure-checklist": "selector", "constraint-tension-resolution": "match",
    "tactic-algebra-interference": "match", "parametric-tension-dissolution": "fold-step",
    "route-exploration-and-pivot": "fold-step", "next-steps-to-done": "fixpoint",
    "prototype-alignment-role": "emit", "prototype-alignment-tension": "emit",
    "prototype-alignment-bridge": "(discipline)", "devmap-scope-discipline": "(scope)",
    "pattern-as-strategy": "selector", "learning-event-detection": "match",
    "artifact-entity-mention-grounding": "fold-step", "proof-as-social-process": "fixpoint",
    "futon-bridge-health": "emit", "mission-interface-signature": "(scope)",
}
BOX_ROLES = {"selector", "match", "fold-step", "fixpoint", "emit"}

def main():
    psi = identify_psi(DOC)
    qv = cc._embed(psi)
    ranked = sorted(((cc.cos(qv, v), pid) for pid, v in cc.EMB.items()), reverse=True)
    stem = cc.pattern_stem

    # where does each foldable pattern rank for THIS mission's psi?
    rank_of = {}
    for i, (rel, pid) in enumerate(ranked):
        s = stem(pid)
        if s in RULES and s not in rank_of:
            rank_of[s] = (i + 1, rel, pid)

    print(f"psi_chars={len(psi)}  library_size={len(cc.EMB)}\n")
    print("=== TOP 20 patterns by relevance to M-learning-loop's IDENTIFY ===")
    print(f"{'rank':>4} {'rel':>6}  {'fold?':<12} pattern")
    for i, (rel, pid) in enumerate(ranked[:20]):
        s = stem(pid)
        role = RULES.get(s, "")
        mark = f"BOX:{role}" if role in BOX_ROLES else (role if role else "-")
        print(f"{i+1:>4} {rel:>6.3f}  {mark:<12} {s}")

    print("\n=== every FOLDABLE pattern, and where it ranks for this psi ===")
    print(f"{'rank':>4} {'rel':>6}  {'role':<10} pattern")
    for s, (rk, rel, pid) in sorted(rank_of.items(), key=lambda kv: kv[1][0]):
        print(f"{rk:>4} {rel:>6.3f}  {RULES[s]:<10} {s}")
    box_foldables = [s for s in rank_of if RULES[s] in BOX_ROLES]
    best = min((rank_of[s] for s in box_foldables), key=lambda t: t[0]) if box_foldables else None
    print(f"\nbest BOX-producing foldable: rank {best[0]} rel {best[1]:.3f} "
          f"({stem(best[2])})" if best else "NO box-producing foldable in library")

    # what the argmax-F cascade actually chose (mirror offramp_cascade sweep)
    phy = cc.load_phylogeny()
    bestF = None
    for e in [0.02, 0.08, 0.12, 0.15, 0.18, 0.22, 0.30, 0.45]:
        r = cc.construct_cascade(psi, epsilon=e, phylogeny=phy)
        if bestF is None or r["F-free-energy"] > bestF[1]:
            bestF = (e, r["F-free-energy"], r)
    e, F, r = bestF
    chosen = [stem(p) for (p, _rl, _mc) in r["cascade"]]
    print(f"\n=== argmax-F cascade actually chosen: eps*={e} F={F:.3f} size={r['size']} ===")
    for p in chosen:
        role = RULES.get(stem(p), "")
        print(f"    {stem(p):<34} {'BOX:'+role if role in BOX_ROLES else (role or 'UNFOLDABLE')}")

    # what if we FORCE bigger cascades? does a box-producing foldable ever enter with F>0?
    print("\n=== cascade contents as epsilon rises (does a foldable box-pattern ever enter?) ===")
    for e in [0.02, 0.08, 0.15, 0.30, 0.45, 0.6, 0.8]:
        r = cc.construct_cascade(psi, epsilon=e, phylogeny=phy)
        ids = [stem(p) for (p, _rl, _mc) in r["cascade"]]
        foldable_boxes = [p for p in ids if RULES.get(p) in BOX_ROLES]
        print(f"  eps={e:<4} size={r['size']:<2} F={r['F-free-energy']:>6.3f}  "
              f"box-foldables={foldable_boxes or '[]'}  cascade={ids}")

if __name__ == "__main__":
    main()
