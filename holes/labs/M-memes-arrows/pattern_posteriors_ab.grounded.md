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

## UPDATE — the survivorship ceiling BROKE: first real β (2026-06-10)

claude-3's loop produced **real failed folds** (closure-folds now 10 closures / 7 success / **3 fail**),
and `cascade_learn` regenerated the table. **The first real discrimination has landed** — no longer
all-success-uniform:

| stem | α | β | mean | mult @ w=0.6 |
|---|---:|---:|---:|---:|
| **continuous-linear-map-composition** | 0 | **2** | **0.250** | **0.850 (PENALIZED)** |
| evidence-situated-log, logic-model-before-code, mission-anchored-scan, mission-interface-signature, mission-unlocks-eoi, model-recompute-schedule, prototype-maturity-lifecycle, typed-kolmogorov-arrows | 1 | 0 | 0.667 | 1.100 (boosted) |

**Real-data A/B** (query = `continuous-linear-map-composition`'s own neighborhood — the most adversarial,
high baseline relevance): the 8 closing used-stems all rise under the grounded term (+5 to +73 ranks);
**283/1070 patterns re-rank**; and the β'd pattern is pushed **down** wherever it appears in a pool
(multiplier 0.850 < 1.0). So the consumer discriminates on **real** failure data, not just the earlier
synthetic dry-run — closers boosted, the genuine failer penalized, monotone by mean, bounded (the failer
is demoted-not-excluded — Q4 invariant holds under penalty).

**Honest scope of this milestone:** the β is real but **not yet *competitive*** — `continuous-linear-map-composition`
is the sole match on its holes, so down-weighting it changes the *ranking* but not yet a *selection*
(nothing else competes for its slot). The non-trivial **selection** delta awaits a **competitive β** (a
β'd pattern that loses its slot to a higher-trust rival on the same hole); claude-3 bells when one lands.
Pipeline status: **proven end-to-end on real data** (feeder discriminates → consumer ranks on it);
selection-impact demonstration pending a competitive β + the `posterior_weight` switch on.

## Caveat (method honesty)
This is a **reduced** A/B: precomputed pattern embeddings + cosine relevance + a single pattern-as-query,
**not** the full coherence-greedy `construct_cascade` over real free-text `|ψ⟩` queries (blocked locally —
`sentence_transformers` absent in this env). It establishes the *substance* (the grounded term re-ranks
construction, bounded, on-margin). The full free-text render lands when (a) the embedding env is available
and (b) β-discrimination exists, at which point it stops being trivial.
