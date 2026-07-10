#!/usr/bin/env bb
;; fold_engine.clj — (2) the AUTO-DERIVATION: generate the wiring FROM the cascade.
;;
;; inter_step.clj CHECKS a wiring; this GENERATES one. v1 is a deterministic
;; rule-table fold: each cascade pattern's THEN-obligation is encoded as a rule
;; (pattern → its contribution to the wiring). Auto-extracting those rules from the
;; pattern THEN-clauses (NL→rule) is v2 — flagged, not hidden.
;;
;; Reuses meme.gates (the live store's gates) to validate the generated wiring, and
;; runs the SELF-APPLICATION acceptance: folding the fold-engine's OWN cascade must
;; reproduce the hand-derived E-fold-engine-wiring.edn (boxes, terminals, warrants).
;;
;; Run:  cd futon3a && bb --classpath src holes/labs/M-memes-arrows/fold_engine.clj [gen|test]

(require '[meme.gates :as g]
         '[clojure.edn :as edn]
         '[clojure.string :as str]
         '[clojure.pprint]
         '[cheshire.core :as json])

(def LAB "/home/joe/code/futon3a/holes/labs/M-memes-arrows")
(defn redn [f] (edn/read-string (slurp (str LAB "/" f))))
(defn cascade-of [] (->> (json/parse-string (slurp (str LAB "/E-fold-engine-stage1.json")) true)
                         :cascade (map first) vec))

;; ---- the rule table: pattern → contribution (THEN-clause encoded; v2 auto-extracts this) ----
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
   "devmap-coherence/devmap-scope-discipline"       {:scope true :discipline "scope the terminals to the want-signature"}
   ;; ---- 2026-07-03 reach extension (claude-11): honest NL→rule extraction from the
   ;; live-lane patterns' THEN clauses (each :does derived from the flexiarg's own THEN;
   ;; E-aif-post-mission-mining #1 — the sweep's rule-candidates were REJECTED, see the
   ;; ledger; these six are read-from-source). Grows the executor's reach so live passes
   ;; produce DIVERSE realized-G — the R14-variance channel (contract v0.22).
   "f6/pattern-as-strategy"                         {:box :selector  :order 1 :does "pattern → agent-operational rule (asker/answerer/critic)"}
   "f6/learning-event-detection"                    {:box :match     :order 2 :does "detect learning/treatment events over the corpus (first-use trajectories)"}
   "sidecar/artifact-entity-mention-grounding"      {:box :fold-step :order 3 :does "ground mentions → deterministic artifact/entity/alias records"}
   "f6/proof-as-social-process"                     {:box :fixpoint  :order 4 :does "map patterns → thesis categories; surface coverage gaps"}
   "stack-coherence/futon-bridge-health"            {:box :emit      :order 5 :does "verify each declared bridge; emit per-bridge health report"}
   "futon-theory/mission-interface-signature"       {:scope true :discipline "type the mission's ports (interface signature = the want-signature discipline)"}})

;; ---- the fold: cascade + want-signature → wiring ----
(defn fold [cascade want-signature]
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
     :generated-by "fold_engine.clj v1 (rule-table fold; THEN→rule encoded, NL-extraction = v2)"
     :want-signature want-signature
     :terminals {:in [{:port (:dom sig)}] :out outs}
     :boxes boxes
     :wires (vec (concat seq-wires loop-wire out-wires))
     :discipline disciplines
     :policy-holes (mapv (fn [p] {:unfolded-pattern p}) unfolded)}))

;; ---- compare generated vs hand-derived (the self-application acceptance) ----
(defn box-warrant-sets [wiring]
  (into {} (map (fn [b] [(:id b) (g/warrant-patterns (:warrant b))]) (:boxes wiring))))

;; ---- gen: write the generated wiring + report ----
(defn run-gen []
  (let [cascade (cascade-of)
        sorry   (redn "E-fold-engine-sorry.edn")
        w       (fold cascade (:want-signature sorry))]
    (spit (str LAB "/E-fold-engine-wiring-GENERATED.edn")
          (with-out-str (clojure.pprint/pprint w)))
    (println "=== fold_engine GEN ===")
    (println "  boxes:" (mapv :id (:boxes w)))
    (doseq [b (:boxes w)] (println (format "    %-10s ← %s" (name (:id b)) (:warrant b))))
    (println "  terminals in:" (mapv :port (get-in w [:terminals :in]))
             " out:" (mapv :port (get-in w [:terminals :out])))
    (println "  unfolded (→ policy-holes):" (mapv :unfolded-pattern (:policy-holes w)))
    (println "  gates: TERMINALS-MATCH" (:ok (g/terminals-match? (:want-signature w) w))
             "| CASCADE-WARRANT" (:ok (g/cascade-warrant-ok? cascade w)))
    (println "  wrote E-fold-engine-wiring-GENERATED.edn")
    w))

;; ---- apply: fold an ARBITRARY cascade (Car-3 :apply-cascade executor entry) ----
;; bb fold_engine.clj apply '<json-array-of-pattern-ids>' '<want-signature>'
;; Emits JSON {:wiring :box-ids :policy-holes :want-signature}. Pure given (cascade, want-sig):
;; reuses `fold` (only g/parse-want-signature, no live-store access). Coverage-honest —
;; patterns absent from RULES come out as :policy-holes, never fabricated.
(defn run-apply []
  (let [cascade  (vec (json/parse-string (nth *command-line-args* 1) true))
        want-sig (or (nth *command-line-args* 2 nil) "MissionState -> {Wiring, PolicyHoles}")
        w        (fold cascade want-sig)]
    (println (json/generate-string
              {:wiring w
               :box-ids (mapv :id (:boxes w))
               :policy-holes (mapv :unfolded-pattern (:policy-holes w))
               :folded-count (count (:boxes w))
               :unfolded-count (count (:policy-holes w))
               :want-signature want-sig}))))

;; ---- tests ----
(def fails (atom 0))
(defn t [nm p] (if p (println "  PASS" nm) (do (swap! fails inc) (println "  FAIL" nm))))

(defn run-tests []
  (println "=== fold_engine tests ===")
  (let [cascade (cascade-of)
        sorry   (redn "E-fold-engine-sorry.edn")
        hand    (redn "E-fold-engine-wiring.edn")
        sig     (:want-signature sorry)
        gen     (fold cascade sig)]
    ;; structure
    (t "generates the 5-box pipeline"
       (= [:selector :match :fold-step :fixpoint :emit] (mapv :id (:boxes gen))))
    ;; the generated wiring PASSES the live gates (by construction)
    (t "generated passes TERMINALS-MATCH" (:ok (g/terminals-match? sig gen)))
    (t "generated passes CASCADE-WARRANT" (:ok (g/cascade-warrant-ok? cascade gen)))
    ;; SELF-APPLICATION: folding its own cascade reproduces the hand-derived design
    (t "SELF-APPLICATION: box set reproduces hand-derived"
       (= (set (keys (box-warrant-sets gen))) (set (keys (box-warrant-sets hand)))))
    (t "SELF-APPLICATION: per-box warrants reproduce hand-derived"
       (= (box-warrant-sets gen) (box-warrant-sets hand)))
    (t "generated terminals reproduce hand-derived"
       (and (= (mapv :port (get-in gen [:terminals :in]))  (mapv :port (get-in hand [:terminals :in])))
            (= (mapv :port (get-in gen [:terminals :out])) (mapv :port (get-in hand [:terminals :out])))))
    ;; generality: a foreign pattern not in RULES is surfaced, not silently dropped
    (t "foreign cascade pattern → surfaced as policy-hole (not dropped)"
       (= [{:unfolded-pattern "foreign-ns/unknown"}]
          (:policy-holes (fold (conj cascade "foreign-ns/unknown") sig))))
    ;; honesty: a foreign-only fold cannot fabricate boxes it has no rules for
    (t "foreign-only cascade → no boxes (no fabrication)"
       (empty? (:boxes (fold ["foreign-ns/unknown"] sig)))))
  (println (if (zero? @fails) "\nALL FOLD-ENGINE TESTS PASS" (str "\n" @fails " FAILED")))
  (zero? @fails))

;; ---- main ----
(case (first *command-line-args*)
  "gen"   (run-gen)
  "apply" (run-apply)
  "test"  (when-not (run-tests) (System/exit 1))
  (do (run-tests) (run-gen)))
