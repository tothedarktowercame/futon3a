# Excursion: Keep Pattern + Mission Embeddings Live

**Type:** Excursion (E-prefix; bounded repair, owned end-to-end by a single agent per
[[project_e_prefix_excursions]]).
**Status:** **CLOSED 2026-06-09** — repair landed (futon3a `8f8a099`) by codex-1 and
**reviewed/verified by claude-4** (author≠reviewer gate). All four completion criteria met;
guard added; live cache self-heals (stamp-keyed). Review notes at foot.
**Date:** 2026-06-09
**Author / framing:** claude-4 (futon3a). **Repair owner:** codex-1.
**Spawned from:** `futon3a/holes/missions/M-memes-arrows-patterns-diagrams.md` §2.1 MAP
closure (Q4 surprise: the MiniLM pattern index is an empty file). Split out as a repair so
the parent mission can stay on its main work.
**Repos:** futon3a (notions indices + regen scripts), futon3c (live consumer via
`real_backend`), futon2/substrate-2 (mission source for `--include-missions`).

## The issue (one line)

`futon3a/resources/notions/minilm_pattern_embeddings.json` is **0 bytes** — the MiniLM
embedding-search index for *patterns* is empty, while every sibling index is populated.
More generally: **pattern and mission embeddings drifting stale/empty without anyone
noticing is a repair-and-guard issue**, not a design question.

## 1. IDENTIFY

### Motivation

The futon3a notions layer offers two retrieval paths over patterns: a **TSV token path**
(`load-pattern-index` → `patterns-index.tsv`, used by `real_backend` `psr-search`) and a
**MiniLM embedding (semantic) path** (`notions.clj:76-78`, `NOTIONS_EMBEDDINGS_PATH`
default = `resources/notions/minilm_pattern_embeddings.json`). The embedding path's index
is currently an **empty file**, so MiniLM-cosine pattern retrieval silently returns
nothing useful. A silent empty index is the exact artifact case [[feedback_combining_methods_as_diagnostic]]
warns about — a retrieval method quietly contributing zero signal.

### Evidence (live, 2026-06-09)

Real sizes (symlinks followed) under `futon3a/resources/notions/`:

| index | bytes | state |
|---|---|---|
| `minilm_pattern_embeddings.json` | **0** | **EMPTY — the bug** |
| `fasttext_pattern_embeddings.json` | 3,483,488 | populated |
| `glove_pattern_embeddings.json` | 534,797 | populated |
| `minilm_mission_embeddings.json` | 2,220,135 | populated |
| `minilm_corpus_embeddings.json` | 13,934,544 | populated |
| `pattern-embedding-records.json` (records) | 2,684,485 | 1073 records, 77 namespaces |
| `mission_records.json` | 373,310 | populated |

So it is specifically the **MiniLM pattern** index that failed to write; MiniLM mission and
corpus indices wrote fine in the same family. The file carries a 2026-06-09 timestamp, so a
regen *ran* but produced an empty artifact (silent failure — likely a model-load /
sentence-transformers env error swallowed by the script, or an empty input list). Root-cause
is codex-1's to confirm.

### Scope in
- Regenerate `minilm_pattern_embeddings.json` so it is non-empty and at parity with the
  pattern record set.
- Confirm **mission** embeddings (`minilm_mission_embeddings.json` + `mission_records.json`)
  are current against today's mission set (refresh if stale).
- Add a **freshness/non-empty guard** so an empty or stale embedding artifact is *caught*
  next time instead of silently shipping (this is the durable half — the bug is that nothing
  noticed).

### Scope out
- No change to retrieval *algorithm* or ranking (BGE-vs-MiniLM quality is
  [[feedback_superpod_embeddings]]' concern, a separate thread — do not swap encoders here).
- No change to the parent mission's holes-as-arrows design work.
- No JVM restart (see Gates). On-disk regen + verification only; if the live JVM holds a
  stale in-memory index, flag it for a Drawbridge reload, do not restart.

### Completion criteria
1. `minilm_pattern_embeddings.json` is non-empty and contains embeddings for the pattern
   record set (target ≈ the 1073 `pattern-embedding-records.json` entries; MiniLM embeds full
   text so it should cover the records, not just the fastText vocab subset of 407).
2. Mission embeddings confirmed current (or refreshed) against the live mission set.
3. A non-empty/freshness check exists (script guard, test, or CI/check hook) that fails loudly
   on an empty or zero-length embedding artifact.
4. Evidence reported back: before/after byte sizes + entry counts, the exact regen command,
   and the verification output.

## 2. MAP — facts for the repair owner

### Regeneration mechanism (already exists — this is wiring, not writing)
`futon3a/scripts/index_patterns.sh` builds the indices:
- `--minilm` → MiniLM embeddings (default model `sentence-transformers/all-MiniLM-L6-v2`)
- `--fasttext PATH` / `--glove PATH` → those encoders
- `--include-missions` → refresh `mission_records` + mission embeddings from substrate-2/cache
- `--out-dir` defaults to `resources/notions`; `--futon3-root` defaults to `../futon3`

`scripts/embed_text.py` is the MiniLM sibling (output dim 384, normalized). Python env: the
Clojure side resolves a `.venv/bin/python3` walking up ≤6 parents, else `python3`
(`notions.clj` `venv-python`), overridable via `NOTIONS_PYTHON`. **A likely root cause is the
regen running under a python without `sentence-transformers` installed**, writing an empty
file — codex-1 should verify the venv has it before re-running.

### Live consumer (don't break it)
`futon3c/src/futon3c/peripheral/real_backend.clj:436` reads the **TSV** index for
`psr-search` (token path) — unaffected by this bug. The **embedding** path resolves
`NOTIONS_EMBEDDINGS_PATH` (default = the empty file). `notions.clj` caches embeddings in an
atom (`embeddings-cache`) and may spawn an embed-server — so a regenerated file may need the
cache invalidated to take effect in the running JVM.

### Ready vs missing
| Ready | Missing (the work) |
|---|---|
| `index_patterns.sh --minilm --include-missions` regen path exists. | A successful MiniLM-pattern run (current artifact is empty). |
| `pattern-embedding-records.json` (1073) defines the target record set. | Parity check: embeddings present for those records. |
| Sibling MiniLM indices (mission/corpus) prove the pipeline works. | A guard so an empty artifact fails loudly next time. |

## Gates (per workspace handoff protocol)
- If any Clojure is touched: **clj-kondo** clean on changed files.
- If any Lisp/Clojure: `futon4/dev/check-parens.el`.
- futon3a tests relevant to notions/embedding search pass (`clojure -X:test` or the notions
  subset).
- **Never restart the futon3c JVM** ([[feedback_no_server_restart]]); use Drawbridge reload
  if a live cache-invalidate is needed, and flag it rather than restarting.
- Report before/after sizes + counts + the exact command (evidence-first), and **bell
  claude-4 back** with a summary + commit shas.

## Provenance
Authored by claude-4 on 2026-06-09 from M-memes-arrows-patterns-diagrams §2.1 (MAP closure,
Q4 surprise). Dispatched to codex-1 by Agency bell per Joe's instruction ("having pattern and
mission embeddings up to date is a repair issue … belld that to codex-1 so we can focus on the
main work").

## Review (claude-4, 2026-06-09) — real gate, not rubber-stamp

**Verdict: PASS.** What I checked (auditable):
1. **Diff** (`git show 8f8a099`): exactly 2 files — `minilm_pattern_embeddings.json` + `scripts/index_patterns.sh`. Nothing else touched.
2. **Artifacts spot-checked against codex-1's claimed numbers — match exactly:** minilm_pattern 1073 entries / 14,249,705 B (**parity** with `pattern-embedding-records.json` 1073); minilm_mission 224 / 2,262,458 B (**parity** with `mission_records.json` 224; refreshed 220→224); corpus 1297. No JSON parse errors.
3. **Guard reviewed (the durable deliverable):** `require_minilm_python` precheck (fails loudly if sentence-transformers absent — the exact root cause) + write-to-`mktemp` → `assert_embedding_parity` (non-empty AND records==embeddings count) → atomic `mv`. This structurally prevents the recurrence ("shell redirect truncated the file to 0 bytes before Python failed"), since the real file is replaced only after the parity assertion. NOTIONS_PYTHON/.venv resolution mirrors the Clojure `venv-python` logic.
4. **Re-ran the projection test myself:** `clojure -X:test :nses '[futon.flexiarg.projection-test]'` → 5 tests, 21 assertions, 0 failures/0 errors (matches claim).
5. **Pre-existing-failure claim confirmed:** the `futon.missions-test` compile error (`No such var missions/parse-mission-doc`) is unrelated — this commit didn't touch that ns, and there is no `(defn parse-mission-doc …)` in `missions.clj` (only stale comments referencing it). Not a regression from this change.
6. **Live-cache loose end closed:** `futon.notions/embeddings-cache` is stamp-keyed (`load-embeddings-file` compares `file-stamp`; reloads on mismatch). The regen changed size+mtime, so the serving JVM auto-reloads on the next embedding query — **no Drawbridge reload or restart needed.**

**Minor note (not a blocker, policy question for later):** `minilm_pattern_embeddings.json` is committed as a 14 MB real file in futon3a (it was already git-tracked historically), whereas `minilm_corpus` is a `storage/` symlink. The in-repo-big-artifact vs storage-symlink inconsistency predates this change and was not introduced by it; worth a future decision on where these large indices should live.
