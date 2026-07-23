#!/usr/bin/env python3
"""cascade_serve.py (claude-1) — thin CLI over construct_cascade for the live WM cascade lane.

The v1 visible-non-degenerate-policy lane: given a circumstance |psi> (mission text),
construct the coverage-saturated coherence-greedy cascade, apply the live-judge BUDGET
(parsimony ceiling, set from data), and emit JSON the Clojure judge can read.

Usage:  cascade_serve.py "<psi query text>" [budget=6] [epsilon=0.15]
Emits:  {"psi","size","wholeness","H-coherence","T-intensity","coverage-reward","prior-cost","cascade-score","budget","shown":[...],"truncated"}
"""
import sys
import json
from cascade_construct import chosen_semi_lattice, construct_cascade, load_phylogeny


def main():
    if len(sys.argv) < 2:
        print(json.dumps({"error": "usage: cascade_serve.py '<psi>' [budget] [epsilon]"}))
        return
    psi = sys.argv[1]
    # default 6->20, operator ruling 2026-07-05 (invariant-grade arrivals at
    # ranks 10-16 sat outside the budget-6 window; E-live-loop-2 deposit-002)
    budget = int(sys.argv[2]) if len(sys.argv) > 2 else 20
    eps = float(sys.argv[3]) if len(sys.argv) > 3 else 0.15
    r = construct_cascade(psi, epsilon=eps)
    full = [{"pattern": p, "rel": rel, "mc": mc} for (p, rel, mc) in r["cascade"]]
    shown = full[:budget]
    shown_ids = [row["pattern"] for row in shown]
    phylogeny = load_phylogeny()
    semilattice = chosen_semi_lattice(shown_ids, phylogeny)
    print(json.dumps({
        "psi": psi,
        "size": r["size"],            # full coverage-saturated size
        "wholeness": r["wholeness"], "H-coherence": r["H-coherence"], "T-intensity": r["T-intensity"],
        # Engineering score only: coverage reward minus prior inclusion cost.
        "coverage-reward": r["coverage-reward"], "prior-cost": r["prior-cost"],
        "cascade-score": r["cascade-score"], "lambda": r["lambda"],
        "budget": budget,
        "shown": shown,               # the top-budget strong centres (what the WM displays)
        "semilattice": semilattice,
        "truncated": r["size"] > budget,
    }))


if __name__ == "__main__":
    main()
