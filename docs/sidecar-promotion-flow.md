# Sidecar Promotion Flow

## Proposal lifecycle

- Proposed: fuzzy output is logged in SQLite as a proposal with score, method, and evidence payload.
- Rejected: reviewers record a rejection decision; the proposal remains in the ledger for audit.
- Promoted: reviewers append a promotion record that names the target fact and rationale.

## Promotion logging and audit trail

- Promotions are immutable, append-only records that reference the proposal_id.
- Decisions are logged with who/why/where metadata so reviewers can replay the trail.
- Promotions never mutate prior proposals; any correction is a new decision record.

## Store responsibilities (XTDB vs SQLite vs ANN)

- XTDB: authoritative facts only (materialized from promoted decisions, e.g., bridge_triples).
- SQLite: proposals, promotions, and their audit logs; this is the inspectable, grounded ledger.
- ANN/HNSW: similarity recall only; it seeds proposals but never writes facts directly.

## Example proposal with evidence

Proposal (SQLite proposal ledger):
- proposal_id: prop-2024-0217-01
- kind: arrow
- target_id: arrow-88
- status: pending
- score: 0.82
- method: ann/hnsw
- evidence_json: {"neighbor_ids":["ent-9","ent-11"],"distance":0.18}

Promotion decision (SQLite promotions + audit log):
- promotion_id: prom-2024-0217-02
- proposal_id: prop-2024-0217-01
- promoted_kind: bridge_triple
- target_id: bridge-2024-0217-03
- decided_by: reviewer:jo
- rationale: "Validated in source doc 3; matches ontology"
- promotion_log: actor=reviewer:jo, decision=accepted, source=manual

Materialized fact (XTDB bridge_triples):
- bridge_id: bridge-2024-0217-03
- subject_entity_id: ent-9
- predicate: derived_from
- object_entity_id: ent-11
- rationale: "promoted from prop-2024-0217-01"
- status: active

Evidence: aligns with the proposal/promotion/audit invariants in docs/sidecar-promotions.md and docs/sidecar-audit-flow.md.
