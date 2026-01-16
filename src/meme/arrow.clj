(ns meme.arrow
  "Kolmogorov arrows: typed semantic transforms between entities.

   In the BHK interpretation, an arrow A → B is a construction
   that transforms any proof of A into a proof of B.

   Arrow modes (construction types):
   - :translation - language/encoding change (same meaning)
   - :abstraction - generalization (loses detail)
   - :specialization - instantiation (gains detail)
   - :metonymy - part-for-whole or association
   - :derivation - logical/causal derivation
   - :construction - explicit program/proof that builds B from A
   - :analogy - structural similarity across domains
   - :untyped - arrow asserted without construction

   Arrow status lifecycle:
   - :draft - proposed, unverified
   - :active - verified, in use
   - :retired - superseded or invalidated

   Confidence ranges from 0.0 (I dunno mate) to 1.0 (here's the construction)."
  (:require [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]
            [clojure.data.json :as json]
            [clojure.string :as str]
            [meme.core :as core])
  (:import [java.util UUID]))

(defn- gen-id []
  (str "arr-" (subs (str (UUID/randomUUID)) 0 12)))

(defn- now-iso []
  (str (java.time.Instant/now)))

(defn- ->json [x]
  (when x (json/write-str x)))

(defn- <-json [s]
  (when (and s (not (str/blank? s)))
    (json/read-str s :key-fn keyword)))

(def ^:private query-opts
  {:builder-fn rs/as-unqualified-maps})

(defn create-arrow!
  "Create a typed arrow between entities.

   Required:
   - :source-id - source entity id
   - :target-id - target entity id
   - :mode - arrow mode (see namespace doc)

   Optional:
   - :payload - the construction/proof (code, explanation, etc.)
   - :scope-tags - domains where this arrow applies
   - :confidence - 0.0 to 1.0
   - :status - :draft, :active, :retired (default :draft)
   - :rationale - why this arrow exists
   - :created-by - who/what created this"
  [ds {:keys [source-id target-id mode payload scope-tags confidence status rationale created-by]}]
  (let [id (gen-id)
        now (now-iso)]
    (jdbc/execute-one! ds
      ["INSERT INTO arrows (id, source_id, target_id, mode, payload, scope_tags, confidence, status, rationale, created_by, created_at, updated_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
       id source-id target-id (name mode) (->json payload) (->json scope-tags)
       (or confidence 0.5) (name (or status :draft)) rationale created-by now now]
      query-opts)
    {:id id :source-id source-id :target-id target-id :mode mode}))

(defn get-arrow
  "Retrieve an arrow by id."
  [ds id]
  (some-> (jdbc/execute-one! ds
            ["SELECT * FROM arrows WHERE id = ?" id]
            query-opts)
          (update :payload <-json)
          (update :scope-tags <-json)
          (update :mode keyword)
          (update :status keyword)))

(defn update-arrow!
  "Update arrow fields."
  [ds id updates]
  (let [now (now-iso)
        ;; Convert keywords to strings for storage
        updates (cond-> updates
                  (:mode updates) (update :mode name)
                  (:status updates) (update :status name)
                  (:payload updates) (update :payload ->json)
                  (:scope-tags updates) (update :scope-tags ->json))
        sets (str/join ", " (for [k (keys updates)]
                              (str (str/replace (name k) "-" "_") " = ?")))
        vals (concat (vals updates) [now id])]
    (jdbc/execute-one! ds
      (into [(str "UPDATE arrows SET " sets ", updated_at = ? WHERE id = ?")]
            vals)
      query-opts)))

(defn activate-arrow!
  "Activate a draft arrow."
  [ds id]
  (update-arrow! ds id {:status :active}))

(defn retire-arrow!
  "Retire an active arrow."
  [ds id]
  (update-arrow! ds id {:status :retired}))

(defn strengthen-arrow!
  "Increase an arrow's confidence."
  [ds id delta]
  (let [arrow (get-arrow ds id)
        new-confidence (min 1.0 (+ (or (:confidence arrow) 0.5) delta))]
    (update-arrow! ds id {:confidence new-confidence})
    {:id id :confidence new-confidence}))

(defn weaken-arrow!
  "Decrease an arrow's confidence."
  [ds id delta]
  (let [arrow (get-arrow ds id)
        new-confidence (max 0.0 (- (or (:confidence arrow) 0.5) delta))]
    (update-arrow! ds id {:confidence new-confidence})
    {:id id :confidence new-confidence}))

;; Query operations

(defn arrows-from
  "List arrows from a source entity."
  [ds source-id & {:keys [mode status]}]
  (let [conditions ["source_id = ?"]
        params [source-id]
        [conditions params] (if mode
                              [(conj conditions "mode = ?") (conj params (name mode))]
                              [conditions params])
        [conditions params] (if status
                              [(conj conditions "status = ?") (conj params (name status))]
                              [conditions params])
        where (str/join " AND " conditions)
        sql (str "SELECT * FROM arrows WHERE " where " ORDER BY confidence DESC")]
    (->> (jdbc/execute! ds (into [sql] params) query-opts)
         (mapv #(-> %
                    (update :payload <-json)
                    (update :scope-tags <-json)
                    (update :mode keyword)
                    (update :status keyword))))))

(defn arrows-to
  "List arrows to a target entity."
  [ds target-id & {:keys [mode status]}]
  (let [conditions ["target_id = ?"]
        params [target-id]
        [conditions params] (if mode
                              [(conj conditions "mode = ?") (conj params (name mode))]
                              [conditions params])
        [conditions params] (if status
                              [(conj conditions "status = ?") (conj params (name status))]
                              [conditions params])
        where (str/join " AND " conditions)
        sql (str "SELECT * FROM arrows WHERE " where " ORDER BY confidence DESC")]
    (->> (jdbc/execute! ds (into [sql] params) query-opts)
         (mapv #(-> %
                    (update :payload <-json)
                    (update :scope-tags <-json)
                    (update :mode keyword)
                    (update :status keyword))))))

(defn find-arrow
  "Find an arrow between two entities."
  [ds source-id target-id & {:keys [mode]}]
  (let [sql (if mode
              ["SELECT * FROM arrows WHERE source_id = ? AND target_id = ? AND mode = ? ORDER BY confidence DESC LIMIT 1"
               source-id target-id (name mode)]
              ["SELECT * FROM arrows WHERE source_id = ? AND target_id = ? ORDER BY confidence DESC LIMIT 1"
               source-id target-id])]
    (some-> (jdbc/execute-one! ds sql query-opts)
            (update :payload <-json)
            (update :scope-tags <-json)
            (update :mode keyword)
            (update :status keyword))))

(defn list-arrows
  "List all arrows with optional filters."
  ([ds] (list-arrows ds {}))
  ([ds {:keys [mode status limit]}]
   (let [conditions []
         params []
         [conditions params] (if mode
                               [(conj conditions "mode = ?") (conj params (name mode))]
                               [conditions params])
         [conditions params] (if status
                               [(conj conditions "status = ?") (conj params (name status))]
                               [conditions params])
         where (when (seq conditions)
                 (str " WHERE " (str/join " AND " conditions)))
         limit-clause (if limit (str " LIMIT " limit) " LIMIT 100")
         sql (str "SELECT * FROM arrows" where " ORDER BY confidence DESC" limit-clause)]
     (->> (jdbc/execute! ds (into [sql] params) query-opts)
          (mapv #(-> %
                     (update :payload <-json)
                     (update :scope-tags <-json)
                     (update :mode keyword)
                     (update :status keyword)))))))

;; Convenience: create arrow with entity names

(defn assert-arrow!
  "Create an arrow between entities by name, creating entities if needed.

   This is the 'draw arrows freely' operation for the meme layer.
   The arrow starts as :draft with confidence based on whether
   a construction is provided."
  [ds source-name target-name mode & {:keys [payload rationale scope-tags created-by]}]
  (let [source (core/ensure-entity! ds source-name)
        target (core/ensure-entity! ds target-name)
        confidence (if payload 0.7 0.3)]  ; Higher if construction provided
    (create-arrow! ds
      {:source-id (:id source)
       :target-id (:id target)
       :mode mode
       :payload payload
       :scope-tags scope-tags
       :confidence confidence
       :status :draft
       :rationale rationale
       :created-by (or created-by "meme/assert")})))

;; Chain operations (for future use)

(defn chain-score
  "Score a chain of arrows.

   Scoring model:
   - typed arrow (with construction): base +3, softness +0
   - typed arrow (no construction): base +2, softness +1
   - untyped arrow: base +1, softness +2

   Returns {:base N :softness N :arrows [...]}"
  [arrows]
  (reduce
   (fn [acc arrow]
     (let [has-payload? (some? (:payload arrow))
           typed? (not= :untyped (:mode arrow))
           [base soft] (cond
                         (and typed? has-payload?) [3 0]
                         typed? [2 1]
                         :else [1 2])]
       (-> acc
           (update :base + base)
           (update :softness + soft)
           (update :arrows conj arrow))))
   {:base 0 :softness 0 :arrows []}
   arrows))
