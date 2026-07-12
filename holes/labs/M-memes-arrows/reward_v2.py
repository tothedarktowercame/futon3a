"""reward_v2.py — R-hat v2: mission-similarity-conditioned reliability.

EXTENDS reward_v1 (never edits reward_v0.py or reward_v1.py).

Change from v1.2:
  reliability pseudo-counts are conditioned on the target mission want text.
  For training records R and query q:

    k(q, r) = softmax(cos(embed(q), embed(want_r)) / tau)
    a_p = PRIOR_A + sum_r k(q, r) * N * [p in used_r and success_r]
    b_p = PRIOR_B + sum_r k(q, r) * N * [p in used_r and not success_r]

N is fixed to len(training records).  This is not tuned: it preserves the
total evidence mass of v1.2 at the exact uniform-kernel limit, because
tau=inf gives k=1/N and therefore recovers the ordinary global counts.

Same 4 feature axes as v1.2:
  [reliability, coverage, wireability, size_mismatch]

Run:
  cd ~/code/futon3a && .venv/bin/python3 holes/labs/M-memes-arrows/reward_v2.py
"""
from __future__ import annotations

import json
import math
import random
import sys
from pathlib import Path

LAB = Path(__file__).parent
sys.path.insert(0, str(LAB))

import numpy as np

from aliveness_v3_gate2 import MATCHER, DROP, produces_of, auc
from cascade_construct import pattern_stem
from cascade_rollout import salient
from fold_ground_truth import load_records
from mission_kernel import DEFAULT_TAU, embed_records, kernel_weights, mission_group
from reward_v0 import PRIOR_A, PRIOR_B, SEED
from reward_v1 import (RewardV1, V1_FIXED_W, WIRING_JSON, fit_weights_v1,
                       features_v1, hard_checks_v1, obligation_count,
                       wireability_score, reliability_topk)
from reward_v0 import fit_reliability
from wiring_corpus import build_corpus, load_corpus as load_wiring_corpus

TAU_GRID = (0.15, 0.25, 0.50, 1.00)
UNIFORM_TOLERANCE = 1e-9
_PRODUCES_CACHE = {}
_COVERAGE_CACHE = {}
_STATIC_RECORD_FEATURE_CACHE = {}
_CORPUS_CACHE = None


def cached_corpus():
    global _CORPUS_CACHE
    if _CORPUS_CACHE is None:
        _CORPUS_CACHE = load_wiring_corpus(WIRING_JSON) if WIRING_JSON.exists() else build_corpus()
    return _CORPUS_CACHE


def cached_produces_of(used):
    key = tuple(used)
    if key not in _PRODUCES_CACHE:
        _PRODUCES_CACHE[key] = produces_of(used)
    return _PRODUCES_CACHE[key]


def cached_coverage(want_tokens, produces):
    want_key = tuple(sorted(want_tokens))
    prod_key = tuple(sorted(produces))
    key = (want_key, prod_key)
    if key not in _COVERAGE_CACHE:
        _COVERAGE_CACHE[key] = MATCHER.coverage(set(want_key), set(prod_key)) if prod_key else 0.0
    return _COVERAGE_CACHE[key]


def static_record_features(record: dict, corpus: dict) -> tuple[float, float, float, int]:
    """Return v1/v2 axes independent of reliability: coverage, wire, size, n_obj."""
    key = (record["scope"], tuple(record["used"]))
    if key in _STATIC_RECORD_FEATURE_CACHE:
        return _STATIC_RECORD_FEATURE_CACHE[key]
    produces, _ = cached_produces_of(record["used"])
    want_tokens = salient(record["problem"], DROP)
    n_obj = obligation_count(record["problem"])
    cov = cached_coverage(want_tokens, produces) if produces else 0.0
    wire = wireability_score(record["used"], corpus)
    sm = float(abs(len(record["used"]) - n_obj))
    out = (cov, wire, sm, n_obj)
    _STATIC_RECORD_FEATURE_CACHE[key] = out
    return out


def fit_reliability_kernel(records: list[dict], query_want: str,
                           embeddings: np.ndarray | None = None,
                           tau: float = DEFAULT_TAU) -> dict:
    """Kernel-weighted per-pattern Beta pseudo-counts for query_want."""
    if embeddings is None:
        embeddings = embed_records(records)
    weights = kernel_weights(query_want, records, embeddings, tau=tau)
    n_mass = float(len(records))
    ab = {}
    for w, r in zip(weights, records):
        mass = w * n_mass
        if mass == 0.0:
            continue
        for p in r["used"]:
            s = pattern_stem(p)
            a, b = ab.get(s, (PRIOR_A, PRIOR_B))
            if r["success"]:
                a += mass
            else:
                b += mass
            ab[s] = (a, b)
    return ab


def features_v2(used, want, records: list[dict], embeddings: np.ndarray,
                tau: float, produces=None, corpus=None, agg="topk", n_obj=None):
    """v1.2 feature vector with query-conditioned reliability counts."""
    if corpus is None:
        corpus = cached_corpus()
    if produces is None:
        produces, _ = cached_produces_of(used)
    want_text = _want_text(want)
    if isinstance(want, (set, list)):
        want_tokens = set(want)
        if n_obj is None:
            n_obj = max(1, len(want_tokens) // 5)
    else:
        want_tokens = salient(want_text, DROP)
        if n_obj is None:
            n_obj = obligation_count(want_text)
    cov = cached_coverage(want_tokens, produces) if produces else 0.0
    ab = fit_reliability_kernel(records, want_text, embeddings, tau=tau)
    if agg == "topk":
        rel = reliability_topk(used, ab, n_obj)
    else:
        from reward_v0 import reliability_logodds
        rel = reliability_logodds(used, ab)
    wire = wireability_score(used, corpus)
    sm = float(abs(len(used) - n_obj))
    return np.array([rel, cov, wire, sm])


def _want_text(want) -> str:
    if isinstance(want, (set, list, tuple)):
        return " ".join(str(x) for x in want)
    return str(want)


class RewardV2:
    """reward_v1.2 + mission-similarity-conditioned reliability."""

    def __init__(self, records, fit=True, tau=DEFAULT_TAU, agg="topk"):
        self.records = list(records)
        self.tau = tau
        self.agg = agg
        self.embeddings = embed_records(self.records)
        self.corpus = cached_corpus()
        if fit and len(self.records) >= 6:
            feats = [self._feat(r) for r in self.records]
            self.w = fit_weights_v1(feats, [r["success"] for r in self.records])
        else:
            self.w = V1_FIXED_W.copy()

    def _feat(self, r):
        return features_v2(r["used"], r["problem"], self.records, self.embeddings,
                           self.tau, corpus=self.corpus, agg=self.agg)

    def score(self, used, want, produces=None, n_obj=None):
        return float(features_v2(used, want, self.records, self.embeddings,
                                 self.tau, produces=produces, corpus=self.corpus,
                                 agg=self.agg, n_obj=n_obj) @ self.w)


class RewardV1Cached:
    """RewardV1-equivalent scorer with cached pattern productions for LOMO runs."""

    def __init__(self, records, fit=True, agg="topk"):
        self.records = list(records)
        self.agg = agg
        self.ab = fit_reliability(self.records)
        self.corpus = cached_corpus()
        if fit and len(self.records) >= 6:
            feats = [self._feat(r) for r in self.records]
            self.w = fit_weights_v1(feats, [r["success"] for r in self.records])
        else:
            self.w = V1_FIXED_W.copy()

    def _feat(self, r):
        produces, _ = cached_produces_of(r["used"])
        return self._features(r["used"], r["problem"], produces=produces)

    def score(self, used, want, produces=None, n_obj=None):
        if produces is None:
            produces, _ = cached_produces_of(used)
        return float(self._features(used, want, produces=produces, n_obj=n_obj) @ self.w)

    def _features(self, used, want, produces=None, n_obj=None):
        want_text = _want_text(want)
        if isinstance(want, (set, list)):
            want_tokens = set(want)
            if n_obj is None:
                n_obj = max(1, len(want_tokens) // 5)
        else:
            want_tokens = salient(want_text, DROP)
            if n_obj is None:
                n_obj = obligation_count(want_text)
        if produces is None:
            produces, _ = cached_produces_of(used)
        cov = cached_coverage(want_tokens, produces) if produces else 0.0
        rel = reliability_topk(used, self.ab, n_obj)
        wire = wireability_score(used, self.corpus)
        sm = float(abs(len(used) - n_obj))
        return np.array([rel, cov, wire, sm])


def hard_checks_v2(reward: "RewardV2", records):
    """Same hard checks as v1, scored by v2."""
    succ = [r for r in records if r["success"]]
    best = max(succ, key=lambda r: reward.score(r["used"], r["problem"]))
    best_score = reward.score(best["used"], best["problem"])
    t = reward.score([records[0]["used"][0]], "x equals x it is what it is trivially true")
    bloat = list(dict.fromkeys(p for r in records for p in r["used"]))[:20]
    b = reward.score(bloat, best["problem"])
    multi_records = [r for r in records if r["success"] and obligation_count(r["problem"]) >= 2]
    degeneracy_fail = False
    if multi_records:
        best_multi = max(multi_records, key=lambda r: reward.score(r["used"], r["problem"]))
        best_multi_score = reward.score(best_multi["used"], best_multi["problem"])
        all_patterns = list(dict.fromkeys(p for r in records for p in r["used"]))
        best_single_score = max(reward.score([p], best_multi["problem"]) for p in all_patterns)
        degeneracy_fail = best_single_score >= best_multi_score
    return {"substantive": best_score,
            "trivial": t, "trivial_dead": t < best_score,
            "bloated": b, "bloated_dead": b < best_score,
            "degeneracy_check": not degeneracy_fail,
            "degeneracy_detail": f"single >= multi: {degeneracy_fail}"}


def loo_s3_v2(records, n_null=200, seed=SEED, tau=DEFAULT_TAU):
    scores = []
    for i, r in enumerate(records):
        rest = records[:i] + records[i + 1:]
        rw = RewardV2(rest, tau=tau)
        scores.append(rw.score(r["used"], r["problem"]))
    y = [r["success"] for r in records]
    real = auc(scores, y)
    rng = random.Random(seed)
    nulls = sorted(auc(scores, rng.sample(y, len(y))) for _ in range(n_null))
    return real, nulls[int(0.95 * n_null)], scores


def choose_tau_inner_loo(train_records: list[dict], tau_grid=TAU_GRID) -> tuple[float, list[dict]]:
    """Choose tau inside a LOMO training fold by fixed-grid within-fold LOO.

    Practical CPU form: for each tau, fit the v1-shaped logistic weights once
    on the outer training fold, then score each inner held record with its own
    reliability counts removed.  The outer held mission is never used.
    """
    rows = []
    y = [r["success"] for r in train_records]
    for tau in tau_grid:
        w = fit_v2_weight_matrix(train_records, tau)
        scores = []
        for i, r in enumerate(train_records):
            rest = train_records[:i] + train_records[i + 1:]
            scores.append(score_record_v2(r, rest, tau, w))
        rows.append({"tau": tau, "loo_auc": auc(scores, y)})
    # Deterministic tie-break: choose the broadest kernel among tied AUCs.
    best = max(rows, key=lambda row: (row["loo_auc"], row["tau"]))
    return best["tau"], rows


def fit_v2_weight_matrix(train_records: list[dict], tau: float) -> np.ndarray:
    corpus = cached_corpus()
    embeddings = embed_records(train_records)
    feats = [feature_for_record_v2(r, train_records, embeddings, tau, corpus)
             for r in train_records]
    return fit_weights_v1(feats, [r["success"] for r in train_records])


def feature_for_record_v2(record: dict, train_records: list[dict],
                          train_embeddings: np.ndarray, tau: float,
                          corpus: dict) -> np.ndarray:
    cov, wire, sm, n_obj = static_record_features(record, corpus)
    ab = fit_reliability_kernel(train_records, record["problem"], train_embeddings, tau=tau)
    rel = reliability_topk(record["used"], ab, n_obj)
    return np.array([rel, cov, wire, sm])


def score_record_v2(record: dict, train_records: list[dict], tau: float,
                    weights: np.ndarray) -> float:
    corpus = cached_corpus()
    embeddings = embed_records(train_records)
    return float(feature_for_record_v2(record, train_records, embeddings, tau, corpus) @ weights)


def fit_v1_weight_matrix(train_records: list[dict]) -> np.ndarray:
    corpus = cached_corpus()
    ab = fit_reliability(train_records)
    feats = [feature_for_record_v1(r, ab, corpus) for r in train_records]
    return fit_weights_v1(feats, [r["success"] for r in train_records])


def feature_for_record_v1(record: dict, ab: dict, corpus: dict) -> np.ndarray:
    cov, wire, sm, n_obj = static_record_features(record, corpus)
    rel = reliability_topk(record["used"], ab, n_obj)
    return np.array([rel, cov, wire, sm])


def score_record_v1(record: dict, train_records: list[dict], weights: np.ndarray) -> float:
    corpus = cached_corpus()
    ab = fit_reliability(train_records)
    return float(feature_for_record_v1(record, ab, corpus) @ weights)


def lomo_scores(records: list[dict], model="v2", shuffle_labels=False,
                seed=SEED, tau_grid=TAU_GRID):
    groups = sorted({mission_group(r["scope"]) for r in records})
    rng = random.Random(seed)
    scores, labels, fold_rows = [], [], []
    for gi, group in enumerate(groups):
        train = [r for r in records if mission_group(r["scope"]) != group]
        held = [r for r in records if mission_group(r["scope"]) == group]
        if shuffle_labels:
            shuffled = [dict(r) for r in train]
            ys = [r["success"] for r in shuffled]
            rng_fold = random.Random(seed + 1009 * gi)
            rng_fold.shuffle(ys)
            for r, y in zip(shuffled, ys):
                r["success"] = y
            train = shuffled
        if model == "v1":
            w = fit_v1_weight_matrix(train)
            tau = None
            tau_rows = []
        elif model == "v2":
            tau, tau_rows = choose_tau_inner_loo(train, tau_grid=tau_grid)
            w = fit_v2_weight_matrix(train, tau)
        else:
            raise ValueError(f"unknown model: {model}")
        for r in held:
            if model == "v1":
                scores.append(score_record_v1(r, train, w))
            else:
                scores.append(score_record_v2(r, train, tau, w))
            labels.append(r["success"])
        fold_rows.append({"group": group, "n_train": len(train), "n_held": len(held),
                          "tau": tau, "tau_grid": tau_rows})
    return scores, labels, fold_rows


def lomo_auc(records: list[dict], model="v2", shuffle_labels=False,
             seed=SEED, tau_grid=TAU_GRID):
    scores, labels, folds = lomo_scores(records, model=model,
                                        shuffle_labels=shuffle_labels,
                                        seed=seed, tau_grid=tau_grid)
    return auc(scores, labels), scores, labels, folds


def lomo_null95(records: list[dict], model="v2", n_null=50, seed=SEED,
                tau_grid=TAU_GRID):
    vals = []
    for j in range(n_null):
        val, _, _, _ = lomo_auc(records, model=model, shuffle_labels=True,
                                seed=seed + 7919 * j, tau_grid=tau_grid)
        vals.append(val)
    vals = sorted(vals)
    return vals[int(0.95 * len(vals))], vals


def _format_tau_choices(folds: list[dict]) -> str:
    return "per-fold tau choices: " + ", ".join(
        f"{f['group']}={f['tau']}" for f in folds
    )


def main():
    records = load_records()
    y = [r["success"] for r in records]
    print(f"labels: {len(records)} records / "
          f"{len({mission_group(r['scope']) for r in records})} LOMO groups "
          f"({sum(y)} success / {len(y) - sum(y)} fail)")
    print(f"tau grid (predeclared): {list(TAU_GRID)} ; N normalization=len(training records)")

    v2_lomo, _, _, folds = lomo_auc(records, model="v2")
    v2_null95, _ = lomo_null95(records, model="v2")
    v1_lomo, _, _, _ = lomo_auc(records, model="v1")
    v1_null95, _ = lomo_null95(records, model="v1")
    print(f"A1 LOMO v2 AUC={v2_lomo:.3f} null95={v2_null95:.3f} ; "
          f"v1.2 AUC={v1_lomo:.3f} null95={v1_null95:.3f}")
    print(_format_tau_choices(folds))

    s3, s3_null, _ = loo_s3_v2(records)
    print(f"A1 LOO S3 v2 AUC={s3:.3f} null95={s3_null:.3f}")

    rw_inf = RewardV2(records, tau=math.inf)
    rw_v1 = RewardV1(records)
    diffs = [abs(rw_inf.score(r["used"], r["problem"]) -
                 rw_v1.score(r["used"], r["problem"])) for r in records]
    print(f"A3 tau=inf regression max_abs_diff={max(diffs):.3e} tolerance={UNIFORM_TOLERANCE:.1e} "
          f"{'PASS' if max(diffs) <= UNIFORM_TOLERANCE else 'FAIL'}")

    rw = RewardV2(records)
    hc = hard_checks_v2(rw, records)
    print(f"A4 hard checks v2: trivial={'PASS' if hc['trivial_dead'] else 'FAIL'} "
          f"bloated={'PASS' if hc['bloated_dead'] else 'FAIL'} "
          f"degeneracy={'PASS' if hc['degeneracy_check'] else 'FAIL'}")
    print(f"fitted w v2 default tau={DEFAULT_TAU}: {np.round(rw.w, 3).tolist()}")
    print(json.dumps({
        "n": len(records),
        "groups": len({mission_group(r["scope"]) for r in records}),
        "tau_grid": list(TAU_GRID),
        "lomo_v2_auc": v2_lomo,
        "lomo_v2_null95": v2_null95,
        "lomo_v1_auc": v1_lomo,
        "lomo_v1_null95": v1_null95,
        "loo_s3_v2_auc": s3,
        "loo_s3_v2_null95": s3_null,
        "tau_choices": {f["group"]: f["tau"] for f in folds},
    }))


if __name__ == "__main__":
    main()
