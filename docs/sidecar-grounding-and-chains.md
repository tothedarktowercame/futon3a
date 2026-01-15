# Sidecar Grounding and Chains

This note connects grounding (artifacts, entities, aliases, mentions) to query candidates and shows how typed arrows plus softness scoring rank chain outputs.

## Grounding to Query Candidates

1. Artifacts are the stable inputs (documents, logs, transcripts) ingested and hashed.
2. Entities are canonical records extracted from artifacts.
3. Aliases map surface forms to entities for lexical expansion.
4. Mentions record spans in artifacts that point to entities or aliases.

Query candidates are seeded by matching mention spans and alias expansions against the incoming query text. The candidate set is then enriched with entity-linked artifacts so every candidate can be traced back to a specific artifact and mention span.

## Typed Arrows and Softness Scoring

Chain assembly treats each step as a typed arrow with mode, scope, confidence, and status. Chains are scored by multiplying or aggregating arrow confidences, with optional softness penalties when a step is weakly grounded (e.g., metonymy or abstraction). Soft hops are still allowed but ranked lower unless supported by nearby hard evidence.

## Example Chain (One Soft Hop)

- Artifact: "incident_report_2024-05-12.md"
- Mention: "satellite outage" -> Entity: INCIDENT-742
- Arrow A (hard): INCIDENT-742 --(caused-by, confidence=0.92)--> ENTITY: WEATHER-SOLAR-STORM
- Arrow B (soft): WEATHER-SOLAR-STORM --(metonymy, confidence=0.41, SOFT)--> ENTITY: SPACE-WEATHER
- Arrow C (hard): SPACE-WEATHER --(classifies, confidence=0.88)--> ENTITY: SYSTEMIC-RISK

The soft hop (Arrow B) is retained but reduces the overall chain score compared to a chain with only hard hops.
