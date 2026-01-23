(ns meme.compass-bridge
  "Bridge compass reports to the meme proposal layer.

   Compass outputs become auditable proposals:
   - Retrieved patterns → pattern observation proposals
   - Recommended policy → navigation intent proposals
   - Risks to acknowledge → risk investigation proposals
   - Energy profile → dynamics observation proposals

   All proposals are created with method :compass/navigation,
   enabling downstream filtering and analysis."
  (:require [meme.proposal :as proposal]))

(defn- pattern-observation-proposal
  "Create a proposal for an observed pattern retrieval."
  [narrative pattern]
  {:kind :pattern
   :method :compass/retrieval
   :target-id (:id pattern)
   :score (:score pattern)
   :evidence {:narrative narrative
              :pattern-id (:id pattern)
              :pattern-title (:title pattern)
              :retrieval-score (:score pattern)}
   :created-by "futon3a/compass"})

(defn- navigation-intent-proposal
  "Create a proposal for the recommended navigation policy."
  [report]
  (let [rec (:recommendation report)
        compass (:compass report)]
    {:kind :arrow
     :method :compass/recommendation
     :score (:confidence rec)
     :evidence {:narrative (:narrative report)
                :recommended-policy (:best-policy rec)
                :direction (:direction compass)
                :pragmatic-signal (:pragmatic-signal compass)
                :epistemic-signal (:epistemic-signal compass)
                :G (:G rec)}
     :metadata {:source "current-state"
                :target (name (:best-policy rec))
                :mode :navigation}
     :created-by "futon3a/compass"}))

(defn- risk-investigation-proposal
  "Create a proposal for a risk that needs investigation."
  [narrative risk]
  {:kind :pattern
   :method :compass/risk-surface
   :evidence {:narrative narrative
              :risk-text risk
              :action :investigate}
   :metadata {:requires-acknowledgment true}
   :created-by "futon3a/compass"})

(defn- evidence-suggestion-proposal
  "Create a proposal for evidence to collect."
  [narrative suggestion]
  {:kind :pattern
   :method :compass/evidence-gap
   :evidence {:narrative narrative
              :suggestion suggestion
              :action :collect-evidence}
   :created-by "futon3a/compass"})

(defn- energy-profile-proposal
  "Create a proposal documenting the energy dynamics used."
  [report]
  (let [profile (get-in report [:audit :best-energy-profile])]
    {:kind :pattern
     :method :compass/dynamics
     :evidence {:narrative (:narrative report)
                :energy-profile profile
                :dominant-energy (when (seq profile)
                                   (key (apply max-key val profile)))
                :simulation-steps (get-in report [:audit :simulation-steps])
                :seed (get-in report [:audit :seed])}
     :metadata {:eight-gates true}
     :created-by "futon3a/compass"}))

(defn compass->proposals
  "Convert a compass report to a sequence of meme proposals.

   Returns a vector of proposal maps ready for `create-proposal!`.
   Does NOT persist—caller decides whether to commit to database.

   Options:
   - :include-patterns - include pattern observation proposals (default true)
   - :include-navigation - include navigation intent (default true)
   - :include-risks - include risk investigation proposals (default true)
   - :include-evidence - include evidence suggestions (default true)
   - :include-energy - include energy profile (default true)
   - :top-patterns - max patterns to include (default 3)"
  [report & {:keys [include-patterns include-navigation include-risks
                    include-evidence include-energy top-patterns]
             :or {include-patterns true
                  include-navigation true
                  include-risks true
                  include-evidence true
                  include-energy true
                  top-patterns 3}}]
  (let [narrative (:narrative report)
        proposals (transient [])]

    ;; Pattern observations (top N)
    (when include-patterns
      (doseq [p (take top-patterns (:patterns-retrieved report))]
        (conj! proposals (pattern-observation-proposal narrative p))))

    ;; Navigation intent
    (when include-navigation
      (conj! proposals (navigation-intent-proposal report)))

    ;; Risk investigations
    (when include-risks
      (doseq [risk (get-in report [:preference-model :risks])]
        (conj! proposals (risk-investigation-proposal narrative risk))))

    ;; Evidence suggestions
    (when include-evidence
      (doseq [suggestion (get-in report [:compass :next-evidence])]
        (conj! proposals (evidence-suggestion-proposal narrative suggestion))))

    ;; Energy profile
    (when include-energy
      (conj! proposals (energy-profile-proposal report)))

    (persistent! proposals)))

(defn persist-proposals!
  "Persist compass proposals to the database.

   Returns a vector of created proposal records with their IDs."
  [ds proposals]
  (mapv #(proposal/create-proposal! ds %) proposals))

(defn compass-report->meme!
  "Full pipeline: generate proposals from compass report and persist.

   This is the main entry point for bridging compass to meme layer.

   Returns:
   {:proposals [...] :count N :narrative \"...\"}"
  [ds report & opts]
  (let [proposals (apply compass->proposals report opts)
        persisted (persist-proposals! ds proposals)]
    {:proposals persisted
     :count (count persisted)
     :narrative (:narrative report)}))

;; Query helpers

(defn compass-proposals
  "List all proposals created by compass navigation."
  [ds & {:keys [limit] :or {limit 50}}]
  (->> (proposal/list-proposals ds {:limit limit})
       (filter #(#{:compass/retrieval :compass/recommendation
                   :compass/risk-surface :compass/evidence-gap
                   :compass/dynamics}
                 (:method %)))))

(defn proposals-for-narrative
  "Find proposals associated with a specific narrative."
  [ds narrative & {:keys [limit] :or {limit 50}}]
  (->> (compass-proposals ds :limit limit)
       (filter #(= narrative (get-in % [:evidence :narrative])))))
