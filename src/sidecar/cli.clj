(ns sidecar.cli
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [sidecar.store :as store]))

(defn- usage []
  (str "usage: sidecar audit [--log PATH] <command> [args]\n"
       "commands:\n"
       "  timeline <id>            show timeline entries for id\n"
       "  failures <id>            show failure entries for id\n"
       "  failures-by-type <id>    group failure entries for id\n"
       "  latest <fact-id>         show latest active fact state\n"
       "  active-ids               list active fact ids\n"
       "  audit-log                print raw audit log entries\n"
       "env: SIDECAR_LOG_ROOT (default log/)\n"))

(defn- exit! [code msg]
  (binding [*out* *err*]
    (when msg
      (println msg)))
  (System/exit code))

(defn- log-root []
  (or (some-> (System/getenv "SIDECAR_LOG_ROOT") str/trim not-empty)
      "log"))

(defn- audit-path []
  (io/file (log-root) "sidecar-audit.edn"))

(defn- read-audit-entries [path]
  (let [file (io/file path)]
    (if (.exists file)
      (with-open [reader (io/reader file)]
        (->> (line-seq reader)
             (remove str/blank?)
             (mapv (fn [line]
                     (try
                       (edn/read-string {:readers *data-readers*} line)
                       (catch Throwable _
                         {:audit/type :parse-error
                          :errors [{:field :line :type :invalid :msg "parse error"}]
                          :line line}))))))
      [])))

(defn- load-store [path]
  (let [state (store/new-store)
        entries (read-audit-entries path)]
    (swap! state assoc :audit entries)
    (doseq [entry entries]
      (when (and (= :success (:audit/type entry)) (:event entry))
        (store/apply-event! state (:event entry))))
    state))

(defn- parse-args [args]
  (loop [opts {:log (str (audit-path))
               :args []}
         remaining args]
    (if (empty? remaining)
      opts
      (case (first remaining)
        "--log" (recur (assoc opts :log (second remaining)) (nnext remaining))
        "--help" (exit! 0 (usage))
        (recur (update opts :args conj (first remaining)) (next remaining))))))

(defn- render [data]
  (prn data))

(defn- handle-audit [args]
  (let [{:keys [log args]} (parse-args args)
        command (first args)
        rest-args (rest args)
        state (load-store log)]
    (case command
      "timeline" (if-let [entity-id (first rest-args)]
                   (render (store/event-timeline state entity-id))
                   (exit! 2 (usage)))
      "failures" (if-let [entity-id (first rest-args)]
                   (render (store/failure-reasons state entity-id))
                   (exit! 2 (usage)))
      "failures-by-type" (if-let [entity-id (first rest-args)]
                             (render (store/failure-reasons-by-type state entity-id))
                             (exit! 2 (usage)))
      "latest" (if-let [fact-id (first rest-args)]
                 (render (store/latest-active-state state fact-id))
                 (exit! 2 (usage)))
      "active-ids" (render (store/active-fact-ids state))
      "audit-log" (render (store/audit-log state))
      (exit! 2 (usage)))))

(defn -main [& args]
  (case (first args)
    "audit" (handle-audit (rest args))
    (exit! 2 (usage))))
