"""aliveness_v3 gate v2 (Slice 1.5) — VALIDITY check of the obligation-unification
accuracy limb on the closure-folds ground truth.

The v3 gate (aliveness_v3_gate.py) showed the lexical accuracy limb scores known
dischargers at median 0 — a validity failure, not sparsity. This gate re-runs the
same 10 records with obligation_accuracy's tiered unification (T1 exact / T2 stem
/ T3 per-atom semantic @ TAU) and checks VALIDITY, not tuning:

  V1  known positives (success=true, measurable) score accuracy > 0;
  V2  the cosine-artifact negatives stay LOW — strictly below every measurable
      positive (ordering, since AUC on n=10 has granularity 0.0625);
  V3  anti-1=1 control still passes with the new limb;
  V4  missing-flexiarg records are reported MISSING, never silently 0.

TAU=0.60 fixed a priori; 0.55/0.65 reported as sensitivity, not selected on.
Run: cd ~/code/futon3a && .venv/bin/python3 holes/labs/M-memes-arrows/aliveness_v3_gate2.py
"""
from __future__ import annotations
import math, random, sys
LAB = "/home/joe/code/futon3a/holes/labs/M-memes-arrows"
sys.path.insert(0, LAB)

from cascade_rollout import salient, move_interface
from alexandrian_aif import parse
from cascade_construct import load_phylogeny, base_rate_prior, pattern_stem
import rollout_execute as rx
from fold_ground_truth import load_records
from obligation_accuracy import AtomMatcher

GROUND = load_records()   # closure-folds (10) + adjudicated fold-turn deposits

LAMBDA = 0.05
SEED = 20260710
TAU = 0.60          # a priori; see sensitivity section
TAUS = (0.55, 0.60, 0.65)

PRIOR, PRIOR_DEFAULT = (lambda phy: base_rate_prior(phy))(load_phylogeny())
DROP, _CORPUS = rx.corpus_df_drop()
MATCHER = AtomMatcher(use_semantic=True, tau=TAU)


def complexity(patterns):
    return sum(-math.log(PRIOR.get(pattern_stem(p), PRIOR_DEFAULT)) for p in patterns)


def produces_of(patterns):
    """Union of THEN-productions; also reports patterns with no flexiarg (M1)."""
    prod, missing = set(), []
    for p in patterns:
        f = rx.find_flexiarg(pattern_stem(p))
        if not f:
            missing.append(p)
            continue
        d = parse(f)
        # produces from d["action"] = THEN + "! conclusion:" — the conclusion IS the
        # pattern's declared output claim (and `action` is already the pragmatic side
        # used by alexandrian_aif's own want-matching). d["then"] alone drops it.
        prod |= move_interface(d["id"], d["ifhow"], d["action"], DROP)["produces"]
    return prod, missing


def auc(scores, labels):
    pos = [s for s, y in zip(scores, labels) if y]
    neg = [s for s, y in zip(scores, labels) if not y]
    if not pos or not neg:
        return float("nan")
    return sum((a > b) + 0.5 * (a == b) for a in pos for b in neg) / (len(pos) * len(neg))


def gate():
    rows = []
    for g in GROUND:
        used = g.get("used") or []
        if not used:
            continue
        want = salient(g["problem"], DROP)
        prod, missing = produces_of(used)
        row = {"scope": g["scope"], "success": bool(g["success"]),
               "n_used": len(used), "missing": missing, "want": want, "prod": prod}
        if not prod:
            row["status"] = "MISSING"          # V4: no interface text at all
        else:
            row["status"] = "ok"
            row["acc_T1"] = MATCHER.coverage(want, prod, tiers=("T1",))
            row["acc_T12"] = MATCHER.coverage(want, prod, tiers=("T1", "T2"))
            row["acc_T123"] = MATCHER.coverage(want, prod)
            row["report"] = MATCHER.match_report(want, prod)
            row["cx"] = complexity(used)
            row["aliveness"] = row["acc_T123"] - LAMBDA * row["cx"]
        rows.append(row)

    ok = [r for r in rows if r["status"] == "ok"]
    print(f"records: {len(rows)} total; measurable {len(ok)}; "
          f"MISSING {sum(1 for r in rows if r['status'] == 'MISSING')} "
          f"({[r['scope'] for r in rows if r['status'] == 'MISSING']})\n")

    print(f"{'scope':38} {'succ':4} {'T1':>5} {'T1+2':>5} {'T1+2+3':>6}")
    for r in ok:
        print(f"{r['scope']:38} {str(r['success']):4} {r['acc_T1']:5.2f} "
              f"{r['acc_T12']:5.2f} {r['acc_T123']:6.2f}")

    print("\n--- per-atom witnesses (T2/T3 only; audit trail) ---")
    for r in ok:
        t23 = {w: v for w, v in r["report"].items() if v[0] != "T1"}
        if t23:
            print(f"  {r['scope']}: " + "  ".join(
                f"{w}<-[{t}]{p}" for w, (t, p) in sorted(t23.items())))

    # V1/V2 — validity
    pos = [r for r in ok if r["success"]]
    neg = [r for r in ok if not r["success"]]
    v1 = all(r["acc_T123"] > 0 for r in pos)
    v2 = all(n["acc_T123"] < min(p["acc_T123"] for p in pos) for n in neg)
    print(f"\nV1 all measurable positives > 0      : {'PASS' if v1 else 'FAIL'} "
          f"(min positive {min(p['acc_T123'] for p in pos):.2f})")
    print(f"V2 negatives below every positive    : {'PASS' if v2 else 'FAIL'} "
          f"(max negative {max((n['acc_T123'] for n in neg), default=0.0):.2f})")

    # AUC (reported for continuity; n too small to gate on)
    y = [r["success"] for r in ok]
    for key in ("acc_T1", "acc_T123", "aliveness"):
        real = auc([r[key] for r in ok], y)
        rng = random.Random(SEED)
        nulls = sorted(
            auc([r[key] for r in ok], rng.sample(y, len(y))) for _ in range(200))
        print(f"AUC[{key:9}] = {real:.3f}   null-95 = {nulls[int(0.95 * len(nulls))]:.3f}")

    # TAU sensitivity (V2 must hold at every TAU or the tier is doing relevance)
    print("\n--- TAU sensitivity ---")
    for tau in TAUS:
        accs = {r["scope"]: MATCHER.coverage(r["want"], r["prod"], tau=tau) for r in ok}
        pmin = min(accs[r["scope"]] for r in pos)
        nmax = max((accs[r["scope"]] for r in neg), default=0.0)
        print(f"  tau={tau:.2f}: min-positive {pmin:.2f}  max-negative {nmax:.2f}  "
              f"ordering {'PASS' if nmax < pmin else 'FAIL'}")

    # V3 — anti-1=1 control with the new limb
    print("\n=== ANTI-1=1 CONTROL (v2 accuracy) ===")
    sub = max(pos, key=lambda r: r["aliveness"])
    triv_want = salient("x equals x it is what it is trivially true", DROP)
    any_pat = next(g["used"][0] for g in GROUND if g.get("used"))
    t_prod, _ = produces_of([any_pat])
    t_acc = MATCHER.coverage(triv_want, t_prod)
    t_alv = t_acc - LAMBDA * complexity([any_pat])
    bloat = list(dict.fromkeys(p for g in GROUND for p in (g.get("used") or [])))[:20]
    b_prod, _ = produces_of(bloat)
    target = next(g for g in GROUND if g.get("used") and g["success"])
    b_want = salient(target["problem"], DROP)
    b_acc = MATCHER.coverage(b_want, b_prod)
    b_alv = b_acc - LAMBDA * complexity(bloat)
    print(f"  substantive [{sub['scope']}] aliveness={sub['aliveness']:+.3f} "
          f"(acc={sub['acc_T123']:.2f} cx={sub['cx']:.2f})")
    print(f"  trivial-want single-pattern aliveness={t_alv:+.3f} (acc={t_acc:.2f})")
    print(f"  bloated-shell ({len(bloat)} patterns) aliveness={b_alv:+.3f} (acc={b_acc:.2f})")
    print(f"  substantive > trivial : {'PASS' if sub['aliveness'] > t_alv else 'FAIL'}")
    print(f"  substantive > bloated : {'PASS' if sub['aliveness'] > b_alv else 'FAIL'}")


if __name__ == "__main__":
    gate()
