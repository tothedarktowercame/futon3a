# Sidecar Overview

## Tri-Store Separation

The sidecar stack splits semantic data into three stores with distinct trust levels:

- Facts live in XTDB and represent authoritative, curated semantics.
- Memes live in SQLite and hold inspectable, grounded artifacts and proposals.
- Notions live in ANN/HNSW and provide fast fuzzy recall only.

Core invariants:

- Similarity results never write to facts directly; they only seed proposals.
- Facts are written only through explicit promotion, not as a side effect of search.
- Boundaries between stores are enforced by adapters so fuzzy output cannot become authoritative without review.

## Proposal Ledger Invariants

All fuzzy outputs are recorded as proposals with evidence in SQLite. Each proposal must include:

- The candidate ids returned by ANN search.
- The score or distance used to rank the candidate.
- The method and evidence payload that justify the proposal.

Core invariants:

- Fuzzy neighbors are proposals, not facts.
- Every proposal is attributable to a method and evidence payload for audit.
- Promotion to facts is an explicit, separate action after review.
