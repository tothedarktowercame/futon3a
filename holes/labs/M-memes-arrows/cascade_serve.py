#!/usr/bin/env python3
"""cascade_serve.py (claude-1) — thin CLI over construct_cascade for the live WM cascade lane.

The v1 visible-non-degenerate-policy lane: given a circumstance |psi> (mission text),
construct the coverage-saturated coherence-greedy cascade, apply the live-judge BUDGET
(parsimony ceiling, set from data), and emit JSON the Clojure judge can read.

Usage:  cascade_serve.py "<psi query text>" [budget=6] [epsilon=0.15]
Emits:  {"psi","size","C","H","T","budget","shown":[{"pattern","rel","mc"}...],"truncated"}
"""
import sys
import json
from cascade_construct import construct_cascade


def main():
    if len(sys.argv) < 2:
        print(json.dumps({"error": "usage: cascade_serve.py '<psi>' [budget] [epsilon]"}))
        return
    psi = sys.argv[1]
    budget = int(sys.argv[2]) if len(sys.argv) > 2 else 6
    eps = float(sys.argv[3]) if len(sys.argv) > 3 else 0.15
    r = construct_cascade(psi, epsilon=eps)
    full = [{"pattern": p, "rel": rel, "mc": mc} for (p, rel, mc) in r["cascade"]]
    shown = full[:budget]
    print(json.dumps({
        "psi": psi,
        "size": r["size"],            # full coverage-saturated size
        "C": r["C"], "H": r["H"], "T": r["T"],
        "budget": budget,
        "shown": shown,               # the top-budget strong centres (what the WM displays)
        "truncated": r["size"] > budget,
    }))


if __name__ == "__main__":
    main()
