# What the BHK Interpretation Really Is

Status: archived

*A research note for sharpening the informal phrase "a BHK arrow is a proof step."*

**Short answer, up front.** A BHK "arrow" `A → B` is not a relation that *holds* between `A` and `B`, nor an *assertion* that `B` follows; it is a **construction** — an effective method/function — that, applied to *any* construction proving `A`, *produces* a construction proving `B`. The load-bearing words are **construction**, **method/function**, and **transform**, not "step." See the synthesis in §7.

---

## 1. Origins and the three strands

The BHK interpretation is the standard informal explanation of the *meaning* of the intuitionistic logical constants in terms of constructions/proofs rather than truth-conditions. It braids three independent strands [SEP-Dev][nLab][Rodin].

**Brouwer (intuitionism, 1907–1927).** Brouwer never wrote down clauses, but he supplied the foundational stance: what is preserved by valid inference is *constructibility*, not mind-independent truth. Mathematics is mental construction, prior to and independent of language and logic; logic is a *post hoc* description of regularities in constructive activity. His bar-theorem proofs (1924, 1927) already used implication operationally — as something that *transforms* demonstrations — before that reading was made explicit [SEP-Dev].

**Heyting (the *proof* interpretation, 1930–1934).** Heyting turned Brouwer's stance into an explicit *meaning explanation* of each connective. After early vague phrasings ("if `a` is correct, then `b` is correct"), in an October 1930 letter to Freudenthal he stated the transformation clause for implication — "I possess a construction that derives from every proof of `a` a proof of `b`" — published in Heyting 1934. Heyting frames propositions phenomenologically as *intentions* whose assertion is the *fulfilment* of the intention by a construction [SEP-Dev].

**Kolmogorov (the *calculus of problems*, 1932).** In *Zur Deutung der intuitionistischen Logik* (*Math. Zeitschrift* **35**, 58–65), Kolmogorov independently read Heyting's calculus as a **calculus of problems (*Aufgaben*) and their solutions**, not of propositions. Each formula denotes a *problem*; the connectives build compound problems; a "proof" is a *solution*. His paper "contains no technical results" but argues the calculus should be interpreted in terms of problems rather than sentences [Rodin §1][SEP-Dev].

**Where Kolmogorov differs in emphasis from Heyting.** This is subtle and often misreported. Per Rodin's primary-source analysis [Rodin §1–2]:

- **Philosophically divergent.** Kolmogorov *rejected* mathematical intuitionism as a philosophy of mathematics; Heyting followed Brouwer. Kolmogorov did **not** intend his calculus to belong to intuitionistic mathematics. His aim was to *extend* classical logical analysis with a *new* theory dealing with *problems*, keeping classical reasoning over propositions intact [Rodin §1, abstract].
- **Two kinds of object, kept apart.** For Heyting (in his mature 1934/1956 writings) the difference between "problem" and "proposition" is *merely linguistic* — proofs and solutions are the same notion in two registers. Kolmogorov insisted the two be kept distinct and did **not** apply his calculus to propositions, wanting "a unified logical apparatus dealing with objects of two types — propositions and problems" [Rodin §1–2, quoting Kolmogorov's *Collected Works*].
- **Algorithmic/operational flavour.** Kolmogorov's *reducibility* of one problem to another is the analogue of implication: "If we can reduce the solution of problem `b` to the solution of problem `a` … then …" — and his elementary axioms are literally *postulated as already-solved problems*, in Euclid's sense of a postulate as a construction-task ("to construct a straight line between two points"), not an asserted truth [Rodin §2.1, §2.3]. This is the reading that anticipates Curry–Howard and HoTT's problem/proposition split.

**Why "BHK."** The name was coined by **Troelstra (1977)**, where the "K" initially stood for **Kreisel**; it was later corrected to **Kolmogorov** (Troelstra 1990), with the idea that Heyting and Kolmogorov "have an equal share." So the now-standard acronym postdates all three authors and is, historically, a convenient label rather than a self-description [SEP-Dev][Rodin §1, n.3]. Note Rodin's caution: the *unified BHK notion is coherent*, but flattening Kolmogorov's problems-vs-propositions distinction into Heyting's "same thing" misrepresents Kolmogorov's actual contribution.

---

## 2. The clause-by-clause definition

The canonical formulation (Troelstra & van Dalen, *Constructivism in Mathematics*, reproduced in [SEP-Dev]; matched by [nLab][Wiki]). Read "proof of `X`" as "construction witnessing `X`." Let `D` be the domain of quantification.

- **(H0) Atomic `A`.** A proof of an atomic proposition is a mathematical construction *in Brouwer's sense* that makes `A` true (this clause is what grounds the whole inductive definition in actual mathematics).
- **(H1) `A ∧ B`.** A proof is a **pair** `⟨a, b⟩` where `a` proves `A` and `b` proves `B`.
- **(H2) `A ∨ B`.** A proof is a **pair** `⟨i, c⟩` with a *tag* `i ∈ {0,1}`: either `⟨0,a⟩` with `a` proving `A`, or `⟨1,b⟩` with `b` proving `B`. The tag — *which disjunct* — is part of the datum. This is the **constructively loaded** clause: classical `∨` only asserts `¬(¬A ∧ ¬B)`, "not both fail," which need not *deliver* either disjunct [SEP-Log][Wiki].
- **(H3) `A → B`.** A proof is a **construction (method/function)** that transforms *any* proof of `A` into a proof of `B`. Higher-order: its input and output are *themselves proofs*.
- **(H4) `¬A`** (defined as `A → ⊥`). A proof is a construction transforming any *hypothetical* proof of `A` into a proof of `⊥` (a contradiction) [SEP-Dev][Wiki].
- **(H5) `∀x∈D. A(x)`.** A proof is a **construction (function)** transforming any `d ∈ D` into a proof of `A(d)`. Higher-order/uniform, like `→`.
- **(H6) `∃x∈D. A(x)`.** A proof is a **pair** `⟨d, a⟩`: an explicit *witness* `d ∈ D` together with a proof `a` of `A(d)`.
- **`⊤`:** a canonical (trivial) proof exists. **`⊥`:** there is **no** proof.

**The higher-order point (load-bearing for §3).** `→` and `∀` are the *function-valued* clauses: their proofs are not finished objects but **methods** that must *act* on arbitrary inputs and *return* proofs. `∧`, `∨`, `∃` are *data* (pairs/tagged-unions/witness-pairs). Curry–Howard (§4) makes this exact: `→ ↦ function type`, `∧ ↦ product`, `∨ ↦ sum`, `∃ ↦ dependent pair`, `⊥ ↦ empty type`, `⊤ ↦ unit` [Wiki-CH][nLab].

---

## 3. THE CRUX: why a BHK object is a *construction*, not a relation or an assertion

This is the heart of the matter, and the place where "proof step" can mislead. Distinguish three things that an arrow `A → B` could denote:

**(a) An arbitrary binary relation `R(A,B)`.** A relation merely *records* that some pairing holds; it has no computational content and need not do anything. `R(A,B)` could be `true` because, say, both `A` and `B` happen to be provable, with no link between their proofs. A BHK arrow is emphatically **not** this: it is rejected precisely because a relation does not *transform* a proof of `A` into a proof of `B`. Knowing "the relation holds" gives you no method.

**(b) An *asserted* implication ("`B` follows from `A`").** Classically, `A → B` is a *truth value*: it is `true` iff `A` is false or `B` is true. The assertion can be correct with no witnessing procedure — e.g. it holds vacuously when `A` is false, or "by truth-table" when `B` is true, regardless of any connection. Kolmogorov's own footnote makes the analogous point about negation: from `¬a` one gets *the theorem that `a` is unsolvable*, "but not the converse" — *being-unsolvable* (a fact) and *having-a-refutation-construction* (a method) are different things, and the BHK clause demands the latter [Rodin §2.2].

**(c) A BHK construction that actually transforms proofs.** A proof of `A → B` is an **effective method `f`** such that for *every* construction `p` proving `A`, `f(p)` is a construction proving `B`. Three features mark it off from (a) and (b):
1. **It is an object you possess and can apply** ("a construction which *permits us to transform*"), not a fact that obtains [SEP-Dev].
2. **It is uniform/total over all proofs of `A`** — it must succeed on *any* input proof, not on a chosen one.
3. **It carries computational content**: running it on a witness *yields* a witness. Where classical implication is satisfied by the *absence* of counterexamples, BHK implication is satisfied only by the *presence* of a transformer.

**Is "proof step" the right term?** No — it is defensible shorthand but strictly *too weak and too local*. "Step" suggests a single inference-rule application inside a derivation (one line following from previous lines). A BHK arrow is the **whole transformer**, a first-class object of *function* type — closer to "the entire procedure that maps proofs to proofs" than to "one step." The authorities' own nouns are: **construction** (Brouwer/Heyting/Troelstra–van Dalen, the dominant term), **proof** (Heyting), **method** (the operational gloss on `→`/`∀`), **solution to a problem** (Kolmogorov), and — once formalised — **realizer** (Kleene) or **(typed) program/term** (Curry–Howard) [SEP-Dev][nLab][Rodin]. "Witness" is correct but properly belongs to the *data* clauses `∃`/`∨` (the witnessing element/disjunct), whereas `→`/`∀` are *methods*. If you want one word, use **construction** (interpretation-neutral) or **realizer** (when you mean the formal version).

**What is left primitive.** BHK is explicitly **informal / semi-formal**: it is a *meaning explanation*, not a model-theoretic interpretation translating one formal system into another [SEP-Dev]. The terms "construction," "proof," "method," and "transform" are taken as *primitive* and admit several precisifications — and *which* class of functions you allow for the `→`/`∀` clauses is exactly where different constructivisms (and §4's machineries) diverge [Streicher][nLab].

---

## 4. The formal underpinnings that make "proof = construction" precise

Two rigorous theories pin down the otherwise-primitive "construction." They formalise different facets and **do not coincide**.

**Curry–Howard (propositions-as-types, proofs-as-programs).** A direct structural isomorphism: *a proof is a program, and the proposition it proves is that program's type* [Wiki-CH]. Implication = function type, conjunction = product, disjunction = sum, `∃` = dependent pair, `⊥` = empty type; **proof normalisation = program evaluation**. Curry (1934–58) noticed combinator types matched intuitionistic axiom schemes; Howard (1969) extended this to natural deduction and typed λ-calculus; Martin-Löf's type theory is the dependently-typed home of the `∀`/`∃` clauses. The standard textbook is **Sørensen & Urzyczyn, *Lectures on the Curry–Howard Isomorphism* (2006)** [Wiki-CH]. Curry–Howard makes BHK's "method" exact: a proof of `A → B` *is* a (typed) λ-term, i.e. literally a program transforming proofs-of-`A`-terms into proofs-of-`B`-terms. As nLab puts it, BHK and propositions-as-types are "closely related to the point of being synonymous" once you fix λ-calculus as the function class [nLab].

**Kleene realizability (1945).** A number-theoretic semantics for Heyting Arithmetic: a number `e` **realizes** a formula, defined by recursion that mirrors BHK [SEP-Log]:
- `e` realizes `A → B` iff for every `f` realizing `A`, the `e`-th partial recursive function is defined at `f` and `{e}(f)` realizes `B` (the *method* is a **computable function**, coded by `e`);
- `e` realizes `A ∧ B` iff `e` codes a pair of realizers; `e` realizes `A ∨ B` iff `e` codes a *tag plus* a realizer of the chosen disjunct; `e` realizes `∃x A(x)` iff `e` codes a *witness `n`* plus a realizer of `A(n)`; `e` realizes `¬A` iff *no* `f` realizes `A`.

Realizability "replaces the vague notions of proof/construction/transformation with computable functionals" — the cleanest rigorous BHK-style semantics [SEP-Log][Realizability-Wiki]. Nelson's theorem: every theorem of HA is realizable.

**Where BHK and realizability come apart.** Realizability is *not* a faithful image of the intended (proof-)BHK reading; it is one *precisification* among several.
- It validates principles the proof-reading does **not** obviously give: **Church's Thesis** (`CT₀`) and **Markov's Principle (MP)** are *realizable*, but MP is *not* derivable in intuitionistic logic and is rejected on a strict BHK/Brouwerian reading; MP "exactly captures the difference between a constructive and classical meta-theory" for first-order arithmetic [SEP-Log][Markov-Wiki][CompMP].
- Conversely the *function class* is a free parameter: Kleene fixes **computable** functions; a Brouwerian might allow free-choice-sequence operations; a type-theorist allows typed λ-terms. "The BHK interpretation will depend on the view taken about what constitutes a function that converts one proof to another, and different versions of constructivism diverge on this point" [Wiki]. So realizability *sharpens* BHK but also *commits* it to choices BHK left open.

---

## 5. Examples (genuine BHK constructions)

Writing proofs as λ-terms / pairs makes the constructions explicit (Curry–Howard reading).

- **`A → A`.** The identity method `λx. x`: given any proof `x` of `A`, return it. A bona fide transformer.
- **`A ∧ B → A`.** First projection `λp. fst p`: given the pair `⟨a,b⟩` proving `A ∧ B`, return its first component `a`. (Symmetrically `λp. snd p` proves `A ∧ B → B`.)
- **`A → (B → A)`.** Constant-function former `λa. (λb. a)`: given a proof `a` of `A`, return the method that ignores any proof of `B` and hands back `a`. (This is combinator **K**; Curry's original observation was that **K**'s type *is* this axiom [Wiki-CH].)
- **`¬(A ∧ ¬A)`** (non-contradiction, fully worked, from [Wiki]). Recall `¬A = A → ⊥`. The proof is `f = λ⟨a,b⟩. b(a)`: given a pair whose first component `a` proves `A` and whose second component `b` proves `A → ⊥`, apply `b` to `a` to obtain a proof of `⊥`. So `f` realizes `(A ∧ ¬A) → ⊥`, i.e. `¬(A ∧ ¬A)`. Note: this is BHK-valid *constructively*, unlike its sibling LEM in §6.
- **Distributivity `A ∧ (B ∨ C) → (A ∧ B) ∨ (A ∧ C)`.** `λ⟨a, c⟩. case c of ⟨0,b⟩ ↦ ⟨0,⟨a,b⟩⟩ | ⟨1,d⟩ ↦ ⟨1,⟨a,d⟩⟩`: inspect the *tag* of the disjunction proof and rebuild the appropriately-tagged output pair. The construction must read the tag — exactly the constructive content classical logic discards.
- **A witnessed `∃`.** "There is a prime `> 10`" is BHK-proved by the pair `⟨11, p⟩` where `p` proves `11` is prime. The witness `11` is *part of the proof* — this is the existence property (§6) in action.
- **Reading a proof as a program.** By Curry–Howard, a natural-deduction proof of `A → B` *is* a λ-term; normalising the proof = running the program; the witness it extracts from an input proof = the program's output. "Proof = construction" is here literally "proof = executable term."

---

## 6. Counterexamples (NOT BHK constructions / not constructively valid)

These sharpen §3: each is a *true-or-asserted* statement with **no uniform construction**.

- **Law of Excluded Middle `A ∨ ¬A`.** By (H2) a proof must be a *tagged* pair: either a proof of `A` or a proof of `¬A = A → ⊥`. A *general* construction would be a uniform method that, for **every** proposition `A`, *decides* `A` and outputs the correct disjunct with its proof — i.e. a universal decision procedure. No such construction exists. SEP's **twin-primes** instance: let `A(x)` say "a twin-prime pair exists above `x`"; we cannot assert `∀x(A(x) ∨ ¬A(x))` because no method is known to settle it [SEP-Log]. So LEM fails *not because it is false* (intuitionistically `¬¬(A ∨ ¬A)` is a theorem) but because the *disjunct-plus-tag* cannot be produced uniformly [Wiki][SEP-Log].
- **Double-negation elimination `¬¬A → A`.** A proof would be a method turning any proof of `(A → ⊥) → ⊥` into a proof of `A`. But a proof of `¬¬A` only shows "assuming a refutation of `A` yields `⊥`" — it contains **no witness for `A`**. There is no way to *extract* a proof of `A` from the mere impossibility of its refutation. (If there were, LEM would follow by modus ponens from `¬¬(A∨¬A)`, collapsing intuitionistic into classical logic [SEP-Log].)
- **Classical `∃` without a witness: irrational `a,b` with `a^b` rational.** Classical proof: either `√2^√2` is rational — take `a=b=√2` — or it is irrational — take `a=√2^√2, b=√2`, giving `(√2^√2)^√2 = √2² = 2`. Either way such `a,b` exist. But the proof uses LEM on "`√2^√2` is rational" and **does not say which case holds**, so it delivers *no witness pair* `⟨a,b⟩` — it fails (H6). (One *can* repair it constructively, e.g. `a=√2, b=2·log₂3`, giving `a^b = 3`; and `√2^√2` is in fact transcendental by Gelfond–Schneider — but those are *different, witness-bearing* proofs, not the classical existence argument [HMC][GS].) This is the cleanest illustration that an *asserted* `∃` (§3b) is not a *constructed* `∃` (§3c).
- **An arbitrary relation / unwitnessed truth.** "Goldbach's conjecture or its negation holds" is *true* on the classical relation reading, but carries **no** BHK construction: we possess neither a verifying construction of Goldbach nor a refutation, hence neither tagged disjunct. Likewise a bare relation `R(A,B)` that "holds" tells you nothing transformable. These are precisely the §3(a)/(b) objects that the BHK `→`/`∨`/`∃` clauses *exclude*: truth-of-a-relation and correctness-of-an-assertion are not constructions.

---

## 7. Synthesis

**Crispest authoritative one-liner.** *A BHK arrow `A → B` is a construction — an effective method (function) — that transforms any construction proving `A` into a construction proving `B`* (Heyting 1934 / Troelstra–van Dalen, clause H3 [SEP-Dev]); equivalently, under Curry–Howard, *it is a program of function type sending proofs-of-`A` to proofs-of-`B`* [Wiki-CH][nLab], and under Kleene, *a computable functional `e` such that `{e}` carries realizers of `A` to realizers of `B`* [SEP-Log]. The unifying content across all three: a proof is a **construction with computational content**, not a truth-value, relation, or assertion.

**Verdict on "proof step."** *Defensible shorthand, but strictly looser than the sources warrant.* "Step" connotes one local inference inside a derivation; a BHK arrow is the **whole proof-to-proof transformer**, a first-class object of *function/method* type. The disciplined nouns are **construction** (interpretation-neutral, Brouwer/Heyting/Troelstra), **method** (the operational gloss on `→`/`∀`), **solution-to-a-problem** (Kolmogorov), **realizer** (Kleene, formal), and **program/typed-term** (Curry–Howard, formal); reserve **witness** for the *data* clauses `∃`/`∨`. If you want a single stricter word, say **construction** (or **realizer** when you mean the formalised version) rather than "proof step."

---

## References

- **[SEP-Dev]** Mark van Atten, "The Development of Intuitionistic Logic," *Stanford Encyclopedia of Philosophy*. https://plato.stanford.edu/entries/intuitionistic-logic-development/ — quotes the Troelstra–van Dalen BHK clauses (H0–H6), the Heyting/Freudenthal 1930 letter, Kolmogorov's problems reading, and the Troelstra-coined "BHK"/Kreisel→Kolmogorov history.
- **[SEP-Log]** Joan Moschovakis, "Intuitionistic Logic," *Stanford Encyclopedia of Philosophy*. https://plato.stanford.edu/entries/logic-intuitionistic/ — BHK informal clauses, failure of LEM and `¬¬A→A`, twin-primes example, Kleene realizability definition, disjunction/existence properties.
- **[SEP-Const]** D. Bridges, E. Palmgren, H. Ishihara, "Constructive Mathematics," *Stanford Encyclopedia of Philosophy*. https://plato.stanford.edu/entries/mathematics-constructive/ — Markov's principle, realizability, function-class dependence.
- **[Rodin]** Andrei Rodin, "Kolmogorov's Calculus of Problems and Its Legacy," *History and Philosophy of Logic* **47**(1), 2025; preprint PhilSci-Archive 22316 (2023). https://philsci-archive.pitt.edu/22316/1/kolmoeng.pdf — primary-source analysis of Kolmogorov 1932: problems-vs-propositions distinction, reducibility-as-implication, negation footnote, postulate-as-construction-task, the BHK naming/Kreisel correction, Heyting–Kolmogorov controversy.
- **[Kolmogorov-1932]** A. N. Kolmogorov, "Zur Deutung der intuitionistischen Logik," *Mathematische Zeitschrift* **35** (1932), 58–65. English trans. "On the interpretation of intuitionistic logic," in *Selected Works of A. N. Kolmogorov*, vol. I.
- **[TvD]** A. S. Troelstra & D. van Dalen, *Constructivism in Mathematics: An Introduction*, 2 vols., North-Holland, 1988 — canonical statement of the BHK clauses (as reproduced in [SEP-Dev]); origin of the "BHK" terminology (Troelstra 1977/1990).
- **[Heyting]** A. Heyting, *Mathematische Grundlagenforschung. Intuitionismus. Beweistheorie*, Springer, 1934; *Intuitionism: An Introduction*, North-Holland, 1956 — the proof interpretation.
- **[Kleene-1945]** S. C. Kleene, "On the interpretation of intuitionistic number theory," *Journal of Symbolic Logic* **10** (1945), 109–124 — recursive realizability.
- **[Wiki-CH]** "Curry–Howard correspondence," *Wikipedia*. https://en.wikipedia.org/wiki/Curry%E2%80%93Howard_correspondence — propositions-as-types/proofs-as-programs, connective↔type table, Curry/Howard/Martin-Löf history, Sørensen–Urzyczyn.
- **[SU]** M. H. Sørensen & P. Urzyczyn, *Lectures on the Curry–Howard Isomorphism*, Studies in Logic and the Foundations of Mathematics **149**, Elsevier, 2006 — standard textbook treatment.
- **[nLab]** "BHK interpretation," *nLab*. https://ncatlab.org/nlab/show/BHK+interpretation — clause-by-clause definition; "synonymous with propositions-as-types"; relation to realizability.
- **[Wiki]** "Brouwer–Heyting–Kolmogorov interpretation," *Wikipedia*. https://en.wikipedia.org/wiki/Brouwer%E2%80%93Heyting%E2%80%93Kolmogorov_interpretation — pair/tagged-union/witness-pair clauses, worked `¬(P∧¬P)` construction, LEM failure, realizability link, function-class dependence.
- **[Realizability-Wiki]** "Realizability," *Wikipedia*. https://en.wikipedia.org/wiki/Realizability — realizability as formalisation of BHK; "proof" replaced by formal "realizer."
- **[Markov-Wiki]** "Markov's principle," *Wikipedia*. https://en.wikipedia.org/wiki/Markov%27s_principle — MP realizable but not intuitionistically derivable.
- **[CompMP]** "Computational interpretations of Markov's principle," arXiv:1611.03714. https://arxiv.org/pdf/1611.03714 — MP captures the constructive/classical meta-theory gap.
- **[Streicher]** T. Streicher, *Introduction to Constructive Logic and Mathematics* (lecture notes, TU Darmstadt). https://www2.mathematik.tu-darmstadt.de/~streicher/CLM/clm.pdf — BHK as informal explanation; function-class as free parameter.
- **[HMC]** "Rational Irrational Power," *Harvey Mudd College Math Fun Facts*. https://math.hmc.edu/funfacts/rational-irrational-power/ — the nonconstructive `√2^√2` existence argument.
- **[GS]** "Gelfond–Schneider constant," *Wikipedia*. https://en.wikipedia.org/wiki/Gelfond%E2%80%93Schneider_constant — `√2^√2` is transcendental (so the classical case-split's first branch is the false one).
