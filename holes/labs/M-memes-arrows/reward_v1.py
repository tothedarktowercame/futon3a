"""reward_v1.py — R2 v1.1: reward_v0 + wireability + size-mismatch (fitted) + degeneracy check.

EXTENDS reward_v0 (never edits it). v1.1 design fix (reviewer revision):
  - REMOVED raw -complexity from the fitted vector (it confounded with
    size-match at opposite signs on n=15 labels: the 11-pattern success
    taught "bigger is better", flipping w_cx positive).
  - REPLACED with size-mismatch |n - obligation_count| as a FITTED feature
    (the data sets its weight/sign, not a hand-fixed -1.0).
  - One size axis, one learned sign.

Features: [reliability, coverage, wireability, size_mismatch]
  reliability     = mean posterior log-odds (same as v0)
  coverage        = obligation-unification measure (same as v0)
  wireability     = expected pairwise wiring affinity (Laplace-smoothed)
  size_mismatch   = abs(|proposal| - obligation_count) — FITTED, not fixed

Weights: fixed default (1.0, 1.0, 1.0, -1.0) or logistic fit on labels.
S3 LOO evaluated same as v0.

Hard checks: v0's trivial + bloated (scored by v1) + new degeneracy gate
(single-pattern argmax on multi-obligation want = FAIL).

CLI:
  python3 reward_v1.py                    # S3 + hard checks (default main)
  python3 reward_v1.py --canary [mission ...]   # A1 canary re-run

Run: cd ~/code/futon3a && .venv/bin/python3 holes/labs/M-memes-arrows/reward_v1.py
"""
from __future__ import annotations
import json, math, random, sys
from itertools import combinations
from pathlib import Path

LAB = Path(__file__).parent
sys.path.insert(0, str(LAB))

import numpy as np
from aliveness_v3_gate2 import MATCHER, DROP, produces_of, complexity, auc
from cascade_rollout import salient
from cascade_construct import pattern_stem
from fold_ground_truth import load_records
from reward_v0 import (RewardV0, fit_reliability, reliability_logodds,
                        hard_checks as hard_checks_v0, loo_s3 as loo_s3_v0,
                        FIXED_W as V0_FIXED_W, SEED, PRIOR_A, PRIOR_B)
from wiring_corpus import build_corpus, pair_affinity, load_corpus as load_wiring_corpus

WIRING_JSON = LAB / "wiring-corpus.json"

# v1.1 Feature vector: [reliability, coverage, wireability, size_mismatch]
# Default: size_mismatch penalized at -1.0 (mismatch is bad), wireability rewarded at 1.0
V1_FIXED_W = np.array([1.0, 1.0, 1.0, -1.0])


def obligation_count(want_text: str) -> int:
    """Count distinct HUNGRY-FOR clauses as the obligation count.

    A simple, DOCUMENTED, mechanical rule over the psi:
    - Extract the HUNGRY-FOR section
    - Split on semicolons (the clause separator in all observed psis)
    - Count non-empty clauses after salient-token filtering
    """
    text = want_text.replace('\\n', '\n')
    hungry_match = None
    for line in text.split('\n'):
        if line.strip().upper().startswith('HUNGRY-FOR:'):
            hungry_match = line.strip()[len('HUNGRY-FOR:'):].strip()
            break

    if not hungry_match:
        want_match = None
        for line in text.split('\n'):
            if line.strip().upper().startswith('WANT:'):
                want_match = line.strip()[len('WANT:'):].strip()
                break
        if want_match:
            tokens = salient(want_match, DROP)
            return max(1, len(tokens) // 5)
        return 1

    clauses = [c.strip() for c in hungry_match.split(';') if c.strip()]
    n = 0
    for clause in clauses:
        tokens = salient(clause, DROP)
        if tokens:
            n += 1
    return max(1, n)


def wireability_score(patterns: list[str], corpus: dict) -> float:
    """Expected pairwise wiring affinity of the proposed set.

    For all pairs in the set, compute the Laplace-smoothed affinity.
    Return the mean (0.0 = neutral for unseen pairs).
    Single-pattern sets return 0.0 (no pairs to evaluate).
    """
    if len(patterns) < 2:
        return 0.0
    affinities = []
    for a, b in combinations(sorted(set(patterns)), 2):
        aff = pair_affinity((a, b), corpus)
        affinities.append(aff)
    return float(np.mean(affinities)) if affinities else 0.0


def reliability_topk(patterns, ab, k):
    """Top-k pooled reliability: mean posterior log-odds of the k best patterns.

    k = min(|proposal|, obligation_count). Composing up to the want's
    complexity never dilutes; overshoot is governed by size_mismatch and
    the bloated-shell gate.
    """
    if not patterns:
        return math.log(PRIOR_A / PRIOR_B)
    stems = [pattern_stem(p) for p in patterns]
    logodds = []
    for s in stems:
        a, b = ab.get(s, (PRIOR_A, PRIOR_B))
        logodds.append(math.log(a / b))
    k = min(k, len(logodds))
    if k < 1:
        k = 1
    top_k = sorted(logodds, reverse=True)[:k]
    return float(np.mean(top_k))


def size_mismatch(n_patterns: int, n_obligations: int) -> float:
    """Size-mismatch feature: abs(|proposal| - obligation_count).

    FITTED feature — the data sets its sign. If the fit turns this positive
    (labels prefer mismatched sizes), that is the label-poverty finding.
    """
    return float(abs(n_patterns - n_obligations))


def features_v1(used, want, ab, produces=None, corpus=None, agg="topk", n_obj=None):
    """[reliability, coverage, wireability, size_mismatch] (4 features, v1.2).

    agg="topk" (default): reliability = mean log-odds of the k best patterns,
      k = min(|proposal|, obligation_count). Composing up to the want's
      complexity never dilutes.
    agg="mean": reliability = mean log-odds of all patterns (v1.1 behavior).

    n_obj: precomputed obligation count (avoids re-parsing want text when
    want is already tokenized). If None, computed from want.
    """
    if corpus is None:
        corpus = load_wiring_corpus(WIRING_JSON) if WIRING_JSON.exists() else build_corpus()
    if produces is None:
        produces, _ = produces_of(used)
    # want may be the original text or pre-tokenized tokens
    if isinstance(want, (set, list)):
        want_tokens = set(want)
        if n_obj is None:
            n_obj = max(1, len(want_tokens) // 5)
    else:
        want_tokens = salient(want, DROP)
        if n_obj is None:
            n_obj = obligation_count(want)
    cov = MATCHER.coverage(want_tokens, produces) if produces else 0.0
    if agg == "topk":
        rel = reliability_topk(used, ab, n_obj)
    else:
        rel = reliability_logodds(used, ab)
    wire = wireability_score(used, corpus)
    sm = size_mismatch(len(used), n_obj)
    return np.array([rel, cov, wire, sm])


class RewardV1:
    """reward_v0 + wireability + size-mismatch (all fitted); fit on flown-fold records.

    agg="topk" (default): reliability = top-k pooled (v1.2).
    agg="mean": reliability = mean-pooled (v1.1, for comparison).
    """

    def __init__(self, records, fit=True, agg="topk"):
        self.agg = agg
        self.ab = fit_reliability(records)
        self.corpus = load_wiring_corpus(WIRING_JSON) if WIRING_JSON.exists() else build_corpus()
        if fit and len(records) >= 6:
            feats = [self._feat(r) for r in records]
            self.w = fit_weights_v1(feats, [r["success"] for r in records])
        else:
            self.w = V1_FIXED_W.copy()

    def _feat(self, r):
        return features_v1(r["used"], r["problem"], self.ab,
                           corpus=self.corpus, agg=self.agg)

    def score(self, used, want, produces=None, n_obj=None):
        return float(features_v1(used, want, self.ab, produces=produces,
                                 corpus=self.corpus, agg=self.agg, n_obj=n_obj) @ self.w)


def fit_weights_v1(feats, labels, l2=1.0, iters=500, lr=0.1):
    """Tiny ridge-logistic fit for v1.1 (4 features); regularizes toward V1_FIXED_W.

    ALL 4 weights are fitted (no held-fixed weight). The size_mismatch weight
    is learned from the data — if it turns positive, that is the label-poverty
    finding (the n=15 labels prefer mismatched sizes) and we report it.
    """
    X = np.asarray(feats)
    y = np.asarray(labels, dtype=float)
    w = V1_FIXED_W.copy()
    for _ in range(iters):
        p = 1.0 / (1.0 + np.exp(-(X @ w)))
        g = X.T @ (p - y) / len(y) + l2 * (w - V1_FIXED_W) / len(y)
        w -= lr * g
    return w


def loo_s3_v1(records, n_null=200, seed=SEED, agg="topk"):
    """LOO AUC of R-hat v1 vs a shuffle null."""
    scores = []
    for i, r in enumerate(records):
        rest = records[:i] + records[i + 1:]
        rw = RewardV1(rest, agg=agg)
        scores.append(rw.score(r["used"], r["problem"]))
    y = [r["success"] for r in records]
    real = auc(scores, y)
    rng = random.Random(seed)
    nulls = sorted(auc(scores, rng.sample(y, len(y))) for _ in range(n_null))
    return real, nulls[int(0.95 * n_null)], scores


def hard_checks_v1(reward: "RewardV1", records):
    """Anti-gaming controls: v0's two checks (scored by v1) + new degeneracy check."""
    succ = [r for r in records if r["success"]]
    best = max(succ, key=lambda r: reward.score(r["used"], r["problem"]))
    best_score = reward.score(best["used"], best["problem"])

    # Trivial want check
    t = reward.score([records[0]["used"][0]], "x equals x it is what it is trivially true")

    # Bloated check (20 patterns from the pool)
    bloat = list(dict.fromkeys(p for r in records for p in r["used"]))[:20]
    b = reward.score(bloat, best["problem"])

    # NEW check: single-pattern argmax on a multi-obligation want = FAIL
    multi_records = [r for r in records if r["success"] and obligation_count(r["problem"]) >= 2]
    degeneracy_fail = False
    if multi_records:
        best_multi = max(multi_records, key=lambda r: reward.score(r["used"], r["problem"]))
        best_multi_score = reward.score(best_multi["used"], best_multi["problem"])
        all_patterns = list(dict.fromkeys(p for r in records for p in r["used"]))
        best_single_score = max(
            reward.score([p], best_multi["problem"]) for p in all_patterns
        )
        degeneracy_fail = best_single_score >= best_multi_score

    return {"substantive": best_score,
            "trivial": t, "trivial_dead": t < best_score,
            "bloated": b, "bloated_dead": b < best_score,
            "degeneracy_check": not degeneracy_fail,
            "degeneracy_detail": f"single >= multi: {degeneracy_fail}"}


def greedy_argmax(proposal_patterns, want_text, reward: "RewardV1", n_obj=None):
    """Greedy argmax: iteratively add the pattern that maximizes reward.

    Returns the best subset and its score.
    """
    if n_obj is None:
        n_obj = obligation_count(want_text)

    chosen = []
    remaining = list(proposal_patterns)
    best_score = float('-inf')
    best_set = []

    while remaining:
        scores = []
        for p in remaining:
            trial = chosen + [p]
            s = reward.score(trial, want_text, n_obj=n_obj)
            scores.append((s, p))
        scores.sort(reverse=True)
        best_s, best_p = scores[0]

        if not chosen or best_s > best_score:
            chosen.append(best_p)
            remaining.remove(best_p)
            best_score = best_s
            best_set = list(chosen)
            if len(chosen) >= n_obj + 2:
                break
        else:
            break

    return best_set, best_score


# ---------------------------------------------------------------------------
# A1 canary: pool construction (retrieval or proposal-union)
# ---------------------------------------------------------------------------

# Hand-transcribed want texts (used by proposal-pool mode and as fallback)
MISSION_WANTS = {
    "M-operational-vocabulary": (
        "WANT: served/abstaining missions get a non-nil delta-G from REAL provenance at box-run scale.\n"
        "HUNGRY-FOR: commissioning the box run; the promotion paths that restore circulation; "
        "the backward/sorry half chartered as the sequel; the R14 gamma feed; runtime SELECT-PER-MISSION.\n"
        "HAVE: the forward mining pipeline BUILT and CPU-validated end-to-end."
    ),
    "M-pattern-mining": (
        "WANT: a pattern-mining substrate where the MiniLM-cosine decoration is replaced "
        "by hypergraph-first structural diagnosis.\n"
        "HUNGRY-FOR: the typed pattern schema; the deterministic ingest pipeline; "
        "the layered ingestion; the live sync; the tension read as observation; the seam-read.\n"
        "HAVE: the IDENTIFY findings; the MAP-phase typed pipeline graph; seven probes sequenced."
    ),
    "M-legacy-sorry-cleanup": (
        "WANT: the 23 legacy code/v05/sorry entries each validated and classified.\n"
        "HUNGRY-FOR: a durable review log; a batch-check discipline; "
        "artifact-tagging discipline; drift-detection.\n"
        "HAVE: the legacy-sorries-snapshot.edn; the per-item shape; the review protocol; the A-next schema."
    ),
}

BATCH4_WORKLIST = Path("/home/joe/code/futon2/holes/labs/slush-demo/findings/proposals/batch-4-worklist.json")

# Lazy imports for retrieval-pool mode (avoids loading the embedding stack
# when only proposal-pool mode is used)
_RETRIEVAL_LOADED = False
_identify_psi = None
_locate_doc = None
_build_pool = None


def _ensure_retrieval():
    global _RETRIEVAL_LOADED, _identify_psi, _locate_doc, _build_pool
    if _RETRIEVAL_LOADED:
        return
    SLICE2 = "/home/joe/code/futon2/holes/labs/slush-demo/slice2"
    if SLICE2 not in sys.path:
        sys.path.insert(0, SLICE2)
    from offramp_cascade import identify_psi
    from aliveness_v3_corpus_gate2 import locate_doc
    from gfn_live import build_pool
    _identify_psi = identify_psi
    _locate_doc = locate_doc
    _build_pool = build_pool
    _RETRIEVAL_LOADED = True


def load_batch4_proposals():
    """Load the batch-4 worklist and group proposals by mission."""
    worklist = json.load(open(BATCH4_WORKLIST))
    missions = {}
    for entry in worklist:
        m = entry["mission"]
        missions.setdefault(m, []).append(entry)
    return missions


def build_retrieval_pool(mission_name, pool_size=24):
    """Build the retrieval pool exactly as gfn_live.py does: identify_psi from
    the mission doc, then top-M cosine against pattern embeddings.
    """
    _ensure_retrieval()
    doc = _locate_doc(mission_name)
    if not doc:
        return None, None
    psi = _identify_psi(doc)
    pool, sims = _build_pool(psi, pool_size)
    return psi, pool


def run_canary(mission_names=None, pool_mode="retrieval"):
    """A1 canary re-run: greedy-argmax under reward_v1 on batch-4 missions.

    pool_mode="retrieval" (default): replicate the instrument exactly —
      identify_psi from the mission doc, top-24 retrieval pool (same as
      gfn_live.py's build_pool).
    pool_mode="proposals": use the union of batch-4 proposal patterns
      (the v1.2 mode; pool-poverty probe).

    Prints argmax set, size, obligation count, and score for each mission.
    """
    records = load_records()
    rw = RewardV1(records)
    proposals = load_batch4_proposals()

    if mission_names is None:
        mission_names = list(MISSION_WANTS.keys())

    print("=" * 80)
    print(f"A1: CANARY RE-RUN — greedy-argmax under reward_v1 (v1.2, agg={rw.agg}, "
          f"pool={pool_mode})")
    print("=" * 80)
    print(f"fitted w (rel, cov, wire, size_mismatch): {np.round(rw.w, 3).tolist()}")

    results = {}
    for mission_name in mission_names:
        if pool_mode == "retrieval":
            psi, all_patterns = build_retrieval_pool(mission_name)
            if psi is None:
                print(f"\nWARNING: could not build retrieval pool for '{mission_name}', skipping")
                continue
            # Use the hand-transcribed structured want for obligation counting
            # (the instrument's psi is raw prose without HUNGRY-FOR; the
            # obligation count needs structured sections)
            want_for_obj = MISSION_WANTS.get(mission_name, psi)
            # But use the instrument's psi for coverage/reliability scoring
            # (salient tokens from the raw mission-doc prose, same as gfn_live)
            want = psi
            n_obj = obligation_count(want_for_obj)
        else:
            want = MISSION_WANTS.get(mission_name, "")
            if not want:
                print(f"\nWARNING: unknown mission '{mission_name}', skipping")
                continue
            all_patterns = list(dict.fromkeys(
                p for e in proposals.get(mission_name, []) for p in e["patterns"]
            ))
            n_obj = obligation_count(want)

        if not all_patterns:
            print(f"\nWARNING: no patterns for '{mission_name}', skipping")
            continue

        best_set, best_score = greedy_argmax(all_patterns, want, rw, n_obj=n_obj)
        size = len(best_set)
        within = abs(size - n_obj) <= 2
        is_comp = size > 1
        passed = is_comp and (n_obj < 2 or within)

        results[mission_name] = {
            "set": best_set, "score": best_score, "size": size,
            "n_obj": n_obj, "passed": passed,
        }

        print(f"\n{mission_name} (obligation_count={n_obj}, pool={len(all_patterns)} patterns):")
        print(f"  argmax set ({size}): {best_set}")
        print(f"  score: {best_score:.4f}")
        print(f"  A1: size>1={is_comp}, |size-obl|={abs(size-n_obj)}, "
              f"within±2={within}, PASS={passed}")

    n_pass = sum(1 for r in results.values() if r["passed"])
    print(f"\nA1 overall: {n_pass}/{len(results)} missions produce compositions "
          f"within ±2 of obligation count")
    return results


def main():
    # Parse CLI args
    args = sys.argv[1:]
    if args and args[0] == "--canary":
        # Parse --canary-pool flag
        pool_mode = "retrieval"
        mission_names = []
        i = 1
        while i < len(args):
            if args[i] == "--canary-pool" and i + 1 < len(args):
                pool_mode = args[i + 1]
                i += 2
            else:
                mission_names.append(args[i])
                i += 1
        if not mission_names:
            mission_names = None
        run_canary(mission_names, pool_mode=pool_mode)
        return

    # Default: S3 + hard checks
    records = load_records()
    y = [r["success"] for r in records]
    print(f"labels: {len(records)} flown-fold records "
          f"({sum(y)} success / {len(y) - sum(y)} fail)")

    rw = RewardV1(records)
    real, n95, scores = loo_s3_v1(records)
    print(f"S3 (LOO AUC of R-hat v1 vs shuffle null, agg={rw.agg}): {real:.3f}  null-95 {n95:.3f}  "
          f"{'PASS' if real > n95 else 'below-null (FINDING to report)'}")

    for r, s in sorted(zip(records, scores), key=lambda t: -t[1]):
        print(f"  {s:+7.3f}  {str(r['success']):5}  {r['scope']}")

    hc = hard_checks_v1(rw, records)
    print(f"hard checks: substantive {hc['substantive']:+.3f} > "
          f"trivial {hc['trivial']:+.3f} : {'PASS' if hc['trivial_dead'] else 'FAIL'} ; "
          f"> bloated {hc['bloated']:+.3f} : {'PASS' if hc['bloated_dead'] else 'FAIL'} ; "
          f"degeneracy: {'PASS' if hc['degeneracy_check'] else 'FAIL'} ({hc['degeneracy_detail']})")
    print(f"fitted w (rel, cov, wire, size_mismatch): "
          f"{np.round(rw.w, 3).tolist()}")

    # Report size_mismatch sign
    w_sm = rw.w[3]
    if w_sm > 0:
        print(f"WARNING: size_mismatch weight is POSITIVE ({w_sm:.3f}) — "
              f"the labels prefer mismatched sizes (label-poverty finding)")
    else:
        print(f"size_mismatch weight: {w_sm:.3f} (negative = mismatch penalized, as expected)")

    out = {"n": len(records), "s3_auc": real, "s3_null95": n95,
           "hard_checks": {k: (bool(v) if isinstance(v, (bool, np.bool_)) else float(v))
                           for k, v in hc.items()},
           "w": rw.w.tolist()}
    print(json.dumps(out))


if __name__ == "__main__":
    main()
