#!/usr/bin/env python3
"""pattern_index.py — the pattern library's retrieval index as a SQLite sidecar.

TN-pattern-index-sidecar-2026-11-11 (futon3a holes/, 9da58aa1d), packet 1 of 5:
the store and its incremental builder.  No reader is changed by this packet.

  pattern_index.py build [--futon3-root PATH] [--model NAME] [--db PATH] [--full]
  pattern_index.py status [--db PATH]
  pattern_index.py search --query TEXT [--top N] [--db PATH] [--json]

Sidecar: ~/code/storage/futon3a/pattern-index.sqlite (override with
FUTON3A_PATTERN_INDEX_DB).  Tables: pattern (id, path, file_sha256, title,
if/however/then/because, conclusion, status, indexed_at), embedding
(pattern_id, model, dim, vector float32 BLOB, file_sha256), checkpoint
(one row per model per build: library HEAD, counts, ids, drift, finished_at).

Incremental by file sha256: re-embed only new or changed patterns, delete
ids no longer present, checkpoint last, all in ONE transaction so a reader
never sees a half-built state.

The embedding text is exactly what the current MiniLM index embeds: title +
clause texts, whitespace-normalised, produced by futon3a's own parser
(futon.flexiarg.projection --embed-json-out), invoked with `clojure` (clj
needs rlwrap on a non-tty).  Parts for the pattern table are parsed with
futon3c scripts/occurrence_square.py's read_pattern (the other existing
parser; the projection's canonical packets are not JSON-consumable).
"""
from __future__ import annotations

import argparse
import hashlib
import importlib.util
import json
import os
import re
import sqlite3
import struct
import subprocess
import sys
import tempfile
import time
from pathlib import Path

DEFAULT_DB = os.environ.get(
    "FUTON3A_PATTERN_INDEX_DB",
    str(Path.home() / "code/storage/futon3a/pattern-index.sqlite"))
DEFAULT_MODEL = "sentence-transformers/all-MiniLM-L6-v2"
HEADER = re.compile(r"^\s*@(arg|flexiarg|multiarg)\s+(\S+)", re.M)
STATUS = re.compile(r"^@status\s+(.*)$", re.M)
TITLE = re.compile(r"^@title\s+(.*)$", re.M)
SOURCE_ROOTS = ("library", "holes")  # what projection.clj covers (l.13)

FUTON3A_ROOT = Path(__file__).resolve().parent.parent
FUTON3_CODE_ROOT = Path(os.environ.get(
    "FUTON3_CODE_ROOT", str(Path.home() / "code/futon3")))
FUTON3C_OCC = Path.home() / "code/futon3c/scripts/occurrence_square.py"


def _occurrence_square():
    """futon3c occurrence_square — the existing parts parser.  Its
    read_pattern is bound to the canonical library path, so for arbitrary
    roots we replicate its rule below but reuse its TOP regex."""
    spec = importlib.util.spec_from_file_location("occurrence_square", FUTON3C_OCC)
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


def sha256_file(path: Path) -> str:
    h = hashlib.sha256()
    h.update(path.read_bytes())
    return h.hexdigest()


def scan_library(futon3_root: Path) -> dict:
    """Every @arg/@flexiarg/@multiarg section under library/ and holes/:
    id -> {path, file_sha256, title, status, parts}.  A .multiarg file holds
    one section per @arg header."""
    occ = _occurrence_square()
    out = {}
    for root_name in SOURCE_ROOTS:
        root = futon3_root / root_name
        if not root.is_dir():
            continue
        for path in sorted(root.rglob("*")):
            if path.suffix.lower() not in {".flexiarg", ".multiarg", ".arg"}:
                continue
            text = path.read_text(errors="replace")
            headers = list(HEADER.finditer(text))
            if not headers:
                continue
            rel = str(path.relative_to(futon3_root))
            sha = sha256_file(path)
            for i, m in enumerate(headers):
                pid = m.group(2)
                end = headers[i + 1].start() if i + 1 < len(headers) else len(text)
                section = text[m.start():end]
                sm = STATUS.search(section)
                tm = TITLE.search(section)
                out[pid] = {
                    "path": rel,
                    "file_sha256": sha,
                    "title": (tm.group(1).strip() if tm else pid),
                    "status": (sm.group(1).strip() if sm else ""),
                    "parts": parse_parts(section, occ.TOP),
                }
    return out


def parse_parts(text: str, top_re) -> dict:
    """IF/HOWEVER/THEN/BECAUSE + conclusion, occurrence_square.py's
    read_pattern rule (two-space indent = a top-level part), with its TOP
    regex passed in."""
    parts, cur, buf = {}, None, []
    conclusion = None
    for line in text.split("\n"):
        if line.startswith("! conclusion:"):
            conclusion = line[len("! conclusion:"):].strip()
        m = top_re.match(line)
        if m:
            if cur:
                parts[cur] = "\n".join(buf).strip("\n")
            cur, buf = m.group(1).upper(), ([m.group(2).strip()] if m.group(2).strip() else [])
            continue
        if cur is not None:
            if line and not line.startswith("    ") and not line.startswith("  +"):
                if line.strip():
                    parts[cur] = "\n".join(buf).strip("\n")
                    cur, buf = None, []
                    continue
            buf.append(line)
    if cur:
        parts[cur] = "\n".join(buf).strip("\n")
    return {"if": parts.get("IF"), "however": parts.get("HOWEVER"),
            "then": parts.get("THEN"), "because": parts.get("BECAUSE"),
            "conclusion": conclusion}


def embedding_records(futon3_root: Path, futon3a_root: Path) -> dict:
    """id -> embedding text, from futon.flexiarg.projection (the existing
    parser; identical text to what minilm_pattern_embeddings.json embeds)."""
    with tempfile.TemporaryDirectory() as td:
        edn_out = Path(td) / "projections.edn"
        json_out = Path(td) / "records.json"
        clj = os.environ.get("FUTON3A_CLJ", "clojure")
        subprocess.run(
            [clj, "-M", "-m", "futon.flexiarg.projection",
             "--futon3-root", str(futon3_root),
             "--out", str(edn_out),
             "--embed-json-out", str(json_out)],
            cwd=str(futon3a_root), check=True,
            stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        return {r["id"]: r["text"] for r in json.loads(json_out.read_text())}


def embed_texts(model_name: str, texts: list) -> list:
    os.environ.setdefault("HF_HUB_OFFLINE", "1")
    os.environ.setdefault("TRANSFORMERS_OFFLINE", "1")
    from sentence_transformers import SentenceTransformer
    model = SentenceTransformer(model_name, local_files_only=True)
    vecs = model.encode(list(texts), normalize_embeddings=True)
    return [[float(x) for x in v] for v in vecs]


def embed_stub(_model_name: str, texts: list) -> list:
    """TEST-ONLY (--embed-stub): deterministic hash vectors, no model load."""
    out = []
    for t in texts:
        d = hashlib.sha256(t.encode()).digest()
        out.append([b / 255.0 for b in d[:8]])
    return out


SCHEMA = """
CREATE TABLE IF NOT EXISTS pattern (
  id TEXT PRIMARY KEY,
  path TEXT NOT NULL,
  file_sha256 TEXT NOT NULL,
  title TEXT,
  if_ TEXT, however_ TEXT, then_ TEXT, because_ TEXT,
  conclusion TEXT, status TEXT,
  indexed_at TEXT NOT NULL);
CREATE TABLE IF NOT EXISTS embedding (
  pattern_id TEXT NOT NULL,
  model TEXT NOT NULL,
  dim INTEGER NOT NULL,
  vector BLOB NOT NULL,
  file_sha256 TEXT NOT NULL,
  PRIMARY KEY (pattern_id, model));
CREATE TABLE IF NOT EXISTS checkpoint (
  model TEXT NOT NULL,
  library_head TEXT,
  patterns_indexed INTEGER NOT NULL,
  library_ids TEXT NOT NULL,
  drift TEXT NOT NULL,
  finished_at TEXT NOT NULL,
  seq INTEGER NOT NULL,
  PRIMARY KEY (model, seq));
"""


def connect(db_path: str) -> sqlite3.Connection:
    Path(db_path).parent.mkdir(parents=True, exist_ok=True)
    conn = sqlite3.connect(db_path, timeout=30)
    conn.execute("PRAGMA busy_timeout=30000")
    conn.executescript(SCHEMA)
    return conn


def vec_blob(vec: list) -> bytes:
    return struct.pack("<%df" % len(vec), *vec)


def blob_vec(blob: bytes) -> list:
    n = len(blob) // 4
    return list(struct.unpack("<%df" % n, blob))


def library_head(futon3_root: Path):
    try:
        return subprocess.run(
            ["git", "-C", str(futon3_root), "rev-parse", "HEAD"],
            check=True, capture_output=True, text=True).stdout.strip()
    except Exception:
        return None


def build(futon3_root: Path, db_path: str, model: str = DEFAULT_MODEL,
          full: bool = False, embed_fn=None, records=None) -> dict:
    """Incremental build.  Returns a stats dict.  embed_fn(model, texts) is
    injectable for tests; records (id -> text) likewise."""
    t0 = time.time()
    inventory = scan_library(futon3_root)
    conn = connect(db_path)
    stored = {r[0]: r[1] for r in conn.execute("SELECT id, file_sha256 FROM pattern")}
    ids = set(inventory)
    deleted = sorted(set(stored) - ids)
    # self-heal: rows whose embedding is missing for this model or whose
    # embedding was computed from a different file_sha256 (the gate's
    # staleness figure) re-embed even when the pattern file is unchanged
    stale_emb = {r[0] for r in conn.execute(
        "SELECT p.id FROM pattern p LEFT JOIN embedding e"
        " ON e.pattern_id = p.id AND e.model = ?"
        " WHERE e.pattern_id IS NULL OR e.file_sha256 <> p.file_sha256",
        (model,))}
    changed = sorted(pid for pid in ids
                     if full or pid in stale_emb
                     or stored.get(pid) != inventory[pid]["file_sha256"])
    n_embedded = 0
    vectors = {}
    if changed:
        texts_by_id = records if records is not None else embedding_records(
            futon3_root, FUTON3A_ROOT)
        to_embed = [pid for pid in changed if pid in texts_by_id]
        if to_embed:
            embed = embed_fn or embed_texts
            vecs = embed(model, [texts_by_id[pid] for pid in to_embed])
            vectors = dict(zip(to_embed, vecs))
            n_embedded = len(to_embed)
    now = time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime())
    try:
        conn.execute("BEGIN IMMEDIATE")
        for pid in deleted:
            conn.execute("DELETE FROM embedding WHERE pattern_id=?", (pid,))
            conn.execute("DELETE FROM pattern WHERE id=?", (pid,))
        for pid in changed:
            inv = inventory[pid]
            p = inv["parts"]
            conn.execute(
                "INSERT OR REPLACE INTO pattern VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                (pid, inv["path"], inv["file_sha256"], inv["title"],
                 p["if"], p["however"], p["then"], p["because"],
                 p["conclusion"], inv["status"], now))
            if pid in vectors:
                conn.execute(
                    "INSERT OR REPLACE INTO embedding VALUES (?,?,?,?,?)",
                    (pid, model, len(vectors[pid]), vec_blob(vectors[pid]),
                     inv["file_sha256"]))
        indexed = {r[0] for r in conn.execute("SELECT id FROM pattern")}
        drift = {"missing": sorted(ids - indexed), "stale": sorted(indexed - ids)}
        seq = (conn.execute(
            "SELECT COALESCE(MAX(seq),0)+1 FROM checkpoint WHERE model=?",
            (model,)).fetchone()[0])
        ckpt = (model, library_head(futon3_root), len(indexed),
                json.dumps(sorted(ids)), json.dumps(drift), now, seq)
        # The checkpoint is written inside the transaction, so a reader never
        # sees patterns without the checkpoint that describes them.
        conn.execute("INSERT INTO checkpoint VALUES (?,?,?,?,?,?,?)", ckpt)
        conn.commit()
    except Exception:
        conn.rollback()
        conn.close()
        raise
    conn.close()
    return {"scanned": len(ids), "changed": len(changed), "deleted": len(deleted),
            "embedded": n_embedded, "seconds": round(time.time() - t0, 2)}


def build_sigil_snapshot(futon3_root: Path, db_path: str,
                         model: str = DEFAULT_MODEL) -> dict:
    """Run futon3's canonical Clojure derivation, then export TSV from the
    sidecar. No sigil, rationale, hotword, or devmap rule is duplicated here."""
    clojure = os.environ.get("FUTON3_CLJ", "clojure")
    export = futon3_root / "resources/sigils/patterns-index.tsv"
    conn = sqlite3.connect(db_path)
    embedding_seq = conn.execute(
        "SELECT max(seq) FROM checkpoint WHERE model=?", (model,)).fetchone()[0]
    conn.close()
    cmd = [clojure, "-M", "-m", "futon3.pattern-index.derivation",
           "--futon3-root", str(futon3_root), "--db", db_path,
           "--library-head", library_head(futon3_root) or "",
           "--embedding-seq", str(embedding_seq),
           "--export-tsv", str(export), "--no-examples"]
    proc = subprocess.run(cmd, cwd=str(FUTON3_CODE_ROOT), text=True,
                          stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    if proc.returncode:
        raise RuntimeError(
            f"canonical sigil snapshot failed ({proc.returncode}): {proc.stderr}")
    conn = sqlite3.connect(db_path)
    row = conn.execute(
        "SELECT rows_indexed, seq FROM sigil_checkpoint ORDER BY seq DESC LIMIT 1"
    ).fetchone()
    conn.close()
    return {"rows": row[0], "checkpoint_seq": row[1], "export": str(export)}


def status(db_path: str, futon3_root: Path | None = None) -> dict:
    conn = connect(db_path)
    rows = conn.execute(
        "SELECT model, library_head, patterns_indexed, drift, finished_at, seq"
        " FROM checkpoint ORDER BY model, seq").fetchall()
    latest = {}
    for model, head, n, drift, finished, seq in rows:
        latest[model] = {"library_head": head, "patterns_indexed": n,
                         "drift": json.loads(drift), "finished_at": finished,
                         "seq": seq}
    indexed = {r[0] for r in conn.execute("SELECT id FROM pattern")}
    n_stale_emb = conn.execute(
        "SELECT count(*) FROM embedding e JOIN pattern p"
        " ON p.id = e.pattern_id WHERE e.file_sha256 <> p.file_sha256"
    ).fetchone()[0]
    conn.close()
    out = {"db": db_path, "checkpoints": latest, "indexed": len(indexed),
           "stale-embeddings": n_stale_emb}
    if futon3_root:
        live = set(scan_library(futon3_root))
        out["live-drift"] = {"missing": sorted(live - indexed),
                             "stale": sorted(indexed - live)}
    return out


def search(db_path: str, query: str, top: int, model: str = DEFAULT_MODEL) -> list:
    """Cosine top-N from the sidecar — same ranking as notions_search.py
    (normalised matrix @ normalised query, stable descending argsort)."""
    import numpy as np
    os.environ.setdefault("HF_HUB_OFFLINE", "1")
    os.environ.setdefault("TRANSFORMERS_OFFLINE", "1")
    os.environ["OMP_NUM_THREADS"] = "2"
    os.environ["OPENBLAS_NUM_THREADS"] = "1"
    from sentence_transformers import SentenceTransformer
    conn = connect(db_path)
    rows = conn.execute(
        "SELECT p.id, p.title, e.vector FROM embedding e"
        " JOIN pattern p ON p.id = e.pattern_id WHERE e.model=?", (model,)).fetchall()
    conn.close()
    ids = [r[0] for r in rows]
    matrix = np.asarray([blob_vec(r[2]) for r in rows], dtype=np.float32)
    norms = np.linalg.norm(matrix, axis=1, keepdims=True)
    norms[norms == 0.0] = 1.0
    matrix = matrix / norms
    enc = SentenceTransformer(model, local_files_only=True)
    q = np.asarray(enc.encode([query], normalize_embeddings=True)[0], dtype=np.float32)
    scores = matrix @ q
    order = np.argsort(-scores, kind="stable")[:top]
    return [{"rank": i + 1, "id": ids[int(idx)],
             "title": rows[int(idx)][1],
             "score": round(float(scores[int(idx)]), 4)}
            for i, idx in enumerate(order)]


def main():
    p = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    sub = p.add_subparsers(dest="cmd", required=True)
    b = sub.add_parser("build")
    b.add_argument("--futon3-root", default=str(Path.home() / "code/futon3"))
    b.add_argument("--model", default=DEFAULT_MODEL)
    b.add_argument("--db", default=DEFAULT_DB)
    b.add_argument("--full", action="store_true")
    b.add_argument("--embed-stub", action="store_true",
                   help="TEST ONLY: deterministic hash vectors, no model load")
    s = sub.add_parser("status")
    s.add_argument("--db", default=DEFAULT_DB)
    s.add_argument("--futon3-root", default=None,
                   help="also recompute live drift against this library")
    q = sub.add_parser("search")
    q.add_argument("--query", required=True)
    q.add_argument("--top", type=int, default=8)
    q.add_argument("--model", default=DEFAULT_MODEL)
    q.add_argument("--db", default=DEFAULT_DB)
    q.add_argument("--json", action="store_true")
    args = p.parse_args()
    if args.cmd == "build":
        stats = build(Path(args.futon3_root), args.db, args.model, args.full,
                      embed_fn=(embed_stub if args.embed_stub else None))
        stats["sigils"] = build_sigil_snapshot(Path(args.futon3_root), args.db,
                                                args.model)
        print(json.dumps(stats, indent=1))
    elif args.cmd == "status":
        root = Path(args.futon3_root) if args.futon3_root else None
        print(json.dumps(status(args.db, root), indent=1))
    elif args.cmd == "search":
        results = search(args.db, args.query, args.top, args.model)
        print(json.dumps(results) if args.json else
              "\n".join(f"{r['rank']}. {r['id']}  {r['score']}" for r in results))


if __name__ == "__main__":
    main()
