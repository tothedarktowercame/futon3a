# Control Patterns (Security Layer)

Security patterns for detecting and handling fabrication templates.

## Core Insight

**Pattern templates are not self-certifying.**

The same formal structure (summary / context / if / however / then / because / next-steps) can host either:
- A genuine design pattern coupled to feedback and review
- A fabrication template that produces action while blocking learning

This is not anti-pattern (a bad pattern) but **anti-patterning** (an attack on the capacity to pattern).

## Architecture

```
Layer 1: Everyday patterns ──────────── Default trust
                │
                ▼
Layer 2: Tripwires ──────────────────── Passive monitoring
                │ (fires when invariant breaks)
                ▼
Layer 3: Security patterns ──────────── Active escalation
                │
        ┌───────┼───────┐
        ▼       ▼       ▼
    Quarantine  Test   Release/Reject
```

## Tripwires

| Tripwire | Detection Signal |
|----------|------------------|
| harm-is-external | Cost assigned outside pattern boundary |
| dissent-is-threat | Critique reframed as attack |
| self-sealing-logic | Evidence reinterpreted to confirm |
| escalate-on-failure | Doubling down when wrong |
| exit-suppression | Cannot withdraw or disengage |
| review-blocking | Audit channels closed |

When a tripwire fires, escalation begins.

## Primary Security Patterns (四正)

| Pattern | Energy | Action |
|---------|--------|--------|
| [ward-off-boundary](ward-off-boundary.flexiarg) | Péng 掤 | Establish boundary, quarantine |
| [roll-back-hold](roll-back-hold.flexiarg) | Lǚ 捋 | Hold without adopting |
| [press-mechanism](press-mechanism.flexiarg) | Jǐ 擠 | Demand mechanism test |
| [push-warrant](push-warrant.flexiarg) | Àn 按 | Force warrant test |

## Escalation Flow

```
Tripwire fires
      │
      ▼
ward-off-boundary ─────── Quarantine pattern
      │
      ▼
roll-back-hold ─────────── Study without adopting
      │
      ├──► press-mechanism ─── Is BECAUSE causal or decorative?
      │           │
      │           ▼
      └──► push-warrant ────── Does mechanism warrant adoption?
                  │
                  ├──► Pass: Release from quarantine
                  │
                  └──► Fail: Escalate to secondary energies
```

## Two Tests

### Mechanism Test (press-mechanism)
> Is the BECAUSE actually explaining what causes what?

- Decorative BECAUSE: could justify any THEN equally well
- Genuine BECAUSE: constrains which outcomes follow

### Warrant Test (push-warrant)
> Even if the BECAUSE explains attraction, does it warrant adoption?

- Explanation ≠ Warrant
- Must pass ahimsa test: harm visible, bounded, corrigible

## Secondary Security Patterns (四隅)

For exceptional escalation when primary patterns are insufficient:

| Pattern | Energy | Action |
|---------|--------|--------|
| [pluck-extract](pluck-extract.flexiarg) | Cǎi 採 | Extract suspect component |
| [split-isolate](split-isolate.flexiarg) | Liè 挒 | Separate pattern from context |
| [elbow-immediate](elbow-immediate.flexiarg) | Zhǒu 肘 | Close-range intervention |
| [lean-commit](lean-commit.flexiarg) | Kào 靠 | Full escalation, final resort |

## Ahimsa Constraint

> Harm must remain visible, bounded, and corrigible.

This is the legitimacy test for any pattern:
- **Visible**: Those harmed know they are harmed
- **Bounded**: Harm does not scale unboundedly
- **Corrigible**: Those harmed can contest and seek correction

Fabrication templates violate ahimsa by making harm invisible, unbounded, or uncorrigible.

## Connection to Xenotypes

In futon5 terms, these security patterns are the **xenotype layer**:
- Local exotypes: everyday pattern execution (trust by default)
- Xenotypes: security/escalation layer (dormant until tripwire)

The xenotype "bends" all local exotypes when activated, enforcing the security posture across the system.

## Implementation Status

- [x] Mission 2a plan (`docs/compass-mission-2a-plan.md`)
- [x] Primary security patterns (4)
- [x] Secondary security patterns (4)
- [x] Tripwire detection functions (`src/futon/compass_security.clj`)
- [x] Mechanism test implementation (`src/futon/compass_security.clj`)
- [x] Warrant test implementation (`src/futon/compass_security.clj`)
- [x] Integration with compass (`security-adjusted-preferences`)
- [x] Integration with futon5 xenotype layer (`security-event->xenotype-activation`)
