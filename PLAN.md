# futon3a implementation plan (draft)

## Goal

Provide a small, reliable pipeline where:
- Guidance (pattern candidates) comes from Futon1.
- Work happens in any repo (CLI/Emacs/Drawbridge).
- Selections, actions, and evidence are logged in MUSN.
The logged actions should be meaningful summaries of real work, not a constraint on CLI activity.

## Current state

- `musn.core` implements sessions, turns, selections, actions, evidence, and end logs.
- `portal` can eval Clojure via Drawbridge and query Futon1 patterns.
- CLI helpers exist (`musn-session`, `musn-select`, `musn-action`, `musn-evidence`, `musn-end`).

## Design pattern guidance

The sidecar architecture and rationale are captured as flexiarg patterns in
`library/sidecar/*.flexiarg`. Treat those patterns as the plan of record for
next steps, and update them as implementation details become clearer.

## Implementation plan

1) Tighten the core contract
- Decide on required fields for `select!`, `action!`, `evidence!`, `end-turn!`.
- Decide on log shape stability (field names + event types).
- Add minimal validation (reject missing session, missing pattern-id).

2) Portal query improvements
- Treat intent as data stored in Futon1, not just a prompt string.
- Expand query modes beyond simple token matching:
  - namespace filter
  - keyword matching across pattern metadata
  - optional score explanations
- Add an option to return candidate details (summary, sigils) when present.

3) Guidance format
- Define how guidance is presented to the agent:
  - candidates list
  - optional constraints
  - short instruction text
- Keep it compatible with Drawbridge + CLI usage.

4) Logging conventions
- Specify canonical action values: read, implement, update, discover.
- Specify evidence format: file paths, timestamps, notes.
- Emphasize that actions are reflective summaries, not enforcement of CLI behavior.
- Ensure every command wrapper logs a clear entry.

5) Runner (optional helper)
- Add a lightweight wrapper that runs: start -> select -> action -> evidence -> end.
- Keep it optional; users can call commands manually.

6) Smoke test
- One minimal run:
  - `portal patterns search "building community"`
  - `musn-session` with a simple intent
  - `musn-select`, `musn-action`, `musn-evidence`, `musn-end`
  - verify `log/<session>.edn` contents

## Integration points

- Futon1 API:
  - `GET /api/alpha/patterns/registry`
  - optional profile header

- Drawbridge:
  - `scripts/musn-repl` starts Drawbridge locally
  - `scripts/portal` is the entrypoint for eval and pattern queries
  - Querying stays first-class by evaluating directly against the running JVM, not a fixed set of endpoints

## Non-goals (for now)

- No HTTP server in futon3a.
- No enforcement layer for tool usage.
- No AIF-driven control of agent behavior by default.

## Follow-ups (later)

- AIF scoring based on observed runs (patterns treated as priors).
- Optional AIF-based steering only when signals are strong and stable.
- Rich pattern evidence updates (push back to Futon1).
- Better search (full-text or vector).
