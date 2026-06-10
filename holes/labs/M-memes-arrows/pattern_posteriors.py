#!/usr/bin/env python3
"""Self-graded v0 pattern reliability posteriors from historical PURs.

This projection is rebuildable from disk. It reads mission markdown files,
extracts PUR records with typed outcomes, and writes a per-pattern Beta table
plus an explicit drop log. v0 evidence is self-graded by construction; grounded
peradam-attributed updates are a later seam, not implemented here.
"""

from __future__ import annotations

import argparse
import json
import re
from collections import Counter, defaultdict
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable


CODE_ROOT = Path("/home/joe/code")
ROOT = CODE_ROOT / "futon3a"
EMBEDDINGS = ROOT / "resources/notions/minilm_pattern_embeddings.json"
DEFAULT_OUT = ROOT / "resources/notions/pattern_posteriors.self_graded.json"
DEFAULT_DROP_LOG = ROOT / "resources/notions/pattern_posteriors.drop_log.self_graded.json"
DEFAULT_AB_REPORT = ROOT / "holes/labs/M-memes-arrows/pattern_posteriors_ab.self_graded.md"

OUTCOME_WEIGHTS = {
    "success": (1.0, 0.0),
    "partial": (0.5, 0.5),
    "fail": (0.0, 1.0),
}

CREDIT_ASSIGNMENT = {
    "v0": {
        "status": "active",
        "grain": "pattern",
        "rule": "PUR outcomes update pattern posteriors only; rollout realized-G(pi) updates move/R2 priors only.",
        "label": "self-graded",
    },
    "grounded": {
        "status": ":escrowed",
        "blocked_on": "M-peradam-grounding",
        "rule": "A certified peradam unit may be split across the closing move and member patterns, but total assigned credit must be <= 1.0.",
    },
}


@dataclass(frozen=True)
class PatternIndex:
    ids: set[str]
    basename_to_ids: dict[str, list[str]]


def load_pattern_index(path: Path = EMBEDDINGS) -> PatternIndex:
    rows = json.loads(path.read_text(encoding="utf-8"))
    ids = {row["id"] for row in rows}
    basenames: dict[str, list[str]] = defaultdict(list)
    for pid in sorted(ids):
        basenames[pid.rsplit("/", 1)[-1]].append(pid)
    return PatternIndex(ids=ids, basename_to_ids=dict(basenames))


def mission_files(code_root: Path = CODE_ROOT) -> list[Path]:
    files: list[Path] = []
    for repo in sorted(code_root.glob("futon*")):
        holes = repo / "holes"
        if holes.exists():
            files.extend(sorted(holes.glob("missions/**/*.md")))
    return files


def clean_pattern_token(raw: str) -> str:
    token = raw.strip().strip("*").strip("`").strip()
    token = re.split(r"\s+(?:\(|-|—|–|:)|\s+#|\s+//", token, maxsplit=1)[0]
    token = token.removeprefix("library/").removesuffix(".flexiarg")
    token = token.strip().strip("`").strip(".,;)")
    return token


def resolve_pattern(raw: str, index: PatternIndex) -> tuple[str | None, str | None]:
    token = clean_pattern_token(raw)
    if not token:
        return None, "blank-pattern"
    if token in index.ids:
        return token, None
    if "/" in token:
        tail = token.rsplit("/", 1)[-1]
        matches = index.basename_to_ids.get(tail, [])
        if len(matches) == 1:
            return matches[0], None
        if len(matches) > 1:
            return None, "ambiguous-pattern-basename"
        return None, "pattern-not-in-library"
    matches = index.basename_to_ids.get(token, [])
    if len(matches) == 1:
        return matches[0], None
    if len(matches) > 1:
        return None, "ambiguous-pattern-basename"
    return None, "pattern-not-in-library"


def field_value(window: str, label: str) -> str | None:
    m = re.search(
        r"\*{0,2}" + re.escape(label) + r"\*{0,2}:?\s*(.+?)"
        r"(?:\n\s*[-*]?\s*\*{0,2}[A-Z][A-Za-z -]{2,24}\*{0,2}:|\n\s*\n|$)",
        window,
        re.I | re.S,
    )
    if not m:
        return None
    return re.sub(r"\s+", " ", m.group(1)).strip().strip("*").strip()[:500]


def typed_outcome(raw: str | None) -> tuple[str | None, str | None]:
    if not raw:
        return None, "missing-outcome"
    low = raw.lower()
    if re.search(r"\bsuccess(?:ful)?\b|\bpass(?:ed)?\b|\bverified\b", low):
        return "success", None
    if re.search(r"\bpartial(?:ly)?\b|\bmixed\b|\bincomplete\b", low):
        return "partial", None
    if re.search(r"\bfail(?:ed|ure)?\b|\bnegative\b|\brefuted\b", low):
        return "fail", None
    return None, "unrecognized-outcome"


def pur_candidates(path: Path) -> Iterable[dict]:
    lines = path.read_text(encoding="utf-8", errors="ignore").splitlines()
    for i, line in enumerate(lines):
        pattern_line = re.match(
            r"\s*(?:[-*]\s*)?\*{0,2}Pattern\*{0,2}:?\s+(.+?)\s*$",
            line,
            re.I,
        )
        if not pattern_line or "chosen" in line.lower():
            continue
        window = "\n".join(lines[i : i + 14])
        if not (re.search(r"\bOutcome\b", window, re.I) or re.search(r"\bPrediction error\b", window, re.I)):
            continue
        yield {
            "path": str(path),
            "line": i + 1,
            "mission": path.stem,
            "raw_pattern": pattern_line.group(1),
            "outcome_raw": field_value(window, "Outcome"),
            "prediction_error": field_value(window, "Prediction error"),
            "actions": field_value(window, "Actions") or field_value(window, "Actions taken"),
            "anchor": line.strip()[:180],
        }


def pur_heading_drops(path: Path) -> Iterable[dict]:
    text = path.read_text(encoding="utf-8", errors="ignore")
    lines = text.splitlines()
    for i, line in enumerate(lines):
        if re.match(r"^\s*#{1,6}\s+.*\bPUR\b", line, re.I):
            window = "\n".join(lines[i : i + 18])
            if not re.search(r"\bPattern\b", window, re.I):
                yield {
                    "path": str(path),
                    "line": i + 1,
                    "mission": path.stem,
                    "reason": "pur-heading-without-pattern",
                    "anchor": line.strip()[:180],
                }


def extract_records(files: Iterable[Path], index: PatternIndex) -> tuple[list[dict], list[dict]]:
    records: list[dict] = []
    drops: list[dict] = []
    seen_refs: set[tuple[str, int]] = set()
    for path in files:
        for drop in pur_heading_drops(path):
            drops.append(drop)
        for cand in pur_candidates(path):
            ref_key = (cand["path"], cand["line"])
            if ref_key in seen_refs:
                continue
            seen_refs.add(ref_key)
            pattern_id, pattern_reason = resolve_pattern(cand["raw_pattern"], index)
            outcome, outcome_reason = typed_outcome(cand["outcome_raw"])
            reason = pattern_reason or outcome_reason
            if not cand["prediction_error"]:
                reason = reason or "missing-prediction-error"
            if reason:
                drops.append({**cand, "reason": reason})
                continue
            records.append(
                {
                    "pattern_id": pattern_id,
                    "outcome": outcome,
                    "outcome_raw": cand["outcome_raw"],
                    "prediction_error": cand["prediction_error"],
                    "actions": cand["actions"],
                    "evidence_ref": f"{cand['path']}:{cand['line']}",
                    "mission": cand["mission"],
                    "anchor": cand["anchor"],
                    "grading": "self-graded",
                }
            )
    return records, drops


def build_posteriors(records: list[dict], index: PatternIndex) -> dict:
    table = {
        pid: {"pattern_id": pid, "alpha": 1.0, "beta": 1.0, "n": 0, "evidence": []}
        for pid in sorted(index.ids)
    }
    for rec in records:
        alpha_inc, beta_inc = OUTCOME_WEIGHTS[rec["outcome"]]
        row = table[rec["pattern_id"]]
        row["alpha"] += alpha_inc
        row["beta"] += beta_inc
        row["n"] += 1
        row["evidence"].append(rec)
    for row in table.values():
        row["mean"] = round(row["alpha"] / (row["alpha"] + row["beta"]), 6)
        row["label"] = "self-graded"
    moved = [row for row in table.values() if row["n"]]
    return {
        "schema": "futon.pattern-posteriors.v0",
        "label": "self-graded",
        "prior": {"alpha": 1.0, "beta": 1.0},
        "outcome_weights": OUTCOME_WEIGHTS,
        "credit_assignment": CREDIT_ASSIGNMENT,
        "patterns": table,
        "summary": {
            "pattern_count": len(table),
            "patterns_with_evidence": len(moved),
            "accepted_purs": len(records),
        },
    }


def assign_v0_credit(evidence: dict) -> dict:
    """Semantic witness for the pattern-vs-move grain boundary.

    v0 consumes self-graded PUR outcomes at pattern grain. Rollout realized-G(pi)
    belongs to the move/R2 learner and does not update this posterior table.
    """
    kind = evidence.get("kind")
    if kind == "pur-outcome":
        return {
            "pattern_credit": 1.0,
            "move_credit": 0.0,
            "consumer": "pattern-posteriors",
            "label": "self-graded",
        }
    if kind == "rollout-realized-g":
        return {
            "pattern_credit": 0.0,
            "move_credit": 1.0,
            "consumer": "move-r2",
            "label": "move-grain",
        }
    return {
        "pattern_credit": 0.0,
        "move_credit": 0.0,
        "consumer": "none",
        "label": "unrecognized",
    }


def grounded_peradam_split_stub() -> dict:
    """Escrowed seam for M-peradam-grounding; not implemented in v0."""
    return {
        "status": ":escrowed",
        "blocked_on": "M-peradam-grounding",
        "implemented": False,
        "conservation_rule": "sum(move_credit + member_pattern_credits) <= 1.0 certified peradam unit",
        "consumer": "pattern-posteriors-after-grounding",
    }


def drop_summary(drops: list[dict], records: list[dict]) -> dict:
    return {
        "schema": "futon.pattern-posteriors.drop-log.v0",
        "label": "self-graded",
        "accepted_purs": len(records),
        "dropped_purs": len(drops),
        "drop_reasons": dict(sorted(Counter(d["reason"] for d in drops).items())),
        "drops": drops,
    }


AB_QUERIES = {
    "PUR-OVERLAP: witness/reduction proof strategy":
        "construct explicit witness proof verify reduce known result non circularity objection lemma mathematical argument",
    "BROAD: interim-director proxy-metrics":
        "proxy metric inventory value-generating arms ingest retrieve process evaluate report external uptake internal mastery pipeline health observation channels scaffold business evidence",
    "FOCUSED: AIF/EFE policy selection":
        "active inference expected free energy policy selection belief update observation vector precision action candidate ranking",
    "TECHNICAL: substrate ground-metric":
        "ground metric ollivier ricci curvature wasserstein fisher rao latent distance substrate tension field differentiable",
}


def write_ab_report(posteriors: dict, out: Path = DEFAULT_AB_REPORT, posterior_weight: float = 0.6) -> dict:
    import sys

    sys.path.insert(0, str(ROOT / "holes/labs/M-memes-arrows"))
    import cascade_construct as cascade

    lines = [
        "# Pattern Posterior A/B Surface",
        "",
        "**Label:** `self-graded`.",
        "",
        "Posterior term composes with coherence-greedy marginal coverage as a multiplier; it does not replace relevance/coherence.",
        "",
    ]
    results = {}
    for name, query in AB_QUERIES.items():
        baseline = cascade.construct_cascade(query, posterior_weight=0.0, posterior_table=posteriors)
        posterior = cascade.construct_cascade(
            query,
            posterior_weight=posterior_weight,
            posterior_table=posteriors,
        )
        surface = cascade.ranked_candidates(query, pool=20, posterior_table=posteriors)
        results[name] = {
            "baseline": baseline,
            "posterior": posterior,
            "surface": surface,
        }
        base_ids = [row[0] for row in baseline["cascade"]]
        post_ids = [row[0] for row in posterior["cascade"]]
        changed = base_ids != post_ids
        lines.extend(
            [
                f"## {name}",
                "",
                f"- baseline size/C: {baseline['size']} / {baseline['C']}",
                f"- posterior size/C: {posterior['size']} / {posterior['C']} (`posterior_weight={posterior_weight}`)",
                f"- changed construction: `{str(changed).lower()}`",
                "",
                "| pattern | embedding-rank | posterior-rank | posterior mean | n |",
                "|---|---:|---:|---:|---:|",
            ]
        )
        for row in surface[:12]:
            lines.append(
                f"| `{row['pattern_id']}` | {row['embedding_rank']} | {row['posterior_rank']} | "
                f"{row['posterior_mean']:.3f} | {row['posterior_n']} |"
            )
        lines.append("")
    out.write_text("\n".join(lines) + "\n", encoding="utf-8")
    return results


def write_projection(
    out: Path = DEFAULT_OUT,
    drop_log: Path = DEFAULT_DROP_LOG,
    ab_report: Path | None = DEFAULT_AB_REPORT,
) -> tuple[dict, dict, dict | None]:
    index = load_pattern_index()
    records, drops = extract_records(mission_files(), index)
    posteriors = build_posteriors(records, index)
    drop_doc = drop_summary(drops, records)
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(posteriors, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    drop_log.write_text(json.dumps(drop_doc, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    ab = write_ab_report(posteriors, ab_report) if ab_report else None
    return posteriors, drop_doc, ab


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", type=Path, default=DEFAULT_OUT)
    ap.add_argument("--drop-log", type=Path, default=DEFAULT_DROP_LOG)
    ap.add_argument("--ab-report", type=Path, default=DEFAULT_AB_REPORT)
    ap.add_argument("--no-ab", action="store_true")
    args = ap.parse_args()
    posteriors, drop_doc, ab = write_projection(args.out, args.drop_log, None if args.no_ab else args.ab_report)
    print(
        json.dumps(
            {
                "label": "self-graded",
                **posteriors["summary"],
                "dropped_purs": drop_doc["dropped_purs"],
                "drop_reasons": drop_doc["drop_reasons"],
                "ab_queries": 0 if ab is None else len(ab),
                "out": str(args.out),
                "drop_log": str(args.drop_log),
                "ab_report": None if args.no_ab else str(args.ab_report),
            },
            sort_keys=True,
        )
    )


if __name__ == "__main__":
    main()
