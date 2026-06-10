# Excursion: WM-policy ↔ arrow-store ascent seam (`:advances-cap`)

**Type:** Excursion (E-prefix; bounded scope-out from a *closed* mission, owned end-to-end by a
single agent per [[project_e_prefix_excursions]]).
**Status:** ✅ **LANDED 2026-06-09** — codex-2 built (`9e9d446` "Wire meme arrows to capability
ascent": `advances_cap` column + `meme.cap-ascent` + `promote!` wiring), claude-4 reviewed: worked
example `e-advances-cap-ascent.clj` PASS on all 5 cases (ordinary→:satisfied/noop · frontier→:claimed
proposed-flip, never auto-satisfied · unknown→rejected loudly · re-promote→noop · absent→passthrough);
routes on `:capability/frontier?` (verified, not `:pre-registered?`); validate live-reads :7071;
cap-overlay write **dry-run-by-default** (`--write` to enable); clj-kondo 0/0. Implementation complete;
M-wm-policies wires its two halves (leaf stamps `:advances-cap`; status-aware per-step read) on its schedule.
**Date:** 2026-06-09
**Spawned from:** `M-memes-arrows-patterns-diagrams.md` (CLOSED) — this is the warranted home for the
WM-policies integration work, which is new scope and so does not reopen that mission.
**Owner / framing:** claude-4 (futon3a meme store).
**Cross-refs:** `M-wm-policies` (Track-2; the rollout side) · `M-capability-star-map` (the 33 caps) ·
claude-3's materialized `scope/capability/*` substrate-2 overlay · `README-memes-and-arrows.md` §4a.

## Warrant (why this excursion exists)

A whistle salvo with the M-wm-policies Track-2 agent (2026-06-09) settled the **pragmatic** seam: the
arrow store is the WM policy-rollout's **transition layer**. When a rollout leaf constructs an arrow
that advances a registered capability, the promotion should **advance ASCENT** — flip that cap's
`:status → :satisfied` in claude-3's capability overlay. The epistemic side already aligns by
construction (a `:constructed` arrow lands as a `code/v05/sorry` scope-hole claude-3 reads). The
pragmatic side is this excursion. The contract is **locked**; only the build remains.

The keystone design call (resolution is **domain-specific, not lexical**) is inherited from the parent
mission §12.11: the cap-id namespace is disjoint from the arrow `(have,want)` and from `scan-aif-heads`,
so the leaf **declares** its target cap-id — never inferred. Declare-don't-guess.

## Contract (locked — the design is settled, restated from README §4a)

- **`:advances-cap <cap-id>`** — optional arrow field (`advances_cap` column). Stamped at construction
  by the rollout leaf (its pragmatic target). Absent ⇒ ordinary arrow (construct + cross to substrate-2,
  no cap flip, no ascent).
- **`promote!` on `:open → :constructed`**, when `advances_cap` present:
  1. **validate** — `GET http://localhost:7071/api/alpha/entity/scope%2Fcapability%2F<cap-id>`. Cap
     exists iff response is an `:entity` (not `{:error true :code "not-found"}`). **Reject/flag loudly
     if absent — never silent (no mislock)** (mirrors Contract-C / logic-model I4).
  2. **route** on `:props :capability/frontier?` (**NOT** `:pre-registered?` — true for both classes):
     - `false`/absent → **ordinary** → flip `:status → :satisfied` (the construction *is* the evidence).
     - `true` → **frontier** → emit `:status → :claimed` (🟡-pending) + proposed-flip event; **never
       auto-satisfy** (the `:claimed → :satisfied` witness gate is Dokusan's layer).
  3. **idempotent** (keyed by endpoint-key + cap-id; re-promote = no-op) → a leaf advances ascent
     **exactly once** (the rollout's status-aware per-step gives 0 credit for already-`:satisfied`).
- **Stability commitment:** `promote!` reads claude-3's `:7071` overlay as a read-only validation API;
  `:capability/frontier?` + `:capability/status` + the `scope/capability/<id>` endpoint are a stable
  contract (no rename under us). Live anchors: `:ai-passes-prelims` frontier?=true/`:held`;
  `:agency` frontier?=false/`:satisfied`.
- **Layer split:** *us* = `advances_cap` column + `promote!` validate/route/write · *M-wm-policies* =
  stamp `:advances-cap` on the leaf + status-aware per-step read · *Me/Dokusan* = the frontier witness gate.

## Deliverables (the build — pending dispatch)

1. **`advances_cap TEXT` column** on the `arrows` table (`meme.schema`), threaded through
   `meme.arrow/create-arrow!`+`get-arrow`, `meme.writer/write-arrow!`, and `meme.identity` (carried
   orthogonally to the `(have,want)` key). Accessor `meme.identity/advances-cap`.
2. **`promote!` validate/route/write block** (the contract above), co-located with Contract-C's
   unify-by-endpoint at the promotion step. The cap-overlay read (validation) + the flip/claim write
   go through the same `:7071` seam as the `code/v05/sorry` projection (penholder `api`, EDN responses).
3. **Worked example** `holes/labs/M-memes-arrows/worked-examples/e-advances-cap-ascent.clj` — runnable:
   - ordinary cap (`:agency`) → `promote!` **auto-flips** `:status → :satisfied`;
   - frontier cap (`:ai-passes-prelims`) → `promote!` emits **`:claimed` + proposed-flip**, NOT satisfied;
   - unknown cap-id → `promote!` **rejects loudly** (no mislock);
   - re-promote → **no-op** (idempotent); absent `:advances-cap` → construct+cross only.

## Acceptance bar

The worked example PASSes all five cases above against the live `:7071` overlay (read-only validate +
the cap-status write, or a dry-run flag if Joe wants the cap-overlay write itself operator-gated like
the substrate-2 write was). Gates: clj-kondo · `dev/check-parens.el` · futon3a tests · never restart
the JVM · never auto-satisfy a frontier cap.

## Provenance

Authored by claude-4 2026-06-09 after Joe ratified that the closed-mission follow-on belongs in a
warranted excursion. Contract settled by two-round whistle salvo with the M-wm-policies Track-2 agent;
read-source + predicate verified live by that agent. Implementation not yet dispatched.
