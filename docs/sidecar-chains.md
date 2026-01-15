# Sidecar Chains

## Chain scoring + softness accounting

Chain scoring ranks candidate chains by favoring typed arrows, then bridge triples, then proposals, while tracking how much fuzz entered the chain.

Scoring model (example weights):

- typed arrow hop: base +3, softness +0
- bridge triple hop: base +2, softness +1
- proposal hop: base +1, softness +2
- ungated sense shift: hard reject, or apply a heavy penalty (e.g. -5) and mark as invalid

Softness accounting:

- Sum the per-hop softness to get `softness_total`.
- Report per-hop evidence (arrow id, bridge id, or proposal id + evidence).
- Prefer lower `softness_total` at the same base score.

## Sense-shift gate

A sense-broaden or sense-narrow hop is only valid when:

- the hop is a typed arrow whose `mode` encodes the sense shift, or
- the hop is backed by an explicit bridge triple that justifies the shift.

If a hop implies a sense shift without either warrant, the chain is rejected or penalized as ungated.

## Example chain

Query: "ant colony" → target: "routing protocol"

| hop | link type | evidence | base | softness | gate |
| --- | --- | --- | --- | --- | --- |
| 1 | typed arrow | arrow:abstraction (ant colony → distributed swarm) | +3 | 0 | ok |
| 2 | bridge triple | bridge:bio-to-network (distributed swarm → distributed routing) | +2 | 1 | ok |
| 3 | proposal | proposal:ann-42 (distributed routing → routing protocol) | +1 | 2 | n/a |

Totals: base 6, softness_total 3. The chain is ranked below any chain with the same base and lower softness_total.
