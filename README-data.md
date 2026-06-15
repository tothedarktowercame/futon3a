# External data — `resources/notions/`

Generated notions data (pattern embeddings, projections, indexes, posteriors)
is **not stored in this repo**. It is large, churns on every regeneration, and
is reproducible — so per the workspace policy, generated artifacts live under
**`~/code/data/`**, not in git.

`resources/notions/` is a **symlink** to `~/code/data/notions/`:

```
futon3a/resources/notions  ->  /home/joe/code/data/notions
```

Every code path that reads `resources/notions/...` — both the relative readers
in futon3a (`src/futon/notions.clj`, `missions.clj`, `flexiarg/projection.clj`)
and the `futon3a-root`-prefixed readers in futon3c
(`peripheral/proof_backend.clj`, `peripheral/real_backend.clj`, `dev.clj`) —
resolves through this symlink unchanged. The symlink itself is gitignored
(`/resources/notions`); the directory's former contents were untracked
(`git rm --cached`) on 2026-06-15.

## What's stored there

Produced by `scripts/index_patterns.sh` (and the pattern-posteriors tooling):

| File | What |
|------|------|
| `patterns-index.tsv` | canonical pattern index (id → fields) |
| `minilm_pattern_embeddings.json` | MiniLM pattern embeddings (the retrieval index) |
| `minilm_mission_embeddings.json`, `minilm_corpus_embeddings.json` | mission / corpus embeddings |
| `bge_*_embeddings.json` | BGE embeddings (preferred for retrieval; see superpod notes) |
| `glove_*`, `fasttext_pattern_embeddings.json` | alternate embedding sets |
| `pattern-projections.edn` | canonical flexiarg projections |
| `pattern-embedding-records.json` | projection embedding records |
| `pattern_posteriors*.json` | self-graded pattern posteriors |
| `sigil-index.edn`, `rationale-examples.edn`, `mission_records.json` | derived indexes/records |
| `corpus_projection_2d.{json,png}` | 2-D corpus projection (viz) |

## Setup on a fresh checkout / new box

The symlink and data don't come with a `git clone`. Recreate them:

```bash
mkdir -p ~/code/data/notions
ln -s ~/code/data/notions ~/code/futon3a/resources/notions
# then populate (regenerates the whole set):
cd ~/code/futon3a && ./scripts/index_patterns.sh
# (or rsync ~/code/data/notions/ from a box that already has it)
```

## Overrides

- `NOTIONS_EMBEDDINGS_PATH` — override the MiniLM pattern-embeddings path (`notions.clj`).
- `OUT_DIR` / `--out-dir` — where `index_patterns.sh` writes (defaults to `resources/notions`, i.e. the symlink target).
