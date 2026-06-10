# Mission: M-pattern-posteriors

*Keep score on which patterns actually help, and let that score — auditable,
per-pattern — inform which patterns get picked next time. This is the mission that
makes the AIF learning loop **pattern-based**: learning lifted from move grain
(5 metric weights) to the pattern library itself.*

**Date:** 2026-06-10 — **Phase: IDENTIFY** (drafted by Fable from the 2026-06-10
planning session over `futonzero-alphazero.md`; PM handoff → Opus pool, build → codex pool).
**Principal:** Joe.
**Repos:** futon3a (cascade constructor, phylogeny, notions index), futon3
(`library/` — the 996 `.flexiarg` patterns), futon2 (WM consumption), futon7
(grounded outcomes via M-peradam-grounding).
**Cross-ref:** `futon2/docs/futonzero-alphazero.md` §2 ("cascade = on-the-fly Pattern
Language") + §5 (self-graded reward), `futon2/holes/M-wm-policies.md` §"The policy
framework" (semilattice, coherence-greedy, monotone-`C`, budget-external),
`futon4/holes/mission-lifecycle.md` §PSR/PUR Discipline (the intake that already
exists), [[M-peradam-grounding]] (the grounded outcome source), M-aif2
(extensible-registry: patterns as operators whose trust is learned),
the Bayesian-structure-learning frame (reliability posteriors replacing heuristic
counters; send-to-scale = expected info gain).

---

## 0. Why this is a mission, not a task

The *known* parts are reused: the cascade constructor
(`futon3a/holes/labs/M-memes-arrows/cascade_construct.py` — relevance +
coherence-greedy + coverage-saturation), the pattern phylogeny (2,538
co-application edges = the prior over *what combines*), the PSR/PUR discipline and
its parser (the mission watcher already lifts `## PSR`/`## PUR` sections into
substrate-2 as `:mission/psrs` / `:mission/purs`). What is **genuinely unknown**:

- **Credit assignment over a semilattice.** A cascade succeeds or fails as a whole;
  patterns overlap ("A City is Not a Tree"). Which member pattern earned the win?
  Candidate rules exist (marginal-coverage-at-insertion; uniform within the strong
  centres; centrality-weighted) — none is obviously right. Carry into DERIVE.
- **PUR text → typed outcome.** PURs carry `Outcome: success/partial/fail` and
  `Prediction error:` fields by convention, in free text of varying discipline.
  What is the honest extraction, and what gets dropped as unusable?
- **How the posterior enters construction without re-introducing the pointwise
  degeneracy.** Per-pattern trust is a *pointwise* signal; the recurring lesson at
  every scale (cursor bug; naive cascade proxy) is *reward wholeness, never
  pointwise-greedy*. The posterior must compose with the coherence-greedy/wholeness
  machinery, not replace it. Getting this composition wrong rebuilds the cursor bug
  at the library scale.
- **Whether there is enough signal at all.** PURs are sparse and self-reported;
  grounded (peradam-attributed) outcomes are rarer still. A null finding —
  "posteriors don't move, the data doesn't exist yet" — is a legitimate exit that
  redirects effort to intake discipline.

## 1. IDENTIFY

### Motivation

The cascade constructor ranks patterns by MiniLM relevance and assembles them
coherence-greedily; the phylogeny prior says what *tends to combine*. **Nothing in
the stack encodes what *worked*.** A pattern that has fired in thirty missions with
attested-success PURs and a pattern that has never survived contact both enter the
candidate pool on embedding similarity alone. Meanwhile the loop's R2 learning
trains five *metric weights* at move grain — the pattern library, the thing the
FutonZero framing calls the actual policy substrate, learns nothing from its own
outcome history.

The intake half of this already exists as discipline: PSR records selection, PUR
records outcome and prediction error, and the watcher surfaces both. They are
written for *humans and future agents*; no machine consumer closes the loop. This
mission is that consumer.

### Plain-language gap statement

We write down which reasoning patterns we chose and how they turned out, but
nothing ever reads those records to choose better next time. Build the reader, and
make its opinion visible and checkable rather than baked-in.

### Theoretical anchoring

- **Reliability posteriors over heuristic counters** (the Bayesian-structure-learning
  frame; same shape as R12's per-action-class Beta posteriors in
  `futon2.aif.intrinsic-values` — that apparatus is the move-grain precedent, this
  is the pattern-grain instance).
- **Combining-methods-as-diagnostic:** disagreement between embedding-rank and
  posterior-rank is not noise to be averaged away — it IS a diagnostic surface
  (a pattern that *looks* relevant but historically fails is exactly what an
  operator wants flagged).
- **aif2 / niche construction:** patterns are operators in an extensible registry;
  learned trust per operator is the LEARN half of LEARN+INSTALL.
- **Defeasibility discipline** (from symbol-grounding): trust is meta-level and
  per-strategy, revisable; a posterior is a standing *prior for next time*, never a
  verdict on the pattern's truth.

### Scope in

- The PUR→typed-outcome extractor over the existing corpus (historical PURs across
  futon0–7 mission files + substrate-2 props), with an explicit drop-log of
  unusable records (no silent caps).
- Per-pattern reliability posterior (Beta-style: α/β + evidence refs), rebuildable
  from disk (reload-safety: the table is a projection of the records, never a
  hand-edited source).
- The credit-assignment rule, ratified explicitly (co-designed with
  M-peradam-grounding for the grounded path).
- A pluggable posterior term in cascade construction, **A/B-able** against
  relevance-only construction (the experiment-harness lesson from
  M-differentiable-substrate: don't fix blind, run the side-by-side).
- The grounded-update path: one peradam-attributed outcome moving one posterior,
  demonstrated, once M-peradam-grounding lands (deferred-gated, not blocking v0).

### Scope out

- Changing the wholeness scorer `C`, the coherence-greedy order, or the
  coverage-saturation stop (claude-3 / claude-4 / claude-1 ownership stands).
- Improving PUR *authoring* discipline (if the null finding fires, that becomes a
  recommendation, possibly a follow-on — not scope here).
- Pattern *content* revision (PURs feeding library evolution remains the
  lifecycle's human path).
- Cross-paper/strategy-level transfer learning beyond the per-pattern posterior.

### Honest staging

v0 runs on historical PURs and is therefore **self-graded** (the same closure
caveat as CH1 — labeled as such everywhere it surfaces, per the
"presentational-not-informational" discipline). It becomes *grounded* exactly when
M-peradam-grounding's credit-assignment rule delivers certificate-attributed
outcomes. Ship v0 anyway: the extractor, the table, and the A/B harness are the
same machinery either way, and the self-graded label is the honest bridge.

### Completion criteria

1. A posterior table over the pattern library exists, inspectable (per pattern:
   α/β, n, evidence refs back to the PURs/discharges that moved it), rebuilt
   deterministically from disk.
2. The extractor reports coverage honestly: how many PURs parsed, how many dropped
   and why (the drop-log is a deliverable, not a footnote).
3. The cascade constructor accepts the posterior as a pluggable term; an A/B on
   real `|ψ⟩` queries shows the term *changes* construction, and the disagreement
   surface (embedding-rank vs posterior-rank) renders legibly.
4. The credit-assignment rule is stated and ratified — including, if v1 declines to
   flow cascade-level outcomes down to members, that refusal recorded explicitly.
5. (Gated on M-peradam-grounding) ≥1 grounded outcome shifts ≥1 posterior,
   end-to-end, with the self-graded/grounded provenance distinguishable per
   evidence ref.

### Relationship to other missions

| Mission | Relationship |
|---|---|
| M-peradam-grounding | Upstream for the grounded path; credit-assignment rule co-designed there |
| M-wm-policies | Consumer: the cascade-policy lane's construction gains the posterior term |
| M-differentiable-substrate | Sibling learner: R2 learns move-grain weights; this learns pattern-grain trust; the two must not double-count |
| M-aif2 | Frame: extensible-registry / LEARN+INSTALL; this is LEARN at pattern grain |
| M-arguing-worlds | Sibling: tournament buildouts are cascades; posterior-informed vs relevance-only construction is itself tournament material |

### Source material

| Source | Role |
|---|---|
| futon3a/holes/labs/M-memes-arrows/cascade_construct.py | the constructor the posterior plugs into |
| futon3/library/ (996 .flexiarg) | the population the posterior ranges over |
| mission files futon0–7 (`## PSR`/`## PUR` sections) + substrate-2 `:mission/purs` | the historical outcome corpus |
| futon2/src/futon2/aif/intrinsic_values (R12 Beta posteriors) | the move-grain precedent for the posterior shape |
| pattern_phylogeny (2,538 co-app edges) | the combination prior the posterior sits beside, not inside |

### Carried tensions (named, not settled)

- **Pointwise-vs-wholeness composition** (the central design risk — see §0).
- **Self-graded v0** until grounding lands; label travels with every surface.
- **Sparse-data humility:** posteriors stay close to their priors until evidence
  accumulates; resist the temptation to sharpen presentationally (temperature
  lesson, M-differentiable-substrate Review finding 2).
- **Capstone form undecided** (`:O-capstone-form`).

---

## v0 result — built + reviewed PASS (codex-3 build, claude-1 PM-review, 2026-06-10)

**Branch:** `codex/m-pattern-posteriors-v0` (futon3a `6402ceb`). Artifacts: `pattern_posteriors.py`
(disk-rebuildable PUR extractor + typed classifier + Beta posterior + evidence-refs + the explicit
`CREDIT_ASSIGNMENT` seam), `cascade_construct.py` (optional `posterior_weight`, default 0.0, the posterior
as a **multiplier** on `m(p)`), the self-graded `.json` table + drop-log, the A/B report, tests.

**Review (claude-1, real gate — re-verified):** both anti-glibness gates HOLD —
(1) **compose-not-replace**: `posterior_multiplier` centers at 1.0 (inert at `weight=0` *or* no-evidence
`mean=0.5`), multiplies `m(p)`, never replaces; (2) **credit seam**: v0 = pattern-grain-only; grounded =
`:escrowed` with the **≤1.0 conservation** (one peradam can't inflate across move+patterns). Build verifies
independently: 5 tests pass, the rebuild reproduces the exact numbers deterministically (21 accepted PURs,
28 dropped [23 not-in-library, 5 heading-without-pattern], **12/1071 patterns with evidence**), self-graded
label on every surface.

**The honest v0 finding (the legitimate sparse-data exit — Completion #4 / "Sparse-data humility" above,
vindicated):** the machinery is *sound* but the PUR corpus is *too sparse* — only 12/1071 patterns carry
evidence, so the WM-style cascade surfaces mostly hit `n=0` → posterior inert → **null** (interim-director
21/5.698, AIF/EFE 17/5.164, substrate 4/0.574 all unchanged). It moves only on PUR-overlapping queries
(`construct-an-explicit-witness` n=6 mean=0.875: 26/8.225 → 27/8.404 at `weight=0.6`). **So the learning
layer works; it needs more evidence to move the WM** — grounded peradams (the escrowed car) and/or more PURs
(the scope-out "PUR-authoring discipline" follow-on). Recorded honestly; falsifiable stance held. Mergeable
on operator/coordinator call.

**Role-fork resolved (Joe via claude-3, 2026-06-10 — `:O-peradam-role` → CALIBRATION, not training):** the
grounded peradam *calibrates* the posterior (does a pattern's PUR-learned trust correspond to real
discharges?) — an **audit**, not a second training signal. This sharpens the v0 null into **two distinct
axes**, not one: **(1) coverage** — only 12/1071 patterns have *any* PUR, so most of the library is
*untrained*; more PURs extend coverage (that is training). **(2) calibration** — even the trained 12 are
*self-graded* until peradams audit them. So the earlier "needs more evidence to move the WM — grounded
peradams *and/or* more PURs" resolves precisely: **more PURs train (coverage); the grounded peradam
calibrates (audit)** — different roles, not interchangeable. The machinery isn't starved-*for-training*
(PURs train it); it's *un-calibrated* until the car's peradams audit it. The ≤1.0-conservation credit rule
(co-designed on [[M-peradam-grounding]]) still applies on the calibration path; escrow stays `:held`.

## Grounded-learning convergence — grounding-source = FOLDS, escrow RELEASABLE (2026-06-10, claude-1/3/4)

Spec: `futon3c/holes/campaigns/grounded-learning-spec.md`. Three contracts agreed (3/3). **The grounding
source moved from the peradam (the car) to FOLD-USAGE** — and that supersedes the line just above: the
posterior is **grounded by real closures** (`futon6/holes/closure-folds.edn` `:used`-subsets →
`cascade_learn.py` → `futon6/data/pattern_posteriors.grounded.json`, Beta(1,1)), **not** by peradams. The
peradam is now strictly the **out-of-loop AUDITOR** (Goodhart/drift-check), which keeps the
calibration-not-training resolution intact while relocating the *grounding* to the live fold loop. **So this
mission's grounded path no longer waits on the car** — escrow is **RELEASABLE on the closure signal**, gated
only on Joe's STANDARD-VERIFY.

**My surface (confirmed): the pattern-NODE posteriors + the A/B credit term.** claude-3 holds the co-app
EDGE surface (phylogeny upvote/seed). One closure projects onto vertices (my α/β) and edges (claude-3's
overlay) **orthogonally → non-double-counting by construction.**

**Q2 (credit assignment) — resolved.** Verified in code: `closure-folds.edn` records `:used` (the subset the
fold structurally rested on, e.g. `kit-outbox :used [mission-anchored-scan mission-unlocks-eoi]`, not the
27-pattern cascade), and `cascade_learn` credits **only `:used`** (`α+=1` per used stem). So used-subset
Bernoulli is *already the implementation* — the 6 stems in `grounded.json` ARE the used-subsets across 5
closures, not the ~135 cascade patterns. The cross-stage **non-double-count invariant** vs R2: no
closure-derived scalar enters both a move-weight and a pattern-posterior that are later *summed* in one
score — satisfied because R2 acts at move-ranking, the posterior at within-cascade construction. **Open
unlock (claude-3 owns):** `cascade_learn` currently skips `success:false` folds, so β never bumps and the
table can't discriminate; recording failed folds (`β+=1` on the used subset) makes discrimination appear.

**Q4 (composition — the cursor-bug risk) — resolved.** Three load-bearing invariants make composition
provably non-greedy: (I) **trust-neutral at prior** (unclosed pattern → Beta(1,1) mean 0.5 → multiplier
1.0); (II) **multiplicative on positive margin only** (trust re-weights `m'(p)=rel·connectivity`, already
wholeness-coherent; can't rescue a non-coherent candidate); (III) **bounded + saturation-external**
(multiplier in `[1−0.5w, 1+0.5w]`; never alters the coverage-saturation stop). EXPLORE coupling: my surface
exposes **variance** (α,β) so the star-map's explore term (Q3) gets Thompson-compatible epistemic boost —
mean=exploit, variance=explore.

**Grounded A/B — composition demonstrated (Completion #3, grounded).** Reduced A/B (precomputed embeddings,
query = `mission-anchored-scan`): all 6 grounded used-stems move **up** (+77 to +101 ranks) under the
grounded term, **277/1070 patterns re-rank** (vs 0 at weight 0) → construction changes. Bounded/on-margin
shown empirically: `typed-kolmogorov-arrows` (rel 0.107) is boosted yet stays #899 — trust nudges within the
relevance order, never manufactures a place. **No intra-used discrimination yet** (all at mean 0.667 → uniform
1.10× boost) — that awaits claude-3's failed-fold β refinement. Full free-text coherence-greedy render
deferred until β-discrimination exists (else a trivial uniform shift) + the embedding env is available.
Artifact: `holes/labs/M-memes-arrows/pattern_posteriors_ab.grounded.md`.

**STANDARD-VERIFY ratified — escrow `:contract-released`, car-independent (Joe via claude-3, 2026-06-10).**
The gate above ("releasable on the closure signal, gated on STANDARD-VERIFY") is **cleared**: grounding-source
is the **closure-fold** (`pattern_posteriors.grounded.json`), peradam is the out-of-loop auditor only, and
the path is released independent of the car. Cleared to build to the converged
`futon3c/holes/campaigns/grounded-learning-spec.md`. **Critical-path reminder (my survivorship finding):** the
discriminating grounded A/B is gated not on a code change but on **failure-recording** — `closure-folds.edn`
is currently success-only (5/0), so β can't grow until attempted-but-unclosed used-sets are written; the
`cascade_learn` `success:false` fix (claude-3) is a no-op until that recording exists. Until then the grounded
A/B is correct but uniform (demonstrated, above).
