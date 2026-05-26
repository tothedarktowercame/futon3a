# Pattern Indexing (Notions)

Futon3a treats fuzzy recall as a separate notions store. This workflow builds
the notions index from the Futon3 library patterns without writing facts.

Outputs land in `resources/notions`:
- `patterns-index.tsv` (pattern id + sigils + rationale + hotwords)
- `pattern-projections.edn` (canonical packet projection of flexiarg content)
- `pattern-embedding-records.json` (JSONL-style text records fed to embedder jobs)
- `rationale-examples.edn` (sampled rationale strings)
- `sigil-index.edn` (sigil allowlist from Futon3)
- `mission_records.json` (optional shared-corpus mission cache from substrate-2)
- `glove_pattern_neighbors.json` (optional fuzzy neighbors)
- `glove_pattern_embeddings.json` (optional GloVe embeddings)
- `fasttext_pattern_embeddings.json` (optional fastText embeddings)
- `minilm_pattern_embeddings.json` (optional MiniLM embeddings)
- `minilm_mission_embeddings.json` (optional MiniLM mission embeddings)
- `minilm_corpus_embeddings.json` (merged shared corpus: patterns + missions)

## Build

From `futon3a/`:

```
scripts/index_patterns.sh
```

To include GloVe neighbors:

```
scripts/index_patterns.sh --glove /path/to/glove.6B.50d.txt
```

To add fastText embeddings:

```
scripts/index_patterns.sh --fasttext /path/to/wiki.en.vec
```

To add MiniLM embeddings:

```
scripts/index_patterns.sh --minilm
```

To refresh the shared corpus that combines pattern and mission embeddings:

```
PYTHON_BIN=/home/joe/code/futon3a/.venv/bin/python3 \
CLJ_CMD=/usr/local/bin/clojure \
scripts/index_patterns.sh --minilm --include-missions
```

Or specify a model explicitly:

```
scripts/index_patterns.sh --minilm-model sentence-transformers/all-MiniLM-L6-v2
```

Dependencies for fastText/MiniLM live in `futon3/scripts/requirements-embeddings.txt`.

For a local venv:

```
python3 -m venv .venv
source .venv/bin/activate
python3 -m pip install -r futon3/scripts/requirements-embeddings.txt
```

Set `FUTON3_ROOT` or pass `--futon3-root` if your Futon3 checkout lives elsewhere.
Set `PYTHON_BIN` and/or `CLJ_CMD` when running from cron or another restricted
environment where `python3` / `clj` discovery via `PATH` is not reliable.

This does not build an ANN/HNSW index; it prepares the inputs that those
stores will consume.
