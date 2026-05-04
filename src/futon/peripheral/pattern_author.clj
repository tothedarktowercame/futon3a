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

(def canonical-clause-names
  "Top-level clause name-keys admitted at the peripheral.

   The seven canonical components plus the conclusion-aliases (which are
   normalised to `:conclusion` by the parser, but we still allow them at
   the surface so authors can write `! summary:` etc. directly).

   Bespoke clauses are admitted *as substructure* under a canonical
   parent; they should not appear at top level. The peripheral's job is
   to refuse top-level non-canonicals so they get reshaped before
   landing."
  #{"context" "if" "however" "then" "because" "next-steps"
    "conclusion" "claim" "summary" "instantiated-by"})

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
               (when-not (contains? canonical-clause-names (:name-key c))
                 (violation :non-canonical-top-level-clause
                            (str "Top-level clause `+ " (:name c) ":` is not canonical. "
                                 "Move it under one of {context, if, however, then, because, next-steps} "
                                 "as substructure per E-clause-vocabulary-reshape.sexp; "
                                 "or use one of the canonical names if that fits the content.")
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
