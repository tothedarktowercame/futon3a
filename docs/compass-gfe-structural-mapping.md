# Compass GFE Structural Mapping (Draft)

This document maps compass concepts to the *structure* of Generalized Free
Energy models, not just the terminology. The goal is to avoid collapsing
Friston's framework into arbitrary weight tuning.

## Caution

We are currently using "GFE-inspired" loosely. This document attempts to
clarify what a proper structural mapping would require, and what we're
actually doing vs. what we're approximating.

Status: **Aspirational with honest gaps**.

---

## GFE Core Structure (Friston)

The Generalized Free Energy framework has specific structural components:

### 1. Generative Model: p(o, s) = p(o|s)p(s)

A probabilistic model of how observations (o) arise from hidden states (s).

- **p(s)** — prior beliefs about states
- **p(o|s)** — likelihood of observations given states

### 2. Variational Density: q(s)

An approximate posterior over hidden states, updated to minimize free energy.

### 3. Free Energy: F

```
F = E_q[log q(s) - log p(o,s)]
  = KL[q(s) || p(s|o)] - log p(o)    (up to constant)
```

F bounds surprisal (negative log evidence). Minimizing F approximates
Bayesian inference.

### 4. Expected Free Energy: G

For policy selection under active inference:

```
G(π) = E_q[log q(s|π) - log p(o,s|π)]
```

Decomposes into:
- **Risk/Pragmatic**: distance from preferred outcomes p(o|C)
- **Ambiguity/Epistemic**: expected uncertainty about state-observation mapping

### 5. Precision: τ

Inverse variance of prediction errors. High precision → confident predictions,
low precision → uncertain/flexible. Precision weighting controls how different
signals contribute to inference.

### 6. Active Inference

Action selection minimizes expected free energy:

```
π* = argmin_π G(π)
```

---

## Compass Terminal Vocabulary

### Observations (per compass run)

What the compass "senses" from narrative + patterns:

| Terminal | Type | Description |
|----------|------|-------------|
| :narrative | string | Input narrative text |
| :retrieved-patterns | vec | Patterns retrieved with scores |
| :pattern-scores | vec[float] | Retrieval scores per pattern |
| :scope-conditions | vec[string] | IF fields from patterns |
| :risks | vec[string] | HOWEVER fields from patterns |
| :desired-outcomes | vec[string] | THEN + NEXT-STEPS from patterns |
| :concepts | set[string] | Tokenized key concepts |

### Internal State (latent/derived)

| Terminal | Type | Description |
|----------|------|-------------|
| :preference-model | map | Extracted {scope, risks, desired, concepts} |
| :simulation-state | map | {concepts, unacknowledged-risks, risks-acknowledged} |
| :energy-history | vec[keyword] | Sequence of energies applied |
| :current-policy | keyword | Assumed current policy (if tracked) |

### Action Terminals (policy outputs)

| Terminal | Type | Description |
|----------|------|-------------|
| :recommended-policy | keyword | :exploit, :explore, :balanced |
| :policy-transition | keyword | :escalation, :de-escalation, :maintenance |
| :next-evidence | vec[string] | Suggested evidence to collect |
| :energy-applied | keyword | Which 八勁 energy was used in mutation |

### Outcome/Utility Signals

| Terminal | Type | Description |
|----------|------|-------------|
| :G | float | Free energy score (lower is better) |
| :pragmatic-signal | float | Concept overlap with desired outcomes |
| :epistemic-signal | float | Risk awareness fraction |
| :direction | keyword | :aligned, :progressing, :drifting, :blocked |
| :confidence | float | 1 - |G|, rough confidence measure |

---

## Structural Mapping Attempt

### What is the Generative Model?

**Candidate mapping:**

```
p(patterns | narrative) — likelihood of patterns given narrative
p(narrative) — prior over narratives (implicit in retrieval)
```

But this is weak. We don't have an explicit probabilistic model of how
narratives generate pattern retrievals. The embedding similarity is a proxy,
not a proper generative model.

**Honest assessment:** We're doing retrieval + heuristic scoring, not
generative modeling. The "generative model" is implicit in the embedding
space and pattern library.

### What are the Hidden States?

**Candidate mapping:**

- The agent's "true" situation (what patterns actually apply)
- The agent's goal state (what outcomes are actually desired)
- The environment's state (what risks are actually present)

**Current approximation:** We treat retrieved patterns as observations of
the hidden "relevance" state. The preference model is our variational
approximation of what matters.

### What is Precision?

**Candidate mapping:**

Precision should control how much we weight different signals:
- High precision on pragmatic → exploitation-heavy
- High precision on epistemic → exploration-heavy
- Balanced precision → balanced policy

**Current state:** We have fixed weights (0.6/0.4). This is NOT precision
in the GFE sense—it's just a weighted sum.

**What precision SHOULD be:**
- Learned or adapted based on prediction error history
- Narrative-dependent (some narratives warrant high pragmatic precision)
- Energy-dependent (Lǚ energy → lower pragmatic precision, yield to uncertainty)

### Eight Energies and Precision

The eight-gate energies could map to precision modulation:

| Energy | Precision Effect | Rationale |
|--------|-----------------|-----------|
| Péng (ward off) | Balanced | Maintain current precision, expand within it |
| Lǚ (roll back) | Lower pragmatic | Yield to uncertainty, increase epistemic weight |
| Jǐ (press) | Higher pragmatic | Focus on single outcome, reduce ambiguity |
| Àn (push) | High pragmatic | Full commitment to goals |
| Cǎi (pluck) | Ground both | Connect both signals to evidence |
| Liè (split) | Separate | Different precision for different sub-problems |
| Zhǒu (elbow) | Local adjustment | Small precision change |
| Kào (lean) | Structural | Major precision regime shift |

**This is speculative.** We haven't implemented energy-based precision.

### G Decomposition

**Friston's decomposition:**
```
G = risk + ambiguity
  = pragmatic_value + epistemic_value
```

**Our current formula:**
```
G = -(0.6 * pragmatic + 0.4 * epistemic)
```

**Structural issues:**
1. The weights are arbitrary, not derived from precision
2. We're not computing "expected" free energy over futures
3. Our simulation is random mutations, not policy-conditioned prediction

**What proper GFE would require:**
1. A generative model that predicts future observations under each policy
2. Precision-weighted prediction errors
3. Epistemic value as expected information gain, not just "risks acknowledged"

---

## Gap Analysis

### What we're doing well

1. **Decomposition intuition**: Pragmatic + epistemic is structurally correct
2. **Policy space**: Exploit/explore/balanced maps to risk/ambiguity tradeoff
3. **Simulation**: Running forward simulation to evaluate policies is right idea
4. **Eight energies**: Energy modes as precision/action modulation is promising

### What we're approximating badly

1. **No generative model**: We're retrieving, not generating
2. **Fixed weights**: Not precision-weighted, not adaptive
3. **Epistemic value**: Counting acknowledged risks ≠ expected information gain
4. **Simulation dynamics**: Random mutations ≠ policy-conditioned prediction

### What we're missing entirely

1. **Prediction error**: No explicit prediction → observation → error cycle
2. **Belief updating**: No variational inference, just retrieval
3. **Precision learning**: Weights are fixed, not learned from prediction errors
4. **Temporal depth**: We simulate N steps but don't model temporal dependencies

---

## Proposed Path Forward

### Option A: Honest Heuristic

Keep the current approach but rename it:
- "Compass heuristic scoring" not "GFE"
- Acknowledge it's inspired-by, not implementing
- Focus on practical utility, not theoretical correctness

### Option B: Minimal GFE Structure

Add the minimal structural elements:

1. **Explicit prediction**: Before observing patterns, predict what patterns
   SHOULD be retrieved given the preference model
2. **Prediction error**: Compare predicted vs. actual retrieval
3. **Precision from error**: High prediction error → lower precision on that signal
4. **Update loop**: Multiple rounds of predict → observe → update

### Option C: Full Active Inference

Implement proper active inference:

1. Generative model of narrative → pattern → outcome
2. Variational inference over hidden states
3. Policy selection by expected free energy minimization
4. Precision learning from prediction error history

This is a significant undertaking.

### Recommended: Option B (Minimal Structure) — IMPLEMENTED

Add prediction error and precision modulation without full generative modeling.

**Implementation:** `src/futon/gfe.clj` and `src/futon/compass_gfe.clj`

```clojure
;; Protocols define the structure (invariant across levels)
(defprotocol IGenerativeModel
  (predict-observations [this state])
  (observation-likelihood [this observation state]))

(defprotocol IPrecision
  (get-precision [this signal-type])
  (update-precision! [this signal-type error])
  (precision-weights [this]))

;; Level 0: Fixed precision (current behavior)
(gfe/fixed-precision 0.6 0.4)

;; Level 1: Adaptive precision
(gfe/adaptive-precision :learning-rate 0.1)

;; Usage
(compass-gfe/compass-report-gfe narrative :level 1)
```

The gfe-cycle structure (predict → observe → error → update → evaluate)
is preserved across implementation levels. Only the protocol implementations
change during upgrades.

---

## Terminal Hooks for GFE Wiring (Draft)

If we move toward proper GFE structure, these would be the wiring points:

### GENERATIVE hooks
- :pattern-prior — p(pattern | preference-model)
- :outcome-likelihood — p(outcome | pattern, policy)
- :narrative-prior — p(narrative) (could be uniform or context-dependent)

### INFERENCE hooks
- :variational-params — parameters of q(s)
- :free-energy — computed F
- :prediction-error — observed - predicted

### PRECISION hooks
- :pragmatic-precision — τ for pragmatic signal
- :epistemic-precision — τ for epistemic signal
- :energy-precision — precision modulation by 八勁

### POLICY hooks
- :expected-free-energy — G(π) for each policy
- :policy-distribution — softmax over -G
- :selected-policy — argmin G

---

## Open Questions

1. What is the right generative model for narrative → pattern retrieval?
   (Embedding space? Topic model? Something else?)

2. Should precision be per-pattern or per-signal-type?

3. How do the eight energies interact with precision learning?
   (Are they precision priors? Precision modulators? Something else?)

4. What temporal structure matters?
   (Single-shot vs. multi-turn dialogue vs. session-level?)

5. Can we learn precision from MUSN session logs?
   (Historical prediction errors → precision priors?)

---

## References

- Friston, K. (2010). The free-energy principle: a unified brain theory?
- Parr, T., & Friston, K. (2019). Generalised free energy and active inference.
- Da Costa, L., et al. (2020). Active inference on discrete state-spaces.
- Futon5 metaca-terminal-vocabulary.md
- Futon5 cyberants-terminal-vocabulary.md
