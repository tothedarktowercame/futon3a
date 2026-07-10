"""aliveness_v3 reward-before-generator gate (slice-2 v3).

Morphogenetic aliveness = self-evidencing = accuracy - lambda*complexity
(Bayesian model evidence), NOT energy/tension minimization.

  accuracy(S, want)  = want_coverage of the cascade's produces (THEN tokens)
                       against the mission's want (the built-artifact accuracy).
  complexity(S)      = sum_{p in S} -log(base-rate inclusion prior)  [cascade_construct].
  aliveness_v3       = accuracy - lambda * complexity.

This GATE (run before any GFN) checks the reward is real:
  (1) does accuracy / aliveness_v3 separate real SUCCESS from FAIL on the
      closure-folds ground truth (discharge_experiment.GROUND)?  vs a shuffle null.
  (2) ANTI-1=1 control (the core claim): a TRIVIAL cascade (covers a trivial want)
      and a BLOATED-SHELL cascade (many patterns, low coverage) must both score
      DEAD relative to a substantive one.
"""
from __future__ import annotations
import math, sys, random
from pathlib import Path
LAB = "/home/joe/code/futon3a/holes/labs/M-memes-arrows"
sys.path.insert(0, LAB)

from cascade_rollout import salient, move_interface, want_coverage
from alexandrian_aif import parse
from cascade_construct import load_phylogeny, base_rate_prior, pattern_stem
import rollout_execute as rx           # find_flexiarg, corpus_df_drop
from discharge_experiment import GROUND

LAMBDA = 0.05                          # accuracy in [0,1]; complexity ~ size*O(1) -> small lambda
SEED = 20260710


def _prior():
    phy = load_phylogeny()
    return base_rate_prior(phy)        # (prior_by_stem, default)


PRIOR, PRIOR_DEFAULT = _prior()
DROP, _CORPUS = rx.corpus_df_drop()


def complexity(patterns):
    return sum(-math.log(PRIOR.get(pattern_stem(p), PRIOR_DEFAULT)) for p in patterns)


def accuracy(patterns, want):
    """want_coverage of the union of the patterns' THEN-produces against `want`."""
    moves = []
    for p in patterns:
        f = rx.find_flexiarg(pattern_stem(p))
        if not f:
            continue
        d = parse(f)
        moves.append(move_interface(d["id"], d["ifhow"], d["then"], DROP))
    return want_coverage(moves, want), len(moves)


def aliveness_v3(patterns, want, lam=LAMBDA):
    acc, n_moves = accuracy(patterns, want)
    cx = complexity(patterns)
    return acc - lam * cx, acc, cx, n_moves


def auc(scores, labels):
    pos = [s for s, y in zip(scores, labels) if y]
    neg = [s for s, y in zip(scores, labels) if not y]
    if not pos or not neg:
        return float("nan")
    w = sum((a > b) + 0.5 * (a == b) for a in pos for b in neg)
    return w / (len(pos) * len(neg))


def gate():
    rows = []
    for g in GROUND:
        used = g.get("used") or []
        if not used:
            continue
        want = salient(g["problem"], DROP)
        alv, acc, cx, nm = aliveness_v3(used, want)
        rows.append({"scope": g["scope"], "success": bool(g["success"]),
                     "aliveness": alv, "accuracy": acc, "complexity": cx,
                     "n_used": len(used), "n_moves": nm})
    y = [r["success"] for r in rows]
    print(f"closure-folds records with used+moves: {len(rows)}  "
          f"(success {sum(y)} / fail {len(y)-sum(y)})\n")
    for key in ("accuracy", "aliveness"):
        real = auc([r[key] for r in rows], y)
        rng = random.Random(SEED); nulls = []
        for _ in range(200):
            yy = y[:]; rng.shuffle(yy); nulls.append(auc([r[key] for r in rows], yy))
        nulls.sort(); nmax = nulls[int(0.95 * len(nulls))]
        print(f"[{key:9}] AUC(success>fail)={real:.3f}  null-95pct={nmax:.3f}  "
              f"{'PASS' if real > nmax else 'weak'}")

    # spread check (must NOT saturate like scope-L)
    accs = sorted(r["accuracy"] for r in rows)
    print(f"\naccuracy spread: min={accs[0]:.2f} med={accs[len(accs)//2]:.2f} max={accs[-1]:.2f}")

    # ---- ANTI-1=1 CONTROL ----
    print("\n=== ANTI-1=1 CONTROL (substantive must beat trivial AND bloated) ===")
    # substantive: the best real SUCCESS record
    succ = [r for r in rows if r["success"]]
    sub = max(succ, key=lambda r: r["aliveness"]) if succ else None
    # trivial: a tautological want with a single generic pattern (near-zero real coverage)
    triv_want = salient("x equals x it is what it is trivially true", DROP)
    triv_used = (succ[0]["scope"] if False else None) and None
    # use one arbitrary real pattern from a fail record against the trivial want
    any_pat = next((g["used"][0] for g in GROUND if g.get("used")), None)
    t_alv, t_acc, t_cx, _ = aliveness_v3([any_pat], triv_want)
    # bloated: many patterns pulled from across records vs a specific want (low coverage, high complexity)
    bloat = []
    for g in GROUND:
        bloat += (g.get("used") or [])
    bloat = list(dict.fromkeys(bloat))[:20]
    target = next((g for g in GROUND if g.get("used") and g["success"]), None)
    b_want = salient(target["problem"], DROP)
    b_alv, b_acc, b_cx, _ = aliveness_v3(bloat, b_want)
    if sub:
        print(f"  substantive [{sub['scope']}] aliveness={sub['aliveness']:+.3f} "
              f"(acc={sub['accuracy']:.2f} cx={sub['complexity']:.2f})")
    print(f"  trivial-want single-pattern aliveness={t_alv:+.3f} (acc={t_acc:.2f} cx={t_cx:.2f})")
    print(f"  bloated-shell ({len(bloat)} patterns) aliveness={b_alv:+.3f} (acc={b_acc:.2f} cx={b_cx:.2f})")
    ok_triv = sub and sub["aliveness"] > t_alv
    ok_bloat = sub and sub["aliveness"] > b_alv
    print(f"  substantive > trivial : {'PASS' if ok_triv else 'FAIL'}")
    print(f"  substantive > bloated : {'PASS' if ok_bloat else 'FAIL'}")


if __name__ == "__main__":
    gate()
