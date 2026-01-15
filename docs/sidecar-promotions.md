# Sidecar Promotions

## Flow

1. A proposal is recorded with evidence (score, method, evidence_json).
2. Reviewers decide whether to promote a proposal to a fact.
3. A promotion append is written with who decided, rationale, and target.
4. Fact materialization happens elsewhere; the sidecar only logs the decision.

## Invariants

- Proposals are append-only; corrections are new records, not updates.
- Promotions are append-only; a proposal can have multiple promotions.
- Every promotion references a proposal_id and records decided_by + rationale.
- Bridge triples are explicit warrants; retiring one means superseding it.

## Example

Proposal

- proposal_id: prop-2024-0217-01
- kind: arrow
- target_id: arrow-88
- status: pending
- score: 0.82
- method: ann/hnsw
- evidence_json: {"neighbor_ids":["ent-9","ent-11"],"distance":0.18}

Promotion

- promotion_id: prom-2024-0217-02
- proposal_id: prop-2024-0217-01
- promoted_kind: arrow
- target_id: arrow-88
- decided_by: reviewer:jo
- rationale: "Validated in source doc 3; matches ontology"

Bridge triple (optional warrant)

- bridge_id: bridge-2024-0217-03
- subject_entity_id: ent-9
- predicate: derived_from
- object_entity_id: ent-11
- rationale: "Curated bridge for cross-domain hop"
- created_by: reviewer:jo
- status: active
