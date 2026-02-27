# Tech Note: The Raft (futon.raft)

## Overview

`futon.raft` — futon3a as "xenotype of the xenotype" for futon5.

Futon5 operates with Daoist orientation: hexagrams, tai chi energies, wu wei (following the grain). But the Daoist flow can calcify just as readily as anything else. The Buddhist liberation layer provides the anti-calcification check that keeps the flow flowing.

## The Raft Parable

> "Monks, I will teach you the Dhamma compared to a raft, for the purpose of crossing over, not for the purpose of holding onto."
> — MN 22

The raft is for crossing, not for carrying. Futon3a's liberation patterns are tools for maintaining flexibility, not doctrines to rigidify around.

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                    FUTON3A (RAFT)                                │
│                                                                  │
│  Noble tier ─────────────────────────────────────────────────   │
│  │ Cannot be computed, only recognized                          │
│  │ Suggests promotions when faculty-operating qualities appear  │
│  │ REQUIRES: Kolmogorov arrows for causal reasoning             │
│  └──────────────────────────────────────────────────────────────│
│                           ↑                                      │
│  Mundane tier ──────────────────────────────────────────────────│
│  │ Computable gates:                                            │
│  │  • Tripwire detection (fabrication template signatures)     │
│  │  • Mechanism test (is BECAUSE causal or decorative?)        │
│  │  • Warrant test (ahimsa: visible, bounded, corrigible?)     │
│  └──────────────────────────────────────────────────────────────│
│                           ↓                                      │
├─────────────────────────────────────────────────────────────────┤
│                    FUTON5 (FLOW)                                 │
│                                                                  │
│  Xenotype: 256 global rules (64 hexagrams × 4 energies)        │
│  Exotype: local physics (context → hexagram → energy → rule)   │
│  Genotype: sigil (8-bit CA rule)                                │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

## Mundane Gates (Computable Now)

Before a xenotype activates, run it through mundane-tier checks:

### Gate 1: Tripwire Scan

```clojure
(defn gate-tripwires [xenotype-spec]
  ;; Extract the "BECAUSE" of this xenotype's operation
  ;; Scan for fabrication-template signatures
  ;; Returns :pass, :suspicious, :blocked
  )
```

Questions answered:
- Is this xenotype externalizing harm?
- Does it treat disconfirmation as threat?
- Is its logic self-sealing?

### Gate 2: Mechanism Test

```clojure
(defn gate-mechanism [xenotype-spec]
  ;; Is the hexagram → energy → rule mapping genuinely causal?
  ;; Or is it decorative (any mapping would "work" equally)?
  ;; Returns :genuine, :decorative, :ambiguous
  )
```

The hexagram extraction (eigenvalue decomposition) has genuine mechanism. But particular xenotype *applications* might be decorative — "use this energy because it sounds right" vs. "use this energy because the state actually calls for it."

### Gate 3: Warrant Test (Ahimsa)

```clojure
(defn gate-ahimsa [xenotype-spec run-result]
  ;; After a xenotype has operated:
  ;; - Was harm visible? (not externalized)
  ;; - Was harm bounded? (not scaling)
  ;; - Was harm corrigible? (can be contested)
  ;; Returns :pass, :violation with details
  )
```

This gate operates *after* the xenotype runs — it's a check on effects, not intentions.

## Noble Promotions (Requires Kolmogorov Arrows)

The noble tier cannot be computed — it can only be recognized. But recognition requires causal reasoning: "This pattern of operation exhibits qualities *because* of structural features, not accidentally."

### Why Arrows Are Needed

Without causal arrows, we can only say:
- "This xenotype's energy distribution is varied" (observation)
- "This pattern didn't calcify" (observation)

With causal arrows, we can say:
- "Energy variety → reduced calcification" (causal claim)
- "State-sensitivity → appropriate response" (mechanism)
- "Liberation corrections → preserved flexibility without destroying value" (warranted intervention)

### Promotion Candidates

When Kolmogorov arrows are available, the raft can suggest promotions:

| Observation | Arrow Required | Promotion Candidate |
|-------------|----------------|---------------------|
| Varied energy distribution | variety → flexibility | Right Effort (faculty operating) |
| State-responsive selection | response → appropriateness | Right View (faculty operating) |
| Calcification detected and corrected | correction → preservation | Right Mindfulness (faculty operating) |
| Stable without rigidity | stability → concentration without grasping | Right Concentration (faculty operating) |

The promotions are *suggestions*, not assertions. They say: "This operation has qualities associated with X" — not "This operation IS X."

### Arrow Structure for Promotions

```clojure
{:source "xenotype-operation-pattern"
 :target "noble-factor-quality"
 :mode :recognition  ; not :assertion
 :construction {
   :observation "energy-variety > 0.7"
   :mechanism "state-sensitive selection"
   :warrant "varied response to varied conditions"
 }
 :confidence :suggestive}
```

The `:construction` field is where Kolmogorov arrows provide the causal backbone. Without it, promotions are just blandishments.

## What the Raft Does NOT Do

1. **Does not replace futon5 dynamics** — the Daoist flow continues
2. **Does not compute enlightenment** — noble tier is recognized, not produced
3. **Does not carry itself** — the raft is for crossing, then released
4. **Does not moralize** — gates are structural checks, not value judgments

## Implementation Sequence

### Phase 1: Mundane Gates (Available Now)
- Integrate `compass-security` tripwire/mechanism/warrant tests
- Apply to xenotype specifications before activation
- Log gate results for audit

### Phase 2: Observation Layer (Available Now)
- Capture xenotype operation patterns
- Record energy distributions, calcification events, corrections
- Build dataset for arrow learning

### Phase 3: Arrow Integration (Requires Mission 5 Completion)
- Kolmogorov arrows for causal claims
- Construction-based reasoning for promotions
- Typed transforms between observation and recognition

### Phase 4: Noble Promotions (Requires Arrows)
- Pattern matching for faculty-operating qualities
- Arrow-backed suggestions (not blandishments)
- Promotion candidates with explicit constructions

## Connection to Eight Energies

The raft operates through the same eight energies as the flow:

| Energy | Gate Function | Promotion Recognition |
|--------|---------------|----------------------|
| Péng | Boundary check (tripwire) | Expansion without grasping |
| Lǚ | Hold for review | Yielding without collapse |
| Jǐ | Mechanism test | Focus without fixation |
| Àn | Warrant test | Push without forcing |
| Cǎi | Extract suspect component | Grounding without grabbing |
| Liè | Isolate pattern | Separation without destruction |
| Zhǒu | Immediate intervention | Adjustment without overreaction |
| Kào | Full escalation | Commitment without attachment |

## Open Questions

1. **Granularity**: Does the raft gate individual xenotype activations, or xenotype *types*?

2. **Timing**: When does ahimsa check run? After each step? After full run?

3. **Override**: Can a xenotype be activated despite failing mundane gates? (Emergency override with logging?)

4. **Learning**: Do gate failures improve future xenotype design? (Arrow from failure → improvement)

5. **Reflexivity**: Does the raft apply to itself? (Can liberation patterns calcify?)

## The Point

Futon5's Daoist dynamics are genuine and valuable. But any system can calcify. The raft provides:

- **Mundane layer**: Computable gates that catch obvious problems
- **Noble layer**: Arrow-backed recognition of faculty-operating qualities

The raft doesn't make futon5 "more Buddhist" — it provides the structural check that any flowing system needs to keep flowing.

> The raft is for crossing, not for carrying.
> The gates are for checking, not for blocking.
> The promotions are for suggesting, not for asserting.
