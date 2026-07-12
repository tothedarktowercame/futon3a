"""mission_kernel.py — MiniLM mission-similarity kernel for R-hat v2.

Embeds each labeled record's want text with the existing MATCHER.model and
computes k(q, r) = softmax(cos(q, e_r) / tau) over records.

The single documented bandwidth knob is tau.  Use tau=math.inf for the exact
uniform-kernel limit: every record receives 1/N weight, independent of text.

Run:
  cd ~/code/futon3a && .venv/bin/python3 holes/labs/M-memes-arrows/mission_kernel.py
"""
from __future__ import annotations

import math
import re
import sys
from itertools import combinations
from pathlib import Path

LAB = Path(__file__).parent
sys.path.insert(0, str(LAB))

import numpy as np

from aliveness_v3_gate2 import MATCHER
from fold_ground_truth import load_records

DEFAULT_TAU = 0.25
SEED = 20260712
_EMBED_CACHE: dict[str, np.ndarray] = {}


def mission_group(scope: str) -> str:
    """Return the deterministic LOMO group key used by the batch-7 verdict.

    Rules:
    - slash-scoped records group by the slash prefix;
    - fold-turn ids drop their numeric flight suffix;
    - fold-turn ``ft-`` ids drop that transport prefix, so older
      ``hypergraph-operator/*`` records group with ``ft-hypergraph-operator-*``.

    On the current label set this yields 39 records / 22 groups.
    """
    base = str(scope).split("/", 1)[0]
    base = re.sub(r"-\d+$", "", base)
    if base.startswith("ft-"):
        base = base[3:]
    return base


def want_text(record: dict) -> str:
    return str(record.get("problem") or "")


def embed_texts(texts: list[str]) -> np.ndarray:
    """Embed wants with MiniLM, normalized for cosine = dot product."""
    texts = [str(t) for t in texts]
    missing = []
    seen_missing = set()
    for text in texts:
        if text not in _EMBED_CACHE and text not in seen_missing:
            missing.append(text)
            seen_missing.add(text)
    if missing:
        emb = MATCHER.model.encode(
            missing,
            normalize_embeddings=True,
            convert_to_numpy=True,
            show_progress_bar=False,
        )
        for text, vec in zip(missing, np.asarray(emb, dtype=float)):
            _EMBED_CACHE[text] = vec
    return np.asarray([_EMBED_CACHE[text] for text in texts], dtype=float)


def embed_records(records: list[dict]) -> np.ndarray:
    return embed_texts([want_text(r) for r in records])


def kernel_weights(query_text: str, records: list[dict], embeddings: np.ndarray,
                   tau: float = DEFAULT_TAU) -> np.ndarray:
    """Return softmax-over-records weights for query_text.

    tau=math.inf is an exact uniform limit, not a large-number approximation.
    """
    n = len(records)
    if n == 0:
        return np.array([], dtype=float)
    if math.isinf(tau):
        return np.full(n, 1.0 / n, dtype=float)
    if tau <= 0:
        raise ValueError("tau must be positive or math.inf")
    q = embed_texts([query_text])[0]
    sims = embeddings @ q
    z = sims / tau
    z = z - float(np.max(z))
    exp = np.exp(z)
    return exp / float(np.sum(exp))


def pairwise_similarity_report(records: list[dict] | None = None,
                               embeddings: np.ndarray | None = None) -> dict:
    """Return within-vs-cross mission cosine distributions and rank statistic."""
    if records is None:
        records = load_records()
    if embeddings is None:
        embeddings = embed_records(records)
    within, cross = [], []
    for i, j in combinations(range(len(records)), 2):
        sim = float(embeddings[i] @ embeddings[j])
        if mission_group(records[i]["scope"]) == mission_group(records[j]["scope"]):
            within.append(sim)
        else:
            cross.append(sim)
    rank = _auc_greater(within, cross)
    return {
        "n_records": len(records),
        "n_groups": len({mission_group(r["scope"]) for r in records}),
        "within": within,
        "cross": cross,
        "within_mean": float(np.mean(within)) if within else float("nan"),
        "cross_mean": float(np.mean(cross)) if cross else float("nan"),
        "within_median": float(np.median(within)) if within else float("nan"),
        "cross_median": float(np.median(cross)) if cross else float("nan"),
        "rank_auc_within_gt_cross": rank,
        "pass": bool(rank > 0.5 and np.mean(within) > np.mean(cross)),
    }


def _auc_greater(pos: list[float], neg: list[float]) -> float:
    if not pos or not neg:
        return float("nan")
    wins = 0.0
    for a in pos:
        for b in neg:
            wins += (a > b) + 0.5 * (a == b)
    return wins / (len(pos) * len(neg))


def format_kill_test(report: dict) -> str:
    verdict = "PASS" if report["pass"] else "FAIL"
    return (
        "A2 kill-test: "
        f"records={report['n_records']} groups={report['n_groups']} "
        f"within_n={len(report['within'])} cross_n={len(report['cross'])} "
        f"within_mean={report['within_mean']:.4f} cross_mean={report['cross_mean']:.4f} "
        f"within_median={report['within_median']:.4f} cross_median={report['cross_median']:.4f} "
        f"rank_auc={report['rank_auc_within_gt_cross']:.4f} {verdict}"
    )


def main():
    records = load_records()
    embeddings = embed_records(records)
    report = pairwise_similarity_report(records, embeddings)
    print(format_kill_test(report))
    if not report["pass"]:
        print("STOP: mission similarity kill-test failed; do not proceed to reward_v2.")


if __name__ == "__main__":
    main()
