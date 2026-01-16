# Pattern Indexing (Notions)

Futon3a treats fuzzy recall as a separate notions store. This workflow builds
the notions index from the Futon3 library patterns without writing facts.

Outputs land in `resources/notions`:
- `patterns-index.tsv` (pattern id + sigils + rationale + hotwords)
- `rationale-examples.edn` (sampled rationale strings)
- `sigil-index.edn` (sigil allowlist from Futon3)
- `glove_pattern_neighbors.json` (optional fuzzy neighbors)
- `glove_pattern_embeddings.json` (optional GloVe embeddings)
- `fasttext_pattern_embeddings.json` (optional fastText embeddings)
- `minilm_pattern_embeddings.json` (optional MiniLM embeddings)

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

This does not build an ANN/HNSW index; it prepares the inputs that those
stores will consume.
