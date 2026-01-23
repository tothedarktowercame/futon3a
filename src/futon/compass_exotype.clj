(ns futon.compass-exotype
  "Bridge between futon3a compass and futon5 MMCA exotype model.

   Mission 7: Connect compass policies to CA dynamics.

   This namespace translates between:
   - Compass level: narratives, patterns, preferences, policies
   - MMCA level: genotypes, exotypes, kernels, metrics

   The bridge enables:
   1. Testing compass recommendations via CA simulation
   2. Using CA regime classification to validate policy choice
   3. Feeding MMCA metrics back to GFE precision

   See docs/compass-exotype-bridge-architecture.md for full design."
  (:require [clojure.string :as str]
            [clojure.set :as set]))

;; =============================================================================
;; MAPPING TABLES (Configurable, not hardcoded)
;; =============================================================================

(def ^:private policy->regime
  "Map compass policies to target MMCA regimes."
  {:exploit  :static    ; High structure preservation
   :explore  :eoc       ; Edge-of-chaos, high activity
   :balanced :eoc})     ; Adaptive edge-of-chaos

(def ^:private regime->dynamics
  "MMCA regime characteristics."
  {:static {:entropy-target 0.3
            :change-target 0.15
            :description "Stable, structure-preserving"}
   :eoc    {:entropy-target 0.6
            :change-target 0.3
            :description "Edge-of-chaos, balanced activity"}
   :chaos  {:entropy-target 0.85
            :change-target 0.55
            :description "High entropy, rapid change"}
   :freeze {:entropy-target 0.1
            :change-target 0.05
            :description "Minimal activity, frozen"}
   :magma  {:entropy-target 0.9
            :change-target 0.7
            :description "Maximum entropy, dissolution"}})

(def ^:private energy->exotype-params
  "Map eight energies to exotype lift parameters."
  {:peng {:update-prob 1.0
          :match-threshold 0.5
          :description "Ward off - high mutation, expansion"}
   :lu   {:update-prob 0.25
          :match-threshold 0.6
          :description "Roll back - conservative, yielding"}
   :ji   {:update-prob 0.75
          :match-threshold 0.8
          :description "Press - focused, selective"}
   :an   {:update-prob 0.9
          :mix-mode :rotate-left
          :description "Push - forward momentum"}
   :cai  {:update-prob 0.6
          :invert-on-phenotype? true
          :description "Pluck - grounded in feedback"}
   :lie  {:update-prob 0.5
          :mix-mode :swap-halves
          :description "Split - separation, trade-offs"}
   :zhou {:update-prob 0.4
          :match-threshold 0.4
          :description "Elbow - small adjustments"}
   :kao  {:update-prob 0.5
          :mix-mode :majority
          :description "Lean - consolidation"}})

;; =============================================================================
;; PATTERN → CA RULE MAPPING
;; =============================================================================

(defn pattern-id->rule
  "Convert a pattern ID to a CA rule number (0-255).

   Uses hash-based mapping for determinism and distribution."
  [pattern-id]
  (let [h (hash (str pattern-id))]
    (mod (Math/abs h) 256)))

(defn patterns->rule-set
  "Convert a collection of patterns to their CA rule numbers."
  [patterns]
  (->> patterns
       (map #(or (:id %) %))
       (map pattern-id->rule)
       set
       vec
       sort))

(defn rule->sigil
  "Convert a CA rule number to its futon5 sigil character.

   This is a placeholder - actual sigil lookup would use futon5.ca.core."
  [rule]
  ;; Placeholder: use rule number directly
  ;; Real implementation: (ca/sigil-for-rule rule)
  (str (char (+ 0x4E00 rule)))) ; CJK block as placeholder

(defn patterns->sigil-string
  "Convert patterns to a genotype string for MMCA."
  [patterns length]
  (let [rules (patterns->rule-set patterns)
        rule-count (count rules)]
    (if (zero? rule-count)
      (apply str (repeat length "一")) ; default sigil
      (->> (range length)
           (map #(rule->sigil (nth rules (mod % rule-count))))
           (apply str)))))

;; =============================================================================
;; COMPASS POLICY → EXOTYPE SPEC
;; =============================================================================

(defn policy->exotype-spec
  "Convert a compass policy to an exotype specification."
  [policy energy-profile]
  (let [policy-id (or (:id policy) (:strategy policy) :balanced)
        target-regime (get policy->regime policy-id :eoc)
        regime-params (get regime->dynamics target-regime)

        ;; Blend energy params based on profile
        dominant-energy (or (first (sort-by val > energy-profile)) [:peng 1])
        energy-params (get energy->exotype-params (first dominant-energy) {})

        ;; Build exotype spec
        spec {:tier :local
              :target-regime target-regime
              :params (merge
                       {:rotation 0
                        :match-threshold 0.5
                        :invert-on-phenotype? false
                        :update-prob 0.5
                        :mix-mode :none
                        :mix-shift 0}
                       energy-params)
              :regime-constraints regime-params
              :source {:policy policy-id
                       :energy (first dominant-energy)}}]
    spec))

(defn preference-model->fitness-landscape
  "Convert a compass preference model to MMCA fitness constraints."
  [preference-model]
  (let [{:keys [desired scope risks concepts]} preference-model
        ;; More desired futures → seek higher coherence
        coherence-weight (min 0.6 (+ 0.3 (* 0.03 (count desired))))
        ;; More risks → avoid extreme regimes
        risk-penalty (min 0.3 (* 0.05 (count risks)))
        ;; More scope conditions → narrower acceptable regime band
        regime-band (max 0.2 (- 0.5 (* 0.05 (count scope))))]
    {:coherence-weight coherence-weight
     :risk-penalty risk-penalty
     :regime-band regime-band
     :target-regimes #{:eoc :static}
     :avoid-regimes #{:magma :chaos}
     :concept-affinity concepts}))

;; =============================================================================
;; COMPASS REPORT → MMCA CONFIGURATION
;; =============================================================================

(defn compass-report->mmca-config
  "Convert a full compass report to MMCA run configuration.

   This is the main bridge function."
  [report & {:keys [generations length seed]
             :or {generations 32 length 50 seed 42}}]
  (let [;; Extract components from compass report
        patterns (:patterns-retrieved report)
        preference-model (:preference-model report)
        best-policy (get-in report [:recommendation :best-policy])
        energy-profile (get-in report [:audit :best-energy-profile] {})

        ;; Generate MMCA components
        genotype (patterns->sigil-string patterns length)
        exotype-spec (policy->exotype-spec {:id best-policy} energy-profile)
        fitness (preference-model->fitness-landscape preference-model)

        ;; Build run configuration
        config {:genotype genotype
                :generations generations
                :exotype exotype-spec
                :seed seed
                :fitness-landscape fitness
                :source {:narrative (:narrative report)
                         :compass-direction (get-in report [:compass :direction])
                         :policy best-policy}}]
    config))

;; =============================================================================
;; MMCA RESULT → COMPASS FEEDBACK
;; =============================================================================

(defn regime-validates-policy?
  "Check if MMCA regime classification validates the compass policy choice."
  [policy-id regime]
  (let [target (get policy->regime policy-id :eoc)]
    (or (= regime target)
        ;; Accept edge-of-chaos as valid for all policies
        (= regime :eoc)
        ;; Exploit is validated by static or eoc
        (and (= policy-id :exploit) (#{:static :eoc} regime))
        ;; Explore is validated by eoc or mild chaos
        (and (= policy-id :explore) (#{:eoc :chaos} regime)))))

(defn mmca-metrics->compass-signals
  "Convert MMCA metrics to compass-interpretable signals."
  [metrics]
  (let [{:keys [composite-score coherence avg-change avg-entropy-n]} metrics
        ;; Map coherence → pragmatic signal (goal alignment)
        pragmatic (or coherence 0.5)
        ;; Map interestingness → epistemic signal (exploration value)
        epistemic (min 1.0 (/ (or composite-score 50.0) 100.0))
        ;; Detect regime from metrics
        regime (cond
                 (and (number? avg-change) (< avg-change 0.1)) :freeze
                 (and (number? avg-change) (> avg-change 0.7)) :magma
                 (and (number? avg-entropy-n) (> avg-entropy-n 0.8)
                      (number? avg-change) (> avg-change 0.45)) :chaos
                 (and (number? coherence) (> coherence 0.6)
                      (number? avg-change) (< avg-change 0.35)) :static
                 :else :eoc)]
    {:pragmatic-from-mmca pragmatic
     :epistemic-from-mmca epistemic
     :inferred-regime regime
     :regime-description (get-in regime->dynamics [regime :description])
     :raw-metrics (select-keys metrics [:composite-score :coherence
                                        :avg-change :avg-entropy-n])}))

(defn mmca-result->compass-feedback
  "Convert full MMCA result to compass feedback structure."
  [result compass-policy]
  (let [{:keys [metrics-history gen-history]} result
        ;; Compute summary metrics (simplified version of mmca/metrics)
        final-gen (last gen-history)
        final-metrics (last metrics-history)

        ;; Basic summary if we don't have access to full metrics ns
        summary {:avg-change (when (seq metrics-history)
                               (let [changes (keep :change-rate metrics-history)]
                                 (when (seq changes)
                                   (/ (reduce + changes) (count changes)))))
                 :avg-entropy-n (when (seq metrics-history)
                                  (let [entropies (keep :entropy metrics-history)]
                                    (when (seq entropies)
                                      (/ (reduce + entropies) (count entropies)))))
                 :coherence (or (:coherence final-metrics) 0.5)
                 :composite-score (or (:composite-score final-metrics) 50.0)}

        signals (mmca-metrics->compass-signals summary)
        validated? (regime-validates-policy? compass-policy (:inferred-regime signals))]

    {:validation {:policy compass-policy
                  :validated? validated?
                  :mmca-regime (:inferred-regime signals)
                  :regime-description (:regime-description signals)}
     :signals signals
     :feedback (cond
                 validated?
                 {:status :confirmed
                  :message (str "MMCA simulation confirms " (name compass-policy) " policy")}

                 (= (:inferred-regime signals) :freeze)
                 {:status :warning
                  :message "MMCA shows freeze regime - consider more exploratory policy"}

                 (= (:inferred-regime signals) :magma)
                 {:status :warning
                  :message "MMCA shows magma regime - consider more conservative policy"}

                 :else
                 {:status :note
                  :message (str "MMCA regime (" (name (:inferred-regime signals))
                                ") differs from expected - review policy choice")})
     :precision-adjustment
     ;; Suggest precision updates based on MMCA confidence
     (let [confidence (if validated? 0.1 -0.1)]
       {:pragmatic-delta confidence
        :epistemic-delta confidence})}))

;; =============================================================================
;; INTEGRATED COMPASS-EXOTYPE EVALUATION
;; =============================================================================

(defn compass-with-exotype-validation
  "Run compass report, then validate with MMCA simulation.

   This is a sketch - actual implementation would require futon5 dependency.

   Returns enhanced compass report with MMCA feedback."
  [compass-report & {:keys [generations length seed simulate?]
                     :or {generations 32 length 50 seed 42 simulate? false}}]
  (let [config (compass-report->mmca-config compass-report
                                            :generations generations
                                            :length length
                                            :seed seed)]
    (if simulate?
      ;; In real implementation, would call:
      ;; (let [result (mmca/run-mmca config)
      ;;       feedback (mmca-result->compass-feedback result ...)]
      ;;   ...)
      (assoc compass-report
             :exotype-config config
             :exotype-validation {:status :sketch
                                  :message "MMCA simulation not available - futon5 dependency required"})

      ;; Without simulation, just attach configuration
      (assoc compass-report
             :exotype-config config
             :exotype-validation {:status :pending
                                  :message "Run with :simulate? true when futon5 available"}))))

;; =============================================================================
;; UTILITY: Energy Profile → Exotype Blend
;; =============================================================================

(defn blend-energy-exotypes
  "Blend multiple energy-derived exotype params based on profile weights."
  [energy-profile]
  (let [total (reduce + (vals energy-profile))
        normalized (if (pos? total)
                     (into {} (map (fn [[k v]] [k (/ v total)]) energy-profile))
                     {:peng 1.0})]
    (reduce
     (fn [acc [energy weight]]
       (let [params (get energy->exotype-params energy {})]
         (reduce-kv
          (fn [m k v]
            (cond
              ;; Numeric params: weighted average
              (number? v)
              (update m k (fnil + 0) (* weight v))
              ;; Keywords: most weighted wins
              (keyword? v)
              (if (> weight (get-in m [:weights k] 0))
                (-> m
                    (assoc k v)
                    (assoc-in [:weights k] weight))
                m)
              ;; Booleans: OR if any energy sets true
              (boolean? v)
              (update m k #(or % v))
              ;; Default: keep first
              :else
              (if (contains? m k) m (assoc m k v))))
          acc
          params)))
     {:weights {}}
     normalized)))

;; =============================================================================
;; CLI ENTRY POINT (for testing)
;; =============================================================================

(defn -main [& args]
  (println "compass-exotype bridge sketch")
  (println "")
  (println "This namespace provides the translation layer between:")
  (println "  - futon3a compass: narratives, patterns, policies, GFE")
  (println "  - futon5 MMCA: genotypes, exotypes, kernels, metrics")
  (println "")
  (println "Key functions:")
  (println "  compass-report->mmca-config   Convert compass output to MMCA config")
  (println "  mmca-result->compass-feedback Convert MMCA result to compass signals")
  (println "  compass-with-exotype-validation  Full integration (requires futon5)")
  (println "")
  (println "See docs/compass-exotype-bridge-architecture.md for design details."))
