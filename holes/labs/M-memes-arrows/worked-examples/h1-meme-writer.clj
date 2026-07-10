;; H1 worked example — persisted meme.db writer.
;;
;; Run:
;;   cd /home/joe/code/futon3a
;;   clojure -M holes/labs/M-memes-arrows/worked-examples/h1-meme-writer.clj
;;
;; Optional:
;;   MEME_DB_PATH=/path/to/meme.db clojure -M holes/labs/M-memes-arrows/worked-examples/h1-meme-writer.clj

(require '[clojure.java.io :as io]
         '[meme.arrow :as arrow]
         '[meme.writer :as writer]
         '[next.jdbc :as jdbc])

(def arrows-to-write
  [{:example "r3a-constructed"
    :source "belief-mass-on-supports-tagged-cohort"
    :target "support-coverage-channel"
    :mode :construction
    :status :constructed
    :payload {:construction "futon2.aif.belief/predict-support-coverage"
              :cg "cg-17bbaa01-33fc-4a31-bcc6-568cc047f093"
              :prerequisite "futon2.aif.belief/classify-entity-tags-from-stack-annotations"
              :shipped "2026-05-26"}
    :scope-tags ["sorry" "wm-channel" "h1"]
    :confidence 0.9
    :rationale "sorry/r3a-likelihood-support-coverage: construction shipped; support-coverage derives from the supports-tagged belief cohort."
    :created-by "h1-meme-writer"}

   {:example "pattern-cascade-correlated"
    :source "construct-an-explicit-witness"
    :target "reduce-to-known-result"
    :mode :analogy
    :status :correlated
    :payload nil
    :scope-tags ["pattern-cascade" "h1"]
    :confidence 0.6
    :rationale "Co-applied across 8 missions in pattern_phylogeny; correlation only, no construction."
    :created-by "h1-meme-writer"}

   {:example "r3a-open-coupling-density"
    :source "coupling-density-channel-measured-structurally"
    :target "predict-coupling-density-from-belief-mass"
    :mode :untyped
    :status :open
    :payload nil
    :scope-tags ["sorry" "wm-channel" "h1"]
    :confidence 0.3
    :rationale "sorry/r3a-likelihood-coupling-density: RHS specified; construction absent."
    :created-by "h1-meme-writer"}])

(defn- table-count [ds table]
  (:n (jdbc/execute-one! ds [(str "SELECT COUNT(*) AS n FROM " table)])))

(defn- comparable [arrow-row]
  (select-keys arrow-row [:source_id :target_id :mode :status :payload]))

(defn- read-back [ds written]
  (some #(when (= (:id written) (:id %)) %)
        (arrow/arrows-from ds (:source_id written))))

(defn- assert-round-trip! [ds written]
  (let [actual (read-back ds written)
        expected (comparable written)
        observed (comparable actual)]
    (when-not actual
      (throw (ex-info "arrow not returned by arrows-from"
                      {:id (:id written)
                       :source (:source-name written)
                       :target (:target-name written)})))
    (when-not (= expected observed)
      (throw (ex-info "round-trip mismatch"
                      {:id (:id written)
                       :expected expected
                       :observed observed})))
    (assoc actual
           :source-name (:source-name written)
           :target-name (:target-name written))))

(defn- print-arrow [prefix arrow-row]
  (println (format "%s %-28s %-48s -> %-44s mode=%-12s status=%s payload?=%s"
                   prefix
                   (:id arrow-row)
                   (:source-name arrow-row)
                   (:target-name arrow-row)
                   (name (:mode arrow-row))
                   (name (:status arrow-row))
                   (some? (:payload arrow-row)))))

(def wx-db-path
  "Isolated DB for a DETERMINISTIC worked example (reset each run, so before=0/after=3 every
   run). The writer ITSELF defaults to the canonical (writer/meme-db-path); we isolate here only
   so the demo doesn't pollute the canonical store or count non-deterministically."
  (or (System/getenv "MEME_DB_PATH") "/tmp/h1-meme-writer.db"))

(defn -main []
  (io/delete-file wx-db-path true)               ; fresh db each run -> deterministic counts
  (let [db-path wx-db-path
        ds (writer/ensure-db! db-path)
        before (table-count ds "arrows")
        written (writer/write-arrows! ds arrows-to-write)
        read (mapv #(assert-round-trip! ds %) written)
        after (table-count ds "arrows")]
    (println "=== H1 meme writer worked example ===")
    (println "writer canonical default:" (writer/meme-db-path) "(R5)")
    (println "worked-example db (isolated):" (.getCanonicalPath (io/file db-path)))
    (println "arrows before:" before)
    (println "arrows written:" (count written))
    (println "arrows after:" after)
    (println)
    (doseq [arrow-row read]
      (print-arrow "READ" arrow-row))
    (println)
    (println (format "PASS persisted-count=%d round-tripped=%d db=%s"
                     after (count read) (.getCanonicalPath (io/file db-path))))))

(try
  (-main)
  (catch Throwable t
    (binding [*out* *err*]
      (println "=== H1 meme writer worked example ===")
      (println "FAIL" (.getMessage t))
      (when (ex-data t)
        (prn (ex-data t))))
    (System/exit 1)))
