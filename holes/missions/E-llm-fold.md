# E-llm-fold — the cascade fold is an LLM turn (toy: NL patterns → a fugue)

**Date:** 2026-06-24 · **Owner:** claude-2 (E-prefix excursion) · **Parents:** E-fold-engine, M-wm-policies
(Car-3 Part-B). **Artifacts:** `futon3a/holes/labs/llm-fold/` — `music-cascade.flexiarg` (the NL cascade),
`compose_fugue.py` (the fold's output), `fugue-c-major.{mid,mp3}` (listen).

## The breakthrough (Joe, 2026-06-24)

The function a fold needs — `f(IF, HOWEVER, circumstance) → construction` — **cannot be compiled a priori**
(you can't read it off the `THEN`-clause: "learning it from `THEN` alone would be like learning
`(fn [a b c d e f] x)` from `x` alone"), **and cannot be learned without a dataset** (the Daisyworld lesson:
the rule was *evolved from outcomes*, not parsed). **But an LLM turn just evaluates it.** The LLM grounds the
prose `IF`/`HOWEVER` against the circumstance AND emits the contribution, in one turn — natively. So:

- `fold_engine.clj` (deterministic rule-table, E-fold-engine) is the **special case** — works only where the
  patterns are pre-compiled. It is really a verifier / degenerate fold.
- The **general** `:apply-cascade` executor is an **LLM-turn fold**: (NL cascade + circumstance) → an inhabiting
  agent → (construction + wiring + policy-holes). This is why "apply-cascade = agent-in-the-loop" kept
  surfacing — the inhabiting agent *is* the engine. It needs **no** rule-table, NL→rule parse, or
  outcome-dataset for the basic capability.

This retires build (b)'s "learn the rule-table" program in favour of "the fold is an LLM turn."

## The toy — proof on a domain OUTSIDE the stack

Music theory has crisp right/wrong and is nowhere near the futon stack, so it's a fair test. **Cascade** =
7 NL patterns (`music-cascade.flexiarg`): `fugue-subject · subject-answer · countersubject ·
exposition-entries · episode-by-sequence · voice-leading · authentic-cadence`. **Circumstance** = "compose a
3-voice fugue exposition in C major." **The fold** = an LLM turn (claude-2) read the patterns + circumstance and
emitted the construction (`compose_fugue.py` → `fugue-c-major.mid`).

### Listen
`futon3a/holes/labs/llm-fold/fugue-c-major.mp3` (24s). (Regenerate: `python3 compose_fugue.py` →
`fluidsynth -F out.wav FluidR3_GM.sf2 fugue-c-major.mid`.)

### WIRING — which pattern landed where (the construction's provenance)
| pattern | where it fired |
|---|---|
| `fugue-subject` | bars 1–2: Soprano states the subject in C (tonic), alone |
| `subject-answer` | bars 3–4: Alto enters with the answer in G (dominant) |
| `countersubject` | bars 3–4: Soprano's line against the answer |
| `exposition-entries` | bars 5–6: Bass enters with the subject in C (the 3rd voice → all entered) |
| `voice-leading` | throughout: triads on strong beats, parallel 3rds S–A, LT→tonic / 7th→3rd at the cadence |
| `authentic-cadence` | bars 7–8: V (G) → I (C), leading-tone B→C, dominant-7th F→E |

### UNFIRED pattern (surfaced honestly, not silently dropped — the fold's coverage discipline)
- `episode-by-sequence` — **did not fire**: this is a *tight* exposition (entries back-to-back), so no episode
  was spun. Reported, not hidden — exactly the policy-hole discipline (a pattern available but not applicable
  to this circumstance).

### POLICY-HOLES — what the patterns left FREE (the construction's free content)
The patterns fix **structure** (subject in tonic, answer in dominant, V→I close, no parallels); they never fix
**content**. So these were free choices of the fold, not derived from any pattern:
- the actual subject melody (`C D E G F E D`);
- the specific countersubject line and the inner voices' notes;
- the answer rendered as a **real** answer (exact 5th up) — a strict **tonal** answer (adjust degree 5→1) is
  the orthodox refinement, left open;
- the countersubject rendered as a 3rd-companion — strict **invertible** counterpoint is left open.
These are the music's "free will": the place the structure stops and taste begins — and the part only the EAR
(not the patterns) can judge.

## Second data point — 12-bar blues (the A/B control, Joe 2026-06-24)

To be moderately scientific: a *different* genre, its *own* 7 patterns, same mechanism. **Cascade** =
`blues-cascade.flexiarg` (`twelve-bar-form · dominant-sevenths · quick-change · blues-scale-melody ·
call-and-response · shuffle-feel · turnaround`). **Circumstance** = "compose one 12-bar blues chorus in A."
**Fold** = an LLM turn → `compose_blues.py` → `blues-A.mid` / **`blues-A.mp3`** (~34s; tenor-sax head, e-piano
comp, boogie bass, shuffle drums).

WIRING — every pattern fired (vs the fugue, where 1 didn't):
| pattern | where |
|---|---|
| `twelve-bar-form` | the I-IV-I-I / IV-IV-I-I / V-IV-I-V skeleton (all 12 bars) |
| `dominant-sevenths` | I7/IV7/V7 = A7/D7/E7 (comp voicings + the b7 in the boogie bass) |
| `quick-change` | bar 2 = IV (D7) before returning to I |
| `blues-scale-melody` | the head from the A blues scale (A C D Eb E G), blue notes b3/b5/b7 |
| `call-and-response` | AAB: call (bars 1–4), same call (5–8 over IV), answer B (9–12); air between calls |
| `shuffle-feel` | swung-8th triplet feel in melody, bass, and ride |
| `turnaround` | bars 11–12 descent (D C# C B) landing on V to loop the chorus |

POLICY-HOLES (free content the patterns don't fix): the actual licks, the boogie bassline choice, the comp
rhythm, the specific turnaround line, the AAB melodic shape. Structure fixed; content free.

**A/B significance:** two genres, two disjoint NL cascades, ONE mechanism (an LLM turn) — both produced
structurally-correct, ear-judgeable constructions with honest wiring + holes. If both pass the ear test, that's
two independent points for "the cascade fold is an LLM turn," not one lucky fugue.

## Scaling test — all 14 patterns at once (Joe 2026-06-24)

Hand the LLM turn the UNION of both cascades (7 fugue + 7 blues = 14) → does it integrate or hit a complexity
knee? Fold → a **blues fugue** (`compose_bluesfugue.py` → **`bluesfugue-A.mp3`**, 16 bars in A: a bluesy subject
treated fugally over a 12-bar form). **All 14 fired** (none dropped). The real finding is the **cross-pattern
TENSIONS** the fold had to reconcile — bigger heterogeneous cascade ⇒ more reconciliation-work, not just
concatenation:

| tension (pattern × pattern) | how the fold resolved it |
|---|---|
| `authentic-cadence` (close on V→I) **vs** `turnaround` (loop via V) — compete for the ending | **structural placement:** turnaround at the chorus loop (bars 11–12), authentic cadence at the final coda (bars 15–16). Both fire, different slots. |
| `subject-answer` (answer in the **dominant**) **vs** blues `call-and-response` AAB (2nd statement over **IV**) | **genuine synthesis:** the answer enters up a 4th → lands toward D = IV, satisfying *both* readings at once (not a compromise). |
| `invertible-counterpoint` (dense, swappable) **vs** `call-and-response`/space (sparse, air) | **traded toward blues:** sparse counterpoint with rests → strict invertibility **dropped → policy-hole**. |
| functional `voice-leading`/cadence **vs** `dominant-sevenths` everywhere (non-functional b7s) | **aligned at the cadence:** E7→A is *both* a dom7 (blues) and V→I (fugue); pervasive b7s sit under the body. |
| `exposition-entries` (each voice states subject) **vs** `twelve-bar-form` (fixed 12 bars) | **packed:** 3 entries into bars 1–6 of the form (S over I, A over the quick-IV, B over IV). |

**Scaling finding:** the fold did **integration, not concatenation** — it reconciled ~5 genuine inter-pattern
tensions (mostly by *structural placement* + one true *synthesis*: answer-up-a-4th serving both fugal-dominant
and blues-IV) and surfaced the irreconcilable one (invertibility) as a policy-hole. This is exactly what "a
cascade is a semilattice scored by wholeness/coherence" predicts: at scale the patterns *overlap and conflict*,
and the fold's job is to make them cohere. Whether 14 still *sounds* unified (vs the "any more, too complicated"
knee) is Joe's ear-test — the scaling acceptance.

## The connective grammar — futon5's CT starter kit in the mix (Joe 2026-06-24)

The 14-pattern blues-fugue was an *ad-hoc* integration. The dark-tower CT kit names the missing layer: the
**BV connectives** (`⊗ ⅋ ◁ × ⊕`) — *how* cascades compose (the iiching gives the types; BV gives the
connectives; the connective layer is the explicitly-skipped rung, dark-tower-2 §6/§10). So: same two cascades
(blues, fugue), **only the connective varies** — three audibly different pieces (`compose_connectives.py`, all in A):

| connective | meaning | render |
|---|---|---|
| **`◁`** one-way sequential | "begins as blues, **ends as fugue**" — a 12-bar blues hands off **one-way** to a fugue whose **subject IS the blues head** (signal flows blues→fugue: the fugue inherits the theme; the blues never reacts back). Texture morphs shuffle→sustained, drums drop. | `connective-seq.mp3` |
| **`⅋`** coupled (fully-signalling) | the **blend** — the two reconciled into one fused thing (answer lands on IV serving both, voice-leading reconciled). | `bluesfugue-A.mp3` |
| **`⊗`** non-signalling parallel | two **independent** layers in ONE shared frame (key A, tempo, bars), each composed from its OWN cascade with **no reference to the other**. | `connective-par.mp3` |

**On `⊗` (the subtle one):** it is **not** simultaneous replay of two finished tracks (different keys → clash,
proves nothing). It is non-signalling in the Caus[−] sense: the layers **share the ambient frame** (the *type*:
key A, tempo, meter) but **never coordinate** — the fugue voices do not voice-lead to, or avoid, the blues
chords. The operational **non-signalling test**: *delete either layer and the other is still self-consistent*
(each is independently well-formed) — which is exactly how it's built (the blues layer = blues cascade alone;
the fugue layer = fugue cascade alone). You hear **two parallel streams, occasionally rubbing** — the honest
*cost* of non-signalling — vs `⅋` where the rubbing is *resolved away* into one texture. That contrast (rub vs
fuse vs hand-off) is the connectives made audible.

This brings futon5's CT starter kit into the proven LLM-fold at its one missing place (the connective layer),
and is the seed of the telos: **HEAD → impl-plan in one turn** needs exactly this — an implementation plan is
patterns composed with `◁`/`⊗`/`×` (a BV process-expression), folded — not a bag of patterns.

## Acceptance
If it sounds like a fugue and sounds good (Joe's ear), the LLM-fold is real and general: NL patterns + one LLM
turn → a coherent construction, with honest wiring + holes, in a domain with no home-field advantage. That
validates the `:apply-cascade` = LLM-turn executor for the real stack (the inhabiting agent folds a mission's
cascade the same way), and supersedes the rule-table generalization (b).

## Next direction — embed wiring diagrams (the third leg) + DarkTower-flow (Joe, 2026-06-24)

We have a **joint pattern+mission embedding** (MiniLM). The next leg = **embed wiring diagrams** (the DarkTower
generalisations — the 2-morphism / curvature level) into the *same* space, and **reconstruct them for historical
missions** (each mission's ARGUE *is* a cascade → fold → its wiring; git + docs are the under-mined source —
[[project_retrospective_reconstruction]]). Then the loop Joe sketched: **grab related patterns → see where they
sit in the embedding → read them as a cascade → DarkTower-flow them into a wiring diagram.**

**The honest hard part — how do you *embed* a wiring diagram?** It's a structured object (boxes/wires/terminals),
not text. The DarkTower-native answer: a wiring's position = **a function of its patterns' embeddings under the
connective grammar** — the wiring lives in the same space as its constituent patterns, located by *which
patterns* + *how they compose* (`◁/⅋/⊗`). So embedding-a-wiring = **the fold run in embedding-space** — which is
exactly where **JAX would pay off** (the fold-in-embedding-space as a fast differentiable map; and the
*inverse* fold = "given a target region, what cascade+connectives flow there?"). Cheaper alternatives:
serialize-and-MiniLM (lossy) or a structural/graph embedding (richer, more work); the patterns-under-connectives
form is the one that ties wiring-embedding to the fold *and* the tower.

**First concrete step:** reconstruct + embed wirings for a handful of historical missions (fold each mission's
cited patterns → wiring → embed), then test the navigate→cascade→flow loop on real data.
