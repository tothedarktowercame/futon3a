"""fold_ground_truth.py — the merged fold-grain discharge ground truth.

Sources:
  1. discharge_experiment.GROUND — the 10 closure-folds records with claude-1's
     resolver-blind problem texts (the validity anchor).
  2. futon6/holes/fold-turn-adjudications.edn — escrow-deposit records whose
     plans were FLOWN and adjudicated (evidence quoted per record); records
     with :success :unadjudicated are skipped (plans without outcomes are not
     ground truth). Want text = the deposit's own :hole :want (authored from
     the mission doc before the fold — resolver-blind by provenance).

Every record: {"scope", "success" (bool), "used" [pattern-ids], "problem" (want text)}.
"""
from __future__ import annotations
import re, sys
from pathlib import Path

LAB = "/home/joe/code/futon3a/holes/labs/M-memes-arrows"
sys.path.insert(0, LAB)
from discharge_experiment import GROUND  # noqa: E402

ADJUDICATIONS = Path("/home/joe/code/futon6/holes/fold-turn-adjudications.edn")
MARK_LABELS = Path("/home/joe/code/futon3a/holes/labs/M-memes-arrows/mark-labels.edn")


def _parse_adjudications(path=ADJUDICATIONS):
    text = path.read_text()
    out = []
    for block in re.finditer(
            r'\{:fold-turn/id\s+"([^"]+)".*?:used\s+\[([^\]]*)\]\s*'
            r':want\s+"((?:[^"\\]|\\.)*)"\s*:success\s+(\S+)', text, re.S):
        fid, used_raw, want, success = block.groups()
        if success not in ("true", "false"):
            continue                      # :unadjudicated — not ground truth
        out.append({"scope": fid,
                    "success": success == "true",
                    "used": re.findall(r'"([^"]+)"', used_raw),
                    "problem": want.replace('\\"', '"')})
    return out


def _parse_mark_labels(path=None):
    path = path or MARK_LABELS
    if not path.exists():
        return []
    from mark_labels import load_mark_labels
    return load_mark_labels(path)


def load_records(include_mark_labels=False):
    records = [{"scope": g["scope"], "success": bool(g["success"]),
                "used": g.get("used") or [], "problem": g["problem"]}
               for g in GROUND if g.get("used")]
    records += _parse_adjudications()
    if include_mark_labels:
        records += _parse_mark_labels()
    return records


if __name__ == "__main__":
    rs = load_records()
    pos = sum(1 for r in rs if r["success"])
    print(f"{len(rs)} fold-grain records: {pos} success / {len(rs) - pos} fail")
    for r in rs:
        print(f"  {r['scope']:44} {r['success']} used={len(r['used'])}")
