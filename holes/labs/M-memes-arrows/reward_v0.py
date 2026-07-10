"""reward_v0.py — B1 of SPEC-full-loop-gfn: the LEARNED reward R̂ (v0).

R̂(S|m) = w · [reliability, coverage, −complexity]
  reliability = mean posterior log-odds of the patterns in S, Beta(2,1) prior
                (the closure-folds 0.667-uniform convention), updated ONLY by
                flown-fold outcomes (fold_ground_truth records);
  coverage    = the locked Slice-1.5 obligation-unification measure — a FEATURE,
                demoted from reward (Goodhart ceiling, fold_grain_expansion
                findings);
  complexity  = base-rate −log inclusion prior (cascade_construct).

Weights: fixed default (1.0, 1.0, 0.05) or logistic fit on flown labels.
S3 (the preregistered discrimination curve) is evaluated LEAVE-ONE-OUT: the
held-out record contributes to NEITHER the reliability posteriors NOR the
weights that score it.

Hard checks outside the learned weights (preregistration): the trivial-want
cascade and the bloated shell must score below the best real success record,
whatever the fit says; a retrain that breaks them is rejected.

Run: cd ~/code/futon3a && .venv/bin/python3 holes/labs/M-memes-arrows/reward_v0.py
"""
from __future__ import annotations
import json, math, random, sys

LAB = "/home/joe/code/futon3a/holes/labs/M-memes-arrows"
sys.path.insert(0, LAB)

import numpy as np
from aliveness_v3_gate2 import MATCHER, DROP, produces_of, complexity, auc
from cascade_rollout import salient
from cascade_construct import pattern_stem
from fold_ground_truth import load_records

PRIOR_A, PRIOR_B = 2.0, 1.0            # Beta(2,1): the 0.667-uniform convention
FIXED_W = np.array([1.0, 1.0, -0.05])  # reliability, coverage, -complexity
SEED = 20260710


def fit_reliability(records):
    """Per-pattern-stem Beta posteriors from flown-fold outcomes."""
    ab = {}
    for r in records:
        for p in r["used"]:
            s = pattern_stem(p)
            a, b = ab.get(s, (PRIOR_A, PRIOR_B))
            ab[s] = (a + 1, b) if r["success"] else (a, b + 1)
    return ab


def reliability_logodds(patterns, ab):
    vals = []
    for p in patterns:
        a, b = ab.get(pattern_stem(p), (PRIOR_A, PRIOR_B))
        vals.append(math.log(a / b))
    return float(np.mean(vals)) if vals else math.log(PRIOR_A / PRIOR_B)


def features(used, want, ab, produces=None):
    """[reliability, coverage, -complexity]. `produces` may be precomputed."""
    if produces is None:
        produces, _ = produces_of(used)
    cov = MATCHER.coverage(set(want), produces) if produces else 0.0
    return np.array([reliability_logodds(used, ab), cov, -complexity(used)])


def fit_weights(feats, labels, l2=1.0, iters=500, lr=0.1):
    """Tiny ridge-logistic fit (numpy); n is small, regularize hard."""
    X = np.asarray(feats)
    y = np.asarray(labels, dtype=float)
    w = FIXED_W.copy()
    for _ in range(iters):
        p = 1.0 / (1.0 + np.exp(-(X @ w)))
        g = X.T @ (p - y) / len(y) + l2 * (w - FIXED_W) / len(y)
        w -= lr * g
    return w


class RewardV0:
    """Fit on a set of flown-fold records; score candidate cascades."""

    def __init__(self, records, fit=True):
        self.ab = fit_reliability(records)
        if fit and len(records) >= 6:
            feats = [self._feat(r) for r in records]
            self.w = fit_weights(feats, [r["success"] for r in records])
        else:
            self.w = FIXED_W.copy()

    def _feat(self, r):
        return features(r["used"], salient(r["problem"], DROP), self.ab)

    def score(self, used, want, produces=None):
        return float(features(used, want, self.ab, produces=produces) @ self.w)


def loo_s3(records, n_null=200, seed=SEED):
    """The preregistered S3 point: LOO AUC of R̂ vs a shuffle null."""
    scores = []
    for i, r in enumerate(records):
        rest = records[:i] + records[i + 1:]
        rw = RewardV0(rest)
        scores.append(rw.score(r["used"], salient(r["problem"], DROP)))
    y = [r["success"] for r in records]
    real = auc(scores, y)
    rng = random.Random(seed)
    nulls = sorted(auc(scores, rng.sample(y, len(y))) for _ in range(n_null))
    return real, nulls[int(0.95 * n_null)], scores


def hard_checks(reward: "RewardV0", records):
    """Anti-gaming controls outside the learned weights (must both PASS)."""
    succ = [r for r in records if r["success"]]
    best = max(succ, key=lambda r: reward.score(r["used"], salient(r["problem"], DROP)))
    best_score = reward.score(best["used"], salient(best["problem"], DROP))
    triv_want = salient("x equals x it is what it is trivially true", DROP)
    t = reward.score([records[0]["used"][0]], triv_want)
    bloat = list(dict.fromkeys(p for r in records for p in r["used"]))[:20]
    b = reward.score(bloat, salient(best["problem"], DROP))
    return {"substantive": best_score, "trivial": t, "bloated": b,
            "trivial_dead": t < best_score, "bloated_dead": b < best_score}


def main():
    records = load_records()
    y = [r["success"] for r in records]
    print(f"labels: {len(records)} flown-fold records "
          f"({sum(y)} success / {len(y) - sum(y)} fail)")
    real, n95, scores = loo_s3(records)
    print(f"S3 (LOO AUC of R-hat vs shuffle null): {real:.3f}  null-95 {n95:.3f}  "
          f"{'PASS' if real > n95 else 'below-null (expected at this n)'}")
    for r, s in sorted(zip(records, scores), key=lambda t: -t[1]):
        print(f"  {s:+7.3f}  {str(r['success']):5}  {r['scope']}")
    rw = RewardV0(records)
    hc = hard_checks(rw, records)
    print(f"hard checks: substantive {hc['substantive']:+.3f} > "
          f"trivial {hc['trivial']:+.3f} : {'PASS' if hc['trivial_dead'] else 'FAIL'} ; "
          f"> bloated {hc['bloated']:+.3f} : {'PASS' if hc['bloated_dead'] else 'FAIL'}")
    print(f"fitted w (reliability, coverage, -complexity): "
          f"{np.round(rw.w, 3).tolist()}")
    out = {"n": len(records), "s3_auc": real, "s3_null95": n95,
           "hard_checks": {k: (bool(v) if isinstance(v, (bool, np.bool_)) else float(v))
                           for k, v in hc.items()},
           "w": rw.w.tolist()}
    print(json.dumps(out))


if __name__ == "__main__":
    main()
