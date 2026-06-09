;; H2 worked example — registry-canonicalised endpoint extraction.
;;
;; Run:
;;   cd /home/joe/code/futon3a
;;   clojure -M holes/labs/M-memes-arrows/worked-examples/h2-endpoint-extraction.clj

(require '[meme.endpoints :as endpoints])

(def sources
  {:live-wm-priority {:type "missing-head"
                     :id "mission-aif-head"
                     :note "computes locally; not served to the WM head"}
   :miner-mint :sorry/aif-head-missing-mission-aif-head
   :legacy-registry :sorry/mission-aif-head-not-served})

(def ground-truth
  {:have "aif-head/mission-aif-head/local"
   :want "aif-head/mission-aif-head/wm-readable"})

(def historical-id :sorry/mission-aif-head-not-served)
(def fresh-miner-id :sorry/aif-head-missing-mission-aif-head)

(defn- fail! [message data]
  (throw (ex-info message data)))

(defn- assert! [pred message data]
  (when-not pred
    (fail! message data)))

(defn- second-head-example [head-ids]
  (when-let [head-id (first (remove #{"mission-aif-head"} (sort head-ids)))]
    {:head-id head-id
     :endpoints (endpoints/extract-endpoints {:type "missing-head" :id head-id} head-ids)}))

(defn -main []
  (let [{:keys [head-ids source] :as registry} (endpoints/head-registry)
        canonical (into {}
                        (for [[k signal] sources]
                          [k (endpoints/extract-endpoints signal head-ids)]))
        canonical-heads (into {}
                              (for [[k signal] sources]
                                [k (endpoints/canonicalize-head-id signal head-ids)]))
        naive-historical (endpoints/endpoints-via-naive historical-id)
        naive-fresh (endpoints/endpoints-via-naive fresh-miner-id)
        naive-unify? (= naive-historical naive-fresh)
        canonical-unify? (= (endpoints/extract-endpoints historical-id head-ids)
                            (endpoints/extract-endpoints fresh-miner-id head-ids))
        agree? (= 1 (count (distinct (vals canonical))))
        match-ground-truth? (= ground-truth (:live-wm-priority canonical))
        second-head (second-head-example head-ids)]
    (assert! (contains? head-ids "mission-aif-head")
             "mission-aif-head absent from registry"
             {:registry registry})
    (assert! agree?
             "three source conventions did not canonicalise to one endpoint pair"
             {:canonical canonical})
    (assert! match-ground-truth?
             "extracted endpoints do not match documented :sorry/mission-aif-head-not-served endpoints"
             {:expected ground-truth
              :actual (:live-wm-priority canonical)})
    (assert! (false? naive-unify?)
             "naive regex unexpectedly unified the legacy and miner ids"
             {:historical naive-historical
              :fresh naive-fresh})
    (assert! canonical-unify?
             "registry-canonicalised extraction did not unify the legacy and miner ids"
             {:historical (endpoints/extract-endpoints historical-id head-ids)
              :fresh (endpoints/extract-endpoints fresh-miner-id head-ids)})

    (println "=== H2 endpoint extraction worked example ===")
    (println "registry-source:" source)
    (println "registry-head-count:" (count head-ids))
    (println "mission-aif-head canonical heads:" canonical-heads)
    (println)
    (println "Extracted endpoints by source convention:")
    (doseq [[k e] canonical]
      (println (format "  %-16s -> %s" (name k) e)))
    (println)
    (println "Ground truth :sorry/mission-aif-head-not-served:" ground-truth)
    (println "documented-match?:" match-ground-truth?)
    (println)
    (println "Naive regex comparison:")
    (println "  historical:" historical-id "=>" naive-historical)
    (println "  miner     :" fresh-miner-id "=>" naive-fresh)
    (println "  NAIVE unify=false:" naive-unify?)
    (println)
    (println "Canonical registry comparison:")
    (println "  historical:" historical-id "=>"
             (endpoints/extract-endpoints historical-id head-ids))
    (println "  miner     :" fresh-miner-id "=>"
             (endpoints/extract-endpoints fresh-miner-id head-ids))
    (println "  CANONICAL unify=true:" canonical-unify?)
    (when second-head
      (println)
      (println "Second registry head smoke:"
               (:head-id second-head) "=>" (:endpoints second-head)))
    (println)
    (println (format "PASS agree=%s documented-match=%s naive-unify=%s canonical-unify=%s registry=%s"
                     agree? match-ground-truth? naive-unify? canonical-unify? source))))

(try
  (-main)
  (shutdown-agents)
  (catch Throwable t
    (binding [*out* *err*]
      (println "=== H2 endpoint extraction worked example ===")
      (println "FAIL" (.getMessage t))
      (when (ex-data t)
        (prn (ex-data t))))
    (shutdown-agents)
    (System/exit 1)))
