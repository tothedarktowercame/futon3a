(ns futon.peripheral.pattern-author
  "Pattern Peripheral — Level-1 Sokoban mockup.

   Single bound action: `submit-draft!`. The peripheral's contract is:
   given an authoring proposal `{author, target-path, draft-body}`, either
   the draft lands as a canonical .flexiarg AND `pattern-created` evidence
   is appropriate for emission, or specific structured violations come back
   and nothing is written. Sokoban: no leakage — an agent inside this
   surface cannot emit a non-canonical .flexiarg.

   This is the *rough mockup* per Joe's framing 2026-05-04: enough working
   substrate to specify M-patterns-done-right.md against (vs guessing at
   what the peripheral should do). The four layers of ambition — Sokoban
   over text shape (here), algorithm-guided authoring, functorial
   admission, and pattern→code receipts — are named in the companion
   excursion `futon3/holes/excursions/E-pattern-peripheral.md` (and the
   eventual mission). This namespace ships only Level 1.

   Engine: `futon.flexiarg.projection` (canonical parser, P-1).
   Companion algorithm (agent-prep step):
     ~/code/algorithms/author-flexiarg-core-and-wire-discoverability.md
   Rulebook (failure-mode reference for agents):
     futon3/holes/excursions/E-clause-vocabulary-reshape.sexp"
  (:require [clojure.java.io :as io]
            [futon.flexiarg.projection :as projection]))

(def admitted-clause-names
  "Clause name-keys the peripheral admits anywhere in the draft.

   Three layers per E-clause-vocabulary-reshape.sexp:

   1. Canonical seven (top-level structure):
      context, if, however, then, because, next-steps, conclusion.
   2. Conclusion-aliases (treated as :conclusion by the parser):
      claim, summary, instantiated-by.
   3. Rulebook-recognised substructure (admitted because the canonical
      parser is flat — it does not yet track indent-as-nesting, so a
      properly-nested `+ CHECK:` under `+ THEN:` parses as a separate
      top-level clause; the Sokoban admits it on the understanding that
      it would render as substructure under a tighter parser):
        :then > compositions, check, enforcement, lean
        :however > failure-modes, anti-patterns, absence-signals, signals
        :because > evidence, evidence-base, because->evidence,
                   mechanism, counterfactual
        :if > does-not-apply
        :next-steps > use

   Bespoke names outside these three layers are refused — the rulebook's
   long-tail catch-all heuristic should reshape them before submission.

   Future tightening (graduation hook): when the parser tracks
   indent-as-nesting, the peripheral can refuse substructure names at
   true top level while still admitting them under their canonical
   parents."
  #{;; canonical seven
    "context" "if" "however" "then" "because" "next-steps" "conclusion"
    ;; conclusion-aliases
    "claim" "summary" "instantiated-by"
    ;; THEN substructure
    "compositions" "check" "enforcement" "lean"
    ;; HOWEVER substructure
    "failure-modes" "anti-patterns" "absence-signals" "signals"
    ;; BECAUSE substructure
    "evidence" "evidence-base" "because->evidence" "mechanism" "counterfactual"
    ;; IF substructure
    "does-not-apply"
    ;; NEXT-STEPS substructure
    "use"})

(defn- violation [kind detail & [where]]
  (cond-> {:kind kind :detail detail}
    where (assoc :where where)))

(defn- check-parser-status [packet]
  (when (= :error (:pattern/status packet))
    [(violation :missing-flexiarg-header
                (str "Draft must begin with `@flexiarg <ns>/<name>` "
                     "(or `@arg`/`@multiarg` for multi-block files). "
                     "See: futon3a/src/futon/flexiarg/projection.clj for the parser contract.")
                {:parser-error (:pattern/error packet)})]))

(defn- check-conclusion [packet]
  (when-not (:pattern/conclusion packet)
    [(violation :missing-conclusion
                (str "Required: `! conclusion: <one-paragraph claim>` "
                     "(or `! summary:` / `! claim:` / `! instantiated-by:` "
                     "as syntactic-sugar aliases per "
                     "E-clause-vocabulary-reshape.sexp §:reshape-spec/canonical/:conclusion). "
                     "The conclusion is the only required canonical slot.")
                {:pattern/id (:pattern/id packet)})]))

(defn- check-canonical-top-level-clauses [packet]
  (->> (:pattern/clauses packet)
       (keep (fn [c]
               (when-not (contains? admitted-clause-names (:name-key c))
                 (violation :non-canonical-clause
                            (str "Clause `+ " (:name c) ":` is not in the admitted set "
                                 "(canonical seven + conclusion-aliases + rulebook-recognised "
                                 "substructure under canonical parents). Either rename it to "
                                 "a canonical name that fits the content, or apply the "
                                 "long-tail-catch-all heuristic from E-clause-vocabulary-reshape.sexp "
                                 "to route it as substructure under {context, if, however, then, "
                                 "because, next-steps}.")
                            {:clause-name (:name c)
                             :clause-name-key (:name-key c)
                             :pattern/id (:pattern/id packet)}))))
       seq))

(defn validate
  "Pure validation of a draft proposal. Returns
   `{:status :ok :packets [...]}` or
   `{:status :refused :violations [...] :packets [...]}`.

   Does not touch disk except for a temp file used by the parser, which is
   deleted before return."
  [{:keys [draft-body]}]
  (let [tmp (java.io.File/createTempFile "pattern-author-" ".flexiarg")]
    (try
      (spit tmp (or draft-body ""))
      (let [packets (vec (projection/parse-file tmp))
            violations (vec (mapcat (fn [p]
                                      (concat (check-parser-status p)
                                              (check-conclusion p)
                                              (check-canonical-top-level-clauses p)))
                                    packets))]
        (cond
          (empty? packets)
          {:status :refused
           :violations [(violation :empty-draft
                                   "Draft body produced no parseable blocks.")]
           :packets []}

          (seq violations)
          {:status :refused :violations violations :packets packets}

          :else
          {:status :ok :packets packets}))
      (finally
        (.delete tmp)))))

(defn submit-draft!
  "Sokoban submit. Validates the draft; on success writes it to
   `:target-path` and returns `{:landed? true :path ...}`. On any
   violation, returns `{:landed? false :path :violations [...]}` and
   writes nothing.

   This mockup writes the draft body byte-for-byte on success (the parser
   already validated parseability). A future version should also re-emit
   the canonical packet through the parser and verify byte-stable
   round-trip before declaring landing."
  [{:keys [author target-path draft-body] :as proposal}]
  (let [{:keys [status violations packets]} (validate proposal)]
    (if (= :refused status)
      {:landed? false
       :path target-path
       :author author
       :violations violations
       :note "Sokoban refusal: re-draft and resubmit. The peripheral does not write partial results."}
      (let [out (io/file target-path)]
        (when-let [parent (.getParentFile out)]
          (.mkdirs parent))
        (spit out draft-body)
        {:landed? true
         :path target-path
         :author author
         :violations []
         :pattern-id (:pattern/id (first packets))
         :note "Level-1 Sokoban: wrote draft after validation. Future levels add categorical-admission (functorial composition) and code-receipts (pattern→code traceability)."}))))
