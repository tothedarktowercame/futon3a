(ns futon.compass-security
  "Security layer for compass: tripwire detection, mechanism/warrant tests.

   Mission 2a: BECAUSE Clause Security Layer

   Pattern templates are not self-certifying. The BECAUSE clause is where
   fabrication templates hide because it can be descriptively true while being
   normatively invalid.

   Architecture:
   - Layer 1: Everyday patterns (default trust)
   - Layer 2: Tripwires (passive monitoring)
   - Layer 3: Escalation (active security via xenotype layer)

   Six tripwires detect fabrication template signatures:
   - harm-is-external: Cost assigned outside boundary
   - dissent-is-threat: Critique reframed as attack
   - self-sealing-logic: Evidence reinterpreted to confirm
   - escalate-on-failure: Doubling down when wrong
   - exit-suppression: Can't withdraw/revise
   - review-blocking: Audit channels closed

   Two tests for BECAUSE under scrutiny:
   - Mechanism test: Is BECAUSE causal or decorative?
   - Warrant test: Does mechanism warrant adoption? (ahimsa: visible, bounded, corrigible)"
  (:require [clojure.string :as str]
            [clojure.set :as set]))

;; =============================================================================
;; TRIPWIRE SIGNATURES
;; =============================================================================

(def tripwire-signatures
  "Detection signatures for each tripwire.

   Each tripwire maps to phrases/patterns that indicate its presence.
   These are heuristics, not definitive detectors."
  {:harm-is-external
   {:description "Cost assigned outside pattern boundary"
    :blocks :visibility
    :indicators #{"they caused" "their fault" "not our responsibility"
                  "external factors" "blame them" "costs are theirs"
                  "they bear the burden" "not our problem"
                  "market forces" "inevitable consequence"}
    :anti-indicators #{"we acknowledge" "our responsibility" "costs we create"
                       "harm we cause" "our side effects"}}

   :dissent-is-threat
   {:description "Critique reframed as attack or bad faith"
    :blocks :correction
    :indicators #{"attack on" "bad faith" "they don't understand"
                  "enemy of" "sabotage" "against us" "hostile"
                  "they're trying to" "ulterior motives"
                  "can't trust critics" "defensive position"}
    :anti-indicators #{"valid concern" "they have a point" "critique helps"
                       "welcome feedback" "constructive criticism"}}

   :self-sealing-logic
   {:description "Evidence reinterpreted to confirm regardless of content"
    :blocks :disconfirmation
    :indicators #{"proves my point" "confirms what we knew" "as expected"
                  "this too shows" "either way" "heads I win"
                  "can't lose" "no matter what" "whatever happens"
                  "proves it either way"}
    :anti-indicators #{"would change my mind" "disconfirming evidence"
                       "if this fails" "falsifiable prediction"}}

   :escalate-on-failure
   {:description "Doubling down when outcomes worsen"
    :blocks :learning-from-error
    :indicators #{"double down" "try harder" "more of the same"
                  "not enough commitment" "insufficient effort"
                  "need more" "intensify" "redouble"
                  "just need to push through"}
    :anti-indicators #{"reconsider" "pivot" "change approach"
                       "learn from failure" "adjust course"}}

   :exit-suppression
   {:description "Cannot withdraw, revise, or disengage"
    :blocks :corrigibility
    :indicators #{"no way out" "committed now" "can't turn back"
                  "too late to change" "point of no return"
                  "locked in" "no exit" "must continue"
                  "irreversible" "burned bridges"}
    :anti-indicators #{"can withdraw" "reversible" "exit available"
                       "can stop" "option to change"}}

   :review-blocking
   {:description "Audit channels closed or degraded"
    :blocks :the-loop-itself
    :indicators #{"no need to review" "trust the process"
                  "don't question" "just follow" "oversight unnecessary"
                  "audit is interference" "review is distraction"
                  "too complex to audit"}
    :anti-indicators #{"open to review" "audit welcome" "transparent process"
                       "can be questioned" "oversight enabled"}}})

;; =============================================================================
;; TRIPWIRE DETECTION
;; =============================================================================

(defn- normalize-text
  "Normalize text for pattern matching."
  [text]
  (when text
    (-> text
        str/lower-case
        (str/replace #"[^\w\s]" " ")
        (str/replace #"\s+" " ")
        str/trim)))

(defn- text-contains-indicator?
  "Check if text contains any indicator phrases."
  [text indicators]
  (let [normalized (normalize-text text)]
    (when normalized
      (some #(str/includes? normalized %) indicators))))

(defn- count-indicators
  "Count how many indicators match in text."
  [text indicators]
  (let [normalized (normalize-text text)]
    (if normalized
      (count (filter #(str/includes? normalized %) indicators))
      0)))

(defn detect-tripwire
  "Detect a specific tripwire in pattern text.

   Returns nil if not detected, or a map with:
   - :tripwire - the tripwire keyword
   - :confidence - 0.0 to 1.0
   - :matches - indicator phrases found
   - :blocks - what this tripwire blocks"
  [tripwire-key text]
  (let [{:keys [indicators anti-indicators blocks]} (get tripwire-signatures tripwire-key)
        normalized (normalize-text text)]
    (when normalized
      (let [positive-matches (filter #(str/includes? normalized %) indicators)
            negative-matches (filter #(str/includes? normalized %) anti-indicators)
            positive-count (count positive-matches)
            negative-count (count negative-matches)
            ;; Confidence: positive signals minus negative mitigation
            raw-confidence (if (zero? positive-count)
                             0.0
                             (max 0.0 (- (/ positive-count (count indicators))
                                         (* 0.5 (/ negative-count (max 1 (count anti-indicators)))))))]
        (when (pos? positive-count)
          {:tripwire tripwire-key
           :confidence (min 1.0 raw-confidence)
           :matches (vec positive-matches)
           :mitigated-by (vec negative-matches)
           :blocks blocks})))))

(defn detect-all-tripwires
  "Scan text for all tripwire signatures.

   Returns sequence of detected tripwires sorted by confidence."
  [text]
  (->> (keys tripwire-signatures)
       (map #(detect-tripwire % text))
       (remove nil?)
       (sort-by :confidence >)))

(defn scan-pattern-for-tripwires
  "Scan a flexiarg pattern for tripwires.

   Focuses primarily on the BECAUSE clause (the attack surface) but also
   checks THEN and NEXT-STEPS for consistency.

   Returns:
   - :status - :clear, :suspicious, :triggered
   - :tripwires - list of detected tripwires
   - :primary-concern - highest confidence tripwire if any"
  [pattern]
  (let [because (or (:because pattern) "")
        then-clause (or (:then pattern) "")
        next-steps (str/join " " (or (:next-steps pattern) []))
        rationale (or (:rationale pattern) "")

        ;; BECAUSE is primary attack surface
        because-tripwires (detect-all-tripwires because)

        ;; Also check THEN and NEXT-STEPS for consistency
        then-tripwires (detect-all-tripwires then-clause)
        next-tripwires (detect-all-tripwires next-steps)
        rationale-tripwires (detect-all-tripwires rationale)

        ;; Weight BECAUSE most heavily
        all-tripwires (concat
                       (map #(assoc % :source :because :weight 1.0) because-tripwires)
                       (map #(assoc % :source :then :weight 0.5) then-tripwires)
                       (map #(assoc % :source :next-steps :weight 0.3) next-tripwires)
                       (map #(assoc % :source :rationale :weight 0.7) rationale-tripwires))

        ;; Dedupe by tripwire, keeping highest weighted confidence
        by-tripwire (group-by :tripwire all-tripwires)
        deduplicated (for [[tw detections] by-tripwire]
                       (apply max-key #(* (:confidence %) (:weight %)) detections))
        sorted (sort-by #(* (:confidence %) (:weight %)) > deduplicated)

        high-confidence? (fn [t] (> (* (:confidence t) (:weight t)) 0.4))
        triggered (filter high-confidence? sorted)]

    {:status (cond
               (seq triggered) :triggered
               (seq sorted) :suspicious
               :else :clear)
     :tripwires (vec sorted)
     :primary-concern (first triggered)
     :pattern-id (or (:id pattern) (:flexiarg pattern) :unknown)}))

;; =============================================================================
;; MECHANISM TEST
;; =============================================================================

(def mechanism-test-criteria
  "Criteria for evaluating whether a BECAUSE clause has genuine mechanism."
  {:causal-language #{"causes" "leads to" "results in" "produces"
                      "because of" "due to" "through" "by means of"
                      "enables" "prevents" "when X then Y"}
   :decorative-language #{"it just is" "obviously" "everyone knows"
                          "naturally" "of course" "clearly"
                          "self-evident" "simply" "just"}
   :falsifiable-markers #{"if not" "unless" "would fail when"
                          "disconfirmed by" "falsified if"
                          "would change if" "contrary evidence"}})

(defn assess-mechanism
  "Assess whether a BECAUSE clause contains genuine causal mechanism.

   Returns:
   - :status - :genuine, :decorative, :ambiguous
   - :causal-signals - causal language found
   - :decorative-signals - decorative language found
   - :falsifiable? - whether disconfirmation criteria present
   - :recommendation - next action"
  [because-text]
  (let [normalized (normalize-text because-text)

        causal-found (when normalized
                       (filter #(str/includes? normalized %)
                               (:causal-language mechanism-test-criteria)))
        decorative-found (when normalized
                           (filter #(str/includes? normalized %)
                                   (:decorative-language mechanism-test-criteria)))
        falsifiable-found (when normalized
                            (filter #(str/includes? normalized %)
                                    (:falsifiable-markers mechanism-test-criteria)))

        causal-score (count causal-found)
        decorative-score (count decorative-found)
        falsifiable? (seq falsifiable-found)

        ;; Net mechanism score
        net-score (- causal-score (* 2 decorative-score))

        status (cond
                 (and (> net-score 2) falsifiable?) :genuine
                 (or (< net-score 0) (> decorative-score causal-score)) :decorative
                 :else :ambiguous)]

    {:status status
     :causal-signals (vec causal-found)
     :decorative-signals (vec decorative-found)
     :falsifiable? (boolean falsifiable?)
     :falsifiable-markers (vec falsifiable-found)
     :mechanism-score net-score
     :recommendation (case status
                       :genuine "Proceed to warrant test"
                       :decorative "Fail: BECAUSE is post-hoc rationalization"
                       :ambiguous "Hold: collect disconfirming evidence")}))

(defn run-mechanism-test
  "Run the full mechanism test on a pattern.

   This implements the press-mechanism procedure:
   1. State the BECAUSE clause
   2. Ask: What would disconfirm this?
   3. If no disconfirmation possible → fail
   4. If possible but not checked → hold
   5. If checked and survived → pass"
  [pattern]
  (let [because (or (:because pattern) (:rationale pattern) "")
        assessment (assess-mechanism because)

        ;; Check for explicit disconfirmation criteria in pattern
        has-disconfirmation? (or (:falsifiable? assessment)
                                 (some? (:disconfirmation pattern))
                                 (some? (:failure-modes pattern)))

        ;; Check if pattern acknowledges how it could fail
        however (or (:however pattern) "")
        has-failure-awareness? (and (seq however)
                                    (or (str/includes? (str/lower-case however) "fail")
                                        (str/includes? (str/lower-case however) "risk")
                                        (str/includes? (str/lower-case however) "wrong")))]

    {:test :mechanism
     :pattern-id (or (:id pattern) :unknown)
     :because-clause because
     :assessment assessment
     :has-disconfirmation-criteria? has-disconfirmation?
     :has-failure-awareness? has-failure-awareness?
     :result (cond
               ;; Genuine mechanism with disconfirmation → pass
               (and (= (:status assessment) :genuine) has-disconfirmation?)
               {:status :pass
                :confidence :high
                :next :warrant-test}

               ;; Decorative BECAUSE → fail
               (= (:status assessment) :decorative)
               {:status :fail
                :confidence :high
                :reason "BECAUSE clause is decorative, not causal"
                :next :escalate-split}

               ;; Ambiguous but has failure awareness → conditional pass
               (and (= (:status assessment) :ambiguous) has-failure-awareness?)
               {:status :pass
                :confidence :low
                :next :warrant-test
                :note "Pattern shows failure awareness despite ambiguous mechanism"}

               ;; Ambiguous → hold
               :else
               {:status :hold
                :confidence :medium
                :reason "Cannot determine mechanism validity"
                :next :collect-evidence})}))

;; =============================================================================
;; WARRANT TEST (AHIMSA)
;; =============================================================================

(def ahimsa-criteria
  "The three criteria for ahimsa (non-harm that's visible, bounded, corrigible)."
  {:visible {:description "Those harmed know they are harmed"
             :positive-markers #{"transparent" "visible" "disclosed" "aware"
                                 "informed" "acknowledged harm" "costs stated"}
             :negative-markers #{"hidden" "obscured" "externalized" "invisible"
                                 "not disclosed" "unknown harm"}}

   :bounded {:description "Harm does not scale unboundedly"
             :positive-markers #{"limited" "bounded" "capped" "contained"
                                 "finite" "constrained" "maximum"}
             :negative-markers #{"unlimited" "unbounded" "scaling" "cascading"
                                 "exponential" "recursive" "uncapped"}}

   :corrigible {:description "Those harmed can contest and seek correction"
                :positive-markers #{"reversible" "correctable" "can appeal"
                                    "recourse" "revision possible" "contestable"
                                    "redress available"}
                :negative-markers #{"irreversible" "no recourse" "final"
                                    "permanent" "cannot appeal" "locked"}}})

(defn assess-ahimsa-criterion
  "Assess a single ahimsa criterion against pattern text."
  [criterion-key text]
  (let [{:keys [description positive-markers negative-markers]}
        (get ahimsa-criteria criterion-key)
        normalized (normalize-text text)

        positive-found (when normalized
                         (filter #(str/includes? normalized %) positive-markers))
        negative-found (when normalized
                         (filter #(str/includes? normalized %) negative-markers))

        positive-count (count positive-found)
        negative-count (count negative-found)]

    {:criterion criterion-key
     :description description
     :positive-signals (vec positive-found)
     :negative-signals (vec negative-found)
     :status (cond
               (and (pos? positive-count) (zero? negative-count)) :satisfied
               (and (zero? positive-count) (pos? negative-count)) :violated
               (and (pos? positive-count) (pos? negative-count)) :contested
               :else :unknown)}))

(defn run-warrant-test
  "Run the full warrant test (ahimsa) on a pattern.

   This implements the push-warrant procedure:
   1. State the mechanism (from mechanism test)
   2. State the goals the pattern claims to serve
   3. Check: Are costs visible? bounded? corrigible?
   4. If ahimsa passes → warrant confirmed
   5. If ahimsa fails → fabrication template"
  [pattern mechanism-result]
  (let [;; Combine all text that might contain warrant signals
        all-text (str/join " "
                          [(or (:because pattern) "")
                           (or (:then pattern) "")
                           (or (:however pattern) "")
                           (str/join " " (or (:next-steps pattern) []))])

        ;; Assess each ahimsa criterion
        visible (assess-ahimsa-criterion :visible all-text)
        bounded (assess-ahimsa-criterion :bounded all-text)
        corrigible (assess-ahimsa-criterion :corrigible all-text)

        ;; Count violations
        violated? (fn [a] (= (:status a) :violated))
        satisfied? (fn [a] (= (:status a) :satisfied))

        violation-count (count (filter violated? [visible bounded corrigible]))
        satisfaction-count (count (filter satisfied? [visible bounded corrigible]))]

    {:test :warrant
     :pattern-id (or (:id pattern) :unknown)
     :mechanism-status (get-in mechanism-result [:result :status])
     :ahimsa {:visible visible
              :bounded bounded
              :corrigible corrigible}
     :result (cond
               ;; All three satisfied → warrant confirmed
               (= satisfaction-count 3)
               {:status :pass
                :confidence :high
                :next :release-from-quarantine}

               ;; Any violation → fabrication template
               (pos? violation-count)
               {:status :fail
                :confidence :high
                :violations (filterv violated? [visible bounded corrigible])
                :reason "Pattern violates ahimsa constraint"
                :next :escalate-secondary}

               ;; Some satisfied, none violated → conditional pass
               (pos? satisfaction-count)
               {:status :pass
                :confidence :low
                :next :release-with-monitoring
                :note (format "%d/3 ahimsa criteria explicitly satisfied" satisfaction-count)}

               ;; Unknown → hold
               :else
               {:status :hold
                :confidence :low
                :reason "Cannot assess ahimsa criteria"
                :next :require-explicit-acknowledgment})}))

;; =============================================================================
;; ESCALATION PROTOCOL
;; =============================================================================

(def escalation-levels
  "Escalation levels with their security patterns and energies."
  {:quarantine {:energy :peng
                :pattern "control/ward-off-boundary"
                :description "Establish boundary, quarantine pattern"}
   :hold {:energy :lu
          :pattern "control/roll-back-hold"
          :description "Hold without adopting, study"}
   :mechanism {:energy :ji
               :pattern "control/press-mechanism"
               :description "Demand mechanism test"}
   :warrant {:energy :an
             :pattern "control/push-warrant"
             :description "Force warrant test"}
   :extract {:energy :cai
             :pattern "control/pluck-extract"
             :description "Extract suspect component"}
   :isolate {:energy :lie
             :pattern "control/split-isolate"
             :description "Separate pattern from context"}
   :immediate {:energy :zhou
               :pattern "control/elbow-immediate"
               :description "Close-range intervention"}
   :commit {:energy :kao
            :pattern "control/lean-commit"
            :description "Full escalation, final resort"}})

(defn determine-escalation
  "Determine appropriate escalation level based on security scan results.

   Returns the escalation level and associated security pattern."
  [tripwire-scan mechanism-result warrant-result]
  (let [tripwire-status (:status tripwire-scan)
        mechanism-status (get-in mechanism-result [:result :status])
        warrant-status (get-in warrant-result [:result :status])]

    (cond
      ;; No tripwire → default trust (no escalation)
      (= tripwire-status :clear)
      {:level :none
       :action "Default trust - pattern runs normally"}

      ;; Tripwire but tests pass → release with monitoring
      (and (= mechanism-status :pass) (= warrant-status :pass))
      {:level :release
       :action "Release from quarantine - pattern cleared"
       :monitoring true}

      ;; Tripwire, mechanism decorative → extract/isolate
      (= mechanism-status :fail)
      (get escalation-levels :isolate)

      ;; Tripwire, mechanism pass, warrant fail → secondary escalation
      (= warrant-status :fail)
      (let [violations (get-in warrant-result [:result :violations])]
        (if (> (count violations) 1)
          (get escalation-levels :commit)
          (get escalation-levels :extract)))

      ;; Tripwire, tests hold → stay in quarantine
      (or (= mechanism-status :hold) (= warrant-status :hold))
      (get escalation-levels :hold)

      ;; Suspicious but not triggered → monitor
      (= tripwire-status :suspicious)
      {:level :monitor
       :action "Passive monitoring - watch for escalation signals"
       :energy nil}

      ;; Default → quarantine
      :else
      (get escalation-levels :quarantine))))

;; =============================================================================
;; FULL SECURITY SCAN
;; =============================================================================

(defn full-security-scan
  "Run complete security scan on a pattern.

   Executes:
   1. Tripwire detection
   2. Mechanism test (if tripwires fire)
   3. Warrant test (if mechanism passes)
   4. Escalation determination

   Returns comprehensive security report."
  [pattern]
  (let [tripwire-scan (scan-pattern-for-tripwires pattern)

        ;; Only run tests if tripwires detected
        mechanism-result (when (not= (:status tripwire-scan) :clear)
                           (run-mechanism-test pattern))

        ;; Only run warrant if mechanism passes or ambiguous
        warrant-result (when (and mechanism-result
                                  (not= (get-in mechanism-result [:result :status]) :fail))
                         (run-warrant-test pattern mechanism-result))

        ;; Determine escalation
        escalation (determine-escalation tripwire-scan mechanism-result warrant-result)]

    {:pattern-id (or (:id pattern) (:flexiarg pattern) :unknown)
     :scan-time (java.util.Date.)
     :tripwires tripwire-scan
     :mechanism-test mechanism-result
     :warrant-test warrant-result
     :escalation escalation
     :summary {:status (cond
                         (= (:level escalation) :none) :trusted
                         (= (:level escalation) :release) :cleared
                         (= (:level escalation) :monitor) :monitoring
                         :else :escalated)
               :energy (:energy escalation)
               :action (:action escalation)}}))

;; =============================================================================
;; COMPASS INTEGRATION
;; =============================================================================

(defn scan-retrieved-patterns
  "Scan all retrieved patterns for security issues.

   For compass integration: scan patterns before policy simulation."
  [patterns]
  (let [scans (map full-security-scan patterns)
        escalated (filter #(= (get-in % [:summary :status]) :escalated) scans)
        cleared (filter #(#{:trusted :cleared} (get-in % [:summary :status])) scans)
        monitoring (filter #(= (get-in % [:summary :status]) :monitoring) scans)]

    {:total (count patterns)
     :trusted (count (filter #(= (get-in % [:summary :status]) :trusted) cleared))
     :cleared (count (filter #(= (get-in % [:summary :status]) :cleared) cleared))
     :monitoring (count monitoring)
     :escalated (count escalated)
     :escalated-patterns (mapv #(select-keys % [:pattern-id :escalation]) escalated)
     :scans scans}))

(defn security-adjusted-preferences
  "Adjust preference model based on security scan results.

   Quarantined patterns are excluded from policy formation.
   Patterns under monitoring have reduced weight."
  [preference-model security-scan]
  (let [escalated-ids (set (map :pattern-id (:escalated-patterns security-scan)))
        monitoring-ids (set (map :pattern-id
                                 (filter #(= (get-in % [:summary :status]) :monitoring)
                                         (:scans security-scan))))]

    (-> preference-model
        ;; Add security metadata
        (assoc :security-status
               {:escalated-count (:escalated security-scan)
                :monitoring-count (:monitoring security-scan)
                :quarantined-patterns escalated-ids})

        ;; Filter source patterns
        (update :source-patterns
                (fn [patterns]
                  (remove #(contains? escalated-ids (:id %)) patterns)))

        ;; Add security note to rationale
        (update :rationale
                (fn [rationale]
                  (if (pos? (:escalated security-scan))
                    (conj (vec rationale)
                          (format "NOTE: %d pattern(s) quarantined by security layer"
                                  (:escalated security-scan)))
                    rationale))))))

;; =============================================================================
;; XENOTYPE BRIDGE
;; =============================================================================

(defn security-event->xenotype-activation
  "Convert a security event to xenotype activation signal.

   This bridges futon3a security layer to futon5 xenotype system."
  [security-report]
  (let [energy (get-in security-report [:escalation :energy])
        level (:level (:escalation security-report))]
    (when energy
      {:xenotype-trigger true
       :energy energy
       :source :security-layer
       :pattern-id (:pattern-id security-report)
       :escalation-level level
       ;; Map to futon5 bending parameters
       :bending-params (case energy
                         :peng {:mode :boundary :strength 0.8}
                         :lu {:mode :yield-hold :strength 0.6}
                         :ji {:mode :focus-scrutinize :strength 0.7}
                         :an {:mode :pressure-review :strength 0.9}
                         :cai {:mode :extract :strength 0.5}
                         :lie {:mode :isolate :strength 0.6}
                         :zhou {:mode :immediate :strength 0.8}
                         :kao {:mode :full-commitment :strength 1.0}
                         {:mode :default :strength 0.5})})))

;; =============================================================================
;; CLI ENTRY POINT
;; =============================================================================

(defn -main [& args]
  (println "")
  (println "╔══════════════════════════════════════════════════════════════╗")
  (println "║              COMPASS SECURITY LAYER                          ║")
  (println "╚══════════════════════════════════════════════════════════════╝")
  (println "")
  (println "Mission 2a: BECAUSE Clause Security Layer")
  (println "")
  (println "Tripwires detected:")
  (doseq [[k v] tripwire-signatures]
    (println (format "  %s: %s" (name k) (:description v))))
  (println "")
  (println "Tests available:")
  (println "  - Mechanism test: Is BECAUSE causal or decorative?")
  (println "  - Warrant test: Does mechanism warrant adoption? (ahimsa)")
  (println "")
  (println "Escalation energies:")
  (doseq [[level {:keys [energy description]}] escalation-levels]
    (when energy
      (println (format "  %s (%s): %s" (name level) (name energy) description))))
  (println "")
  (println "Key functions:")
  (println "  full-security-scan        Run complete scan on a pattern")
  (println "  scan-retrieved-patterns   Scan patterns for compass integration")
  (println "  security-adjusted-preferences  Filter preferences by security status")
  (println ""))
