(ns musn.portal
  (:require [cemerick.drawbridge.client :as drawbridge]
            [clojure.data.json :as json]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.tools.nrepl :as nrepl]))

(defn- usage []
  (str "usage: portal [--url URL] [--token TOKEN] <clj-code>\n"
       "   or: portal patterns <list|search|get> [args]\n"
       "env: ADMIN_TOKEN or .admintoken, PORTAL_URL (default http://127.0.0.1:6767/repl)\n"
       "env: PORTAL_FUTON1_URL or MUSN_FUTON1_URL (default http://localhost:8080/api/alpha)\n"))

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
  (let [conn (.openConnection (java.net.URL. url))]
    (doseq [[k v] headers]
      (.setRequestProperty conn k v))
    (with-open [r (io/reader (.getInputStream conn))]
      (json/read r :key-fn keyword))))

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

(defn- patterns-get [pattern-id]
  (let [entries (registry-entities)]
    (some (fn [entry]
            (when (= pattern-id (pattern-id entry))
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

(defn -main [& args]
  (if (= "patterns" (first args))
    (handle-patterns (rest args))
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
