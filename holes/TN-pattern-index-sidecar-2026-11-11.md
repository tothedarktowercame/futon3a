# TN: pattern-index sidecar — the library's retrieval indexes, and a sidecar that keeps itself current

Discovery note (kimi-2, 2026-11-11), for Joe's "reindexing done right — no
external .tsv files, a sidecar so reindexing happens automatically" after the
81 象-proposal patterns were onboarded (futon3 `a59c721`).  Read-only survey;
no code.  File:line evidence throughout.

## 0. The files

Two homes, joined by symlinks in `futon3a/resources/notions/` (`ls -la`):
`patterns-index.tsv` → `futon3/resources/sigils/patterns-index.tsv`;
`minilm_corpus_embeddings.json`, `mission_records.json`,
`pattern-embedding-records.json`, `typed_mission_slots.json`,
`corpus_projection_2d.*` → `storage/futon3a/resources/notions/`.  The
rest (`minilm_pattern_embeddings.json`, `bge_*`, `fasttext_*`, `glove_*`,
`sigil-index.edn`, `rationale-examples.edn`) are plain files in
`futon3a/resources/notions/` or `futon3/resources/sigils/`.  A third,
parallel home exists at `/home/joe/code/data/notions/` — the writers in
item 2 disagree about which home they write.

## 1. Readers

| reader (file:line) | reads | caches | sees a new index when |
|---|---|---|---|
| futon3c `transport/http.clj:4180-4202` | `futon3/resources/sigils/patterns-index.tsv` | `!patterns-cache` defonce atom (l.4182) | **JVM restart only** |
| futon3c `agency/pattern_search.clj:29` (resident), `:141` (fallback) | `minilm_pattern_embeddings.json` via `notions_search.py` | resident python process holds it in RAM; fallback spawns a process per call | resident: on resident restart; fallback: next call |
| futon3c `agency/artifact_activation.clj:19-21,121-124` | sha256 of `notions_search.py` + the minilm JSON, in its retrieval descriptor | digest cached by (path, mtime, length) key (`!digest-cache` l.23) | mtime change |
| futon3c `xiang/lu_rerank.clj:113-125` (驴) | minilm JSON via `pattern-search/search` | inherits pattern_search's resident | as pattern_search |
| futon3c `xiang/lu_memory.clj:396-430`, prewarmed by `xiang/ma_reading_live.clj:17` | the 驴/m40 resident is `lu-situations.py` (situation sidecar), not the pattern index; pattern leg goes through pattern_search | resident process | as pattern_search |
| futon3c `peripheral/proof_backend.clj:954-975` | spawns `notions_search.py --query … --embeddings minilm_pattern_embeddings.json` per query | none (process per call) | next call |
| futon3c `peripheral/real_backend.clj:530-547` | `patterns-index.tsv` (path hunt incl. the futon3a symlink) | none observed (file read per call) | next call |
| futon3a `futon/notions.clj:30-38` | `patterns-index.tsv` | none (read per call) | next call |
| futon3a `futon/notions.clj:63,105-146` | `minilm_pattern_embeddings.json` | `embeddings-cache` keyed by **mtime+length stamp** (`file-stamp` l.110-115) | **mtime change — the only self-refreshing reader** |
| futon3c `live_efe_map.clj:24` | `bge_mission_embeddings.json` (missions, not patterns) | — | — |
| futon3a `futon/missions.clj:319` | `minilm_corpus_embeddings.json` | — | — |
| futon3 `src/futon3/pattern_hints.clj:20-30` | `sigil-index.edn` | `delay` (memoized) | process restart |

## 2. Writers

- **futon3a `scripts/index_patterns.sh`** — the MiniLM pattern indexer
  (`--minilm`; `--glove`/`--fasttext` optional, `--include-missions` for
  missions).  Writes into `--out-dir` (default `resources/notions`):
  `minilm_pattern_embeddings.json`, `minilm_corpus_embeddings.json`, etc.
  Uses `clj` when present (l.101-104) — `clj` needs rlwrap on a non-tty, so
  run it with `clojure` earlier on PATH.
  **Measured 2026-11-11** (this note's timing run, `--out-dir` a mktemp):
  1,779 patterns embedded, **42 s wall** (18:01:36→18:02:18; 400 s CPU,
  ~10 cores busy).
- **futon3a `scripts/notions_reembed_pipeline.sh`** — computes the drift
  figure (shelf patterns vs embedded patterns vs embedded missions;
  hyperedge limit pinned to the 1000 ceiling so checker and checked don't
  truncate together, l.8-16) and writes under `/home/joe/code/data/notions/`
  — a different home than `resources/notions`.
- **futon6 `scripts/daily_reembed.sh`** — re-embeds missions with
  `BAAI/bge-large-en-v1.5` into `/home/joe/code/data/notions/bge_mission_embeddings.json`
  (missions again, not patterns).
- **futon3 `scripts/build_pattern_index.clj`** (and `_incremental.clj`, with
  `data/pattern-index-cache.edn`) — write `futon3/resources/sigils/patterns-index.tsv`
  and `rationale-examples.edn`; sigil-index material also flows from these
  (they are the files claude-13 regenerated today for futon3 `a59c721`).

No timer or path unit drives any of these today; before claude-13's manual
run the last MiniLM pattern re-embed was 2026-09-29 (197 library ids
missing; bge_pattern_embeddings is from 2026-06-12 with 727 missing).

## 3. Which indexes still matter

Live readers: **minilm_pattern_embeddings.json** (the pattern index — 4
call paths), **patterns-index.tsv** (3 readers), **sigil-index.edn**
(pattern_hints), minilm_corpus + bge_mission (mission tooling).
**written-never-read**: `bge_pattern_embeddings.json` (no reader found in
futon3c/futon3a/futon6), `fasttext_*`/`glove_*` (only
`project_corpus_2d.py`, an offline visualiser), `rationale-examples.edn`
(no live JVM reader found).

## 4. Existing sidecar shapes to follow

- **futon1b sidecars** (`futon1b_sidecars.clj`, gates at l.71,91;
  `futon1b_entindex.clj:503-510`): a SQLite sidecar per derived family with
  a `reads-usable?` gate — true only when a checkpoint exists and the hook
  failure count has not moved since catch-up.  Readers own the gate.
- **test-registry SQLite** (futon3c `test_registry/sqlite_backend.clj:1-30`):
  one shared SQLite file, `busy_timeout`, indexed columns over an exact
  payload — proof that one SQLite file can serve several writers/readers
  without lock fights.
- **futon3c watchers** (`watcher/multi.clj`: poll-mode multi-repo ingest
  loop, in-JVM service with start!/stop!/status; `watcher/scope_reingest.clj`:
  debounce + single background thread on doc-land — the shape a
  library-watcher trigger wants).

## 5. Proposed design (no code)

One SQLite sidecar, e.g. `storage/notions-sidecar/patterns.sqlite`, owned by
a writer in the futon3c JVM (a `watcher/multi`-style poller on the futon3
library, or a systemd path unit — the watcher fits the stack's "one JVM"
preference and scope-reingest's debounce):

1. Schema: one row per library pattern — id, path, sha256 of the `.flexiarg`
   file, parsed parts (IF/HOWEVER/BECAUSE/THEN, rationale, hotwords, sigil),
   and per-model embedding BLOBs keyed by model name.
2. Incremental update keyed on file sha256: re-embed only new/changed
   patterns (the 42 s full embed amortises to milliseconds per changed file),
   delete rows for deleted files; commit the MiniLM model name with each row.
3. Freshness gate in `reads-usable?` style: meta table holds
   `checkpoint-ts` and the drift figure (library ids vs indexed ids, the
   count `notions_reembed_pipeline.sh` already computes); readers consult it
   and fall back to keyword search when drift > 0.
4. Reader migration, in order, none breaking: (a) `futon/notions.clj`
   (already mtime-aware — point it at the sidecar with the same stamp
   contract); (b) `pattern_search.clj`'s resident (restart picks the sidecar;
   the query path speaks the same top-N protocol); (c) `proof_backend.clj`
   per-call spawns; (d) `http.clj` and `real_backend.clj` TSV readers (the
   sidecar serves the same five columns; the TSV becomes a generated export,
   then dies); (e) `pattern_hints.clj` last (sigil-index is a different
   shape).
5. Replaces: `minilm_pattern_embeddings.json` first, then
   `patterns-index.tsv`; `bge/fasttext/glove_pattern_embeddings.json` are
   already never-read and just stop being written.  `data/notions/` vs
   `resources/notions/` writer disagreement disappears with the single
   sidecar path.
