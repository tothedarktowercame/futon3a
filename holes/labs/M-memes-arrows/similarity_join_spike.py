#!/usr/bin/env python3
"""similarity_join_spike.py — M-memes-arrows VERIFY hook T2.

Tests the §9.5 LAYERED claim: the relational arrow store (meme; holds the arrow + lifecycle)
and the notions/embedding layer (holds similarity) COMPOSE by shared ids — and the embedding
layer is what surfaces which cascades are ripe to become sorries.

Two things checked, on real data:
  1. THE JOIN: a cascade/arrow endpoint id keys BOTH layers (notions `aif/<name>` <-> meme
     stem `<name>` by stripping the lib prefix — a canonicalisation, mirroring the EP finding).
  2. CROSS-VALIDATION (combining-methods-as-diagnostic): are the embedding-nearest neighbours
     of an endpoint the SAME as its co-occurrence-nearest? Agreement => the two methods
     corroborate (a strong cascade-seed); disagreement => the diagnostic signal itself.

Run:  cd ~/code/futon3a && python3 holes/labs/M-memes-arrows/similarity_join_spike.py
Uses the REAL repaired minilm pattern index (no model/embed-server: the query pattern is
already in the index, so we cosine its stored vector against the rest).
"""
import json, glob, math
from pathlib import Path
from collections import Counter

ROOT = Path("/home/joe/code")
QUERY = "aif/expected-free-energy-scorecard"   # a real §10.7 cascade endpoint

# --- load the real repaired notions pattern vectors ---
recs = json.load(open(ROOT / "futon3a/resources/notions/minilm_pattern_embeddings.json"))
vec = {r["id"]: r["vector"] for r in recs}
print(f"\n=== similarity-join spike (T2) ===\nnotions index: {len(vec)} pattern vectors (384-d)\n")

def cosine(a, b):
    d = sum(x*y for x, y in zip(a, b))
    na = math.sqrt(sum(x*x for x in a)); nb = math.sqrt(sum(y*y for y in b))
    return d / (na*nb) if na and nb else 0.0

# --- 1. embedding-nearest neighbours of the query (the similarity layer) ---
q = vec[QUERY]
emb_near = sorted(((i, cosine(q, v)) for i, v in vec.items() if i != QUERY),
                  key=lambda t: -t[1])[:8]
print(f"EMBEDDING-nearest to {QUERY}:")
for i, s in emb_near:
    print(f"  {s:.3f}  {i}")

# --- 2. co-occurrence-nearest of the same endpoint (the relational/cascade layer) ---
stem = QUERY.split("/", 1)[1]                       # canonicalise: aif/X -> X (the join key)
fx = {Path(f).stem for f in glob.glob(str(ROOT/'futon*/library/**/*.flexiarg'), recursive=True)}
P = {b for b in fx if b.count('-') >= 2 and len(b) >= 12}
co = Counter()
for p in ROOT.glob('futon*/holes/**/M-*.md'):
    try:
        present = sorted({b for b in P if b in p.read_text(errors='ignore')})
    except Exception:
        continue
    if stem in present:
        for b in present:
            if b != stem:
                co[b] += 1
coapp_near = co.most_common(8)
print(f"\nCO-OCCURRENCE-nearest to {stem} (cascade layer, real mission co-firing):")
for b, w in coapp_near:
    print(f"  {w:3d}  {b}")

# --- 3. THE JOIN: do the two layers share ids? (strip lib prefix to canonicalise) ---
emb_stems = {i.split("/", 1)[1] for i, _ in emb_near}
coapp_stems = {b for b, _ in coapp_near}
joinable = {i for i, _ in emb_near if i.split("/", 1)[1] in P}   # embedding id is a real pattern node
print(f"\nJOIN: embedding-neighbour ids that are also relational pattern nodes (shareable key): "
      f"{len(joinable)}/{len(emb_near)}")

# --- 4. CROSS-VALIDATION: agreement vs diagnostic-disagreement ---
agree = emb_stems & coapp_stems
print(f"\nCROSS-VALIDATION (combining-methods-as-diagnostic):")
print(f"  embedding-nearest stems : {sorted(emb_stems)}")
print(f"  co-occurrence stems     : {sorted(coapp_stems)}")
print(f"  AGREE (both methods)    : {sorted(agree)}  <- strong cascade-seeds")
print(f"  embedding-only          : {sorted(emb_stems - coapp_stems)}  <- semantic kin not yet co-fired (candidate edges)")
print(f"  co-occurrence-only      : {sorted(coapp_stems - emb_stems)}  <- co-fire w/o semantic kinship (the diagnostic signal)")

join_ok = len(joinable) >= 1
xval_ok = len(agree) >= 1
print(f"\nRESULT: join-works={join_ok}  cross-validation-meaningful={xval_ok}  => T2 "
      f"{'PASS' if (join_ok and xval_ok) else 'PARTIAL'}")
print("  (join needs canonical stem<->aif/<name> mapping — same registry-canonicalisation lesson as EP §11.3)")
