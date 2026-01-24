# Pre-Part II CHECKPOINT

Date: 2026-01-24

## What This Session Accomplished

### Futon5: Exotype Invariants

Fixed the fundamental exotype architecture:

1. **36-bit local physics**: Each cell computes its own context (LEFT/EGO/RIGHT/NEXT/PHENOTYPE) → hexagram → energy → rule (0-255)

2. **Eigenvalue diagonalization**: Proper matrix decomposition for hexagram extraction (not just diagonal)

3. **256 rules = 64 hexagrams × 4 primary energies** (Péng, Lǚ, Jǐ, Àn)

4. **Runtime support**: `:exotype-mode :local-physics` in run-mmca

5. **Invariant verification**: `verify-invariants.clj` — run before any compute

6. **Xenotype redefinition**: Not fitness specs, but the security/escalation layer

**Key files:**
- `src/futon5/hexagram/lift.clj` — eigenvalue-based hexagram extraction
- `src/futon5/mmca/exotype.clj` — local physics, composition, global bending
- `src/futon5/mmca/runtime.clj` — local-physics mode
- `src/futon5/mmca/xenoevolve.clj` — global rule evolution
- `src/futon5/mmca/verify_invariants.clj` — invariant checker
- `test/futon5/mmca/exotype_invariants_test.clj` — 13 tests, 1124 assertions

### Futon3a: Mission 2a Security Layer

Established the security pattern architecture:

1. **Fabrication templates identified**: Pattern-shaped artifacts that block learning

2. **BECAUSE clause as attack surface**: Can be descriptively true but normatively invalid

3. **Tripwires defined**: harm-is-external, dissent-is-threat, self-sealing-logic, escalate-on-failure, exit-suppression, review-blocking

4. **Two tests for BECAUSE**:
   - Mechanism test: Is it causal or decorative?
   - Warrant test: Does explanation warrant adoption? (ahimsa: visible, bounded, corrigible)

5. **Four primary security patterns** (control/):
   - ward-off-boundary (Péng) — quarantine
   - roll-back-hold (Lǚ) — hold without adopting
   - press-mechanism (Jǐ) — mechanism test
   - push-warrant (Àn) — warrant test

**Key files:**
- `docs/compass-mission-2a-plan.md` — mission plan
- `library/control/README.md` — library overview
- `library/control/*.flexiarg` — four primary security patterns

### Conceptual Breakthroughs

1. **Xenotypes = Security Layer**: The "global rule that bends local rules" is the immune system, not just optimization

2. **Anti-patterning vs Anti-patterns**: Fabrication templates attack the capacity to pattern, not just individual patterns

3. **Ahimsa as operational constraint**: Harm must be visible, bounded, and corrigible — not zero harm, but corrigible harm

4. **Eight energies map to escalation**: Primary energies for normal escalation, secondary for exceptional

## What Remains (Part II)

### Futon5

- [ ] Run xenoevolve with new global-rule evolution
- [ ] Test that local physics actually produces better dynamics
- [ ] Verify scoring is against real runs (invariant 4 in practice)

### Futon3a

- [ ] Implement tripwire detection functions
- [ ] Implement mechanism test in compass
- [ ] Implement warrant test in compass
- [ ] Create secondary security patterns (Cǎi, Liè, Zhǒu, Kào)
- [ ] Connect control patterns to compass escalation
- [ ] Bridge security layer to futon5 xenotype activation

### Bridge

- [ ] Revisit `compass_exotype.clj` with security layer in mind
- [ ] Map tripwire → xenotype activation
- [ ] Map energy → global rule bending mode

## Key Insight Summary

> Pattern templates are not self-certifying. The BECAUSE clause is where fabrication templates hide. Ahimsa (visible, bounded, corrigible harm) is the legitimacy test.

### Xenotypes: Broader Than Security

The security layer is one **application** of xenotypes, not their definition.
Xenotypes are domain-agnostic "patterns of improvisation" (256 = 64 hexagrams × 4 energies)
that work across all domains. The security patterns (ward-off, roll-back, press, push)
are xenotypes specialized for the fabrication-template-detection problem.

| Layer | Relationship |
|-------|--------------|
| Genotype | Sigil: 8-bit CA rule |
| Exotype | Local policy: which rule for this cell |
| Xenotype | Global policy: how to bend all local rules |
| AIF/GFE | Meta-policy: what selects among xenotypes |

AIF is the "xenotype to the xenotype" — the fixed reference frame within which
xenotypes evolve.

## To Resume

1. Run `clojure -M -m futon5.mmca.verify-invariants` to confirm futon5 invariants still pass
2. Review `docs/compass-mission-2a-plan.md` for next tasks
3. Decide: implement tripwire detection, or test futon5 xenoevolve first?

## Files Changed This Session

### Futon5
```
src/futon5/hexagram/lift.clj (modified)
src/futon5/mmca/exotype.clj (modified)
src/futon5/mmca/runtime.clj (modified)
src/futon5/mmca/xenoevolve.clj (modified)
src/futon5/mmca/verify_invariants.clj (created)
test/futon5/mmca/exotype_invariants_test.clj (created)
```

### Futon3a
```
docs/compass-mission-2a-plan.md (created)
library/control/README.md (created)
library/control/ward-off-boundary.flexiarg (created)
library/control/roll-back-hold.flexiarg (created)
library/control/press-mechanism.flexiarg (created)
library/control/push-warrant.flexiarg (created)
```
