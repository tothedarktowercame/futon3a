(ns meme.ch2-test
  (:require [clojure.java.io :as io]
            [clojure.test :refer [deftest is]]
            [meme.ch2 :as ch2]
            [meme.identity :as identity]
            [meme.schema :as schema]
            [meme.step :as step]))

(defn- temp-path [prefix suffix]
  (let [f (java.io.File/createTempFile prefix suffix)]
    (io/delete-file f true)
    (.getAbsolutePath f)))

(defn- temp-sink []
  (temp-path "ch2-discharge-events-" ".edn"))

(defn- temp-ds []
  (let [path (temp-path "ch2-meme-" ".db")
        ds (schema/datasource path)]
    (schema/ensure-db! ds)
    ds))

(defn- mint-open! [ds ep]
  (:arrow
   (identity/mint-or-unify!
    ds ep
    {:mode :untyped
     :status :open
     :rationale "CH2 test open arrow"})))

(defn- construct! [ds ep sink]
  (identity/promote!
   ds ep :constructed
   :mode :construction
   :payload {:construction "CH2 test construction"}
   :cap-ascent {:write? false}
   :ch2 {:sink sink}))

(deftest pure-step-never-emits-ch2
  (let [sink (temp-sink)
        state {:arrows {}
               :cap-overlay {}
               :reachable #{}
               :trace []}
        leaf {:move/id "scope/a->scope/b"
              :have "scope/a"
              :want "scope/b"
              :to-state :constructed}
        out (step/step state leaf)]
    (is (= :constructed (get-in out [:arrows ["scope/a" "scope/b"] :status])))
    (is (empty? (ch2/read-events sink)))))

(deftest promote-constructed-emits-well-formed-discharge-event
  (let [ds (temp-ds)
        sink (temp-sink)
        ep {:have "ch2/live/have"
            :want "ch2/live/want"}
        _ (mint-open! ds ep)
        result (construct! ds ep sink)
        events (ch2/read-events sink)
        event (first events)]
    (is (= 1 (count events)))
    (is (= "ch2/live/have->ch2/live/want" (:move/id event)))
    (is (re-matches #".+/sorry/meme-arrow-.+" (:sorry-ref event)))
    (is (true? (:discharged? event)))
    (is (true? (:ch2/discharge-event event)))
    (is (not (contains? event :peradam)))
    (is (= event (:ch2/discharge-event result)))))

(deftest rejection-witness-refuses-q-laundering
  (let [sink (temp-sink)]
    (is (false? (ch2/discharge-event? 0.42)))
    (is (false? (ch2/discharge-event? {:q 0.42})))
    (is (thrown-with-msg?
         clojure.lang.ExceptionInfo
         #"refusing to emit non-CH2 discharge event"
         (ch2/emit-discharge-event! {:q 0.42} :sink sink)))
    (is (empty? (ch2/read-events sink)))))

(deftest semantic-regression-rejects-near-misses
  (let [good (ch2/discharge-event "a->b" "futon3a/sorry/meme-arrow-abc123" "2026-06-10T00:00:00Z")]
    (is (true? (ch2/discharge-event? good)))
    (is (false? (ch2/discharge-event? (dissoc good :ch2/discharge-event))))
    (is (false? (ch2/discharge-event? (assoc good :sorry-ref "futon3a/sorry/not-a-meme-arrow"))))
    (is (false? (ch2/discharge-event? (assoc good :peradam {:minted? true}))))))

(deftest promote-can-disable-ch2-for-no-regression
  (let [ds (temp-ds)
        sink (temp-sink)
        ep {:have "ch2/disabled/have"
            :want "ch2/disabled/want"}]
    (mint-open! ds ep)
    (identity/promote!
     ds ep :constructed
     :mode :construction
     :payload {:construction "disabled CH2 test"}
     :cap-ascent {:write? false}
     :ch2 {:emit? false
           :sink sink})
    (is (empty? (ch2/read-events sink)))))
