(ns scripts.bootstrap-meme
  "Bootstrap meme.db from patterns-index.tsv.

   Reads the pattern library, filters hotwords by frequency band,
   creates concept entities and co-occurrence arrows.

   Run: cd futon3a && clj -M -m scripts.bootstrap-meme"
  (:require [meme.schema :as schema]
            [meme.core :as core]
            [meme.arrow :as arrow]
            [clojure.string :as str]
            [clojure.java.io :as io]
            [clojure.data.json :as json]
            [next.jdbc :as jdbc]))

;; --- Configuration ---

(def ^:private tsv-path "resources/notions/patterns-index.tsv")
(def ^:private min-freq 5)
(def ^:private max-freq 200)
(def ^:private iiching-prefix "iching/")

;; Stop words that appear as hotwords but carry no concept weight
(def ^:private stop-words
  #{"the" "a" "an" "and" "or" "but" "in" "on" "at" "to" "for" "of" "with"
    "by" "from" "is" "it" "its" "this" "that" "as" "not" "no" "be" "do"
    "so" "if" "s" "t" "re" "can" "than" "just" "how" "what" "when" "where"
    "who" "why" "does" "doesn" "don" "has" "have" "had" "was" "were" "been"
    "being" "are" "am" "will" "would" "could" "should" "may" "might" "must"
    "shall" "get" "got" "let" "also" "too" "very" "more" "most" "less"
    "each" "every" "any" "all" "both" "own" "same" "other" "such" "only"
    "here" "there" "then" "now" "about" "into" "over" "after" "before"
    "between" "under" "above" "out" "up" "down" "through" "during" "without"
    "within" "along" "across" "against" "beyond" "like" "make" "made"
    "new" "one" "two" "three" "first" "well" "way" "use" "used" "using"
    "rather" "keep" "keeps" "become" "becomes" "already" "means" "need"
    "needs" "allow" "allows" "turn" "turns" "requires" "provide" "provides"
    "enables" "creates" "produce" "instead" "e" "g"})

;; --- Parsing ---

(defn- parse-tsv-line [line]
  (let [fields (str/split line #"\t")]
    (when (>= (count fields) 5)
      {:pattern (nth fields 0)
       :tokipona (nth fields 1)
       :truth (nth fields 2)
       :rationale (nth fields 3)
       :hotwords (mapv str/trim (str/split (nth fields 4) #",\s*"))})))

(defn- load-patterns []
  (let [lines (str/split-lines (slurp tsv-path))]
    (->> (rest lines) ;; skip header
         (keep parse-tsv-line))))

;; --- Filtering ---

(defn- non-iiching? [pattern]
  (not (str/starts-with? (:pattern pattern) iiching-prefix)))

(defn- compute-frequencies [patterns]
  "Count hotword frequency across non-iiching patterns."
  (let [non-iiching (filter non-iiching? patterns)]
    (frequencies (mapcat :hotwords non-iiching))))

(defn- in-band? [freq-map word]
  (let [f (get freq-map word 0)]
    (and (>= f min-freq)
         (<= f max-freq)
         (not (stop-words word))
         (> (count word) 2))))

(defn- select-concepts [patterns]
  (let [freq-map (compute-frequencies patterns)
        all-words (keys freq-map)
        kept (filter #(in-band? freq-map %) all-words)
        dropped (remove #(in-band? freq-map %) all-words)]
    {:kept (sort kept)
     :dropped-count (count dropped)
     :freq-map freq-map}))

;; --- Co-occurrence ---

(defn- pattern-concepts [pattern kept-set]
  "Return the kept concepts that appear in this pattern's hotwords."
  (filter kept-set (:hotwords pattern)))

(defn- co-occurrence-pairs [patterns kept-set]
  "Find concept pairs that co-occur in the same pattern.
   Returns a map of [a b] -> count (a < b lexicographically)."
  (reduce
   (fn [acc pattern]
     (let [concepts (sort (distinct (pattern-concepts pattern kept-set)))]
       (reduce
        (fn [acc2 [a b]]
          (update acc2 [a b] (fnil inc 0)))
        acc
        (for [i (range (count concepts))
              j (range (inc i) (count concepts))]
          [(nth concepts i) (nth concepts j)]))))
   {}
   patterns))

;; --- Bootstrap ---

(defn- bootstrap! [ds patterns]
  (let [{:keys [kept freq-map]} (select-concepts patterns)
        kept-set (set kept)
        _ (println (str "Concepts to create: " (count kept)))

        ;; Create concept entities
        _ (println "Creating concept entities...")
        concept-entities
        (doall
         (for [concept kept]
           (do
             (core/ensure-entity! ds concept
                                  :kind "concept"
                                  :description (str "Hotword concept (freq "
                                                    (get freq-map concept) ")")
                                  :metadata {:source "patterns-index.tsv"
                                             :frequency (get freq-map concept)}))))

        ;; Create pattern entities
        _ (println "Creating pattern entities...")
        non-iiching (filter non-iiching? patterns)
        pattern-entities
        (doall
         (for [p non-iiching]
           (core/ensure-entity! ds (:pattern p)
                                :kind "pattern"
                                :description (:rationale p)
                                :metadata {:tokipona (:tokipona p)
                                           :truth (:truth p)})))

        ;; Create derivation arrows: concept ← pattern
        _ (println "Creating derivation arrows (concept ← pattern)...")
        derivation-count
        (atom 0)
        _ (doseq [p non-iiching
                  :let [pconcepts (pattern-concepts p kept-set)]
                  concept pconcepts]
            (arrow/assert-arrow! ds concept (:pattern p) :derivation
                                 :rationale (str "hotword of " (:pattern p))
                                 :created-by "bootstrap-meme")
            (swap! derivation-count inc))

        ;; Create analogy arrows: concept → concept (co-occurrence)
        _ (println "Creating analogy arrows (concept co-occurrence)...")
        pairs (co-occurrence-pairs patterns kept-set)
        ;; Only keep pairs with count >= 3 (meaningful co-occurrence)
        strong-pairs (filter (fn [[_ c]] (>= c 3)) pairs)
        _ (println (str "  Strong co-occurrence pairs (>=3): " (count strong-pairs)))
        analogy-count
        (atom 0)
        _ (doseq [[[a b] cnt] strong-pairs]
            (arrow/assert-arrow! ds a b :analogy
                                 :rationale (str "co-occur in " cnt " patterns")
                                 :payload {:co-occurrence-count cnt}
                                 :created-by "bootstrap-meme")
            (swap! analogy-count inc))]

    {:concepts (count kept)
     :patterns (count non-iiching)
     :derivation-arrows @derivation-count
     :analogy-arrows @analogy-count}))

;; --- Filter report ---

(defn- write-filter-report! [patterns result]
  (let [{:keys [kept dropped-count freq-map]} (select-concepts patterns)
        kept-set (set kept)
        report-path "data/bootstrap-filter-report.edn"
        all-words (keys freq-map)
        too-rare (filter #(< (get freq-map % 0) min-freq) all-words)
        too-common (filter #(> (get freq-map % 0) max-freq) all-words)
        too-short (filter #(<= (count %) 2) all-words)
        stopped (filter stop-words all-words)]
    (io/make-parents report-path)
    (spit report-path
          (pr-str {:generated-at (str (java.time.Instant/now))
                   :config {:min-freq min-freq
                            :max-freq max-freq
                            :iiching-excluded true
                            :stop-words-count (count stop-words)}
                   :summary {:total-hotwords (count all-words)
                             :kept (count kept)
                             :dropped dropped-count
                             :too-rare (count too-rare)
                             :too-common (count too-common)
                             :too-short (count too-short)
                             :stopped (count stopped)}
                   :kept-concepts (mapv (fn [c] {:concept c :freq (get freq-map c)})
                                       kept)
                   :result result}))
    (println (str "Filter report written to " report-path))))

;; --- Main ---

(defn -main [& _args]
  (println "=== Bootstrap meme.db from patterns-index.tsv ===")
  (let [patterns (load-patterns)
        _ (println (str "Loaded " (count patterns) " patterns"))
        ds (schema/ensure-db!)
        _ (println (str "Database ready: " (schema/db-path)))
        result (bootstrap! ds patterns)]
    (write-filter-report! patterns result)
    (println)
    (println "=== Bootstrap complete ===")
    (println (str "  Concepts:         " (:concepts result)))
    (println (str "  Patterns:         " (:patterns result)))
    (println (str "  Derivation arrows: " (:derivation-arrows result)))
    (println (str "  Analogy arrows:   " (:analogy-arrows result)))

    ;; Verify counts
    (let [entity-count (-> (jdbc/execute-one! ds ["SELECT count(*) as cnt FROM entities"])
                           :cnt)
          arrow-count (-> (jdbc/execute-one! ds ["SELECT count(*) as cnt FROM arrows"])
                          :cnt)]
      (println)
      (println (str "  Total entities in DB: " entity-count))
      (println (str "  Total arrows in DB:   " arrow-count))
      (when (< entity-count 100)
        (println "  WARNING: entity count < 100 (criterion 1 not met)"))
      (when (< arrow-count 200)
        (println "  WARNING: arrow count < 200 (criterion 1 not met)")))))
