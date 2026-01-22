(ns sidecar.inspect
  (:require [clojure.data.json :as json]
            [clojure.string :as str]
            [sidecar.store :as store]))

(defn- usage []
  (str "usage: sidecar-inspect [--audit PATH] [--json] <command> [args]\n"
       "commands:\n"
       "  timeline <id>        show success + failure timeline for id\n"
       "  failures <id>        show failure reasons for id\n"
       "  failure-types <id>   group failure reasons by audit type\n"
       "  failures-by-type <id> same as failure-types\n"
       "  latest <fact-id>     show latest active fact state\n"
       "  latest-event <fact-id> show latest active fact event (fact/warrant)\n"
       "  latest-state <fact-id> show latest active fact state\n"
       "  active               list active fact ids\n"))

(defn- exit! [code msg]
  (binding [*out* *err*]
    (when msg
      (println msg)))
  (System/exit code))

(defn- render-output [data format]
  (case format
    :json (println (json/write-str data))
    (prn data)))

(defn- parse-args [args]
  (loop [opts {:audit (store/default-audit-path)
               :format :edn}
         remaining args]
    (if (empty? remaining)
      opts
      (case (first remaining)
        "--audit" (recur (assoc opts :audit (second remaining)) (nnext remaining))
        "--json" (recur (assoc opts :format :json) (next remaining))
        "--help" (recur (assoc opts :help true) (next remaining))
        (recur (assoc opts :cmd (first remaining) :args (vec (rest remaining))) [])))))

(defn- require-id [args]
  (if-let [value (first args)]
    value
    (exit! 2 (usage))))

(defn -main [& args]
  (let [{:keys [audit cmd args format help]} (parse-args args)]
    (when help
      (exit! 0 (usage)))
    (when-not (and cmd (not (str/blank? cmd)))
      (exit! 2 (usage)))
    (let [state (store/load-store-from-audit-log audit)]
      (case cmd
        "timeline" (render-output (store/event-timeline state (require-id args)) format)
        "failures" (render-output (store/failure-reasons state (require-id args)) format)
        "failure-types" (render-output (store/failure-reasons-by-type state (require-id args)) format)
        "failures-by-type" (render-output (store/failure-reasons-by-type state (require-id args)) format)
        "latest" (render-output (store/latest-active-state state (require-id args)) format)
        "latest-event" (render-output (store/latest-active-fact state (require-id args)) format)
        "latest-state" (render-output (store/latest-active-state state (require-id args)) format)
        "active" (render-output (store/active-fact-ids state) format)
        (exit! 2 (usage))))))
