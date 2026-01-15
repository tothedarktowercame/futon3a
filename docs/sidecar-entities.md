# Sidecar Entities and Arrows

## Entities, aliases, and mentions

Artifacts are the raw inputs (documents, logs, code snippets) that get hashed and stored.
Mentions are span-level extractions within artifacts; they preserve the exact text and location.
Entities are the canonical nodes the system reasons about. Aliases connect variant surface forms
back to a single entity to support deterministic lookup and stable chain building.

## Typed Kolmogorov arrows

Arrows represent semantic transforms between entities. Each arrow carries a mode
(e.g., translation, abstraction, metonymy), a payload, scope tags, a confidence value,
and a status lifecycle (draft, active, retired). Explicit arrows make chains inspectable
and allow the system to score transformations rather than infer them implicitly.

## Tri-store separation

Facts, memes, and notions live in distinct stores with different trust levels: facts in XTDB,
memes (artifacts, entities, aliases, mentions) in SQLite, and notions in ANN/HNSW.
ANN results propose candidates only; explicit promotion is required before a fact is written.

## Example chain

Artifact "brief-2025-01.md" (hash `a19c...`) contains the phrase "Control Plane".
That span becomes a mention linked to alias "control plane".
The alias resolves to entity `entity:control-plane`.
A typed arrow `entity:control-plane` --(abstraction, confidence 0.78, active)--> `entity:platform-governance`.

This chain is explicit: artifact -> mention -> entity -> arrow, and it respects the tri-store
boundary by keeping the mention/entity in SQLite while the arrow is evaluated for promotion.
