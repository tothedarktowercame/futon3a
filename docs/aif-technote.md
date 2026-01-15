# AIF Technical Note: Governance vs Control

## The GitHub Analogy

GitHub issues and commits don't *control* human programmers:
- You can close an issue without fixing it
- You can commit without referencing a ticket
- You can merge without approvals (if you have access)

Yet GitHub still provides *governance*:
- **Visibility**: everyone can see what happened
- **Structure**: commits have diffs, issues have bodies
- **Audit trail**: history is append-only and attributable
- **Social pressure**: norms emerge from visibility, not enforcement

The system creates accountability through transparency, not gates.

## AIF Design Spectrum

```
Control ←————————————————————————————→ Governance

"Deviation blocks       "Deviation is logged,      "No AIF,
 auto-use without        suggestion surfaced,       just append
 justification"          nothing blocked"           logs"

     futon3                  futon3a                 raw EDN
   (current)                (target)                  logs
```

### Control Mode (futon3 current implementation)

- `detect-deviation` checks if agent's choice differs from AIF suggestion
- Unjustified deviations block `auto-use-policy`
- HUD instructs agent to "provide a deviation reason"
- Abstain policy prevents auto-selection when τ < threshold

This is useful for high-stakes workflows where drift is costly, but it's heavy-handed for exploratory work.

### Governance Mode (lighter approach)

AIF still computes and surfaces:
- G scores for candidates
- Softmax probabilities over -G/τ
- A suggested pattern with confidence
- τ (precision) reflecting uncertainty

But enforcement is removed:
- Deviations are logged, not gated
- No "unjustified deviation" concept — all deviations are valid
- Abstain surfaces uncertainty but doesn't prevent selection
- Auto-use proceeds regardless of deviation

The audit trail captures the delta between suggestion and choice. Humans review logs to spot patterns of drift, not the system blocking in real-time.

## When to Use Which

**Control mode** (current futon3):
- Agent is operating with significant autonomy
- Mistakes are expensive to reverse
- Trust is being established
- Compliance/audit requirements are strict

**Governance mode** (futon3a direction):
- Agent is collaborating with human in the loop
- Exploration is valued over exploitation
- Rapid iteration matters more than perfect choices
- AIF signals are informative, not prescriptive

## Implementation Notes

### Sampling (shared)

Both modes benefit from proper softmax sampling:

```
P(pattern_i) = exp(-G_i / τ) / Σ exp(-G_j / τ)
```

- Low τ → greedy (best G dominates)
- High τ → exploratory (probability spreads)
- Deterministic seeding from (session-id, turn, candidates)

The sampling math is useful regardless of enforcement — "68% confidence" is valuable signal even if nothing is blocked.

### Logging (shared)

Both modes should log:
```clojure
{:event/type :turn/select
 :pattern/id "agent/scope-before-action"
 :aif/suggested "agent/trail-enables-return"
 :aif/suggested-prob 0.68
 :aif/tau 0.72
 :aif/deviated? true}
```

The difference is what happens *after* logging.

### Enforcement (control mode only)

Control mode adds:
```clojure
(when (and deviated? (not justified?))
  (swap! state assoc :deviation {:unjustified true ...})
  ;; Later: auto-use-policy checks this and blocks
  )
```

Governance mode omits this — the log entry is the entire effect.

## Migration Path

To move futon3 toward governance mode:

1. Keep all AIF computation (G, τ, sampling, probabilities)
2. Keep all logging (suggestions, deviations, confidence)
3. Remove enforcement in `auto-use-policy`
4. Remove "provide a deviation reason" instruction from HUD
5. Make abstain advisory (log it, don't prevent selection)

The result: full AIF instrumentation with zero blocking. Humans and downstream analysis consume the logs; the agent is never prevented from acting.

## Relation to futon3a

futon3a starts from a simpler baseline:
- No AIF computation yet
- Actions are "meaningful summaries, not constraints"
- Drawbridge-based querying (first-class queries)

AIF can be added to futon3a following governance mode:
- Compute suggestions in `start-turn!`
- Log suggestion vs choice delta in `select!`
- Never block or require justification

This keeps futon3a's lightweight philosophy while adding the value of AIF signals.

## Summary

| Aspect | Control | Governance |
|--------|---------|------------|
| G/τ computation | ✓ | ✓ |
| Softmax sampling | ✓ | ✓ |
| Suggestion in HUD | ✓ | ✓ |
| Deviation logging | ✓ | ✓ |
| Justification required | ✓ | ✗ |
| Auto-use blocked | ✓ | ✗ |
| Abstain prevents selection | ✓ | ✗ |

The math is the same. The UX is different. Choose based on trust level and workflow needs.
