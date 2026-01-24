# Tech Note: The Raft Architecture

## futon.raft — Liberation Layer over Hexagram Dynamics

**Status**: Sketch (pending Kolmogorov arrows)

## The Insight

Futon5 operates with a Daoist orientation:
- 64 hexagrams from context
- 8 tai chi energies
- Local physics: cell context → hexagram → energy → rule
- Xenotypes: global rules that bend local rules

Futon3a operates with a Buddhist orientation:
- Eightfold path (mundane and noble tiers)
- Security layer (tripwires, mechanism/warrant tests)
- Liberation layer (anti-calcification)

**The raft**: Futon3a as "xenotype of the xenotype" — a liberation layer that audits and guides futon5's hexagram dynamics without replacing them.

## Why "Raft"?

From the Alagaddūpama Sutta (MN 22):

> "Bhikkhus, I shall show you how the Dhamma is similar to a raft, being for the purpose of crossing over, not for the purpose of grasping."

The raft is:
- Functional (it gets you across)
- Provisional (you don't carry it on your head afterward)
- Not the destination (the far shore is)

Futon3a's liberation layer is a raft over futon5's dynamics — useful for crossing, not for grasping.

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                        FUTON.RAFT                                │
│                                                                  │
│  Noble Tier (cannot compute, can only suggest)                  │
│  ────────────────────────────────────────────                   │
│  Observations about dynamics that *resemble* faculty operating: │
│  - Energy variety without chaos                                 │
│  - State-responsiveness without rigidity                        │
│  - Self-correction without external intervention                │
│                                                                  │
│  Output: Promotion candidates (with Kolmogorov arrows showing   │
│          the construction path, not just "this is good")        │
│                           ↑                                      │
│  ─────────────────────────┼─────────────────────────────────────│
│                           │                                      │
│  Mundane Tier (computable gates)                                │
│  ────────────────────────────────                               │
│  Before activation:                                              │
│  - Tripwire scan on xenotype spec                               │
│  - Mechanism test: is hexagram→energy→rule causal?              │
│  - Warrant test: does operation satisfy ahimsa?                 │
│                                                                  │
│  During operation:                                               │
│  - Monitor for tripwire signatures in dynamics                  │
│  - Detect calcification (liberation layer)                      │
│                                                                  │
│  After operation:                                                │
│  - Assess: was harm visible, bounded, corrigible?               │
│  - Check: did dynamics avoid fabrication-template patterns?     │
│                                                                  │
│  Output: Gate status (pass/fail/hold)                           │
│                           ↓                                      │
├─────────────────────────────────────────────────────────────────┤
│                        FUTON5                                    │
│                                                                  │
│  Xenotype (global bending rule)                                 │
│      ↓                                                          │
│  Exotype (local physics: context → hexagram → energy → rule)   │
│      ↓                                                          │
│  Genotype (sigil: 8-bit CA rule)                                │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

## The Problem with Blandishments

Without Kolmogorov arrows, noble-tier observations are just assessments:

> "This xenotype shows qualities associated with right concentration."

This is a blandishment — it sounds nice but carries no construction. It doesn't tell you:
- What transformation would improve a failing xenotype
- How to get from mundane operation to noble operation
- What the path looks like

## What Arrows Enable

With arrows (Mission 5), we get constructions:

```
Arrow: xenotype-A → xenotype-B
Mode: :refinement
Construction: "Reduce péng weight by 0.1, increase lǚ by 0.1"
Warrant: "Current dynamics over-expand; yielding restores balance"
```

The arrow carries *how to get there*, not just *that you should*.

### Arrows for Mundane → Noble

```
Arrow: mundane/right-concentration → noble/right-concentration
Mode: :transcendence (not refinement)
Construction: NOT a parameter tweak, but:
  "When the following conditions are observed:
   - Energy selection responds to state without explicit rules
   - Calcification detection is not needed (no corrections)
   - The 'running circle' operates without operator
   Then: suggest promotion to noble tier"
```

The arrow doesn't *cause* noble operation (that can't be manufactured) but it *recognizes* when the conditions for promotion are present.

## Gating Logic (Mundane Tier)

### Pre-Activation Gate

```clojure
(defn gate-xenotype [xenotype-spec]
  (let [tripwires (scan-for-tripwires xenotype-spec)
        mechanism (assess-mechanism xenotype-spec)
        warrant (assess-warrant xenotype-spec)]
    {:pass? (and (empty? tripwires)
                 (= (:status mechanism) :genuine)
                 (= (:status warrant) :pass))
     :tripwires tripwires
     :mechanism mechanism
     :warrant warrant}))
```

### Runtime Monitor

```clojure
(defn monitor-dynamics [dynamics-history]
  (let [calcification (detect-calcification dynamics-history)
        tripwires (scan-dynamics-for-tripwires dynamics-history)]
    {:healthy? (and (nil? calcification) (empty? tripwires))
     :calcification calcification
     :tripwires tripwires}))
```

### Post-Operation Assessment

```clojure
(defn assess-operation [run-result]
  (let [harm-visible? (assess-visibility run-result)
        harm-bounded? (assess-boundedness run-result)
        harm-corrigible? (assess-corrigibility run-result)]
    {:ahimsa-satisfied? (and harm-visible? harm-bounded? harm-corrigible?)
     :details {:visible harm-visible?
               :bounded harm-bounded?
               :corrigible harm-corrigible?}}))
```

## Promotion Logic (Noble Tier)

### Observation Criteria

Signs that dynamics *resemble* noble operation:

1. **Faculty operating without operator**
   - Energy selection is state-responsive but not rule-following
   - No explicit "I am selecting this energy"
   - Emerges from conditions rather than being computed

2. **The running circle**
   - View, effort, mindfulness operating together
   - Each supporting the others
   - No single factor dominating

3. **Unforced stability**
   - Calcification detection never triggers
   - Not because it's suppressed, but because dynamics don't calcify
   - Flexibility maintained without effort

### Arrow-Based Promotion

```clojure
(defn suggest-promotion [dynamics-history]
  (when (noble-conditions-present? dynamics-history)
    {:promotion-candidate true
     :tier :noble
     :arrow {:source (current-mundane-factor dynamics-history)
             :target (corresponding-noble-factor)
             :mode :transcendence
             :construction (describe-what-was-observed dynamics-history)
             :note "Cannot manufacture; can only recognize"}}))
```

## What This Is Not

The raft is **not**:

1. **A replacement for futon5 dynamics** — it audits, doesn't override
2. **A way to compute noble operation** — noble tier can only be recognized
3. **A guarantee of liberation** — it's a raft, not the far shore
4. **Permanent** — once you've crossed, you don't carry it

## Implementation Sequence

1. **Now**: This tech note (sketch)
2. **Mission 5 complete**: Kolmogorov arrows working
3. **Then**: Implement `futon.raft` with:
   - Mundane gates (computable)
   - Noble observations (arrow-based, not blandishments)
   - Bridge to futon5 xenotype layer

## The Daoist-Buddhist Interface

Futon5's Daoist orientation (following the hexagrams, wu wei) is genuine and valuable. But:

> The Dao that can be spoken is not the eternal Dao.

Even the most fluid hexagram dynamics can calcify into "following the hexagrams" as a fixed practice. The Buddhist liberation layer provides the check:

- **Tripwires**: Is this "following" actually fabrication?
- **Mechanism**: Is the hexagram mapping genuinely causal?
- **Warrant**: Does following produce visible, bounded, corrigible effects?
- **Calcification**: Has the following itself become rigid?

The raft carries you across without replacing the water.

## Open Questions

1. **Arrow modes for transcendence**: What does a :transcendence arrow look like vs :refinement?
2. **Observability**: What dynamics signatures indicate noble-tier operation?
3. **Feedback**: Should noble-tier observations affect future mundane-tier gating?
4. **Letting go**: How does the raft know when to let go of itself?

---

*To be revisited when Kolmogorov arrows are operational.*
