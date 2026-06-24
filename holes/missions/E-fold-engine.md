# E-fold-engine — build the fold engine by folding its own cascade

**Status:** DERIVE (stage-1 cascade executed 2026-06-22). **2026-06-24: the fold is now the live Car-3
`:apply-cascade` executor** — `fold_engine.clj` gained an `apply` CLI entry (arbitrary cascade → wiring +
policy-holes), wired consent-gated into `futon3c .../war_machine_pilot_backend.clj/apply-cascade!`
(M-wm-policies Car-3 Part-B (a); read-only / no `:7071` write). The remaining stage-3 generality = build (b),
the NL→rule extraction so the rule-table covers arbitrary cascades (today ~10 patterns; the rest → policy-holes).
**Owner:** claude-2 (E-prefix excursion, owned end-to-end)
**Parents:**
- M-value-creation-loop (`futon7/holes/missions/`) — the keystone + the Ck2 hand-fold this automates
- M-wm-policies (`futon2/holes/`) — the *dynamics* layer this fills (datatype→**dynamics**→selection)
- M-memes-arrows (`futon3a`) — the `(have,want)` three-state arrow machinery
**Code home:** `futon3a/holes/labs/M-memes-arrows/` (next to `cascade_construct.py` + `worked-examples/`)

## Why

M-value-creation-loop Ck1 isolated the **fold engine** as the one unbuilt piece — "two
builds after the keystone": (a) a **rule-selector** (pattern `THEN`-clauses → rewrite
rules matched to a `have→want` topology), and (b) a **fold engine** (apply rules →
fixpoint → wiring diagram + surfaced policy-holes). Ck2 then **hand-folded** kit-cadence
and recovered the known-good design — proving the fold is feasible by hand. This
excursion builds the engine that does it automatically.

## The conceit — apply it to itself

The gap *"we have a hand-fold but no fold engine"* is **itself a `(have→want)` meme**. So
we build the engine **by walking the exact lifecycle we want to evidence** — meme →
cascade → sorry → wiring — on this very gap. Each stage instantiates a **real typed data
object** and a **real transition** (`:correlated → :open → :constructed`), not a drawing.
When the engine exists it **folds its own specifying cascade** and must recover its own
design. The construction is its own evidence; the self-application is the acceptance test.

## The walk — the parts we evidence

| stage | data object (real) | what executes | arrow state |
|---|---|---|---|
| 1 meme + cascade | the **`(have→want)` meme** (the magnet) + its cascade (correlation halo) | `cascade_construct.py` | `:correlated` |
| 2 sorry | the meme **grounded as an identifiable typed hole in substrate-2** (`want` typed, method absent) | substrate-2 identify (`:7071`); `:correlated→:open` **grounding** transition | `:open` |
| 3 wiring | the fold engine (the realizer) + ArSE witness + substrate-2 edge | Codex builds the spec, claude-2 reviews (author≠reviewer); `fill.py kind=discharge`; `promote!` `:open→:constructed` | `:constructed` |
| 4 self-application | the engine's output on two cascades | run the engine | — |

### Stage 1 — meme + cascade (`:correlated`) — DONE (executed 2026-06-22)

- **have** = the hand-fold (manual cascade→wiring judgment).
- **want** = an executable fold engine (cascade → wiring automatically).
- ψ-query: *"fold engine rewrite rule selector pattern THEN clause obligation wiring
  diagram cascade to construction hand fold automate"*
- `cascade_construct.py` → **size 10, C=3.063**:
  1. `devmap-coherence/prototype-alignment-bridge`
  2. `devmap-coherence/devmap-scope-discipline`
  3. `devmap-coherence/prototype-structure-checklist`
  4. `devmap-coherence/prototype-alignment-role`
  5. `devmap-coherence/prototype-alignment-tension`
  6. `devmap-coherence/next-steps-to-done`
  7. `math-informal/parametric-tension-dissolution`
  8. `math-strategy/constraint-tension-resolution`
  9. `math-formalization/tactic-algebra-interference`
  10. `math-strategy/route-exploration-and-pivot`

  **Coherence check:** this cascade shares the `prototype-*` family with the kit-cadence
  cascade (Ck2: `prototype-structure-checklist`, `prototype-alignment-tension`,
  `prototype-alignment-role`). Same fold logic applies → kit-cadence is a built-in
  ground-truth yardstick.

### Stage 2 — sorry (`:open`) — identify the typed hole in substrate-2 (NEXT)

**Correction (Joe, 2026-06-22):** a sorry is **not** the `(have→want)` arrow — that is the
*meme*, already in hand (you cannot produce a cascade without the magnet, so Stage 1 already
carried it). A **sorry is an identifiable typed hole in substrate-2**: the meme *grounded*
against a real typed gap in the running stack (`:7071`). The `meme → sorry` transition
(`:correlated → :open`) is the **grounding / ARGUE gate** — the cascade gives a *proposal*;
substrate-2 binding is what makes it a *hole*.

- the **meme** `(have→want)` (carried from Stage 1): have = { `cascade_construct.py` ·
  `THEN`-clauses (prose) · the Ck2 hand-fold }; want = { an executable fold engine emitting
  `(wiring, policy-holes)` }.
- the **sorry** = that `want` identified as a **typed hole in substrate-2** — the actual
  `code/v05/sorry`-shaped gap, type fixed, method absent. This grounded `:open` hole is the
  build spec / the Codex handoff.

### Stage 3 — wiring (`:constructed`) — the engine

The fold-reading of the engine's **own** cascade gives the engine's **own design**
(self-application, previewed):
- `prototype-structure-checklist` → pure functions before bang-verbs (selector · match ·
  fold-step · emit-wiring).
- `prototype-alignment-tension` → keep selector ⟂ fold; keep correlation(cascade) ⟂
  construction(wiring) — don't let one launder the other.
- `prototype-alignment-role` → roles: selector = rule-match; fold = rewrite-to-fixpoint;
  emit = wiring + policy-holes.
- `next-steps-to-done` → coverage/fixpoint termination.
- `*-tension-*` / `route-exploration-and-pivot` (math lane) → the fold resolves
  topology tensions; pivot when a rule can't fire.

Witness with `fill.py` (`kind=discharge`); `promote!` `:open→:constructed` into
substrate-2 (`:7071`).

### Stage 4 — self-application (the acceptance test)

Run the finished engine on:
1. the **kit-cadence** cascade → must recover Ck2's ground-truth wiring (the yardstick).
2. its **own** cascade → must recover the engine's own design (the self-application closes).

## Acceptance

- engine folds kit-cadence cascade → recovers Ck2 design (the pure functions, the
  `:unsampled→:sampled-unpriced→:priced` state machine, the anti-laundering split);
- engine folds its own cascade → recovers its own design;
- the arrow is witnessed end-to-end: ArSE fill + substrate-2 `:constructed`;
- policy-holes surfaced **honestly** — the things the fold cannot derive (numeric
  thresholds, calendar choices — Ck2's misses) come out as named holes, not silent gaps.

## Honest seams

- **`THEN`-clauses are prose today** (Ck1). v1 may hand-encode the `THEN`→rule step for
  the ~10 cascade patterns; full NL→rule extraction is a later build — flagged, not hidden.
- **C is topicality, not foldability** (Ck1 caveat): a trivial hole scored C=9.79, a
  clean-folding one 1.84. The engine must **not** lean on C for build-feasibility.
- **The fold itself is hand-validated against Ck2**, not against a corpus — first walk,
  one yardstick. Generalization is later.
