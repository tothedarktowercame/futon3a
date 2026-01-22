# Portal and Sidecar Interop

Portal is the query/eval surface. Sidecar is the append-only audit and logging surface.
This separation keeps guidance flexible while logs remain authoritative.

## What portal does

- Connects to Drawbridge and evaluates Clojure.
- Fetches pattern candidates from Futon1.
- Offers `suggest`, `propose`, and `promote` helpers for sidecar logging.
- Provides a stable CLI for query and pattern retrieval.

## What sidecar does

- Records selections, actions, evidence, promotions, and chains.
- Enforces validation and append-only rules at write-time.
- Provides per-id timelines and audit trails.

## Storage roles (tri-store)

- Vector/ANN index: fast recall only; it seeds proposals but never writes facts.
- SQLite ledger: inspectable proposals, promotions, evidence, and audit logs.
- XTDB: authoritative facts materialized only through explicit promotion.

## How they work together

1) Portal fetches intent and pattern guidance.
2) The agent performs work in any repo.
3) Portal can log proposals and promotions; sidecar records selections, actions, and evidence.
4) Audits and timelines are queried from sidecar.

## Example workflow

1) Get guidance

```
scripts/portal suggest "building community" --limit 5
scripts/portal patterns get vsatlas/some-pattern-id
```

2) Record a proposal and promotion

```
scripts/portal propose prop-001 --kind proposal --target vsatlas/some-pattern-id --score 0.72 --method ann
scripts/portal promote prom-001 prop-001 --kind proposal --decided-by reviewer:jo --rationale "validated in source"
```

3) Record actions and evidence

```
scripts/musn-action <session-id> vsatlas/some-pattern-id implement "implemented feature"
scripts/musn-evidence <session-id> vsatlas/some-pattern-id path/to/file "evidence note"
```

4) Inspect the audit trail

```
scripts/sidecar-audit timeline prop-001
scripts/sidecar-audit failures-by-type prop-001
```

## Worked example across the tri-store

This example shows the intent of the three stores working together. It assumes
the backing adapters exist; if they are mocked/in-memory, treat the checks as
conceptual verification.

1) Recall (vector/ANN)

- Run a portal suggest query to simulate ANN recall.

```
scripts/portal suggest "climate treaty market" --limit 3
```

Expected: a shortlist of candidate ids (recall only, no facts written).

2) Propose (SQLite ledger)

- Record a proposal seeded by recall.

```
scripts/portal propose prop-100 --kind proposal --target vsatlas/kyoto-protocol --score 0.63 --method ann
```

Expected: a proposal entry appears in the ledger (inspectable, non-authoritative).

3) Promote (XTDB facts)

- Record a promotion decision for the proposal.

```
scripts/portal promote prom-100 prop-100 --kind proposal --decided-by reviewer:joe --rationale "validated in source"
```

Expected: a promotion record exists in the ledger; a fact is eligible to be
materialized in XTDB via explicit promotion.

4) Audit (sidecar)

- Query timelines to see the cross-store story.

```
scripts/sidecar-audit timeline prop-100
```

Expected: timeline shows proposal and promotion events, plus any actions/evidence
you recorded during the work.

## Developer notes

- Portal should not write facts directly; promotion remains explicit.
- Sidecar should not be used for fuzzy recall; keep that in the query layer.
- When adding portal commands, document which sidecar events they emit.
