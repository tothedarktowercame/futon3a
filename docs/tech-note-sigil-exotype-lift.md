# Tech Note: p4ng Patterns as Exotypes

**Date:** 2026-01-16
**Context:** Compass demonstrator, p4ng environment patterns, futon5 MMCA integration

## Core Insight

**p4ng agent-environment patterns ARE exotypes.**

They're not operational patterns about *what* to do. They're meta-strategic patterns about *how the environment shapes viable behavior*. They define selection pressures.

| Pattern | Selection Pressure |
|---------|-------------------|
| `institutional-drift` | Routinization → ossification risk |
| `norm-seed` | Initial conditions → path dependence |
| `boundary-oscillation` | Divergence → reconvergence demand |
| `proportional-load-sharing` | Overcontribution → redistribution pressure |
| `legitimate-iteration` | Authority ambiguity → validation requirement |
| `disruption-traceback` | Breakdown → accountability demand |

When the compass retrieves these patterns, it's not just finding relevant advice. **It's detecting which exotype regime the agent is operating in.**

```
narrative → compass → retrieves p4ng patterns → IDENTIFIES EXOTYPE
```

The compass is already doing exotype detection. It just doesn't know it yet.

## Secondary Insight: Sigil Structure

The p4ng pattern sigils have a two-part structure:

```
@sigils [💢/了]
        ^^^^
        emoji/hanzi
```

This encodes the exotype relationship:

| Component | Meaning | Maps To |
|-----------|---------|---------|
| emoji | Exotype regime | Environmental selection pressure TYPE |
| hanzi | Sigil (rule) | Specific CA dynamics (0-255) |

## Observed Emoji Regimes in p4ng-agent-environments

| Emoji | Regime | Patterns |
|-------|--------|----------|
| 💢 | tension/conflict | institutional-drift, norm-seed, boundary-oscillation, reflect-in-layers, disruption-traceback |
| 🎐 | meta/reflection | reflection-scaffold-upgrade |
| 🚴 | effort/work | proportional-load-sharing |
| 😻 | trust/affection | legitimate-iteration |
| 🔃 | recursion/self | self-patterning-mandate |
| 💬 | dialogue/communication | pattern-dispute-dialogue |

## The Lift

**Problem:** How do we go from a retrieved pattern's sigil to exotype dynamics for simulation?

**Solution:** Decompose the sigil.

```
sigil = emoji/hanzi
      = exotype/genotype
      = regime/rule
```

The **lift** is:
1. emoji → exotype category (which CA transition function family)
2. hanzi → rule number (which specific rule within that family)

```clojure
(defn parse-sigil [sigil-str]
  ;; "[💢/了]" → {:emoji "💢" :hanzi "了"}
  (let [[_ emoji hanzi] (re-matches #"\[(.+)/(.+)\]" sigil-str)]
    {:emoji emoji :hanzi hanzi}))

(defn sigil->exotype [{:keys [emoji hanzi]}]
  {:regime   (emoji->regime emoji)       ; 💢 → :tension
   :rule     (hanzi->rule-number hanzi)  ; 了 → some 0-255 index
   :dynamics (regime->dynamics emoji)})  ; CA transition fn
```

## Why This Matters

### p4ng patterns = exotype detection

The compass already retrieves p4ng patterns when narratives involve agent coordination, strategy, governance. This means:

1. **Compass is an exotype detector** - it identifies environmental regimes
2. **Policy simulation should be exotype-conditioned** - retrieved patterns tell us which dynamics to use
3. **The simulation isn't generic** - it's parameterized by detected exotype

### Sigil structure enables fine-grained dynamics

**Same hanzi, different emoji = same rule, different exotype = different emergent behavior.**

A rule like `了` in the 💢 (tension) regime might produce boundary-seeking behavior.
The same rule in 🎐 (meta) regime might produce reflective oscillation.

The exotype conditions how the rule expresses. This is exactly the genotype/phenotype/exotype relationship from futon5:
- Genotype = hanzi (the rule)
- Phenotype = behavior (what the rule produces)
- Exotype = emoji (environmental pressure that shapes expression)
- **Pattern itself** = the exotype definition (what selection pressure exists)

## Integration Path

### Level 1: Pattern-as-Exotype (semantic)

1. **Compass retrieves p4ng patterns** → these ARE exotypes
2. **Pattern content defines selection pressure** → IF/HOWEVER/THEN describe environmental constraints
3. **Policy viability is exotype-relative** → "exploit" might be viable under `legitimate-iteration` but not under `boundary-oscillation`

### Level 2: Sigil-as-Dynamics (computational)

1. **Extract sigils** → gets `[💢/了]` from retrieved patterns
2. **Parse sigils** → extract emoji (regime) and hanzi (rule)
3. **Select dynamics** → emoji determines which CA family
4. **Parameterize simulation** → hanzi selects specific rule (0-255)
5. **Run simulation** → exotype-conditioned policy evolution
6. **Score with GFE** → pragmatic + epistemic signals

The two levels compose: the pattern tells you WHAT exotype you're in (semantic), the sigil tells you HOW to simulate it (computational).

## Open Questions

1. **Hanzi → rule number mapping:** Is there a canonical mapping? Or do we hash?
2. **Regime → dynamics:** What CA families correspond to each emoji?
3. **Multiple sigils:** Some patterns have multiple sigils. How to combine?
4. **Emoji vocabulary:** What's the full set of regime emojis across all patterns?

## Next Steps

- Inventory all emoji used in p4ng patterns
- Propose emoji → CA-family mapping
- Implement `sigil->exotype` in futon3a
- Wire into compass policy simulation

## References

- `futon3/library/p4ng/p4ng-agent-environments.multiarg` - source patterns
- `futon3a/src/futon/compass.clj` - current simulation (random mutations)
- futon5 MMCA documentation - genotype/phenotype/exotype model
