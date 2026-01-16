# Sidecar Query Flow

## Stage 1: Notion recall (soft)

- Store: ANN/HNSW notions.
- Output: candidate ids + distances only.
- Rule: neighbors never write to facts; they only seed proposals.

## Stage 2: Meme grounding (inspectable)

- Store: SQLite memes/proposals.
- Output: proposals with evidence payloads, artifact links, and method metadata.
- Rule: proposals are inspectable and reviewable but still non-authoritative.

## Stage 3: Fact promotion (hard)

- Store: XTDB facts.
- Output: curated facts written only through explicit promotion.
- Rule: facts are never written as a side-effect of search.

## Softness scoring

Score each chain hop by source trust and transformation type:

- 0.0 = fact-to-fact arrow (typed, promoted).
- 0.5 = bridge triple grounded in an artifact (inspectable meme).
- 1.0 = proposal seeded by notion recall.

Chain softness = average hop softness. Any chain with average > 0.6 is flagged as soft and must show per-hop evidence.

## Sense-shift gate

Sense-broaden and sense-narrow steps are allowed only when:

- The hop is a typed arrow (explicitly labeled shift), or
- A bridge triple cites an artifact that justifies the shift.

Ungated sense shifts are rejected and logged as drift.

## Example chain

Query: "climate treaty market"

| Hop | From -> To | Type | Softness | Note |
| --- | --- | --- | --- | --- |
| 1 | "climate treaty" -> Kyoto Protocol | proposal (ANN) | 1.0 | Notion recall seeds a proposal. |
| 2 | Kyoto Protocol -> emissions trading | bridge triple | 0.5 | Grounded by a cited policy artifact. |
| 3 | emissions trading -> carbon market | typed arrow | 0.0 | Explicit policy-to-market relationship. |

Average softness: (1.0 + 0.5 + 0.0) / 3 = 0.5. Hop 1 is soft; hops 2-3 are grounded or hard.

Pattern use: sidecar/tri-store-separation, sidecar/sense-shift-gate.
