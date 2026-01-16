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

(defn search-embeddings
  "Search patterns using MiniLM embeddings (requires sentence-transformers)."
  ([query] (search-embeddings query 5))
  ([query top-k]
   (let [script (notions-search-path)
         embeddings (embeddings-path)
         result (shell/sh "python3" script
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
          parse-field (fn [prefix]
                        (->> lines
                             (filter #(str/starts-with? (str/trim %) prefix))
                             first
                             (#(when % (str/trim (subs % (count prefix)))))))]
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

(defn get-pattern-details
  "Load full pattern details from its flexiarg source."
  [pattern-id]
  (let [;; Try common paths
        paths [(str "library/" (str/replace pattern-id #"/" "/") ".flexiarg")
               (str "../futon3/library/" (str/replace pattern-id #"/" "/") ".flexiarg")
               (str (System/getenv "FUTON3_ROOT") "/library/" (str/replace pattern-id #"/" "/") ".flexiarg")]]
    (some load-flexiarg paths)))

(defn enrich-results
  "Enrich search results with full pattern details."
  [results]
  (mapv (fn [r]
          (if-let [details (get-pattern-details (:id r))]
            (merge r details)
            r))
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
