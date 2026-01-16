#!/usr/bin/env python3
"""Fuzzy pattern search against a precomputed embeddings file."""
from __future__ import annotations

import argparse
import json
import math
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


def main() -> None:
    parser = argparse.ArgumentParser(description="Fuzzy search patterns with MiniLM embeddings.")
    parser.add_argument("--embeddings", default="resources/notions/minilm_pattern_embeddings.json",
                        help="Embeddings JSON file (list of {id, vector, title}).")
    parser.add_argument("--query", required=True, help="Query text.")
    parser.add_argument("--top", type=int, default=8, help="Number of matches to show.")
    parser.add_argument("--model", default="sentence-transformers/all-MiniLM-L6-v2",
                        help="SentenceTransformer model name or path.")
    args = parser.parse_args()

    try:
        from sentence_transformers import SentenceTransformer  # type: ignore
    except ImportError as exc:
        raise SystemExit("sentence-transformers is required for MiniLM search") from exc

    model = SentenceTransformer(args.model)
    query_vec = model.encode([args.query], normalize_embeddings=True)[0]
    query_vec = [float(x) for x in query_vec]

    entries = _load_embeddings(Path(args.embeddings))
    ranked = _rank(query_vec, entries, args.top)

    for idx, (score, entry) in enumerate(ranked, start=1):
        pid = entry.get("id", "unknown")
        title = entry.get("title", "")
        if title:
            print(f"{idx:2d}. {pid} ({score:.4f}) - {title}")
        else:
            print(f"{idx:2d}. {pid} ({score:.4f})")


if __name__ == "__main__":
    main()
