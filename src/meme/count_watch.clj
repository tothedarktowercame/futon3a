(ns meme.count-watch
  "Contract A count tripwire for the meme arrow store.

   Contract A says the persisted sorry/arrow store stays in the coarse
   10^3-10^4 envelope. Crossing the upper envelope does not migrate storage; it
   emits the review flag that reopens the fast-triple-store decision."
  (:require [next.jdbc :as jdbc]
            [meme.schema :as schema]))

(def default-threshold
  "Upper Contract A envelope for persisted sorry/arrows."
  10000)

(def default-db-path
  "/home/joe/code/futon3a/meme.db")

(defn meme-db-path
  []
  (or (System/getenv "MEME_DB_PATH")
      default-db-path))

(defn datasource
  ([]
   (datasource (meme-db-path)))
  ([db-path]
   (schema/datasource db-path)))

(defn arrows-count
  "Return the persisted arrow count for an initialized meme datasource."
  [ds]
  (long
   (:n
    (jdbc/execute-one! ds ["select count(*) as n from arrows"]))))

(defn breach-message
  [count threshold]
  (format
   (str "LOUD Contract A envelope breach: persisted sorry/arrow count %d "
        "exceeds threshold %d; this reopens the fast-triple-store decision.")
   count
   threshold))

(defn watch
  "Check the persisted arrow count.

   Options:
   - :db-path chooses the meme.db path when :count is absent.
   - :ds supplies an existing datasource.
   - :count supplies an explicit count for probes/fixtures.
   - :threshold defaults to default-threshold.

   Returns a map. Below threshold is silent: :flag? false and no :message."
  ([] (watch {}))
  ([{:keys [db-path ds count threshold]
     :or {threshold default-threshold}}]
   (let [count (long (or count
                         (arrows-count (or ds
                                           (datasource (or db-path
                                                           (meme-db-path)))))))
         breach? (> count threshold)]
     (cond-> {:contract :contract-a
              :count count
              :threshold threshold
              :flag? breach?}
       breach?
       (assoc :severity :loud
              :reason :contract-a/envelope-breach
              :reopens :fast-triple-store-decision
              :message (breach-message count threshold))))))

(defn watch!
  "Run watch and print only when Contract A fires."
  ([] (watch! {}))
  ([opts]
   (let [result (watch opts)]
     (when (:flag? result)
       (println (:message result)))
     result)))
