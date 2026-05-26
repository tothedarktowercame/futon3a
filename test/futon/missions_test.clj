(ns futon.missions-test
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.test :refer [deftest is]]
            [futon.missions :as missions]))

(defn- write-file! [path text]
  (io/make-parents path)
  (spit path text)
  path)

(deftest parse-mission-doc-extracts-core-slots
  (let [root (str (io/file (System/getProperty "java.io.tmpdir")
                           (str "mission-test-" (random-uuid))))
        path (str (io/file root "futon7/holes/M-example.md"))]
    (write-file!
     path
     (str "# Mission: M-example\n\n"
          "**Status:** IDENTIFY (2026-05-21)\n"
          "**Owner:** Joe\n\n"
          "## 1. IDENTIFY\n\n"
          "### Motivation\n\n"
          "This mission explains how M-related-discovery should work.\n\n"
          "### Source material\n\n"
          "- `~/code/futon3a/README.md`\n"
          "- `futon4/dev/web/webarxana/src/webarxana/client/core.cljs`\n"))
    (let [mission (missions/parse-mission-doc (io/file path))]
      (is (= "mission/M-example@futon7" (:id mission)))
      (is (= "Joe" (:owner mission)))
      (is (= "IDENTIFY (2026-05-21)" (:status mission)))
      (is (= "identify" (:phase mission)))
      (is (= "2026-05-21" (:date mission)))
      (is (= ["M-related-discovery"] (:cross_refs mission)))
      (is (= (str (System/getProperty "user.home") "/code/futon3a/README.md")
             (first (:code_paths mission))))
      (is (.contains (:summary mission) "M-related-discovery")))))

(deftest load-missions-scans-canonical-paths-only
  (let [root (io/file (System/getProperty "java.io.tmpdir")
                      (str "mission-scan-" (random-uuid)))
        canonical-a (io/file root "futon3/holes/missions/M-alpha.md")
        canonical-b (io/file root "futon4/holes/M-beta.md")
        ignored (io/file root "futon4/holes/M-beta.journal/M-gamma.md")]
    (write-file! canonical-a "# Mission: M-alpha\n\n**Status:** MAP\n")
    (write-file! canonical-b "# Mission: M-beta\n\n**Status:** DERIVE\n")
    (write-file! ignored "# Mission: M-gamma\n\n**Status:** IDENTIFY\n")
    (let [missions (missions/load-missions {:roots [(io/file root "futon3")
                                                    (io/file root "futon4")]
                                            :use-records? false
                                            ;; T-9: skip substrate-2 query for hermetic test
                                            :prefer-substrate-2? false})]
      (is (= #{"mission/M-alpha@futon3" "mission/M-beta@futon4"}
             (set (map :id missions)))))))

(deftest structural-search-applies-slot-filters
  (let [missions [{:id "mission/M-alpha@futon3"
                   :title "Alpha"
                   :status "IDENTIFY"
                   :owner "Joe"
                   :phase "identify"
                   :phase_rank 1
                   :summary "Search surface for self documenting stack"
                   :cross_refs []
                   :date "2026-05-21"}
                  {:id "mission/M-beta@futon4"
                   :title "Beta"
                   :status "DERIVE"
                   :owner "Claude"
                   :phase "derive"
                   :phase_rank 3
                   :summary "Different topic entirely"
                   :cross_refs []
                   :date "2026-05-20"}]
        results (missions/structural-search missions
                                            "self documenting"
                                            {:top-k 5
                                             :slot-filters {:owner "Joe"
                                                            :phase "identify"}})]
    (is (= ["mission/M-alpha@futon3"] (mapv :id results)))))

(deftest mission-summary-prefers-plain-language-argument
  (let [root (str (io/file (System/getProperty "java.io.tmpdir")
                           (str "mission-argue-" (random-uuid))))
        path (str (io/file root "futon7/holes/M-argue.md"))]
    (write-file!
     path
     (str "# Mission: M-argue\n\n"
          "**Status:** DOCUMENT (2026-05-21)\n\n"
          "## 4. ARGUE\n\n"
          "### Plain-language argument (no jargon)\n\n"
          "This plain language summary should be preferred.\n\n"
          "## 1. IDENTIFY\n\n"
          "### Motivation\n\n"
          "This motivation should not win.\n"))
    (let [mission (missions/parse-mission-doc (io/file path))]
      (is (= "document" (:phase mission)))
      (is (= "This plain language summary should be preferred."
             (:summary mission))))))

(deftest mission-summary-falls-back-after-status-line
  (let [root (str (io/file (System/getProperty "java.io.tmpdir")
                           (str "mission-status-fallback-" (random-uuid))))
        path (str (io/file root "futon7/holes/M-status-fallback.md"))]
    (write-file!
     path
     (str "# Mission: M-status-fallback\n\n"
          "**Status:** OPEN (2026-05-21)\n\n"
          "This summary should be found after the status line.\n\n"))
    (let [mission (missions/parse-mission-doc (io/file path))]
      (is (= "This summary should be found after the status line."
             (:summary mission))))))

(deftest record-consumer-event-returns-normalized-payload
  (let [root (str (io/file (System/getProperty "java.io.tmpdir")
                           (str "mission-trace-" (random-uuid))))
        _ (System/setProperty "MISSION_SEARCH_LOG_ROOT" root)]
    (try
      (let [event (missions/record-consumer-event!
                   {:query-id "mission-query-test"
                    :consumer-id "test-consumer"
                    :result-id "mission/M-example@futon7"
                    :action :clicked
                    :title "Example"})]
        (is (= true (:ok event)))
        (is (= "consumer-event" (:event_type event)))
        (is (= "mission-query-test" (:query_id event)))
        (is (= "test-consumer" (:consumer_id event)))
        (is (= "mission/M-example@futon7" (:result_id event)))
        (is (= "clicked" (:action event))))
      (finally
        (System/clearProperty "MISSION_SEARCH_LOG_ROOT")))))

(deftest t8-fusion-downweights-v1-only-cosine-artifact
  ;; T-8 (2026-05-21): the "a sorry enterprise" empirical case from M-pattern-
  ;; mining's verdict. v1.1-only result with structural-rank=1 should beat
  ;; a both-ranked but-mostly-cosine result that v1 ranks #1 and v1.1 ranks #4.
  (let [root (str (io/file (System/getProperty "java.io.tmpdir")
                           (str "mission-t8-" (random-uuid))))
        ;; "M-target" — the obvious literal-title match (v1 misses; v1.1 #1)
        target {:id "mission/M-target@futon5a" :basename "M-target"
                :title "A Sorry Enterprise" :type "mission"
                :path "/tmp/M-target.md" :source "/tmp/M-target.md"
                :home_repo "futon5a" :status "IDENTIFY" :owner "Joe"
                :phase "identify" :phase_rank 1 :date "2026-05-21"
                :summary "x" :text "x" :cross_refs [] :code_paths []}
        ;; "M-artifact" — semantically-adjacent but wrong (v1 #1; v1.1 ranks lower)
        artifact (assoc target :id "mission/M-artifact@futon3"
                        :basename "M-artifact" :title "Proxy Metric Inventory"
                        :path "/tmp/M-artifact.md" :source "/tmp/M-artifact.md"
                        :home_repo "futon3")]
    (System/setProperty "MISSION_SEARCH_LOG_ROOT" root)
    (try
      (with-redefs-fn {#'missions/load-missions (fn [_] [target artifact])
                       #'missions/structural-search
                       (fn [_ _ _] [(assoc target :structural_score 5.0)
                                    (assoc artifact :structural_score 1.0)])
                       #'missions/embedding-results
                       (fn [_ _] {:missions [{:id "mission/M-artifact@futon3"
                                              :rank 1 :score 0.6}]
                                  :patterns []})}
        #(let [result (missions/search-missions "a sorry enterprise" {:top-k 2})
               results (:results result)]
           (is (= "mission/M-target@futon5a" (:id (first results)))
               "v1.1-only structural-rank=1 should beat v1-only cosine-rank=1")
           (is (nil? (:v1_rank (first results)))
               "Target has no embedding rank (cosine missed it)")
           (is (= 1 (:v1_1_rank (first results)))
               "Target has structural rank 1")))
      (finally
        (System/clearProperty "MISSION_SEARCH_LOG_ROOT")))))

(deftest search-missions-emits-divergence-trace-when-ranks-disagree
  (let [root (str (io/file (System/getProperty "java.io.tmpdir")
                           (str "mission-divergence-" (random-uuid))))
        trace-file (io/file root "mission-search-trace.edn")
        mission-a {:id "mission/M-a@futon7"
                   :basename "M-a"
                   :title "Mission A"
                   :type "mission"
                   :path "/tmp/M-a.md"
                   :source "/tmp/M-a.md"
                   :home_repo "futon7"
                   :status "IDENTIFY"
                   :owner "Joe"
                   :phase "identify"
                   :phase_rank 1
                   :date "2026-05-21"
                   :summary "alpha"
                   :text "alpha"
                   :cross_refs []
                   :code_paths []}
        mission-b (assoc mission-a
                         :id "mission/M-b@futon7"
                         :basename "M-b"
                         :title "Mission B"
                         :path "/tmp/M-b.md"
                         :source "/tmp/M-b.md"
                         :summary "beta"
                         :text "beta")]
    (System/setProperty "MISSION_SEARCH_LOG_ROOT" root)
    (try
      (with-redefs-fn {#'missions/load-missions (fn [_] [mission-a mission-b])
                       #'missions/structural-search (fn [_ _ _]
                                                      [(assoc mission-a :structural_score 5.0)
                                                       (assoc mission-b :structural_score 4.0)])
                       #'missions/embedding-results (fn [_ _]
                                                      {:missions [{:id "mission/M-b@futon7" :rank 1 :score 0.9}
                                                                  {:id "mission/M-a@futon7" :rank 2 :score 0.8}]
                                                       :patterns []})}
        #(do
           (missions/search-missions "divergent query" {:top-k 2})
           (let [trace (slurp trace-file)]
             (is (str/includes? trace ":event/type :mission-search/divergence"))
             (is (str/includes? trace ":result/id \"mission/M-a@futon7\""))
             (is (str/includes? trace ":result/id \"mission/M-b@futon7\""))
             (is (str/includes? trace ":v1.1/rank 1"))
             (is (str/includes? trace ":v1.1/rank 2")))))
      (finally
        (System/clearProperty "MISSION_SEARCH_LOG_ROOT")))))
