# futon3a

## Problem this solves

Provide a simple way to:
- Supply guidance to an agent from a shared pattern store.
- Record the agent's selections, actions, and evidence in a structured log.
- Keep the execution layer flexible (CLI, Drawbridge, editor), while guidance and logging stay consistent.
Logged actions are intended as meaningful summaries, not constraints on CLI activity.

## What it is

A small Clojure core with:
- A minimal in-process MUSN state machine (`src/musn/core.clj`).
- A Drawbridge helper (`scripts/portal`) for eval and pattern queries.
- Simple CLI wrappers that call the core via Drawbridge.

There is no HTTP server in this repo. All interaction is via Drawbridge.
Drawbridge keeps querying as a first-class operation (not limited to pre-made endpoints).

## Portal and sidecar interop

Portal is the query/eval surface; sidecar is the append-only audit surface.
Portal retrieves pattern guidance and intent, while sidecar records selections, actions,
evidence, promotions, and chains. See `docs/portal-interop.md` for the developer view.

## User story (plain)

1. The user asks the agent to retrieve relevant patterns using `portal`.
2. The agent queries patterns from Futon1 (XTDB) and uses them as the specification for work.
3. The agent completes work in any repo.
4. The agent logs selections, actions, and evidence back to MUSN.
5. The agent ends the turn and leaves a structured log.

This can be used in interactive or autonomous workflows without changing MUSN.

## AIF stance (initial)

- Patterns are treated as priors, with confidence that can change based on observed runs.
- Portal can help compute AIF signals from real usage logs.
- AIF does not drive agent actions by default; it can inform guidance when signals are strong.

## Design patterns (next steps)

The sidecar architecture is captured as flexiarg patterns in `library/sidecar/*.flexiarg`.
Use these as the plan for next steps; each pattern includes rationale and concrete next-steps.

## Core contract

Inputs from MUSN:
- `intent` (typically stored in Futon1 and used to find patterns)
- `candidates` (pattern IDs)
- optional `candidate-details`
- optional `constraints`

Outputs to MUSN:
- selections (`select!`)
- actions (`action!`)
- evidence (`evidence!`)
- turn end (`end-turn!`)

All outputs are appended to `log/<session>.edn` (override with `MUSN_LOG_ROOT`).

## Pattern guidance source

Portal queries Futon1's pattern registry at:

`http://localhost:8080/api/alpha/patterns/registry`

Set `PORTAL_FUTON1_URL` or `MUSN_FUTON1_URL` to override.

Scoring is simple token matching against pattern IDs and names. This can be replaced later.

## Pattern indexing (notions store)

Futon3a's fuzzy recall expects a local notions index derived from the Futon3
library patterns. Build it with:

```
scripts/index_patterns.sh
```

See `docs/pattern-indexing.md` for optional GloVe, fastText, and MiniLM embeddings.

## Usage

Start Drawbridge (local):

```
export ADMIN_TOKEN=...     # or place token in .admintoken
scripts/musn-repl
```

Create a session and start a turn:

```
scripts/musn-session "draft a plan for vsatlas"
```

Query patterns:

```
scripts/portal patterns list --limit 8
scripts/portal patterns list --namespace vsatlas --limit 10
scripts/portal patterns search "building community" --limit 8
scripts/portal patterns get vsatlas/some-pattern-id
```

Portal helpers for sidecar logging:

```
scripts/portal suggest "building community" --limit 8
scripts/portal propose prop-001 --kind proposal --target vsatlas/some-pattern-id --score 0.72 --method ann
scripts/portal promote prom-001 prop-001 --kind proposal --decided-by reviewer:jo --rationale "validated in source"
```

Log selections and actions:

```
scripts/musn-select <session-id> vsatlas/pattern-id "reason for selection"
scripts/musn-action <session-id> vsatlas/pattern-id implement "what you did"
scripts/musn-evidence <session-id> vsatlas/pattern-id path/to/file "evidence note"
```

End the turn:

```
scripts/musn-end <session-id> "summary note"
```

## Environment variables

- `ADMIN_TOKEN` or `.admintoken`: Drawbridge auth token.
- `PORTAL_URL`: Drawbridge URL (default `http://127.0.0.1:6767/repl`).
- `PORTAL_FUTON1_URL` / `MUSN_FUTON1_URL`: Futon1 API base.
- `PORTAL_FUTON1_PROFILE` / `MUSN_FUTON1_PROFILE`: Futon1 profile header.
- `MUSN_LOG_ROOT`: log directory (default `log`).
- `MUSN_PATTERN_LIMIT`: candidate limit (default 4).

## Files

- `src/musn/core.clj`: in-process MUSN contract + logging.
- `src/musn/portal.clj`: Drawbridge eval + pattern queries.
- `src/repl/http.clj`: Drawbridge server helper.
- `scripts/portal`: CLI entrypoint for Drawbridge + pattern queries.
- `scripts/musn-*`: thin wrappers around the core functions.
