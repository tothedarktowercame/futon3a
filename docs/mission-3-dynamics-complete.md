# Mission 3: Policy Dynamics Lab — Complete

Date: 2026-01-24

## What Was Built

The simulation has evolved from random energy selection to a sophisticated, context-aware dynamics system.

### Before (compass.clj)

```
energy = random_weighted_select(base_weights)
state' = apply_energy(state, energy)
```

- Fixed weights (péng 30%, lǚ 15%, etc.)
- No awareness of state
- No pattern influence
- No security integration
- No calcification detection

### After (compass_dynamics.clj)

```
modifiers = state_modifiers(state) + pattern_modifiers(patterns) + security_modifiers(scan)
weights' = base_weights + modifiers
energy = weighted_select(weights')
if (calcified?) energy = liberation_correction(energy)
state' = apply_energy(state, energy)
```

## Key Components

### 1. State-Sensitive Selection

Energy weights are modified based on current state:

| Condition | Energy Boost | Rationale |
|-----------|--------------|-----------|
| Few concepts (< 3) | péng +15%, àn +10% | Need expansion |
| Many concepts (> 10) | jǐ +10%, kào +10% | Need focus/consolidation |
| Many risks (> 5) | lǚ +15%, cǎi +10% | Need acknowledgment |
| No risks | àn +10%, péng +5% | Can push forward |
| Repeated energy | dominant -15% | Anti-calcification |

### 2. Pattern-Derived Energies

If retrieved patterns have `@energy` annotations, those energies are boosted:

```clojure
;; Pattern has @energy ji
;; Simulation weights jǐ higher
{:ji +0.2}  ; proportional to pattern count
```

This lets the simulation follow the "grain" of the patterns.

### 3. Security Integration

When tripwires fire, control-associated energies are boosted:

```clojure
;; Tripwire :triggered
{:lu +0.15 :ji +0.10 :peng +0.05}

;; Specific escalation energy
;; e.g., escalation → :lie (split-isolate)
{:lie +0.20}
```

### 4. Calcification Detection

The liberation layer monitors for rigid patterns:

| Type | Detection | Correction |
|------|-----------|------------|
| Energy repetition | Same energy 8+ times in last 10 | Force different energy |
| Risk stagnation | > 5 risks after 15+ steps | Force yield energy (lǚ, cǎi, kào) |

### 5. Liberation Corrections

When calcification is detected, the system injects a corrective energy:

```
Step 0: energy-repetition, dominant: an -> corrective: lu
Step 20: risk-stagnation, dominant: none -> corrective: lu
```

This prevents the simulation from getting stuck while preserving accumulated value.

## Simulation Modes

Three levels of sophistication:

1. **Basic** (`compass/simulate-policy`): Random weighted selection
2. **Enriched** (`simulate-policy-enriched`): Context-sensitive, pattern-aware
3. **Liberation** (`simulate-policy-liberation`): Full calcification detection/correction

## Files Changed

```
src/futon/compass_dynamics.clj  # New: enriched dynamics (350+ lines)
src/futon/notions.clj           # Modified: added @energy, @sigils parsing
```

## Connection to Futon5

This directly mirrors the exotype/xenotype architecture:

| Compass Dynamics | Futon5 Equivalent |
|------------------|-------------------|
| Energy selection | Local exotype rule selection |
| State modifiers | Context-dependent rule bending |
| Pattern energies | Hexagram → energy mapping |
| Security integration | Xenotype activation |
| Liberation corrections | Global rule that bends local rules |

The simulation is now a proper "exotype-style" dynamics system, not just random mutations.

## Test Results

### Context-Sensitive Selection

For a low-concept, high-risk state:
```
lu: 23%    (yield to acknowledge risks)
peng: 17%  (expand concepts)
an: 15%    (push forward)
cai: 13%   (pluck - ground in risk)
ji: 13%    (focus)
```

### Liberation Corrections

Starting with pre-calcified state (8x àn repetition):
```
Step 0: energy-repetition, dominant: an -> corrective: lu
Step 20: risk-stagnation, dominant: none -> corrective: lu
```

System broke the calcification pattern automatically.

## What's Next

- **Mission 3a**: Devmap enrichment (parse devmap patterns for compass)
- **Mission 4**: Connect to meme layer (compass → proposals)
- **Integration**: Replace `simulate-policy` with `simulate-policy-liberation` in main compass flow

## Architectural Insight

The liberation layer operating on the simulation is structurally identical to how the noble tier operates on practice:

- **Mundane**: Apply energies, accumulate concepts
- **Noble**: Notice when the accumulation calcifies, inject correction without destroying value

This is the "running circle" — view (understand state), effort (apply energy), mindfulness (detect calcification) — operating at the simulation level.
