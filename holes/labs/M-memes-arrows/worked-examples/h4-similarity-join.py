#!/usr/bin/env python3
"""H4 worked example — meme<->notions similarity join + cascade seeding.

Run:
  cd /home/joe/code/futon3a
  python3 holes/labs/M-memes-arrows/worked-examples/h4-similarity-join.py

Optional:
  MEME_DB_PATH=/path/to/meme.db python3 holes/labs/M-memes-arrows/worked-examples/h4-similarity-join.py
"""

import json
import math
import os
import sqlite3
from collections import Counter
from pathlib import Path

ROOT = Path("/home/joe/code")
FUTON3A = ROOT / "futon3a"
QUERY = "aif/expected-free-energy-scorecard"
TOP_K = 8
EXPECTED_CORE = {
    "candidate-pattern-action-space",
    "policy-precision-commitment-temperature",
    "structured-observation-vector",
    "term-to-channel-traceability",
}


def stem(pattern_id):
    return pattern_id.split("/", 1)[1] if "/" in pattern_id else pattern_id


def pattern_id(stem_value):
    return f"aif/{stem_value}"


def cosine(a, b):
    dot = sum(x * y for x, y in zip(a, b))
    na = math.sqrt(sum(x * x for x in a))
    nb = math.sqrt(sum(y * y for y in b))
    return dot / (na * nb) if na and nb else 0.0


def load_embeddings(path):
    records = json.loads(path.read_text())
    return {record["id"]: record["vector"] for record in records}


def embedding_nearest(vectors, query, top_k):
    q = vectors[query]
    scored = (
        (pattern, cosine(q, vector))
        for pattern, vector in vectors.items()
        if pattern != query
    )
    return sorted(scored, key=lambda item: -item[1])[:top_k]


def relational_pattern_stems():
    stems = set()
    for path in ROOT.glob("futon*/library/**/*.flexiarg"):
        name = path.stem
        if name.count("-") >= 2 and len(name) >= 12:
            stems.add(name)
    return stems


def cooccurrence_nearest(query_stem, relational_stems, top_k):
    counts = Counter()
    for mission in ROOT.glob("futon*/holes/**/M-*.md"):
        try:
            text = mission.read_text(errors="ignore")
        except Exception:
            continue
        present = sorted(stem for stem in relational_stems if stem in text)
        if query_stem in present:
            for other in present:
                if other != query_stem:
                    counts[other] += 1
    return counts.most_common(top_k), counts


def meme_db_path():
    return Path(os.environ.get("MEME_DB_PATH", str(FUTON3A / "meme.db")))


def table_exists(conn, table):
    row = conn.execute(
        "SELECT 1 FROM sqlite_master WHERE type='table' AND name=?",
        (table,),
    ).fetchone()
    return row is not None


def entity_id(conn, name):
    if not table_exists(conn, "entities"):
        return None
    row = conn.execute("SELECT id FROM entities WHERE name=?", (name,)).fetchone()
    return row[0] if row else None


def has_constructed_arrow(conn, have, want):
    if not table_exists(conn, "arrows"):
        return False
    source_id = entity_id(conn, have)
    target_id = entity_id(conn, want)
    if not source_id or not target_id:
        return False
    row = conn.execute(
        """
        SELECT id FROM arrows
        WHERE source_id=? AND target_id=? AND status='constructed' AND payload IS NOT NULL
        LIMIT 1
        """,
        (source_id, target_id),
    ).fetchone()
    return row is not None


def seed_candidates(query_stem, agreement, co_counts, emb_scores, db_path):
    candidates = []
    with sqlite3.connect(db_path) as conn:
        for target_stem in sorted(
            agreement,
            key=lambda s: (-co_counts[s], -emb_scores.get(pattern_id(s), 0.0), s),
        ):
            have = pattern_id(query_stem)
            want = pattern_id(target_stem)
            if not has_constructed_arrow(conn, have, want):
                candidates.append(
                    {
                        "have": have,
                        "want": want,
                        "status": "open",
                        "reason": "similar AND co-occurring AND no constructed arrow in meme store",
                        "embedding_score": round(emb_scores.get(want, 0.0), 6),
                        "co_applications": co_counts[target_stem],
                    }
                )
    return candidates


def require(condition, message):
    if not condition:
        raise AssertionError(message)


def main():
    embedding_path = FUTON3A / "resources/notions/minilm_pattern_embeddings.json"
    vectors = load_embeddings(embedding_path)
    query_stem = stem(QUERY)
    relational_stems = relational_pattern_stems()
    emb_near = embedding_nearest(vectors, QUERY, TOP_K)
    emb_scores = dict(emb_near)
    co_near, co_counts = cooccurrence_nearest(query_stem, relational_stems, TOP_K)

    emb_stems = {stem(pattern) for pattern, _score in emb_near}
    co_stems = {s for s, _weight in co_near}
    joinable = [pattern for pattern, _score in emb_near if stem(pattern) in relational_stems]
    agreement = emb_stems & co_stems
    db_path = meme_db_path()
    candidates = seed_candidates(query_stem, agreement, co_counts, emb_scores, db_path)

    join_ok = len(joinable) == TOP_K
    core_ok = agreement == EXPECTED_CORE
    seeded_ok = len(candidates) >= 1

    print("=== H4 similarity join worked example ===")
    print(f"notions-index: {embedding_path} ({len(vectors)} vectors)")
    print(f"meme-db: {db_path}")
    print(f"query: {QUERY} -> stem {query_stem}")
    print()
    print("EMBEDDING nearest:")
    for pattern, score in emb_near:
        print(f"  {score:.3f}  {pattern} -> {stem(pattern)}")
    print()
    print("CO-OCCURRENCE nearest:")
    for s, weight in co_near:
        print(f"  {weight:3d}  {s}")
    print()
    print(f"JOIN: {len(joinable)}/{TOP_K} embedding neighbours are relational pattern nodes")
    print("CROSS-VALIDATION agree:", sorted(agreement))
    print("embedding-only:", sorted(emb_stems - co_stems))
    print("co-occurrence-only:", sorted(co_stems - emb_stems))
    print()
    print("Seed candidates (:open, no constructed arrow in meme store):")
    for candidate in candidates:
        print(" ", json.dumps(candidate, sort_keys=True))
    print()

    require(join_ok, f"join failed: {len(joinable)}/{TOP_K}")
    require(core_ok, f"cross-validation core mismatch: {sorted(agreement)}")
    require(seeded_ok, "no :open seed candidates emitted")

    print(
        "PASS "
        f"join={len(joinable)}/{TOP_K} "
        f"core={len(agreement)}/4 "
        f"seeded={len(candidates)} "
        f"db={db_path}"
    )


if __name__ == "__main__":
    try:
        main()
    except Exception as exc:
        print("=== H4 similarity join worked example ===")
        print(f"FAIL {exc}")
        raise
