(ns meme.proposal
  "Proposal ledger for the meme layer.

   All fuzzy outputs are recorded as proposals with evidence.
   Proposals are append-only; corrections are new records, not updates.
   Promotion to facts is an explicit, separate action after review.

   Proposal kinds:
   - :arrow - a proposed typed arrow between entities
   - :entity - a proposed new entity
   - :alias - a proposed alias for an entity
   - :pattern - a proposed pattern (from MUSN/futon3)
   - :sigil-sequence - a proposed sigil sequence (from MMCA/futon5)

   Proposal methods:
   - :ann/hnsw - from ANN similarity search
   - :mmca/emergence - emerged from MMCA evolution
   - :mmca/recurrence - recurred across multiple MMCA runs
   - :human/assertion - human-provided
   - :agent/inference - agent-inferred"
  (:require [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]
            [clojure.data.json :as json]
            [clojure.string :as str])
  (:import [java.util UUID]))

(defn- gen-id [prefix]
  (str prefix "-" (subs (str (UUID/randomUUID)) 0 12)))

(defn- now-iso []
  (str (java.time.Instant/now)))

(defn- ->json [x]
  (when x (json/write-str x)))

(defn- <-json [s]
  (when (and s (not (str/blank? s)))
    (json/read-str s :key-fn keyword)))

(def ^:private query-opts
  {:builder-fn rs/as-unqualified-maps})

(defn create-proposal!
  "Create a proposal with evidence.

   Required keys:
   - :kind - one of :arrow, :entity, :alias, :pattern, :sigil-sequence
   - :method - how the proposal was generated
   - :evidence - map with supporting data

   Optional keys:
   - :target-id - id of the target (for arrows, patterns, etc.)
   - :score - numeric score/confidence
   - :metadata - additional data
   - :created-by - who/what created this"
  [ds {:keys [kind method evidence target-id score metadata created-by]}]
  (let [id (gen-id "prop")
        now (now-iso)]
    (jdbc/execute-one! ds
      ["INSERT INTO proposals (id, kind, target_id, status, score, method, evidence, metadata, created_by, created_at, updated_at)
        VALUES (?, ?, ?, 'pending', ?, ?, ?, ?, ?, ?, ?)"
       id (name kind) target-id score (name method) (->json evidence) (->json metadata) created-by now now]
      query-opts)
    {:id id :kind kind :status :pending}))

(defn get-proposal
  "Retrieve a proposal by id."
  [ds id]
  (some-> (jdbc/execute-one! ds
            ["SELECT * FROM proposals WHERE id = ?" id]
            query-opts)
          (update :evidence <-json)
          (update :metadata <-json)
          (update :kind keyword)
          (update :status keyword)))

(defn update-proposal-status!
  "Update a proposal's status."
  [ds id status]
  (let [now (now-iso)]
    (jdbc/execute-one! ds
      ["UPDATE proposals SET status = ?, updated_at = ? WHERE id = ?"
       (name status) now id]
      query-opts)))

(defn list-proposals
  "List proposals with optional filters."
  ([ds] (list-proposals ds {}))
  ([ds {:keys [kind status method limit]}]
   (let [conditions []
         params []
         [conditions params] (if kind
                               [(conj conditions "kind = ?") (conj params (name kind))]
                               [conditions params])
         [conditions params] (if status
                               [(conj conditions "status = ?") (conj params (name status))]
                               [conditions params])
         [conditions params] (if method
                               [(conj conditions "method = ?") (conj params (name method))]
                               [conditions params])
         where (when (seq conditions)
                 (str " WHERE " (str/join " AND " conditions)))
         limit-clause (if limit (str " LIMIT " limit) " LIMIT 100")
         sql (str "SELECT * FROM proposals" where " ORDER BY created_at DESC" limit-clause)]
     (->> (jdbc/execute! ds (into [sql] params) query-opts)
          (mapv #(-> %
                     (update :evidence <-json)
                     (update :metadata <-json)
                     (update :kind keyword)
                     (update :status keyword)))))))

(defn pending-proposals
  "List all pending proposals."
  [ds]
  (list-proposals ds {:status :pending}))

;; Promotions

(defn promote!
  "Promote a proposal to a fact.

   This records the promotion decision; actual fact creation
   happens elsewhere (e.g., in futon1/XTDB)."
  [ds {:keys [proposal-id promoted-kind target-id decided-by rationale]}]
  (let [id (gen-id "prom")
        now (now-iso)]
    ;; Record the promotion
    (jdbc/execute-one! ds
      ["INSERT INTO promotions (id, proposal_id, promoted_kind, target_id, decided_by, rationale, created_at)
        VALUES (?, ?, ?, ?, ?, ?, ?)"
       id proposal-id (name promoted-kind) target-id decided-by rationale now]
      query-opts)
    ;; Update the proposal status
    (update-proposal-status! ds proposal-id :accepted)
    {:id id :proposal-id proposal-id :status :accepted}))

(defn reject!
  "Reject a proposal."
  [ds proposal-id & {:keys [rationale]}]
  (update-proposal-status! ds proposal-id :rejected)
  {:proposal-id proposal-id :status :rejected :rationale rationale})

(defn list-promotions
  "List promotions, optionally for a specific proposal."
  ([ds] (list-promotions ds nil))
  ([ds proposal-id]
   (let [sql (if proposal-id
               ["SELECT * FROM promotions WHERE proposal_id = ? ORDER BY created_at DESC" proposal-id]
               ["SELECT * FROM promotions ORDER BY created_at DESC LIMIT 100"])]
     (jdbc/execute! ds sql query-opts))))

;; Convenience: MMCA proposal helpers

(defn propose-sigil-sequence!
  "Create a proposal for a sigil sequence from MMCA."
  [ds {:keys [pattern score run-seed aif-score regime-score generations method]}]
  (create-proposal! ds
    {:kind :sigil-sequence
     :method (or method :mmca/emergence)
     :score score
     :evidence {:pattern pattern
                :run-seed run-seed
                :aif-score aif-score
                :regime-score regime-score
                :generations generations}
     :created-by "futon5/mmca"}))

(defn propose-pattern-recurrence!
  "Create a proposal for a pattern that recurred across runs."
  [ds {:keys [pattern run-count total-runs avg-score run-seeds]}]
  (create-proposal! ds
    {:kind :sigil-sequence
     :method :mmca/recurrence
     :score avg-score
     :evidence {:pattern pattern
                :run-count run-count
                :total-runs total-runs
                :recurrence-rate (/ run-count total-runs)
                :run-seeds run-seeds}
     :metadata {:recurrence-rate (double (/ run-count total-runs))}
     :created-by "futon5/mmca"}))

;; Convenience: Arrow proposal helpers

(defn propose-arrow!
  "Create a proposal for a Kolmogorov arrow."
  [ds {:keys [source-id target-id mode score method evidence rationale]}]
  (create-proposal! ds
    {:kind :arrow
     :method (or method :human/assertion)
     :score score
     :evidence (merge {:source-id source-id
                       :target-id target-id
                       :mode mode}
                      evidence)
     :metadata {:rationale rationale}}))
