#!/usr/bin/env bb
;; Standalone test of meme.gates (loaded from src via --classpath) against the real
;; E-fold-engine artifacts. Run:  cd futon3a && bb --classpath src holes/labs/M-memes-arrows/gates_test.clj
(require '[meme.gates :as g]
         '[clojure.edn :as edn]
         '[clojure.string :as str]
         '[cheshire.core :as json])

(def LAB "/home/joe/code/futon3a/holes/labs/M-memes-arrows")
(defn redn [f] (edn/read-string (slurp (str LAB "/" f))))
(def sorry (redn "E-fold-engine-sorry.edn"))
(def wiring (redn "E-fold-engine-wiring.edn"))
(def cascade (->> (json/parse-string (slurp (str LAB "/E-fold-engine-stage1.json")) true)
                  :cascade (map first) set))
(def eps (:endpoints sorry))
(def sig (:want-signature sorry))

(def fails (atom 0))
(defn t [nm p] (if p (println "  PASS" nm) (do (swap! fails inc) (println "  FAIL" nm))))

(println "=== meme.gates (live source) tests ===")
;; GROUNDING
(t "grounded? ok (claims trusted)"                 (:ok (g/grounded? eps (constantly true))))
(t "grounded? fails on a false cascade_construct claim"
   (false? (:ok (g/grounded? eps (fn [ep] (not (str/includes? (:ref ep) "cascade_construct")))))))
(t "grounded? fails when no have grounded"         (false? (:ok (g/grounded? [{:role :want :in-map true :ref "x"}] (constantly true)))))
;; TERMINALS-MATCH
(t "terminals-match? ok on real wiring"            (:ok (g/terminals-match? sig wiring)))
(t "terminals-match? fails on wrong in-port"       (false? (:ok (g/terminals-match? sig (assoc-in wiring [:terminals :in] [{:port "NOT-cascade-dict"}])))))
;; CASCADE-WARRANT
(t "cascade-warrant ok on real wiring"             (:ok (g/cascade-warrant-ok? cascade wiring)))
(t "cascade-warrant fails on rogue box"            (false? (:ok (g/cascade-warrant-ok? cascade (update wiring :boxes conj {:id :rogue :warrant "fake-ns/not-in-cascade"})))))
;; gate!
(t "gate! throws on not-ok"                        (try (g/gate! "X" {:ok false :reason :y} {}) false (catch Exception _ true)))
(t "gate! returns result on ok"                    (= {:ok true} (g/gate! "X" {:ok true} {})))

(println (if (zero? @fails) "\nALL GATES TESTS PASS" (str "\n" @fails " FAILED")))
(when (pos? @fails) (System/exit 1))
