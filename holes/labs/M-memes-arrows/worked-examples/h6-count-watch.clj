;; H6 worked example — Contract A count-watch tripwire.
;;
;; Run:
;;   cd /home/joe/code/futon3a
;;   clojure -M holes/labs/M-memes-arrows/worked-examples/h6-count-watch.clj

(require '[clojure.java.io :as io]
         '[clojure.string :as str]
         '[meme.count-watch :as count-watch]
         '[meme.schema :as schema])

(def db-path "/tmp/futon3a-h6-meme.db")
(def threshold count-watch/default-threshold)

(defn- reset-db! []
  (io/delete-file db-path true)
  (let [ds (schema/datasource db-path)]
    (schema/ensure-db! ds)
    ds))

(defn- assert! [pred message data]
  (when-not pred
    (throw (ex-info message data))))

(defn -main []
  (let [ds (reset-db!)
        persisted-small (count-watch/watch {:ds ds
                                            :threshold threshold})
        synthetic-small (count-watch/watch {:count 42
                                            :threshold threshold})
        synthetic-over (count-watch/watch! {:count (inc threshold)
                                            :threshold threshold})
        breach-message (:message synthetic-over)]
    (println "=== H6 Contract A count-watch worked example ===")
    (println "meme.db:" (.getCanonicalPath (io/file db-path)))
    (println "persisted-small:" persisted-small)
    (println "synthetic-small:" synthetic-small)
    (println "synthetic-over:" synthetic-over)
    (assert! (false? (:flag? persisted-small))
             "empty persisted meme.db should be silent"
             {:result persisted-small})
    (assert! (false? (:flag? synthetic-small))
             "synthetic below-threshold count should be silent"
             {:result synthetic-small})
    (assert! (true? (:flag? synthetic-over))
             "synthetic over-threshold count should fire"
             {:result synthetic-over})
    (assert! (= :loud (:severity synthetic-over))
             "over-threshold count should be loud"
             {:result synthetic-over})
    (assert! (and (str/includes? breach-message "Contract A")
                  (str/includes? breach-message "fast-triple-store decision"))
             "breach message should name Contract A and triple-store decision"
             {:message breach-message})
    (println (format (str "PASS threshold=%d persisted-count=%d persisted-silent=%s "
                          "synthetic-count=%d synthetic-fired=%s")
                     threshold
                     (:count persisted-small)
                     (not (:flag? persisted-small))
                     (:count synthetic-over)
                     (:flag? synthetic-over)))))

(try
  (-main)
  (shutdown-agents)
  (catch Throwable t
    (binding [*out* *err*]
      (println "=== H6 Contract A count-watch worked example ===")
      (println "FAIL" (.getMessage t))
      (when (ex-data t)
        (prn (ex-data t))))
    (shutdown-agents)
    (System/exit 1)))
