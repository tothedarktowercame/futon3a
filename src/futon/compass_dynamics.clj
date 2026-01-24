(ns futon.compass-dynamics
  "Mission 3: Enriched policy simulation dynamics.

   Moves beyond random energy selection to context-sensitive dynamics:
   1. Pattern-derived energies: use @energy annotations from retrieved patterns
   2. State-sensitive selection: choose energy based on current state
   3. Security integration: tripwire scanning affects energy weights
   4. Calcification detection: avoid rigid repetition of same energy

   This namespace provides an enriched simulation that can replace
   the basic simulate-policy in compass.clj."
  (:require [clojure.string :as str]
            [clojure.set :as set]
            [futon.compass :as compass]
            [futon.compass-security :as security]))

;; =============================================================================
;; PATTERN ENERGY EXTRACTION
;; =============================================================================

(defn extract-pattern-energy
  "Extract @energy annotation from a pattern if present.
   Returns keyword like :peng, :lu, :ji, etc. or nil."
  [pattern]
  (when-let [energy-str (or (:energy pattern)
                            ;; Try to parse from raw content if available
                            (when-let [path (:path pattern)]
                              (try
                                (let [content (slurp path)
                                      match (re-find #"@energy\s+(\w+)" content)]
                                  (when match (second match)))
                                (catch Exception _ nil))))]
    (keyword (str/lower-case (str energy-str)))))

(defn extract-pattern-energies
  "Extract all energies from a collection of patterns.
   Returns a frequency map of energy → count."
  [patterns]
  (->> patterns
       (keep extract-pattern-energy)
       frequencies))

;; =============================================================================
;; STATE-SENSITIVE ENERGY SELECTION
;; =============================================================================

(def ^:private base-weights
  "Base selection weights for each energy."
  {:peng 0.20   ; expand
   :lu   0.15   ; yield
   :ji   0.12   ; focus
   :an   0.12   ; push
   :cai  0.10   ; pluck
   :lie  0.10   ; split
   :zhou 0.11   ; elbow
   :kao  0.10}) ; lean

(defn compute-state-modifiers
  "Compute energy weight modifiers based on current state.

   State signals:
   - Few concepts → favor expansion (péng, àn)
   - Many concepts → favor focus (jǐ) or consolidation (kào)
   - Many unacknowledged risks → favor yield (lǚ) or pluck (cǎi)
   - Few risks left → favor push (àn)
   - Stalled (low recent change) → favor elbow (zhǒu) for adjustment"
  [state]
  (let [concept-count (count (or (:concepts state) #{}))
        risk-count (count (or (:unacknowledged-risks state) []))
        recent-energies (take-last 5 (or (:energy-history state) []))
        energy-variety (count (set recent-energies))]

    (merge
     ;; Concept-based modifiers
     (cond
       (< concept-count 3)  {:peng 0.15 :an 0.10}  ; expand
       (> concept-count 10) {:ji 0.10 :kao 0.10}   ; focus/consolidate
       :else {})

     ;; Risk-based modifiers
     (cond
       (> risk-count 5)  {:lu 0.15 :cai 0.10}   ; acknowledge
       (zero? risk-count) {:an 0.10 :peng 0.05} ; push forward
       :else {})

     ;; Anti-calcification: if same energy used repeatedly, penalize it
     (when (and (seq recent-energies) (< energy-variety 3))
       (let [dominant (first (sort-by val > (frequencies recent-energies)))]
         (when dominant
           {(first dominant) -0.15}))))))

(defn compute-pattern-modifiers
  "Compute energy weight modifiers based on retrieved patterns.

   If patterns have @energy annotations, those energies get boosted.
   This allows the simulation to follow the 'grain' of the patterns."
  [patterns]
  (let [pattern-energies (extract-pattern-energies patterns)
        total (max 1 (reduce + (vals pattern-energies)))]
    (->> pattern-energies
         (map (fn [[energy count]]
                [energy (* 0.2 (/ count total))]))
         (into {}))))

(defn compute-security-modifiers
  "Compute energy weight modifiers based on security scan.

   If tripwires have fired, boost control-associated energies:
   - :peng → ward-off-boundary
   - :lu → roll-back-hold
   - :ji → press-mechanism
   - :an → push-warrant"
  [security-scan]
  (when security-scan
    (let [status (:status (:tripwires security-scan))
          escalation-energy (get-in security-scan [:escalation :energy])]
      (merge
       ;; General security alertness
       (when (= status :triggered)
         {:lu 0.15 :ji 0.10 :peng 0.05})

       ;; Specific escalation energy boost
       (when escalation-energy
         {escalation-energy 0.20})))))

(defn select-energy-contextual
  "Select energy based on context: state, patterns, security.

   Combines base weights with modifiers from:
   - Current state (concept count, risk count, history)
   - Pattern energies (if available)
   - Security scan (if tripwires fired)"
  [rng state patterns security-scan]
  (let [state-mods (compute-state-modifiers state)
        pattern-mods (compute-pattern-modifiers patterns)
        security-mods (compute-security-modifiers security-scan)

        ;; Combine all modifiers
        combined-mods (merge-with + state-mods pattern-mods security-mods)

        ;; Apply to base weights
        adjusted (merge-with + base-weights combined-mods)

        ;; Ensure non-negative and normalize
        clamped (into {} (map (fn [[k v]] [k (max 0.01 v)]) adjusted))
        total (reduce + (vals clamped))
        normalized (into {} (map (fn [[k v]] [k (/ v total)]) clamped))

        ;; Select based on normalized weights
        roll (.nextDouble rng)]
    (loop [remaining (seq normalized)
           cumulative 0.0]
      (if (empty? remaining)
        :peng ; fallback
        (let [[e weight] (first remaining)
              threshold (+ cumulative weight)]
          (if (< roll threshold)
            e
            (recur (rest remaining) threshold)))))))

;; =============================================================================
;; ENRICHED SIMULATION
;; =============================================================================

(defn apply-mutation-enriched
  "Apply a mutation using context-sensitive energy selection."
  [state policy patterns security-scan rng]
  (let [energy (select-energy-contextual rng state patterns security-scan)]
    (-> state
        (compass/apply-energy policy energy rng)
        (update :steps (fnil inc 0))
        (update :energy-history (fnil conj []) energy))))

(defn simulate-policy-enriched
  "Simulate a policy with enriched dynamics.

   Unlike the basic simulate-policy, this version:
   - Uses context-sensitive energy selection
   - Considers pattern energies
   - Integrates security scanning
   - Detects and avoids calcification

   Parameters:
   - initial-state: starting state
   - policy: policy being simulated
   - patterns: retrieved patterns (for energy extraction)
   - steps: number of simulation steps
   - seed: RNG seed for reproducibility
   - security-scan: optional security scan results"
  [initial-state policy patterns steps seed & {:keys [security-scan]}]
  (let [rng (java.util.Random. (long seed))]
    (reduce
     (fn [state _]
       (apply-mutation-enriched state policy patterns security-scan rng))
     initial-state
     (range steps))))

;; =============================================================================
;; CALCIFICATION DETECTION
;; =============================================================================

(defn detect-calcification
  "Detect if the simulation has calcified (stuck in rigid patterns).

   Signs of calcification:
   - Same energy repeated many times
   - Concepts not changing
   - Risks not being acknowledged

   Returns nil if healthy, or a map describing the calcification."
  [state]
  (let [energy-history (or (:energy-history state) [])
        recent (take-last 10 energy-history)
        variety (count (set recent))
        dominant-energy (when (seq recent)
                          (first (first (sort-by val > (frequencies recent)))))]

    (cond
      ;; Very low variety in recent energies
      (and (>= (count recent) 8) (< variety 3))
      {:type :energy-repetition
       :dominant dominant-energy
       :variety variety
       :recommendation "Consider forcing different energy mode"}

      ;; Many risks remaining after many steps
      (and (> (:steps state 0) 15)
           (> (count (:unacknowledged-risks state [])) 5))
      {:type :risk-stagnation
       :remaining-risks (count (:unacknowledged-risks state []))
       :recommendation "Boost yield energies (lǚ, cǎi, kào)"}

      ;; No new concepts for a while (would need history tracking)
      :else nil)))

;; =============================================================================
;; LIBERATION-AWARE SIMULATION
;; =============================================================================

(defn apply-liberation-correction
  "Apply liberation-layer correction to avoid calcification.

   If calcification is detected, inject a corrective energy that
   breaks the pattern without destroying accumulated value."
  [state calcification rng]
  (when calcification
    (case (:type calcification)
      :energy-repetition
      ;; Force an energy different from dominant
      (let [dominant (:dominant calcification)
            alternatives (remove #{dominant} [:peng :lu :ji :an :cai :lie :zhou :kao])
            corrective (nth alternatives (.nextInt rng (count alternatives)))]
        (assoc state :forced-energy corrective))

      :risk-stagnation
      ;; Force a risk-acknowledging energy
      (let [risk-energies [:lu :cai :kao :lie]
            corrective (nth risk-energies (.nextInt rng (count risk-energies)))]
        (assoc state :forced-energy corrective))

      ;; Default: no correction
      state)))

(defn simulate-policy-liberation
  "Simulate with liberation layer: detect and correct calcification.

   This is the most sophisticated simulation mode, combining:
   - Context-sensitive energy selection
   - Pattern energy influence
   - Security tripwire awareness
   - Calcification detection and correction"
  [initial-state policy patterns steps seed & {:keys [security-scan check-interval]
                                                :or {check-interval 5}}]
  (let [rng (java.util.Random. (long seed))]
    (loop [state initial-state
           step 0]
      (if (>= step steps)
        state
        (let [;; Check for calcification periodically
              calcification (when (zero? (mod step check-interval))
                              (detect-calcification state))

              ;; Apply liberation correction if needed
              corrected (if calcification
                          (apply-liberation-correction state calcification rng)
                          state)

              ;; Use forced energy if set, otherwise select contextually
              energy (or (:forced-energy corrected)
                         (select-energy-contextual rng state patterns security-scan))

              ;; Apply the mutation
              next-state (-> (dissoc corrected :forced-energy)
                             (compass/apply-energy policy energy rng)
                             (update :steps (fnil inc 0))
                             (update :energy-history (fnil conj []) energy)
                             (cond-> calcification
                               (update :liberation-corrections (fnil conj [])
                                       {:step step
                                        :calcification calcification
                                        :corrective-energy energy})))]
          (recur next-state (inc step)))))))

;; =============================================================================
;; INTEGRATION WITH COMPASS
;; =============================================================================

(defn enrich-compass-report
  "Enrich a compass report with advanced dynamics.

   Takes a standard compass report and re-runs simulation with:
   - Pattern-derived energies
   - Security scanning
   - Liberation-aware dynamics

   Returns enhanced report with dynamics metadata."
  [report & {:keys [steps seed]
             :or {steps 15 seed 42}}]
  (let [patterns (:patterns-retrieved report)
        prefs (:preference-model report)

        ;; Run security scan on patterns
        security-results (security/scan-retrieved-patterns
                          (map #(hash-map :id (:id %)
                                          :because (get-in report [:preference-model :rationale 0] "")
                                          :then (get-in report [:preference-model :desired 0] ""))
                               patterns))

        ;; Initial state
        initial-state {:concepts #{}
                       :unacknowledged-risks (:risks prefs)
                       :risks-acknowledged #{}
                       :steps 0}

        ;; Re-simulate each policy with enriched dynamics
        policies (mapv #(compass/make-policy prefs %) [:exploit :explore :balanced])
        results (for [policy policies]
                  (let [final (simulate-policy-liberation
                               initial-state
                               policy
                               patterns
                               steps
                               seed
                               :security-scan security-results)
                        score (compass/score-gfe prefs initial-state final)]
                    {:policy policy
                     :final-state final
                     :score score
                     :liberation-corrections (:liberation-corrections final)
                     :energy-history (:energy-history final)}))

        ;; Rank and select best
        ranked (sort-by #(get-in % [:score :G]) results)
        best (first ranked)]

    (assoc report
           :dynamics {:mode :liberation-aware
                      :security-scan {:escalated (:escalated security-results)
                                      :monitoring (:monitoring security-results)}
                      :pattern-energies (extract-pattern-energies patterns)
                      :best-policy-dynamics {:energy-profile (frequencies (:energy-history (:final-state best)))
                                             :corrections (:liberation-corrections best)
                                             :calcification-detected? (some? (:liberation-corrections best))}}
           :candidate-policies-enriched
           (mapv (fn [{:keys [policy score final-state liberation-corrections]}]
                   {:id (:id policy)
                    :strategy (:strategy policy)
                    :score score
                    :energy-history (frequencies (:energy-history final-state))
                    :corrections-applied (count (or liberation-corrections []))})
                 ranked))))

;; =============================================================================
;; CLI ENTRY POINT
;; =============================================================================

(defn -main [& args]
  (println "")
  (println "╔══════════════════════════════════════════════════════════════╗")
  (println "║           COMPASS DYNAMICS (Mission 3)                       ║")
  (println "╚══════════════════════════════════════════════════════════════╝")
  (println "")
  (println "Enriched policy simulation with:")
  (println "  - Pattern-derived energies (from @energy annotations)")
  (println "  - State-sensitive selection (concepts, risks, history)")
  (println "  - Security integration (tripwire-aware)")
  (println "  - Liberation layer (calcification detection/correction)")
  (println "")
  (println "Simulation modes:")
  (println "  simulate-policy-enriched    Context-sensitive energies")
  (println "  simulate-policy-liberation  Full liberation-aware simulation")
  (println "  enrich-compass-report       Enhance standard compass output")
  (println ""))
