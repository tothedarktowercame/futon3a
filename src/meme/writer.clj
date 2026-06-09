(ns meme.writer
  "Live writer for the persisted meme.db arrow store."
  (:require [clojure.java.io :as io]
            [meme.arrow :as arrow]
            [meme.core :as core]
            [meme.schema :as schema]))

(def canonical-meme-db-path
  "/home/joe/code/futon3a/meme.db")

(defn meme-db-path
  "Return the canonical meme.db path, overridable by MEME_DB_PATH."
  []
  (or (System/getenv "MEME_DB_PATH")
      canonical-meme-db-path))

(defn datasource
  "Create a datasource for the configured meme.db path."
  ([] (datasource (meme-db-path)))
  ([path]
   (schema/datasource path)))

(defn ensure-db!
  "Create parent directories as needed and initialize the meme schema."
  ([] (ensure-db! (meme-db-path)))
  ([path]
   (io/make-parents path)
   (schema/ensure-db! (datasource path))))

(defn write-arrow!
  "Persist one named-endpoint arrow through the existing meme.arrow API.

   Required keys:
   - :source / :target: endpoint entity names
   - :mode: arrow mode
   - :status: lifecycle status, e.g. :correlated/:open/:constructed

   Optional keys are passed through to meme.arrow/create-arrow!."
  [ds {:keys [source target mode status payload scope-tags advances-cap confidence rationale created-by]}]
  (let [source-entity (core/ensure-entity! ds source)
        target-entity (core/ensure-entity! ds target)
        created (arrow/create-arrow!
                 ds
                 {:source-id (:id source-entity)
                  :target-id (:id target-entity)
                  :mode mode
                  :payload payload
                  :scope-tags scope-tags
                  :advances-cap advances-cap
                  :confidence confidence
                  :status status
                  :rationale rationale
                  :created-by (or created-by "meme.writer/write-arrow!")})]
    (assoc (arrow/get-arrow ds (:id created))
           :source-name (:name source-entity)
           :target-name (:name target-entity))))

(defn write-arrows!
  "Persist arrows in order and return their stored rows."
  [ds arrows]
  (mapv #(write-arrow! ds %) arrows))
