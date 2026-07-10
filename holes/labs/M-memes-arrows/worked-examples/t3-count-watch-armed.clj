;; t3-count-watch-armed.clj — T3 exit condition: ARM the Contract-A count-watch on the LIVE store.
;;
;; H6 built+tested the tripwire on synthetic counts. T3 arms it on the canonical meme.db: seed the
;; live store idempotently (endpoint-keyed, no dups on re-run), then run the watch against it and
;; confirm it reports the real count, is silent below threshold, and still fires when breached.
;;
;; Run:  cd ~/code/futon3a && clojure -M holes/labs/M-memes-arrows/worked-examples/t3-count-watch-armed.clj

(require '[meme.writer :as writer]
         '[meme.identity :as identity]
         '[meme.count-watch :as cw])

(def live-db (cw/meme-db-path))           ; canonical /home/joe/code/futon3a/meme.db
(def ds (writer/ensure-db! live-db))

;; Idempotent seed of the live store — the three real arrows, keyed by (have,want).
(def seed-arrows
  [{:have "belief-mass-on-supports-tagged-cohort" :want "support-coverage-channel"
    :mode :construction :status :constructed
    :payload {:construction "futon2.aif.belief/predict-support-coverage"}}
   {:have "construct-an-explicit-witness" :want "reduce-to-known-result"
    :mode :analogy :status :correlated}
   {:have "coupling-density-channel-measured-structurally" :want "predict-coupling-density-from-belief-mass"
    :mode :untyped :status :open}])

(doseq [{:keys [have want] :as a} seed-arrows]
  (identity/mint-or-unify! ds {:have have :want want}
                           (-> a (dissoc :have :want) (assoc :created-by "t3-arm"))))

(println "\n=== T3 — Contract-A count-watch ARMED on the live store ===")
(println "live store:" live-db)

;; ARM: run the watch against the live store (silent below threshold).
(def armed (cw/watch! {:db-path live-db}))
(println "watch result:" armed)

;; Prove it is a real tripwire, not just silent: a synthetic breach fires.
(def fired (cw/watch {:count 10001}))
(println "synthetic breach fires:" (:flag? fired) "->" (:message fired))

(def ok? (and (= :contract-a (:contract armed))
              (false? (:flag? armed))          ; live count is well under 10^4 -> silent (armed, not firing)
              (true? (:flag? fired))))         ; but fires when breached
(println (format "\nRESULT: armed-on-live=%s live-count=%d silent-below=%s fires-when-breached=%s => T3 %s"
                 (= :contract-a (:contract armed)) (:count armed) (false? (:flag? armed)) (:flag? fired)
                 (if ok? "ARMED (PASS)" "FAIL")))
