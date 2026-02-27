#!/usr/bin/env clj -M
;; Record compass-derived proposals into the sidecar audit log.
;;
;; Usage:
;;   clj -M -m scripts.compass-sidecar --report PATH [--limit N]
;;       [--method METHOD] [--kind KW] [--status KW] [--json]

(ns scripts.compass-sidecar
  (:require [clojure.data.json :as json]
            [clojure.edn :as edn]
            [clojure.string :as str]
            [sidecar.store :as store]))

(defn- usage []
  (str "usage: compass_sidecar --report PATH [--limit N] "
       "[--method METHOD] [--kind KW] [--status KW] [--json]\n"))

(defn- parse-int [value fallback]
  (try
    (Integer/parseInt (str value))
    (catch Throwable _ fallback)))

(defn- parse-keyword [value]
  (when (and value (not (str/blank? (str value))))
    (if (keyword? value) value (keyword value))))

(defn- parse-args [args]
  (loop [opts {:format :edn
               :limit nil
               :method "compass"
               :kind :pattern
               :status :pending}
         remaining args]
    (if (empty? remaining)
      opts
      (case (first remaining)
        "--report" (recur (assoc opts :report (second remaining)) (nnext remaining))
        "--limit" (recur (assoc opts :limit (parse-int (second remaining) (:limit opts))) (nnext remaining))
        "--method" (recur (assoc opts :method (second remaining)) (nnext remaining))
        "--kind" (recur (assoc opts :kind (parse-keyword (second remaining))) (nnext remaining))
        "--status" (recur (assoc opts :status (parse-keyword (second remaining))) (nnext remaining))
        "--json" (recur (assoc opts :format :json) (next remaining))
        (recur opts (next remaining))))))

(defn- load-report [path]
  (edn/read-string (slurp path)))

(defn- candidate-id [entry]
  (or (:id entry)
      (:pattern/id entry)
      (:pattern entry)
      (:target-id entry)))

(defn- clamp-01 [v]
  (cond
    (not (number? v)) 0.5
    (< (double v) 0.0) 0.0
    (> (double v) 1.0) 1.0
    :else (double v)))

(defn- candidate-score [entry]
  (clamp-01 (or (:score entry)
                (:match-score entry)
                (:confidence entry)
                0.5)))

(defn- extract-patterns [report]
  (or (get-in report [:observations :patterns])
      (:patterns-retrieved report)
      (:patterns report)
      []))

(defn- proposal-id [idx]
  (str "compass-prop-" idx "-" (subs (str (java.util.UUID/randomUUID)) 0 8)))

(defn- build-proposal [entry idx opts report-path]
  (let [pid (candidate-id entry)]
    {:proposal/id (proposal-id idx)
     :proposal/kind (:kind opts)
     :proposal/status (:status opts)
     :proposal/score (candidate-score entry)
     :proposal/method (:method opts)
     :proposal/evidence [{:source :compass
                          :report report-path
                          :pattern/id pid
                          :entry (select-keys entry [:id :name :score :match-score :confidence])}]
     :proposal/target-id pid}))

(defn- render [data format]
  (case format
    :json (println (json/write-str data))
    (prn data)))

(defn -main [& args]
  (let [{:keys [report limit format] :as opts} (parse-args args)]
    (when (str/blank? report)
      (println (usage))
      (System/exit 2))
    (let [report-path report
          report (load-report report-path)
          patterns (extract-patterns report)
          patterns (if (and (number? limit) (pos? (int limit)))
                     (take limit patterns)
                     patterns)
          store (store/load-store-from-audit-log)
          results (mapv (fn [entry idx]
                          (let [proposal (build-proposal entry idx opts report-path)]
                            (store/record-proposal! store proposal)))
                        patterns
                        (range 1 (inc (count patterns))))]
      (render {:ok true
               :report report-path
               :proposals results} format))))

(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))
