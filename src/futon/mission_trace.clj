(ns futon.mission-trace
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.walk :as walk]))

(defn- now []
  (java.time.Instant/now))

(defn- trace-root []
  (or (some-> (System/getenv "MISSION_SEARCH_LOG_ROOT") str/trim not-empty)
      (some-> (System/getProperty "MISSION_SEARCH_LOG_ROOT") str/trim not-empty)
      "log"))

(defn trace-path []
  (io/file (trace-root) "mission-search-trace.edn"))

(defn- serializable [entry]
  (walk/postwalk
   (fn [value]
     (if (instance? java.time.Instant value)
       (java.util.Date/from ^java.time.Instant value)
       value))
   entry))

(defn emit!
  [event]
  (let [path (trace-path)
        record (assoc event :emitted-at (now))]
    (.mkdirs (.getParentFile path))
    (spit path (str (pr-str (serializable record)) "\n") :append true)
    record))

(defn emit-divergence!
  [event]
  (let [v1_1-rank (or (:v1_1-rank event)
                      (:v1.1-rank event))]
  (emit! {:event/type :mission-search/divergence
          :query/id (:query-id event)
          :query/text (:query event)
          :result/id (:result-id event)
          :result/title (:title event)
          :v1/rank (:v1-rank event)
          :v1.1/rank v1_1-rank
          :confidence (:confidence event)
          :divergence-score (:divergence-score event)})))

(defn emit-consumer-event!
  [{:keys [query-id consumer-id result-id action title]}]
  (emit! {:event/type :mission-search/consumer-event
          :query/id query-id
          :consumer/id consumer-id
          :result/id result-id
          :result/title title
          :action action}))
