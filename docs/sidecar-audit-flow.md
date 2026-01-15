# Sidecar Audit Flow

## Flow overview

1. Proposals land in the proposal ledger (`proposals`) as fuzzy output.
2. A promotion decision records the explicit choice to mint a fact
   (`promotions` plus its append-only log metadata).
3. The promotion emits a stable fact for XTDB via bridge triples
   (`bridge_triples`), without mutating prior records.

## Append-only audit log

- The proposal ledger is append-only; proposals are never overwritten.
- Promotions are explicit and logged as immutable records with metadata
  for the decision context (who, why, where, and when).
- Bridge triples are immutable facts; superseding a triple creates a new
  record that points at the prior bridge entry.

## Concrete example

Proposal (fuzzy):
- `proposals`: proposal_id `prop-001`, kind `bridge`, target_id `ent-42`,
  status `pending`, score `0.82`, method `llm`, evidence_json contains
  source spans, created_at `2024-05-10T12:30:00Z`.

Promotion record:
- `promotions`: promotion_id `prom-001`, proposal_id `prop-001`,
  promoted_kind `bridge_triple`, target_id `ent-42`, decided_by `triage`,
  rationale `verified by curator`, created_at `2024-05-10T12:35:00Z`.
- `promotion_log`: promotion_log_id `promlog-001`, promotion_id
  `prom-001`, decision `accepted`, actor `curator-7`, source `manual`,
  run_id `run-9a2`, recorded_at `2024-05-10T12:35:05Z`.

XTDB fact (bridge triple):
- `bridge_triples`: bridge_id `bridge-001`, subject_entity_id `ent-17`,
  predicate `related_to`, object_entity_id `ent-42`, rationale
  `promoted from prop-001`, created_by `triage`, created_at
  `2024-05-10T12:36:00Z`, status `active`.
