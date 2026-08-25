(ns futon.missions
  "Mission corpus indexing and search for the self-documenting-stack mission.

   **T-9b (2026-05-22)**: substrate-2 (futon1a) is now the *sole* parser
   path. `futon3c.watcher.file-ingest/ingest-mission-doc!` writes the
   full enriched record (id, title, status, repo, date, owner, summary,
   cross-refs, code-paths, phase) to substrate-2 as `code/v05/mission-doc`
   hyperedge props. This namespace reads those props directly — the
   previous per-mission markdown reparse for enrichment has been removed,
   along with the `parse-mission-doc` filesystem parser and its
   filesystem-walk fallback in `load-missions`. The records-cache
   fallback remains for offline / hermetic-test scenarios where
   futon1a is unreachable.

   The ranking layer is unchanged:
   - typed slots feed the structural matcher
   - embedding ranking is delegated to the existing MiniLM helper
   - both run in parallel per D10; divergence emits per D11"
  (:require [clojure.data.json :as json]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [futon.mission-trace :as trace]
            [futon.notions :as notions]
            [futon.text :as text])
  (:import (java.io File)
           (java.net URLEncoder)
           (java.time Instant LocalDate ZoneId)
           (java.time.format DateTimeFormatter)
           (java.util UUID)))

(def ^:private futon1a-url
  (or (System/getenv "FUTON1A_URL")
      (System/getProperty "FUTON1A_URL")
      "http://localhost:7071"))

(def ^:private mission-doc-hyperedge-type "code/v05/mission-doc")

(def ^:private phase-order
  [:head :identify :map :derive :argue :verify :instantiate :document :complete])

(def ^:private mission-cache (atom nil))

(defn- exit!
  [code msg]
  (binding [*out* *err*]
    (when msg
      (println msg)))
  (System/exit code))

(defn- normalize-spaces [s]
  (-> s
      (str/replace #"\s+" " ")
      str/trim))

(defn- phase-rank [phase]
  (let [idx (.indexOf phase-order phase)]
    (if (neg? idx) 999 idx)))

(defn default-records-path []
  "resources/notions/mission_records.json")

;; T-9b: `parse-mission-doc` and its enrichment helpers (mission-summary,
;; extract-code-paths, classify-phase, etc.) removed. Filesystem parsing
;; is now owned exclusively by
;; `futon3c.peripheral.mission-control-backend/parse-mission-md`, which
;; writes the enriched record to substrate-2 via the multi-watcher.
;; This namespace reads from substrate-2 — see `hyperedge-props->record`.

(defn- file-stamp [path]
  (let [f (io/file path)]
    {:path path
     :exists (.exists f)
     :mtime (when (.exists f) (.lastModified f))
     :length (when (.exists f) (.length f))}))

(defn- load-missions-from-records [records-path]
  (let [f (io/file records-path)]
    (when (.exists f)
      (-> records-path
          slurp
          (json/read-str :key-fn keyword)
          vec))))

;; ---------------------------------------------------------------------
;; T-9: substrate-2 hyperedge query as the canonical mission corpus source.
;; ---------------------------------------------------------------------

(defn- url-encode [s]
  (URLEncoder/encode (str s) "UTF-8"))

(defn- fetch-mission-hyperedges
  "Query futon1a for all current mission-doc hyperedges. Returns a vec of
   props maps (the `:hx/props` payload from each hyperedge), or `nil` if
   futon1a is unreachable. Operator-overridable via :limit kwarg; default
   500 is large enough for the current corpus (~64-200 missions)."
  ([] (fetch-mission-hyperedges {}))
  ([{:keys [limit base-url]
     :or {limit 500
          base-url futon1a-url}}]
   (let [url (str base-url
                  "/api/alpha/hyperedges?type="
                  (url-encode mission-doc-hyperedge-type)
                  "&limit=" limit)]
     (try
       (let [conn (-> (java.net.URL. url)
                      .openConnection)]
         (.setRequestProperty conn "Accept" "application/json")
         (.setConnectTimeout conn 5000)
         ;; 5s was not enough: the corpus grew past what substrate-2 serves in
         ;; that window (limit 100 returns, limit 1000 times out), and the
         ;; `catch Exception _ nil` below turns a timeout into a SILENT fall
         ;; back to the cached mission_records.json. That froze the corpus at
         ;; 311 records / 277 missions while the live substrate held 338, and
         ;; the 30 missing ones rendered at one fallback coordinate on the EFE
         ;; field. (claude-13, 2026-08-25.)
         (.setReadTimeout conn 60000)
         (let [code (.getResponseCode conn)]
           (when (= 200 code)
             (let [body (slurp (.getInputStream conn))
                   parsed (json/read-str body :key-fn keyword)
                   hxes (:hyperedges parsed)]
               ;; futon1b serves `hx/props` INCONSISTENTLY: as a JSON object
               ;; for some edges and as an EDN string (a pr-str of the same
               ;; map) for others -- 73 vs 299 on the mission-doc set,
               ;; 2026-08-25. `(get props :mission/id)` on a string is nil, so
               ;; every string-shaped edge mapped to the degenerate record
               ;; `mission/M-@`. Parse the string form rather than dropping it;
               ;; the upstream serialization is the real defect.
               ;; (claude-13, 2026-08-25.)
               (->> hxes
                    (map :hx/props)
                    (map (fn [p]
                           (if (string? p)
                             (try (edn/read-string p) (catch Exception _ nil))
                             p)))
                    (filter map?)
                    vec)))))
       (catch Exception _ nil)))))

(defn- hyperedge-props->record
  "Map a substrate-2 mission-doc hyperedge's props to LC1's record shape.

   T-9b: All enrichment fields (`:owner`, `:summary`, `:cross_refs`,
   `:code_paths`, `:phase`) now come directly from substrate-2 props
   written by `futon3c.watcher.file-ingest/ingest-mission-doc!`. No
   per-mission filesystem reparse. If a downstream consumer needs a
   field that substrate-2 doesn't carry, the fix is to extend the
   canonical parser (`futon3c.peripheral.mission-control-backend/
   parse-mission-md`), not to re-parse here."
  [props]
  (let [g (fn [kw str-key] (or (get props kw) (get props str-key)))
        mission-id (g :mission/id "mission/id")
        title (g :mission/title "mission/title")
        status (g :mission/status "mission/status")
        repo (g :mission/repo "mission/repo")
        date (g :mission/date "mission/date")
        source-file (g :source-file "source-file")
        owner (g :mission/owner "mission/owner")
        summary (g :mission/summary "mission/summary")
        refs (or (g :mission/cross-refs "mission/cross-refs") [])
        code-paths (or (g :mission/code-paths "mission/code-paths") [])
        phase-raw (g :mission/phase "mission/phase")
        phase (cond
                (nil? phase-raw) :unknown
                (keyword? phase-raw) phase-raw
                (string? phase-raw) (keyword phase-raw)
                :else :unknown)
        source-text (normalize-spaces
                     (str (or title "") " "
                          (or status "") " "
                          (or summary "")))]
    {:id (str "mission/M-" mission-id "@" repo)
     :basename (str "M-" mission-id)
     :title title
     :type "mission"
     :path source-file
     :source source-file
     :home_repo repo
     :status (or status "")
     :owner (or owner "")
     :phase (name phase)
     :phase_rank (phase-rank phase)
     :date date
     :summary (or summary "")
     :text source-text
     :cross_refs refs
     :code_paths code-paths
     :substrate-2/hyperedge-id (or (:hx/id props) nil)}))

(defn- load-missions-from-substrate-2
  "Query substrate-2 hyperedges and map to LC1 record shape. Returns vec
   of records, or `nil` if substrate-2 is unreachable."
  ([] (load-missions-from-substrate-2 {}))
  ([opts]
   (when-let [props-list (fetch-mission-hyperedges opts)]
     (mapv hyperedge-props->record props-list))))

(defn load-missions
  "Load the canonical mission corpus. Sources tried in priority order:

   1. **substrate-2 via futon1a** (T-9b; the canonical and only parser
      path). Query the live hypergraph for `code/v05/mission-doc`
      hyperedges; convert via `hyperedge-props->record`. Operator-
      overridable via `FUTON1A_URL` env-var.
   2. **Cached records JSON** — if substrate-2 is unreachable AND a
      `mission_records.json` exists from a prior offline ingest.

   The previous filesystem-walk fallback was removed in T-9b along
   with the duplicate `parse-mission-doc` parser. If both substrate-2
   and the records cache are unavailable, `load-missions` returns
   an empty vector and emits a warning to *err*.

   Opts:
   - `:prefer-substrate-2?` (default true) — set to false to skip the
     substrate-2 path (e.g., for offline / hermetic testing)
   - `:records-path`, `:use-records?` — control the cache fallback"
  ([] (load-missions {}))
  ([{:keys [records-path use-records? prefer-substrate-2?]
     :or {records-path (default-records-path)
          use-records? true
          prefer-substrate-2? true}}]
   (let [records-stamp (file-stamp records-path)
         cache-key {:records-path records-path
                    :records-stamp records-stamp
                    :use-records? use-records?
                    :prefer-substrate-2? prefer-substrate-2?}
         cached @mission-cache]
     (if (= (:cache-key cached) cache-key)
       (:missions cached)
       (let [substrate-2 (when prefer-substrate-2?
                           (load-missions-from-substrate-2))
             records-cache (when (and (nil? substrate-2)
                                      use-records?
                                      (:exists records-stamp))
                             (load-missions-from-records records-path))
             missions (or substrate-2 records-cache [])
             source (cond
                      substrate-2 :substrate-2
                      records-cache :records-cache
                      :else :empty)]
         (when (= source :empty)
           (binding [*out* *err*]
             (println "[futon.missions] WARNING: load-missions returning empty —"
                      "substrate-2 (futon1a) unreachable AND no records cache.")))
         (reset! mission-cache {:cache-key cache-key
                                :missions missions
                                :source source})
         missions)))))

(defn last-load-source
  "Returns the source mode used by the most recent `load-missions` call
   — one of `:substrate-2`, `:records-cache`, `:filesystem-walk-fallback`,
   or nil if no load has occurred."
  []
  (:source @mission-cache))

(defn typed-slots [mission]
  (select-keys mission [:id :basename :title :status :home_repo :date :owner :phase
                        :cross_refs :summary :path :code_paths]))

(defn- weighted-overlap [tokens text weight]
  (* weight (text/overlap-count tokens text)))

(defn- structural-score [tokens mission]
  (+ (weighted-overlap tokens (:title mission) 3.0)
     (weighted-overlap tokens (:summary mission) 2.0)
     (weighted-overlap tokens (:status mission) 1.0)
     (weighted-overlap tokens (:owner mission) 1.0)
     (weighted-overlap tokens (str/join " " (:cross_refs mission)) 1.0)))

(defn- matches-slot? [mission [slot raw-value]]
  (let [value (str/lower-case (or raw-value ""))
        current (-> (get mission slot "")
                    str
                    str/lower-case)]
    (or (= current value)
        (and (= slot :status) (str/includes? current value))
        (and (= slot :phase) (str/includes? current value))
        (and (= slot :owner) (str/includes? current value))
        (and (= slot :title) (str/includes? current value))
        (and (= slot :summary) (str/includes? current value)))))

(defn filter-missions [missions slot-filters]
  (if (seq slot-filters)
    (filterv (fn [mission]
               (every? #(matches-slot? mission %) slot-filters))
             missions)
    missions))

(defn structural-search
  [missions query {:keys [top-k slot-filters] :or {top-k 5 slot-filters {}}}]
  (let [tokens (text/tokenize query)
        filtered (filter-missions missions slot-filters)]
    (->> filtered
         (map (fn [mission]
                (assoc mission :structural_score (structural-score tokens mission))))
         (filter #(or (seq slot-filters) (pos? (:structural_score %))))
         (sort-by (juxt (comp - :structural_score)
                        :phase_rank
                        :date
                        :title))
         (take top-k)
         vec)))

(defn- embedding-results
  [query opts]
  (let [mission-results (or (notions/search-embeddings-file
                             query
                             :embeddings-path (or (:mission-embeddings-path opts)
                                                  "resources/notions/minilm_mission_embeddings.json")
                             :type-filter "mission"
                             :top-k (max (* 3 (:top-k opts 5)) 8))
                            [])
        pattern-results (if (#{:pattern :both} (:type opts))
                          (or (notions/search-embeddings-file
                               query
                               :embeddings-path (or (:corpus-embeddings-path opts)
                                                    "resources/notions/minilm_corpus_embeddings.json")
                               :type-filter "pattern"
                               :top-k (max (* 3 (:top-k opts 5)) 8))
                              [])
                          [])]
    {:missions mission-results
     :patterns pattern-results}))

(defn- agreement-score
  "How much do v1 and v1.1 agree on the rank? 1.0 = identical rank,
   0.0 = maximally divergent. Defined only when both methods ranked it."
  [v1-rank v11-rank]
  (when (and v1-rank v11-rank)
    (/ 1.0 (+ 1.0 (Math/abs ^long (- v1-rank v11-rank))))))

(defn- confidence-from-ranks
  "T-8 + T-1 refinement (2026-05-21): signal-strength rather than agreement-
   only. Reflects 'how confident are we this result is relevant'.

   - Both methods rank: best-rank dominates, scaled by corroboration bonus
     (0.5 + 0.5 * agreement). A both-ranked top-1 with full agreement = 1.0;
     a both-ranked top-1 with delta=6 still gets ~0.54 (best-rank is strong).
   - v1.1 only: 1/rank (rank-1 literal-title-match = 1.0; rank-3 = 0.33).
   - v1 only: 0.5 * 1/rank (cosine artifact-prone per M-pattern-mining; halved
     vs v1.1-only at same rank)."
  [v1-rank v11-rank]
  (cond
    (and v1-rank v11-rank)
    (let [best (min v1-rank v11-rank)
          agreement (agreement-score v1-rank v11-rank)]
      (min 1.0 (* (/ 1.0 (double best))
                  (+ 0.5 (* 0.5 agreement)))))

    v11-rank (/ 1.0 (double v11-rank))
    v1-rank (* 0.5 (/ 1.0 (double v1-rank)))
    :else 0.0))

(defn- divergence-score
  "1.0 minus the agreement-score. Defined only when both methods ranked
   the result. Independent of confidence-from-ranks (which is now signal-
   strength rather than agreement)."
  [v1-rank v11-rank]
  (when-let [a (agreement-score v1-rank v11-rank)]
    (- 1.0 a)))

(defn- fusion-score
  "T-8 (2026-05-21): rank-based fusion that downweights v1-only (cosine-
   artifact) results per M-pattern-mining's substrate verdict. Replaces
   the prior score-based formula where embedding-score had implicit
   weight 1.0 and dominated v1.1's structural signal.

   Components:
   - v1.1-component: dominant (weight 2.0/rank). Structural is verifiable.
   - v1-component: proportional only when v1.1 corroborates (0.8/rank);
                   v1-only contribution heavily downweighted (0.2/rank).
   - agreement-bonus: 0.1 when both methods rank the result (corroboration is signal).
   - structural-tiebreak: small (0.05 * structural-score) for ordering ties."
  [embedding-rank structural-rank structural-score]
  (let [both? (and embedding-rank structural-rank)
        v1-only? (and embedding-rank (not structural-rank))
        v11-component (if structural-rank (/ 2.0 (double structural-rank)) 0.0)
        v1-component (cond
                       both? (/ 0.8 (double embedding-rank))
                       v1-only? (/ 0.2 (double embedding-rank))
                       :else 0.0)
        agreement-bonus (if both? 0.1 0.0)]
    (+ v11-component
       v1-component
       agreement-bonus
       (* 0.05 (or structural-score 0.0)))))

(defn- attach-diagnostics
  [mission embedding-rank structural-rank embedding-score]
  (let [confidence (confidence-from-ranks embedding-rank structural-rank)]
    (assoc mission
           :score (double (fusion-score embedding-rank structural-rank
                                        (:structural_score mission 0.0)))
           :embedding_score (when embedding-score (double embedding-score))
           :v1_rank embedding-rank
           :v1_1_rank structural-rank
           :confidence confidence
           :divergence_score (divergence-score embedding-rank structural-rank))))

(defn- emit-divergence-trace!
  [query-id query results]
  (doseq [result results
          :when (pos? (double (or (:divergence_score result) 0.0)))]
    (trace/emit-divergence!
     {:query-id query-id
      :query query
      :result-id (:id result)
      :title (:title result)
      :v1-rank (:v1_rank result)
      :v1.1-rank (:v1_1_rank result)
      :confidence (:confidence result)
      :divergence-score (:divergence_score result)})))

(defn- mission-result
  [mission]
  (select-keys mission [:id :title :path :home_repo :status :phase :date :owner
                        :summary :score :v1_rank :v1_1_rank :confidence
                        :divergence_score :cross_refs]))

(defn- pattern-result [entry]
  {:id (:id entry)
   :title (:title entry)
   :path (:source entry)
   :type "pattern"
   :score (:score entry)
   :summary (or (:summary entry) "")
   :confidence 0.0
   :divergence_score 1.0})

(defn- resolve-mission-ref [basename-index current mission-name]
  (or (some #(when (= (:home_repo %) (:home_repo current)) %) (get basename-index mission-name))
      (first (get basename-index mission-name))))

(defn build-mission-graph
  [missions results]
  (let [mission-map (into {} (map (juxt :id identity) missions))
        basename-index (reduce (fn [acc mission]
                                 (update acc (:basename mission) (fnil conj []) mission))
                               {}
                               missions)
        top-missions (->> results
                          (filter #(= "mission" (:type % "mission")))
                          vec)
        nodes (atom {})
        links (atom {})]
    (doseq [result top-missions]
      (swap! nodes assoc (:id result)
             {:id (:id result)
              :label (:title result)
              :node_type "mission"
              :status (:status result)
              :path (:path result)
              :score (:score result)})
      (doseq [ref (:cross_refs result)]
        (when-let [target (resolve-mission-ref basename-index result ref)]
          (swap! nodes assoc (:id target)
                 {:id (:id target)
                  :label (:title target)
                  :node_type "mission"
                  :status (:status target)
                  :path (:path target)
                  :score (:score target 0.0)})
          (swap! links assoc (str (:id result) "->" (:id target) ":cross-reference")
                 {:id (str (:id result) "->" (:id target) ":cross-reference")
                  :source (:id result)
                  :target (:id target)
                  :type "cross-reference"})))
      (doseq [code-path (take 6 (:code_paths (get mission-map (:id result)) []))]
        (let [node-id (str "path:" code-path)]
          (swap! nodes assoc node-id
                 {:id node-id
                  :label (.getName (io/file code-path))
                  :node_type "code-path"
                  :path code-path
                  :score 0.0})
          (swap! links assoc (str (:id result) "->" node-id ":mentions-path")
                 {:id (str (:id result) "->" node-id ":mentions-path")
                  :source (:id result)
                  :target node-id
                  :type "mentions-path"}))))
    {:nodes (vec (vals @nodes))
     :links (vec (vals @links))}))

(defn record-consumer-event!
  [{:keys [query-id consumer-id result-id action title]}]
  (let [event (trace/emit-consumer-event!
               {:query-id query-id
                :consumer-id (or consumer-id "unknown-consumer")
                :result-id result-id
                :action action
                :title title})]
    {:ok true
     :event_type "consumer-event"
     :query_id (:query/id event)
     :consumer_id (:consumer/id event)
     :result_id (:result/id event)
     :title (:result/title event)
     :action (some-> (:action event) name)
     :emitted_at (str (:emitted-at event))}))

(defn search-missions
  "Search missions corpus via parallel v1 (MiniLM cosine) + v1.1 (structural).
   Filter knobs:
   - `:confidence-threshold` (0.0-1.0; default 0.0) — drops low-confidence results
   - `:agreement-only?` (default false) — restrict to both-ranked results (T-1)
   - `:top-k` (default 5)
   - `:slot-filters` (default {}) — typed-slot predicates
   - `:type` :mission | :pattern | :both"
  [query {:keys [top-k slot-filters type confidence-threshold agreement-only?
                 records-path]
          :or {top-k 5 slot-filters {} type :mission confidence-threshold 0.0
               agreement-only? false
               records-path (default-records-path)}
          :as opts}]
  (let [query-id (str "mission-query-" (UUID/randomUUID))
        missions (load-missions {:records-path records-path})
        filtered (filter-missions missions slot-filters)
        structural-f (future (structural-search filtered query {:top-k (max (* 3 top-k) 8)
                                                                :slot-filters slot-filters}))
        embedding-f (future (embedding-results query (assoc opts :top-k top-k)))
        structural @structural-f
        structural-ranks (into {} (map-indexed (fn [idx mission]
                                                 [(:id mission) (inc idx)])
                                               structural))
        structural-by-id (into {} (map (juxt :id identity) structural))
        embedding-payload @embedding-f
        mission-embedding-results (:missions embedding-payload)
        pattern-embedding-results (:patterns embedding-payload)
        embedding-ranks (into {} (map (juxt :id :rank) mission-embedding-results))
        embedding-scores (into {} (map (juxt :id :score) mission-embedding-results))
        embedding-ids (set (keys embedding-ranks))
        candidate-ids (vec (distinct (concat (keys structural-by-id) embedding-ids)))
        combined (->> candidate-ids
                      (keep (fn [mission-id]
                              (when-let [mission (or (get structural-by-id mission-id)
                                                     (some #(when (= (:id %) mission-id) %) filtered))]
                                (attach-diagnostics mission
                                                    (get embedding-ranks mission-id)
                                                    (get structural-ranks mission-id)
                                                    (get embedding-scores mission-id)))))
                      (sort-by (juxt (comp - :score)
                                     (comp - :confidence)
                                     :phase_rank
                                     :title))
                      (filter #(>= (:confidence %) confidence-threshold))
                      (filter #(if agreement-only?
                                 (and (:v1_rank %) (:v1_1_rank %))
                                 true))
                      (take top-k)
                      vec)
        results (cond
                  (= type :pattern)
                  (->> pattern-embedding-results
                       (take top-k)
                       (mapv pattern-result))

                  (= type :both)
                  (->> (concat (map mission-result combined)
                               (map pattern-result (take top-k pattern-embedding-results)))
                       (sort-by (comp - :score))
                       (take top-k)
                       vec)

                  :else
                  (mapv mission-result combined))]
    (emit-divergence-trace! query-id query combined)
    {:query query
     :query_id query-id
     :type (name type)
     :results results
     :graph (build-mission-graph missions results)}))

(defn write-index!
  [{:keys [out-dir]}]
  (let [missions (load-missions {:use-records? true})
        out-dir-file (io/file out-dir)
        records-file (io/file out-dir-file "mission_records.json")
        slots-file (io/file out-dir-file "typed_mission_slots.json")]
    (when (empty? missions)
      (throw (ex-info "refusing to write empty mission index"
                      {:out-dir out-dir
                       :load-source (last-load-source)})))
    (.mkdirs out-dir-file)
    (spit records-file (json/write-str missions))
    (spit slots-file (json/write-str (mapv typed-slots missions)))
    {:records (.getAbsolutePath records-file)
     :typed-slots (.getAbsolutePath slots-file)
     :count (count missions)}))

;; T-9b: parse-roots and --mission-roots CLI option removed. load-missions
;; reads from substrate-2 (futon1a) regardless of any roots arg; the
;; legacy filesystem-walk path is gone.

(defn- parse-type [value]
  (case (some-> value str/lower-case)
    ("pattern" ":pattern") :pattern
    ("both" ":both") :both
    :mission))

(defn- parse-int [s fallback]
  (try
    (Integer/parseInt (str s))
    (catch Throwable _
      fallback)))

(defn- parse-decimal [s fallback]
  (try
    (Double/parseDouble (str s))
    (catch Throwable _
      fallback)))

(defn- parse-slot [value]
  (when-let [[_ k v] (re-matches #"([^=]+)=(.+)" (or value ""))]
    [(keyword (str/replace (str/lower-case (str/trim k)) "-" "_"))
     (str/trim v)]))

(defn- parse-args [args]
  (loop [opts {:cmd :search
               :top-k 5
               :format :text
               :type :mission
               :slot-filters {}}
         remaining args]
    (if (empty? remaining)
      opts
      (let [[arg & more] remaining]
        (case arg
          "search" (recur opts more)
          "index" (recur (assoc opts :cmd :index) more)
          "record-action" (recur (assoc opts :cmd :record-action) more)
          "--out-dir" (recur (assoc opts :out-dir (first more)) (rest more))
          "--top-k" (recur (assoc opts :top-k (parse-int (first more) (:top-k opts))) (rest more))
          "--confidence-threshold" (recur (assoc opts :confidence-threshold
                                                 (parse-decimal (first more) 0.0))
                                          (rest more))
          "--type" (recur (assoc opts :type (parse-type (first more))) (rest more))
          "--format" (recur (assoc opts :format (keyword (str/lower-case (first more)))) (rest more))
          "--consumer-id" (recur (assoc opts :consumer-id (first more)) (rest more))
          "--query-id" (recur (assoc opts :query-id (first more)) (rest more))
          "--result-id" (recur (assoc opts :result-id (first more)) (rest more))
          "--action" (recur (assoc opts :action (keyword (first more))) (rest more))
          "--title" (recur (assoc opts :title (first more)) (rest more))
          "--slot" (if-let [[slot value] (parse-slot (first more))]
                     (recur (update opts :slot-filters assoc slot value) (rest more))
                     (exit! 2 (str "Bad --slot value: " (first more))))
          (if (:query opts)
            (recur (update opts :query #(str % " " arg)) more)
            (recur (assoc opts :query arg) more)))))))

(defn- render-text [payload]
  (println (str "query-id: " (:query_id payload)))
  (doseq [[idx result] (map-indexed vector (:results payload))]
    (println (format "%2d. [%.3f] %s (%s %s)"
                     (inc idx)
                     (double (:score result 0.0))
                     (:id result)
                     (or (:phase result) "")
                     (or (:date result) "")))
    (when-let [path (:path result)]
      (println (str "    " path)))
    (when-let [summary (:summary result)]
      (when (seq summary)
        (println (str "    " summary))))
    (when-let [confidence (:confidence result)]
      (println (format "    confidence=%.2f divergence=%s"
                       (double confidence)
                       (if (some? (:divergence_score result))
                         (format "%.2f" (double (:divergence_score result)))
                         "n/a"))))
    (println)))

(defn -main [& args]
  (let [{:keys [cmd format out-dir query query-id consumer-id result-id action title] :as opts}
        (parse-args args)]
    (case cmd
      :index
      (let [result (write-index! {:out-dir (or out-dir "resources/notions")})]
        (if (= format :json)
          (println (json/write-str result))
          (println (str "Indexed " (:count result)
                        " missions -> " (:records result)))))

      :search
      (do
        (when (str/blank? query)
          (exit! 2 "usage: mission-search <query> [--top-k N] [--slot key=value] [--format text|json]"))
        (let [payload (cond-> (search-missions query opts)
                        consumer-id (assoc :consumer_id consumer-id))]
          (if (= format :json)
            (println (json/write-str payload))
            (render-text payload))))

      :record-action
      (do
        (when (or (str/blank? query-id) (str/blank? consumer-id) (str/blank? result-id) (nil? action))
          (exit! 2 "usage: mission-search record-action --query-id ID --consumer-id ID --result-id ID --action :clicked|:read|:cited|:ignored|:rejected [--title TITLE]"))
        (let [event (record-consumer-event! {:query-id query-id
                                             :consumer-id consumer-id
                                             :result-id result-id
                                             :action action
                                             :title title})]
          (if (= format :json)
            (println (json/write-str event))
            (println (pr-str event))))))))
