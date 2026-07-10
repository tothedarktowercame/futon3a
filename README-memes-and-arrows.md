# Memes & Arrows — the futon3a arrow store (revived)

*What it is, what was built, and what it means for substrate-2.*
Authored 2026-06-09 (claude-4) from mission `holes/missions/M-memes-arrows-patterns-diagrams.md`.

## 1. What this is, in one breath

We needed a home for **half-finished ideas and the gaps in them** — kept apart from things we've
actually pinned down. It turns out three things we wanted to store are **not three kinds of thing**:
they are **one arrow, from what you *have* to what you *want*, at three stages of growing up.**

| stage | plain name | meaning | `status` |
|---|---|---|---|
| **cascade** | a hunch | two things *observed* to go together; no method, no committed goal | `:correlated` |
| **sorry** | a gap with a known shape | the goal is committed and its exact type is fixed by the surrounding construction, but the method is **absent** | `:open` |
| **construction** | the recipe / the machine | a runnable method that actually produces `want` from `have` (a wiring diagram; a BHK arrow) | `:constructed` |

`correlation → conjecture → proof`. The same arrow, gaining structure. **The store keeps them
together because the value is the *movement between stages*** — a well-attested cascade with no
method *is* a conjecture worth proving, and you can only see that when cascades and sorries sit side
by side. (Kolmogorov: a problem and its solution are the same object — the problem is the solution
with a hole the exact shape of the answer.)

The **keystone**: an arrow is identified by its **`(have, want)` endpoint pair**, *not* by a
record-id. So a freshly-mined sorry with a new id **unifies onto the existing arrow by
endpoint-match** rather than minting a duplicate. That is *why* the three stages share one home — it
is a mechanism (one primary key), not a filing preference.

## 2. What was built (`src/meme/`)

The `meme` SQLite store was built in Jan 2026, then abandoned — **read-wired but
write-orphaned** (no writer ⇒ `meme.db` was never created). This mission **revived it** (the
abandonment was plain neglect, not a shape problem — verified from git history) and added the
endpoint-identity lifecycle:

| ns | what it does |
|---|---|
| `meme.writer` | **(R5, the missing half)** creates+initialises `meme.db` and persists arrows via the existing `meme.arrow` API. Default path `MEME_DB_PATH` → `futon3a/meme.db`. |
| `meme.endpoints` | extracts a canonical `(have, want)` from a `missing-head` signal by resolving the head id against the **live `scan-aif-heads` registry** (not regex). |
| `meme.identity` | **the keystone** — arrows keyed by `(have, want)`; `promote!` advances `:correlated→:open→:constructed` *in place* and **unifies** a same-endpoint token instead of minting a duplicate. Carries a **live conformance probe** of the 5 invariants. |
| `meme.substrate2` | projects a `:constructed` arrow to a substrate-2 `code/v05/sorry` doc with `:promoted-from <endpoint-key>` — and **refuses** to project an `:open`/`:correlated` arrow (the priors↔facts boundary). |
| `meme.count_watch` | the Contract-A tripwire — silent below ~10⁴ persisted arrows, fires loudly above (the only event that reopens the "switch to a real triple store" decision). |

Plus the pre-existing `meme.{core,arrow,schema,proposal,policy-arrow,bridge,compass-bridge}`.

### The 5 invariants (enforced by `meme.identity` + its live probe)
- **I1 endpoint-uniqueness** — no two distinct rows share `(have, want)`.
- **I2 construction-iff-constructed** — `status = :constructed` ⟺ a payload/method is present.
- **I3 monotone-advance** — promotion only goes `correlated→open→constructed`, never back.
- **I4 unify-not-mint (Contract C)** — a mint for endpoints an existing row holds must unify.
- **I5 node-reuse (R1)** — every endpoint references an existing node.

*(I1 and I4 are two views of one guarantee: enforcing unify-by-endpoint at write-time is what keeps
endpoint-uniqueness true in the snapshot.)*

## 3. Worked examples (the gallery)

Every piece is demonstrated on a real instance — see `holes/labs/M-memes-arrows/worked-examples/GALLERY.md`.
Highlights: `h3-endpoint-identity.clj` (r3a matures as one row, dup unifies, probe 0-conforming /
5-of-5-adversarial-caught) and `h4-similarity-join.py` (8/8 join + 4/4 corroboration + seeds 4 real
`:open` candidate sorries from *similar AND co-occurring AND no-construction*). The canonical
reference case is `reference-case-one-arrow-three-stages.edn`.

## 4. Implications for substrate-2 (read this)

substrate-2 (futon1a, on :7071) is the **durable facts** store. The meme arrow store is the
**proposals/priors** layer. The boundary between them is load-bearing and the reason both exist:

- **Only `:constructed` arrows promote to substrate-2.** `meme.substrate2/arrow->sorry-doc` *refuses*
  `:open` and `:correlated` arrows (`:boundary/non-constructed-arrow`). Hunches and open gaps stay in
  meme; only a *reached* construction crosses into facts. This preserves the priors↔facts boundary
  (R7) — substrate-2 never accumulates un-constructed conjectures.
- **The fact projection is a one-endpoint `code/v05/sorry` hyperedge**, matching the convention
  `futon3c.watcher.file-ingest` / `E-substrate-2-sorry-typing` already use. It carries
  `promoted-from = <(have,want) endpoint-key>` as a back-link, plus `meme/arrow-id`, `meme/have`,
  `meme/want`, `meme/mode`, and `sorry/t = 0` (a constructed arrow is not an open hole).
- **`E-substrate-2-sorry-typing` ingests `sorrys.edn` = already-promoted facts.** So both tracks
  agree: substrate-2 is *post-promotion only*. The meme store is where an arrow *lives its life*; a
  copy crosses to substrate-2 *when and only when* it reaches `:constructed`.
- **Scale / cost (Contract A).** Persisted arrow count is expected to stay ~10³–10⁴, so SQLite is
  ample and a "real triple store" is premature. `meme.count_watch` fires if that envelope is ever
  breached (the only event that reopens the decision). The one thing that could breach it is
  micro-grain *transcript* mining; the sibling M-a-sorry-enterprise miner is contracted to flag
  before persisting at that grain.

### The live write (now greenlit — tracked here)
Per operator go-ahead (Joe, 2026-06-09), the `:constructed → substrate-2` projection is now wired to
**actually POST** to futon1a, not just project. Every live promotion is recorded in
[§7 Live-write log](#7-live-write-log) below so substrate-2 mutations from meme are auditable.

## 4a. WM policy-rollout seam (M-wm-policies Track-2) — contract LOCKED 2026-06-09

The arrow store is the **transition layer** for the WM policy-rollout. When a rollout leaf constructs
an arrow that advances a registered capability, the promotion advances ASCENT. Contract (declare-
don't-guess; resolution is domain-specific, not lexical — per §12.11 of the mission):

- **`:advances-cap <cap-id>` (optional arrow field, `advances_cap` column).** Stamped at construction
  by the rollout leaf — its pragmatic target, **never inferred** from the `(have,want)` string. Absent
  ⇒ ordinary arrow (construct + cross to substrate-2, no cap flip, no ascent).
- **`promote!` on `:open → :constructed`**, when `advances_cap` present:
  1. **validate** against substrate-2: `GET /api/alpha/entity/scope%2Fcapability%2F<cap-id>` (:7071).
     Cap exists iff the response is an `:entity` (not `{:error true :code "not-found"}`). **Reject/flag
     loudly if absent — never silent (no mislock)**, mirroring Contract-C/I4.
  2. **route** on `:props :capability/frontier?` (NOT `:pre-registered?`, which is true for both classes):
     - `false`/absent → **ordinary** → flip cap `:status → :satisfied` (the construction *is* the evidence).
     - `true` → **frontier** → emit `:status → :claimed` (🟡-pending) + a proposed-flip event; **never
       auto-satisfy**. The `:claimed → :satisfied` transition is the witness/Dokusan gate (their layer).
  3. **idempotent** (keyed by endpoint-key + cap-id; re-promote = no-op) — matches the deterministic
     hyperedge upsert. So a leaf advances ascent **exactly once** (their status-aware per-step gives 0
     credit for already-`:satisfied`).
- **Layer split:** us = `advances_cap` column + `promote!` validate/route/write; M-wm-policies = stamp
  `:advances-cap` on the leaf + status-aware per-step read; Me/Dokusan = the frontier witness gate.
- **Stability commitment:** `promote!` reads claude-3's `:7071` capability overlay as a validation API;
  we treat `:capability/frontier?` + `:capability/status` + the `scope/capability/<id>` endpoint as a
  **stable contract** (no rename under us).

*Status: contract locked + designed; implementation (column + `promote!` block) pending — see mission §13.*

## 5. How to run

```bash
cd ~/code/futon3a
# write arrows + round-trip (isolated db)
clojure -M holes/labs/M-memes-arrows/worked-examples/h1-meme-writer.clj
# endpoint-identity + Contract C + live probe
clojure -M holes/labs/M-memes-arrows/worked-examples/h3-endpoint-identity.clj
# similarity join + cascade-seeding (real notions index)
python3 holes/labs/M-memes-arrows/worked-examples/h4-similarity-join.py
```
`MEME_DB_PATH` overrides the db path (default `futon3a/meme.db`). substrate-2 lives at
`http://localhost:7071` (`GET/POST /api/alpha/hyperedge[s]`).

## 6. Status

- Mission `M-memes-arrows-patterns-diagrams.md`: INSTANTIATE H1–H6 complete (all worked examples PASS).
- **Pending:** T3 (arm the count-watch on the live store), and the gallery stretch — ∃-sorry /
  ∀-sorry flavour examples (the generalisation test of `meme.endpoints`), plus the B1–B5 boundary demos.

## 7. Live-write log

*(Each row = one real promotion of a `:constructed` meme arrow into substrate-2. Appended as they happen.
The substrate-2 hyperedge id is **deterministic** from the `(have,want)` endpoint key, so re-promoting
the same arrow **upserts** the same hyperedge — idempotent, no duplicates. The meme arrow-id is
ephemeral per run; the hyperedge id is the stable identity.)*

Mechanism: `holes/labs/M-memes-arrows/worked-examples/h5-live-substrate2-promotion.clj` —
`POST /api/alpha/hyperedge` (penholder `api`), responses are EDN.

| when | meme arrow-id | (have → want) | substrate-2 hyperedge id |
|---|---|---|---|
| 2026-06-09 | arr-e95370bc-3d1 | belief-mass-on-supports-tagged-cohort → support-coverage-channel | `hx:code/v05/sorry:futon3a/sorry/meme-arrow-6b69271667003880` |
