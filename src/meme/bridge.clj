(ns meme.bridge
  "Bridge triples: explicit warrants for sense-shift hops.

   A sense-shift (sense-broaden or sense-narrow) is only valid when:
   - the hop is a typed arrow whose mode encodes the shift, or
   - the hop is backed by an explicit bridge triple that justifies it

   Bridges are curated relationships that allow crossing domain boundaries.
   They serve as the 'warrant' for chains that would otherwise be ungated.

   Example:
   distributed swarm → distributed routing
   requires bridge: (swarm, derived_from, routing) with rationale

   Predicates:
   - :derived-from - target concept derives from source
   - :analogous-to - structural similarity
   - :implements - target implements source pattern
   - :generalizes - source generalizes target
   - :specializes - source specializes target
   - :maps-to - cross-domain mapping"
  (:require [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]
            [clojure.string :as str])
  (:import [java.util UUID]))

(defn- gen-id []
  (str "brg-" (subs (str (UUID/randomUUID)) 0 12)))

(defn- now-iso []
  (str (java.time.Instant/now)))

(def ^:private query-opts
  {:builder-fn rs/as-unqualified-maps})

(defn create-bridge!
  "Create a bridge triple (explicit warrant for sense-shift).

   Required:
   - :subject-id - source entity id
   - :predicate - relationship type
   - :object-id - target entity id

   Optional:
   - :rationale - why this bridge exists
   - :status - :active or :retired (default :active)
   - :created-by - who/what created this"
  [ds {:keys [subject-id predicate object-id rationale status created-by]}]
  (let [id (gen-id)
        now (now-iso)]
    (jdbc/execute-one! ds
      ["INSERT INTO bridges (id, subject_id, predicate, object_id, rationale, status, created_by, created_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?)"
       id subject-id (name predicate) object-id rationale (name (or status :active)) created-by now]
      query-opts)
    {:id id :subject-id subject-id :predicate predicate :object-id object-id}))

(defn get-bridge
  "Retrieve a bridge by id."
  [ds id]
  (some-> (jdbc/execute-one! ds
            ["SELECT * FROM bridges WHERE id = ?" id]
            query-opts)
          (update :predicate keyword)
          (update :status keyword)))

(defn retire-bridge!
  "Retire an active bridge."
  [ds id]
  (let [now (now-iso)]
    (jdbc/execute-one! ds
      ["UPDATE bridges SET status = 'retired', updated_at = ? WHERE id = ?" now id]
      query-opts)))

(defn find-bridge
  "Find a bridge between two entities."
  [ds subject-id object-id & {:keys [predicate]}]
  (let [sql (if predicate
              ["SELECT * FROM bridges WHERE subject_id = ? AND object_id = ? AND predicate = ? AND status = 'active' LIMIT 1"
               subject-id object-id (name predicate)]
              ["SELECT * FROM bridges WHERE subject_id = ? AND object_id = ? AND status = 'active' LIMIT 1"
               subject-id object-id])]
    (some-> (jdbc/execute-one! ds sql query-opts)
            (update :predicate keyword)
            (update :status keyword))))

(defn bridges-from
  "List active bridges from a subject entity."
  [ds subject-id]
  (->> (jdbc/execute! ds
         ["SELECT * FROM bridges WHERE subject_id = ? AND status = 'active'" subject-id]
         query-opts)
       (mapv #(-> %
                  (update :predicate keyword)
                  (update :status keyword)))))

(defn bridges-to
  "List active bridges to an object entity."
  [ds object-id]
  (->> (jdbc/execute! ds
         ["SELECT * FROM bridges WHERE object_id = ? AND status = 'active'" object-id]
         query-opts)
       (mapv #(-> %
                  (update :predicate keyword)
                  (update :status keyword)))))

(defn list-bridges
  "List all bridges with optional filters."
  ([ds] (list-bridges ds {}))
  ([ds {:keys [predicate status limit]}]
   (let [conditions []
         params []
         [conditions params] (if predicate
                               [(conj conditions "predicate = ?") (conj params (name predicate))]
                               [conditions params])
         [conditions params] (if status
                               [(conj conditions "status = ?") (conj params (name status))]
                               [conditions params])
         where (when (seq conditions)
                 (str " WHERE " (str/join " AND " conditions)))
         limit-clause (if limit (str " LIMIT " limit) " LIMIT 100")
         sql (str "SELECT * FROM bridges" where " ORDER BY created_at DESC" limit-clause)]
     (->> (jdbc/execute! ds (into [sql] params) query-opts)
          (mapv #(-> %
                     (update :predicate keyword)
                     (update :status keyword)))))))

;; Chain validation

(defn hop-warranted?
  "Check if a sense-shift hop is warranted.

   A hop from source to target is warranted if:
   1. There's an active bridge between them, or
   2. There's an active typed arrow (not :untyped) between them"
  [ds source-id target-id]
  (or
   ;; Check for bridge
   (some? (find-bridge ds source-id target-id))
   ;; Check for typed arrow
   (some? (jdbc/execute-one! ds
            ["SELECT id FROM arrows
              WHERE source_id = ? AND target_id = ?
              AND mode != 'untyped' AND status = 'active'
              LIMIT 1"
             source-id target-id]
            query-opts))))

(defn validate-chain
  "Validate a chain of entity ids.

   Returns:
   - :valid if all hops are warranted
   - :ungated if any hop lacks a warrant
   - includes details of each hop"
  [ds entity-ids]
  (let [pairs (partition 2 1 entity-ids)
        results (for [[from to] pairs]
                  {:from from
                   :to to
                   :warranted? (hop-warranted? ds from to)})]
    {:status (if (every? :warranted? results) :valid :ungated)
     :hops (vec results)
     :ungated-count (count (remove :warranted? results))}))
