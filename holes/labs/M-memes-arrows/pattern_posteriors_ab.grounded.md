# Pattern Posteriors — Grounded A/B (closure-fold-grounded, NOT self-graded)

**Source table:** `futon6/data/pattern_posteriors.grounded.json` — emitted by claude-3's
`futon6/scripts/cascade_learn.py` from `futon6/holes/closure-folds.edn` (the `:used`-subset of each
real closure; `success:true` folds → `α+=1` on each used pattern; Beta(1,1) prior). **Grounded, not
self-graded:** the signal is real closures in the shared store, not self-reported PURs.

**Consumer:** `cascade_construct.py` — `load_posteriors()` auto-prefers the grounded table when present
and adapts its flat/bare-stem shape; `posterior_multiplier` composes it onto the marginal `m'(p)`
(never replaces). Owned by claude-1 (the pattern-NODE surface; claude-3 owns the co-app EDGE surface).

## State of the grounded signal (2026-06-10)
5 closures recorded; **6 used-stems** carry evidence, **all at (α=1, β=0) → mean 0.667** → a **uniform
`1.10×` multiplier at `posterior_weight=0.6`**. All-success-each-once data: zero failures yet, so the
table cannot yet *discriminate* among the 6 (no β). This is honest sparse-but-grounded, the grounded
analogue of the v0 sparse-data finding.

## A/B result (reduced method — see caveat)
Query = `scan-coherence/mission-anchored-scan` (a used stem's own precomputed MiniLM vector); pool =
all 1071 embedded patterns; baseline = cosine relevance; grounded = relevance × `posterior_multiplier`.

| used-stem | baseline rank | grounded rank | moved | relevance |
|---|---:|---:|---:|---:|
| evidence-situated-log | #362 | #261 | **+101** | 0.234 |
| prototype-maturity-lifecycle | #414 | #319 | **+95** | 0.222 |
| mission-unlocks-eoi | #423 | #334 | **+89** | 0.218 |
| model-recompute-schedule | #459 | #373 | **+86** | 0.211 |
| typed-kolmogorov-arrows | #976 | #899 | **+77** | 0.107 |

**Construction CHANGES:** 277 / 1070 patterns re-rank under the grounded term (vs 0 at `weight=0`).
Completion-criterion #3 (the term *changes* construction) — **demonstrated on real embedding space.**

**Two findings, both honest:**
1. **Composition is sound and bounded** — the grounded term lifts historically-used patterns but cannot
   *manufacture a place*: `typed-kolmogorov-arrows` (relevance 0.107) is boosted yet stays at #899. Trust
   re-orders *within* the relevance ordering; it never overrides it. (Q4 invariant II, shown empirically.)
2. **No intra-used discrimination yet** — all 6 move up by a similar uniform 10%, because every used
   stem is at mean 0.667. Discrimination unlocks when `cascade_learn` stops skipping `success:false`
   folds (`β+=1` on the used subset) — claude-3's owned feeder refinement. *Until then the full
   coherence-greedy A/B render is deliberately deferred: with uniform data it would be a trivial uniform
   shift.*

## Caveat (method honesty)
This is a **reduced** A/B: precomputed pattern embeddings + cosine relevance + a single pattern-as-query,
**not** the full coherence-greedy `construct_cascade` over real free-text `|ψ⟩` queries (blocked locally —
`sentence_transformers` absent in this env). It establishes the *substance* (the grounded term re-ranks
construction, bounded, on-margin). The full free-text render lands when (a) the embedding env is available
and (b) β-discrimination exists, at which point it stops being trivial.
