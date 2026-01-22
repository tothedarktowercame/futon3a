# Sidecar Usage

This guide covers common, interactive workflows for the sidecar system once it is running.
It assumes Drawbridge + portal are available and the sidecar store is reachable.

## Core flow (happy path)

1) Start a session with an intent.
2) Query patterns and select one or more candidates.
3) Perform work and log a summary action.
4) Attach evidence.
5) End the turn.

Expected outcome:
- A log entry for each step.
- A per-id timeline that includes selections, actions, evidence, and turn end.

## Validation gate checks

Goal: confirm invalid writes are rejected and audited.

- Missing required fields (proposal/promotion/evidence/action/fact) should fail validation.
- Promotion without an existing proposal should fail a boundary check.
- Direct fact writes without promotion should fail a boundary check.

Expected outcome:
- Each invalid write produces an audit entry with a failure reason.

## Append-only enforcement

Goal: confirm duplicates are rejected.

- Re-submit a proposal, action, or fact with the same id.

Expected outcome:
- Second write is rejected.
- Audit log shows an append-only violation for that id.

## Promotion linkage

Goal: confirm per-id timelines link related records.

- Record a proposal, then promote it, then materialize a fact.
- Query timeline by proposal id.

Expected outcome:
- Timeline shows proposal, promotion, and fact events in order.

## Evidence targeting

Goal: confirm evidence can target multiple entities.

- Attach evidence to a proposal and a promotion.
- Query timeline by each id.

Expected outcome:
- Evidence events appear in each target timeline.

## Chain building and softness

Goal: confirm chain scoring and gating.

- Build a chain with arrow, bridge, and proposal steps.
- Confirm softness totals/averages match the documented weights.

Expected outcome:
- The chain is stored with per-step softness.
- Ungated sense shifts are rejected and audited.

## Audit queries

Goal: confirm audit visibility for debugging.

- Query per-id timelines.
- Query failure reasons for a given id.

Expected outcome:
- Timelines contain success + failure entries.
- Failure reasons include event type and validation/boundary category.
