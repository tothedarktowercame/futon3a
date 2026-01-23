(ns meme.policy-arrow
  "Kolmogorov arrows for policy transitions.

   In BHK terms, a policy arrow A → B is a construction that
   transforms being-in-policy-A into being-in-policy-B.

   The construction isn't just 'these policies are related' but
   'here's HOW to make the transition':
   - What signals warrant the transition
   - What energies (八勁) enable it
   - What risks must be acknowledged
   - What scope conditions must hold

   Policy transition modes:
   - :adaptation - shift strategy based on signals
   - :escalation - move to more aggressive policy
   - :de-escalation - move to more exploratory policy
   - :rebalancing - return to balanced from extreme

   The eight-gate energies map to transition dynamics:
   - Péng (ward off) → maintain current policy, expand within it
   - Lǚ (roll back) → de-escalate, yield to uncertainty
   - Àn (push) → escalate, commit to forward motion
   - Jǐ (press) → focus, narrow scope within policy
   - Cǎi (pluck) → ground, connect policy to evidence
   - Liè (split) → separate, may need different policies for sub-problems
   - Zhǒu (elbow) → small adjustment, stay in policy
   - Kào (lean) → structural shift, major policy change"
  (:require [meme.arrow :as arrow]
            [clojure.set :as set]))

;; Policy entities

(def policies
  "The three compass policies as entity IDs."
  {:exploit "policy/exploit"
   :explore "policy/explore"
   :balanced "policy/balanced"})

;; Transition classification

(defn classify-transition
  "Classify a policy transition by its mode."
  [from-policy to-policy]
  (case [from-policy to-policy]
    ;; From exploit
    [:exploit :balanced] :de-escalation
    [:exploit :explore] :de-escalation

    ;; From explore
    [:explore :balanced] :escalation
    [:explore :exploit] :escalation

    ;; From balanced
    [:balanced :exploit] :escalation
    [:balanced :explore] :de-escalation

    ;; Same policy
    (if (= from-policy to-policy)
      :maintenance
      :adaptation)))

(defn transition-warrants
  "Determine what warrants a policy transition.

   Returns a map describing the conditions that justify the transition."
  [from-policy to-policy pragmatic-signal epistemic-signal]
  (let [mode (classify-transition from-policy to-policy)]
    (case mode
      :escalation
      {:warrant :low-epistemic-or-sufficient-pragmatic
       :condition (or (> pragmatic-signal 0.6)
                      (> epistemic-signal 0.7))
       :rationale (cond
                    (> pragmatic-signal 0.6)
                    "Pragmatic signal strong enough to commit"
                    (> epistemic-signal 0.7)
                    "Sufficient risk awareness to proceed"
                    :else
                    "Signals suggest forward motion")}

      :de-escalation
      {:warrant :low-pragmatic-or-low-epistemic
       :condition (or (< pragmatic-signal 0.4)
                      (< epistemic-signal 0.3))
       :rationale (cond
                    (< epistemic-signal 0.3)
                    "Epistemic signal too low—investigate risks"
                    (< pragmatic-signal 0.4)
                    "Pragmatic alignment weak—reconsider approach"
                    :else
                    "Signals suggest stepping back")}

      :maintenance
      {:warrant :signals-stable
       :condition true
       :rationale "Current policy remains appropriate"}

      ;; Default adaptation
      {:warrant :signal-shift
       :condition true
       :rationale "Signals suggest policy adjustment"})))

(defn enabling-energies
  "Determine which eight-gate energies enable a transition."
  [from-policy to-policy]
  (case (classify-transition from-policy to-policy)
    :escalation
    {:primary :an    ; push - forward motion
     :supporting [:ji :peng]  ; press (focus) and ward-off (expand)
     :rationale "Escalation requires Àn (push) with Jǐ (focus)"}

    :de-escalation
    {:primary :lu    ; roll back - yield
     :supporting [:cai :kao]  ; pluck (ground) and lean (structure)
     :rationale "De-escalation requires Lǚ (yield) with Cǎi (grounding)"}

    :maintenance
    {:primary :peng  ; ward off - maintain expansion
     :supporting [:zhou]  ; elbow - small adjustments
     :rationale "Maintenance requires Péng (hold space) with Zhǒu (adjust)"}

    :rebalancing
    {:primary :peng
     :supporting [:lu :an]  ; both yield and push available
     :rationale "Rebalancing requires Péng with access to both Lǚ and Àn"}

    ;; Default
    {:primary :peng
     :supporting [:zhou]
     :rationale "Default to Péng with small adjustments"}))

;; Construction builders

(defn build-transition-construction
  "Build the constructive content for a policy arrow.

   The construction is the 'proof' that transforms being-in-A to being-in-B.
   It includes:
   - The signals that warrant the transition
   - The energies that enable it
   - Concrete steps to effect the change"
  [from-policy to-policy compass-report]
  (let [pragmatic (get-in compass-report [:compass :pragmatic-signal])
        epistemic (get-in compass-report [:compass :epistemic-signal])
        energy-profile (get-in compass-report [:audit :best-energy-profile])
        warrants (transition-warrants from-policy to-policy pragmatic epistemic)
        energies (enabling-energies from-policy to-policy)]
    {:type :policy-transition
     :from from-policy
     :to to-policy
     :mode (classify-transition from-policy to-policy)

     ;; The warrant (why this transition)
     :warrant warrants

     ;; The enabling energies (how to transition)
     :energies energies

     ;; The signals at transition time
     :signals {:pragmatic pragmatic
               :epistemic epistemic
               :G (get-in compass-report [:recommendation :G])}

     ;; The energy profile that led here
     :observed-energies energy-profile

     ;; Concrete guidance
     :steps (case (classify-transition from-policy to-policy)
              :escalation
              ["Verify risk awareness is sufficient"
               "Commit to desired outcomes from preference model"
               "Apply Àn (push) energy - whole-structure forward motion"
               "Reduce exploration, increase goal pursuit"]

              :de-escalation
              ["Acknowledge that current approach isn't working"
               "Apply Lǚ (roll back) energy - yield and redirect"
               "Investigate unacknowledged risks"
               "Expand scope understanding before committing"]

              :maintenance
              ["Continue current policy"
               "Apply Péng (ward off) - expand from rooted base"
               "Monitor signals for change"]

              ["Assess current signals"
               "Apply appropriate energy based on context"
               "Adjust policy as signals indicate"])}))

;; Arrow emission

(defn should-emit-arrow?
  "Determine if a policy transition arrow should be emitted.

   Emit when:
   - Recommended policy differs from assumed current policy
   - Energy profile strongly suggests a different policy
   - Signals cross threshold boundaries"
  [compass-report & {:keys [current-policy]}]
  (let [recommended (get-in compass-report [:recommendation :best-policy])
        pragmatic (get-in compass-report [:compass :pragmatic-signal])
        epistemic (get-in compass-report [:compass :epistemic-signal])
        current (or current-policy :balanced)]  ; assume balanced if unknown
    (or
     ;; Different from current
     (not= recommended current)
     ;; Strong signal imbalance
     (> (Math/abs (- pragmatic epistemic)) 0.4)
     ;; Extreme signals
     (< epistemic 0.2)
     (> pragmatic 0.8))))

(defn emit-policy-arrow
  "Create a policy transition arrow from compass output.

   Returns an arrow map ready for `arrow/create-arrow!` or
   `arrow/assert-arrow!`."
  [compass-report & {:keys [current-policy]}]
  (let [recommended (get-in compass-report [:recommendation :best-policy])
        current (or current-policy :balanced)
        construction (build-transition-construction current recommended compass-report)]
    {:source-id (get policies current)
     :target-id (get policies recommended)
     :mode (classify-transition current recommended)
     :payload construction
     :scope-tags ["compass" "navigation" "policy"]
     :confidence (get-in compass-report [:recommendation :confidence])
     :rationale (get-in construction [:warrant :rationale])
     :created-by "futon3a/compass"}))

(defn compass->policy-arrows
  "Extract all relevant policy arrows from a compass report.

   May return multiple arrows:
   - The primary recommendation arrow
   - Arrows for alternative policies if close in score"
  [compass-report & {:keys [current-policy include-alternatives]
                     :or {include-alternatives false}}]
  (let [recommended (get-in compass-report [:recommendation :best-policy])
        current (or current-policy :balanced)
        primary (emit-policy-arrow compass-report :current-policy current)]
    (if include-alternatives
      ;; Include arrows to alternative policies with lower confidence
      (let [candidates (:candidate-policies compass-report)
            alternatives (->> candidates
                              (remove #(= (:id %) recommended))
                              (map (fn [alt]
                                     (-> (emit-policy-arrow
                                          (assoc-in compass-report
                                                    [:recommendation :best-policy]
                                                    (:id alt))
                                          :current-policy current)
                                         (assoc :confidence
                                                (* 0.5 (- 1.0 (Math/abs (get-in alt [:score :G])))))))))]
        (into [primary] alternatives))
      [primary])))

;; Persistence helpers

(defn persist-policy-arrow!
  "Persist a policy arrow to the database."
  [ds arrow-map]
  (arrow/create-arrow! ds arrow-map))

(defn persist-compass-arrows!
  "Persist all policy arrows from a compass report."
  [ds compass-report & opts]
  (let [arrows (apply compass->policy-arrows compass-report opts)]
    (mapv #(persist-policy-arrow! ds %) arrows)))
