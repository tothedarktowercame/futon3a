(ns futon.compass
  "GFE-inspired navigation demonstrator.

   Connects futon3a (patterns) and futon5-style (exotype) concepts:
   1. Retrieve patterns from narrative input
   2. Extract preference model (desired futures, obstacles)
   3. Simulate candidate policies with tiny exotype-style kernels
   4. Score with GFE-inspired objective
   5. Produce compass output (direction, deviation, next evidence)

   Futon theory: treat near-future as virtual attractor shaping present action.
   GFE lens: compute alignment signals, not meaning."
  (:require [clojure.string :as str]
            [clojure.set :as set]
            [futon.notions :as notions]))

;; === Preference Model Extraction ===

(defn- tokenize [text]
  (->> (str/split (str/lower-case (or text "")) #"[^a-z0-9]+")
       (remove str/blank?)
       (remove #(< (count %) 3))
       set))

(defn extract-preferences
  "Extract a preference model from retrieved patterns.

   Maps flexiarg fields to preference structure:
   - THEN + NEXT-STEPS → desired futures
   - IF + HOWEVER → obstacles/deviations to avoid
   - BECAUSE → rationale (for audit)
   - hotwords → key concepts"
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
     :obstacles (vec (concat all-if all-however))
     :rationale (vec (concat all-because all-rationale))
     :concepts (reduce set/union #{}
                       (map #(or (:hotwords %) (tokenize (:rationale %))) patterns))
     :source-patterns (mapv #(select-keys % [:id :title :score]) patterns)}))

;; === Exotype-Style Policy Simulator ===

(defn- apply-mutation
  "Apply a small mutation to state based on policy."
  [state policy rng]
  (let [mutation-rate (or (:mutation-rate policy) 0.1)
        concepts (or (:concepts state) #{})
        policy-concepts (or (:concepts policy) #{})
        policy-seq (seq policy-concepts)
        ;; Add some policy concepts
        added (if (and policy-seq (< (.nextDouble rng) mutation-rate))
                (set/union concepts (set (take 2 (shuffle policy-seq))))
                concepts)
        ;; Maybe remove an obstacle
        obstacles (or (:obstacles state) [])
        obstacles' (if (and (seq obstacles) (< (.nextDouble rng) (* 0.5 mutation-rate)))
                     (vec (rest (shuffle obstacles)))
                     obstacles)]
    (-> state
        (assoc :concepts added)
        (assoc :obstacles obstacles')
        (update :steps (fnil inc 0)))))

(defn simulate-policy
  "Simulate a policy for N steps using exotype-style dynamics.

   State includes:
   - :concepts - current active concepts
   - :obstacles - remaining obstacles
   - :alignment - running alignment score
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
     :description "Explore to reduce uncertainty about obstacles"
     :concepts (tokenize (str/join " " (:obstacles preference-model)))
     :mutation-rate 0.5
     :strategy :explore}

    :balanced
    {:id :balanced
     :description "Balance exploitation and exploration"
     :concepts (set/union
                (:concepts preference-model)
                (tokenize (str/join " " (:obstacles preference-model))))
     :mutation-rate 0.4
     :strategy :balanced}

    ;; Default
    {:id :default
     :description "Default policy"
     :concepts #{}
     :mutation-rate 0.2
     :strategy :default}))

;; === GFE-Inspired Scoring ===

(defn- concept-overlap
  "Compute normalized overlap between two concept sets."
  [a b]
  (if (or (empty? a) (empty? b))
    0.0
    (let [intersection (count (set/intersection a b))
          union (count (set/union a b))]
      (if (zero? union)
        0.0
        (double (/ intersection union))))))

(defn- obstacle-coverage
  "How many obstacles have been addressed (removed from state)."
  [initial-obstacles final-obstacles]
  (let [initial (set initial-obstacles)
        final (set final-obstacles)
        addressed (set/difference initial final)]
    (if (empty? initial)
      1.0
      (double (/ (count addressed) (count initial))))))

(defn score-gfe
  "Score a simulated outcome using GFE-inspired objective.

   G = pragmatic_value + epistemic_value

   Lower G is better (like free energy minimization).

   - Pragmatic: alignment between outcome concepts and desired futures
   - Epistemic: information gain about obstacles (coverage)"
  [preference-model initial-state final-state]
  (let [desired-concepts (:concepts preference-model)
        outcome-concepts (:concepts final-state #{})

        ;; Pragmatic value: how well do we align with desired?
        pragmatic (concept-overlap desired-concepts outcome-concepts)

        ;; Epistemic value: did we learn about/address obstacles?
        epistemic (obstacle-coverage
                   (:obstacles initial-state)
                   (:obstacles final-state))

        ;; Combine (negative because lower G is better)
        ;; Weight pragmatic slightly higher
        G (- (+ (* 0.6 pragmatic) (* 0.4 epistemic)))]
    {:G G
     :pragmatic pragmatic
     :epistemic epistemic
     :outcome-concepts (count outcome-concepts)
     :obstacles-remaining (count (:obstacles final-state))}))

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
        obstacles (:obstacles preference-model)]
    (cond-> []
      low-pragmatic?
      (conj "Collect evidence that desired outcomes are achievable")

      low-epistemic?
      (conj "Investigate obstacles to reduce uncertainty")

      (seq obstacles)
      (conj (str "Address obstacle: " (first obstacles))))))

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
                       :obstacles (:obstacles prefs)
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
     :candidate-policies (mapv (fn [{:keys [policy score]}]
                                 {:id (:id policy)
                                  :description (:description policy)
                                  :strategy (:strategy policy)
                                  :score score})
                               ranked)
     :recommendation {:best-policy (:id (:policy best))
                      :G (get-in best [:score :G])
                      :confidence (- 1.0 (Math/abs (double (get-in best [:score :G]))))}
     :compass {:direction direction
               :pragmatic-signal (get-in best [:score :pragmatic])
               :epistemic-signal (get-in best [:score :epistemic])
               :next-evidence next-evidence}
     :audit {:patterns-used (count patterns)
             :obstacles-identified (count (:obstacles prefs))
             :simulation-steps sim-steps
             :seed seed}}))

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
        (println "  Obstacles:" (count (get-in report [:preference-model :obstacles])))
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
        (println "COMPASS:")
        (println "  Direction:" (name (get-in report [:compass :direction])))
        (println "  Pragmatic signal:" (format "%.2f" (get-in report [:compass :pragmatic-signal])))
        (println "  Epistemic signal:" (format "%.2f" (get-in report [:compass :epistemic-signal])))
        (println "")
        (println "NEXT EVIDENCE TO COLLECT:")
        (doseq [e (get-in report [:compass :next-evidence])]
          (println "  •" e))
        (println "")))))
