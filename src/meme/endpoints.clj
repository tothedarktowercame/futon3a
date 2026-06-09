(ns meme.endpoints
  "Endpoint extraction for meme arrows.

   Contract C requires missing-head signals to resolve by canonical AIF
   head id, not by synthetic sorry ids. A missing head always means:
   have = the head's local typed computation
   want = the same head as a WM-readable surface."
  (:require [clojure.edn :as edn]
            [clojure.string :as str]))

(def fallback-head-ids
  #{"mission-aif-head"
    "wm-aif-head"
    "metabolic-balance"
    "self-watch"
    "commit-hygiene"})

(def ^:private scan-timeout-ms 20000)

(defn- id-name [x]
  (cond
    (keyword? x) (name x)
    (string? x) x
    (some? x) (str x)
    :else nil))

(defn missing-head-signal? [signal]
  (and (map? signal)
       (#{"missing-head" :missing-head} (:type signal))))

(defn naive-head-id
  "Regex-only baseline retained for the H2 worked-example contrast.

   This handles the miner `aif-head-missing-<id>` shape but leaves legacy
   `<id>-not-served` ids divergent."
  [signal]
  (cond
    (missing-head-signal? signal) (id-name (:id signal))
    (map? signal) (id-name (:id signal))
    (keyword? signal) (str/replace (name signal) #"^aif-head-missing-" "")
    :else (id-name signal)))

(defn- canonical-candidates [head-ids raw]
  (->> head-ids
       (map id-name)
       (remove str/blank?)
       distinct
       (filter #(or (= raw %) (str/includes? raw %)))
       (sort-by (juxt (comp - count) identity))
       vec))

(defn canonicalize-head-id
  "Resolve a signal or minted sorry id to a canonical AIF head id.

   The registry is a collection of real head ids from scan-aif-heads. The
   source id may be any known convention, e.g. a WM priority map,
   :sorry/aif-head-missing-<head>, or :sorry/<head>-not-served."
  ([signal]
   (canonicalize-head-id signal fallback-head-ids))
  ([signal head-ids]
   (let [raw (naive-head-id signal)
         candidates (canonical-candidates head-ids raw)]
     (cond
       (str/blank? raw)
       (throw (ex-info "missing head id" {:signal signal}))

       (= 1 (count candidates))
       (first candidates)

       (< 1 (count candidates))
       (first candidates)

       :else
       (throw (ex-info "head id not found in registry"
                       {:signal signal
                        :raw raw
                        :known-head-count (count head-ids)}))))))

(defn extract-endpoints
  "Extract {:have ... :want ...} from a missing-head signal or sorry id."
  ([signal]
   (extract-endpoints signal fallback-head-ids))
  ([signal head-ids]
   (let [head-id (canonicalize-head-id signal head-ids)]
     {:have (str "aif-head/" head-id "/local")
      :want (str "aif-head/" head-id "/wm-readable")})))

(defn endpoints-via-naive [signal]
  (let [head-id (naive-head-id signal)]
    {:have (str "aif-head/" head-id "/local")
     :want (str "aif-head/" head-id "/wm-readable")}))

(defn- parse-last-edn-map [s]
  (some (fn [line]
          (try
            (let [v (edn/read-string line)]
              (when (map? v) v))
            (catch Throwable _ nil)))
        (reverse (str/split-lines (or s "")))))

(defn- scan-heads-via-futon2 []
  (let [form "(do (require 'futon2.report.war-machine) (let [s (futon2.report.war-machine/scan-aif-heads)] (prn {:head-ids (mapv (comp name :head-id) (concat (:heads s) (:missing s))) :head-count (:head-count s) :missing-count (:missing-count s)})))"
        pb (doto (ProcessBuilder. ["clojure" "-M" "-e" form])
             (.directory (java.io.File. "/home/joe/code/futon2"))
             (.redirectErrorStream true))
        proc (.start pb)
        finished? (.waitFor proc scan-timeout-ms java.util.concurrent.TimeUnit/MILLISECONDS)]
    (if finished?
      (let [out (slurp (.getInputStream proc))]
        (when (zero? (.exitValue proc))
          (parse-last-edn-map out)))
      (do
        (.destroyForcibly proc)
        nil))))

(defn- registry-from-scan [scan-result]
  (let [heads (or (:head-ids scan-result)
                  (map :head-id (concat (:heads scan-result) (:missing scan-result))))]
    (->> heads
         (map id-name)
         (remove str/blank?)
         set)))

(defn head-registry
  "Return {:head-ids #{...} :source ...} for canonicalisation.

   The primary path is the live futon2 scan-aif-heads reader. If it is
   unavailable from this repo's classpath/runtime, fall back to the small
   documented registry used by the verified EP spike."
  []
  (try
    (if-let [scan-result (scan-heads-via-futon2)]
      (let [head-ids (registry-from-scan scan-result)]
        (if (seq head-ids)
          {:head-ids head-ids
           :source :scan-aif-heads
           :scan-summary (select-keys scan-result [:head-count :missing-count])}
          {:head-ids fallback-head-ids
           :source :fallback-documented
           :reason :empty-scan}))
      {:head-ids fallback-head-ids
       :source :fallback-documented
       :reason :scan-unavailable})
    (catch Throwable t
      {:head-ids fallback-head-ids
       :source :fallback-documented
       :reason (.getMessage t)})))
