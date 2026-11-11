#!/usr/bin/env python3
"""Fuzzy pattern search against a precomputed embeddings file."""
from __future__ import annotations

import argparse
import json
import math
import os
import sys
from pathlib import Path
from typing import Dict, Iterable, List, Tuple


def _normalize(vec: Iterable[float]) -> List[float]:
    values = [float(x) for x in vec]
    norm = math.sqrt(sum(v * v for v in values))
    if norm == 0.0:
        return values
    return [v / norm for v in values]


def _dot(a: List[float], b: List[float]) -> float:
    return sum(x * y for x, y in zip(a, b))


def _load_sidecar_embeddings(db_path: Path, model: str) -> List[Dict[str, object]]:
    """Vectors from the pattern-index sidecar (packet 1), joined to titles."""
    import sqlite3
    conn = sqlite3.connect(f"file:{db_path}?mode=ro", uri=True, timeout=30)
    rows = conn.execute(
        "SELECT p.id, p.title, e.vector FROM embedding e"
        " JOIN pattern p ON p.id = e.pattern_id WHERE e.model=?", (model,)).fetchall()
    conn.close()
    import struct
    return [{"id": pid, "title": title or "",
             "vector": list(struct.unpack("<%df" % (len(blob) // 4), blob))}
            for pid, title, blob in rows]


def _sidecar_checkpoint_seq(db_path: Path) -> int:
    """The newest checkpoint seq — one cheap read; the reload signal."""
    import sqlite3
    conn = sqlite3.connect(f"file:{db_path}?mode=ro", uri=True, timeout=30)
    try:
        return conn.execute("SELECT COALESCE(MAX(seq),0) FROM checkpoint").fetchone()[0]
    except sqlite3.OperationalError:  # no checkpoint table yet
        return 0
    finally:
        conn.close()


def _stub_vec(text: str) -> List[float]:
    """TEST-ONLY (--embed-stub): the same hash vector pattern_index.py's
    embed_stub writes, so a stub-built sidecar and a stub query agree."""
    import hashlib
    d = hashlib.sha256(text.encode()).digest()
    return [b / 255.0 for b in d[:8]]


def _load_embeddings(path: Path) -> List[Dict[str, object]]:
    payload = json.loads(path.read_text())
    if isinstance(payload, dict):
        out = []
        for pid, vec in payload.items():
            out.append({"id": pid, "vector": vec})
        return out
    if isinstance(payload, list):
        return payload
    raise SystemExit(f"Unsupported embeddings format in {path}")


def _entry_type(entry: Dict[str, object]) -> str:
    value = entry.get("type") or entry.get("record_type")
    if isinstance(value, str):
        return value
    return ""


def _rank(query_vec: List[float], entries: List[Dict[str, object]], top: int) -> List[Tuple[float, Dict[str, object]]]:
    scored = []
    for entry in entries:
        vec = entry.get("vector")
        if not isinstance(vec, list):
            continue
        score = _dot(query_vec, _normalize(vec))
        scored.append((score, entry))
    scored.sort(key=lambda x: x[0], reverse=True)
    return scored[:top]


def _result_payload(ranked: List[Tuple[float, Dict[str, object]]]) -> List[Dict[str, object]]:
    results = []
    for idx, (score, entry) in enumerate(ranked, start=1):
        payload = {
            "rank": idx,
            "id": entry.get("id", "unknown"),
            "title": entry.get("title", ""),
            "score": round(float(score), 4),
        }
        for key in ("type", "source", "summary", "status", "phase", "path", "home_repo"):
            if key in entry:
                payload[key] = entry.get(key)
        results.append(payload)
    return results


class ResidentSearch:
    """One loaded encoder and one pre-normalised embedding matrix.

    With sidecar=PATH the matrix comes from the pattern-index sidecar and
    reloads by itself: before each query (at most once a second) the newest
    checkpoint seq is re-read (one cheap SQLite read); only when it moved are
    the vectors reloaded.  No JVM-side restart after a rebuild."""

    RELOAD_CHECK_SECONDS = 1.0

    def __init__(self, model_name: str, embeddings: Path, filter_type: str | None,
                 sidecar: Path | None = None, stub: bool = False) -> None:
        os.environ.setdefault("HF_HUB_OFFLINE", "1")
        os.environ.setdefault("TRANSFORMERS_OFFLINE", "1")
        os.environ["OMP_NUM_THREADS"] = "2"
        os.environ["OPENBLAS_NUM_THREADS"] = "1"
        import time
        self._time = time
        self._sidecar = sidecar
        self._stub = stub
        self._checkpoint_seq = -1
        self._last_seq_check = 0.0
        self._np = None
        if stub:
            self._model = None
        else:
            try:
                import numpy as np
                import torch
                from sentence_transformers import SentenceTransformer  # type: ignore
            except ImportError as exc:
                raise SystemExit("sentence-transformers and numpy are required for resident search") from exc

            self._np = np
            # The resident answers one query at a time. PyTorch's large default CPU
            # pool costs more in scheduling than MiniLM spends on this small batch.
            torch.set_num_threads(2)
            try:
                torch.set_num_interop_threads(1)
            except RuntimeError:
                pass
            self._model = SentenceTransformer(model_name, local_files_only=True)
        self._model_name = model_name
        self._filter_type = filter_type
        self._embeddings_path = embeddings
        self._load()

    def _load(self) -> None:
        if self._sidecar is not None:
            entries = _load_sidecar_embeddings(self._sidecar, self._model_name)
            self._checkpoint_seq = _sidecar_checkpoint_seq(self._sidecar)
        else:
            entries = _load_embeddings(self._embeddings_path)
        if self._filter_type:
            entries = [entry for entry in entries if _entry_type(entry) == self._filter_type]
        usable = [(entry, entry.get("vector")) for entry in entries]
        usable = [(entry, vec) for entry, vec in usable if isinstance(vec, list)]
        self._entries = [entry for entry, _ in usable]
        if self._np is None:  # stub mode: plain python lists
            self._matrix = [_normalize(vec) for _, vec in usable]
        else:
            matrix = self._np.asarray([vec for _, vec in usable], dtype=self._np.float32) \
                if usable else self._np.zeros((0, 384), dtype=self._np.float32)
            norms = self._np.linalg.norm(matrix, axis=1, keepdims=True)
            norms[norms == 0.0] = 1.0
            self._matrix = matrix / norms if usable else matrix

    def _maybe_reload(self) -> None:
        if self._sidecar is None:
            return
        now = self._time.monotonic()
        if now - self._last_seq_check < self.RELOAD_CHECK_SECONDS:
            return
        self._last_seq_check = now
        if _sidecar_checkpoint_seq(self._sidecar) != self._checkpoint_seq:
            self._load()

    def search(self, query: str, top: int) -> List[Dict[str, object]]:
        self._maybe_reload()
        if self._stub:
            q = _normalize(_stub_vec(query))
            ranked = sorted(
                ((_dot(q, vec), entry) for vec, entry in zip(self._matrix, self._entries)),
                key=lambda x: x[0], reverse=True)[:top]
            return _result_payload(ranked)
        query_vec = self._model.encode([query], normalize_embeddings=True)[0]
        scores = self._matrix @ self._np.asarray(query_vec, dtype=self._np.float32)
        order = self._np.argsort(-scores, kind="stable")[:top]
        ranked = [(float(scores[idx]), self._entries[int(idx)]) for idx in order]
        return _result_payload(ranked)

    def serve(self) -> None:
        print(json.dumps({"resident": "ready"}), flush=True)
        for raw in sys.stdin:
            try:
                request = json.loads(raw)
                query = request["query"]
                top = int(request.get("top", 8))
                if not isinstance(query, str) or not query or top <= 0:
                    raise ValueError("query must be non-empty and top must be positive")
                response: object = self.search(query, top)
            except Exception as exc:  # one bad request must not kill the resident
                response = {"error": type(exc).__name__, "message": str(exc)}
            print(json.dumps(response), flush=True)


def main() -> None:
    parser = argparse.ArgumentParser(description="Fuzzy search patterns with MiniLM embeddings.")
    parser.add_argument("--embeddings", default="resources/notions/minilm_pattern_embeddings.json",
                        help="Embeddings JSON file (list of {id, vector, title}).")
    parser.add_argument("--query", help="Query text (required outside resident mode).")
    parser.add_argument("--top", type=int, default=8, help="Number of matches to show.")
    parser.add_argument("--model", default="sentence-transformers/all-MiniLM-L6-v2",
                        help="SentenceTransformer model name or path.")
    parser.add_argument("--filter-type", choices=["mission", "pattern"],
                        help="Restrict results to entries carrying a matching type field.")
    parser.add_argument("--json", action="store_true",
                        help="Output results as JSON array.")
    parser.add_argument("--resident", action="store_true",
                        help="Read JSON requests from stdin and write one JSON result line each.")
    parser.add_argument("--sidecar", default=None,
                        help="Read embeddings from the pattern-index sidecar SQLite at PATH "
                             "instead of --embeddings; in --resident mode the matrix reloads "
                             "when the sidecar's checkpoint seq moves.")
    parser.add_argument("--embed-stub", action="store_true",
                        help="TEST ONLY: hash-based query embedding matching "
                             "pattern_index.py --embed-stub builds (no model load).")
    args = parser.parse_args()

    if args.resident:
        ResidentSearch(args.model, Path(args.embeddings), args.filter_type,
                       sidecar=(Path(args.sidecar) if args.sidecar else None),
                       stub=args.embed_stub).serve()
        return
    if not args.query:
        parser.error("--query is required outside resident mode")

    if args.embed_stub:
        query_vec = _stub_vec(args.query)
    else:
        try:
            from sentence_transformers import SentenceTransformer  # type: ignore
        except ImportError as exc:
            raise SystemExit("sentence-transformers is required for MiniLM search") from exc

        model = SentenceTransformer(args.model)
        query_vec = model.encode([args.query], normalize_embeddings=True)[0]
        query_vec = [float(x) for x in query_vec]

    entries = (_load_sidecar_embeddings(Path(args.sidecar), args.model)
               if args.sidecar else _load_embeddings(Path(args.embeddings)))
    if args.filter_type:
        entries = [entry for entry in entries if _entry_type(entry) == args.filter_type]
    ranked = _rank(query_vec, entries, args.top)

    if args.json:
        print(json.dumps(_result_payload(ranked)))
    else:
        for idx, (score, entry) in enumerate(ranked, start=1):
            pid = entry.get("id", "unknown")
            title = entry.get("title", "")
            if title:
                print(f"{idx:2d}. {pid} ({score:.4f}) - {title}")
            else:
                print(f"{idx:2d}. {pid} ({score:.4f})")


if __name__ == "__main__":
    main()
