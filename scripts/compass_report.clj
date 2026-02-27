#!/usr/bin/env clj -M
;; Generate a compass report (EDN or JSON).
;;
;; Usage:
;;   clj -M -m scripts.compass-report --narrative "..." [--level N]
;;       [--top-k N] [--sim-steps N] [--seed N] [--method KW]
;;       [--out PATH] [--json]

(ns scripts.compass-report
  (:require [clojure.data.json :as json]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [futon.compass :as compass]
            [futon.compass-gfe :as compass-gfe]))

(defn- usage []
  (str "usage: compass_report --narrative TEXT [--level N] "
       "[--top-k N] [--sim-steps N] [--seed N] [--method KW] "
       "[--out PATH] [--json]\n"))

(defn- parse-int [value fallback]
  (try
    (Integer/parseInt (str value))
    (catch Throwable _ fallback)))

(defn- parse-keyword [value]
  (when (and value (not (str/blank? (str value))))
    (if (keyword? value) value (keyword value))))

(defn- parse-args [args]
  (loop [opts {:format :edn
               :level 0
               :top-k 5
               :sim-steps 10
               :seed 42}
         remaining args]
    (if (empty? remaining)
      opts
      (case (first remaining)
        "--narrative" (recur (assoc opts :narrative (second remaining)) (nnext remaining))
        "--narrative-file" (recur (assoc opts :narrative-file (second remaining)) (nnext remaining))
        "--level" (recur (assoc opts :level (parse-int (second remaining) (:level opts))) (nnext remaining))
        "--top-k" (recur (assoc opts :top-k (parse-int (second remaining) (:top-k opts))) (nnext remaining))
        "--sim-steps" (recur (assoc opts :sim-steps (parse-int (second remaining) (:sim-steps opts))) (nnext remaining))
        "--seed" (recur (assoc opts :seed (parse-int (second remaining) (:seed opts))) (nnext remaining))
        "--method" (recur (assoc opts :method (parse-keyword (second remaining))) (nnext remaining))
        "--out" (recur (assoc opts :out (second remaining)) (nnext remaining))
        "--json" (recur (assoc opts :format :json) (next remaining))
        (recur opts (next remaining))))))

(defn- read-narrative [{:keys [narrative narrative-file]}]
  (cond
    (and narrative-file (.exists (io/file narrative-file)))
    (slurp narrative-file)

    (and narrative (not (str/blank? narrative)))
    narrative

    :else nil))

(defn- render [data format]
  (case format
    :json (json/write-str data)
    (pr-str data)))

(defn -main [& args]
  (let [{:keys [level top-k sim-steps seed method out format] :as opts} (parse-args args)
        narrative (read-narrative opts)]
    (when (str/blank? narrative)
      (println (usage))
      (System/exit 2))
    (when (and (pos? (or level 0)) method)
      (binding [*out* *err*]
        (println "compass-report: --method is ignored for level>0")))
    (let [report (if (pos? (or level 0))
                   (compass-gfe/compass-report-gfe narrative
                                                  :level level
                                                  :top-k top-k
                                                  :sim-steps sim-steps
                                                  :seed seed)
                   (compass/compass-report narrative
                                           :top-k top-k
                                           :sim-steps sim-steps
                                           :seed seed
                                           :method (or method :auto)))
          out (or out (str (io/file "/tmp" (str "compass-report-" (System/currentTimeMillis) ".edn"))))
          payload (render report format)]
      (spit out payload)
      (println "Wrote:" out)
      (println payload))))

(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))
