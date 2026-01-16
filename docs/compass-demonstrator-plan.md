# Compass Demonstrator Plan

## Vision

Build a small end-to-end demonstrator connecting futon3a (patterns) and futon5 (exotic programming) using Active Inference / Generalized Free Energy concepts.

The "compass" metaphor: output should show alignment signals (direction, deviation, uncertainty reduction) between current evidence and preferred future observations - not pretend to "compute meaning."

## Futon Theory Background

- Treat the near-future as a virtual attractor shaping present action
- "Futon" = structured description of preferred future observations (navigational reference)
- Not a fixed command or moral verdict - a testable, traceable target

## Architecture

```
┌─────────────────┐     ┌──────────────────┐     ┌─────────────────┐
│  Input Narrative │────▶│ Pattern Retrieval │────▶│ Preference Model │
│  (log, PAR, etc) │     │    (futon3a)      │     │   Extraction     │
└─────────────────┘     └──────────────────┘     └────────┬────────┘
                                                          │
                                                          ▼
┌─────────────────┐     ┌──────────────────┐     ┌─────────────────┐
│  Compass Output  │◀────│   GFE Scoring    │◀────│ Policy Simulator │
│  (audit trail)   │     │   (alignment)    │     │   (exotype-style)│
└─────────────────┘     └──────────────────┘     └─────────────────┘
```

## Step 1: Pattern Retrieval (futon3a)

**Input**: Short narrative (log snippet, PAR, commit message)

**Process**:
- Tokenize narrative
- Score patterns by keyword overlap (existing `musn.core/futon1-candidates` approach)
- Or use meme layer similarity once ANN is wired

**Output**: 2-5 relevant patterns with their flexiarg fields

**Codex note**: Codex has been working on relevant changes here - sync before implementing.

## Step 2: Preference Model Extraction

**Input**: Retrieved patterns (flexiarg format)

**Mapping**:
```clojure
{:desired      ; from THEN + NEXT-STEPS
 :obstacles    ; from IF (problem state) + HOWEVER (complications)
 :evidence     ; from BECAUSE->EVIDENCE references
 :uncertainty  ; inverse of pattern confidence/match score
}
```

**Key insight**: Patterns encode preferred futures implicitly:
- THEN = what should happen
- NEXT-STEPS = concrete actions toward that future
- HOWEVER = what could go wrong (deviation to avoid)

## Step 3: Policy Simulator (futon5-style)

**Approach**: Tiny exotype-inspired kernel

- Define "state" as a simple map (current observations + beliefs)
- Define "policies" as state transitions (candidate actions)
- Use mutation/mixing to generate variations
- Run a few steps, observe outcomes

**Minimal version**:
```clojure
(defn simulate-policy [state policy steps]
  (reduce (fn [s _] (apply-policy s policy)) state (range steps)))
```

**Connection to futon5**: Can later use actual MMCA sigil dynamics as the simulator substrate.

## Step 4: GFE-Inspired Scoring

**Generalized Free Energy** (simplified):
```
G = epistemic_value + pragmatic_value

epistemic  = how much does this policy reduce uncertainty about preferences?
pragmatic  = how well does expected outcome align with desired future?
```

**Implementation**:
```clojure
(defn score-policy [preference-model policy simulated-outcome]
  (let [;; Pragmatic: overlap between outcome and desired observations
        pragmatic (alignment simulated-outcome (:desired preference-model))

        ;; Epistemic: does this policy help us learn about obstacles?
        epistemic (information-gain simulated-outcome (:obstacles preference-model))

        ;; Combine (lower G = better, like AIF)
        G (- (+ (* 0.7 pragmatic) (* 0.3 epistemic)))]
    {:G G
     :pragmatic pragmatic
     :epistemic epistemic}))
```

**Assumptions to make explicit**:
- Alignment is keyword/concept overlap (can upgrade to embeddings later)
- Information gain approximated by obstacle coverage
- Weights (0.7/0.3) are tunable

## Step 5: Compass Output

**Format** (EDN/JSON):
```clojure
{:narrative "input text..."
 :patterns-retrieved [{:id "sidecar/typed-kolmogorov-arrows"
                       :match-score 0.7
                       :then "Store arrows with mode, payload..."
                       :next-steps [...]}
                      ...]
 :preference-model {:desired [...]
                    :obstacles [...]
                    :evidence [...]}
 :candidate-policies [{:id :policy-a
                       :description "..."
                       :simulated-outcome {...}
                       :score {:G -0.42 :pragmatic 0.6 :epistemic 0.3}}
                      ...]
 :recommendation {:best-policy :policy-a
                  :confidence 0.65
                  :next-evidence ["Run test X to reduce uncertainty about Y"
                                  "Check if obstacle Z has been addressed"]}
 :compass {:direction :aligned  ; or :drifting, :blocked
           :deviation-signals [...]
           :uncertainty-sources [...]}}
```

## Constraints

- Minimal dependencies (just what's in futon3a already)
- Transparent data structures (EDN)
- Small scripts, runnable locally
- Proof trail: cite files, commands, reasoning

## Deliverables

1. `src/futon/compass.clj` - Core namespace
2. `src/futon/flexiarg.clj` - Pattern parser
3. `scripts/compass-demo` - CLI runner
4. `resources/compass/sample-inputs.edn` - Test narratives
5. `docs/compass-demo-readme.md` - Usage instructions

## Open Questions

- How to handle patterns without explicit THEN/NEXT-STEPS?
- Should policy simulation use actual futon5 MMCA or stay abstract?
- How to calibrate GFE weights?
- Integration with meme layer proposals?

## Dependencies on Other Work

- **Codex changes for step 2**: Pattern retrieval improvements (sync first)
- **futon5 adapters**: For richer policy simulation
- **ANN layer**: For semantic pattern matching (optional upgrade)
