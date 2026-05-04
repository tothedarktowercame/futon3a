(ns futon.flexiarg.projection
  "Canonical parser/projector for futon flexiarg files.

   The projection preserves ordered clause structure and emits a deterministic
   packet suitable for indexing, retrieval, and downstream derivations."
  (:require [clojure.java.io :as io]
            [clojure.string :as str]))

(def ^:private default-output "resources/notions/pattern-projections.edn")
(def ^:private default-source-roots ["library" "holes"])
(def ^:private flexiarg-exts #{".flexiarg" ".multiarg"})
(def ^:private section-header-re #"^\s*[!+]\s+([^:]+):\s*(.*)$")
(def ^:private sigil-block-re #"\[[^\]]+\]")
(def ^:private sigil-token-re #"[^\s\[\]]+/[^\s\[\]]+")

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

(defn parse-components
  "Parse all !/+ sections from a block, preserving original clause names while
   also emitting a lower-cased lookup key and slug."
  [block]
  (let [lines (str/split-lines (or block ""))]
    (loop [remaining lines
           current nil
           sections []]
      (if-let [line (first remaining)]
        (if-let [[_ label trailing] (re-matches section-header-re line)]
          (let [next-section (when current
                               (let [clean (trim-empty-lines (:lines current))
                                     original (str/trim (:label current))]
                                 (sorted-map* :name original
                                              :name-key (str/lower-case original)
                                              :slug (slugify original)
                                              :text (str/trimr (str/join "\n" clean)))))
                new-lines (cond-> []
                            (and trailing (not (str/blank? trailing)))
                            (conj trailing))]
            (recur (rest remaining)
                   {:label label :lines new-lines}
                   (cond-> sections next-section (conj next-section))))
          (recur (rest remaining)
                 (if current
                   (update current :lines conj line)
                   current)
                 sections))
        (let [final-section (when current
                              (let [clean (trim-empty-lines (:lines current))
                                    original (str/trim (:label current))]
                                (sorted-map* :name original
                                             :name-key (str/lower-case original)
                                             :slug (slugify original)
                                             :text (str/trimr (str/join "\n" clean)))))]
          (cond-> sections final-section (conj final-section)))))))

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

(defn- parse-keywords [value]
  (->> (str/split (or value "") #",")
       (map str/trim)
       (remove str/blank?)
       vec))

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

(defn- first-by-key [components k]
  (some (fn [component]
          (when (= k (:name-key component))
            (:text component)))
        components))

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
  ([file block {:keys [futon3-root]}]
   (let [file (io/file file)
         pattern-id (or (extract-meta block "arg")
                        (extract-meta block "flexiarg")
                        (extract-meta block "multiarg"))]
     (if-not pattern-id
       (error-packet file futon3-root :missing-header
                     "Block is missing @arg/@flexiarg/@multiarg header")
       (let [components (parse-components block)
             direct-sigils (parse-list-directive (extract-meta block "sigils"))
             all-sigils (vec (distinct (concat direct-sigils
                                              (sigils-in-text block))))
             directives (reduce
                         (fn [acc key]
                           (let [value (extract-meta block key)]
                             (cond
                               (and value (= key "keywords"))
                               (assoc acc :keywords (parse-keywords value))

                               (and value (= key "references"))
                               (assoc acc :references (parse-list-directive value))

                               (and value (= key "sigils"))
                               (assoc acc :sigils (parse-list-directive value))

                               value
                               (assoc acc (keyword key) value)

                               :else
                               acc)))
                         {}
                         ["title" "sigils" "keywords" "references" "pattern-ref"
                          "audience" "tone" "factor" "style" "energy"])]
         (sorted-map* :pattern/clauses components
                      :pattern/conclusion (first-conclusion components)
                      :pattern/directives (into (sorted-map) directives)
                      :pattern/id pattern-id
                      :pattern/keywords (vec (:keywords directives))
                      :pattern/projection-version "flexiarg-v0"
                      :pattern/references (vec (:references directives))
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
  ([path {:keys [futon3-root] :as opts}]
   (let [file (io/file path)]
     (try
       (->> (split-arg-blocks (slurp file))
            (mapv #(parse-block file % opts)))
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
  [{:keys [futon3-root source-roots] :as opts}]
  (let [root (resolve-futon3-root opts)]
    (->> (source-files root source-roots)
         (mapcat #(parse-file % {:futon3-root root}))
         (sort-by (fn [packet]
                    [(:pattern/source-path packet)
                     (or (:pattern/id packet) "")]))
         vec)))

(defn write-projections!
  "Write the projected packets to an EDN file deterministically."
  [path packets]
  (let [file (io/file path)]
    (.mkdirs (.getParentFile file))
    (spit file (str (pr-str packets) "\n"))
    (.getCanonicalPath file)))

(defn- usage []
  (str/join
   "\n"
   ["Usage: clj -M -m futon.flexiarg.projection [--futon3-root PATH]"
    "                                            [--source-root ROOT]*"
    "                                            [--out PATH]"
    ""
    "Defaults:"
    "  --futon3-root $FUTON3_ROOT or ../futon3"
    "  --source-root library"
    "  --source-root holes"
    "  --out resources/notions/pattern-projections.edn"]))

(defn- parse-args [args]
  (loop [opts {:source-roots []
               :out default-output}
         remaining args]
    (if-let [arg (first remaining)]
      (case arg
        "--futon3-root" (recur (assoc opts :futon3-root (second remaining)) (nnext remaining))
        "--source-root" (recur (update opts :source-roots conj (second remaining)) (nnext remaining))
        "--out" (recur (assoc opts :out (second remaining)) (nnext remaining))
        "-h" (recur (assoc opts :help? true) (rest remaining))
        "--help" (recur (assoc opts :help? true) (rest remaining))
        (throw (ex-info (str "Unknown argument " arg) {:arg arg})))
      opts)))

(defn -main
  [& args]
  (let [{:keys [help? out] :as opts} (parse-args args)]
    (if help?
      (println (usage))
      (let [packets (parse-roots opts)
            written (write-projections! out packets)]
        (println (format "Wrote %d pattern projections to %s"
                         (count packets)
                         written))))))
