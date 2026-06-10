# Mission: M-memes-arrows-patterns-diagrams — Is futon3a fit to hold pattern cascades?

**Date:** 2026-06-09
**Status:** ✅ **COMPLETE (2026-06-09, Joe).** All phases IDENTIFY→MAP→DERIVE→ARGUE→VERIFY→
INSTANTIATE→DOCUMENT done. The abandoned `meme` store is **revived + live** (endpoint-identity
keystone; `meme.{writer,endpoints,identity,substrate2,count_watch}`); both exit conditions met
(T4 live substrate-2 promotion logged, T3 tripwire armed); full worked-example gallery incl. the
B5 capstone (one arrow cascade→sorry→construction→fact, live). **DOCUMENT = `README-memes-and-arrows.md`**
(Joe: "Document is closed by the README"). Follow-on (not blocking): per-flavour endpoint extractors
(∃/∀), and the B1–B4 seam demos (covered by B5). See §13 close.
**Prior phase trail —** IDENTIFY + MAP (MAP signed off by Joe 2026-06-09; §2.1 closes
Q1/Q2/Q4, Q3 deferred to a DERIVE/VERIFY spike, ready-vs-missing table complete, surprises
logged + the empty-index one repaired via E-patterns-and-missions-live). **VERIFY COMPLETE
(§11): T1 revive-safe, CORE endpoint-identity logic-model VERIFIED, EP endpoint-extraction PASS,
T2 similarity-join PASS; R3 (named-diagram composite) partial; T3+T4 carried as MISSION EXIT
CONDITIONS; codex-5 off Agency so miner-side now owned directly. INSTANTIATE authored as 6
scoped handoffs (§12, H1–H6, not yet dispatched).** **DERIVE complete;
ARGUE COMPLETE (2026-06-09, §10) — thesis re-scoped to endpoint-identity (one arrow-TYPE keyed by
(have,want), 3 states); demonstrated by a real token-closure example (r3a); ratified across TWO
rounds of claude-5 adversarial review (§10.7–10.10) with unwitnessed claims named explicitly.
Highest-leverage VERIFY build item = endpoint-extraction on the missing-head class.** §9 car-1 = placement decision; §9.1 = **grain-spike (3 real
examples written through the real meme API + read back): grain does NOT fight; Q3 answered
NO.** The meme schema already ships `proposals`/`promotions` tables encoding the
proposal→arrow→promote-to-fact lifecycle — so "priors store" is renamed to its real terms
(**proposals**, **promotion**). §9.2 = **Joe steer + correction:** the §9.1 "tilts to REVIVE"
was over-concluded — the **store-implementation decision is OPEN** (revive `meme` / "real"
triple store for speed / graph embeddings), to be judged against *real instances*. BHK
sharpening: cascade = correlation (not a BHK arrow); sorry = Kolmogorov *problem* (RHS
specified, method absent); **BHK arrow = wiring diagram** = construction (real instance:
`futon5 aif2-exotype.edn`). Sorry *flavours* map to BHK clauses (→/∃/∀). Key MAP finding (§7.1): sorries carry a codomain *in prose, not structure*, and the only live
sorry typing (E-substrate-2-sorry-typing) models them as *vertices, not arrows*. **Priority direction:
(C′) learn *holes* not patterns, integrated with M-a-sorry-enterprise (§7).** Open fork
underneath: revive vs replace the abandoned `meme` store. The
`cascade → edge-of-chaos → conjecture` idea (§8) is **parked / explore-later**.
This is an **investigation mission** (track what we learn); not yet committed to
a build.
**Xenotype:** derivation (IDENTIFY → MAP → DERIVE → ARGUE → VERIFY → INSTANTIATE)
**Repos:** futon3a (meme store, notions/embeddings, MUSN), futon3c (live caller:
real_backend, bridge, http). Touches: substrate-2 (futon1a) only by contrast.
**Owner:** Joe frames; agent TBD (architecture/framing stays with the Claude owner).
**Related:** the cascades/prior-vs-fact thread; D1 (mission-scopes → substrate-2)
runs in parallel and **must not tangle** with this. Originating exchange: a
claude-3 recommendation on futon3a-as-cascade-home, corrected here.

## HEAD (Joe, 2026-06-09, verbatim sense)

> "I question whether the storage is fit for purpose. My sense is that agents
> aren't using it to find patterns, and moreover, I'm not sure the 'meme' graph
> is being used at all. (futon3b also seems very unused unless it is being relied
> upon silently somewhere!) But I'm now thinking to use *some* storage mechanism
> for pattern cascades. … Let's make the investigation a Mission so that we track
> what we learn."

> "claude-3 is just incorrect to say that substrate-2 = BHK facts. That's not
> even a thing. Substrate-2 are relations, yes, but BHK is not an arbitrary
> relation, it's a proof step."

## 1. IDENTIFY

### The question (why this mission exists)

We want a home for **pattern cascades** (and wiring-diagrams) — light, ad-hoc,
consult-as-needed *priors*, distinct from durable facts. futon3a is the
*conceptual* candidate (its README: a sidecar for pattern **guidance, not a
constraint**). But two things are unclear and must be settled before building:
1. Is futon3a's existing storage actually **used** today, or is it dead weight?
2. Does it already have the **mechanism** a cascade store needs, or not?

The investigation below answers both — and the answers are not what the
originating recommendation assumed.

## 2. MAP — verified live 2026-06-09

### Joe's three worries, against the evidence
- **"The meme graph isn't being used at all"** — **correct.** The SQLite backing
  file `meme.db` **does not exist** anywhere on disk (`MEME_DB_PATH` unset; JVM
  cwd `futon3c/` has no file). Zero persisted data.
- **"Agents aren't using futon3a to find patterns"** — **underused, not
  unwired.** `futon3c/src/futon3c/peripheral/real_backend.clj` genuinely calls
  `notions/load-pattern-index`, `notions/get-pattern-details`,
  `relations/pattern-exists?` in its pattern methods, and the notions embeddings
  (fasttext/glove/minilm) were **regenerated 2026-06-09**. Path is live and
  maintained; what is unconfirmed is whether agents *trigger* it at runtime.
- **"futon3b seems very unused unless relied upon silently"** — **silently
  relied upon.** `futon3.gate.pipeline`/`util` and `futon3b.query.relations` are
  required by live `futon3c/src/futon3c/bridge.clj` and `real_backend.clj`;
  `bridge.clj` converts a futon3b proof-path into sidecar evidence for futon3a.
  Quietly load-bearing, not dead.

### The decisive finding: the faceted store already exists, built-and-abandoned
The originating recommendation said futon3a is "**not** a faceted triple store…
no SQLite even" and that cascades need a faceted-triple capability **added**.
**Both wrong:**
- `meme.core`/`meme.arrow`/`meme.schema` use `next.jdbc` over
  `{:dbtype "sqlite" :dbname (or $MEME_DB_PATH "meme.db")}`. **It is SQLite.**
- `meme.arrow` **is** the faceted edge store in question: *"Kolmogorov arrows:
  typed semantic transforms between entities, A → B,"* with typed modes
  (`:derivation` `:analogy` `:specialization` `:metonymy` `:construction`
  `:untyped` …), a `:draft → :active → :retired` lifecycle, and confidence
  0.0–1.0. That is precisely `pattern —[co-applies, conf, draft]→ pattern`.
- It is **read**-wired live: `http.clj` exposes `find-entity-by-name` +
  `arrows-from`.

**But it was never adopted.** git history: meme built across 3 commits
**2026-01-16 … 2026-01-23** ("Add meme layer (SQLite)…", "compass-to-meme
bridge", "Kolmogorov arrows for policy transitions" — old "Mission 4/5"
numbering), then **untouched for ~5 months**. meme has its own writers
(`insert!`/`execute!`, ~21 sites) but **nothing in futon3c calls them** — only
the read endpoints. Read-wired, write-orphaned ⇒ DB never created ⇒ empty.

So the situation is not "add a facet" — it is "**an abandoned faceted-arrow
store sits exactly where you'd build one.**" The fork is revive vs replace.

## 2.1 MAP closure (2026-06-09, claude-4) — §5 survey questions + ready/missing table

Per `futon4/holes/mission-lifecycle.md`, MAP's exit criterion is "every MAP question
has a concrete answer; the ready-vs-missing table is complete" — and MAP produces
*facts, not decisions*. The §5 questions, answered live:

- **Q1 — why was meme abandoned?** *Answered (§2):* read-wired, write-orphaned, built
  across 3 commits 2026-01-16…23 then untouched ~5 months. DB never created ⇒ empty.
  Sufficient for MAP; the deeper "Mission 4/5 intent" read is not needed to decide
  revive-vs-replace (that's a DERIVE judgement).
- **Q2 — are agents exercising the notions pattern path at runtime?** *Answered: YES,
  confirmed live (corrects §2's "unconfirmed").* `GET /api/alpha/war-machine?days=14`
  (async-cached snapshot) reports **5,085 pattern activations across all 84 collections**
  (every collection >0), top: futon-theory 566, iching 378, devmap-coherence 316. These
  are context-retrieval evidence entries — one per A→B turn whose futon3a retrieval
  surfaced patterns (`http.clj` `derive-pattern-activations`, :4104). **Caveat:** this is
  the *pattern-surfaced-by-retrieval* signal (raw retrieval, ~360/day), **not**
  PSR-confirmed *application* (http.clj:4116-4121). Retrieval fires every turn; whether
  agents act on what's retrieved is a finer signal this count does not capture. So the
  path is *live and hot*, not merely call-able-but-cold.
- **Q3 — does the cascade schema fight the grain as `meme.arrow` rows?** *Deferred (a
  spike, not research).* Per the lifecycle, MAP is "read code, call APIs, count things";
  the fight-the-grain test requires writing one real arrow end-to-end, which is a
  VERIFY/DERIVE spike. Flagged open, not closed here.
- **Q4 — does the notions embedding index cover the pattern vocabulary cascades need?**
  *Answered, with a surprise.* `pattern-embedding-records.json` = **1073 records across
  77 id-namespaces**, but skewed to divination/notion corpus: iiching 257 + iching 64 =
  **~30%**; the pattern-language namespaces cascades actually chain are a minority
  (aif 18, realtime 13, coordination 13, structure/peripherals small). Live indices:
  **fasttext = 407** pattern embeddings, **glove present** (both regenerated 2026-06-09).
  **SURPRISE: `minilm_pattern_embeddings.json` is 0 bytes** (empty file, Jun-9 timestamp)
  — yet that is the index `real_backend`/M-a-sorry-enterprise source material names for
  embedding search. §2's "embeddings regenerated 2026-06-09" was too rosy for the minilm
  *pattern* index specifically. So similarity-facet coverage for cascade vocabulary is
  *partial* (fasttext yes, minilm empty) and *skewed* (notions dominate patterns).
  **Split out + repaired (2026-06-09):** the empty-index defect was a repair-and-guard
  issue, not a design question — `futon3a/holes/missions/E-patterns-and-missions-live.md`
  (**CLOSED**: codex-1 landed futon3a `8f8a099`, claude-4 reviewed/verified). minilm_pattern
  now 1073/14.2MB at parity; a temp-file→parity-assert→atomic-mv guard prevents recurrence;
  live cache self-heals (stamp-keyed). The *skew* observation (notions dominate patterns)
  stays a MAP finding here.

### Ready vs missing — for the (C′) holes-as-arrows direction

| Ready (no new code) | Missing (the actual work) |
|---|---|
| Two sorry populations that already name a codomain *in prose*: persisted registry (`futon2/resources/sorrys.edn`, 15) + live auto-miner (`loop_learning.clj`). | No `:rhs`/codomain as a **typed field** anywhere; nothing shaped as an `LHS→RHS` arrow with addressable ends. |
| Live sorry typing on disk: `E-substrate-2-sorry-typing` projects `code/v05/sorry` one-endpoint hyperedges via the watcher HTTP seam. | That typing is a **vertex, not an arrow** (DERIVE Choice 1 rejected multi-endpoint "because the registry does not provide" the structure). The 2-endpoint upgrade is unbuilt. |
| Pattern-retrieval path **live and hot** (5085 activations/14d) — the attestation/activation signal source (R4). | Activation is *retrieval-surfaced*, not *application-confirmed*; the finer "agent acted on pattern" signal is uncollected. |
| `fasttext` (407) + `glove` pattern embeddings regenerated 2026-06-09 — a similarity facet for free. | `minilm_pattern_embeddings.json` is **empty (0 bytes)**; embedding vocabulary skewed ~30% to I Ching, pattern-language patterns a minority. |
| `meme.arrow` SQLite faceted store exists, read-wired (`arrows-from`/`find-entity-by-name`). | **No live writer (R5):** meme DB never created; auto-miner is advisory-only / frame-local / not persisted (`loop_learning.clj:96-99`). |

**MAP exit status:** Q1, Q2, Q4 answered with concrete findings; Q3 explicitly deferred
to a DERIVE/VERIFY spike; ready-vs-missing table complete; surprises (minilm empty file;
runtime path hot not cold) documented. MAP closeable pending operator agreement.

## 3. Conceptual correction (Joe, 2026-06-09) — keep this straight

claude-3's clean slogan **"substrate-2 = BHK arrows = facts" is a category
error.** Corrected:
- **substrate-2 = relations** — general, durable, proof-*relevant* relations
  (missions, code, scopes). Being a relation says nothing about being a proof.
- **BHK is a proof step**, not an arbitrary relation: an arrow A → B under the
  BHK reading is a *construction* turning any proof of A into a proof of B. That
  is a specific, typed, load-bearing thing.
- `meme.arrow` literally adopts the BHK reading — and the BHK research note
  (`M-memes-arrows-patterns-diagrams.BHK-research.md`) **corrects** an earlier
  draft of this section. Mode and confidence are **orthogonal axes, not one
  gradient**:
  - **mode** = the *ontological kind*: a *correlation* (`:co-application`,
    `:analogy` — a relation we have *observed*) vs a *construction*
    (`:construction`/`:composition` — a method that actually *produces* the RHS).
  - **confidence** = the *epistemic strength*: how well-attested/believed it is.
  A construction can be low-confidence (unverified); a correlation can be
  high-confidence (heavily attested). **Attestation never promotes a correlation
  into a construction** — BHK explicitly *excludes* truth-of-a-relation and
  correctness-of-an-assertion from being constructions. (So the earlier
  "confidence × mode ARE the gradient" reading was wrong: the axes are
  perpendicular.)

Consequence for the store split: the fact/prior boundary is **mode crossing into
*construction*, gated by an actual method** — *not* a confidence threshold, and
*not* which repo the row sits in. You move from prior to fact by **supplying a
construction (changing mode)**, never by **accumulating attestation (raising
confidence)**. The discipline lives in the *facets* (status / mode / confidence),
and the load-bearing one is **mode**.

## 4. The decision (for DERIVE)

Provisional, to be argued:
- **Home:** futon3a is the right altitude (guidance/priors, pattern-centric,
  lightweight). Agreed with claude-3's conclusion, for sounder reasons than it
  gave.
- **Mechanism:** do **not** build a new triple store. The candidate is the
  existing `meme.arrow` SQLite store + the notions embeddings (similarity facet
  for free). Cascades = draft/low-confidence typed arrows.
- **The fork:** *revive* meme.arrow (write cascades to it, give it a live writer)
  vs *replace* it (the 5-month abandonment may be a signal the shape didn't fit).
  This fork is the reason the work deserves its own bounded excursion rather than
  a deferred section under pattern-phylogeny.
- **Boundary:** stays clear of D1 (mission-scopes → substrate-2): different store,
  different repo, different altitude (priors vs facts).

## 5. Open questions / next cars

1. **Why was meme abandoned?** (cheapest high-info step) — was it ever written to
   anywhere historically, or DOA on adoption? Read the "Mission 4/5" intent +
   any callers that existed and were removed. Decides revive vs replace.
2. Are agents actually exercising the notions pattern path at runtime, or is it
   call-able-but-cold? (runtime evidence, not just call sites.)
3. What is the minimal cascade schema as `meme.arrow` rows, and does it fight the
   grain when tried? (claude-3's own "if it fights, look further" test.)
4. Does the notions embedding index cover the *pattern* vocabulary cascades need,
   or only the corpus/missions it was last regenerated over?

## 6. NOTE (Joe ask, 2026-06-09): what we *need now* — wiring-diagram storage so cascades are thinkable

The framing shifts slightly: not "is meme fit?" in the abstract, but **what is the
minimum storage we need so a *pattern cascade* / *wiring diagram* is a thing we can
hold, attest, and reason over** — rather than a transient artifact.

**Where cascades live today (the actual deficit).** The 559 cross-reference cascade
edges exist **only** as the computed output of `futon6/scripts/pattern_phylogeny.py`
— recomputed each run, in a Python EDN dump, with no identity, no attestation, no
read-back. That is the gap: cascades are a *render*, not a *store*. You cannot point
at "the argue→verify→close cascade," ask what it's made of, or how often it's been
seen. Everything below is about closing exactly that.

### What we need now (requirements, prior-altitude — to argue in DERIVE)

- **R1 — Reference nodes, never re-mint them.** A wiring-diagram node is a pattern /
  notion that **already exists** (the notions index + pattern registry). The store
  must link to it by canonical pattern-id and fetch-merge — never create a second
  pattern entity. (This is the same node-reuse lesson D1 just learned for mission
  nodes; it transfers cleanly. The two stores stay separate, the *discipline* is shared.)
- **R2 — Typed wires with the prior↔proof gradient already in the facets.**
  `meme.arrow`'s `(mode × confidence × lifecycle)` is enough — no new primitive. A
  cascade wire = `:draft`, low-confidence, mode `:co-application` (or a new `:cascade`
  mode); a *designed* wire (a futon5-style composition) = higher-confidence,
  `:construction`/`:composition`. Per §3, the facets **are** the epistemic status.
- **R3 — The diagram as a first-class named object (the real new thing).** This is
  what neither meme.arrow nor the Python dump gives us. A wiring diagram / cascade is
  a *named composite* — `{id, name, status(:draft/:active/:retired), member-nodes,
  member-wires, provenance}` — that means something **as a whole** (a pattern-language
  fragment). meme.arrow stores wires one at a time; we need a grouping so a cascade is
  retrievable, versionable, and discussable as a unit, with its own diagram-level
  confidence distinct from per-wire. (Mechanism — a `diagram` + `diagram_wire`
  membership table vs a `:diagram-id` facet on arrows — is a DERIVE choice; the *need*
  is the named container.)
- **R4 — Attestation on every wire (what keeps a prior honest).** Each wire records
  **where it was observed** — co-application count + source refs (the missions / turns
  where the two patterns co-fired). We already compute this signal
  (`futon6/data/pattern-attestation.json`, the carpet's turn-weighted HGT roads); the
  store should hold it so **confidence is a function of attestation, not hand-set**. A
  wire with zero attestation is a guess, and the schema should make that visible.
- **R5 — A live writer (the orphaned half — the single most concrete need).**
  meme.arrow is read-wired, write-orphaned (§2): no writer ⇒ no DB ⇒ empty. The one
  thing that unblocks everything is a writer that creates wires + diagrams
  **opportunistically** (an agent notices A-then-B) and **idempotently** (re-observing
  bumps attestation, never duplicates). This is also the cheapest probe of the
  revive-vs-replace fork: writing one real cascade end-to-end tells us if the grain fits.
- **R6 — A consult-as-needed read.** The query we actually want: "given pattern X (or
  the current mission's bound patterns), what wires/cascades touch it, at what
  confidence?" → the *try-a-pattern* prior the WM would sample. `arrows-from` covers
  the wire-level read; R3 adds the diagram-level one ("which cascades contain X").
- **R7 — Boundary, restated as a facet not a wall.** This is priors in futon3a:
  `:draft`, low-confidence, loosely-typed. It must not leak into substrate-2 / D1
  (durable facts). Per §3 the guard is the **facets** (status/confidence/mode), not the
  repo location — a cascade promotes to a fact only via the normal evidence path,
  never by where the row sits.

### The futon5 connection (why "wiring diagram" and not just "cascade")
A futon5 wiring diagram (the designed, CT-style composition in `wiring-claims.edn` /
the diagrams) and an empirically-observed cascade are **the same storage shape at
different confidence** — designed wiring is a high-confidence, constructed diagram;
an observed cascade is a draft, attested one. One store (R1–R4) holds both, and that
is a *feature*: it lets us lay the **observed** cascade graph over the **designed**
wiring and read the gap (where do agents actually chain patterns vs where did we
*say* they compose?). That comparison is the sensible way to "think about cascades."

### Minimal first step (concrete, answers Open Questions #1 + #3)
Write **one** real cascade end-to-end into meme.arrow: pick a high-attestation pair
from `pattern_phylogeny.py` (e.g. a trunk primitive → its top co-applied pattern),
mint the two nodes as references (R1), one `:draft :co-application` wire with its real
attestation count (R2/R4), wrap it as a one-wire named diagram (R3) via a throwaway
writer (R5), and read it back (R6). If that fights the grain, that's the signal to
*replace* rather than *revive* — claude-3's own "if it fights, look further" test,
now made concrete.

## 7. (C) reframed → (C′): learn *holes*, not patterns — PRIORITY (Joe, 2026-06-09)

claude-3's earlier (C) "learn patterns from associations" is **dropped**: patterns are
cheap *supply* (infinitely generable, low value). The scarce, valuable signal is
**demand — which *holes* are worth proving.** So **(C′) = learn holes / sorries**,
wired to **M-a-sorry-enterprise** (the existing mining chain: agent interactions →
vocabulary → `sorrys.edn`).

**A sorry has the same shape as a cascade / wiring diagram** (Joe's correction to an
earlier "hole = node" framing). A hole is *not* a bare node ("something's missing
here") — it is a **term-less arrow**: an arrow *type* `A → B` whose **RHS is
specified** (the goal you must construct) and whose **construction is absent**. "We
need a RHS" is load-bearing: a hole with no codomain is not a goal — it cannot be
ranked, filled, or detected-as-filled. (In Curry–Howard terms a hole is a *type
without a term*, and when that type is an arrow type it inherits the arrow shape;
the RHS is the codomain to construct.)

So the store collapses to **one shape — a typed arrow `LHS → RHS` — with three
*statuses* forming a lifecycle:**

| status | = | RHS | construction |
|---|---|---|---|
| `:correlated` | cascade (A) | reached empirically | none |
| `:open` (sorry) | hole (C′) | **specified** | **missing** |
| `:constructed` | wiring diagram (B) | reached by a method | present |

> **correlation → conjecture (sorry) → construction** — one arrow climbing in status.

The synthesis that ties (C′) back to association-learning: **the way you *learn* a
high-value hole is a high-attestation cascade that lacks a construction** — a
well-attested correlation with no method *is* a conjecture worth proving. So
association-learning was right all along, not for *patterns* (nodes) but for
**conjectures** (RHS-bearing `:open` arrows): `attestation × no-construction = the
demand signal`. This closes the capability-star-map loop ("not living until
M-a-sorry-enterprise lands hole discovery") — holes become first-class arrows the WM
can do EFE over.

**PRIORITY — the M-a-sorry-enterprise connection comes first**, ahead of §8 and of
the revive/replace build. The load-bearing check: **do existing sorries carry an
RHS, or are they bare incompleteness markers?**
- If `sorrys.edn` entries name a codomain → this is a *wiring* job: integrate the
  registry as `:open` arrows in the graph; **do not fork it.**
- If they don't → the real work is *teaching the miner to name the codomain* before
  anything is stored.
Either way: **integrate with M-a-sorry-enterprise, do not duplicate it.** Next car =
pull M-a-sorry-enterprise + `sorrys.edn` through exactly this RHS lens.

### 7.1 FINDING (2026-06-09, claude-4) — RHS lens applied to both sorry populations

The next car ran. The load-bearing question — *do existing sorries carry an RHS?* —
has a precise answer: **yes semantically, no structurally**, and the consequence is
sharper than either fork branch anticipated.

**Two sorry populations, both checked:**
- *Persisted hand-curated registry* — `futon2/resources/sorrys.edn`, 15 entries
  (MAP §2 of E-substrate-2-sorry-typing counts 14; one meta + a later add). Schema
  (`sorrys.edn:3-19`): `:id :title :status :rationale :kind :links` — **no `:rhs` /
  `:codomain` field, and no LHS→RHS decomposition.** Nearly all are
  `:addressed`/`:resolved`/`:n-a-by-design` — the persisted store is essentially
  *drained* (only the meta-sorry is in-force).
- *Live auto-miner* — `futon3c/src/futon3c/aif/loop_learning.clj`,
  `judgement-gap-sorries`. Mines `{:id :kind :rating :title :rationale}` from WM
  gap-signals. `missing-head` → *"AIF head not readable by WM head: `<id>`"*
  (`:62-64`); `channel-gap` → *"WM channel `<id>` out of preferred range"* (`:67-70`).
  These are **advisory-only, frame-local, NOT persisted** (`:96-99`); promotion is
  gated to E-cheesemonger.

**They are not bare incompleteness markers.** Every sorry names a goal — a codomain
— in its `:title`+`:rationale` prose (a likelihood-model function, a readable head,
a channel in-range). The RHS is *semantically present*. **But it lives in prose, not
as a typed field, and nothing is shaped as an arrow with separately-addressable
ends.** So §7's clean fork doesn't bite: it is **neither** a pure wiring job (you
can't read a codomain off a field) **nor** a pure "teach the miner" job (the miner
already names the goal). The real work is *add an RHS facet + a cheap extraction pass
that lifts what is already in the prose* — for both the miner and a back-fill over
the registry.

**The decisive cross-check: the live substrate already types sorries — as VERTICES,
not arrows.** `E-substrate-2-sorry-typing` (INSTANTIATE landed on disk, non-live
fixture only) projects each sorry into a **one-endpoint** `code/v05/sorry` hyperedge
(`file_ingest.clj` `build-sorry-registry-docs`; DERIVE Choice 1 explicitly *rejected*
a multi-endpoint vertex "because the source registry does not actually provide" the
structure — E-substrate-2-sorry-typing §3.317). Its carried properties (§6.577-584)
are `title status raised-at sorry/t kind rationale links related-missions` — **no
codomain.** Its only typed edge is `:related-mission`; `:addresses :raises :resolves
:bites` are all deferred (§3 Choice 4).

This is the real result, and it *unifies the two missions*: §7 insists a hole is a
**term-less arrow `LHS → RHS`** (not a node), but the only live sorry typing models a
sorry as a **node** — *precisely because no structured RHS existed to anchor a second
endpoint.* The vertex shape is a direct downstream symptom of the prose-only codomain.
So the one move unblocks both:

> **Teach the miner/registry to name the codomain (RHS) as structure** → a sorry
> becomes typeable as a **2-endpoint arrow hyperedge `LHS → RHS`**, which is *the same
> arrow primitive* this mission (§3, §6 R2) wants for cascades and wiring diagrams.
> `meme.arrow`'s `(mode × confidence × lifecycle)` and the `:correlated/:open/:constructed`
> status column (§7 table) then apply to sorries and cascades through one store.

**Carried-forward to DERIVE (candidate directions — NOT decided in MAP):**
1. Candidate facet is **`:rhs` / codomain** (vs a new store, vs raised confidence). The
   information is already in the prose, so extraction + a schema field looks light —
   but whether the arrow shape fits the grain is a DERIVE/VERIFY spike (§5 Q3), not a
   MAP conclusion.
2. **Do not duplicate E-substrate-2-sorry-typing.** Its ingest seam (`code/v05/sorry`
   via the watcher HTTP path) is the place the arrow-typing extends: a sorry with a
   named RHS upgrades from a one-endpoint vertex to a two-endpoint arrow on that same
   seam. Coordinate, since that excursion is codex-5-owned and still awaiting
   operator VERIFY-of-INSTANTIATE.
3. The auto-miner is a ready-made **writer source** (the R5 deficit) currently wired
   advisory-only; making it name an RHS is the cheapest probe of whether the arrow
   shape fits the grain.

**Next car (proposed):** decide DERIVE direction — author the `:rhs` facet + a
codomain-extraction pass over the two populations, coordinated with codex-5's
E-substrate-2-sorry-typing seam — vs. park and take the §8 EOC overlay test first.
Operator's call.

## 8. EXPLORE LATER (parked behind §7): cascade → edge-of-chaos → conjecture

Speculative but grounded; **not now** — sits behind §7's M-a-sorry-enterprise work.

futon5's `mmca.filament` (`futon5/src/futon5/mmca/filament.clj`) detects edge-of-chaos
in CA runs by **Zhang–Suen thinning** a binary grid to a 1-px skeleton, then measuring
skeleton-graph structure (`giant-component-frac`, component count, branch degree,
length) + **temporal persistence** across frames. The insight: *most of that pipeline
is recovering a graph from a noisy raster* — the exact step we **don't** need, because
the attested-arrow cascade graph **is already the skeleton.** The back half (graph
metrics + persistence) ports directly; the lossy front half is deleted. Joe's bet: it
may work *better* here than in the register-based CA domain, precisely because the
handicap (graph recovery) is gone.

Regime mapping onto the cascade graph:
- **frozen** (Class II) = saturated all-`:constructed` clique → nothing to prove.
- **chaotic** (Class III) = dust (many tiny components, no giant, low persistence) →
  random co-firing, the "patterns are cheap" noise.
- **edge of chaos** (Class IV) = a **persistent giant-component filament that is not
  saturated** — a coherent thread of attested arrows with **open arrows (gaps) in
  it.** A filament-with-a-gap is a near-construction = a **sorry worth proving.**

Langton-λ analogue = the **mix of `:constructed / :open / :correlated`** arrows in a
subgraph (ties to futon5 `mission_0_regime_mix.clj`): all-constructed = frozen,
all-correlation = chaotic, **balance = EOC**.

Caveats: embedding-dependent bits (8-connectivity, Chebyshev radius) **don't port** —
use graph-native analogues (graph distance, edge persistence); "temporal" re-reads as
persistence over **mining-runs / agent-turns** (we have it turn-weighted in
`pattern-attestation.json`); and the metric may have *under*-delivered in CA, so this
is porting a promising-but-unproven idea into the one domain that removes its main
handicap — a good bet, still a bet.

**Cheap first test (when taken up):** overlay the 559-edge `pattern_phylogeny` cascade
graph with the sorry registry, compute giant-component / persistence structure, and
check whether real sorries **cluster on the persistent filaments-with-gaps** vs the
frozen cliques or the dust. If they do, EOC→conjecture has legs; if sorries land in
the dust, the hypothesis is wrong — an afternoon, not a mission.

## 9. DERIVE — phase open (2026-06-09, claude-4)

Per car-of-sequence: this is the **first DERIVE car only** — the one decision that gates
every other (schema, writer, extraction all hang off it). Authored to the boundary, then
pause for operator observation.

### The keystone decision: where does the hole-as-arrow primitive live?

MAP surfaced a real tension between the two missions that §7.1's "unify on one arrow
primitive" glossed over — and it must be resolved before any schema is written:

- **E-substrate-2-sorry-typing already places `:open` sorries in substrate-2** (futon1a,
  the durable **facts** store) as `code/v05/sorry` *vertices*.
- **This mission's own boundary (§4, §7-R7) says the opposite:** an `:open` hole is a
  *conjecture with no construction* — a **prior**, not a fact — and priors live in the
  futon3a store (`meme.arrow`), `:draft`/low-confidence, promoting to a fact only when a
  construction lands (mode crosses to `:construction`, per §3). By that boundary an `:open`
  arrow should **not** sit in the facts store at all.

So the two live representations disagree about where an `:open` sorry belongs. That is the
keystone.

### PSR — Pattern Selection Record

- Pattern chosen: `structure/two-projections-of-one-quantity` (cf. [[project_aliveness_synthesis]];
  the WM/mission-AIF "surfaces should be projections of one quantity" move)
- Candidates: (a) one store owns the arrow, the other projects it; (b) two independent
  stores, duplicated; (c) collapse one into the other
- Rationale: the priors-vs-facts boundary is load-bearing for the whole stack; collapsing
  it (c) would leak draft conjectures into the durable substrate, and duplicating (b)
  re-creates the drift M-INC/D1 fought. A projection relationship preserves the boundary.
- Confidence: medium — rests on the §3 claim that mode (not confidence, not repo) is the
  fact/prior gate; VERIFY should spike whether the promotion actually round-trips.

### IF / HOWEVER / THEN / BECAUSE

**IF** an `:open` hole is by definition a construction-less conjecture (§7 table) and this
mission's boundary makes "construction present" the fact/prior gate (§3),

**HOWEVER** E-substrate-2-sorry-typing has already shipped `:open` sorries as substrate-2
*fact-side vertices*, and that excursion is codex-5-owned and awaiting Joe's
VERIFY-of-INSTANTIATE (so we cannot silently reshape it),

**THEN** the arrow primitive `LHS → RHS` lives in the **futon3a priors store** (the
`meme.arrow` two-endpoint typed edge: `mode × confidence × lifecycle`), and substrate-2's
`code/v05/sorry` vertex is treated as the **fact-side projection** of an arrow that has
reached `:constructed`. The two are complementary projections of one arrow, not competitors:
priors-store holds the live `:correlated`/`:open` arrows; the substrate-2 vertex is what an
arrow *becomes* when it promotes. Promotion = write the fact-side vertex/edge; it does not
move the prior.

**BECAUSE** this keeps the priors-vs-facts boundary intact (the mission's whole reason for
being a separate store), it does **not** fight codex-5's excursion (their vertex stays the
fact-side and needs no reshape — at most a later `:promoted-from` back-link), and it makes
the unification in §7.1 precise: "one arrow primitive" = one `LHS→RHS` row whose *home* is
the priors store and whose *fact projection* is the existing vertex.

### What this car does NOT yet decide (next cars, after operator observation)
1. The exact `meme.arrow` row schema for a hole: how `LHS`/`RHS` map to its `entity_a →
   entity_b` endpoints, and whether `:open` needs a new `mode` or reuses `:co-application`
   + a `:rhs-specified?` flag. (revive-vs-replace fork lands here)
2. The codomain-extraction pass (prose `:title`/`:rationale` → structured `RHS`) for the
   two populations, and whether the auto-miner names the RHS at mint time.
3. The promotion mechanism (priors arrow `:constructed` → substrate-2 fact projection) and
   its coordination contract with codex-5's `code/v05/sorry` ingest.

### Coordination flag (pre-INSTANTIATE, not pre-DERIVE)
The promotion contract (#3) touches codex-5's owned, mid-flight excursion. DERIVE *design*
can proceed without blocking; but **INSTANTIATE of the promotion path must wait** on
codex-5's VERIFY-of-INSTANTIATE landing and an agreed back-link shape. Flagged here so it
isn't discovered late.

**Car boundary — paused for operator observation before the next DERIVE car.**

### 9.1 Grain-spike result — the examples (2026-06-09, claude-4)

Per Joe ("build some examples, otherwise we're talking about vague concepts"). Three real
§7-status examples written through the **real `meme.*` API** into a scratch SQLite DB and
read back. Lab: `holes/labs/M-memes-arrows/grain_spike.clj` (re-runnable; throwaway DB at
`/tmp/meme-grain-spike.db`). Real data, not invented:

| §7 status | real source | LHS → RHS | meme encoding (as written+read back) |
|---|---|---|---|
| `:correlated` | pattern_phylogeny top co-app pair (weight 8) | `construct-an-explicit-witness → reduce-to-known-result` | `arrows` row, mode `:analogy`, payload **nil**, conf 0.60 (attestation) |
| `:open` | `:sorry/r3a-likelihood-coupling-density` (open state) | `coupling-density-channel → predict-coupling-density-from-belief-mass` | `arrows` `:untyped` payload nil **AND** a `proposals` row (pending) |
| `:constructed` | `:sorry/r3a-likelihood-support-coverage` (resolved, cg-17bbaa01) | `belief-mass-on-supports-cohort → support-coverage-channel` | `arrows` mode `:construction`, payload = the real fn, conf 0.90, **+ `promotions` row** |

**The grain did NOT fight** — all three wrote and read back through the existing API with
no schema strain. Two concrete findings the examples forced:

1. **`arrows.payload` cleanly splits `:constructed` (payload present) from not-constructed
   (nil).** That axis is free.
2. **`:open` vs `:correlated` are NOT distinguishable in the `arrows` table alone** — both
   are payload-nil, and `mode` is only a weak proxy. The decisive point: an `:open` hole's
   RHS is an *aspirational goal* (a demand), while a `:correlated` cascade's RHS is an
   *observed node* (a supply). The arrows table has no field for that difference.

**The schema's OWN answer resolves it — and resolves Joe's "priors store" terminology
objection.** `meme.schema` already ships `proposals` (kind/status `pending`/`accepted`,
method + evidence) and `promotions` (explicit proposal→fact crossing, `decided_by`,
`rationale`), with the docstring: *"Memes live here … inspectable, grounded **proposals** …
Promotion to facts is explicit, not a side effect of search."* So the three statuses map
onto the layer's native lifecycle, no new primitive:

> **`proposals`(:open) — supply a construction → `arrows`+payload(:constructed) → `promotions`(→ substrate-2 fact).**
> `:correlated` is a *separate* `arrows` row; a high-attestation correlated arrow with **no**
> construction is precisely what **seeds an `:open` proposal** — §7's
> `attestation × no-construction = demand`, now mechanical.

**Consequences:**
- **Terminology fixed:** drop "priors store." The right words are the schema's: the futon3a
  meme layer holds **proposals**; **promotion** is the explicit crossing to substrate-2 facts.
  DERIVE car-1's split stands, renamed.
- **Revive-vs-replace (the §2.1-Q3 / §4 fork) tilts hard to REVIVE:** the abandoned store
  already contains the exact `proposals`/`promotions`/`arrows` lifecycle this needs — replacing
  it would rebuild what's already on disk. (Caveat: one spike; the writers are still orphaned —
  REVIVE means wiring a live writer (R5), not new schema.)
- **Q3 (does the cascade schema fight the grain?) — answered: NO**, with the refinement that a
  cascade is an `arrows` row and a *hole* is a `proposals` row, not both the same table.

**Car boundary — paused for operator observation.** Next car would be the writer (R5): wire
the auto-miner's mined gaps to emit `proposals` rows (with extracted RHS), the cheapest live
end-to-end.

### 9.2 CORRECTION + reframe (Joe steer, 2026-06-09): store decision is OPEN; build real instances

**§9.1 over-concluded.** It read "grain fits → REVIVE." Joe's correction: the grain-spike
"focused on what the current futon3a store *does*" — useful to understand — but the examples
were shaped to the meme API, so a grain-fit there cannot settle the **store-implementation**
question. That question stays **OPEN**, with three live options:
- **(i) revive `meme`** (the SQLite arrow/proposal/promotion store, as-is);
- **(ii) supersede with a "real" triple store** — e.g. for *speed*;
- **(iii) supersede with graph embeddings.**

**We won't know which until we hold real instances of all three object types** (Joe). So the
decision criterion is deferred to evidence: build the instances first, *then* judge which store
serves them (speed, query shape, embedding-similarity, promotion path).

**BHK sharpening (per the deep-dive note + Joe: "a BHK arrow is a wiring diagram until further
notice").** The note's §3 makes the three object types crisp and non-overlapping:

| object | BHK reading | computational content | real instance (sourced) |
|---|---|---|---|
| **pattern cascade** (`:correlated`) | a §3(a/b) *correlation/assertion* — co-occurrence; explicitly **not** a BHK arrow | none | `construct-an-explicit-witness → reduce-to-known-result` (pattern_phylogeny, co-app weight 8) |
| **sorry** (`:open`) | a **Kolmogorov problem** (*Aufgabe*) — codomain specified, construction absent | the *demand* | `sorrys.edn` entries — **flavoured by BHK clause** (below) |
| **BHK arrow = wiring diagram** (`:constructed`) | a §3(c) *construction* — a method transforming a proof of A into a proof of B | the *method* (payload = the diagram) | `futon5/data/missions/aif2-exotype.edn` (real AIF+ wiring diagram, `validate`-checkable) |

**Sorry flavours, grounded in BHK clauses (Joe: "possibly with a few different flavours"):**
- **`→`-sorry (method needed, H3):** `:sorry/r3a-likelihood-coupling-density` — RHS is a
  *function* (`predict-coupling-density`); the cleanest BHK implication: build the method.
- **`∃`-sorry (witness needed, H6):** `:sorry/stub-lifts-pending-aif-edn` — RHS is a
  *witnessed existential*: exhibit a concrete companion `.aif.edn` artifact.
- **`∀`-sorry (uniform method, H5):** `:sorry/r3d-per-entity-attribution` — RHS is a method
  that must work *uniformly for every entity*, not one chosen entity (the note's uniform clause).

This makes "a sorry has the same shape as a wiring diagram" (§7) precise: both are arrows
`LHS → RHS`; the sorry is the **problem** (RHS specified, method absent), the wiring diagram is
its **solution** (method present) — Kolmogorov's problem/solution pair, not two unrelated things.

**Scope set by Joe (2026-06-09):** set aside **pattern mining** (the auto-miner / cascade
*discovery*) for now — instances come from existing artifacts (pattern_phylogeny, sorrys.edn,
futon5 exotypes), not from a miner. *(Open scope question I'm carrying: whether "moving from
memes to notions" is also set aside, or is the intended direction — flagged for operator
confirm; does not block instance-building, which is store-agnostic.)*

**Next car:** assemble the three real instances as **implementation-neutral** objects (an
abstract `LHS → RHS` + facets shape that commits to neither meme rows nor a triple store nor
embeddings), so they become the shared bench the store options (i)/(ii)/(iii) are judged against.

### 9.3 Pinning down "sorry": proof-hole, NOT wishlist (Joe steer, 2026-06-09)

Joe: *"things the existing system refers to as 'sorries' (e.g. Strategic Sorries in the War
Machine) are probably NOT sorries in the sense we mean… a sorry is a hole in a proof, not
just a wishlist item."* And: don't force the direction yet — **trust the method**; the
implementation-neutral objects are the focus.

**The discriminator (the load-bearing test): WHAT CLOSES IT?**
- **Real sorry (proof-hole / Lean `sorry` / Kolmogorov problem):** closes by *supplying a
  construction* — a term/method/witness that **inhabits a type fixed by the surrounding
  construction**. Closure is *internal* and *type-checked against context*. (BHK note §3c: a
  type without a term; the type is determined by the proof around the hole.)
- **Wishlist / "Strategic Sorry":** closes by an *operator action in the world* (ship the
  feature, deliver the packet, have the conversation). The "type" is a desired outcome, not a
  type a term inhabits. Closure is *external*.

**Evidence the stack's current "sorries" are mostly the WRONG kind** (so my earlier
`sorrys.edn` sourcing was off, as Joe suspected):
- `futon2/resources/sorrys.edn` — PM items ("WM channel out of range", "build
  predict-coupling-density"): closed by *doing work*, not by inhabiting a type.
- `futon5a/data/alignment.edn` — its "sorry topology" is, verbatim, *"every connection that
  should exist but doesn't… each missing inter-edge… is a sorry"* — wishlist **by definition**.
- `futon5a/holes/stories/globe1-market-interface.aif.edn` `:n0` — `:role :strategic-gap`,
  `:ref "SORRY-market-interface"`, closed by *"deliver the offer packet, perform the ask"*.
  This is the **Strategic Sorry** Joe named (there is even a `strategic-sorry-topology.aif.edn`).
- **No literal Lean `sorry`** anywhere in the stack — so "in Lean terms" is a structural
  analogy, not a pointer to existing `.lean` holes.

**What DOES transfer: the structure, not the content.** The `.aif.edn` argument graphs (the
stack's actual "proofs") carry **103 `:status :open` + 19 `:gap` nodes**, and each open node's
*type is fixed by its edges* (`:supports`/`:specifies`/`:attacks` to neighbours). That is
genuinely proof-hole-shaped: a typed gap whose obligation is set by the surrounding argument.
The problem is only that today's instances are *strategic-content* holes (market, revenue,
governance), not *construction-content* holes.

**Implementation-neutral object (the payoff):** a **sorry = a wiring diagram (partial
construction) with one node whose type is determined by the wiring but whose term/construction
is absent.** I.e. the §9.2 wiring-diagram instance (`aif2-exotype`) *minus one filled box*.
This makes the three objects one family at three completion-levels:
`cascade (no construction, just co-occurrence) — sorry (construction-shaped hole, typed, unfilled) — wiring diagram (construction complete)`.
The sorry and the wiring diagram are **the same object**; the sorry is the diagram with a
typed hole — Kolmogorov's problem = the solution-diagram-with-a-gap.

**"Mining sorries not patterns" (Joe's M-a-sorry-enterprise lead):** the notions/embedding
mining machinery, repurposed to **discover typed construction-holes** in the stack's real
constructions — distinct from (a) M-a-sorry-enterprise's *affinity-scoring* (which scores
closure-trajectory of already-named strategic sorries) and (b) the auto-miner's *WM
gap-signals*. Neither currently mines proof-holes; that's the open frontier.

**Next car:** (1) read M-a-sorry-enterprise's mining mechanism precisely through this lens, and
(2) hunt for a *construction-content* `:open` node (candidate: `leaf-*.aif.edn` — the stack's
own logical structure — vs the `globe-*` market graphs) to serve as the real sorry instance.

### 9.4 The three real instances, implementation-neutral (2026-06-09, claude-4)

Per Joe's division of labour (2026-06-09): I stay on M-memes-arrows (the objects + the store
question); the sorry **mining mechanism** goes to a sibling agent on M-a-sorry-enterprise, paired
as needed. So this car is mine: the three **real** instances, as **implementation-neutral**
objects — abstract `lhs → rhs` + facets, committing to **no store**. Artifact:
`holes/labs/M-memes-arrows/instances.edn` (parses clean; 3 objects).

| # | kind / status | real source | lhs → rhs | construction | closes-by |
|---|---|---|---|---|---|
| 1 | cascade / `:correlated` | pattern_phylogeny top pair (co-app 8) | `construct-an-explicit-witness → reduce-to-known-result` | nil | n/a (observed) |
| 2 | **sorry** / `:open` (→/H3) | `leaf-6-4-4.aif.edn` n6 (`:inference`, `:gap "AIF loop step-count = 0"`) | observation-channels → EFE-mission-selection | **nil (defined, never run)** | **supply-construction** |
| 3 | wiring diagram / `:constructed` | `futon5 aif2-exotype.edn` | input ports → action port | the validated `observe→…→act` loop | n/a (done) |

**Why these three are the right bench:**
- The **sorry (2) is construction-content, not wishlist** — it closes by *running the loop*
  (a type-checked construction), not a world-action. It passed the §9.3 discriminator, unlike
  `sorrys.edn`/`alignment.edn`/`globe-*` strategic sorries.
- **Instances 2 and 3 are the SAME construction (the AIF loop) at two completion levels** — the
  sorry is the inference defined-but-unstepped; the wiring diagram is it wired + validated. This
  is "a sorry = a wiring diagram with one typed-but-unfilled node" made concrete (Kolmogorov
  problem = solution-diagram-with-a-gap).
- The **cascade (1) is a different kind** — a correlation, no construction; a high-attestation
  cascade with no construction is what *seeds* a sorry (demand = attestation × no-construction).

**What this bench is FOR (the deferred store decision).** Now that real instances exist, the
store options can be judged against concrete needs, not vibes:
- **lhs/rhs node reuse (R1)** — all three reference existing nodes; any store must link, not re-mint.
- **the `:open`→`:constructed` transition** — store must represent "type fixed, term absent" and
  its filling (the meme `proposals`→`arrows`+payload shape did this; a triple store needs a
  modelling choice; embeddings alone can't represent the *typed gap*).
- **similarity over lhs/rhs** — the cascade/seeding step wants embedding similarity (notions'
  strength; a plain triple store lacks it).
- **speed** — the open question Joe raised; not yet measured.

**Next car (mine):** turn this bench into the explicit store-comparison — score (i) revive meme /
(ii) real triple store / (iii) graph embeddings against the four needs above, with the speed
question called out as needing a measurement, not a guess. (Pairs with the sibling
M-a-sorry-enterprise agent on whether the mined sorries match instance-2's shape.)

### 9.5 DERIVE — the store comparison (2026-06-09, claude-4)

The bench (§9.4) lets the store question be answered against concrete needs. Scoring the three
options Joe named — (i) revive `meme` / (ii) "real" triple store for speed / (iii) graph
embeddings — against the four needs the instances expose.

#### PSR — Pattern Selection Record
- Pattern chosen: `structure/right-tool-per-layer` (the meme schema's own tri-store split)
- Candidates: single-store (meme) ; single-store (triple) ; single-store (embeddings) ; **layered**
- Rationale: the instances need *two different things at once* — a typed-gap **lifecycle**
  (relational) AND **similarity** (embedding). No single one of the three does both. The meme
  schema docstring already says this: *"Facts in XTDB; Memes here (SQLite, grounded proposals);
  Notions in ANN/HNSW (fast fuzzy recall only)."*
- Confidence: high on the layering; the *which relational store* sub-choice is speed-contingent.

#### The comparison

| need (from §9.4) | (i) revive `meme` (SQLite) | (ii) real triple store (for speed) | (iii) graph embeddings |
|---|---|---|---|
| **R1 node-reuse** (link, don't re-mint) | ✓ native (`entities` + `ensure-entity!`) | ✓✓ native (shared IRIs is the whole model) | ~ keyed by id, but no identity enforcement |
| **`:open`→`:constructed` typed-gap lifecycle** (type fixed, term absent → filled) | ✓✓ native (`proposals`→`arrows`+payload→`promotions`; grain-spike §9.1 proved it) | ~ representable but you must MODEL the lifecycle yourself | ✗ **cannot represent a typed gap** — an embedding is a point, no payload, no lifecycle |
| **similarity over lhs/rhs** (the cascade/seeding step) | ✗ not native — delegates to notions | ✗ not native (Datalog is exact) — delegates to a vector index | ✓✓ this is exactly what it's for (= notions, already live) |
| **speed** (Joe's motivation for a triple store) | ✓ fine at the real scale (below) | ✓✓ in-memory Datalog is fastest — but heavier infra + facts-altitude | ✓ ANN is fast for similarity only |

#### The decisive reframe: these are LAYERS, not competitors

**IF** an instance needs a typed-gap lifecycle (relational) *and* similarity (embedding) at once,
**HOWEVER** none of the three does both — embeddings provably can't hold the typed gap (the core
of the sorry/wiring-diagram object), and neither relational store does similarity natively,
**THEN** the architecture is **layered**: a relational store owns the `lhs→rhs` arrow + the
proposal/promotion lifecycle; the **notions/embedding** layer (already live) owns similarity and
the cascade-seeding step; they compose (the relational row carries the node ids the embedding
index is keyed on).
**BECAUSE** this is the meme schema's own tri-store design, and it dissolves the "supersede"
framing: **graph embeddings cannot supersede the relational store** (option iii is the
*similarity layer*, complementary, not a primary-store candidate), and a triple store would only
*replace one layer* — the relational one — not the whole thing.

#### Which relational store — and the speed question, answered with real counts

The "triple store *for speed*" motivation should be checked against the **actual scale**, which
the mission already measured: ~1073 patterns, **2538 co-application edges** (cascades), ~103
`:open` + 19 `:gap` aif nodes (sorries), a handful of wiring diagrams. Total arrows ≈ low
**10³–10⁴**. SQLite with the existing `idx_arrows_source/target/mode/status` indexes handles that
in microseconds. **So the speed motivation is premature optimization at the real scale** — it
only bites if scope grows orders of magnitude (e.g. mining every turn's micro-cascades → 10⁶+).

Two further marks against jumping to a triple store now:
- **Altitude.** A "real triple store" in this stack usually means substrate-2 / futon1a (XTDB) —
  the **facts** store. Putting `:open` sorries there violates the priors-vs-facts boundary (§9.2);
  the open sorry is a *proposal*, not a fact. A *new* fast triple store at the priors altitude is
  possible but is net-new infra.
- **Graph-embeddings caveat.** Even as the *similarity* layer, structure-aware graph embeddings
  (node2vec/R-GCN) are not obviously better than the live text embeddings — per
  [[feedback_superpod_embeddings]], BGE (text) beat R-GCN for retrieval here. So option (iii)'s
  best form is the text-embedding notions layer we already have.

#### DERIVE conclusion (provisional, for ARGUE)
- **Layered, not single-store.** Relational store (arrow + lifecycle) **×** notions (similarity).
- **Relational layer = revive `meme`** as the default: it exists, the lifecycle is built, and the
  scale doesn't justify heavier infra. A faster relational store (in-memory triple/Datalog) is a
  **scale-contingent upgrade of that one layer**, taken only if mining drives the count to 10⁶+ —
  recorded as the upgrade path, not done speculatively.
- **Embeddings are the similarity layer (notions), not a primary store.** They seed cascades and
  rank `lhs/rhs` neighbours; they cannot hold the typed gap.
- **Revive ≠ adopt-as-is.** "Revive `meme`" still means wiring the orphaned writer (R5) and the
  notions↔meme similarity join; the schema is reused, the dead wiring is not.

**Open for ARGUE / a measurement:** confirm the 10³–10⁴ scale estimate against what the sibling
M-a-sorry-enterprise miner actually produces (if it mines per-turn micro-sorries, the count could
jump) — that single number decides whether the triple-store upgrade path is ever taken.

### 9.6 ARGUE prep — cross-agent stress-test with claude-5 (M-a-sorry-enterprise), 2026-06-09

Joe asked for a whistle stress-test with claude-5 (the *mining* side) before writing ARGUE,
since the sorry is the shared object. Two blocking whistles; claude-5 grounded answers in the
live miner (`loop_learning.clj`), not vibes. Outcome — **two ratified contracts** + one
corroboration. (Also in shared memory: `project_sorry_arrow_contracts.md`.)

**CONTRACT A — scale (de-risks the §9.5 store conclusion).** The auto-miner is coarse + capped
(`max-mined 12`/cycle; mines structural `missing-head`/`channel-gap` signals ~10¹–10²;
advisory-only, not persisted). So *persisted* cardinality stays ~10³–10⁴ → **SQLite/meme holds,
triple-store stays premature**. The one honest flip-trigger: the mission's deeper
**transcript-mining** path *could* produce 10⁶ micro-holes. claude-5's commitment: micro-grain
stays ephemeral; **if transcript-mining ever wants to persist at micro-grain, they flag claude-4
BEFORE flipping.** My §9.5 "speed = premature optimization" now rests on a *named contract*, not
an unguarded assumption.

**CONTRACT B — shape (sharpens the object boundary; a real ARGUE refinement).** Mined sorries
split *exactly along the §9.3 discriminator*:
- `missing-head` → "AIF head not readable by WM head" → closes by *wiring/running the head* = a
  **construction-hole** = my instance-2 shape. **ACCEPT as `:open` arrows.**
- `channel-gap` → "channel out of preferred range" (`:kind :technical-debt`) → closes by
  *driving a metric back into range* = a **world-action**. **REJECT as a sorry-arrow** — it's a
  sibling drift/tension class.
So the `:open` object admits `missing-head`-style holes and excludes `channel-gap`-style items;
my instance-2 (`leaf-6-4-4` n6, AIF-loop-unstepped) is confirmed the canonical exemplar. This
tightens §9.4's object definition: not every "mined sorry" is a sorry-arrow.

**CORROBORATION — the priors/facts boundary is now double-sourced.** claude-5 confirms mined
sorries are **proposals** (frame-local priors), promoted to substrate-2 only via a *gated* step
(E-cheesemonger), never landing directly in substrate-2. Bonus: `E-substrate-2-sorry-typing`
ingests from `sorrys.edn` = types *already-promoted facts*, never proposals. So "proposals in
futon3a/meme-priors, facts in substrate-2" is independently consistent across **both** the
mining track and the substrate-typing track — not just my assertion.

**Net effect on the design (carried into ARGUE):** §9.5's layered conclusion survives the
stress-test intact; the scale assumption is now contract-guarded (A); the `:open` object boundary
is sharpened to exclude drift/tension items (B); the priors/facts boundary is triangulated (C).
ARGUE can now argue *inevitability* rather than mere workability, citing two independent tracks.

## 10. ARGUE (2026-06-09, claude-4)

ARGUE's real job is not "is the store design workable" — it is **what is this thing, how does
it work, and why do a cascade, a sorry, and a construction belong in one home at all?** The
store logistics (§10.1–10.4) are downstream of that and kept brief; operational hooks go to
VERIFY (§11). (Revised 2026-06-09 after Joe: the first draft argued cabinets and notifications;
this argues the object.)

### 10.0 What this is — the argument proper

**The three things this holds are not three kinds of thing. They are one thing — an arrow from
what you have to what you want — caught at three stages of growing up.**

- A **construction** is a *method that actually produces what you want from what you have*. Not a
  claim that it's possible; not a note that two things tend to co-occur — an actual procedure you
  can **run**, that takes what you have and hands back what you wanted. A wiring diagram is a
  construction: it is the machine, drawn. (This is the precise sense the BHK note settles — a
  construction *transforms* a proof of A into a proof of B; it must *do* something: run on any
  input and return the goal. It has computational content, not just truth.)

- A **sorry** is a construction *with one box empty* — and crucially, **the construction around
  the box fixes the box's exact shape.** You know precisely what must go there; you just don't
  have it yet. That is what separates a sorry from a wish: a wish is "I'd like more users"; a
  sorry is "right *here*, a thing of *this exact type* must slot in, and I haven't built it." The
  hole has a shape because the proof around it has a shape. (Lean's `sorry`: the proof is complete
  except one step, whose type the rest of the proof determines.)

- A **cascade** is the *rawest* stage: two things you've *noticed go together*, with no method
  joining them and no goal yet committed. You've seen a well-travelled road; you don't yet know if
  a real bridge could be built along it. Pure correlation.

Line them up and they are **one arrow `have → want` at three levels of completion:**

| stage | what you have | plain name |
|---|---|---|
| **cascade** (`:correlated`) | only the *observation* that `have` and `want` co-occur | a hunch |
| **sorry** (`:open`) | a *committed goal* with its exact shape fixed — but no method | a gap with a known shape |
| **construction** (`:constructed`) | a *method* that actually gets from `have` to `want` | the machine / the proof |

`correlation → conjecture → proof`. The **same arrow**, gaining structure.

**Why one home — the real reason, not a filing convenience.** Because it is *one object living its
life*, and the entire value is in the **movement between stages**:
- A cascade you keep seeing that still has no method *is* a **conjecture worth proving** — and that
  is how you *discover which sorries are worth your time* (well-travelled road + no bridge → build
  the bridge). You can only spot it when cascades and sorries sit in the *same place*, because the
  signal is a comparison across two stages: **often-observed AND no-construction**.
- A sorry someone solves *becomes* a construction — the same arrow, hole now filled.

Put the three in three separate homes and a single idea's life-story is torn into three drawers:
you can never watch a hunch harden into a question or a question close into a proof. **The home is
not storage — it is the timeline of an idea.** Kolmogorov already gave the deepest version: a
problem and its solution are the *same object* — the problem is just the solution with a hole the
exact shape of the answer. So the case for one home is not tidiness; it is that **separating the
stages would destroy the one thing we are trying to capture: ideas growing up.**

#### What the design adds on top of that
Everything in §9 is in service of this:
- The store keeps the arrow `have → want` with a marker for *which stage* it's at, and (for a
  construction) the method itself.
- "What stage" is read off two facts already in the object: **is the method present?** (construction
  vs not) and **is the goal a committed target or just an observed node?** (sorry vs cascade) — which
  is exactly why the meme schema's `proposals`/`arrows`+payload/`promotions` shape fit with no strain
  (§9.1).
- The similarity layer (notions) is what *spots* a heavily-travelled cascade — it surfaces the
  hunches that are ripe to become questions. That is why the home needs *both* a structural side and
  a similarity side (§9.5): one holds the stage, the other finds what's ready to advance.

### 10.1 Pattern cross-reference (real library patterns, where each bites)

| pattern (library) | where it applies | how |
|---|---|---|
| `peripherals/split-transport-from-embodiment` | the core layered claim | The pattern: a thing with *two distinct concerns* conflated into one cannot be re-used or composed. A stored arrow has two — its **typed-gap lifecycle** (relational) and its **similarity** (embedding). Forcing both into one store is the exact conflation the pattern warns against; splitting them is the same move, one layer up. |
| `storage/canonical-interface` | "revive ≠ adopt-as-is" (§9.5) | Compose the layers through the meme store's documented API + the notions index's interface, not internal coupling. Revive = reuse the schema/interface, re-wire the dead writer. |
| `peripherals/read-existing-seam-before-implementing` | revive-vs-replace | The abandoned `meme` schema *is* the existing seam (proposals→arrows→promotions). Building a triple store now would implement past a seam that already fits. |
| `combining-methods-as-diagnostic` (validated principle, [[feedback_combining_methods_as_diagnostic]]) | the cascade→sorry seeding step | The relational store and the embedding index *disagreeing* is the signal: a pair with high embedding-similarity but no relational construction is a well-attested correlation lacking a method = a candidate sorry. Their disagreement **is** the demand detector. |

### 10.2 Theoretical coherence — does it serve IDENTIFY?

IDENTIFY asked for "a home for pattern cascades / wiring-diagrams — light, consult-as-needed
**priors, distinct from durable facts**." The design serves that anchoring *and* sharpens it:
- "Distinct from durable facts" is no longer a vibe — it's the **proposal → promotion → fact**
  boundary, triangulated across two independent tracks (§9.6-C).
- The BHK/Kolmogorov reading (§9.2–9.3) makes "cascade vs sorry vs wiring-diagram" a single
  family at three completion levels, so one store shape holds all three — exactly the "light,
  one home" the mission wanted, not three bespoke stores.
The theory did not shift; it got precise.

### 10.3 Trade-offs — what we give up, deliberately

1. **We defer speed.** We bet SQLite is fine and *don't* buy the fast triple store. Mitigation:
   Contract A (§9.6) makes the flip-trigger explicit and owned, so the bet is monitored, not blind.
2. **We keep two layers, not one.** A relational store *and* an embedding index is more moving
   parts than a single store. We accept it because no single store does both (the embedding
   provably can't hold a typed gap); the cost buys composability.
3. **We exclude `channel-gap`-style items** (§9.6-B) from the `:open` object. We give up "all
   mined sorries are arrows" for a clean discriminator (closes-by-construction). Drift/tension
   items get a sibling home, not this one.
4. **We reuse a 5-month-abandoned schema.** Risk: it was abandoned for a reason we haven't found.
   Mitigation: the grain-spike (§9.1) wrote + read all three instances through it with no strain,
   so the abandonment was adoption-failure (no writer), not a shape problem.

### 10.4 Generalization

The layered split generalises beyond sorries: **any object that is both "a typed thing with a
lifecycle" and "a point you want to find by similarity" wants the same two-layer treatment** —
a relational store for the structure/lifecycle, an embedding index for recall, composed by
shared ids. Cascades, sorries, wiring diagrams are one instance; mission-scopes (D1) and
evidence are plausible others. The pattern to reuse is the *split*, not the specific schema.

### 10.5 Plain-language argument (the elevator version of §10.0)

> Three things we want to keep turn out to be one thing at three ages. The youngest is a
> **hunch**: two things keep showing up together and we don't yet know why. The middle one is a
> **question with a precise shape**: we've decided what we want and exactly what would count as
> getting it — we just can't do it yet. The oldest is a **recipe**: a real way to get there that
> we can actually run and check. A hunch we keep having grows into a question; a question we
> answer grows into a recipe. They belong in one place because they are *the same idea growing
> up* — and the most valuable move of all, noticing which hunch is worth turning into a question,
> can only be seen when the hunches and the questions are sitting side by side.

### 10.6 Carried-forward tensions (operational hooks belong in VERIFY)

ARGUE names them; VERIFY (§11) will give each a concrete check:
- **T1 — the abandonment risk.** Confirm there is no *other* reason meme was dropped beyond the
  missing writer (a quick git-history / commit-message read of the 3 build commits + any reverts).
- **T2 — the similarity join is unbuilt.** "meme ↔ notions composed by shared ids" is asserted,
  not demonstrated; VERIFY needs a spike that resolves a cascade's `lhs/rhs` against the notions
  index and back to a meme row.
- **T3 — Contract A is a promise, not a tripwire.** The flag-before-flip depends on claude-5
  noticing; VERIFY should ask whether a cheap *automatic* count-watch is worth wiring.
- **T4 — `:constructed` → substrate-2 promotion** crosses into codex-5's owned excursion; VERIFY
  must confirm the back-link shape before any INSTANTIATE of that path.

### 10.7 ARGUE carried by diagrams — ONE arrow, three stages, end-to-end (real artifacts)

Per Joe (2026-06-09): the argument is only believable if shown *working* — a real cascade, the
sorry it seeds, and a real construction that fills the hole — as **one arrow maturing**, not three
separate examples. Here it is, on real artifacts. The thread is **EFE-based action selection**.
All four patterns are real library flexiargs; co-app weights are real (`pattern_phylogeny`,
2026-06-09); the sorry is a real `:gap` node; the construction is a real validated exotype.

#### STAGE 1 — CASCADE (a hunch): four patterns keep co-firing; no goal, no method
```
        structured-observation-vector
                     │ 5
   candidate-pattern-action-space ──5── expected-free-energy-scorecard
                                          │ 5
                          policy-precision-commitment-temperature

   (edge label = times the pair co-occurs across mission docs)
   What we have: only the OBSERVATION that these four show up together when
   anyone reasons about "pick the next action." No arrow yet, no goal.
```

#### STAGE 2 — SORRY (a gap with a known shape): commit the goal; the method-box is empty
```
   observation channels ─────▶ ┌───────────────┐ ─────▶ EFE-ranked selection
   (structured-observation-     │   ?  method   │        (the committed GOAL)
    vector)                     │     HOLE      │
                                └───────────────┘
   The hole's TYPE is fixed by what surrounds it (leaf-6-4-4 n6, an :inference):
     • must consume the observation vector      (n5 :supports n6)
     • must emit a rank-able selection          (n6 :generalises-shape-to n7)
     • :gap "AIF loop step-count = 0"           (the method exists on paper, never run)
   And the ingredients the hole needs ARE Stage-1's four patterns.
   This is a sorry, not a wish: the box has an exact shape, only the method is absent.
```

#### STAGE 3 — CONSTRUCTION (the machine): the hole filled = the four patterns WIRED
```
  I-observation ──▶ C-propose ──────▶ C-rank ─────────▶ C-select ──────▶ O-recommendation
  (obs vector)      (candidate        (EFE scorecard)   (softmax /        (ranked decision)
        │            action-space)          │            policy precision)        │
        ▼                 ▼                  ▼                 ▼                    ▼
  structured-      candidate-pattern-  expected-free-   policy-precision-    the goal, REACHED
  observation-     action-space        energy-          commitment-          by a runnable,
  vector                               scorecard        temperature          validate-checkable
                                                                             method
   (futon5 aif2-exotype.edn — observe→propose→rank→select→act; futon5.ct.mission/validate)
```

#### The punchline (why this carries the argument)
```
  STAGE 1 cascade   :  • • • •            four patterns merely SEEN together
                          │  (commit a goal; name the hole)
                          ▼
  STAGE 2 sorry     :  ●──▶[   ]──▶◎      same ingredients; GOAL committed, method-box EMPTY
                          │  (supply the method)
                          ▼
  STAGE 3 construct :  ●─▶■─▶■─▶■─▶◎      the four patterns WIRED into the method; goal REACHED

  It is ONE arrow `observation → ranked-selection`. The four patterns that only
  co-occurred in Stage 1 are the wired boxes in Stage 3. The empty box in Stage 2 is
  exactly the method Stage 3 supplies. correlation → conjecture → proof, same object.
```

#### Honesty notes (so the demonstration is believed, not just admired)
- **Same-shape, not literal line-closure.** `aif2-exotype` is the *War Machine's* EFE selector;
  `leaf-6-4-4` n6 is *portfolio-inference's*. They are sibling instances of one method-shape — which
  is the level cascades and sorries actually live at (n6 itself says `:generalises-shape-to` the
  capability AIF). The arrow matures *at the pattern/shape level*; that is the claim, and it holds.
- **A literal hole→fill also exists**, if you want "actually shipped": `:sorry/r3a-likelihood-support-coverage`
  was `:open` ("derive support-coverage from belief mass") and was **closed by a real construction**,
  `futon2.aif.belief/predict-support-coverage` (cg-17bbaa01, 2026-05-26) — the same arrow, hole
  literally filled in code. Its cascade is fuzzier, which is why the diagrammed thread above uses the
  EFE family instead.
- **What this proves for the store (the ARGUE point):** the three stages are the SAME row gaining a
  method and a committed target — so they must share one home, or the maturation in these diagrams
  could not be recorded as one object's history. That is the inevitability §10 was reaching for, now
  shown rather than asserted.

### 10.8 Adversarial check (claude-5, 2026-06-09) — argument did NOT survive as-is; the repair

Joe asked me to pass the §10.7 argument to claude-5 (M-a-sorry-enterprise) and see if they buy
it. **They did not** — and the refutation is correct. Recorded honestly (the mission tracks what
we learn); the repair materially strengthens the design.

**Flaw 1 — the diagrammed cascade is NOT a low-completion `have→want` arrow.** As actually saved
(`reference-case…edn` `:edges`), the cascade is an *undirected co-occurrence graph among the four
method-INGREDIENTS* (candidate↔EFE, EFE↔observation, EFE↔precision). The `want` (EFE-ranked
selection) **is not even a node in the cascade** — it first appears at the sorry stage. So
cascade→sorry is "a clique among the *parts* becomes an arrow toward a *newly-posited* target,"
not "one arrow gaining a goal." My cascade is **ingredient-feasibility evidence for the
construction, not a proto-arrow.** The Kolmogorov cite ("a problem is its solution with a hole the
shape of the answer") nails sorry↔construction but says *nothing* about correlation→problem — I
was stretching it across a joint it doesn't reach.

**Weakest joint = cascade→sorry** (sorry→construction is solid — the type is fixed by n6's edges
and aif2-exotype supplies a method of exactly that type). To make cascade→sorry airtight, the
cascade must be a co-occurrence that **includes the want** — `have`-events and `want`-events
recurring together *with the method absent*. That is a genuine fuzzy `have→want` arrow; my
ingredient-clique is a different object.

**Flaw 2 (decisive, from the mining side) — the miner MINTS, it does not mature.**
`loop_learning.clj`/`judgement-gap-sorries` creates a sorry with a **fresh minted id**
(`:sorry/aif-head-missing-<id>`) directly from a WM gap-signal; there is **no cascade input** and
no promote-the-co-occurrence path. So "one object maturing" is **false at the token level** in the
live system — it is mint, not mature.

**THE REPAIR (claude-5's, adopted) — endpoint-identity makes "one home" a mechanism, not an
aesthetic:**
1. **Key arrow identity by the `(have, want)` endpoint pair, NOT the mint-time record id.** Then
   cascade/sorry/construction are *states* of one endpoint-keyed entity; the miner minting a fresh
   keyword is harmless — the store **unifies it onto the existing arrow by endpoint-match.** This
   is R1 (node-reuse) taken to its conclusion: the *arrow* is identified by its endpoints.
2. **Promotion = unify-or-transition, not create:** "find the arrow with these endpoints; advance
   its state; absorb any token sharing them" — not "create a new sorry."
3. **Re-scope the thesis** from "one *token* maturing" (diachronic — falsified by minting) to
   **"one arrow-*type* keyed by endpoints, three states; tokens occupy a state and unify onto the
   type by endpoint-match."** This survives *both* the miner's minting *and* my sibling-instance
   caveat in one move. "They must share one home" becomes mechanism: *because they are keyed by the
   same endpoints.*

**Disposition (for VERIFY + a new contract):**
- Adopt endpoint-identity as the store's primary key (a real DERIVE refinement — arguably the
  keystone mechanism; promote it from §9.5's R1 line to a first-class design commitment).
- Fix the exemplar: either find a real `have↔want` co-occurrence cascade (claude-5 flags
  `:sorry/r3a-likelihood-support-coverage` — which already has *token* closure — as the better
  full-maturation example, currently buried in my honesty footnote) **or** downgrade the EFE
  cascade's claim to "ingredient-feasibility" honestly. No single example yet carries all three
  stages of *one token*; that gap is the residual weak joint.
- **Candidate new cross-mission contract C (to ratify with claude-5 after Joe agrees):** the miner's
  promotion step must **unify-by-endpoint**, not mint-and-leave-orphan — otherwise the store has
  three tables and the thesis is false in practice.

### 10.9 ARGUE revised (post-claude-5) — endpoint-identity + the r3a one-token thread

Adopting claude-5's repair (§10.8). Reference case rebuilt: `reference-case-one-arrow-three-stages.edn`
**v2** (parses clean). Three changes:

**1. Thesis re-scoped — TYPE, not token.** Cascade/sorry/construction are one **arrow-type keyed by
its `(have, want)` endpoint pair**, in three **states** (`:correlated → :open → :constructed`). A
mined sorry's fresh id is *not* its identity; it **unifies onto the type by endpoint-match**. So
"they share one home" is now a *mechanism*: **the store's primary key is the endpoint pair**, and
three states of one endpoint-keyed entity cannot live in three tables without losing the entity.
(This is R1 / node-reuse taken to its conclusion — the *arrow* is keyed by its endpoints.)

**2. Primary exemplar swapped to the one with real token closure** (claude-5's own pointer):
`:sorry/r3a-likelihood-support-coverage`. The arrow `belief-mass-on-supports-cohort → support-coverage-channel`:
- `:correlated` — the sorry's rationale notices it: support-coverage *"could derive from belief mass
  on entities tagged as supporting evidence"* (have & want related, method absent). *Honest caveat:
  rationale-attested, not hard co-occurrence counts.*
- `:open` — the sorry, raised 2026-05-18: goal committed (a predictor → [0,1]), method absent.
- `:constructed` — `futon2.aif.belief/predict-support-coverage` (cg-a5d2e756, prereq cg-17bbaa01),
  shipped 2026-05-26.
- **Token-identity proof:** it is ONE record — `:status :open → :addressed`, `:addressed-by-cg-chain`
  citing the construction, endpoints unchanged. The arrow **matured**, it was **not re-minted**. This
  is the token-level maturation §10.7's EFE thread lacked.

**3. The EFE thread demoted + relabelled honestly.** It is *not* a maturation arrow — its "cascade"
was an ingredient-clique (the `want` wasn't a node). It is kept only as **construction-internal
composition** (what a `:constructed` arrow looks like inside: ingredients wired), explicitly a
different relationship from `have→want` maturation.

**Residual gap (named, not hidden).** No single token yet carries a *hard-recorded* `:correlated`
stage — r3a's is rationale-attested — because the live miner **mints** rather than promotes a cascade.
**Contract C (next, to ratify with claude-5): the miner's promotion must UNIFY-BY-ENDPOINT** (match
`(have, want)` onto an existing arrow, advance its state) — not mint-and-orphan. With endpoint-identity
as the primary key, that contract makes recorded full-maturation the normal case. Credit: claude-5.

### 10.10 ARGUE — ratified by claude-5 (2nd round), with amendments → ARGUE complete

claude-5's re-review: **v2 carries the thesis as a design claim** — ratified, with three
amendments now made explicit in the reference case (`reference-case…edn` v2, `:unwitnessed` +
amended `:residual-gap-and-contract`):

1. **Cross-source endpoint-unification is UNWITNESSED — stated, not buried.** r3a matured as one
   *hand-edited registry row*; it never passed through the miner. So r3a proves token-maturation
   is *possible*; it does **not** witness the cross-source unify-by-endpoint *mechanism*. (claude-5:
   leave this implicit and a hard reader downgrades it to "one row, not a mechanism.")
2. **The `:correlated→:open` transition is circular as evidence** — r3a's `:correlated` is read off
   the sorry's *own post-commitment* rationale. Witnessing it needs a co-occurrence record
   timestamped **before** `:raised-at`, which doesn't exist yet.
3. **Contract C ratified, AMENDED:** unify-by-endpoint lives on the **promotion step
   (E-cheesemonger), not the miner**; hard precondition = **endpoint-extraction** (derive
   `(have, want)` from the typed AIF head). Without it, C degrades to synthetic-id-proxy
   unification that misses same-endpoint/different-id collisions.

**Highest-leverage build item falling out of the exchange (claude-5):** *endpoint-extraction on
the `missing-head` class.* It is the precondition for Contract C, it is what turns r3a-style
maturation into the **recorded normal case** rather than a hand-edited exception, and it is
implementable precisely because AIF heads are typed. → carried into VERIFY/INSTANTIATE.

**ARGUE status: COMPLETE.** The thesis (one arrow-type keyed by endpoints, three states) is
argued by a real worked example with token-level `:open→:constructed` closure, survived two rounds
of independent adversarial review, and its remaining claims (cross-source unification) are named as
explicit, testable design obligations rather than hidden assumptions. The mission's `:argue/wyrd`
survival test — *does the structure survive a firm reviewer trying to break it?* — is passed. Both
the firm-but-fair review and the mint-vs-unify gap are now standing obligations on both missions
(shared memory `project_sorry_arrow_contracts.md`, Contract C).

## 11. VERIFY (2026-06-09, claude-4)

VERIFY = structural + empirical validation of the DERIVE/ARGUE design before code hardens. Per
[[feedback_argue_strategic_verify_operational]] this phase carries the operational hooks; per
[[feedback_car_of_sequence_dispatch]] only the current car is live, operator interjection at each
boundary.

### 11.0 Dispatch state + structural-verification decision

**Structural verification (wiring-diagram check):** this mission's deliverable is a *data-model +
lifecycle* (a layered, endpoint-keyed store), **not** a component/loop topology — so it has no
exotype of its own (the `aif2-exotype` it cites is a borrowed *instance*, not this design's
diagram). Per the lifecycle, recording why: the right structural check here is an **invariant
logic-model** of endpoint-identity (per [[feedback_logic_model_before_code]]), not an AIF+ exotype.
That logic-model is the VERIFY core (next car).

**Hooks, ordered (cheapest-high-info first):**
| # | hook | state |
|---|---|---|
| **T1** | abandonment risk — was `meme` dropped only for the missing writer? | **DONE this car — PASS (§11.1)** |
| **CORE** | logic-model of the endpoint-identity invariants (keystone design) | **next car** |
| **EP** | endpoint-extraction spike on the `missing-head` class (Contract C precondition) | held — after CORE |
| **T2** | meme↔notions similarity join (the layered claim) | held — spike |
| **T3** | Contract A as a tripwire, not a promise (auto count-watch?) | held — discipline |
| **T4** | `:constructed`→substrate-2 back-link shape (codex-5's excursion) | held — cross-agent |
| **CC** | completion pre-check vs IDENTIFY's two questions + §6 R1–R7 | held — VERIFY close |

### 11.1 T1 — abandonment risk: PASS (revive premise holds)

DERIVE/§10.3-trade-off-4 risk: "we reuse a 5-month-abandoned schema — maybe it was abandoned for
a reason we haven't found." Checked the full git history of `src/meme/`:
- **Exactly 3 commits**, all in Jan 2026 (`e85178a` 01-16 "Add meme layer (SQLite)…", `d34ce8d`
  01-23 "compass-to-meme bridge (Mission 4)", `d1a3710` 01-23 "Kolmogorov arrows… (Mission 5)"),
  then **zero further touches**.
- **No revert / remove / drop / abandon** commit anywhere in history touches meme.
- **No writer was ever wired into a caller and later removed** (pickaxe `-S "assert-arrow!"` /
  `-S "create-arrow!"` outside `src/meme/` finds nothing).

**Verdict:** abandonment = *built as Mission-4/5 scaffolding, then mission attention moved on and
the writer was never wired* — plain neglect, **not a shape rejection or a ripped-out integration.**
This corroborates §2 and the grain-spike (§9.1, shape fits with no strain). The "revive `meme`"
conclusion is safe on the abandonment axis. **T1 closed.**

### 11.2 VERIFY core — endpoint-identity logic-model: VERIFIED

Per [[feedback_logic_model_before_code]], the keystone (endpoint-identity) is checked as a
core.logic+pldb model over an abstract store-trace BEFORE any store code.
Lab: `holes/labs/M-memes-arrows/endpoint_identity_model.clj` (runnable; core.logic via `-Sdeps`).
Five invariants:

| # | invariant | level |
|---|---|---|
| I1 | endpoint-uniqueness — no two distinct rows share `(have, want)` | snapshot (the keystone) |
| I2 | construction iff `:constructed` | snapshot |
| I3 | monotone advance — no `:promote` regresses/stalls state | operation |
| I4 | unify-not-mint = **Contract C** — no `:mint` for endpoints an existing row holds | operation |
| I5 | node-reuse (R1) — every endpoint is an existing node | snapshot |

**Result:** conforming witness (the r3a arrow minted once, promoted correlated→open→constructed,
ending `:constructed` with a construction) yields **0 violations**; each of the 5 adversarial
traces is **CAUGHT** by its target invariant. Model **VERIFIED**.

**Finding (surprise): I1 and I4 are coupled — and that is the design working.** The I1 adversarial
(a duplicate-endpoint row) trips *both* I1 and I4: once two rows share endpoints, the original
mint "should have unified" with the duplicate. This is honest, not a model bug — **I4 (Contract C,
operation-level) is precisely the guard that PREVENTS the I1 (snapshot) violation.** They are two
views of one guarantee: enforce unify-by-endpoint at the mint/promote step → endpoint-uniqueness
holds in the snapshot. That sharpens the build order: **endpoint-extraction + Contract C is not
optional polish — it is the mechanism that makes endpoint-identity true.**

#### PUR — Pattern Use Record
- Pattern: `mission-coherence/logic-model-before-code`
- Actions taken: encoded 5 endpoint-identity invariants as core.logic+pldb over an abstract
  store-trace; conforming witness (r3a maturation) + one adversarial fixture per invariant.
- Outcome: **success** — conforming 0 violations; all 5 adversarials caught; model VERIFIED.
- Prediction error: expected 5 cleanly-isolated catches; got I1↔I4 coupling instead — a *useful*
  surprise that re-confirmed Contract C as the operation-level enforcer of the snapshot keystone,
  not a separable nicety.
- Note: this verifies the DESIGN (invariants are well-formed, conforming passes, adversarials
  caught), not an implementation — exactly the phase's intent.

### 11.3 VERIFY hook EP — endpoint-extraction spike: PASS (with a named requirement)

Contract C's precondition (claude-5): can a clean `(have, want)` be derived from a typed
`missing-head` signal? Lab: `holes/labs/M-memes-arrows/endpoint_extraction_spike.clj` (runnable,
no deps). What makes it implementable: a `missing-head` ALWAYS means the same typed thing — "the
head computes **locally** (have) but is not **readable by the WM head** (want)" — so `(have, want)`
is a pure function of the canonical head id.

**Results:**
- **Extraction clean:** all three real source conventions — live WM priority `{:type "missing-head"
  :id "mission-aif-head"}`, miner mint `:sorry/aif-head-missing-mission-aif-head`, legacy registry
  `:sorry/mission-aif-head-not-served` — resolve to the **same** `(have, want)` =
  `(aif-head/mission-aif-head/local, aif-head/mission-aif-head/wm-readable)`.
- **Ground-truth validated:** the extracted pair == the documented endpoints of the *real resolved*
  sorry `:sorry/mission-aif-head-not-served`, whose construction (the `:constructed` payload) is
  "WM head reads the head's local computation in-process; `scan-aif-heads` un-stubbed → `:available?`
  true". So the extraction matches a real arrow whose three-state history is on record.
- **The unify payoff + THE FINDING:** a fresh miner mint and the historical hand-curated id must
  unify to one arrow. **Naive regex extraction FAILS** (legacy `<head>-not-served` mangled →
  `mission-aif-head-not-served` ≠ miner's `mission-aif-head`) — exactly the same-endpoint/different-id
  collision claude-5 said Contract C must catch. **Registry-resolved canonicalisation PASSES** (both
  → `mission-aif-head` → one arrow).

**Named requirement falling out (precise, for INSTANTIATE):** endpoint-extraction must
**canonicalise the head id against the typed AIF head registry (`scan-aif-heads`), NOT string-munge**
the mint-id. With that, Contract C's unify-by-endpoint (logic-model I4) actually fires; without it,
it silently degrades to synthetic-id-proxy and lets duplicates through — re-confirming, from the
implementation side, the I1↔I4 coupling §11.2 found. This requirement lives on the **promotion step
(E-cheesemonger)** where Contract C sits — a concrete coordination item with claude-5's miner.

**EP closed: PASS** — the keystone's precondition is real, validated against a documented arrow, and
its one sharp requirement (registry-canonicalisation) is named rather than assumed.

### 11.4 VERIFY hook T2 — meme↔notions similarity join: PASS (layered design's other half)

The §9.5 layered claim: relational arrow store **×** notions/embedding layer, composed by shared
ids, with the embedding layer surfacing which cascades are ripe. Lab:
`holes/labs/M-memes-arrows/similarity_join_spike.py` (runnable; uses the *real repaired* minilm
pattern index — 1071 usable 384-d vectors — no embed-server, since the query pattern is already
indexed). Query endpoint: `aif/expected-free-energy-scorecard` (a §10.7 cascade endpoint).

**Result 1 — the join works: 8/8.** Every embedding-nearest neighbour id is also a real relational
pattern node, so the two layers share a key — once the lib prefix is stripped (`aif/<name>` ↔ stem
`<name>`). **Same canonicalisation lesson as EP (§11.3)** — the join key must be canonical, re-found
independently.

**Result 2 — cross-validation is rich (combining-methods-as-diagnostic, validated live).** Comparing
the embedding-nearest vs the co-occurrence-nearest of the same endpoint:
- **AGREE (both methods):** `candidate-pattern-action-space`, `policy-precision-commitment-temperature`,
  `structured-observation-vector`, `term-to-channel-traceability` — i.e. **exactly the §10.7 EFE
  cascade core (4/4).** Two independent signals corroborate the real cascade. This is the embedding
  layer doing its §10.0 job: surfacing which co-occurrences are *semantically* real cascade-seeds.
- **embedding-only** (`predictive-entropy-as-ambiguity`, `free-energy-as-tick-scalar`,
  `belief-state-operational-hypotheses`, `experimental-comparison-of-EFE-variants`): EFE-family kin
  that haven't *yet* co-fired → **candidate new cascade edges** (a discovery signal).
- **co-occurrence-only** (`stop-the-line`, `aif-as-environment-not-instruction`,
  `structural-tension-as-observation`, `hierarchical-budget-aware-action-selection`): patterns that
  co-fire for *workflow/structural* reasons, not semantic kinship → the **diagnostic signal**
  (similarity correctly *down-weights* methodology-noise like `stop-the-line`, which co-occurs with
  everything).

So the layered composition is real **and** the two layers' agreement/disagreement is exactly the
seeding-and-diagnostic mechanism the ARGUE thesis (§10.0) relies on — corroborated on real data,
not asserted.

**T2 closed: PASS.** Both halves of the layered design are now empirically verified (the relational
keystone via the logic-model §11.2 + EP §11.3; the similarity join here). Carried requirement
(shared with EP): the join key must be canonicalised (stem ↔ `aif/<name>`).

### 11.5 VERIFY close — completion pre-check + T3/T4 carried to mission exit conditions

**Completion pre-check (CC) against IDENTIFY + §6 R1–R7:**
- IDENTIFY Q "is futon3a storage *used* today?" → **answered** (MAP Q2: pattern path hot @ 5085
  activations/14d; meme empty/write-orphaned).
- IDENTIFY Q "does it have the *mechanism* a cascade store needs?" → **answered, yes** (meme
  `proposals`/`arrows`/`promotions`; grain-spike §9.1 + logic-model §11.2 verified the shape).
- **R1 node-reuse** → endpoint-identity, VERIFIED (logic-model I1/I5, EP §11.3). *keystone.*
- **R2 typed wires (mode×confidence×lifecycle)** → the three states, VERIFIED (§9.1).
- **R3 diagram as first-class named composite** → **PARTIAL.** A `:constructed` arrow carries the
  wiring diagram as its payload (so a diagram *is* a constructed arrow) — but the *named-composite
  grouping* (member-nodes/wires + diagram-level confidence) is **not** designed. Flagged: either a
  thin `diagram-id` facet on arrows or a follow-on. → INSTANTIATE deferral note, not a blocker.
- **R4 attestation per wire** → co-app/attestation data exists and the join uses it (T2). VERIFIED.
- **R5 live writer** → the one thing still unbuilt → **INSTANTIATE H1.**
- **R6 consult-as-needed read** → `arrows-from` + similarity query, VERIFIED (T2).
- **R7 boundary as facet** → proposals/promotions priors↔facts, VERIFIED + triangulated (§9.6-C).

**VERIFY verdict:** the design is **sound and empirically checked** — keystone (CORE+EP), layered
join (T2), revive-safety (T1) all pass; R3 partial (noted), R5 is the build. **VERIFY COMPLETE.**

**Carried forward as MISSION-LEVEL EXIT CONDITIONS (per Joe 2026-06-09):**
- **T3 — Contract A tripwire:** an automatic count-watch that flags if persisted sorry-count leaves
  the ~10³–10⁴ envelope (the only thing that reopens the triple-store option). → INSTANTIATE H6.
- **T4 — substrate-2 promotion back-link:** the `:constructed`→`code/v05/sorry` fact projection with
  a `:promoted-from` back-link, on E-substrate-2-sorry-typing's seam. → INSTANTIATE H5.
- The mission does not close until T3 + T4 are satisfied.

**Ownership change:** **codex-5 fell off Agency (2026-06-09)** — the M-a-sorry-enterprise / miner-side
work behind Contracts A/B/C (and E-substrate-2-sorry-typing's seam) is **now owned directly** from
this mission, not handed to claude-5. The Contracts remain the spec; we build both sides.

## 12. INSTANTIATE — as well-demarcated handoffs (2026-06-09, claude-4)

Per Joe: write INSTANTIATE as a series of well-demarcated handoffs. Each is scope-bounded (R11 /
CLAUDE.md Codex-handoff protocol): goal, `:in` (read-only) / `:out` (create), an **acceptance bar
tied to the VERIFY artifact it must satisfy** (so a reviewer checks the build against an existing
spike/logic-model, not a vibe), and gates. Dependency-ordered. **Authored, not yet dispatched** —
codex-5 is off Agency, so dispatch goes to an idle Codex from the pool (or owned directly); the
Claude owner reviews each (author ≠ reviewer).

**Dependency graph:** H1 → {H2, H4, H6}; H1+H2 → H3 → H5.

### H1 — Revive the meme writer (R5; the single most concrete need)
- **Goal:** a live writer so `meme.db` is created and arrows persist; close the read-wired/
  write-orphaned gap (§2).
- **:in (read-only):** `src/meme/{core,arrow,schema}.clj`; `holes/labs/M-memes-arrows/grain_spike.clj` (the shapes).
- **:out:** a writer ns/fn + one real caller wiring; `MEME_DB_PATH` set to a canonical path.
- **Acceptance (vs VERIFY):** re-run `grain_spike.clj` semantics against the *persisted* DB — all
  three §9.1 instances write + read back identically; `arrows-from` returns them. Round-trip test.
- **Gates:** clj-kondo clean; `dev/check-parens.el`; futon3a tests; **never restart the serving JVM**
  (write via script/`MEME_DB_PATH`, not by mutating the live store).

### H2 — Endpoint-extraction, registry-canonicalised (EP §11.3 named requirement)
- **Goal:** `missing-head signal → (have, want)` via canonical head-id resolution against the **typed
  AIF head registry (`scan-aif-heads`)**, NOT regex.
- **:in:** `holes/labs/M-memes-arrows/endpoint_extraction_spike.clj` (validated logic); `futon3c.aif`
  heads + `scan-aif-heads`; `futon3c/.../loop_learning.clj` (signal shapes).
- **:out:** `extract-endpoints` + `canonicalize-head-id` over the *live* registry.
- **Acceptance (vs VERIFY):** the EP spike's ground-truth test passes against the live registry
  (the `mission-aif-head` case); legacy `<head>-not-served` and miner `aif-head-missing-<head>` ids
  canonicalise to one head-id (the §11.3 unify result, live).
- **Gates:** clj-kondo; tests; read-only on futon3c (no writes).

### H3 — Endpoint-identity store + unify-by-endpoint promotion (Contract C; logic-model I1–I5)
- **Goal:** arrows keyed by `(have, want)`; `promote!` = find-by-endpoint → advance state → absorb
  same-endpoint tokens (never mint a dup).
- **:in:** `endpoint_identity_model.clj` (the invariants); `meme.arrow`; H1, H2.
- **:out:** the endpoint-keyed store layer + `promote!`; a **live conformance probe** lifting the 5
  offline invariants to the running store (per the logic-model's "may register as live probe families").
- **Acceptance (vs VERIFY):** the live probe reports **0 violations** on a conforming sequence and
  **catches** each of the 5 adversarial mutations — i.e. the §11.2 logic-model, now over real rows.
- **Gates:** clj-kondo; tests; the probe is the regression gate.

### H4 — meme↔notions similarity join + cascade-seeding query (T2)
- **Goal:** the canonical `stem ↔ aif/<name>` join + the seeding query ("given X: similar AND
  co-occurring AND no-construction = candidate sorry").
- **:in:** `similarity_join_spike.py` (the validated join + cross-validation); `futon/notions.clj`
  `search-embeddings`; `futon6 pattern_phylogeny` co-app.
- **:out:** a join fn + the seeding query, over the live (repaired) minilm index.
- **Acceptance (vs VERIFY):** reproduce the T2 result as a callable — 8/8 join, the 4/4 corroboration,
  and surface ≥1 candidate sorry for a real high-attestation/no-construction cascade.
- **Gates:** clj-kondo (if Clojure) / `bash -n` (if script); the freshness guard from
  E-patterns-and-missions-live still green.

### H5 — `:constructed` → substrate-2 promotion back-link (T4 exit condition; now owned directly)
- **Goal:** when an arrow reaches `:constructed`, write the fact-side `code/v05/sorry` vertex on
  E-substrate-2-sorry-typing's seam with a `:promoted-from` back-link; preserve the priors↔facts boundary.
- **:in:** `futon3c/holes/missions/E-substrate-2-sorry-typing.md` + its `build-sorry-registry-docs`
  ingest seam; §9.6-C corroboration.
- **:out:** the promotion writer + back-link; honors the gated two-step (no direct-to-substrate-2).
- **Acceptance (vs VERIFY):** a `:constructed` arrow promotes to a `code/v05/sorry` vertex carrying
  `:promoted-from <endpoint-key>`; round-trips; an `:open` arrow does NOT appear in substrate-2 (R7).
- **Gates:** clj-kondo; tests; **codex-5's excursion is now ours** — reconcile its INSTANTIATE state first.

### H6 — Contract A count-watch tripwire (T3 exit condition)
- **Goal:** a cheap automatic watch that flags when persisted sorry/arrow count leaves the ~10³–10⁴
  envelope (the only event that reopens the fast-triple-store option).
- **:in:** Contract A (`project_sorry_arrow_contracts.md`); H1's store.
- **:out:** a count probe + a loud flag (log/check-hook) at the threshold.
- **Acceptance (vs VERIFY):** fires on a synthetic over-threshold count; silent below. Satisfies the
  T3 exit condition (turns Contract A from a promise into a tripwire).
- **Gates:** clj-kondo / `bash -n`; tests.

**INSTANTIATE exit = H1–H4 built + reviewed, H5+H6 (the T4+T3 exit conditions) satisfied, R3
disposition decided (thin `:diagram-id` facet vs follow-on). Each handoff reviewed by the Claude
owner against its named VERIFY artifact before it counts as done.**

### 12.0 Worked-example discipline (Joe 2026-06-09) — amends every handoff

The "show it working on a real example" bar that carried ARGUE (§10.7) now applies to **every**
INSTANTIATE handoff, with extra weight on **phase boundaries** (the seams between handoffs — where
wiring gaps surface, per [[feedback_qa_means_qa]]). Two binding rules:

1. **A handoff is DONE only when it ships a runnable worked example on a NAMED real instance** — an
   artifact under `holes/labs/M-memes-arrows/worked-examples/`, reviewed by the Claude owner.
   Reproducing the spike is necessary but **not sufficient**: the example must run end-to-end through
   *that handoff's real surface* (the persisted store, the live registry, etc.), not the offline spike.
2. **Examples ACCUMULATE into a gallery** (`worked-examples/GALLERY.md` indexing each). Target: **≥4
   distinct arrows worked through the lifecycle by mission end**, spanning the BHK sorry-flavours
   (§9.2) — so the design is shown working *across kinds*, not on one. This gallery is itself a
   generalisation test (see the flavour note below).

**Per-handoff named worked example (amends the §12 acceptance bars):**
| handoff | named real instance | what the example must show working |
|---|---|---|
| H1 | r3a arrow + 2 others | written to the persisted `meme.db`, read back identical |
| H2 | `mission-aif-head` + a 2nd head | endpoints extracted from the *live* registry; legacy+miner ids canonicalise to one |
| H3 | r3a arrow | promoted `correlated→open→constructed` in the live store + the duplicate-mint **unified** (not dup'd) |
| H4 | the EFE cascade | a candidate `:open` sorry **seeded** from real similarity+co-occurrence+no-construction |
| H5 | `support-coverage` constructed arrow | promoted to a substrate-2 `code/v05/sorry` vertex with `:promoted-from`; `:open` stays out |
| H6 | synthetic over-threshold count | the tripwire **fires** loudly; silent below |

### 12.7 Phase-boundary demonstrations + the gallery

**Boundary demos (each is a worked example that exercises a SEAM, not a single handoff):**
- **B1 (H1→H3):** an arrow *written* by H1's writer is *promoted* by H3 — proves persistence ↔
  lifecycle compose with no schema mismatch.
- **B2 (H2→H3):** the `(have,want)` *extracted* by H2 is the exact key H3 *unifies on* — proves the
  canonicalisation seam (the EP/T2 lesson) holds live, not just in spikes.
- **B3 (H4→H3):** a candidate *seeded* by H4 is *minted* into H3 as an `:open` arrow — proves the
  similarity→store boundary.
- **B4 (H3→H5):** a `:constructed` arrow *promotes* to substrate-2 via H5 while an `:open` one does
  **not** — proves the priors↔facts boundary (R7; the most dangerous seam).
- **B5 (capstone, H4→H3→H5):** ONE arrow walked cascade → sorry → construction → fact end-to-end —
  the live, multi-handoff realisation of the §10.7 reference case.

**Gallery target — ≥4 arrows across BHK flavours (§9.2), each a runnable artifact:**
- **→-sorry** (method): `r3a-likelihood-support-coverage` (token-closure; the §10.9 exemplar).
- **∃-sorry** (witness): `stub-lifts-pending-aif-edn` (exhibit a companion `.aif.edn`).
- **∀-sorry** (uniform method): `r3d-per-entity-attribution` (per-entity-uniform update).
- **capstone**: the B5 cascade→sorry→construction→fact walk.

**Note (the gallery is a generalisation test):** endpoint-extraction (H2/EP) is validated only for the
`missing-head` class. The `∃`/`∀` flavour examples will likely show H2 needs **per-flavour
extractors** (an `∃`-sorry's `(have,want)` comes from its story↔companion-artifact, not an AIF head).
That is a *wanted* finding — building multiple worked examples is precisely how we discover whether
endpoint-extraction generalises or fragments by flavour. Recorded as an open INSTANTIATE risk, to be
answered by the gallery rather than assumed away.

### 12.8 INSTANTIATE close — H1–H6 complete (six-handoff loop, 2026-06-09)

The `/loop` ran H1→H6 as six handoff→review cycles to **codex-2**, each reviewed by the Claude owner
against its named VERIFY artifact (author ≠ reviewer), each landing a runnable worked example. **All
six PASS.**

| H | builds | worked example (PASS) | commit | review note |
|---|---|---|---|---|
| H1 | meme writer (R5) | `h1-meme-writer.clj` — 3 arrows persist + round-trip | (in tree) | I fixed non-determinism directly; clj-kondo 0/0 |
| H2 | endpoint-extraction (EP) | `h2-endpoint-extraction.clj` — live `scan-aif-heads`, 3 conventions→1 | `8ca2833` | finding: live head-ids include UUIDs |
| H3 | endpoint-identity + Contract C (keystone) | `h3-endpoint-identity.clj` — r3a one-row maturation, dup unified, live probe 0/5-of-5 | `e881cfa` | genuine probe (reads live store) |
| H4 | similarity join | `h4-similarity-join.py` — 8/8 join, 4/4 corroboration, **4 candidate sorries seeded** | `ab38c12` | the §10.0 demand mechanism, live |
| H5 | substrate-2 back-link (T4) | `h5-substrate2-promotion.clj` — projection + `:promoted-from` + `:open` refused (R7) | `caf7c20` | **NON-LIVE**; live `:7071` write pending operator OK |
| H6 | Contract A tripwire (T3) | `h6-count-watch.clj` — silent below, fires at 10001 | `39dd879` | clj-kondo 0/0 |

New futon3a code: `src/meme/{writer,endpoints,identity,substrate2,count_watch}.clj` (the abandoned
meme store, **revived** — R5 closed and extended with endpoint-identity, Contract C, the
substrate-2 projection, and the Contract-A watch). Gallery: `holes/labs/M-memes-arrows/worked-examples/GALLERY.md`.

**Worked-example flavours demonstrated live:** →-sorry (full lifecycle, H3 maturation → H5
fact-projection) and cascade-seed (H4). 

**What remains before the mission closes (the carried exit conditions + the gallery stretch):**
1. **T4 — the LIVE substrate-2 write** (operator-gated): H5 is a non-live fixture; the live `:7071`
   wiring needs Joe's go-ahead.
2. **T3 — wired into the running loop:** H6 builds the tripwire; arming it on the live store is the
   last T3 step.
3. **Gallery stretch (the "multiple worked examples" target):** the ∃-sorry (`stub-lifts`) and
   ∀-sorry (`r3d-per-entity-attribution`) flavour examples + the B1–B5 boundary demos remain ⬜.
   The ∃/∀ examples are the **generalisation test** of endpoint-extraction (does it need per-flavour
   extractors?) — an open question the loop deliberately left for the gallery to answer.

**Loop mechanism note:** ScheduleWakeup did not fire reliably in the emacs-claude-repl surface;
**background-Bash pollers (completion-notified) were reliable** and drove cycles H3–H6 cleanly.

### 12.9 T4 exit condition SATISFIED — live substrate-2 write (operator-greenlit 2026-06-09)

Joe greenlit the live substrate-2 write ("as long as we keep track of it") and asked for
`README-memes-and-arrows.md` documenting the build + substrate-2 implications. Both done:
- **`README-memes-and-arrows.md`** authored (what's built, the 5 `meme.*` namespaces, the invariants,
  the priors↔facts implications, and a **live-write log** §7 that tracks every promotion).
- **Live write performed + verified:** `h5-live-substrate2-promotion.clj` promoted the r3a
  `:constructed` arrow into substrate-2 as `hx:code/v05/sorry:futon3a/sorry/meme-arrow-6b69271667003880`
  (POST 200, penholder `api`, read back by endpoint, `promoted-from` back-link intact). Idempotent
  (deterministic endpoint id → upsert). Two real bugs fixed en route: penholder must be `api`;
  futon1a responses are EDN not JSON.

**T4 (the substrate-2 back-link, a mission exit condition) is SATISFIED** — non-live fixture (H5)
*and* a real tracked live promotion. Remaining for mission close: **T3** (arm the count-watch on the
live store) + the gallery stretch (∃/∀ flavour examples + B1–B5 boundary demos).

### 12.10 T3 exit condition SATISFIED — count-watch armed on the live store (2026-06-09)

`t3-count-watch-armed.clj`: seeds the canonical `meme.db` idempotently (3 real arrows via
`mint-or-unify!`, endpoint-keyed → re-runs don't grow the count), then arms `count-watch/watch!`
on it. Live count = 3, **silent** (under the 10⁴ envelope), but **fires** on a synthetic breach.
**T3 (Contract-A tripwire) is SATISFIED** — built (H6) + armed on the live store. Both mission exit
conditions (T3, T4) are now met; remaining is the optional gallery stretch (∃/∀ flavours + B1–B5).

### 12.11 ∃/∀ flavour gallery — generalisation question ANSWERED (2026-06-09)

`flavour-gallery-exists-forall.clj` builds the two remaining BHK flavours as real arrows:
- **∃-sorry** (witness, H6): `:sorry/stub-lifts-pending-aif-edn` — `story-stub/leaf-1 → companion-aif-edn/leaf-1.aif.edn`.
- **∀-sorry** (uniform method, H5): `:sorry/r3d-per-entity-attribution` — `global-uniform-belief-update → per-entity-contribution-weighted-update`.

Both **mature `correlated→open→constructed` in the same store**, exactly like →. But endpoint-extraction
(`meme.endpoints`) **refuses** both (`head id not found in registry`) — it derives `(have,want)` from a
typed AIF head, which ∃/∀ don't have.

**Finding (the open INSTANTIATE risk, now answered):** the endpoint-identity **STORE generalises across
flavours** (one uniform `mint-or-unify!`/`promote!` for →/∃/∀); endpoint-**EXTRACTION is per-flavour**
(missing-head today; ∃ needs story↔companion-artifact, ∀ needs aggregate↔per-entity). So the design
generalises where the value is (the store, never duplicated) and fragments where it must (extraction is
inherently signal-specific). **Three flavours now demonstrated live** (→ via H3/H5/live-write; ∃ and ∀ here).

### 12.12 B5 CAPSTONE — one arrow, four stages, live (2026-06-09)

`b5-capstone-live.clj`: a single live run walks **one endpoint-keyed arrow**
(`belief-mass-on-supports-tagged-cohort → support-coverage-channel`) through all four stages —
**cascade (`:correlated`) → sorry (`:open`) → construction (`:constructed`) → durable fact
(substrate-2 `code/v05/sorry`)** — with the **row id stable across every stage** (one object, not
re-minted) and the `promoted-from` back-link intact in substrate-2. Idempotent (upserts the same
hyperedge `hx:…meme-arrow-6b69271667003880`). This is the live realisation of the §10.7 reference
case: correlation → conjecture → proof → fact, identity preserved by endpoint-key throughout.

The capstone exercises every seam (persistence↔lifecycle, the unify key, similarity→store,
priors↔facts), so B1–B4 are covered by it. **The gallery is complete:** all three flavours
(→/∃/∀), all six handoffs, the live substrate-2 write, the armed tripwire, and the capstone — each
a runnable worked example. INSTANTIATE is done; both mission exit conditions (T3, T4) are met.

## 13. CLOSE — mission complete (2026-06-09)

**DOCUMENT phase:** satisfied by `futon3a/README-memes-and-arrows.md` (Joe, 2026-06-09: "Document
is closed by the README you made") — it documents what was built, the 5 `meme.*` namespaces, the
endpoint-identity keystone + invariants, the worked-example gallery, the substrate-2 implications,
and a live-write log. Mission marked **COMPLETE**.

**What this mission delivered (HEAD → close):**
- Answered Joe's HEAD worries: the meme graph *was* empty (write-orphaned), futon3b *is* silently
  load-bearing, and futon3a *is* the right altitude for cascades — then **revived the meme store**
  rather than building anew (abandonment was neglect, not a shape problem).
- The thesis (ARGUE, broken-and-rebuilt under claude-5's review): cascade / sorry / construction are
  **one arrow-type keyed by `(have, want)`** in three states — a sorry is a hole in a proof, not a
  wishlist item; "one home" is a *mechanism* (endpoint-identity), not a filing preference.
- VERIFY: endpoint-identity logic-model (0 conforming / 5-of-5 adversarial), endpoint-extraction +
  similarity-join spikes, revive-safety — all PASS.
- INSTANTIATE: 6 handoffs to codex-2, each reviewed against a named VERIFY artifact; the store is
  live, a real `:constructed` arrow promoted to substrate-2, the Contract-A tripwire armed.
- A complete **worked-example gallery** (the discipline Joe asked for): every claim shown working on
  a real instance, across all three BHK flavours, with the B5 capstone walking one arrow end-to-end.

**Open follow-ons (recorded, not blocking the close):**
1. **Per-flavour endpoint extractors** — the store generalises across →/∃/∀, but extraction is
   missing-head-specific (§12.11). ∃ needs story↔companion-artifact; ∀ needs aggregate↔per-entity.
2. **B1–B4 isolated seam demos** — covered by the B5 capstone; build standalone if ever needed.
3. **R3** named-diagram composite (a `:constructed` arrow carries the diagram as payload; the grouping
   object is deferred — §11.5).
4. **The sibling miner side (M-a-sorry-enterprise):** Contract C's promotion-step unify-by-endpoint +
   the cross-source maturation that r3a's hand-edited row does not yet witness (§10.10) — now owned
   directly (codex-5 off Agency).

---

**Cross-referenced excursion (post-close, 2026-06-09):** `E-wm-policy-arrow-seam.md` — the warranted
home for the WM-policies Track-2 ascent seam (`:advances-cap` column + `promote!` validate/route/write
against claude-3's `:7071` capability overlay). Contract LOCKED via whistle salvo; implementation
pending dispatch (codex-2 + claude-4 review). New scope → an excursion, not a reopen of this mission.
