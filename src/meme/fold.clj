(ns meme.fold
  "The classical (rule-table) FOLD — promoted from the E-fold-engine lab script
   `holes/labs/M-memes-arrows/fold_engine.clj` into a loadable library so it can
   serve as impl #1 behind the fold interface (`futon2.aif.fold`,
   E-close-the-loop).

   `fold` is pure: `(cascade, want-signature) → wiring` (boxes/wires/terminals +
   surfaced policy-holes for unfolded patterns). v1 is a deterministic rule
   table (each cascade pattern's THEN-obligation encoded as its contribution);
   NL→rule auto-extraction is v2. The lab script keeps the CLI / self-application
   acceptance; this ns is just the realizer."
  (:require [meme.gates :as g]
            [clojure.string :as str]))

;; ---- the rule table: pattern → contribution (THEN-clause encoded; v2 auto-extracts) ----
(def RULES
  {"devmap-coherence/prototype-structure-checklist" {:box :selector  :order 1 :does "pattern → rule"}
   "math-strategy/constraint-tension-resolution"    {:box :match     :order 2 :does "rule × topology → applicable"}
   "math-formalization/tactic-algebra-interference" {:box :match     :order 2 :does "rule × topology → applicable"}
   "math-informal/parametric-tension-dissolution"   {:box :fold-step :order 3 :does "rewrite (tension-resolution); pivot"}
   "math-strategy/route-exploration-and-pivot"      {:box :fold-step :order 3 :does "rewrite (tension-resolution); pivot"}
   "devmap-coherence/next-steps-to-done"            {:box :fixpoint  :order 4 :does "iterate to coverage-saturation"}
   "devmap-coherence/prototype-alignment-role"      {:box :emit      :order 5 :does "emit wiring + policy-holes"}
   "devmap-coherence/prototype-alignment-tension"   {:box :emit      :order 5 :does "emit wiring + policy-holes"}
   "devmap-coherence/prototype-alignment-bridge"    {:discipline "bridge: cascade (correlation) → wiring (construction)"}
   "devmap-coherence/devmap-scope-discipline"       {:scope true :discipline "scope the terminals to the want-signature"}})

(defn fold
  "Cascade + want-signature → wiring. Pure. Patterns absent from RULES are
   surfaced as `:policy-holes`, never fabricated into boxes."
  [cascade want-signature]
  (let [sig      (g/parse-want-signature want-signature)
        contribs (keep (fn [p] (when-let [r (get RULES p)] (assoc r :pattern p))) cascade)
        boxed    (filter :box contribs)
        boxes    (->> (group-by :box boxed)
                      (map (fn [[box cs]]
                             {:id box
                              :order (apply min (map :order cs))
                              :does (:does (first cs))
                              :warrant (str/join " + " (sort (map :pattern cs)))}))
                      (sort-by :order)
                      vec)
        ids      (mapv :id boxes)
        cod      (or (:cod sig) "")
        outs     (cond-> [{:port "wiring-diagram" :type "Wiring"}]
                   (re-find #"(?i)hole" cod) (conj {:port "policy-holes" :type "[PolicyHole]"}))
        seq-wires (mapv (fn [[a b]] {:from a :to b :type :wire/sequencing})
                        (partition 2 1 (cons (:dom sig) ids)))
        loop-wire (when ((set ids) :fixpoint) [{:from :fixpoint :to :fold-step :type :wire/causal :note "rewrite loop"}])
        out-wires (mapv (fn [o] {:from :emit :to (:port o) :type :wire/consequential}) outs)
        disciplines (->> contribs (keep :discipline) distinct vec)
        unfolded (vec (remove RULES cascade))]
    {:id :wiring/fold-engine-generated
     :for-sorry :sorry/fold-engine-cascade-to-wiring
     :generated-by "meme.fold/fold v1 (rule-table fold; THEN→rule encoded, NL-extraction = v2)"
     :want-signature want-signature
     :terminals {:in [{:port (:dom sig)}] :out outs}
     :boxes boxes
     :wires (vec (concat seq-wires loop-wire out-wires))
     :discipline disciplines
     :policy-holes (mapv (fn [p] {:unfolded-pattern p}) unfolded)}))

(defn box-warrant-sets
  "For the self-application acceptance: box-id → the warrant-pattern set."
  [wiring]
  (into {} (map (fn [b] [(:id b) (g/warrant-patterns (:warrant b))]) (:boxes wiring))))
