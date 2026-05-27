(ns futon.notions
  "Pattern retrieval from the notions index.

   Two modes:
   1. Keyword matching (always works, uses hotwords from TSV)
   2. Embedding similarity (requires sentence-transformers, uses MiniLM)

   This namespace provides the Clojure interface for pattern retrieval
  that the compass demonstrator uses."
  (:require [clojure.data.json :as json]
            [futon.flexiarg.projection :as projection]
            [futon.text :as text]
            [clojure.java.io :as io]
            [clojure.set :as set]
            [clojure.string :as str])
  (:import (java.io BufferedReader BufferedWriter InputStreamReader OutputStreamWriter)
           (java.util UUID)))

;; --- TSV Index (keyword matching) ---

(defn- parse-tsv-line [line]
  (let [parts (str/split line #"\t" -1)]
    (when (>= (count parts) 5)
      {:id (nth parts 0)
       :tokipona (nth parts 1)
       :sigil (nth parts 2)
       :rationale (nth parts 3)
       :hotwords (set (str/split (nth parts 4) #",\s*"))})))

(defn load-pattern-index
  "Load the patterns-index.tsv file."
  ([] (load-pattern-index "resources/notions/patterns-index.tsv"))
  ([path]
   (with-open [rdr (io/reader path)]
     (->> (line-seq rdr)
          (drop 1)  ; skip header
          (map parse-tsv-line)
          (remove nil?)
          vec))))

(defn- score-pattern-keywords [tokens pattern]
  (let [hotwords (:hotwords pattern)
        overlap (count (set/intersection tokens hotwords))
        rationale-tokens (text/tokenize (:rationale pattern))
        rationale-overlap (count (set/intersection tokens rationale-tokens))]
    (+ (* 2.0 overlap) (* 0.5 rationale-overlap))))

(defn search-keywords
  "Search patterns using keyword matching against hotwords and rationale."
  ([query] (search-keywords query 5))
  ([query top-k]
   (let [index (load-pattern-index)
         tokens (text/tokenize query)
         scored (->> index
                     (map (fn [p] (assoc p :score (score-pattern-keywords tokens p))))
                     (filter #(pos? (:score %)))
                     (sort-by :score >)
                     (take top-k))]
     scored)))

;; --- Embedding search (via Python) ---

(defonce ^:private embeddings-cache (atom {}))
(defonce ^:private embed-server (atom nil))
(defonce ^:private embed-server-lock (Object.))

(defn- env-or-prop
  [name fallback]
  (or (System/getProperty name)
      (System/getenv name)
      fallback))

(defn- embed-text-path []
  (env-or-prop "EMBED_TEXT_PATH" "scripts/embed_text.py"))

(defn- embeddings-path []
  (env-or-prop "NOTIONS_EMBEDDINGS_PATH"
               "resources/notions/minilm_pattern_embeddings.json"))

(defn- venv-python []
  (let [env-python (or (System/getProperty "NOTIONS_PYTHON")
                       (System/getenv "NOTIONS_PYTHON"))
        venv-rel ".venv/bin/python3"
        start (try
                (.getCanonicalFile (io/file "."))
                (catch Exception _
                  (io/file ".")))
        venv-paths (->> (iterate #(when % (.getParentFile %)) start)
                        (take 6)
                        (keep identity)
                        (map #(io/file % venv-rel)))]
    (cond
      (seq env-python) env-python
      (some #(.exists %) venv-paths) (->> venv-paths (filter #(.exists %)) first str)
      :else "python3")))

(defn- now []
  (java.time.Instant/now))

(defn- normalize-vec [vec]
  (let [values (mapv double vec)
        norm (Math/sqrt (reduce + (map #(* % %) values)))]
    (if (zero? norm)
      values
      (mapv #(/ % norm) values))))

(defn- dot [a b]
  (reduce + (map * a b)))

(defn- file-stamp [path]
  (let [f (io/file path)]
    {:path path
     :exists (.exists f)
     :mtime (when (.exists f) (.lastModified f))
     :length (when (.exists f) (.length f))}))

(defn- parse-embedding-payload [payload]
  (cond
    (map? payload)
    (->> payload
         (map (fn [[id vec]]
                {:id id :vector (normalize-vec vec)}))
         vec)

    (vector? payload)
    (->> payload
         (mapv (fn [entry]
                 (let [vector (:vector entry)]
                   (cond-> entry
                     (vector? vector) (assoc :vector (normalize-vec vector))))))
         vec)

    :else
    []))

(defn load-embeddings-file
  [path]
  (let [stamp (file-stamp path)
        cached (get @embeddings-cache path)]
    (if (= (:stamp cached) stamp)
      (:entries cached)
      (let [entries (if-not (:exists stamp)
                      []
                      (-> path slurp (json/read-str :key-fn keyword) parse-embedding-payload))]
        (swap! embeddings-cache assoc path {:stamp stamp
                                            :loaded-at (now)
                                            :entries entries})
        entries))))

(defn- drain-error-stream! [process]
  (future
    (with-open [rdr (io/reader (.getErrorStream process))]
      (doseq [_line (line-seq rdr)]
        nil))))

(defn- dead-process? [process]
  (try
    (.exitValue process)
    true
    (catch IllegalThreadStateException _
      false)))

(defn- start-embed-server! []
  (let [python (venv-python)
        script (embed-text-path)
        builder (ProcessBuilder. ^java.util.List
                                 [python script "--server" "--model"
                                  "sentence-transformers/all-MiniLM-L6-v2"])
        process (.start builder)
        writer (BufferedWriter. (OutputStreamWriter. (.getOutputStream process)))
        reader (BufferedReader. (InputStreamReader. (.getInputStream process)))]
    (drain-error-stream! process)
    {:process process
     :writer writer
     :reader reader}))

(defn- ensure-embed-server! []
  (locking embed-server-lock
    (let [server @embed-server]
      (if (and server (not (dead-process? (:process server))))
        server
        (let [fresh (start-embed-server!)]
          (reset! embed-server fresh)
          fresh)))))

(defn embed-query
  [query]
  (locking embed-server-lock
    (let [{:keys [writer reader]} (ensure-embed-server!)
          payload (json/write-str {:id (str "query-" (UUID/randomUUID))
                                   :text query})]
      (.write ^BufferedWriter writer payload)
      (.newLine ^BufferedWriter writer)
      (.flush ^BufferedWriter writer)
      (let [line (.readLine ^BufferedReader reader)
            result (when line (json/read-str line :key-fn keyword))]
        (when-let [error (:error result)]
          (throw (ex-info "Embed server error" {:error error})))
        (some-> (:embedding result) normalize-vec)))))

(defn- rank-embedding-entries [query-vec entries top-k]
  (->> entries
       (keep (fn [entry]
               (when-let [vec (:vector entry)]
                 (assoc entry :score (double (dot query-vec vec))))))
       (sort-by :score >)
       (take top-k)
       vec))

(defn search-embeddings-file
  "Search an embeddings file using the Python MiniLM helper.

   Options:
   - :embeddings-path - JSON embeddings file
   - :type-filter     - optional \"mission\" or \"pattern\"
   - :top-k           - number of results (default 5)"
  [query & {:keys [embeddings-path type-filter top-k]
            :or {embeddings-path (embeddings-path)
                 top-k 5}}]
  (let [entries (load-embeddings-file embeddings-path)
        filtered (if (seq type-filter)
                   (filterv #(= type-filter (:type %)) entries)
                   entries)]
    (when (seq filtered)
      (when-let [query-vec (embed-query query)]
        (->> (rank-embedding-entries query-vec filtered top-k)
             (map-indexed (fn [idx entry]
                            (assoc entry :rank (inc idx))))
             vec)))))

(defn search-embeddings
  "Search patterns using MiniLM embeddings (requires sentence-transformers)."
  ([query] (search-embeddings query 5))
  ([query top-k]
   (let [results (search-embeddings-file query
                                         :embeddings-path (embeddings-path)
                                         :top-k top-k)]
     (if (seq results)
       results
       ;; Fallback to keywords if embedding search fails
       (do
         (println "[notions] Embedding search failed, falling back to keywords")
         (search-keywords query top-k))))))

;; --- TSV lookup by ID ---

(defn- index-by-id
  "Create a map from pattern ID to pattern data."
  [patterns]
  (into {} (map (juxt :id identity) patterns)))

(defonce ^:private tsv-index-cache (atom nil))

(defn- get-tsv-index []
  (or @tsv-index-cache
      (reset! tsv-index-cache (index-by-id (load-pattern-index)))))

(defn get-tsv-data
  "Get TSV data for a pattern ID."
  [pattern-id]
  (get (get-tsv-index) pattern-id))

;; --- Unified interface ---

(defn search
  "Search for patterns matching a query.

   Options:
   - :method - :keywords, :embeddings, or :auto (default)
   - :top-k - number of results (default 5)

   :auto tries embeddings first, falls back to keywords."
  [query & {:keys [method top-k] :or {method :auto top-k 5}}]
  (case method
    :keywords (search-keywords query top-k)
    :embeddings (search-embeddings query top-k)
    :auto (let [results (search-embeddings query top-k)]
            (if (seq results)
              results
              (search-keywords query top-k)))))

;; --- Pattern details ---

(defn- clause-map
  [packet]
  (into {} (map (juxt :name-key :text) (:pattern/clauses packet))))

(defn- parse-next-steps [text]
  (->> (str/split-lines (or text ""))
       (map str/trim)
       (remove str/blank?)
       (map #(cond
               (str/starts-with? % "next[") (str/replace % #"^next\[(.*)\]$" "$1")
               (str/starts-with? % "- ") (subs % 2)
               :else %))
       vec))

(defn- load-flexiarg
  "Load and parse a flexiarg file via the canonical projection."
  [path]
  (when (.exists (io/file path))
    (when-let [packet (some #(when (= :ok (:pattern/status %)) %)
                            (projection/parse-file path {:futon3-root "/home/joe/code/futon3"}))]
      (let [clauses (clause-map packet)]
        {:path path
         :id (:pattern/id packet)
         :title (:pattern/title packet)
         :energy (some-> (get-in packet [:pattern/directives :energy])
                         str/lower-case
                         keyword)
         :sigils (str/join " " (:pattern/sigils packet))
         :if (get clauses "if")
         :however (get clauses "however")
         :then (or (get clauses "then")
                   (get clauses "conclusion")
                   (get clauses "claim"))
         :because (get clauses "because")
         :next-steps (parse-next-steps (get clauses "next-steps"))}))))

(def ^:private devmap-header-re
  #"^!\s+instantiated-by:\s+Prototype\s+(\d+)\s+—\s+(.*)\s+\[(.*)\]\s*$")

(def ^:private clause-re
  #"^\s*[+!]\s+([^:]+):\s*(.*)$")

(defn- storage-roots
  "Canonical roots for storage-only Futon3 artifacts (library + holes).
   Order matters: prefer explicit env root, then local futon3 checkout,
   then legacy absolute path. Repo-local fallbacks are appended separately."
  []
  (let [env-root (or (System/getenv "FUTON3_STORAGE_ROOT")
                     (System/getenv "FUTON3_ROOT"))]
    (->> [env-root
          "../futon3"
          "/home/joe/code/futon3"]
         (remove nil?)
         (map str/trim)
         (remove str/blank?)
         distinct)))

(defn- storage-subdir-roots
  "Resolve candidate roots for SUBDIR, preferring futon3 storage roots.
   Falls back to repo-local subdir for compatibility."
  [subdir]
  (let [suffix (str subdir "/")
        storage (map #(str (io/file % subdir) "/") (storage-roots))]
    (->> (concat storage [suffix])
         distinct)))

(defn- parse-devmap-block
  [lines]
  (->> lines
       (keep (fn [line]
               (when-let [[_ label text] (re-matches clause-re line)]
                 [(keyword (str/lower-case (str/trim label))) (str/trim text)])))
       (reduce (fn [acc [k v]]
                 (update acc k (fnil conj []) v))
               {})))

(defn- devmap-paths
  []
  (let [roots (storage-subdir-roots "holes")]
    (->> roots
         (remove nil?)
         (map #(io/file %))
         (filter #(.exists %))
         (mapcat #(file-seq %))
         (filter #(and (.isFile %) (str/ends-with? (.getName %) ".devmap"))))))

(defn- load-devmap
  "Load a devmap entry for pattern IDs like f3/p4."
  [pattern-id]
  (when-let [[_ futon proto] (re-matches #"f(\d+)/p(\d+)" pattern-id)]
    (let [target-futon (str "futon" futon ".devmap")
          files (filter #(= target-futon (.getName %)) (devmap-paths))]
      (some (fn [^java.io.File file]
              (let [lines (str/split-lines (slurp file))
                    sentinel "! instantiated-by: Prototype 0 — END [x/y]"]
                (loop [remaining (concat lines [sentinel])]
                  (when-let [line (first remaining)]
                    (if-let [[_ pnum title _] (re-matches devmap-header-re line)]
                      (let [block (->> (rest remaining)
                                       (take-while #(not (re-matches devmap-header-re %))))
                            data (parse-devmap-block block)]
                        (if (= pnum proto)
                          (let [pick (fn [k] (first (get data k)))
                                then (or (pick :then) (pick :conclusion) (pick :claim))]
                            {:path (.getPath file)
                             :id pattern-id
                             :title title
                             :if (pick :if)
                             :however (pick :however)
                             :then then
                             :because (pick :because)
                             :context (pick :context)
                             :devmap? true})
                          (recur (drop (count block) (rest remaining)))))
                      (recur (rest remaining)))))))
            files))))

(defn get-pattern-details
  "Load full pattern details from its flexiarg source."
  [pattern-id]
  (let [;; Pattern ID like 'agent/evidence-over-assertion' maps to
        ;; 'library/agent/evidence-over-assertion.flexiarg'
        file-path (str pattern-id ".flexiarg")
        ;; Prefer futon3 storage roots, then local fallback.
        roots (storage-subdir-roots "library")
        paths (->> roots
                   (remove nil?)
                   (map #(str % file-path)))]
    (or (some load-flexiarg paths)
        (load-devmap pattern-id))))

(defn enrich-results
  "Enrich search results with TSV data and flexiarg details.
   TSV provides: hotwords, rationale, tokipona, sigil
   Flexiarg provides: if, however, then, because, next-steps"
  [results]
  (mapv (fn [r]
          (let [tsv-data (get-tsv-data (:id r))
                flexiarg-data (get-pattern-details (:id r))]
            (merge r tsv-data flexiarg-data)))
        results))

;; --- Demo ---

(comment
  ;; Keyword search
  (search-keywords "track proposals evidence before facts" 5)

  ;; Auto search (tries embeddings, falls back to keywords)
  (search "I want to track proposals and evidence before committing to facts")

  ;; Enrich with flexiarg details
  (-> (search "typed arrows semantic transforms")
      enrich-results)
  )
