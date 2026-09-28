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
    """One loaded encoder and one pre-normalised embedding matrix."""

    def __init__(self, model_name: str, embeddings: Path, filter_type: str | None) -> None:
        os.environ.setdefault("HF_HUB_OFFLINE", "1")
        os.environ.setdefault("TRANSFORMERS_OFFLINE", "1")
        try:
            import numpy as np
            from sentence_transformers import SentenceTransformer  # type: ignore
        except ImportError as exc:
            raise SystemExit("sentence-transformers and numpy are required for resident search") from exc

        self._np = np
        self._model = SentenceTransformer(model_name, local_files_only=True)
        entries = _load_embeddings(embeddings)
        if filter_type:
            entries = [entry for entry in entries if _entry_type(entry) == filter_type]
        usable = [(entry, entry.get("vector")) for entry in entries]
        usable = [(entry, vec) for entry, vec in usable if isinstance(vec, list)]
        self._entries = [entry for entry, _ in usable]
        matrix = np.asarray([vec for _, vec in usable], dtype=np.float32)
        norms = np.linalg.norm(matrix, axis=1, keepdims=True)
        norms[norms == 0.0] = 1.0
        self._matrix = matrix / norms

    def search(self, query: str, top: int) -> List[Dict[str, object]]:
        query_vec = self._model.encode([query], normalize_embeddings=True)[0]
        scores = self._matrix @ self._np.asarray(query_vec, dtype=self._np.float32)
        order = self._np.argsort(-scores, kind="stable")[:top]
        ranked = [(float(scores[idx]), self._entries[int(idx)]) for idx in order]
        return _result_payload(ranked)

    def serve(self) -> None:
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
    args = parser.parse_args()

    if args.resident:
        ResidentSearch(args.model, Path(args.embeddings), args.filter_type).serve()
        return
    if not args.query:
        parser.error("--query is required outside resident mode")

    try:
        from sentence_transformers import SentenceTransformer  # type: ignore
    except ImportError as exc:
        raise SystemExit("sentence-transformers is required for MiniLM search") from exc

    model = SentenceTransformer(args.model)
    query_vec = model.encode([args.query], normalize_embeddings=True)[0]
    query_vec = [float(x) for x in query_vec]

    entries = _load_embeddings(Path(args.embeddings))
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
