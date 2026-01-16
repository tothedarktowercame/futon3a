# Compass Demonstrator: Exploratory Missions

Handoff document for Joe + Codex to explore the GFE-inspired navigation system.

## What Was Built

```
narrative → patterns → preferences → policies → GFE scoring → compass
```

**Files:**
- `src/futon/notions.clj` - Pattern retrieval (embedding + keyword search)
- `src/futon/compass.clj` - GFE-inspired compass demonstrator
- `resources/notions/patterns-index.tsv` - Pattern index with hotwords
- `resources/notions/minilm_pattern_embeddings.json` - MiniLM embeddings

**Run it:**
```bash
clojure -M -m futon.compass "your narrative here"
```

---

## Mission 1: Compass Calibration

**Goal:** Understand how different narratives produce different compass readings.

**Tasks:**
1. Run the compass with these contrasting narratives:
   ```bash
   clojure -M -m futon.compass "I need to refactor legacy code safely"
   clojure -M -m futon.compass "exploring new architecture without constraints"
   clojure -M -m futon.compass "debugging a critical production issue"
   ```

2. Compare the outputs:
   - Which patterns get retrieved for each?
   - How do the policy scores differ?
   - Does "exploit" vs "explore" vs "balanced" match intuition?

3. Document: What makes a narrative "aligned" vs "drifting" vs "blocked"?

**Success:** Write 3 sentences explaining when each policy type wins.

---

## Mission 2: Preference Model Dissection

**Goal:** Trace how flexiarg fields become preference dimensions.

**Tasks:**
1. Pick a pattern that gets retrieved (e.g., `agent/evidence-over-assertion`)
2. Read its flexiarg: `cat ../futon3/library/agent/evidence-over-assertion.flexiarg`
3. In a REPL, trace the extraction:
   ```clojure
   (require '[futon.notions :as n])
   (require '[futon.compass :as c])

   (def patterns (n/enrich-results (n/search "evidence assertions")))
   (def prefs (c/extract-preferences patterns))

   ;; Examine
   (:desired prefs)      ; THEN + NEXT-STEPS
   (:obstacles prefs)    ; IF + HOWEVER
   (:concepts prefs)     ; hotwords
   ```

4. Question: Are IF/HOWEVER really "obstacles"? Or are they "preconditions"?
   - The current mapping treats them as things to overcome
   - Alternative: treat IF as "when this applies" (scope), HOWEVER as "watch out for"

**Success:** Propose a refinement to the preference model mapping.

---

## Mission 3: Policy Dynamics Lab

**Goal:** Understand the exotype-style simulation.

**Tasks:**
1. Read `simulate-policy` and `apply-mutation` in compass.clj
2. The simulation currently:
   - Adds policy concepts to state (mutation-rate controlled)
   - Randomly removes obstacles
   - Runs for N steps

3. Experiment: Change the simulation parameters
   ```clojure
   (require '[futon.compass :as c])

   ;; Try different step counts
   (c/compass-report "my narrative" :sim-steps 5)
   (c/compass-report "my narrative" :sim-steps 50)

   ;; Try different seeds (affects random mutations)
   (c/compass-report "my narrative" :seed 123)
   (c/compass-report "my narrative" :seed 456)
   ```

4. Question: The simulation is very simple (random mutations). What would make it more "exotype-like"?
   - Idea: Use pattern relationships (arrows?) to guide transitions
   - Idea: Weight mutations by pattern similarity scores

**Success:** Sketch a richer simulation dynamics proposal.

---

## Mission 4: Connect to Meme Layer

**Goal:** Bridge compass recommendations to the meme proposal system.

**Context:** We built `src/meme/proposal.clj` with:
- `propose!` - create proposals
- `propose-pattern-recurrence!` - propose pattern observations
- Proposals can be promoted to facts

**Tasks:**
1. After running compass, the output includes:
   - Retrieved patterns
   - Recommended policy
   - Next evidence to collect

2. Design: How should compass outputs become meme proposals?
   ```
   compass-report → ???? → meme/proposal
   ```

   Options:
   - Auto-propose each retrieved pattern as "observed in context X"
   - Propose the recommended policy as a "navigation intent"
   - Propose obstacles as "blockers to investigate"

3. Implement a bridge function:
   ```clojure
   (defn compass->proposals [report]
     ;; Convert compass report to meme proposals
     ...)
   ```

**Success:** Running compass creates auditable proposals in the meme layer.

---

## Mission 5: Kolmogorov Arrows for Policy Transitions

**Goal:** Use arrows to represent policy transformations.

**Context:** We built `src/meme/arrow.clj` with:
- Arrows have source, target, mode, confidence
- Modes: translation, abstraction, metonymy, etc.

**Tasks:**
1. A policy transition (exploit → balanced) could be an arrow:
   ```
   {:source "policy/exploit"
    :target "policy/balanced"
    :mode :adaptation
    :confidence 0.7
    :warrant "epistemic signal too low"}
   ```

2. Design: When should the compass emit arrows?
   - When recommending a policy change?
   - When patterns suggest a transformation?
   - When obstacles are addressed?

3. Question: What's the "construction" (BHK sense) for a policy arrow?
   - The arrow isn't just "exploit is related to balanced"
   - It's "here's HOW to get from exploit to balanced"

**Success:** Propose arrow semantics for compass-driven navigation.

---

## Mission 6: GFE Tuning

**Goal:** Calibrate the free energy objective.

**Current formula:**
```clojure
G = -( 0.6 * pragmatic + 0.4 * epistemic )
```

Where:
- pragmatic = Jaccard overlap of outcome concepts with desired concepts
- epistemic = fraction of obstacles addressed

**Tasks:**
1. The weights (0.6/0.4) are arbitrary. Experiment:
   ```clojure
   ;; In compass.clj, try:
   G = -(0.8 * pragmatic + 0.2 * epistemic)  ; exploitation-heavy
   G = -(0.3 * pragmatic + 0.7 * epistemic)  ; exploration-heavy
   ```

2. Question: Should weights be narrative-dependent?
   - "Debug production issue" → high pragmatic weight
   - "Explore new architecture" → high epistemic weight

3. Advanced: Add a third term for "surprise" or "novelty"
   - Penalize policies that just confirm what we already know
   - Reward policies that surface unexpected patterns

**Success:** Propose an adaptive weighting scheme.

---

## Mission 7: Futon5 Integration Sketch

**Goal:** Connect compass to the MMCA exotype model.

**Context:** Futon5 has:
- 256 sigils = 256 elementary CA rules
- Genotype (rules) / Phenotype (behavior) / Exotype (environment)
- AIF scoring for edge-of-chaos regimes

**Tasks:**
1. The compass "policies" are like exotypes - they condition agent behavior
2. Design mapping:
   ```
   compass policy  ←→  futon5 exotype
   pattern sigil   ←→  CA rule (0-255)
   preference model ←→  fitness landscape
   ```

3. Question: Can we use CA dynamics to simulate policy evolution?
   - Instead of random mutations, use CA neighborhood rules
   - Policy "state" evolves according to sigil-encoded rules

4. Sketch: A `compass-exotype` namespace that:
   - Takes a narrative
   - Maps retrieved pattern sigils to CA rules
   - Runs CA simulation
   - Scores with GFE

**Success:** Architecture diagram for compass-exotype bridge.

---

## Mission 8: Self-Description Test

**Goal:** Run compass on its own codebase.

**Tasks:**
1. The ultimate reflexivity test:
   ```bash
   clojure -M -m futon.compass "implementing GFE-inspired navigation for pattern retrieval and policy simulation"
   ```

2. Does it retrieve relevant patterns?
3. What policy does it recommend for building itself?
4. What obstacles does it surface?

**Success:** The compass provides useful guidance for its own development.

---

# Part II: p4ng Exotype Missions

These missions explore the sigil-exotype connection discovered during compass development.
See `docs/tech-note-sigil-exotype-lift.md` for background.

**Key insight:** Pattern sigils have structure `[emoji/hanzi]` where:
- emoji = exotype (environmental regime)
- hanzi = genotype (specific rule, 0-255 space)

---

## Mission 9: Emoji Regime Inventory

**Goal:** Map the full emoji vocabulary used across p4ng patterns.

**Tasks:**
1. Extract all sigils from p4ng patterns:
   ```bash
   grep -h "@sigils" ../futon3/library/p4ng/*.multiarg ../futon3/library/p4ng/*.flexiarg | sort | uniq
   ```

2. Build an emoji → regime table:
   | Emoji | Proposed Regime | Count | Example Pattern |
   |-------|-----------------|-------|-----------------|
   | 💢 | tension | ? | institutional-drift |
   | 🎐 | meta | ? | reflection-scaffold-upgrade |
   | ... | ... | ... | ... |

3. Question: Are regimes consistent across p4ng? Or context-dependent?

**Success:** Complete emoji → regime mapping with confidence levels.

---

## Mission 10: Hanzi → Rule Number Mapping

**Goal:** Establish how hanzi characters map to the 0-255 rule space.

**Tasks:**
1. Options for mapping:
   - **Unicode codepoint mod 256** - deterministic but arbitrary
   - **Stroke count** - meaningful but limited range
   - **I Ching mapping** - 64 hexagrams × 4 = 256
   - **Semantic hash** - hash the character's meaning

2. Inventory hanzi used in p4ng:
   ```bash
   grep -oh '\[.*/.\]' ../futon3/library/p4ng/*.multiarg | sed 's/.*\///' | sed 's/\]//' | sort | uniq -c
   ```

3. Propose a mapping function:
   ```clojure
   (defn hanzi->rule [hanzi]
     ;; Your proposal here
     )
   ```

4. Question: Should the mapping preserve semantic relationships? (Similar hanzi → similar rules?)

**Success:** Implemented `hanzi->rule` with rationale for the choice.

---

## Mission 11: Regime → Dynamics Mapping

**Goal:** Define what CA dynamics each emoji regime implies.

**Tasks:**
1. For each regime, propose dynamics characteristics:
   | Regime | Dynamics | Rationale |
   |--------|----------|-----------|
   | 💢 tension | Edge-of-chaos, high λ | Conflict drives boundary activity |
   | 🎐 meta | Periodic/stable | Reflection needs consistency |
   | 🚴 effort | Gradient-following | Work seeks efficiency |
   | 😻 trust | Cooperative attractors | Affection builds stability |
   | 🔃 recursion | Self-similar/fractal | Recursion has nested structure |
   | 💬 dialogue | Turn-taking oscillation | Communication alternates |

2. Map to Wolfram CA classes:
   - Class I (uniform) → ?
   - Class II (periodic) → ?
   - Class III (chaotic) → ?
   - Class IV (complex) → ?

3. Implement:
   ```clojure
   (defn regime->dynamics [emoji]
     ;; Returns a CA transition function or parameters
     )
   ```

**Success:** Each regime has defined dynamics with CA class mapping.

---

## Mission 12: Sigil-Aware Simulation

**Goal:** Replace random mutations with sigil-driven dynamics.

**Tasks:**
1. Current `apply-mutation` in compass.clj uses random mutations
2. New version should:
   ```clojure
   (defn apply-mutation-sigil [state policy sigil rng]
     (let [{:keys [regime rule]} (parse-sigil sigil)
           dynamics (regime->dynamics regime)
           ;; Use CA rule to determine mutation
           ...]
       ...))
   ```

3. The simulation loop becomes:
   - Get sigils from retrieved patterns
   - For each step, apply sigil-conditioned mutation
   - Different patterns → different dynamics → different outcomes

4. Test with contrasting sigil sets:
   - All 💢 patterns → expect volatile/boundary-seeking
   - All 🎐 patterns → expect stable/reflective

**Success:** Simulation behavior visibly differs based on sigil regime.

---

## Mission 13: Multi-Sigil Composition

**Goal:** Handle patterns with multiple sigils.

**Context:** Some patterns have `@sigils [💢/了 🎐/介]` - multiple sigils.

**Tasks:**
1. Semantics options:
   - **Union:** Apply all regimes, blend dynamics
   - **Sequence:** Apply regimes in order
   - **Dominant:** First sigil primary, others modify
   - **Context-switch:** Different sigils for different phases

2. Investigate: What do multi-sigil patterns mean?
   ```bash
   grep -h "@sigils \[.*\ .*\]" ../futon3/library/p4ng/*.multiarg
   ```

3. Propose composition semantics with rationale

**Success:** Documented multi-sigil handling strategy.

---

## Mission 14: End-to-End Sigil Demo

**Goal:** Run compass with full sigil-exotype integration.

**Tasks:**
1. Query that retrieves p4ng environment patterns:
   ```bash
   clojure -M -m futon.compass "multi-agent coordination with norm evolution"
   ```

2. Trace the flow:
   - Patterns retrieved (with sigils)
   - Sigils parsed (emoji + hanzi)
   - Exotypes determined (regimes)
   - Rules selected (hanzi → 0-255)
   - Simulation run with CA dynamics
   - GFE scored
   - Compass output

3. Compare to random-mutation baseline:
   - Does sigil-aware simulation give different recommendations?
   - Are the differences interpretable?

**Success:** Demo showing sigil-driven dynamics affecting compass output.

---

## Mission 15: Sigil Visualization

**Goal:** Visualize the sigil space and pattern distribution.

**Tasks:**
1. Plot patterns in emoji × hanzi space
2. Color by compass recommendation (exploit/explore/balanced)
3. Questions:
   - Do patterns cluster by regime?
   - Are some regimes over/under-represented?
   - Where are the gaps?

**Success:** Visual map of p4ng sigil space with insights.

---

## Handoff Notes for Codex

**If you're Codex picking this up:**

1. The compass is in `src/futon/compass.clj` - it's ~300 lines, self-contained
2. Pattern retrieval uses MiniLM embeddings via Python (`scripts/notions_search.py`)
3. The meme layer is ready but not connected yet (Mission 4)
4. The simulation dynamics are intentionally simple - that's where exotype integration goes
5. **NEW:** See `docs/tech-note-sigil-exotype-lift.md` for the emoji/hanzi discovery

**Key insight from the build:**
> "Futon theory: treat near-future as virtual attractor shaping present action."

The compass doesn't predict the future - it computes alignment signals between where you are (patterns retrieved) and where you want to go (preference model). The policy simulation is a probe, not a prophecy.

**Second key insight (p4ng exploration):**
> "The emoji IS the exotype. The hanzi IS the genotype."

Pattern sigils `[emoji/hanzi]` encode both the environmental regime (selection pressure) and the specific rule. This connects directly to futon5's MMCA model. Missions 9-15 explore this.

**Open questions:**
- Should obstacles be "things to overcome" or "conditions to satisfy"?
- How do pattern arrows (Kolmogorov) relate to policy transitions?
- What makes a compass reading "actionable"?
- How should hanzi map to rule numbers (0-255)?
- What CA dynamics correspond to each emoji regime?

Good hunting.
