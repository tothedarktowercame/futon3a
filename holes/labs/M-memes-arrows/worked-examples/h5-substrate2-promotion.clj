;; H5 worked example — non-live substrate-2 sorry projection.
;;
;; Run:
;;   cd /home/joe/code/futon3a
;;   clojure -M holes/labs/M-memes-arrows/worked-examples/h5-substrate2-promotion.clj
;;
;; This fixture does not POST to futon1a. It only builds the projection doc.

(require '[clojure.edn :as edn]
         '[clojure.java.io :as io]
         '[meme.identity :as identity]
         '[meme.schema :as schema]
         '[meme.substrate2 :as substrate2])

(def db-path "/tmp/futon3a-h5-meme.db")

(def r3a-endpoint
  {:have "belief-mass-on-supports-tagged-cohort"
   :want "support-coverage-channel"})

(def open-endpoint
  {:have "coupling-density-channel-measured-structurally"
   :want "predict-coupling-density-from-belief-mass"})

(def r3a-construction
  {:construction "futon2.aif.belief/predict-support-coverage"
   :cg "cg-17bbaa01-33fc-4a31-bcc6-568cc047f093"
   :prerequisite "futon2.aif.belief/classify-entity-tags-from-stack-annotations"
   :shipped "2026-05-26"})

(defn- reset-db! []
  (io/delete-file db-path true)
  (let [ds (schema/datasource db-path)]
    (schema/ensure-db! ds)
    ds))

(defn- assert! [pred message data]
  (when-not pred
    (throw (ex-info message data))))

(defn- constructed-r3a! [ds]
  (identity/mint-or-unify!
   ds r3a-endpoint
   {:mode :analogy
    :status :correlated
    :rationale "r3a support-coverage relation first recorded as correlated."})
  (identity/promote!
   ds r3a-endpoint :open
   :mode :untyped
   :rationale "r3a has a fixed have/want shape; construction still absent.")
  (:arrow
   (identity/promote!
    ds r3a-endpoint :constructed
    :mode :construction
    :payload r3a-construction
    :rationale "predict-support-coverage construction shipped and verified.")))

(defn- open-arrow! [ds]
  (:arrow
   (identity/mint-or-unify!
    ds open-endpoint
    {:mode :untyped
     :status :open
     :rationale "RHS specified; construction absent."})))

(defn -main []
  (let [ds (reset-db!)
        constructed (constructed-r3a! ds)
        doc (substrate2/arrow->sorry-doc ds constructed
                                         :label "futon3a"
                                         :source-file db-path)
        read-back (edn/read-string (pr-str doc))
        props (:props doc)
        endpoint-key (identity/endpoint-key r3a-endpoint)
        open-arrow (open-arrow! ds)
        refusal (try
                  (substrate2/arrow->sorry-doc ds open-arrow)
                  nil
                  (catch clojure.lang.ExceptionInfo e
                    (ex-data e)))]
    (println "=== H5 substrate-2 promotion worked example ===")
    (println "NON-LIVE: no POST to futon1a / substrate-2")
    (println "meme.db:" (.getCanonicalPath (io/file db-path)))
    (println "constructed-arrow:" (:id constructed))
    (println "projection-doc:" doc)
    (println "roundtrip-equal?:" (= doc read-back))
    (println "promoted-from:" (get props "promoted-from"))
    (println "open-refusal:" refusal)
    (assert! (= "code/v05/sorry" (:hx-type doc))
             "projection doc has wrong hx type"
             {:doc doc})
    (assert! (= 1 (count (:endpoints doc)))
             "projection doc must be one-endpoint code/v05/sorry"
             {:doc doc})
    (assert! (= endpoint-key (get props "promoted-from"))
             "projection doc missing promoted-from endpoint key"
             {:expected endpoint-key
              :actual (get props "promoted-from")})
    (assert! (= doc read-back)
             "projection doc did not round-trip losslessly"
             {:doc doc
              :read-back read-back})
    (assert! (= :boundary/non-constructed-arrow (:reason refusal))
             ":open arrow was not refused by substrate-2 boundary guard"
             {:refusal refusal})
    (println (format "PASS hx-type=%s one-endpoint=%s promoted-from=%s roundtrip=%s open-refused=%s"
                     (:hx-type doc)
                     (= 1 (count (:endpoints doc)))
                     (= endpoint-key (get props "promoted-from"))
                     (= doc read-back)
                     (= :boundary/non-constructed-arrow (:reason refusal))))))

(try
  (-main)
  (shutdown-agents)
  (catch Throwable t
    (binding [*out* *err*]
      (println "=== H5 substrate-2 promotion worked example ===")
      (println "FAIL" (.getMessage t))
      (when (ex-data t)
        (prn (ex-data t))))
    (shutdown-agents)
    (System/exit 1)))
