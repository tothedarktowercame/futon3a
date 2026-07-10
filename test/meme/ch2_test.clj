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

(deftest negative-fold-event-round-trips
  ;; the beta term (closure-folds.edn recording discipline): failed folds are
  ;; representable, emittable, and readable — with attempt evidence required.
  (let [sink (temp-sink)
        neg (ch2/fold-event "hypergraph-operator/argue->closed"
                            "futon3a/sorry/meme-arrow-def456"
                            "2026-07-10T00:00:00Z"
                            false
                            :used ["math-formalization/continuous-linear-map-composition"]
                            :note "cosine artifact on operator; did not fold")]
    (is (true? (ch2/fold-event? neg)))
    (is (false? (ch2/discharge-event? neg)))          ; a negative is NOT a discharge
    (ch2/emit-fold-event! neg :sink sink)
    (let [[event] (ch2/read-events sink)]
      (is (= neg event))
      (is (false? (:discharged? event)))
      (is (= ["math-formalization/continuous-linear-map-composition"] (:used event))))))

(deftest negative-fold-event-requires-attempt-evidence
  (let [bare (ch2/fold-event "a->b" "futon3a/sorry/meme-arrow-abc123"
                             "2026-07-10T00:00:00Z" false)]
    (is (false? (ch2/fold-event? bare)))              ; no :used, no :note
    (is (thrown-with-msg?
         clojure.lang.ExceptionInfo
         #"refusing to emit non-CH2 fold event"
         (ch2/emit-fold-event! bare :sink (temp-sink))))
    (is (true? (ch2/fold-event? (assoc bare :note "attempt evidence"))))
    (is (true? (ch2/fold-event? (assoc bare :used ["ns/some-pattern"]))))
    (is (false? (ch2/fold-event? (assoc bare :used []))))          ; empty vec ≠ evidence
    (is (false? (ch2/fold-event? (assoc bare :note "x" :discharged? "yes"))))))

(deftest strict-positive-emitter-still-refuses-negatives
  ;; back-compat: emit-discharge-event! is the positive-only path
  (let [sink (temp-sink)
        neg (ch2/fold-event "a->b" "futon3a/sorry/meme-arrow-abc123"
                            "2026-07-10T00:00:00Z" false :note "failed fold")]
    (is (thrown-with-msg?
         clojure.lang.ExceptionInfo
         #"refusing to emit non-CH2 discharge event"
         (ch2/emit-discharge-event! neg :sink sink)))
    (is (empty? (ch2/read-events sink)))))

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

(deftest promote-without-payload-surfaces-skip-not-silence
  ;; constructed-without-construction: the anti-laundering gate refuses it (no :payload
  ;; = no construction evidence), and the skip is now VISIBLE in the result instead of
  ;; silently swallowed. (fable-1 / E-mission-head §8.5 — three real discharges went
  ;; invisible to the value channel before this.)
  (let [ds (temp-ds)
        sink (temp-sink)
        ep {:have "ch2/nopayload/have"
            :want "ch2/nopayload/want"}
        _ (mint-open! ds ep)
        result (identity/promote!
                ds ep :constructed
                :mode :construction               ; deliberately NO :payload
                :cap-ascent {:write? false}
                :ch2 {:sink sink})]
    (is (nil? (:ch2/discharge-event result)))                                  ; no event
    (is (= :constructed-without-construction (get-in result [:ch2/emit-skipped :reason]))) ; visible skip
    (is (= "ch2/nopayload/have->ch2/nopayload/want" (get-in result [:ch2/emit-skipped :move/id])))
    (is (empty? (ch2/read-events sink)))))                                     ; nothing laundered
