# Mission 2a: BECAUSE Clause Security Layer

Date: 2026-01-24

## Context

Mission 2 discovered that IF/HOWEVER are not obstacles but tensions to navigate. The eight-gates library emerged from this insight.

Mission 2a addresses a deeper problem: **the BECAUSE clause is the attack surface for fabrication templates**.

A fabrication template is a pattern-shaped artifact that:
- Looks like a valid pattern (has all the formal fields)
- Produces action and justification
- Quietly disables learning by blocking review, disconfirmation, or revision

This is not anti-pattern (a bad pattern) but **anti-patterning** (an attack on the capacity to pattern).

## The Problem

Pattern templates are not self-certifying. The same structure can host:

1. **Genuine pattern**: Remains coupled to feedback and review
2. **Fabrication template**: Produces action while blocking correction

The BECAUSE clause is where masquerade hides because:
- It can be **descriptively true** (explains attraction) while being **normatively invalid** (doesn't warrant adoption)
- Example: "Certainty restores agency" — true as psychology, invalid as guidance if certainty ignores evidence

## Security Architecture

Not every pattern needs security scrutiny. Default is permissive.

### Layer 1: Everyday Patterns (Default Trust)
- Speculative BECAUSE is fine
- Patterns run their course
- Eight-gates energies navigate tensions

### Layer 2: Tripwires (Passive Monitoring)
- Watch for fabrication template signatures
- Fire when invariants break
- Do not interfere unless triggered

### Layer 3: Escalation (Active Security)
- Triggered tripwires activate xenotype layer
- Force validation on BECAUSE clause
- Quarantine rather than erase

## Tripwires (What Triggers Escalation)

| Tripwire | Detection Signal | What It Blocks |
|----------|------------------|----------------|
| Harm-is-external | Cost assigned outside boundary | Visibility |
| Dissent-is-threat | Critique reframed as attack | Correction |
| Self-sealing-logic | Evidence reinterpreted to confirm | Disconfirmation |
| Escalate-on-failure | Doubling down when wrong | Learning from error |
| Exit-suppression | Can't withdraw/revise | Corrigibility |
| Review-blocking | Channels for audit closed | The loop itself |

## Two Tests for BECAUSE (When Scrutiny Is Warranted)

### Mechanism Test
> Is the BECAUSE actually explaining what causes what, or is it decorative?

A decorative BECAUSE provides post-hoc rationalization rather than causal explanation.

**Red flag**: BECAUSE that could justify any THEN equally well.

### Warrant Test
> Even if the BECAUSE explains the attraction, does it warrant the move under relevant goals and constraints?

A valid explanation of why something is attractive is not the same as a warrant for adoption.

**Red flag**: BECAUSE that explains stability without justifying adoption.

## Security Patterns (Eight Energies)

### Primary Energies (四正) — Normal Escalation

| Pattern | Energy | Hanzi | Security Response |
|---------|--------|-------|-------------------|
| `control/ward-off` | Péng | 掤 | Establish boundary, quarantine pattern |
| `control/roll-back` | Lǚ | 捋 | Yield without adopting, hold for review |
| `control/press` | Jǐ | 擠 | Demand mechanism test, focus scrutiny |
| `control/push` | Àn | 按 | Force warrant test, sustained pressure |

### Secondary Energies (四隅) — Exceptional Escalation

| Pattern | Energy | Hanzi | Security Response |
|---------|--------|-------|-------------------|
| `control/pluck` | Cǎi | 採 | Extract suspect component from pattern |
| `control/split` | Liè | 挒 | Separate pattern from context, isolate |
| `control/elbow` | Zhǒu | 肘 | Immediate close-range intervention |
| `control/lean` | Kào | 靠 | Full commitment, final resort escalation |

## Connection to Exotype/Xenotype

In futon5 terms:
- **Local exotypes** = everyday pattern execution (default trust)
- **Xenotypes** = security/escalation layer (dormant until tripwire)

The xenotype "bends" local exotypes when activated:
- Péng bending → boundary enforcement
- Lǚ bending → yield/hold dynamics
- Jǐ bending → focus/scrutinize dynamics
- Àn bending → pressure/force-review dynamics

## Implementation Tasks

### Task 1: Create `library/control/` directory
- One flexiarg per security pattern
- Each pattern defines its tripwire, test, and response

### Task 2: Define Tripwire Detection
- Add `@tripwires` field to flexiarg format?
- Or separate tripwire registry?

### Task 3: Mechanism/Warrant Test Functions
- In compass, add scrutiny functions for BECAUSE
- Triggered only when tripwires fire

### Task 4: Escalation Protocol
- Integrate with eight-gates energy vocabulary
- Map tripwire → energy response

### Task 5: Xenotype Bridge
- Connect to futon5 xenotype layer
- Tripwire → xenotype activation
- Energy → global rule bending

## What Success Looks Like

1. Most patterns run with default trust (no overhead)
2. Tripwires catch fabrication templates quickly
3. Escalation quarantines rather than erases
4. BECAUSE clauses under scrutiny pass mechanism + warrant tests
5. The learning loop is protected even when individual patterns are suspect

## Ahimsa Constraint

> Harm must remain visible, bounded, and corrigible.

This is not about zero harm (impossible) but about:
- **Visible**: Costs are not externalized or hidden
- **Bounded**: Harm doesn't scale unboundedly
- **Corrigible**: Harm can be contested and corrected

Fabrication templates violate ahimsa by making harm invisible, unbounded, or uncorrigible.

## Open Questions

1. Should tripwires be pattern-specific or global?
2. How do we prevent security patterns from becoming fabrication templates themselves?
3. What's the relationship between energy weight and escalation severity?
4. How does the security layer interact with the meme layer (proposals, facts)?
