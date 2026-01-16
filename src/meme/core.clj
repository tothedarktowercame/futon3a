(ns meme.core
  "Core CRUD operations for the meme layer.

   Provides basic entity/artifact/alias/mention management.
   For proposals and arrows, see meme.proposal and meme.arrow."
  (:require [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]
            [clojure.data.json :as json]
            [clojure.string :as str])
  (:import [java.security MessageDigest]
           [java.util UUID]))

(defn- gen-id
  ([] (gen-id nil))
  ([prefix]
   (let [uuid (subs (str (UUID/randomUUID)) 0 8)]
     (if prefix
       (str prefix "-" uuid)
       uuid))))

(defn- sha256 [s]
  (let [md (MessageDigest/getInstance "SHA-256")
        bytes (.digest md (.getBytes (str s) "UTF-8"))]
    (apply str (map #(format "%02x" (bit-and % 0xff)) bytes))))

(defn- now-iso []
  (str (java.time.Instant/now)))

(defn- ->json [x]
  (when x (json/write-str x)))

(defn- <-json [s]
  (when (and s (not (str/blank? s)))
    (json/read-str s :key-fn keyword)))

(def ^:private query-opts
  {:builder-fn rs/as-unqualified-maps})

;; Artifacts

(defn create-artifact!
  "Create an artifact (raw input) in the meme layer.
   Content is hashed for deduplication."
  [ds {:keys [kind source content metadata]}]
  (let [id (gen-id "art")
        hash (sha256 (str kind "|" content))
        now (now-iso)]
    (jdbc/execute-one! ds
      ["INSERT INTO artifacts (id, hash, kind, source, content, metadata, created_at)
        VALUES (?, ?, ?, ?, ?, ?, ?)"
       id hash kind source content (->json metadata) now]
      query-opts)
    {:id id :hash hash}))

(defn get-artifact
  "Retrieve an artifact by id."
  [ds id]
  (some-> (jdbc/execute-one! ds
            ["SELECT * FROM artifacts WHERE id = ?" id]
            query-opts)
          (update :metadata <-json)))

(defn find-artifact-by-hash
  "Find an artifact by its content hash."
  [ds hash]
  (some-> (jdbc/execute-one! ds
            ["SELECT * FROM artifacts WHERE hash = ?" hash]
            query-opts)
          (update :metadata <-json)))

(defn list-artifacts
  "List artifacts, optionally filtered by kind."
  ([ds] (list-artifacts ds nil))
  ([ds kind]
   (let [sql (if kind
               ["SELECT * FROM artifacts WHERE kind = ? ORDER BY created_at DESC" kind]
               ["SELECT * FROM artifacts ORDER BY created_at DESC"])]
     (->> (jdbc/execute! ds sql query-opts)
          (mapv #(update % :metadata <-json))))))

;; Entities

(defn create-entity!
  "Create an entity (canonical node) in the meme layer."
  [ds {:keys [name kind description metadata]}]
  (let [id (gen-id "ent")
        now (now-iso)]
    (jdbc/execute-one! ds
      ["INSERT INTO entities (id, name, kind, description, metadata, created_at, updated_at)
        VALUES (?, ?, ?, ?, ?, ?, ?)"
       id name kind description (->json metadata) now now]
      query-opts)
    {:id id :name name}))

(defn get-entity
  "Retrieve an entity by id."
  [ds id]
  (some-> (jdbc/execute-one! ds
            ["SELECT * FROM entities WHERE id = ?" id]
            query-opts)
          (update :metadata <-json)))

(defn find-entity-by-name
  "Find an entity by name."
  [ds name]
  (some-> (jdbc/execute-one! ds
            ["SELECT * FROM entities WHERE name = ?" name]
            query-opts)
          (update :metadata <-json)))

(defn update-entity!
  "Update an entity's fields."
  [ds id updates]
  (let [now (now-iso)
        sets (str/join ", " (for [k (keys updates)] (str (name k) " = ?")))
        vals (concat (vals updates) [now id])]
    (jdbc/execute-one! ds
      (into [(str "UPDATE entities SET " sets ", updated_at = ? WHERE id = ?")]
            vals)
      query-opts)))

(defn list-entities
  "List entities, optionally filtered by kind."
  ([ds] (list-entities ds nil))
  ([ds kind]
   (let [sql (if kind
               ["SELECT * FROM entities WHERE kind = ? ORDER BY name" kind]
               ["SELECT * FROM entities ORDER BY name"])]
     (->> (jdbc/execute! ds sql query-opts)
          (mapv #(update % :metadata <-json))))))

;; Aliases

(defn create-alias!
  "Create an alias (surface form) pointing to an entity."
  [ds {:keys [entity-id surface-form source confidence]}]
  (let [id (gen-id "als")
        now (now-iso)]
    (jdbc/execute-one! ds
      ["INSERT INTO aliases (id, entity_id, surface_form, source, confidence, created_at)
        VALUES (?, ?, ?, ?, ?, ?)
        ON CONFLICT (entity_id, surface_form) DO UPDATE SET confidence = excluded.confidence"
       id entity-id surface-form source (or confidence 1.0) now]
      query-opts)
    {:id id :entity-id entity-id :surface-form surface-form}))

(defn resolve-alias
  "Resolve a surface form to its entity."
  [ds surface-form]
  (jdbc/execute-one! ds
    ["SELECT e.* FROM entities e
      JOIN aliases a ON a.entity_id = e.id
      WHERE a.surface_form = ?
      ORDER BY a.confidence DESC
      LIMIT 1" surface-form]
    query-opts))

(defn list-aliases
  "List aliases for an entity."
  [ds entity-id]
  (jdbc/execute! ds
    ["SELECT * FROM aliases WHERE entity_id = ? ORDER BY confidence DESC" entity-id]
    query-opts))

;; Mentions

(defn create-mention!
  "Create a mention (span extraction) within an artifact."
  [ds {:keys [artifact-id entity-id alias-id span-start span-end surface-text confidence]}]
  (let [id (gen-id "mnt")
        now (now-iso)]
    (jdbc/execute-one! ds
      ["INSERT INTO mentions (id, artifact_id, entity_id, alias_id, span_start, span_end, surface_text, confidence, created_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)"
       id artifact-id entity-id alias-id span-start span-end surface-text (or confidence 1.0) now]
      query-opts)
    {:id id}))

(defn list-mentions
  "List mentions in an artifact or for an entity."
  ([ds] (list-mentions ds {}))
  ([ds {:keys [artifact-id entity-id]}]
   (cond
     artifact-id
     (jdbc/execute! ds
       ["SELECT * FROM mentions WHERE artifact_id = ? ORDER BY span_start" artifact-id]
       query-opts)

     entity-id
     (jdbc/execute! ds
       ["SELECT * FROM mentions WHERE entity_id = ? ORDER BY created_at DESC" entity-id]
       query-opts)

     :else
     (jdbc/execute! ds
       ["SELECT * FROM mentions ORDER BY created_at DESC LIMIT 100"]
       query-opts))))

;; Utility

(defn ensure-entity!
  "Get or create an entity by name."
  [ds name & {:keys [kind description metadata]}]
  (or (find-entity-by-name ds name)
      (create-entity! ds {:name name
                          :kind kind
                          :description description
                          :metadata metadata})))
