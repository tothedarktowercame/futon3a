# Compass Mission 1 Results

Mission: calibrate compass outputs for contrasting narratives.
Method: MiniLM embeddings via `futon.compass` (using local `.venv`).

## Narrative A

Input: "I need to refactor legacy code safely"

Top patterns:
- `or/retirement-shelf` (0.360)
- `f4/p1` (0.314)
- `f6/p11` (0.292)
- `pacspine/obligations-checker` (0.291)
- `p4ng/legitimate-iteration` (0.290)

Policy scores:
- balanced: G=-0.498 (pragmatic 0.16, epistemic 1.00)
- exploit: G=-0.449 (pragmatic 0.08, epistemic 1.00)
- explore: G=-0.400 (pragmatic 0.00, epistemic 1.00)

Compass: direction = progressing, best policy = balanced.

## Narrative B

Input: "exploring new architecture without constraints"

Top patterns:
- `f4/p12` (0.411)
- `f3/p10` (0.399)
- `agent/budget-bounds-exploration` (0.392)
- `or/bridge-before-portal` (0.380)
- `vsatlas/three-layer-architecture` (0.380)

Policy scores:
- balanced: G=-0.079 (pragmatic 0.13, epistemic 0.00)
- exploit: G=-0.072 (pragmatic 0.12, epistemic 0.00)
- explore: G=-0.021 (pragmatic 0.03, epistemic 0.00)

Compass: direction = blocked, best policy = balanced.

## Narrative C

Input: "debugging a critical production issue"

Top patterns:
- `stack-coherence/stack-blocker-detection` (0.343)
- `f0/p8` (0.310)
- `f2/p11` (0.302)
- `contributing/stack-scan-logging-protocol` (0.286)
- `devmap-coherence/prototype-alignment-tension` (0.276)

Policy scores:
- balanced: G=-0.089 (pragmatic 0.15, epistemic 0.00)
- exploit: G=-0.082 (pragmatic 0.14, epistemic 0.00)
- explore: G=-0.023 (pragmatic 0.04, epistemic 0.00)

Compass: direction = blocked, best policy = balanced.

## Calibration Notes

- In these runs, balanced wins consistently because pragmatic scores are close and
  epistemic rarely differentiates (obstacle coverage stays at 0.0 unless
  obstacles are empty).
- When preference fields are missing (devmap-only patterns), desired/obstacle
  extraction is thin, which flattens policy discrimination.
- If embeddings fail, the compass falls back to keywords and can skew toward
  loosely related patterns; ensure the venv is discoverable or set
  `NOTIONS_PYTHON` explicitly.

## Policy Summary (3 sentences)

Balanced wins when both desired and obstacle signals exist but neither dominates
strongly, because it inherits concepts from both sides. Exploit wins when
pragmatic alignment is materially higher than epistemic coverage and the desired
future is well-specified. Explore wins only when obstacle coverage is
informationally valuable and desired alignment is weak or unknown.
