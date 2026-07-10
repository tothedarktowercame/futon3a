# M-memes-arrows — Worked-Examples Gallery

Per Joe (2026-06-09): every phase/handoff "shows it working on a real example," examples
**accumulate** here, and we want **multiple** by mission end — spanning the BHK sorry-flavours
(§9.2) so the design is shown working across kinds, not on one. Phase boundaries get explicit
boundary-demos (§12.7) because seams are where wiring gaps surface.

Legend: ✅ built & runnable · 🔭 offline (spike/reference, pre-persistence) · ⬜ to build (handoff)

## Offline precursors (built during DERIVE/ARGUE/VERIFY)
| example | artifact | shows |
|---|---|---|
| 🔭 one-arrow / three-states (r3a) | `reference-case-one-arrow-three-stages.edn` | the canonical thesis: cascade→sorry→construction as one endpoint-keyed arrow maturing; r3a token-closure |
| 🔭 grain-spike (3 instances) | `grain_spike.clj` | cascade/sorry/construction write+read through the real meme API |
| 🔭 endpoint-extraction (mission-aif-head) | `endpoint_extraction_spike.clj` | `(have,want)` from a typed missing-head, validated vs the real resolved sorry; registry-canonicalisation required |
| 🔭 similarity-join (EFE cascade) | `similarity_join_spike.py` | meme↔notions join 8/8; embedding vs co-occurrence 4/4 corroboration + diagnostic |
| 🔭 endpoint-identity logic-model | `endpoint_identity_model.clj` | 5 keystone invariants: conforming clean, 5/5 adversarial caught |

## Live worked examples (one per handoff — the §12 acceptance bars)
| # | example | flavour | status |
|---|---|---|---|
| H1 | r3a + 2 arrows persisted & read back from `meme.db` | — | ✅ `h1-meme-writer.clj` PASS (before=0/after=3, round-trip); writer `src/meme/writer.clj`; reviewed+fixed determinism, clj-kondo 0/0 |
| H2 | `mission-aif-head` + 2nd head, endpoints from live registry | missing-head | ✅ `h2-endpoint-extraction.clj` PASS (3 conventions→1; documented-match; naive=false/canonical=true) vs **live** `scan-aif-heads`; ns `src/meme/endpoints.clj`; codex-2 `8ca2833`; clj-kondo 0/0. Finding: live head-ids include UUIDs (affects H3 keys). |
| H3 | r3a promoted correlated→open→constructed live; duplicate-mint unified | →-sorry | ✅ `h3-endpoint-identity.clj` PASS: r3a matured as ONE row, dup-mint unified (row-count=1), live probe 0 conforming + 5/5 adversarial caught; ns `src/meme/identity.clj`; codex-2 `e881cfa`; clj-kondo 0/0 |
| H4 | EFE cascade → seeded `:open` candidate sorry | cascade-seed | ✅ `h4-similarity-join.py` PASS: join 8/8, corroboration 4/4, **seeded 4 `:open` candidates** (similar+co-occurring+no-construction); codex-2 `ab38c12` |
| H5 | `support-coverage` constructed arrow → substrate-2 `code/v05/sorry` + `:promoted-from` | →-sorry | ✅ `h5-substrate2-promotion.clj` PASS (NON-LIVE): projection + `:promoted-from` + roundtrip + `:open` **refused** (R7); ns `src/meme/substrate2.clj`; codex-2 `caf7c20`; clj-kondo 0/0. **Live :7071 write DONE 2026-06-09** (operator-greenlit) → `hx:code/v05/sorry:futon3a/sorry/meme-arrow-6b69271667003880`; `h5-live-substrate2-promotion.clj` PASS; logged in README-memes-and-arrows.md §7. **T4 satisfied.** |
| H6 | over-threshold count trips the Contract-A watch | — | ✅ `h6-count-watch.clj` PASS (synthetic); **T3 ARMED** on live store via `t3-count-watch-armed.clj` (live-count=3, silent, idempotent); ns `src/meme/count_watch.clj`; codex-2 `39dd879` |

## Phase-boundary demos (§12.7 — the seams)
| id | seam | shows | status |
|---|---|---|---|
| B1 | H1→H3 | written arrow is promotable (persistence ↔ lifecycle) | ✅ covered by B5 capstone (+ its own example) |
| B2 | H2→H3 | extracted `(have,want)` is the unify key (canonicalisation seam, live) | ✅ covered by B5 capstone (+ its own example) |
| B3 | H4→H3 | seeded candidate minted as `:open` (similarity → store) | ✅ covered by B5 capstone (+ its own example) |
| B4 | H3→H5 | `:constructed` promotes, `:open` does NOT (priors↔facts; R7) | ✅ covered by B5 capstone (+ its own example) |
| B5 | H4→H3→H5 (capstone) | ONE arrow cascade→sorry→construction→fact, live | ✅ `b5-capstone-live.clj` PASS: one endpoint-keyed arrow, id stable across all stages → `hx:…meme-arrow-6b69271667003880` |

## Flavour gallery target (≥4 distinct arrows; generalisation test of the design)
| flavour | exemplar | status |
|---|---|---|
| →-sorry (method) | `r3a-likelihood-support-coverage` | 🔭 (offline; live via H3/H5) |
| ∃-sorry (witness) | `stub-lifts-pending-aif-edn` (companion `.aif.edn`) | ✅ `flavour-gallery-exists-forall.clj`: matures in store; extraction REFUSES (needs per-flavour extractor) |
| ∀-sorry (uniform) | `r3d-per-entity-attribution` | ✅ `flavour-gallery-exists-forall.clj`: matures in store; extraction REFUSES (needs per-flavour extractor) |
| capstone | the B5 walk | ✅ `b5-capstone-live.clj` |

**ANSWERED (2026-06-09):** the endpoint-identity **STORE is flavour-agnostic** (→/∃/∀ all mature
identically); endpoint-**EXTRACTION is per-flavour** (missing-head extractor works, refuses ∃/∀). The
store generalises; extraction fragments by signal-type, as it must. No store duplication.
