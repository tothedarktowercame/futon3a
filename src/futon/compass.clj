(ns futon.compass
  "GFE-inspired navigation demonstrator.

   Connects futon3a (patterns) and futon5-style (exotype) concepts:
   1. Retrieve patterns from narrative input
   2. Extract preference model (desired futures, scope, risks)
   3. Simulate candidate policies with tiny exotype-style kernels
   4. Score with GFE-inspired objective
   5. Produce compass output (direction, deviation, next evidence)

   Futon theory: treat near-future as virtual attractor shaping present action.
   GFE lens: compute alignment signals, not meaning.

  See docs/README-style-guide.md for the semantic mapping of flexiarg fields."
  (:require [clojure.string :as str]
            [clojure.set :as set]
            [futon.notions :as notions]
            [futon.text :as text]))

;; === Preference Model Extraction ===

(defn extract-preferences
  "Extract a preference model from retrieved patterns.

   Maps flexiarg fields to preference structure:
   - THEN + NEXT-STEPS → desired futures (goals to achieve)
   - IF → scope/preconditions (when patterns apply)
   - HOWEVER → risks/failure-modes (consequences of not following)
   - BECAUSE → rationale (for audit)
   - hotwords → key concepts

   Note: IF and HOWEVER are NOT obstacles to overcome. IF defines
   applicability; HOWEVER warns about failure modes. See
   docs/README-style-guide.md for semantic details."
  [patterns]
  (let [extract-field (fn [p field]
                        (or (get p field)
                            (get p (keyword field))
                            ""))
        all-then (->> patterns
                      (map #(extract-field % :then))
                      (remove str/blank?))
        all-next-steps (->> patterns
                            (mapcat #(or (:next-steps %) []))
                            (remove str/blank?))
        all-if (->> patterns
                    (map #(extract-field % :if))
                    (remove str/blank?))
        all-however (->> patterns
                         (map #(extract-field % :however))
                         (remove str/blank?))
        all-because (->> patterns
                         (map #(extract-field % :because))
                         (remove str/blank?))
        all-rationale (->> patterns
                           (map :rationale)
                           (remove nil?))]
    {:desired (vec (concat all-then all-next-steps))
     :scope (vec all-if)
     :risks (vec all-however)
     :rationale (vec (concat all-because all-rationale))
     :concepts (reduce set/union #{}
                       (map #(or (:hotwords %) (text/tokenize (:rationale %))) patterns))
     :source-patterns (mapv #(select-keys % [:id :title :score]) patterns)}))

;; === Exotype-Style Policy Simulator ===

(defn- rng-shuffle
  "Deterministically shuffle a vector using the provided RNG."
  [^java.util.Random rng xs]
  (let [arr (object-array xs)]
    (loop [i (dec (alength arr))]
      (if (pos? i)
        (let [j (.nextInt rng (inc i))
              tmp (aget arr i)]
          (aset arr i (aget arr j))
          (aset arr j tmp)
          (recur (dec i)))
        (vec arr)))))

;; === Eight Energies (八勁) for Mutation Dynamics ===

(def ^:private energy-weights
  "Selection weights for each energy. Péng is preferred as the default
   expansive mode; others activate based on weighted random selection."
  {:peng 0.30   ; ward off - expand from rooted base (default)
   :lu   0.15   ; roll back - yield and acknowledge risks
   :ji   0.10   ; press - focus on single concept
   :an   0.10   ; push - aggressive forward motion
   :cai  0.10   ; pluck - ground by connecting risk to concept
   :lie  0.08   ; split - separate (trade concept for risk awareness)
   :zhou 0.10   ; elbow - small centered adjustment
   :kao  0.07}) ; lean - structural consolidation

(defn select-energy
  "Select an energy mode based on weights using the RNG.
   Public for GFE integration."
  [rng]
  (let [roll (.nextDouble rng)
        energies (keys energy-weights)]
    (loop [remaining energies
           cumulative 0.0]
      (if (empty? remaining)
        :peng ; fallback
        (let [e (first remaining)
              threshold (+ cumulative (get energy-weights e))]
          (if (< roll threshold)
            e
            (recur (rest remaining) threshold)))))))

(defn apply-energy
  "Apply a specific energy's mutation to state.
   Public for GFE integration.

   Each energy transforms state differently:
   - Péng (ward off): expand concepts, slight risk awareness
   - Lǚ (roll back): high risk acknowledgment, acquire by adherence
   - Jǐ (press): focus on single concept intensely
   - Àn (push): aggressive multi-concept addition
   - Cǎi (pluck): acknowledge risk AND add grounding concept
   - Liè (split): trade a concept for risk awareness
   - Zhǒu (elbow): small adjustment, one thing only
   - Kào (lean): consolidate, acknowledge multiple risks"
  [state policy energy rng]
  (let [concepts (or (:concepts state) #{})
        policy-concepts (vec (sort (or (:concepts policy) #{})))
        risks (vec (or (:unacknowledged-risks state) []))
        acknowledged (or (:risks-acknowledged state) #{})
        mutation-rate (or (:mutation-rate policy) 0.1)
        should-mutate? (< (.nextDouble rng) mutation-rate)]

    (if-not should-mutate?
      state ; no mutation this step
      (case energy
        ;; Péng 掤 - expand from rooted base
        :peng
        (let [added (if (seq policy-concepts)
                      (set/union concepts (set (take 2 (rng-shuffle rng policy-concepts))))
                      concepts)
              [risks' ack'] (if (and (seq risks) (< (.nextDouble rng) 0.3))
                              [(vec (rest risks)) (conj acknowledged (first risks))]
                              [risks acknowledged])]
          (assoc state :concepts added :unacknowledged-risks risks' :risks-acknowledged ack'))

        ;; Lǚ 捋 - yield and acknowledge, acquire by adherence
        :lu
        (let [[risks' ack'] (if (seq risks)
                              (let [to-ack (first (rng-shuffle rng risks))]
                                [(vec (remove #{to-ack} risks)) (conj acknowledged to-ack)])
                              [risks acknowledged])
              ;; Acquire concepts through adherence (from risk text)
              added (if (and (seq risks) (seq policy-concepts))
                      (conj concepts (first (rng-shuffle rng policy-concepts)))
                      concepts)]
          (assoc state :concepts added :unacknowledged-risks risks' :risks-acknowledged ack'))

        ;; Jǐ 擠 - focus on single concept
        :ji
        (let [added (if (seq policy-concepts)
                      (conj concepts (first (rng-shuffle rng policy-concepts)))
                      concepts)]
          (assoc state :concepts added))

        ;; Àn 按 - aggressive forward motion
        :an
        (let [added (if (seq policy-concepts)
                      (set/union concepts (set (take 3 (rng-shuffle rng policy-concepts))))
                      concepts)]
          (assoc state :concepts added))

        ;; Cǎi 採 - ground by connecting risk acknowledgment to concept
        :cai
        (let [[risks' ack' grounded]
              (if (seq risks)
                (let [r (first (rng-shuffle rng risks))]
                  [(vec (remove #{r} risks)) (conj acknowledged r) r])
                [risks acknowledged nil])
              ;; Add a "grounding" concept when we pluck
              added (if (and grounded (seq policy-concepts))
                      (conj concepts (first (rng-shuffle rng policy-concepts)))
                      concepts)]
          (assoc state :concepts added :unacknowledged-risks risks' :risks-acknowledged ack'))

        ;; Liè 挒 - split: trade concept capacity for risk awareness
        :lie
        (let [concepts' (if (> (count concepts) 1)
                          (disj concepts (first (rng-shuffle rng (vec concepts))))
                          concepts)
              [risks' ack'] (if (seq risks)
                              [(vec (rest risks)) (conj acknowledged (first risks))]
                              [risks acknowledged])]
          (assoc state :concepts concepts' :unacknowledged-risks risks' :risks-acknowledged ack'))

        ;; Zhǒu 肘 - small centered adjustment, one thing only
        :zhou
        (if (< (.nextDouble rng) 0.5)
          ;; Add one concept
          (if (seq policy-concepts)
            (assoc state :concepts (conj concepts (first (rng-shuffle rng policy-concepts))))
            state)
          ;; Or acknowledge one risk
          (if (seq risks)
            (assoc state
                   :unacknowledged-risks (vec (rest risks))
                   :risks-acknowledged (conj acknowledged (first risks)))
            state))

        ;; Kào 靠 - structural, consolidate multiple risks
        :kao
        (let [to-ack (take 2 (rng-shuffle rng risks))
              risks' (vec (remove (set to-ack) risks))
              ack' (set/union acknowledged (set to-ack))]
          (assoc state :unacknowledged-risks risks' :risks-acknowledged ack'))

        ;; Default fallback to péng
        (apply-energy state policy :peng rng)))))

(defn- apply-mutation
  "Apply a mutation to state using eight-energy dynamics.

   Selects an energy mode (weighted toward Péng) and applies its
   characteristic transformation. See library/eight-gates/ for
   the semantic meaning of each energy."
  [state policy rng]
  (let [energy (select-energy rng)]
    (-> state
        (apply-energy policy energy rng)
        (update :steps (fnil inc 0))
        (update :energy-history (fnil conj []) energy))))

(defn simulate-policy
  "Simulate a policy for N steps using exotype-style dynamics.

   State includes:
   - :concepts - current active concepts
   - :unacknowledged-risks - risks not yet investigated
   - :risks-acknowledged - risks that have been addressed
   - :steps - simulation steps taken"
  [initial-state policy steps seed]
  (let [rng (java.util.Random. (long seed))]
    (reduce
     (fn [state _]
       (apply-mutation state policy rng))
     initial-state
     (range steps))))

(defn make-policy
  "Create a policy from a preference model and a strategy."
  [preference-model strategy]
  (case strategy
    :exploit
    {:id :exploit
     :description "Focus on achieving desired outcomes"
     :concepts (:concepts preference-model)
     :mutation-rate 0.3
     :strategy :exploit}

    :explore
    {:id :explore
     :description "Investigate risks and expand scope understanding"
     :concepts (tokenize (str/join " " (:risks preference-model)))
     :mutation-rate 0.5
     :strategy :explore}

    :balanced
    {:id :balanced
     :description "Balance goal pursuit with risk awareness"
     :concepts (set/union
                (:concepts preference-model)
                (tokenize (str/join " " (:risks preference-model))))
     :mutation-rate 0.4
     :strategy :balanced}

    ;; Default
    {:id :default
     :description "Default policy"
     :concepts #{}
     :mutation-rate 0.2
     :strategy :default}))

;; === GFE-Inspired Scoring ===

(defn concept-overlap
  "Compute normalized overlap between two concept sets.
   Public for GFE integration."
  [a b]
  (if (or (empty? a) (empty? b))
    0.0
    (let [intersection (count (set/intersection a b))
          union (count (set/union a b))]
      (if (zero? union)
        0.0
        (double (/ intersection union))))))

(defn risk-awareness
  "How many risks have been acknowledged/investigated.
   Public for GFE integration."
  [total-risks acknowledged-risks]
  (let [total (count total-risks)
        acknowledged (count acknowledged-risks)]
    (if (zero? total)
      1.0
      (double (/ acknowledged total)))))

(defn score-gfe
  "Score a simulated outcome using GFE-inspired objective.

   G = pragmatic_value + epistemic_value

   Lower G is better (like free energy minimization).

   - Pragmatic: alignment between outcome concepts and desired futures
   - Epistemic: risk awareness (how many risks have been acknowledged)"
  [preference-model initial-state final-state]
  (let [desired-concepts (:concepts preference-model)
        outcome-concepts (:concepts final-state #{})

        ;; Pragmatic value: how well do we align with desired?
        pragmatic (concept-overlap desired-concepts outcome-concepts)

        ;; Epistemic value: have we acknowledged the risks?
        total-risks (:risks preference-model [])
        acknowledged (:risks-acknowledged final-state #{})
        epistemic (risk-awareness total-risks acknowledged)

        ;; Combine (negative because lower G is better)
        ;; Weight pragmatic slightly higher
        G (- (+ (* 0.6 pragmatic) (* 0.4 epistemic)))]
    {:G G
     :pragmatic pragmatic
     :epistemic epistemic
     :outcome-concepts (count outcome-concepts)
     :risks-remaining (count (:unacknowledged-risks final-state))}))

;; === Compass Output ===

(defn- classify-direction
  "Classify overall direction based on best policy score."
  [best-score]
  (let [g (:G best-score)]
    (cond
      (< g -0.5) :aligned
      (< g -0.3) :progressing
      (< g -0.1) :drifting
      :else :blocked)))

(defn- suggest-evidence
  "Suggest next evidence to collect based on gaps."
  [preference-model scores]
  (let [low-pragmatic? (< (:pragmatic (first scores)) 0.4)
        low-epistemic? (< (:epistemic (first scores)) 0.3)
        risks (:risks preference-model)
        scope (:scope preference-model)]
    (cond-> []
      low-pragmatic?
      (conj "Collect evidence that desired outcomes are achievable")

      low-epistemic?
      (conj "Investigate risks to improve awareness")

      (seq risks)
      (conj (str "Acknowledge risk: " (first risks)))

      (and (seq scope) (< (:pragmatic (first scores)) 0.5))
      (conj (str "Verify scope applies: " (first scope))))))

(defn compass-report
  "Generate full compass report from narrative input."
  [narrative & {:keys [top-k sim-steps seed method]
                :or {top-k 5 sim-steps 10 seed 42 method :auto}}]
  (let [;; Step 1: Retrieve patterns
        patterns (notions/search narrative :method method :top-k top-k)
        enriched (notions/enrich-results patterns)

        ;; Step 2: Extract preferences
        prefs (extract-preferences enriched)

        ;; Step 3: Create initial state
        initial-state {:concepts #{}
                       :unacknowledged-risks (:risks prefs)
                       :risks-acknowledged #{}
                       :steps 0}

        ;; Step 4: Generate and simulate policies
        policies (mapv #(make-policy prefs %) [:exploit :explore :balanced])
        results (for [policy policies]
                  (let [final (simulate-policy initial-state policy sim-steps seed)
                        score (score-gfe prefs initial-state final)]
                    {:policy policy
                     :final-state final
                     :score score}))

        ;; Step 5: Rank by G (lower is better, so sort ascending)
        ranked (sort-by #(get-in % [:score :G]) results)
        best (first ranked)

        ;; Step 6: Generate compass output
        direction (classify-direction (:score best))
        next-evidence (suggest-evidence prefs (map :score ranked))]

    {:narrative narrative
     :patterns-retrieved (mapv #(select-keys % [:id :title :score]) patterns)
     :preference-model (-> prefs
                           (update :concepts #(vec (take 10 %)))
                           (dissoc :source-patterns))
     :candidate-policies (mapv (fn [{:keys [policy score final-state]}]
                                 {:id (:id policy)
                                  :description (:description policy)
                                  :strategy (:strategy policy)
                                  :score score
                                  :energy-history (frequencies (:energy-history final-state))})
                               ranked)
     :recommendation {:best-policy (:id (:policy best))
                      :G (get-in best [:score :G])
                      :confidence (- 1.0 (Math/abs (double (get-in best [:score :G]))))}
     :compass {:direction direction
               :pragmatic-signal (get-in best [:score :pragmatic])
               :epistemic-signal (get-in best [:score :epistemic])
               :next-evidence next-evidence}
     :audit {:patterns-used (count patterns)
             :scope-conditions (count (:scope prefs))
             :risks-identified (count (:risks prefs))
             :simulation-steps sim-steps
             :seed seed
             :best-energy-profile (frequencies (get-in best [:final-state :energy-history]))}}))

;; === CLI Entry Point ===

(defn -main [& args]
  (let [narrative (str/join " " args)]
    (if (str/blank? narrative)
      (println "Usage: clj -M -m futon.compass <narrative>")
      (let [report (compass-report narrative)]
        (println "")
        (println "╔══════════════════════════════════════════════════════════════╗")
        (println "║                     COMPASS REPORT                           ║")
        (println "╚══════════════════════════════════════════════════════════════╝")
        (println "")
        (println "NARRATIVE:" (:narrative report))
        (println "")
        (println "PATTERNS RETRIEVED:")
        (doseq [p (:patterns-retrieved report)]
          (println (format "  %.3f %s" (or (:score p) 0.0) (:id p))))
        (println "")
        (println "PREFERENCE MODEL:")
        (println "  Desired futures:" (count (get-in report [:preference-model :desired])))
        (println "  Scope conditions:" (count (get-in report [:preference-model :scope])))
        (println "  Risks to acknowledge:" (count (get-in report [:preference-model :risks])))
        (println "  Key concepts:" (str/join ", " (take 5 (get-in report [:preference-model :concepts]))))
        (println "")
        (println "CANDIDATE POLICIES:")
        (doseq [p (:candidate-policies report)]
          (println (format "  %s (G=%.3f, pragmatic=%.2f, epistemic=%.2f)"
                           (name (:id p))
                           (get-in p [:score :G])
                           (get-in p [:score :pragmatic])
                           (get-in p [:score :epistemic]))))
        (println "")
        (println "RECOMMENDATION:")
        (println "  Best policy:" (name (get-in report [:recommendation :best-policy])))
        (println "  Confidence:" (format "%.2f" (get-in report [:recommendation :confidence])))
        (println "")
        (println "ENERGY PROFILE (八勁):")
        (let [profile (get-in report [:audit :best-energy-profile])]
          (doseq [[energy count] (sort-by val > profile)]
            (println (format "  %s: %d" (name energy) count))))
        (println "")
        (println "COMPASS:")
        (println "  Direction:" (name (get-in report [:compass :direction])))
        (println "  Pragmatic signal:" (format "%.2f" (get-in report [:compass :pragmatic-signal])))
        (println "  Epistemic signal:" (format "%.2f" (get-in report [:compass :epistemic-signal])))
        (println "")
        (println "NEXT EVIDENCE TO COLLECT:")
        (doseq [e (get-in report [:compass :next-evidence])]
          (println "  •" e))
        (println "")))))
