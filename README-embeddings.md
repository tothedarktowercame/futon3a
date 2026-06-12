# README-embeddings — what is embedded, where, and why

Quick map of the futon stack's text embeddings: which data is vectorized, with which
model, where the artifact lives, and what it's for. Written 2026-06-12 after a BGE/OOM
scare; corrections welcome (a few corpus-side locations are marked **verify**).

## The two models (+ legacy)

| model | dim | size | role | instruction prefix? |
|---|---|---|---|---|
| **`sentence-transformers/all-MiniLM-L6-v2`** | 384 | 88 MB | the **live default** — fast, cheap; missions, patterns, iching generators | no |
| **`BAAI/bge-large-en-v1.5`** | 1024 | 1.3 GB | the **stronger** option — better semantic retrieval/grounding; partly wired | **yes** (queries get a `Represent this sentence…` prefix; see `bge_retract.py`) |
| legacy: GloVe, fastText | — | ~0.5–3 MB | older pattern embeddings, kept for comparison | n/a |

Both models are cached under `~/.cache/huggingface/hub/`. sentence-transformers is installed
in `futon6/.venv` and the repo `.venv`s (NOT `futon3c/.venv`).

## What's embedded where

| data | artifact | model | size | purpose |
|---|---|---|---|---|
| **Patterns** | `futon3/resources/embeddings/minilm_pattern_embeddings.json`, `futon3a/resources/notions/minilm_pattern_embeddings.json` | MiniLM | ~12–14 MB | pattern retrieval (find relevant patterns for a task/turn) |
| Patterns (legacy) | `…/fasttext_pattern_embeddings.json`, `…/glove_pattern_embeddings.json` | fastText / GloVe | 3.4 MB / 0.5 MB | pre-MiniLM baselines |
| **Missions** | `futon3a/resources/notions/minilm_mission_embeddings.json` | MiniLM | 2.2 MB | mission retrieval / portfolio inference |
| **iching generators (64 CT concepts)** | `futon5/resources/iiching-ct/iching-generator-embeddings.npy` (+ `.meta.json`) | MiniLM (384-d) | 96 KB | the CT-concept side of the iiching-CT codebook (`order` + `codes` in the meta). **Note:** this is the *concept-text* embedding; the CT↔hexagram *assignment* is a semantic match, not a geometry fit — see `futon5/resources/iiching-ct/` and the dark-tower excursions. |
| **CT corpus / arXiv math.CT scan** | BGE-grounded artifacts under `~/code/storage/futon6/…` / `futon6/data/` (term-prior `ct-term-prior.json`; dense BGE corpus embeddings **verify location**) | BGE (grounding) | **BIG** | grounding NE/scope extraction against the literature |
| **arXiv hypergraphs (big data)** | storage-side | BGE recompute | **BIG** | the recompute that **OOM'd the box** (see below) |

## MiniLM vs BGE — and the OOM lesson

- **MiniLM is the default because it's cheap** (88 MB model, 384-d) and the small-data corpora
  are genuinely small (patterns ~14 MB, missions ~2 MB, iching 96 KB).
- **BGE-large is better** for semantic retrieval/grounding (1024-d, top-tier MTEB) — confirmed by
  the stack's "BGE-grounded" adoption and `feedback_superpod_embeddings`. The catch: it needs the
  query **instruction prefix**, and the model is 1.3 GB.
- **The OOM was big-data, not small-data.** A prior full-corpus / arXiv-hypergraph BGE recompute
  ran the box out of memory and was backed off (hence missions/patterns are *still* on MiniLM).
  The lesson: **big-data BGE recomputes need memory-managed batching** (chunk + flush, or a cap);
  **small-data (patterns / missions / iching, all < 15 MB) can move to BGE cheaply** — embedding
  ~thousands of short texts is just the 1.3 GB model load + a trivial batch.

## Build / pipeline scripts

- `futon3a/scripts/embed_text.py` — the **MiniLM** pipeline (`model.encode(..., normalize_embeddings=True)`, 384-d). Sibling: `notions_search.py`.
- `futon5/tools/iiching/bge_retract.py` — **BGE** experiment in the iching/retract context (run via `futon3a/.venv/bin/python`; uses the BGE query-instruction prefix).
- `futon5/tools/iiching/build_iching_codebook.py` — builds the 64-generator codebook (gates on Lean `CategoryTheory`, triangulates arXiv-df + nLab in-link).

## Open decisions / TODO

1. **Migrate missions+patterns to BGE** — worth it (better retrieval), and *cheap* because the data
   is small. Do it as a clean re-embed via `embed_text.py` with the BGE model swapped in (mind the
   query-instruction prefix), not a one-shot full-corpus run.
2. **Big-corpus BGE recompute** — only with memory-managed batching; that's the one that OOM'd.
3. **iching CT↔hexagram match as the BGE pilot** — embed the 64 hexagram rich-texts + 64 CT
   concepts with BGE (128 short texts, low-risk even post-OOM), do the bipartite **meaning**-match.
   **Hamming is RETIRED as a metric (Joe, 2026-06-12)** — measured hexagram meaning-distance vs
   Hamming-distance is ~zero-correlated (the strongest meaning-twins Qián/Kūn, Jìjì/Wèijì sit at
   *maximal* Hamming 6, because I Ching meaning is complement/inversion structure). The 6-bit code
   is an index/name only; use BGE embeddings for distance, the hexagram rich-text for attested
   interpretation. Evidence: `futon5/tools/iiching/hexagram_hamming_corr.py`; decision recorded in
   `futon5/resources/iiching-ct/iiching-ct-codebook.edn` `:meta :hamming-metric :retired`.
4. **Verify** the exact location/format of the dense BGE *corpus* embeddings (this doc found the
   term-prior + the MiniLM small-data, but did not pin the big-data BGE artifact).
