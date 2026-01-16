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
        _ (store/record-bridge-triple! state bridge {:promotion/id "pr-bridge"})
        result (store/build-chain! state
                                   {:chain/id "c-1"
                                    :chain/steps [{:step/type :arrow :step/id "a-1"}
                                                  {:step/type :bridge :step/id "b-1"}
                                                  {:step/type :proposal :step/id "p-2"}]})
        softness (:softness result)
        stored (get-in @state [:chains "c-1"])
        timeline (store/event-timeline state "p-2")
        event-types (set (map :event/type timeline))]
    (is (:ok result))
    (is (= 1.5 (:softness/total softness)))
    (is (= 0.5 (:softness/average softness)))
    (is (= 1.5 (:softness/total stored)))
    (is (= 0.5 (:softness/average stored)))
    (is (contains? event-types :chain/built))))
