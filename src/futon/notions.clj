(ns futon.notions
  "Pattern retrieval from the notions index.

   Two modes:
   1. Keyword matching (always works, uses hotwords from TSV)
   2. Embedding similarity (requires sentence-transformers, uses MiniLM)

   This namespace provides the Clojure interface for pattern retrieval
   that the compass demonstrator uses."
  (:require [clojure.data.json :as json]
            [clojure.java.io :as io]
            [clojure.java.shell :as shell]
            [clojure.set :as set]
            [clojure.string :as str]))

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

(defn- tokenize [text]
  (->> (str/split (str/lower-case (or text "")) #"[^a-z0-9]+")
       (remove str/blank?)
       (remove #(< (count %) 3))
       set))

(defn- score-pattern-keywords [tokens pattern]
  (let [hotwords (:hotwords pattern)
        overlap (count (set/intersection tokens hotwords))
        rationale-tokens (tokenize (:rationale pattern))
        rationale-overlap (count (set/intersection tokens rationale-tokens))]
    (+ (* 2.0 overlap) (* 0.5 rationale-overlap))))

(defn search-keywords
  "Search patterns using keyword matching against hotwords and rationale."
  ([query] (search-keywords query 5))
  ([query top-k]
   (let [index (load-pattern-index)
         tokens (tokenize query)
         scored (->> index
                     (map (fn [p] (assoc p :score (score-pattern-keywords tokens p))))
                     (filter #(pos? (:score %)))
                     (sort-by :score >)
                     (take top-k))]
     scored)))

;; --- Embedding search (via Python) ---

(defn- notions-search-path []
  (or (System/getenv "NOTIONS_SEARCH_PATH")
      "scripts/notions_search.py"))

(defn- embeddings-path []
  (or (System/getenv "NOTIONS_EMBEDDINGS_PATH")
      "resources/notions/minilm_pattern_embeddings.json"))

(defn- venv-python []
  (let [env-python (System/getenv "NOTIONS_PYTHON")
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

(defn search-embeddings
  "Search patterns using MiniLM embeddings (requires sentence-transformers)."
  ([query] (search-embeddings query 5))
  ([query top-k]
   (let [script (notions-search-path)
         embeddings (embeddings-path)
         python (venv-python)
         result (shell/sh python script
                          "--query" query
                          "--top" (str top-k)
                          "--embeddings" embeddings)]
     (if (zero? (:exit result))
       (->> (str/split-lines (:out result))
            (map (fn [line]
                   (when-let [[_ rank id score title]
                              (re-matches #"\s*(\d+)\.\s+(\S+)\s+\(([0-9.]+)\)(?:\s+-\s+(.*))?" line)]
                     {:id id
                      :score (Double/parseDouble score)
                      :title (or title "")
                      :rank (Integer/parseInt rank)})))
            (remove nil?)
            vec)
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

(defn- load-flexiarg
  "Load and parse a flexiarg file."
  [path]
  (when (.exists (io/file path))
    (let [content (slurp path)
          lines (str/split-lines content)
          ;; Parse field that may have content on same line or next line
          parse-field (fn [prefix]
                        (let [idx (->> lines
                                       (map-indexed vector)
                                       (filter #(str/starts-with? (str/trim (second %)) prefix))
                                       first)]
                          (when idx
                            (let [[i line] idx
                                  trimmed-line (str/trim line)
                                  ;; Get content after prefix from the trimmed line
                                  same-line (str/trim (subs trimmed-line (count prefix)))]
                              (if (str/blank? same-line)
                                ;; Content on next line(s) - take until next + or ! or @ or blank
                                (->> lines
                                     (drop (inc i))
                                     (take-while #(let [t (str/trim %)]
                                                    (and (not (str/blank? t))
                                                         (not (str/starts-with? t "+"))
                                                         (not (str/starts-with? t "!"))
                                                         (not (str/starts-with? t "@")))))
                                     (map str/trim)
                                     (str/join " "))
                                same-line)))))]
      {:path path
       :id (parse-field "@flexiarg ")
       :title (parse-field "@title ")
       :if (parse-field "+ IF:")
       :however (parse-field "+ HOWEVER:")
       :then (parse-field "+ THEN:")
       :because (parse-field "+ BECAUSE:")
       :next-steps (->> lines
                        (drop-while #(not (str/starts-with? (str/trim %) "+ NEXT-STEPS:")))
                        rest
                        (take-while #(str/starts-with? (str/trim %) "- "))
                        (mapv #(str/replace (str/trim %) #"^- " "")))})))

(def ^:private devmap-header-re
  #"^!\s+instantiated-by:\s+Prototype\s+(\d+)\s+—\s+(.*)\s+\[(.*)\]\s*$")

(def ^:private clause-re
  #"^\s*[+!]\s+([^:]+):\s*(.*)$")

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
  (let [roots ["holes/"
               "../futon3/holes/"
               "/home/joe/code/futon3/holes/"
               (when-let [r (System/getenv "FUTON3_ROOT")] (str r "/holes/"))]]
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
                    sentinel (str "! instantiated-by: Prototype 0 — END [x/y]")]
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
        ;; Try common library roots
        roots ["library/"
               "../futon3/library/"
               "/home/joe/code/futon3/library/"
               (when-let [r (System/getenv "FUTON3_ROOT")] (str r "/library/"))]
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
