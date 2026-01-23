# Flexiarg Style Guide: Semantic Field Mapping

This document defines the semantic meaning of flexiarg fields and how the compass system interprets them. Pattern authors should follow these conventions to ensure correct behavior.

## Core Principle

Flexiarg fields map to a **preference model** that guides navigation decisions. The mapping is semantic, not mechanical—each field type carries specific meaning that affects how patterns influence agent behavior.

## Field Semantics

### IF → Scope / Preconditions

**What it means:** When does this pattern apply?

**Write IF as:** A condition that describes the situation where this pattern is relevant.

**Examples:**
- "The agent cannot proceed confidently without information or authorization it doesn't have."
- "The agent's outputs need to be verifiable by other agents, humans, or future selves."
- "A decision could carry material risk or irreversible impact."

**How the compass uses it:** IF conditions filter pattern applicability. They are NOT obstacles to overcome—they define context. A pattern whose IF condition doesn't match the current situation should carry less weight.

**Anti-pattern:** Don't write IF as a blocker ("The database is down"). That's a different concept—see Blockers below.

---

### HOWEVER → Risks / Failure Modes

**What it means:** What happens if you ignore this pattern?

**Write HOWEVER as:** A consequence or failure mode that occurs when the pattern is not followed.

**Examples:**
- "Assertions without anchors are indistinguishable from confabulation."
- "Without a trail, the agent cannot distinguish 'unexplored' from 'explored and rejected.'"
- "Rushing to act without sensing leads to blind thrashing; sensing without acting leads to analysis paralysis."

**How the compass uses it:** HOWEVER fields inform risk awareness. The epistemic score measures how many risks have been acknowledged (investigated, mitigated, or consciously accepted). Risks are not "removed"—they are acknowledged.

**Anti-pattern:** Don't write HOWEVER as a precondition ("If the user hasn't authenticated"). That belongs in IF.

---

### THEN → Desired Futures / Goals

**What it means:** What should happen if you follow this pattern?

**Write THEN as:** An actionable outcome or behavior to adopt.

**Examples:**
- "Require every substantive claim to point to evidence: a file path, a trace event, a test result."
- "Emit an explicit `await-input` event with what's needed and why."
- "Maintain a versioned audit log of claims, annotated with their evidence status."

**How the compass uses it:** THEN fields define desired futures. The pragmatic score measures alignment between the agent's state and these desired outcomes.

---

### NEXT-STEPS → Concrete Actions

**What it means:** Specific tasks to implement the pattern.

**Write NEXT-STEPS as:** A bulleted list of actionable items.

**Examples:**
```
- Identify claim types in your agent's output (facts, completions, diagnoses).
- For each type, define what counts as adequate evidence.
- Add validation that flags claims missing their evidence anchors.
```

**How the compass uses it:** Combined with THEN to form the complete set of desired futures.

---

### BECAUSE → Rationale

**What it means:** Why does this pattern matter?

**Write BECAUSE as:** The underlying reasoning or principle.

**Examples:**
- "Evidence transforms agent output from 'trust me' to 'check this.'"
- "Coordination is not free: it takes time, consumes attention, introduces latency."

**How the compass uses it:** Rationale is preserved for audit trails but does not directly affect scoring. It helps humans understand why a pattern was recommended.

---

## Preference Model Summary

| Flexiarg Field | Preference Model Key | Semantic Role | Simulation Use |
|----------------|---------------------|---------------|----------------|
| IF | `:scope` | Applicability conditions | Filter relevance |
| HOWEVER | `:risks` | Failure modes / consequences | Track acknowledgment |
| THEN | `:desired` | Target outcomes | Goal alignment |
| NEXT-STEPS | `:desired` | Concrete actions | Goal alignment |
| BECAUSE | `:rationale` | Justification | Audit only |
| hotwords | `:concepts` | Key terms | Concept matching |

## GFE Scoring

The compass uses a Generalized Free Energy-inspired score:

```
G = -(0.6 × pragmatic + 0.4 × epistemic)
```

Where:
- **Pragmatic** = concept overlap between current state and desired futures
- **Epistemic** = fraction of risks that have been acknowledged

Lower G is better (like free energy minimization).

## What About Actual Blockers?

The current flexiarg format doesn't have a dedicated field for blockers (external obstacles that prevent progress). If you need to express a blocker:

1. **Don't use IF**—that's for applicability, not blockage
2. **Don't use HOWEVER**—that's for consequences, not current state
3. Consider whether it belongs in pattern content at all, or in session state

Future versions may add a BLOCKED-BY field for explicit blockers.

## Common Mistakes

### Mistake 1: IF as obstacle
```
# Wrong
+ IF: The team hasn't agreed on the API contract.

# Right
+ IF: The agent needs to integrate with external services.
```

### Mistake 2: HOWEVER as precondition
```
# Wrong
+ HOWEVER: This only works if you have write access.

# Right
+ HOWEVER: Without write access verification, the agent may attempt
  operations that fail silently or corrupt state.
```

### Mistake 3: THEN as explanation
```
# Wrong
+ THEN: This is important because it prevents data loss.

# Right
+ THEN: Checkpoint state before destructive operations. Verify
  checkpoint integrity before proceeding.
```

## Testing Your Pattern

Run your pattern through the compass to verify the preference model extraction:

```clojure
(require '[futon.notions :as n])
(require '[futon.compass :as c])

;; Search for your pattern
(def patterns (n/enrich-results (n/search "your pattern keywords")))

;; Extract preferences
(def prefs (c/extract-preferences patterns))

;; Check the mapping
(:scope prefs)    ; Should be applicability conditions
(:risks prefs)    ; Should be failure modes
(:desired prefs)  ; Should be goals and actions
```

If your IF content appears in `:risks` or your HOWEVER content appears in `:scope`, review this guide and adjust your pattern.
