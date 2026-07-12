"""mark_labels.py — L2 adapter from L1 mark adjudications to reward labels.

Writes mark-labels.edn records shaped like fold_ground_truth records plus:
  grain="operator-mark", confidence, mark_id.

Only fold-deposit referents mint records.  ✓ => success true, ✘ => false,
💡 => no record.

Run:
  cd ~/code/futon3a && .venv/bin/python3 holes/labs/M-memes-arrows/mark_labels.py
"""
from __future__ import annotations

import json
import re
from pathlib import Path

LAB = Path(__file__).parent
L1_PATH = Path("/home/joe/code/futon2/holes/labs/M-zaif-harness/l1-mark-adjudications.edn")
OUT_PATH = LAB / "mark-labels.edn"
FOLD_TURNS = Path("/home/joe/code/futon6/data/fold-turns")


def _edn_unescape(s: str) -> str:
    return s.replace(r"\"", '"').replace(r"\n", "\n")


def parse_l1_records(path: Path = L1_PATH) -> list[dict]:
    if not path.exists():
        return []
    text = path.read_text()
    out = []
    for block in re.finditer(r'\{:mark\s+\{(.*?)\}\s*:referent\s+\{(.*?)\}\s*:mission\s+.*?:confidence\s+:(\w+)',
                             text, re.S):
        mark_raw, ref_raw, confidence = block.groups()
        mark_id = _field_string(mark_raw, "evidence-id")
        glyph = _field_string(mark_raw, "glyph")
        mark_type = _field_keyword(mark_raw, "type")
        kind = _field_keyword(ref_raw, "kind")
        ref_id = _field_string(ref_raw, "id")
        out.append({
            "mark": {"evidence-id": mark_id, "glyph": glyph, "type": mark_type},
            "referent": {"kind": kind, "id": ref_id},
            "confidence": confidence,
        })
    return out


def _field_string(raw: str, field: str):
    m = re.search(r':' + re.escape(field) + r'\s+"((?:[^"\\]|\\.)*)"', raw)
    return _edn_unescape(m.group(1)) if m else None


def _field_keyword(raw: str, field: str):
    m = re.search(r':' + re.escape(field) + r'\s+:(\S+)', raw)
    return m.group(1).rstrip(",") if m else None


def fold_turn_path(fid: str) -> Path:
    return FOLD_TURNS / f"{fid}.edn"


def parse_fold_turn(fid: str) -> dict | None:
    path = fold_turn_path(fid)
    if not path.exists():
        return None
    text = path.read_text()
    used_m = re.search(r':pattern-ids\s+\[([^\]]*)\]', text, re.S)
    used = re.findall(r'"([^"]+)"', used_m.group(1)) if used_m else []
    want_m = (re.search(r':psi\s+"((?:[^"\\]|\\.)*)"', text, re.S)
              or re.search(r':want\s+"((?:[^"\\]|\\.)*)"', text, re.S))
    want = _edn_unescape(want_m.group(1)) if want_m else None
    if not used or not want:
        return None
    return {"scope": fid, "used": used, "problem": want}


def mark_success(mark: dict):
    glyph = mark.get("glyph")
    mark_type = mark.get("type")
    if glyph == "✓" or mark_type == "approval":
        return True
    if glyph == "✘" or mark_type == "correction":
        return False
    return None


def labels_from_l1(records: list[dict]) -> list[dict]:
    out = []
    for r in records:
        ref = r.get("referent") or {}
        if ref.get("kind") != "fold-deposit":
            continue
        success = mark_success(r.get("mark") or {})
        if success is None:
            continue
        fid = ref.get("id")
        fold = parse_fold_turn(fid)
        if not fold:
            continue
        out.append({
            **fold,
            "success": success,
            "grain": "operator-mark",
            "confidence": r.get("confidence"),
            "mark_id": (r.get("mark") or {}).get("evidence-id"),
        })
    return out


def _edn_string(s: str) -> str:
    return json.dumps(s, ensure_ascii=False)


def write_edn(records: list[dict], path: Path = OUT_PATH):
    lines = ["["]
    for r in records:
        used = " ".join(_edn_string(p) for p in r["used"])
        lines.append(
            ' {:scope %s :success %s :used [%s] :problem %s '
            ':grain :operator-mark :confidence :%s :mark-id %s}'
            % (_edn_string(r["scope"]),
               "true" if r["success"] else "false",
               used,
               _edn_string(r["problem"]),
               r.get("confidence") or "unknown",
               _edn_string(r.get("mark_id") or "")))
    lines.append("]\n")
    path.write_text("\n".join(lines))


def load_mark_labels(path: Path = OUT_PATH) -> list[dict]:
    if not path.exists():
        return []
    text = path.read_text()
    out = []
    for block in re.finditer(
            r'\{:scope\s+"([^"]+)".*?:success\s+(true|false)\s+:used\s+\[([^\]]*)\]\s*'
            r':problem\s+"((?:[^"\\]|\\.)*)"\s*:grain\s+:operator-mark\s+'
            r':confidence\s+:(\S+)\s+:mark-id\s+"((?:[^"\\]|\\.)*)"', text, re.S):
        scope, success, used_raw, problem, confidence, mark_id = block.groups()
        out.append({"scope": scope,
                    "success": success == "true",
                    "used": re.findall(r'"([^"]+)"', used_raw),
                    "problem": _edn_unescape(problem),
                    "grain": "operator-mark",
                    "confidence": confidence,
                    "mark_id": _edn_unescape(mark_id)})
    return out


def main():
    records = labels_from_l1(parse_l1_records())
    write_edn(records)
    print(f"MARK_LABELS wrote {len(records)} records to {OUT_PATH}")


if __name__ == "__main__":
    main()
