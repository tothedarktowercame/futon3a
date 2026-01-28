(ns musn.portal
  (:require [cemerick.drawbridge.client :as drawbridge]
            [clojure.data.json :as json]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.tools.nrepl :as nrepl]
            [sidecar.store :as sidecar.store]))

(defn- usage []
  (str "usage: portal [--url URL] [--token TOKEN] <clj-code>\n"
       "   or: portal patterns <list|search|get> [args]\n"
       "   or: portal suggest <query> [--limit N] [--namespace NS] [--json]\n"
       "   or: portal propose <proposal-id> [--kind KW] [--target ID]\n"
       "                         [--score N] [--method METHOD] [--status KW]\n"
       "                         [--evidence EDN] [--json]\n"
       "   or: portal promote <promotion-id> <proposal-id> --kind KW\n"
       "                         --decided-by NAME --rationale TEXT [--json]\n"
       "   or: portal action <action-id> --type KW [--note TEXT] [--json]\n"
       "   or: portal evidence <evidence-id> --target-type KW --target-id ID\n"
       "                         --method METHOD [--payload EDN] [--json]\n"
       "env: ADMIN_TOKEN or .admintoken, PORTAL_URL (default http://127.0.0.1:6767/repl)\n"
       "env: PORTAL_FUTON1_URL or MUSN_FUTON1_URL (default http://localhost:8080/api/alpha)\n"
       "env: SIDECAR_LOG_ROOT (default log/)\n"))

(defn- patterns-usage []
  (str "usage: portal patterns list [--limit N] [--namespace NS] [--json]\n"
       "   or: portal patterns search <query> [--limit N] [--json]\n"
       "   or: portal patterns get <pattern-id> [--json]\n"))

(defn- read-token []
  (or (some-> (System/getenv "ADMIN_TOKEN") str/trim not-empty)
      (let [f (io/file ".admintoken")]
        (when (.exists f)
          (some-> (slurp f) str/trim not-empty)))))

(defn- parse-args [args]
  (loop [opts {:url (or (System/getenv "PORTAL_URL")
                        "http://127.0.0.1:6767/repl")
               :token (read-token)}
         remaining args]
    (if (empty? remaining)
      opts
      (case (first remaining)
        "--url" (recur (assoc opts :url (second remaining)) (nnext remaining))
        "--token" (recur (assoc opts :token (second remaining)) (nnext remaining))
        (if (:code opts)
          (update opts :code #(str % " " (first remaining)))
          (recur (assoc opts :code (first remaining)) (next remaining)))))))

(def ^:private drawbridge-transport drawbridge/ring-client-transport)

(defn- exit! [code msg]
  (binding [*out* *err*]
    (when msg
      (println msg)))
  (System/exit code))

(defn- futon1-url []
  (or (some-> (System/getenv "PORTAL_FUTON1_URL") str/trim not-empty)
      (some-> (System/getenv "MUSN_FUTON1_URL") str/trim not-empty)
      "http://localhost:8080/api/alpha"))

(defn- futon1-profile []
  (or (some-> (System/getenv "PORTAL_FUTON1_PROFILE") str/trim not-empty)
      (some-> (System/getenv "MUSN_FUTON1_PROFILE") str/trim not-empty)))

(defn- fetch-json [url headers]
  (try
    (let [conn (.openConnection (java.net.URL. url))]
      (.setConnectTimeout conn 2000)
      (.setReadTimeout conn 5000)
      (doseq [[k v] headers]
        (.setRequestProperty conn k v))
      (with-open [r (io/reader (.getInputStream conn))]
        (json/read r :key-fn keyword)))
    (catch java.net.ConnectException _
      (binding [*out* *err*]
        (println "[portal] futon1 unavailable at" url))
      nil)
    (catch Exception e
      (binding [*out* *err*]
        (println "[portal] fetch failed:" (.getMessage e)))
      nil)))

(defn- parse-int [value fallback]
  (try
    (Integer/parseInt (str value))
    (catch Throwable _ fallback)))

(defn- tokenize [text]
  (->> (str/split (str/lower-case (or text "")) #"[^a-z0-9._-]+")
       (remove str/blank?)
       distinct
       vec))

(defn- pattern-id [entity]
  (or (:external-id entity)
      (:name entity)
      (:id entity)))

(defn- score-candidate [tokens entity]
  (let [haystack (str/lower-case (str (pattern-id entity)))]
    (count (filter #(str/includes? haystack %) tokens))))

(defn- registry-entities []
  (let [base (str/replace (futon1-url) #"/+$" "")
        url (str base "/patterns/registry")
        headers (cond-> {"accept" "application/json"}
                  (futon1-profile) (assoc "x-profile" (futon1-profile)))
        payload (fetch-json url headers)
        registry (or (:registry payload) payload)]
    (vec (or (:entities registry) []))))

(defn- render-output [data format]
  (case format
    :json (println (json/write-str data))
    (prn data)))

(defn- parse-pattern-args [args]
  (loop [opts {:limit 10
               :format :edn
               :args []}
         remaining args]
    (if (empty? remaining)
      opts
      (case (first remaining)
        "--limit" (recur (assoc opts :limit (parse-int (second remaining) (:limit opts)))
                         (nnext remaining))
        "--namespace" (recur (assoc opts :namespace (second remaining))
                             (nnext remaining))
        "--json" (recur (assoc opts :format :json) (next remaining))
        (recur (update opts :args conj (first remaining)) (next remaining))))))

(defn- parse-suggest-args [args]
  (loop [opts {:limit 8
               :format :edn
               :args []}
         remaining args]
    (if (empty? remaining)
      opts
      (case (first remaining)
        "--limit" (recur (assoc opts :limit (parse-int (second remaining) (:limit opts)))
                         (nnext remaining))
        "--namespace" (recur (assoc opts :namespace (second remaining))
                             (nnext remaining))
        "--json" (recur (assoc opts :format :json) (next remaining))
        (recur (update opts :args conj (first remaining)) (next remaining))))))

(defn- parse-keyword [value]
  (when (and value (not (str/blank? (str value))))
    (if (keyword? value)
      value
      (keyword value))))

(defn- parse-dbl [value fallback]
  (try
    (Double/parseDouble (str value))
    (catch Throwable _ fallback)))

(defn- parse-edn [value fallback]
  (try
    (edn/read-string (str value))
    (catch Throwable _ fallback)))

(defn- parse-propose-args [args]
  (loop [opts {:format :edn
               :kind :pattern
               :status :pending
               :score 0.5
               :method "portal"
               :evidence []
               :args []}
         remaining args]
    (if (empty? remaining)
      opts
      (case (first remaining)
        "--kind" (recur (assoc opts :kind (parse-keyword (second remaining))) (nnext remaining))
        "--target" (recur (assoc opts :target (second remaining)) (nnext remaining))
        "--score" (recur (assoc opts :score (parse-dbl (second remaining) (:score opts)))
                         (nnext remaining))
        "--method" (recur (assoc opts :method (second remaining)) (nnext remaining))
        "--status" (recur (assoc opts :status (parse-keyword (second remaining))) (nnext remaining))
        "--evidence" (recur (assoc opts :evidence (parse-edn (second remaining) (:evidence opts)))
                            (nnext remaining))
        "--json" (recur (assoc opts :format :json) (next remaining))
        (recur (update opts :args conj (first remaining)) (next remaining))))))

(defn- parse-promote-args [args]
  (loop [opts {:format :edn
               :args []}
         remaining args]
    (if (empty? remaining)
      opts
      (case (first remaining)
        "--kind" (recur (assoc opts :kind (parse-keyword (second remaining))) (nnext remaining))
        "--decided-by" (recur (assoc opts :decided-by (second remaining)) (nnext remaining))
        "--rationale" (recur (assoc opts :rationale (second remaining)) (nnext remaining))
        "--json" (recur (assoc opts :format :json) (next remaining))
        (recur (update opts :args conj (first remaining)) (next remaining))))))

(defn- filter-by-namespace [entries ns]
  (if (and ns (not (str/blank? ns)))
    (let [prefix (if (str/ends-with? ns "/") ns (str ns "/"))]
      (filter (fn [entry]
                (when-let [pid (pattern-id entry)]
                  (str/starts-with? pid prefix)))
              entries))
    entries))

(defn- patterns-list [opts]
  (let [entries (registry-entities)
        entries (filter-by-namespace entries (:namespace opts))
        entries (sort-by :name entries)
        entries (take (:limit opts) entries)]
    (mapv (fn [entry]
            {:id (pattern-id entry)
             :name (:name entry)})
          entries)))

(defn- patterns-search [query opts]
  (let [tokens (tokenize query)
        entries (registry-entities)
        entries (filter-by-namespace entries (:namespace opts))
        scored (->> entries
                    (map (fn [entry]
                           (let [score (score-candidate tokens entry)]
                             (assoc entry :score score))))
                    (filter #(pos? (:score %)))
                    (sort-by (juxt (comp - :score) :name))
                    (take (:limit opts)))]
    (mapv (fn [entry]
            {:id (pattern-id entry)
             :name (:name entry)
             :score (:score entry)})
          scored)))

(defn- patterns-get [pid]
  (let [entries (registry-entities)]
    (some (fn [entry]
            (when (= pid (pattern-id entry))
              entry))
          entries)))

(defn- handle-patterns [args]
  (let [{:keys [args format] :as opts} (parse-pattern-args args)
        subcmd (first args)
        rest-args (rest args)]
    (case subcmd
      "list" (render-output (patterns-list opts) format)
      "search" (let [query (str/join " " rest-args)]
                 (if (str/blank? query)
                   (exit! 2 (patterns-usage))
                   (render-output (patterns-search query opts) format)))
      "get" (if-let [pid (first rest-args)]
              (render-output (patterns-get pid) format)
              (exit! 2 (patterns-usage)))
      (exit! 2 (patterns-usage)))))

(defn- handle-suggest [args]
  (let [{:keys [args format] :as opts} (parse-suggest-args args)
        query (str/join " " args)]
    (if (str/blank? query)
      (exit! 2 (usage))
      (render-output (patterns-search query opts) format))))

(defn- require-nonblank [label value]
  (if (and value (not (str/blank? value)))
    value
    (exit! 2 (str "portal: missing " label))))

(defn- handle-propose [args]
  (let [{:keys [args format kind target score method status evidence]} (parse-propose-args args)
        proposal-id (first args)
        proposal-id (require-nonblank "proposal-id" proposal-id)
        evidence (cond
                   (nil? evidence) []
                   (coll? evidence) evidence
                   :else [evidence])
        proposal {:proposal/id proposal-id
                  :proposal/kind kind
                  :proposal/status status
                  :proposal/score score
                  :proposal/method method
                  :proposal/evidence evidence
                  :proposal/target-id target}
        store (sidecar.store/load-store-from-audit-log)
        result (sidecar.store/record-proposal! store proposal)]
    (render-output result format)))

(defn- handle-promote [args]
  (let [{:keys [args format kind decided-by rationale]} (parse-promote-args args)
        promotion-id (first args)
        proposal-id (second args)
        promotion-id (require-nonblank "promotion-id" promotion-id)
        proposal-id (require-nonblank "proposal-id" proposal-id)
        kind (or kind (exit! 2 "portal: missing --kind"))
        decided-by (require-nonblank "--decided-by" decided-by)
        rationale (require-nonblank "--rationale" rationale)
        promotion {:promotion/id promotion-id
                   :proposal/id proposal-id
                   :promotion/kind kind
                   :promotion/decided-by decided-by
                   :promotion/rationale rationale}
        store (sidecar.store/load-store-from-audit-log)
        result (sidecar.store/record-promotion! store promotion)]
    (render-output result format)))

(defn- handle-action [_args]
  (exit! 2 "portal: action not implemented"))

(defn- handle-evidence [_args]
  (exit! 2 "portal: evidence not implemented"))

(defn -main [& args]
  (case (first args)
    "patterns" (handle-patterns (rest args))
    "suggest" (handle-suggest (rest args))
    "propose" (handle-propose (rest args))
    "promote" (handle-promote (rest args))
    "action" (handle-action (rest args))
    "evidence" (handle-evidence (rest args))
    (let [{:keys [url token code]} (parse-args args)]
      (when-not drawbridge-transport
        (exit! 2 "portal: drawbridge transport missing"))
      (when-not code
        (exit! 2 (usage)))
      (when-not (and token (not (str/blank? token)))
        (exit! 2 "portal: missing ADMIN_TOKEN or --token"))
      (let [conn (nrepl/url-connect (str url "?token=" token))
            client (nrepl/client conn 2000)
            resp (nrepl/combine-responses (nrepl/message client {:op "eval" :code code}))
            status (set (:status resp))
            out (:out resp)
            err (:err resp)
            value (some-> (:value resp) last)]
        (when (seq out)
          (print out))
        (when (seq err)
          (binding [*out* *err*]
            (print err)))
        (when (some? value)
          (println value))
        (when (or (contains? status "eval-error")
                  (contains? status "error"))
          (exit! 2 "portal: eval error"))
        (when (instance? java.io.Closeable conn)
          (.close ^java.io.Closeable conn))))))
