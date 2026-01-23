(ns futon.gfe
  "Generalized Free Energy abstraction layer.

   This namespace defines the STRUCTURE of GFE computation without
   committing to a specific implementation. The goal is to enable
   rigorous upgrades from heuristic → probabilistic approaches.

   Structure (invariant across implementations):
   1. PREDICT: Generate expectations about observations
   2. OBSERVE: Gather actual observations
   3. ERROR: Compute prediction error
   4. UPDATE: Adjust precision based on error
   5. EVALUATE: Compute free energy G for policies
   6. SELECT: Choose policy minimizing G

   Implementation levels:
   - Level 0: Fixed weights, no prediction (current compass)
   - Level 1: Prediction error, adaptive precision (Option B)
   - Level 2: Learned precision from history
   - Level 3: Full generative model (Option C)

   Each level should be a drop-in replacement via protocols."
  (:require [clojure.set :as set]))

;; =============================================================================
;; PROTOCOLS: Define the GFE structure abstractly
;; =============================================================================

(defprotocol IGenerativeModel
  "Abstraction for the generative model p(o|s)p(s).

   Level 0: Returns uniform/fixed predictions
   Level 1: Returns heuristic predictions from preference model
   Level 3: Returns proper probabilistic predictions"

  (predict-observations [this state]
    "Given current state/beliefs, predict expected observations.
     Returns {:expected-patterns [...] :confidence float}")

  (observation-likelihood [this observation state]
    "p(o|s) - likelihood of observation given state.
     Returns float in [0,1] or log-likelihood."))

(defprotocol IPrecision
  "Abstraction for precision (inverse variance).

   Level 0: Returns fixed precision
   Level 1: Adapts based on prediction error
   Level 2: Learns from historical errors"

  (get-precision [this signal-type]
    "Get current precision for a signal type (:pragmatic, :epistemic, etc.).
     Returns float > 0.")

  (update-precision! [this signal-type error]
    "Update precision based on prediction error.
     High error → lower precision (less confidence in that signal).")

  (precision-weights [this]
    "Get all precisions as a map {:pragmatic τ_p :epistemic τ_e ...}"))

(defprotocol IFreeEnergy
  "Abstraction for free energy computation.

   Level 0: Weighted sum of signals
   Level 1: Precision-weighted prediction errors
   Level 3: Full variational free energy"

  (compute-F [this observations predictions precision]
    "Compute free energy F given observations, predictions, and precision.
     Returns float (lower is better).")

  (compute-G [this policy state precision]
    "Compute expected free energy G for a policy.
     Returns {:G float :pragmatic float :epistemic float :decomposition map}"))

(defprotocol IBeliefState
  "Abstraction for variational density q(s).

   Level 0: Point estimate (just the preference model)
   Level 1: Point estimate + confidence bounds
   Level 3: Full distribution over states"

  (current-beliefs [this]
    "Get current beliefs about hidden state.
     Returns map of belief parameters.")

  (update-beliefs! [this observations precision]
    "Update beliefs given new observations and precision.
     Implements variational inference (or approximation).")

  (belief-entropy [this]
    "Entropy of current belief distribution.
     Returns float ≥ 0."))

;; =============================================================================
;; LEVEL 0: Current compass implementation (baseline)
;; =============================================================================

(defrecord FixedPrecision [pragmatic-weight epistemic-weight]
  IPrecision
  (get-precision [_ signal-type]
    (case signal-type
      :pragmatic pragmatic-weight
      :epistemic epistemic-weight
      0.5))  ; default

  (update-precision! [this _ _]
    ;; Level 0: No update, fixed weights
    this)

  (precision-weights [_]
    {:pragmatic pragmatic-weight
     :epistemic epistemic-weight}))

(defn fixed-precision
  "Create Level 0 fixed precision (current compass behavior)."
  ([] (fixed-precision 0.6 0.4))
  ([pragmatic epistemic]
   (->FixedPrecision pragmatic epistemic)))

;; =============================================================================
;; LEVEL 1: Adaptive precision based on prediction error
;; =============================================================================

(defrecord AdaptivePrecision [state-atom learning-rate min-precision max-precision]
  IPrecision
  (get-precision [_ signal-type]
    (get @state-atom signal-type 0.5))

  (update-precision! [this signal-type error]
    ;; High error → decrease precision (less confidence)
    ;; Low error → increase precision (more confidence)
    (let [current (get @state-atom signal-type 0.5)
          ;; Error expected in [0, 1], invert for precision adjustment
          adjustment (* learning-rate (- 0.5 error))
          new-precision (-> (+ current adjustment)
                            (max min-precision)
                            (min max-precision))]
      (swap! state-atom assoc signal-type new-precision)
      this))

  (precision-weights [_]
    @state-atom))

(defn adaptive-precision
  "Create Level 1 adaptive precision.

   Options:
   - :initial - initial precision map (default {:pragmatic 0.5 :epistemic 0.5})
   - :learning-rate - how fast to adapt (default 0.1)
   - :min-precision - floor (default 0.1)
   - :max-precision - ceiling (default 0.9)"
  [& {:keys [initial learning-rate min-precision max-precision]
      :or {initial {:pragmatic 0.5 :epistemic 0.5}
           learning-rate 0.1
           min-precision 0.1
           max-precision 0.9}}]
  (->AdaptivePrecision (atom initial) learning-rate min-precision max-precision))

;; =============================================================================
;; LEVEL 1: Heuristic generative model (prediction from preference model)
;; =============================================================================

(defrecord HeuristicGenerativeModel [concept-prior]
  IGenerativeModel
  (predict-observations [_ state]
    ;; Predict patterns based on preference model concepts
    (let [concepts (or (:concepts state) #{})
          ;; Heuristic: expect patterns related to current concepts
          expected-patterns (vec (take 5 (sort concepts)))]
      {:expected-patterns expected-patterns
       :expected-concepts concepts
       :confidence (if (seq concepts) 0.6 0.3)}))

  (observation-likelihood [_ observation state]
    ;; Heuristic likelihood: concept overlap
    (let [expected-concepts (or (:concepts state) #{})
          observed-concepts (or (:concepts observation) #{})
          overlap (set/intersection expected-concepts observed-concepts)
          union (set/union expected-concepts observed-concepts)]
      (if (empty? union)
        0.5  ; uninformative
        (double (/ (count overlap) (count union)))))))

(defn heuristic-generative-model
  "Create Level 1 heuristic generative model."
  []
  (->HeuristicGenerativeModel {}))

;; =============================================================================
;; PREDICTION ERROR: Core to Option B
;; =============================================================================

(defn compute-prediction-error
  "Compute prediction error between expected and observed.

   Returns map with per-signal errors in [0, 1] where:
   - 0 = perfect prediction
   - 1 = maximum surprise"
  [predictions observations]
  (let [;; Pattern prediction error
        expected-patterns (set (:expected-patterns predictions))
        observed-patterns (set (map :id (:patterns observations)))
        pattern-overlap (set/intersection expected-patterns observed-patterns)
        pattern-union (set/union expected-patterns observed-patterns)
        pattern-error (if (empty? pattern-union)
                        0.5
                        (- 1.0 (/ (count pattern-overlap) (count pattern-union))))

        ;; Concept prediction error
        expected-concepts (or (:expected-concepts predictions) #{})
        observed-concepts (or (:concepts observations) #{})
        concept-overlap (set/intersection expected-concepts observed-concepts)
        concept-union (set/union expected-concepts observed-concepts)
        concept-error (if (empty? concept-union)
                        0.5
                        (- 1.0 (/ (count concept-overlap) (count concept-union))))]

    {:pattern-error pattern-error
     :concept-error concept-error
     :total-error (/ (+ pattern-error concept-error) 2.0)
     :pragmatic-error concept-error      ; concepts relate to pragmatic
     :epistemic-error pattern-error}))   ; patterns relate to epistemic

;; =============================================================================
;; FREE ENERGY COMPUTATION: Precision-weighted
;; =============================================================================

(defn compute-G-weighted
  "Compute expected free energy with precision weighting.

   G = -(τ_p * pragmatic + τ_e * epistemic)

   Where τ values come from precision, not fixed weights."
  [pragmatic-signal epistemic-signal precision]
  (let [weights (precision-weights precision)
        tau-p (or (:pragmatic weights) 0.5)
        tau-e (or (:epistemic weights) 0.5)
        ;; Normalize weights
        total (+ tau-p tau-e)
        w-p (/ tau-p total)
        w-e (/ tau-e total)
        G (- (+ (* w-p pragmatic-signal) (* w-e epistemic-signal)))]
    {:G G
     :pragmatic pragmatic-signal
     :epistemic epistemic-signal
     :weights {:pragmatic w-p :epistemic w-e}
     :precision {:pragmatic tau-p :epistemic tau-e}}))

;; =============================================================================
;; GFE CYCLE: The invariant structure
;; =============================================================================

(defn gfe-cycle
  "Execute one GFE cycle: predict → observe → error → update → evaluate.

   This is the STRUCTURAL INVARIANT that remains constant across
   implementation levels. Only the protocol implementations change.

   Arguments:
   - gen-model: IGenerativeModel implementation
   - precision: IPrecision implementation
   - state: current belief state
   - observe-fn: function that returns observations (side-effecting)
   - policies: seq of policies to evaluate

   Returns:
   {:predictions ... :observations ... :errors ... :precision ... :evaluations ...}"
  [gen-model precision state observe-fn policies]
  (let [;; 1. PREDICT: Generate expectations
        predictions (predict-observations gen-model state)

        ;; 2. OBSERVE: Gather actual observations
        observations (observe-fn)

        ;; 3. ERROR: Compute prediction error
        errors (compute-prediction-error predictions observations)

        ;; 4. UPDATE: Adjust precision based on error
        _ (update-precision! precision :pragmatic (:pragmatic-error errors))
        _ (update-precision! precision :epistemic (:epistemic-error errors))

        ;; 5. EVALUATE: Compute G for each policy
        evaluations (for [policy policies]
                      (let [;; Simulate policy to get signals
                            ;; (This part connects to compass simulation)
                            pragmatic-signal (or (:pragmatic policy) 0.5)
                            epistemic-signal (or (:epistemic policy) 0.5)]
                        (assoc policy
                               :gfe (compute-G-weighted pragmatic-signal
                                                        epistemic-signal
                                                        precision))))]

    {:predictions predictions
     :observations observations
     :errors errors
     :precision (precision-weights precision)
     :evaluations (vec evaluations)
     :best-policy (first (sort-by #(get-in % [:gfe :G]) evaluations))}))

;; =============================================================================
;; UPGRADE DOCUMENTATION
;; =============================================================================

(def upgrade-paths
  "Documentation of upgrade paths between implementation levels.

   Each upgrade preserves the gfe-cycle structure but swaps implementations."

  {:level-0->1
   {:description "Add prediction error and adaptive precision"
    :changes ["Replace FixedPrecision with AdaptivePrecision"
              "Add HeuristicGenerativeModel for predictions"
              "Precision now updates based on prediction error"]
    :preserved ["gfe-cycle structure"
                "Protocol interfaces"
                "Terminal vocabulary"]}

   :level-1->2
   {:description "Learn precision from historical errors"
    :changes ["AdaptivePrecision stores error history"
              "Precision priors learned from MUSN logs"
              "Narrative-dependent precision initialization"]
    :preserved ["gfe-cycle structure"
                "Protocol interfaces"
                "Prediction error computation"]}

   :level-2->3
   {:description "Full probabilistic generative model"
    :changes ["Replace HeuristicGenerativeModel with ProbabilisticGenerativeModel"
              "Implement proper variational inference in IBeliefState"
              "compute-F becomes true free energy (KL + log-evidence)"]
    :preserved ["gfe-cycle structure"
                "Protocol interfaces"
                "Precision weighting"
                "Terminal vocabulary"]}})
