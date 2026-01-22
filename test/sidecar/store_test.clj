(ns sidecar.store-test
  (:require [clojure.test :refer [deftest is testing]]
            [sidecar.store :as store]))

(defn- instant [value]
  (java.time.Instant/parse value))

(deftest duplicate-proposal-is-audited
  (let [state (store/new-store)
        proposal {:proposal/id "p-1"
                  :proposal/kind :claim
                  :proposal/status :pending
                  :proposal/score 0.42
                  :proposal/method "ann"
                  :proposal/evidence []
                  :proposal/created-at (instant "2024-01-01T00:00:00Z")}
        first-result (store/record-proposal! state proposal)
        second-result (store/record-proposal! state proposal)
        failures (store/failure-reasons state "p-1")
        timeline (store/event-timeline state "p-1")]
    (is (:ok first-result))
    (is (false? (:ok second-result)))
    (is (some #(= :append-only-violation (:audit/type %)) failures))
    (is (= #{:success :failure} (set (map :kind timeline))))))

(deftest missing-proposal-blocks-promotion
  (let [state (store/new-store)
        promotion {:promotion/id "pr-1"
                   :proposal/id "missing"
                   :promotion/kind :claim
                   :promotion/decided-by "reviewer"
                   :promotion/rationale "missing proposal"
                   :promotion/created-at (instant "2024-01-02T00:00:00Z")}
        result (store/record-promotion! state promotion)
        failures (store/failure-reasons state "pr-1")]
    (is (false? (:ok result)))
    (is (some #(= :boundary-violation (:audit/type %)) failures))))

(deftest timeline-links-related-records
  (let [state (store/new-store)
        proposal {:proposal/id "p-3"
                  :proposal/kind :claim
                  :proposal/status :pending
                  :proposal/score 0.77
                  :proposal/method "ann"
                  :proposal/evidence []
                  :proposal/created-at (instant "2024-02-01T00:00:00Z")}
        promotion {:promotion/id "pr-3"
                   :proposal/id "p-3"
                   :promotion/kind :claim
                   :promotion/decided-by "reviewer"
                   :promotion/rationale "ok"
                   :promotion/created-at (instant "2024-02-01T01:00:00Z")}
        fact {:fact/id "f-3"
              :fact/kind :claim
              :fact/body {:claim "x"}
              :fact/event-id "fe-3"
              :fact/event-type :fact
              :fact/actor "reviewer"
              :fact/rationale "promoted"
              :fact/created-at (instant "2024-02-01T02:00:00Z")}
        _ (store/record-proposal! state proposal)
        _ (store/record-promotion! state promotion)
        _ (store/record-fact! state fact {:promotion/id "pr-3"})
        timeline (store/event-timeline state "p-3")
        event-types (set (map :event/type timeline))]
    (is (contains? event-types :proposal/recorded))
    (is (contains? event-types :promotion/recorded))
    (is (contains? event-types :fact/materialized))))

(deftest chain-softness-scoring
  (let [state (store/new-store)
        proposal {:proposal/id "p-2"
                  :proposal/kind :claim
                  :proposal/status :pending
                  :proposal/score 0.12
                  :proposal/method "ann"
                  :proposal/evidence []
                  :proposal/created-at (instant "2024-01-03T00:00:00Z")}
        bridge-proposal {:proposal/id "p-bridge"
                         :proposal/kind :bridge-triple
                         :proposal/status :pending
                         :proposal/score 0.9
                         :proposal/method "curated"
                         :proposal/evidence []
                         :proposal/created-at (instant "2024-01-03T00:10:00Z")}
        bridge-promotion {:promotion/id "pr-bridge"
                          :proposal/id "p-bridge"
                          :promotion/kind :bridge-triple
                          :promotion/decided-by "reviewer"
                          :promotion/rationale "ok"
                          :promotion/created-at (instant "2024-01-03T01:00:00Z")}
        bridge {:bridge/id "b-1"
                :bridge/created-at (instant "2024-01-03T02:00:00Z")}
        _ (store/record-proposal! state proposal)
        _ (store/record-proposal! state bridge-proposal)
        _ (store/record-promotion! state bridge-promotion)
        _ (store/record-bridge-triple! state bridge {:promotion/id "pr-bridge"
                                                     :fact/actor "reviewer"
                                                     :fact/rationale "curated"})
        result (store/build-chain! state
                                   {:chain/id "c-1"
                                    :chain/steps [{:step/type :arrow :step/id "a-1"}
                                                  {:step/type :bridge :step/id "b-1"}
                                                  {:step/type :proposal :step/id "p-2"}]})
        scoring (:scoring result)
        stored (get-in @state [:chains "c-1"])
        timeline (store/event-timeline state "p-2")
        event-types (set (map :event/type timeline))
        step-evidence (->> (:score/steps scoring)
                           (map :step/evidence)
                           (map #(select-keys % [:proposal/id :bridge/id :arrow/id]))
                           set)]
    (is (:ok result))
    (is (= 6.0 (:score/base scoring)))
    (is (= 1.5 (:softness/total scoring)))
    (is (= 0.5 (:softness/average scoring)))
    (is (= 6.0 (:score/base stored)))
    (is (= 1.5 (:softness/total stored)))
    (is (= 0.5 (:softness/average stored)))
    (is (contains? step-evidence {:proposal/id "p-2"}))
    (is (contains? step-evidence {:arrow/id "a-1"}))
    (is (contains? step-evidence {:bridge/id "b-1"}))
    (is (contains? event-types :chain/built))))

(deftest lifecycle-events-derive-active-state
  (let [state (store/new-store)
        proposal {:proposal/id "p-4"
                  :proposal/kind :claim
                  :proposal/status :pending
                  :proposal/score 0.55
                  :proposal/method "ann"
                  :proposal/evidence []
                  :proposal/created-at (instant "2024-03-01T00:00:00Z")}
        promotion {:promotion/id "pr-4"
                   :proposal/id "p-4"
                   :promotion/kind :claim
                   :promotion/decided-by "reviewer"
                   :promotion/rationale "ok"
                   :promotion/created-at (instant "2024-03-01T01:00:00Z")}
        fact {:fact/id "f-4"
              :fact/kind :claim
              :fact/body {:claim "y"}
              :fact/event-id "fe-4"
              :fact/event-type :fact
              :fact/actor "reviewer"
              :fact/rationale "promoted"
              :fact/created-at (instant "2024-03-01T02:00:00Z")}
        warrant {:fact/id "f-4"
                 :fact/kind :claim
                 :fact/body {:claim "y"}
                 :fact/event-id "fe-5"
                 :fact/event-type :warrant
                 :fact/actor "auditor"
                 :fact/rationale "reinforced"
                 :fact/created-at (instant "2024-03-01T03:00:00Z")}
        retired {:fact/id "f-4"
                 :fact/kind :claim
                 :fact/event-id "fe-6"
                 :fact/event-type :retired
                 :fact/actor "auditor"
                 :fact/rationale "superseded"
                 :fact/created-at (instant "2024-03-01T04:00:00Z")}]
    (store/record-proposal! state proposal)
    (store/record-promotion! state promotion)
    (is (:ok (store/record-fact! state fact {:promotion/id "pr-4"})))
    (is (= :fact (:fact/event-type (store/latest-active-fact state "f-4"))))
    (is (= :fact (:fact/event-type (store/latest-active-state state "f-4"))))
    (is (= ["f-4"] (store/active-fact-ids state)))
    (is (:ok (store/record-fact! state warrant {:promotion/id "pr-4"})))
    (is (= :warrant (:fact/event-type (store/latest-active-fact state "f-4"))))
    (is (:ok (store/record-fact! state retired {})))
    (is (nil? (store/latest-active-fact state "f-4")))
    (is (empty? (store/active-fact-ids state)))))

(deftest failure-reasons-group-by-type
  (let [state (store/new-store)
        bad-fact {:fact/id "f-5"
                  :fact/kind :claim
                  :fact/event-id "fe-5"
                  :fact/event-type :fact
                  :fact/actor "reviewer"
                  :fact/rationale "missing promotion"
                  :fact/created-at (instant "2024-04-01T00:00:00Z")}
        result (store/record-fact! state bad-fact {})
        grouped (store/failure-reasons-by-type state "f-5")]
    (is (false? (:ok result)))
    (is (contains? grouped :boundary-violation))))

(deftest audit-log-reload-preserves-fact-linkage
  (let [log-root (str (System/getProperty "java.io.tmpdir") "/sidecar-audit-test-" (java.util.UUID/randomUUID))
        state (store/new-store)
        proposal {:proposal/id "p-6"
                  :proposal/kind :claim
                  :proposal/status :pending
                  :proposal/score 0.31
                  :proposal/method "ann"
                  :proposal/evidence []
                  :proposal/created-at (instant "2024-05-01T00:00:00Z")}
        promotion {:promotion/id "pr-6"
                   :proposal/id "p-6"
                   :promotion/kind :claim
                   :promotion/decided-by "reviewer"
                   :promotion/rationale "ok"
                   :promotion/created-at (instant "2024-05-01T01:00:00Z")}
        fact {:fact/id "f-6"
              :fact/kind :claim
              :fact/body {:claim "z"}
              :fact/event-id "fe-6"
              :fact/event-type :fact
              :fact/actor "reviewer"
              :fact/rationale "promoted"
              :fact/created-at (instant "2024-05-01T02:00:00Z")}]
    (try
      (System/setProperty "SIDECAR_LOG_ROOT" log-root)
      (store/record-proposal! state proposal)
      (store/record-promotion! state promotion)
      (store/record-fact! state fact {:promotion/id "pr-6"})
      (let [reloaded (store/load-store-from-audit-log)
            timeline (store/event-timeline reloaded "p-6")
            event-types (set (map :event/type timeline))]
        (is (contains? event-types :proposal/recorded))
        (is (contains? event-types :promotion/recorded))
        (is (contains? event-types :fact/materialized)))
      (finally
        (System/clearProperty "SIDECAR_LOG_ROOT")))))

(deftest sense-shift-gate-rejects-ungated-hop
  (let [state (store/new-store)
        result (store/build-chain! state
                                   {:chain/id "c-2"
                                    :chain/steps [{:step/type :arrow
                                                   :step/id "a-1"
                                                   :step/shift? true}]})
        failures (store/failure-reasons state "c-2")]
    (is (false? (:ok result)))
    (is (some #(= :validation-failure (:audit/type %)) failures))))

(deftest sense-shift-gate-rejects-mismatched-hop
  (let [state (store/new-store)
        result (store/build-chain! state
                                   {:chain/id "c-3"
                                    :chain/steps [{:step/type :arrow
                                                   :step/id "a-2"
                                                   :step/shift? true
                                                   :step/gate :bridge-triple}]})
        failures (store/failure-reasons state "c-3")]
    (is (false? (:ok result)))
    (is (some #(= :drift (:audit/type %)) failures))))

(deftest sense-shift-gate-allows-typed-arrow
  (let [state (store/new-store)
        result (store/build-chain! state
                                   {:chain/id "c-3"
                                    :chain/steps [{:step/type :arrow
                                                   :step/id "a-1"
                                                   :step/shift? true
                                                   :step/gate :typed-arrow}]})
        scoring (:scoring result)
        step (-> scoring :score/steps first)]
    (is (:ok result))
    (is (= 3.0 (:score/base scoring)))
    (is (= 0.5 (:score/shift-penalty scoring)))
    (is (= 0.5 (:softness/total scoring)))
    (is (= 0.5 (:softness/average scoring)))
    (is (= 0.5 (:score/shift-penalty step)))
    (is (= {:shift/required :typed-arrow
            :shift/gate :typed-arrow
            :shift/allowed? true}
           (:step/annotation step)))))
