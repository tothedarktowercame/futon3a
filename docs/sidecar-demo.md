# Sidecar Demo Script

This is a short, interactive demo script that exercises the sidecar flow.
Use it to sanity-check logs, validation, and audit timelines.

## Setup

1) Start Drawbridge and open a portal session.
2) Ensure the sidecar store uses a clean log directory.

Suggested environment:
- `SIDECAR_LOG_ROOT=log/demo`
- `PORTAL_URL=http://127.0.0.1:6767/repl`

## Demo steps

1) Start a session
- Create a session with a simple intent.

2) Query patterns
- List patterns and select one candidate to log.

3) Record a proposal
- Create a proposal with required fields.

Expected: proposal recorded, timeline shows `:proposal/recorded`.

4) Record a promotion
- Promote the proposal using its id.

Expected: promotion recorded, timeline for the proposal shows `:promotion/recorded`.

5) Materialize a fact
- Write a fact using the promotion id.

Expected: fact recorded, timeline for the proposal shows `:fact/materialized`.

6) Attach evidence
- Attach evidence to the proposal and promotion.

Expected: evidence events show up in both timelines.

7) Build a chain
- Build a chain with arrow, bridge, and proposal steps.

Expected: chain stored, softness totals/average recorded, timeline for the proposal includes `:chain/built`.

8) Trigger a failure
- Attempt a duplicate proposal id or a promotion without a proposal.

Expected: failure recorded in audit log and returned by the failure-reasons query.

## Cleanup

- Review `SIDECAR_LOG_ROOT` for audit entries.
- Remove the demo log directory if needed.
