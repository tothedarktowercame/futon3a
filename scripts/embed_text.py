#!/usr/bin/env python3
"""Embed arbitrary text with MiniLM-L6-v2. Sibling to notions_search.py.

Usage:
  # Single item via --text
  embed_text.py --text "hello world"

  # Many items, one per line on stdin (NDJSON output)
  cat inputs.txt | embed_text.py --ndjson

  # Many items, JSON array {id, text} on stdin (JSON array output)
  cat items.json | embed_text.py --json

Output dim: 384 (sentence-transformers/all-MiniLM-L6-v2, normalized).
"""
from __future__ import annotations

import argparse
import json
import sys


def embed_one(model, text):
    vec = model.encode([text], normalize_embeddings=True)[0]
    return [float(x) for x in vec]


def embed_batch(model, texts):
    vecs = model.encode(list(texts), normalize_embeddings=True)
    return [[float(x) for x in v] for v in vecs]


def main():
    p = argparse.ArgumentParser(description="Embed text with MiniLM-L6-v2.")
    p.add_argument("--text", help="Embed a single string passed as argument.")
    p.add_argument("--ndjson", action="store_true",
                   help="Read one text per line from stdin, write NDJSON "
                        "{line: N, embedding: [...]}.")
    p.add_argument("--json", action="store_true",
                   help="Read JSON array [{id, text}, ...] from stdin, "
                        "write JSON array [{id, embedding}, ...].")
    p.add_argument("--model", default="sentence-transformers/all-MiniLM-L6-v2")
    args = p.parse_args()

    try:
        from sentence_transformers import SentenceTransformer
    except ImportError as exc:
        sys.stderr.write("sentence-transformers is required\n")
        raise SystemExit(1) from exc

    model = SentenceTransformer(args.model)

    if args.text is not None:
        vec = embed_one(model, args.text)
        print(json.dumps({"embedding": vec}))
        return

    if args.ndjson:
        lines = [line.rstrip("\n") for line in sys.stdin]
        vecs = embed_batch(model, lines)
        for i, v in enumerate(vecs):
            print(json.dumps({"line": i, "embedding": v}))
        return

    if args.json:
        items = json.load(sys.stdin)
        if not isinstance(items, list):
            sys.stderr.write("--json expects a JSON array on stdin\n")
            raise SystemExit(1)
        texts = [(it.get("text") or "") for it in items]
        vecs = embed_batch(model, texts)
        out = [{"id": items[i].get("id"), "embedding": vecs[i]}
               for i in range(len(items))]
        print(json.dumps(out))
        return

    p.print_help()
    raise SystemExit(2)


if __name__ == "__main__":
    main()
