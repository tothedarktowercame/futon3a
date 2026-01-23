(ns futon.compass-gfe
  "Bridge between compass and GFE abstraction layer.

   This namespace connects the existing compass implementation to the
   GFE protocols, enabling gradual upgrade from Level 0 → Level 1 → etc.

   Usage:
   ;; Level 0 (current behavior, fixed weights)
   (compass-report-gfe narrative :level 0)

   ;; Level 1 (adaptive precision)
   (compass-report-gfe narrative :level 1)

   The gfe-cycle structure is preserved; only precision handling changes."
  (:require [futon.compass :as compass]
            [futon.gfe :as gfe]
            [futon.notions :as notions]
            [clojure.set :as set]
            [clojure.string :as str]))

;; =============================================================================
;; OBSERVATION ADAPTERS
;; =============================================================================

(defn- compass-observations
  "Gather observations in compass format for GFE cycle."
  [narrative & {:keys [method top-k] :or {method :auto top-k 5}}]
  (let [patterns (notions/search narrative :method method :top-k top-k)
        enriched (notions/enrich-results patterns)
        prefs (compass/extract-preferences enriched)]
    {:patterns enriched
     :concepts (:concepts prefs)
     :scope (:scope prefs)
     :risks (:risks prefs)
     :desired (:desired prefs)
     :narrative narrative}))

;; =============================================================================
;; POLICY SIMULATION WITH GFE SCORING
;; =============================================================================

(defn- simulate-and-score
  "Simulate a policy and compute GFE score with given precision."
  [initial-state policy sim-steps seed precision]
  (let [rng (java.util.Random. (long seed))
        ;; Run simulation
        final-state (reduce
                     (fn [state _]
                       ;; Simplified mutation for GFE context
                       (compass/apply-energy state policy
                                             (compass/select-energy rng) rng))
                     initial-state
                     (range sim-steps))

        ;; Compute signals
        desired-concepts (:concepts (:preference-model initial-state))
        outcome-concepts (or (:concepts final-state) #{})
        pragmatic (compass/concept-overlap desired-concepts outcome-concepts)

        total-risks (or (:risks (:preference-model initial-state)) [])
        acknowledged (or (:risks-acknowledged final-state) #{})
        epistemic (compass/risk-awareness total-risks acknowledged)]

    ;; Score with precision weighting
    (gfe/compute-G-weighted pragmatic epistemic precision)))

;; =============================================================================
;; LEVEL 0: Fixed precision (current compass behavior)
;; =============================================================================

(defn compass-report-level-0
  "Compass report with Level 0 GFE (fixed weights).

   This is equivalent to the current compass/compass-report but
   structured through the GFE abstraction for upgrade path clarity."
  [narrative & {:keys [top-k sim-steps seed]
                :or {top-k 5 sim-steps 10 seed 42}}]
  (let [precision (gfe/fixed-precision 0.6 0.4)
        observations (compass-observations narrative :top-k top-k)
        prefs (compass/extract-preferences (:patterns observations))

        initial-state {:concepts #{}
                       :unacknowledged-risks (:risks prefs)
                       :risks-acknowledged #{}
                       :preference-model prefs
                       :steps 0}

        policies [(compass/make-policy prefs :exploit)
                  (compass/make-policy prefs :explore)
                  (compass/make-policy prefs :balanced)]

        evaluations (for [policy policies]
                      (let [score (simulate-and-score initial-state policy
                                                      sim-steps seed precision)]
                        {:policy policy :score score}))

        ranked (sort-by #(get-in % [:score :G]) evaluations)
        best (first ranked)]

    {:level 0
     :narrative narrative
     :observations observations
     :precision (gfe/precision-weights precision)
     :evaluations (vec ranked)
     :recommendation {:policy (get-in best [:policy :id])
                      :G (get-in best [:score :G])
                      :weights (get-in best [:score :weights])}}))

;; =============================================================================
;; LEVEL 1: Adaptive precision with prediction error
;; =============================================================================

(defn compass-report-level-1
  "Compass report with Level 1 GFE (adaptive precision).

   Adds:
   - Prediction before observation
   - Prediction error computation
   - Precision updates based on error"
  [narrative & {:keys [top-k sim-steps seed precision-state]
                :or {top-k 5 sim-steps 10 seed 42}}]
  (let [;; Create or reuse precision state
        precision (or precision-state
                      (gfe/adaptive-precision
                       :initial {:pragmatic 0.5 :epistemic 0.5}
                       :learning-rate 0.1))

        ;; Create generative model
        gen-model (gfe/heuristic-generative-model)

        ;; Current belief state (bootstrap from previous or empty)
        belief-state {:concepts #{} :patterns []}

        ;; 1. PREDICT
        predictions (gfe/predict-observations gen-model belief-state)

        ;; 2. OBSERVE
        observations (compass-observations narrative :top-k top-k)

        ;; 3. ERROR
        errors (gfe/compute-prediction-error predictions observations)

        ;; 4. UPDATE precision
        _ (gfe/update-precision! precision :pragmatic (:pragmatic-error errors))
        _ (gfe/update-precision! precision :epistemic (:epistemic-error errors))

        ;; Now run policy evaluation with updated precision
        prefs (compass/extract-preferences (:patterns observations))

        initial-state {:concepts #{}
                       :unacknowledged-risks (:risks prefs)
                       :risks-acknowledged #{}
                       :preference-model prefs
                       :steps 0}

        policies [(compass/make-policy prefs :exploit)
                  (compass/make-policy prefs :explore)
                  (compass/make-policy prefs :balanced)]

        ;; 5. EVALUATE with updated precision
        evaluations (for [policy policies]
                      (let [score (simulate-and-score initial-state policy
                                                      sim-steps seed precision)]
                        {:policy policy :score score}))

        ranked (sort-by #(get-in % [:score :G]) evaluations)
        best (first ranked)]

    {:level 1
     :narrative narrative
     :predictions predictions
     :observations (dissoc observations :patterns)  ; summarize
     :errors errors
     :precision-before (:initial @(:state-atom precision))
     :precision-after (gfe/precision-weights precision)
     :evaluations (vec ranked)
     :recommendation {:policy (get-in best [:policy :id])
                      :G (get-in best [:score :G])
                      :weights (get-in best [:score :weights])}
     ;; Return precision for reuse in subsequent calls
     :precision-state precision}))

;; =============================================================================
;; UNIFIED ENTRY POINT
;; =============================================================================

(defn compass-report-gfe
  "Compass report using GFE abstraction layer.

   Options:
   - :level - GFE implementation level (0, 1; default 0)
   - :precision-state - reuse precision from previous call (level 1+)
   - :top-k, :sim-steps, :seed - standard compass options

   Level 0: Fixed weights (current behavior)
   Level 1: Adaptive precision with prediction error
   Level 2+: Not yet implemented"
  [narrative & {:keys [level] :or {level 0} :as opts}]
  (case level
    0 (apply compass-report-level-0 narrative (mapcat identity (dissoc opts :level)))
    1 (apply compass-report-level-1 narrative (mapcat identity (dissoc opts :level)))
    (throw (ex-info "GFE level not implemented" {:level level}))))

;; =============================================================================
;; SESSION-AWARE PRECISION (for multi-turn dialogues)
;; =============================================================================

(defn create-session-precision
  "Create a precision tracker for a session.

   Returns a precision object that can be passed to subsequent
   compass-report-gfe calls to maintain precision state across turns."
  [& {:keys [initial learning-rate]
      :or {initial {:pragmatic 0.5 :epistemic 0.5}
           learning-rate 0.1}}]
  (gfe/adaptive-precision :initial initial :learning-rate learning-rate))

(defn session-compass
  "Run compass with session-maintained precision.

   Usage:
   (def session (create-session-precision))
   (session-compass session \"first narrative\")
   (session-compass session \"second narrative\")  ; precision carries over"
  [precision-state narrative & opts]
  (apply compass-report-gfe narrative :level 1 :precision-state precision-state opts))
