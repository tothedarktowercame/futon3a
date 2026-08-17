(ns futon.flexiarg.projection
  "Canonical parser/projector for futon flexiarg files.

   The projection preserves ordered clause structure and emits a deterministic
   packet suitable for indexing, retrieval, and downstream derivations."
  (:require [clojure.data.json :as json]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(def ^:private default-output "resources/notions/pattern-projections.edn")
(def ^:private default-embed-json-output "resources/notions/pattern-embedding-records.json")
(def ^:private default-source-roots ["library" "holes"])
(def ^:private flexiarg-exts #{".flexiarg" ".multiarg"})
(def ^:private section-header-re #"^(\s*)[!+]\s+([^:]+):\s*(.*)$")
(def ^:private indented-block-header-re #"(?m)^\s+@(arg|flexiarg|multiarg)\s+\S+")
(def ^:private sigil-block-re #"\[[^\]]+\]")
(def ^:private sigil-token-re #"[^\s\[\]]+/[^\s\[\]]+")
(def ^:private directive-line-re #"(?m)^@([A-Za-z0-9_-]+)(?:\s+(.+?))?\s*$")
(def ^:private directive-ontology-name "flexiarg-directives.edn")

(defn- path-like? [line]
  (or (str/starts-with? line "@arg ")
      (str/starts-with? line "@flexiarg ")
      (str/starts-with? line "@multiarg ")))

(defn- slugify [text]
  (let [lower (str/lower-case (or text "component"))
        clean (str/replace lower #"[^a-z0-9]+" "-")
        trimmed (str/replace clean #"(^-+|-+$)" "")]
    (if (str/blank? trimmed) "component" trimmed)))

(defn- emoji-like? [s]
  (and (string? s)
       (not (str/blank? s))
       (some #(> (int %) 255) s)))

(defn- trim-empty-lines [lines]
  (let [trimmed-start (drop-while #(re-matches #"\s*" %) lines)]
    (reverse (drop-while #(re-matches #"\s*" %) (reverse trimmed-start)))))

(defn- sorted-map*
  [& kvs]
  (apply sorted-map kvs))

(defn extract-meta
  "Extract a directive value using an anchored regex so body prose cannot match."
  [text key]
  (let [key (name key)]
    (some->> (re-find (re-pattern (str "(?m)^@" key "\\s+(.+?)\\s*$")) text)
             second
             str/trim
             not-empty)))

(defn split-arg-blocks
  "Split text into flexiarg blocks. A new block starts at @arg/@flexiarg/@multiarg."
  [text]
  (let [lines (str/split-lines (or text ""))]
    (loop [remaining lines
           current []
           seen-header? false
           blocks []]
      (if-let [line (first remaining)]
        (let [rest-lines (rest remaining)
              starts-block? (path-like? line)]
          (cond
            (and starts-block? seen-header?)
            (recur rest-lines [line] true (conj blocks (str/join "\n" current)))

            starts-block?
            (recur rest-lines (conj current line) true blocks)

            :else
            (recur rest-lines (conj current line) seen-header? blocks)))
        (if seen-header?
          (conj blocks (str/join "\n" current))
          [(or text "")])))))

(defn- finish-section [{:keys [indent label lines]}]
  (let [clean (trim-empty-lines lines)
        original (str/trim label)]
    (sorted-map* :children []
                 :indent indent
                 :name original
                 :name-key (str/lower-case original)
                 :slug (slugify original)
                 :text (str/trimr (str/join "\n" clean)))))

(defn- sections-at-level [sections parent-indent]
  (loop [remaining sections
         nodes []]
    (if-let [section (first remaining)]
      (if (<= (:indent section) parent-indent)
        [nodes remaining]
        (let [[children tail] (sections-at-level (rest remaining)
                                                 (:indent section))]
          (recur tail (conj nodes (assoc section :children children)))))
      [nodes []])))

(defn- strip-parser-fields [component]
  (-> component
      (dissoc :indent)
      (update :children #(mapv strip-parser-fields %))))

(defn parse-tree
  "Parse all !/+ sections into the indentation-defined flexiarg tree."
  [block]
  (let [lines (str/split-lines (or block ""))]
    (loop [remaining lines
           current nil
           sections []]
      (if-let [line (first remaining)]
        (if-let [[_ whitespace label trailing] (re-matches section-header-re line)]
          (let [next-section (when current (finish-section current))
                new-lines (cond-> []
                            (and trailing (not (str/blank? trailing)))
                            (conj trailing))]
            (recur (rest remaining)
                   {:indent (count whitespace) :label label :lines new-lines}
                   (cond-> sections next-section (conj next-section))))
          (recur (rest remaining)
                 (if current
                   (update current :lines conj line)
                   current)
                 sections))
        (let [flat-sections (cond-> sections current (conj (finish-section current)))]
          (mapv strip-parser-fields
                (first (sections-at-level flat-sections -1))))))))

(defn parse-components
  "Return projection clauses in the historical root-plus-facets shape.

   Children below a facet remain nested and are never promoted to peer clauses."
  [block]
  (mapv identity
        (mapcat (fn [root] (cons root (:children root)))
                (parse-tree block))))

(defn- parse-list-directive [value]
  (let [trimmed (some-> value str/trim)]
    (if (str/blank? trimmed)
      []
      (let [inner (if (and (str/starts-with? trimmed "[")
                           (str/ends-with? trimmed "]"))
                    (subs trimmed 1 (dec (count trimmed)))
                    trimmed)]
        (->> (str/split inner #"\s+")
             (map str/trim)
             (remove str/blank?)
             vec)))))

(defn- parse-comma-or-space-list [value]
  (let [trimmed (some-> value str/trim)
        inner (if (and trimmed
                       (str/starts-with? trimmed "[")
                       (str/ends-with? trimmed "]"))
                (subs trimmed 1 (dec (count trimmed)))
                trimmed)]
    (if (str/blank? inner)
      []
      (->> (str/split inner #"[\s,]+")
           (map str/trim)
           (remove str/blank?)
           vec))))

(defn- parse-comma-list [value]
  (let [trimmed (some-> value str/trim)
        inner (if (and trimmed
                       (str/starts-with? trimmed "[")
                       (str/ends-with? trimmed "]"))
                (subs trimmed 1 (dec (count trimmed)))
                trimmed)]
    (if (str/blank? inner)
      []
      (->> (str/split inner #",")
           (map str/trim)
           (remove str/blank?)
           vec))))

(defn- parse-citation-list [value]
  (let [trimmed (some-> value str/trim)]
    (if (str/blank? trimmed)
      []
      (if (and (str/starts-with? trimmed "[")
               (str/ends-with? trimmed "]"))
        (try
          (let [parsed (edn/read-string trimmed)]
            (if (sequential? parsed) (mapv str parsed) [trimmed]))
          (catch Exception _ (parse-comma-list trimmed)))
        (parse-comma-list trimmed)))))

(defn- parse-directive-value [value-type value]
  (cond
    (contains? #{:pattern-id-list :subject-code-list :list} value-type)
    (parse-comma-or-space-list value)

    (= :word-list value-type) (parse-comma-list value)
    (= :citation-list value-type) (parse-citation-list value)

    ;; Scalar, id, and pattern-id values deliberately retain their historical
    ;; string representation. Non-standard value types never reach this fn.
    :else value))

(defn load-directive-ontology
  "Load the semantic directive gate from the futon3 data file."
  [futon3-root]
  (let [requested (io/file futon3-root directive-ontology-name)
        path (if (.isFile requested)
               requested
               (io/file (or (System/getenv "FUTON3_ROOT") "../futon3")
                        directive-ontology-name))
        ontology (edn/read-string (slurp path))]
    (when-not (map? (:directives ontology))
      (throw (ex-info "Invalid flexiarg directive ontology"
                      {:path (.getPath path)})))
    ontology))

(defn- raw-directives [block]
  (mapv (fn [[_ label value]]
          [(keyword label) (some-> value str/trim)])
        (re-seq directive-line-re (or block ""))))

(defn- project-directives [block ontology]
  (reduce
   (fn [{:keys [directives] :as acc} [label value]]
     (if-let [{:keys [status] value-type :value} (get-in ontology [:directives label])]
       (if (= :standard status)
         (assoc acc :directives
                (assoc directives label (parse-directive-value value-type value)))
         (update-in acc [:known-not-ingested label] (fnil inc 0)))
       (update-in acc [:unknown label] (fnil inc 0))))
   {:directives {} :known-not-ingested {} :unknown {}}
   (raw-directives block)))

(defn report-unknown-directives!
  "Print loud per-file reports for directive labels absent from the ontology."
  [packets]
  (let [unknown-packets (filter #(seq (get-in % [:pattern/directive-report :unknown]))
                                packets)]
    (doseq [packet unknown-packets]
      (println (format "FLEXIARG DIRECTIVE UNKNOWN %s: %s"
                       (:pattern/source-path packet)
                       (str/join ", "
                                 (map (fn [[label n]] (str "@" (name label) "=" n))
                                      (sort-by key (get-in packet [:pattern/directive-report
                                                                  :unknown])))))))
    unknown-packets))

(defn report-directives!
  "Print one pass summary for known non-standard directives and loud per-file
   reports for labels missing from the ontology. Returns report counts."
  [packets]
  (let [known (apply merge-with + (map #(get-in % [:pattern/directive-report
                                                    :known-not-ingested] {}) packets))
        unknown-packets (report-unknown-directives! packets)]
    (when (seq known)
      (println (format "FLEXIARG DIRECTIVES known-not-ingested: %d occurrences across %d directives"
                       (reduce + (vals known)) (count known))))
    {:known-not-ingested (reduce + 0 (vals known))
     :known-directives (count known)
     :unknown-files (count unknown-packets)
     :unknown-occurrences (reduce + 0
                                  (mapcat #(vals (get-in % [:pattern/directive-report
                                                           :unknown]))
                                          unknown-packets))}))

(defn- parse-sigil-token [token]
  (let [[emoji hanzi] (some-> token (str/split #"/" 2))]
    (when (and emoji hanzi)
      (let [emoji (str/trim emoji)
            hanzi (str/trim hanzi)]
        (when (emoji-like? emoji)
          (str emoji "/" hanzi))))))

(defn- sigils-in-text [text]
  (->> (re-seq sigil-block-re (or text ""))
       (mapcat #(re-seq sigil-token-re %))
       (keep parse-sigil-token)
       (remove str/blank?)
       distinct
       vec))

(defn- relative-path [^java.io.File file futon3-root]
  (let [absolute (.getCanonicalPath file)
        root-file (when futon3-root
                    (-> futon3-root io/file .getCanonicalFile))
        root-path (when root-file (.getPath root-file))
        prefix (when root-path
                 (str root-path java.io.File/separator))]
    (cond
      (and prefix (str/starts-with? absolute prefix))
      (subs absolute (count prefix))

      :else
      (.getPath file))))

(defn- error-packet [^java.io.File file futon3-root kind message]
  (sorted-map* :pattern/error (sorted-map* :kind kind
                                           :message message)
               :pattern/id nil
               :pattern/projection-version "missing"
               :pattern/source-path (relative-path file futon3-root)
               :pattern/status :error))

(def conclusion-aliases
  "Clause name-keys recognised as syntactic sugar for the canonical
   `! conclusion:` slot. The pattern's *required* top-level claim may
   appear under any of these names; the projection treats them as
   equivalent for the purposes of `:pattern/conclusion` and the
   downstream `:pattern/has-conclusion` invariant."
  #{"conclusion" "claim" "summary" "instantiated-by"})

(defn- first-conclusion [components]
  (some (fn [component]
          (when (contains? conclusion-aliases (:name-key component))
            (:text component)))
        components))

(defn parse-block
  "Parse one flexiarg block into the canonical packet."
  ([file block]
   (parse-block file block {}))
  ([file block {:keys [futon3-root directive-ontology]}]
   (let [file (io/file file)
         futon3-root (or futon3-root "../futon3")
         ontology (or directive-ontology (load-directive-ontology futon3-root))
         pattern-id (or (extract-meta block "arg")
                        (extract-meta block "flexiarg")
                        (extract-meta block "multiarg"))]
     (cond
       (re-find indented-block-header-re block)
       (error-packet file futon3-root :indented-header
                     "Pattern block headers must start at column zero")

       (not pattern-id)
       (error-packet file futon3-root :missing-header
                     "Block is missing @arg/@flexiarg/@multiarg header")

       :else
       (let [components (parse-components block)
             direct-sigils (parse-list-directive (extract-meta block "sigils"))
             all-sigils (vec (distinct (concat direct-sigils
                                              (sigils-in-text block))))
             projection (project-directives block ontology)
             directives (:directives projection)]
         (sorted-map* :pattern/clauses components
                      :pattern/conclusion (first-conclusion components)
                      :pattern/directive-report (dissoc projection :directives)
                      :pattern/directives (into (sorted-map) directives)
                      :pattern/id pattern-id
                      :pattern/keywords (vec (:keywords directives))
                      :pattern/projection-version "flexiarg-v0"
                      ;; Compatibility projection only. @references is a
                      ;; catalogued :split directive and is therefore absent
                      ;; from :pattern/directives; existing readers retain the
                      ;; raw reference list until the per-edge migration.
                      :pattern/references (parse-list-directive
                                           (extract-meta block "references"))
                      :pattern/scores (sorted-map)
                      :pattern/sigils all-sigils
                      :pattern/source-path (relative-path file futon3-root)
                      :pattern/sources []
                      :pattern/status :ok
                      :pattern/title (or (:title directives) pattern-id)
                      :pattern/typed-slots nil))))))

(defn parse-file
  "Parse one .flexiarg/.multiarg file into one or more canonical packets."
  ([path]
   (parse-file path {}))
  ([path {:keys [futon3-root report?] :as opts}]
   (let [file (io/file path)]
     (try
       (let [packets (->> (split-arg-blocks (slurp file))
                          (mapv #(parse-block file % opts)))]
         ;; Reporting is a BATCH concern and defaults OFF. It used to default ON,
         ;; so any caller that did not know to pass :report? false printed one
         ;; summary line PER FILE -- 889 of the 1151 library files carry a
         ;; non-standard directive, so a full pass printed 889 summary lines
         ;; instead of the one the standard asks for. parse-roots and the futon3c
         ;; watcher adapter both passed :report? false and were fine; every other
         ;; caller, including a plain dry run, was not.
         (when (true? report?)
           (report-directives! packets))
         packets)
       (catch Exception ex
         [(sorted-map* :pattern/error (sorted-map* :kind :read-error
                                                   :message (.getMessage ex))
                       :pattern/id nil
                       :pattern/projection-version "missing"
                       :pattern/source-path (relative-path file futon3-root)
                       :pattern/status :error)])))))

(defn resolve-futon3-root
  "Resolve the source repo root. Prefers explicit option, then env, then ../futon3."
  [{:keys [futon3-root]}]
  (let [candidate (or futon3-root
                      (System/getenv "FUTON3_ROOT")
                      "../futon3")]
    (.getCanonicalPath (io/file candidate))))

(defn resolve-source-roots
  "Resolve source roots under the futon3 repo root."
  [futon3-root source-roots]
  (->> (or (seq source-roots) default-source-roots)
       (map #(io/file futon3-root %))
       (filter #(.exists ^java.io.File %))
       (map #(.getCanonicalFile ^java.io.File %))
       distinct
       vec))

(defn source-files
  "List all flexiarg files under the configured roots, sorted deterministically."
  [futon3-root source-roots]
  (let [roots (resolve-source-roots futon3-root source-roots)]
    (->> roots
         (mapcat file-seq)
         (filter #(.isFile ^java.io.File %))
         (filter (fn [^java.io.File file]
                   (contains? flexiarg-exts
                              (str/lower-case
                               (or (re-find #"\.[^.]+$" (.getName file)) "")))))
         (sort-by #(.getCanonicalPath ^java.io.File %))
         vec)))

(defn parse-roots
  "Project all flexiarg files under the configured roots."
  [{:keys [source-roots] :as opts}]
  (let [root (resolve-futon3-root opts)
        ontology (load-directive-ontology root)
        packets (->> (source-files root source-roots)
                     (mapcat #(parse-file % {:futon3-root root
                                             :directive-ontology ontology
                                             :report? false}))
                     (sort-by (fn [packet]
                                [(:pattern/source-path packet)
                                 (or (:pattern/id packet) "")]))
                     vec)]
    (report-directives! packets)
    packets))

(defn write-projections!
  "Write the projected packets to an EDN file deterministically."
  [path packets]
  (let [file (io/file path)]
    (.mkdirs (.getParentFile file))
    (spit file (str (pr-str packets) "\n"))
    (.getCanonicalPath file)))

(defn- normalize-embedding-text [s]
  (-> (or s "")
      (str/replace #"\s+" " ")
      str/trim))

(defn packet->embedding-record
  "Project a canonical packet into the JSON input shape consumed by
   `scripts/embed_text.py --json`."
  [packet]
  (when (= :ok (:pattern/status packet))
    (let [title (or (:pattern/title packet) (:pattern/id packet))
          clause-texts (->> (:pattern/clauses packet)
                            (map :text)
                            (remove str/blank?))
          text (normalize-embedding-text
                (str/join " " (cons title clause-texts)))]
      {:id (:pattern/id packet)
       :title title
       :source (:pattern/source-path packet)
       :text text})))

(defn write-embedding-records!
  "Write JSON embedding-input records derived from canonical packets."
  [path packets]
  (let [file (io/file path)
        records (->> packets
                     (keep packet->embedding-record)
                     vec)]
    (.mkdirs (.getParentFile file))
    (spit file (json/write-str records))
    {:path (.getCanonicalPath file)
     :count (count records)}))

(defn- usage []
  (str/join
   "\n"
   ["Usage: clj -M -m futon.flexiarg.projection [--futon3-root PATH]"
    "                                            [--source-root ROOT]*"
    "                                            [--out PATH]"
    "                                            [--embed-json-out PATH]"
    ""
    "Defaults:"
    "  --futon3-root $FUTON3_ROOT or ../futon3"
    "  --source-root library"
    "  --source-root holes"
    "  --out resources/notions/pattern-projections.edn"
    "  --embed-json-out resources/notions/pattern-embedding-records.json"]))

(defn- parse-args [args]
  (loop [opts {:source-roots []
               :out default-output
               :embed-json-out default-embed-json-output}
         remaining args]
    (if-let [arg (first remaining)]
      (case arg
        "--futon3-root" (recur (assoc opts :futon3-root (second remaining)) (nnext remaining))
        "--source-root" (recur (update opts :source-roots conj (second remaining)) (nnext remaining))
        "--out" (recur (assoc opts :out (second remaining)) (nnext remaining))
        "--embed-json-out" (recur (assoc opts :embed-json-out (second remaining)) (nnext remaining))
        "-h" (recur (assoc opts :help? true) (rest remaining))
        "--help" (recur (assoc opts :help? true) (rest remaining))
        (throw (ex-info (str "Unknown argument " arg) {:arg arg})))
      opts)))

(defn -main
  [& args]
  (let [{:keys [help? out embed-json-out] :as opts} (parse-args args)]
    (if help?
      (println (usage))
      (let [packets (parse-roots opts)
            written (write-projections! out packets)
            embed-result (when embed-json-out
                           (write-embedding-records! embed-json-out packets))]
        (when embed-result
          (println (format "Wrote %d embedding-input records to %s"
                           (:count embed-result)
                           (:path embed-result))))
        (println (format "Wrote %d pattern projections to %s"
                         (count packets)
                         written))))))
