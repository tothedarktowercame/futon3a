# Compass-Exotype Bridge Architecture

Mission 7: Connect futon3a compass to futon5 MMCA exotype model.

## Conceptual Mapping

The compass operates at a **narrative/preference** level while MMCA operates at a **cellular automaton** level. The bridge translates between these abstractions.

### 1. Compass Policy ↔ Exotype Regime

| Compass Policy | MMCA Regime | Dynamics |
|---------------|-------------|----------|
| **exploit** | :static | High structure preservation, low change-rate |
| **explore** | :eoc (edge-of-chaos) | Moderate entropy, balanced activity |
| **balanced** | :eoc with constraints | Adaptive between regimes |

### 2. Eight Energies ↔ Exotype Parameters

The eight energies (八勁) map to exotype lift parameters:

| Energy | Exotype Parameter | Effect |
|--------|-------------------|--------|
| Péng (ward) | `:update-prob 1.0` | High mutation, expansion |
| Lǚ (yield) | `:update-prob 0.25` | Conservative, yielding |
| Jǐ (press) | `:match-threshold 0.8` | Focused, selective |
| Àn (push) | `:mix-mode :rotate-left` | Forward momentum |
| Cǎi (pluck) | `:invert-on-phenotype? true` | Grounded in feedback |
| Liè (split) | `:mix-mode :swap-halves` | Separation, trade-offs |
| Zhǒu (elbow) | `:match-threshold 0.4` | Small adjustments |
| Kào (lean) | `:mix-mode :majority` | Consolidation |

### 3. Pattern Sigil ↔ CA Rule (0-255)

Option implemented: **Hash-based mapping**

```clojure
(defn pattern-id->rule [pattern-id]
  (mod (hash pattern-id) 256))
```

This ensures:
- Deterministic: same pattern → same rule
- Distributed: patterns spread across rule space
- Reversible: can track which patterns contributed

### 4. Preference Model ↔ Fitness Landscape

| Preference Field | MMCA Metric | Relationship |
|-----------------|-------------|--------------|
| `:desired` (futures) | Target entropy band | Goals → seek specific regimes |
| `:scope` (preconditions) | Regime constraints | When policy applies |
| `:risks` (failure modes) | Regime avoidance | Avoid :freeze, :magma |
| `:concepts` | Sigil affinities | Which rules to favor |

### 5. GFE Scoring ↔ MMCA Composite Score

| Compass Signal | MMCA Component | Weight |
|---------------|----------------|--------|
| Pragmatic | Coherence score | 0.4 |
| Epistemic | Interestingness score | 0.3 |
| — | Compressibility | 0.15 |
| — | Autocorrelation | 0.15 |

## Architecture Diagram

```
┌────────────────────────────────────────────────────────────────────┐
│                         COMPASS                                     │
│  narrative → patterns → preferences → policies → GFE → direction   │
└─────────────────────────────────┬──────────────────────────────────┘
                                  │
                          ┌───────▼───────┐
                          │    BRIDGE     │
                          │ compass_exo   │
                          └───────┬───────┘
                                  │
      ┌───────────────────────────┼───────────────────────────┐
      ▼                           ▼                           ▼
┌─────────────┐           ┌─────────────┐           ┌─────────────┐
│ policy→exo  │           │ pattern→rule│           │ energy→param│
│             │           │             │           │             │
│ exploit     │           │ hash mod    │           │ peng→prob   │
│   → static  │           │ 256         │           │ lu→0.25     │
│ explore     │           │             │           │ ji→thresh   │
│   → eoc     │           │             │           │ ...         │
│ balanced    │           │             │           │             │
│   → adapt   │           │             │           │             │
└─────────────┘           └─────────────┘           └─────────────┘
                                  │
                          ┌───────▼───────┐
                          │     MMCA      │
                          │   run-mmca    │
                          └───────┬───────┘
                                  │
                          ┌───────▼───────┐
                          │   METRICS     │
                          │ summarize-run │
                          └───────┬───────┘
                                  │
                          ┌───────▼───────┐
                          │  REGIME/SCORE │
                          │ classify + G  │
                          └───────────────┘
```

## Data Flow

1. **Compass Report** → extract patterns, preference model, policy recommendation
2. **Pattern→Rule**: Convert pattern IDs to CA rules (0-255)
3. **Policy→Exotype**: Convert compass policy to exotype spec
4. **Energy→Params**: Modulate exotype params by energy profile
5. **Run MMCA**: Execute simulation with derived configuration
6. **Score**: Use MMCA metrics to validate/refine compass recommendation
7. **Feedback**: MMCA regime classification can inform next compass iteration

## API Design

```clojure
(ns futon.compass-exotype
  (:require [futon.compass :as compass]
            [futon5.mmca.runtime :as mmca]
            [futon5.mmca.exotype :as exotype]
            [futon5.mmca.metrics :as metrics]))

;; Core bridge functions
(defn compass-report->exotype-spec [report]
  ;; Convert compass output to MMCA configuration
  ...)

(defn run-exotype-simulation [spec]
  ;; Execute MMCA simulation with bridge configuration
  ...)

(defn mmca-result->compass-feedback [result]
  ;; Convert MMCA metrics back to compass-interpretable form
  ...)

;; Main entry point
(defn compass-with-exotype [narrative]
  ;; Full integration: compass → MMCA → enhanced compass
  ...)
```

## Constraint: No Foreclosure

Per user requirement, this bridge must not foreclose future upgrades:

1. **Exotype spec is data**: Can be enhanced without code changes
2. **MMCA result is data**: Feedback channel is extensible
3. **Mapping tables are configurable**: Not hardcoded
4. **Protocol-based**: Can swap implementations

## Open Questions

1. Should MMCA results modify compass precision (Option B from GFE)?
2. How many MMCA generations per compass evaluation?
3. Should pattern sigils carry their own CA rule hints?
4. What genotype length is appropriate for preference model simulation?
