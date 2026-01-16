# Compass Mission 3a Plan: Devmap Enrichment

Goal: enrich devmap-only patterns with preference facets so compass scoring works
even when no flexiarg is present.

## Scope

- Target IDs like `f3/p4` that live in `holes/futon*.devmap`.
- Parse devmap blocks and map fields into the same facets used by compass:
  - `:then` ← `then` or `conclusion` or `claim`
  - `:if`, `:however`, `:because`, `:context` ← same labels in devmap

## Implementation (futon3a)

- Extend `futon.notions/get-pattern-details` to fall back to devmap parsing
  when flexiarg lookup returns nil.
- Reuse the devmap header format:
  `! instantiated-by: Prototype N — Title [x/y]`
- Parse `+ label: text` clauses into a map and lift the first value per label.

## Validation

- Run `futon.compass` on a devmap-heavy query (e.g. `f3/p10` in results).
- Confirm `:desired` and `:obstacles` populate via devmap fields.
- Note any patterns with missing fields to inform later authoring.

## Follow-on Ideas

- Capture multiple clause lines per label (not just the first) to improve signal.
- Record provenance per field (line number + source file) for audit.
