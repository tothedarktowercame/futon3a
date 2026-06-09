;; E-wm-policy-arrow-seam worked example — advances-cap ascent routing.
;;
;; Default run is dry-run for cap-overlay writes:
;;   cd /home/joe/code/futon3a
;;   clojure -M holes/labs/M-memes-arrows/worked-examples/e-advances-cap-ascent.clj
;;
;; Operator-approved live write:
;;   clojure -M holes/labs/M-memes-arrows/worked-examples/e-advances-cap-ascent.clj --write

(require '[clojure.java.io :as io]
         '[meme.arrow :as arrow]
         '[meme.identity :as identity]
         '[meme.schema :as schema])

(def db-path "/tmp/futon3a-e-advances-cap-ascent.db")
(def write? (boolean (some #{"--write"} *command-line-args*)))

(defn- reset-db! []
  (io/delete-file db-path true)
  (let [ds (schema/datasource db-path)]
    (schema/ensure-db! ds)
    ds))

(defn- assert! [pred message data]
  (when-not pred
    (throw (ex-info message data))))

(defn- endpoint [stem]
  {:have (str "e-advances-cap/" stem "/have")
   :want (str "e-advances-cap/" stem "/want")})

(defn- mint-open! [ds ep cap-id]
  (:arrow
   (identity/mint-or-unify!
    ds ep
    (cond-> {:mode :untyped
             :status :open
             :rationale "worked example open arrow"}
      cap-id (assoc :advances-cap cap-id)))))

(defn- construct! [ds ep]
  (identity/promote!
   ds ep :constructed
   :mode :construction
   :payload {:construction "worked-example/construct"
             :dry-run-cap-write? (not write?)}
   :cap-ascent {:write? write?}))

(defn -main []
  (let [ds (reset-db!)
        ordinary-ep (endpoint "ordinary-agency")
        frontier-ep (endpoint "frontier-ai-passes-prelims")
        unknown-ep (endpoint "unknown-cap")
        absent-ep (endpoint "absent-cap")

        _ (mint-open! ds ordinary-ep "agency")
        ordinary (construct! ds ordinary-ep)

        _ (mint-open! ds frontier-ep "ai-passes-prelims")
        frontier (construct! ds frontier-ep)

        _ (mint-open! ds unknown-ep "definitely-unknown-cap")
        unknown-rejection (try
                            (construct! ds unknown-ep)
                            nil
                            (catch clojure.lang.ExceptionInfo e
                              (ex-data e)))
        unknown-row (identity/find-by-endpoint ds unknown-ep)

        repromote (identity/promote! ds ordinary-ep :constructed
                                     :cap-ascent {:write? write?})

        _ (mint-open! ds absent-ep nil)
        absent (construct! ds absent-ep)]
    (println "=== E advances-cap ascent worked example ===")
    (println "meme.db:" (.getCanonicalPath (io/file db-path)))
    (println "cap-overlay write?:" write?)
    (println "ordinary agency:" (select-keys (:cap-ascent ordinary)
                                             [:cap-id :frontier? :current-status
                                              :target-status :operation :dry-run?
                                              :applied?]))
    (println "frontier ai-passes-prelims:" (select-keys (:cap-ascent frontier)
                                                        [:cap-id :frontier? :current-status
                                                         :target-status :operation :dry-run?
                                                         :applied? :event
                                                         :intended-event]))
    (println "unknown rejection:" unknown-rejection)
    (println "re-promote:" {:op (:op repromote)
                            :cap-ascent (select-keys (:cap-ascent repromote)
                                                     [:cap-id :operation :applied?])})
    (println "absent advances-cap:" {:op (:op absent)
                                     :cap-ascent (:cap-ascent absent)})

    ;; (a) ordinary cap routes to satisfied. On the live overlay :agency is
    ;; already satisfied, so an idempotent no-op is the correct write result.
    (assert! (= "agency" (get-in ordinary [:cap-ascent :cap-id]))
             "ordinary cap did not route agency"
             ordinary)
    (assert! (false? (get-in ordinary [:cap-ascent :frontier?]))
             "agency must be ordinary, not frontier"
             ordinary)
    (assert! (= :satisfied (get-in ordinary [:cap-ascent :target-status]))
             "ordinary cap must target :satisfied"
             ordinary)

    ;; (b) frontier cap routes to claimed/proposed-flip and never satisfied.
    (assert! (= "ai-passes-prelims" (get-in frontier [:cap-ascent :cap-id]))
             "frontier cap did not route ai-passes-prelims"
             frontier)
    (assert! (true? (get-in frontier [:cap-ascent :frontier?]))
             "ai-passes-prelims must be frontier"
             frontier)
    (assert! (= :claimed (get-in frontier [:cap-ascent :target-status]))
             "frontier cap must target :claimed"
             frontier)
    (assert! (not= :satisfied (get-in frontier [:cap-ascent :target-status]))
             "frontier cap must never auto-satisfy"
             frontier)
    (assert! (some? (get-in frontier [:cap-ascent :event]))
             "frontier route must emit proposed-flip event"
             frontier)

    ;; (c) unknown capability rejects loudly before the local arrow moves.
    (assert! (= :capability/unknown (:reason unknown-rejection))
             "unknown cap-id did not reject loudly"
             {:unknown-rejection unknown-rejection})
    (assert! (= :open (:status unknown-row))
             "unknown cap rejection must not construct the arrow"
             {:unknown-row (arrow/get-arrow ds (:id unknown-row))})

    ;; (d) re-promote is an endpoint+cap no-op.
    (assert! (= :noop (get-in repromote [:op :op]))
             "re-promote should be an idempotent no-op"
             repromote)

    ;; (e) absent advances-cap constructs without cap-ascent side-effect.
    (assert! (= :constructed (get-in absent [:arrow :status]))
             "absent advances-cap arrow did not construct"
             absent)
    (assert! (nil? (:cap-ascent absent))
             "absent advances-cap should not touch cap overlay"
             absent)

    (println (format (str "PASS ordinary-target=%s frontier-target=%s unknown-rejected=%s "
                          "repromote=%s absent-cap-touch=%s write=%s")
                     (get-in ordinary [:cap-ascent :target-status])
                     (get-in frontier [:cap-ascent :target-status])
                     (= :capability/unknown (:reason unknown-rejection))
                     (get-in repromote [:op :op])
                     (some? (:cap-ascent absent))
                     write?))))

(try
  (-main)
  (shutdown-agents)
  (catch Throwable t
    (binding [*out* *err*]
      (println "=== E advances-cap ascent worked example ===")
      (println "FAIL" (.getMessage t))
      (when (ex-data t)
        (prn (ex-data t))))
    (shutdown-agents)
    (System/exit 1)))
