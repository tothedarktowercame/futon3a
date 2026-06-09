;; H3 worked example — endpoint identity + Contract-C promotion.
;;
;; Run:
;;   cd /home/joe/code/futon3a
;;   clojure -M holes/labs/M-memes-arrows/worked-examples/h3-endpoint-identity.clj
;;
;; Optional:
;;   MEME_DB_PATH=/path/to/meme.db clojure -M holes/labs/M-memes-arrows/worked-examples/h3-endpoint-identity.clj

(require '[clojure.data.json :as json]
         '[clojure.java.io :as io]
         '[meme.arrow :as arrow]
         '[meme.core :as core]
         '[meme.identity :as ident]
         '[meme.schema :as schema]
         '[next.jdbc :as jdbc])

(def db-path
  (or (System/getenv "MEME_DB_PATH")
      "/tmp/futon3a-h3-meme.db"))

(def r3a-endpoint
  {:have "belief-mass-on-supports-tagged-cohort"
   :want "support-coverage-channel"})

(def r3a-construction
  {:construction "futon2.aif.belief/predict-support-coverage"
   :cg "cg-17bbaa01-33fc-4a31-bcc6-568cc047f093"
   :prerequisite "futon2.aif.belief/classify-entity-tags-from-stack-annotations"
   :shipped "2026-05-26"})

(defn- reset-db!
  ([] (reset-db! db-path))
  ([path]
   (io/delete-file path true)
   (let [ds (schema/datasource path)]
     (schema/ensure-db! ds)
     ds)))

(defn- adversarial-db! [inv]
  (reset-db! (str "/tmp/futon3a-h3-" (name inv) ".db")))

(defn- table-count [ds table]
  (:n (jdbc/execute-one! ds [(str "SELECT COUNT(*) AS n FROM " table)])))

(defn- row-view [row]
  {:id (:id row)
   :source-id (:source_id row)
   :target-id (:target_id row)
   :mode (:mode row)
   :status (:status row)
   :payload? (some? (:payload row))
   :scope-tags (:scope-tags row)})

(defn- print-row [label row]
  (println (format "%-14s %s" label (pr-str (row-view row)))))

(defn- assert! [pred message data]
  (when-not pred
    (throw (ex-info message data))))

(defn- insert-raw-arrow!
  [ds {:keys [id source-id target-id mode status payload rationale]}]
  (jdbc/execute-one!
   ds
   ["INSERT INTO arrows
     (id, source_id, target_id, mode, payload, scope_tags, confidence, status, rationale, created_by, created_at, updated_at)
     VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, datetime('now'), datetime('now'))"
    id source-id target-id (name mode) (when payload (json/write-str payload))
    nil 0.5 (name status) rationale "h3-adversarial"])
  (arrow/get-arrow ds id))

(defn- endpoint-ids! [ds endpoint]
  (let [source (core/ensure-entity! ds (:have endpoint) :kind "aif-endpoint")
        target (core/ensure-entity! ds (:want endpoint) :kind "aif-endpoint")]
    {:source-id (:id source)
     :target-id (:id target)}))

(defn- conforming-sequence! [ds]
  (let [minted (ident/mint-or-unify!
                ds r3a-endpoint
                {:mode :analogy
                 :status :correlated
                 :confidence 0.6
                 :rationale "r3a support-coverage relation first recorded as correlated."
                 :token-id :sorry/r3a-likelihood-support-coverage})
        opened (ident/promote!
                ds r3a-endpoint :open
                :mode :untyped
                :rationale "r3a has a fixed have/want shape; construction still absent.")
        constructed (ident/promote!
                     ds r3a-endpoint :constructed
                     :mode :construction
                     :payload r3a-construction
                     :rationale "predict-support-coverage construction shipped and verified.")
        duplicate (ident/mint-or-unify!
                   ds r3a-endpoint
                   {:mode :untyped
                    :status :open
                    :token-id :sorry/aif-head-missing-r3a-support-coverage
                    :rationale "Synthetic duplicate attempt for the same endpoint pair."})
        ops (mapv :op [minted opened constructed duplicate])]
    {:minted minted
     :opened opened
     :constructed constructed
     :duplicate duplicate
     :ops ops}))

(defn- seed-constructed! [ds]
  (let [result (conforming-sequence! ds)]
    {:id (get-in result [:constructed :arrow :id])
     :ops (:ops result)}))

(defn- adversarial-i1 []
  (let [ds (adversarial-db! :i1)
        {:keys [ops]} (seed-constructed! ds)
        {:keys [source-id target-id]} (endpoint-ids! ds r3a-endpoint)]
    (insert-raw-arrow! ds {:id "adv-i1-duplicate"
                           :source-id source-id
                           :target-id target-id
                           :mode :untyped
                           :status :open
                           :rationale "duplicate endpoint row"})
    (ident/probe ds ops)))

(defn- adversarial-i2 []
  (let [ds (adversarial-db! :i2)
        {:keys [source-id target-id]} (endpoint-ids! ds r3a-endpoint)]
    (insert-raw-arrow! ds {:id "adv-i2-no-construction"
                           :source-id source-id
                           :target-id target-id
                           :mode :construction
                           :status :constructed
                           :payload nil
                           :rationale "constructed without construction payload"})
    (ident/probe ds [])))

(defn- adversarial-i3 []
  (let [ds (adversarial-db! :i3)
        {:keys [ops id]} (seed-constructed! ds)
        bad-op {:op :promote :id id :from :constructed :to :open
                :have (:have r3a-endpoint) :want (:want r3a-endpoint)}]
    (ident/probe ds (conj ops bad-op))))

(defn- adversarial-i4 []
  (let [ds (adversarial-db! :i4)
        {:keys [ops]} (seed-constructed! ds)
        bad-op {:op :mint :id "adv-i4-duplicate-mint"
                :have (:have r3a-endpoint) :want (:want r3a-endpoint)}]
    (ident/probe ds (conj ops bad-op))))

(defn- adversarial-i5 []
  (let [ds (adversarial-db! :i5)
        target (core/ensure-entity! ds (:want r3a-endpoint) :kind "aif-endpoint")]
    (insert-raw-arrow! ds {:id "adv-i5-missing-node"
                           :source-id "missing-source-entity-id"
                           :target-id (:id target)
                           :mode :untyped
                           :status :correlated
                           :rationale "endpoint source id does not resolve to an entity"})
    (ident/probe ds [])))

(def adversarial-fns
  {:i1 adversarial-i1
   :i2 adversarial-i2
   :i3 adversarial-i3
   :i4 adversarial-i4
   :i5 adversarial-i5})

(defn -main []
  (let [ds (reset-db!)
        result (conforming-sequence! ds)
        minted-row (get-in result [:minted :arrow])
        opened-row (get-in result [:opened :arrow])
        constructed-row (get-in result [:constructed :arrow])
        duplicate-row (get-in result [:duplicate :arrow])
        endpoint-rows (ident/arrows-by-endpoint ds r3a-endpoint)
        conforming-probe (ident/probe ds (:ops result))
        conforming-clean? (zero? (:violation-count conforming-probe))
        one-row? (= 1 (count endpoint-rows))
        same-row? (= 1 (count (set (map :id [minted-row opened-row constructed-row duplicate-row]))))
        dup-unified? (and (false? (get-in result [:duplicate :created?]))
                          (true? (get-in result [:duplicate :unified?])))
        adversarial-results (into {}
                                  (for [[inv f] adversarial-fns
                                        :let [probe-result (f)
                                              caught? (pos? (count (get-in probe-result [:violations inv])))]]
                                    [inv {:caught? caught?
                                          :violations (:violations probe-result)}]))
        caught-count (count (filter (comp :caught? val) adversarial-results))]
    (println "=== H3 endpoint identity worked example ===")
    (println "meme.db:" (.getCanonicalPath (io/file db-path)))
    (println "r3a endpoint:" r3a-endpoint)
    (println)
    (print-row "correlated" minted-row)
    (print-row "open" opened-row)
    (print-row "constructed" constructed-row)
    (print-row "dup-attempt" duplicate-row)
    (println)
    (println "endpoint-row-count:" (count endpoint-rows))
    (println "same-row-through-promotions?:" same-row?)
    (println "duplicate-unified?:" dup-unified?)
    (println "ops:" (:ops result))
    (println)
    (println "Conforming probe violation-count:" (:violation-count conforming-probe))
    (println "Conforming violations:" (:violations conforming-probe))
    (println)
    (println "Adversarial probe results:")
    (doseq [[inv {:keys [caught? violations]}] adversarial-results]
      (println (format "  %-3s caught=%-5s %s" (name inv) caught? (pr-str (get violations inv)))))
    (println)
    (assert! one-row? "r3a endpoint did not remain one row" {:rows endpoint-rows})
    (assert! same-row? "r3a promotions did not stay in place" {:rows [minted-row opened-row constructed-row duplicate-row]})
    (assert! dup-unified? "duplicate mint did not unify" {:duplicate (:duplicate result)})
    (assert! conforming-clean? "conforming sequence has probe violations" conforming-probe)
    (assert! (= 5 caught-count) "not all adversarial mutations were caught" adversarial-results)
    (println (format "PASS conforming-violations=%d adversarial-caught=%d/5 one-row=%s dup-unified=%s arrows=%d entities=%d"
                     (:violation-count conforming-probe)
                     caught-count
                     same-row?
                     dup-unified?
                     (table-count ds "arrows")
                     (table-count ds "entities")))))

(try
  (-main)
  (shutdown-agents)
  (catch Throwable t
    (binding [*out* *err*]
      (println "=== H3 endpoint identity worked example ===")
      (println "FAIL" (.getMessage t))
      (when (ex-data t)
        (prn (ex-data t))))
    (shutdown-agents)
    (System/exit 1)))
