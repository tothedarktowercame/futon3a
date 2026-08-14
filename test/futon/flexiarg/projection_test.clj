(ns futon.flexiarg.projection-test
  (:require [clojure.data.json :as json]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.test :refer [deftest is]]
            [futon.flexiarg.projection :as projection]))

(def futon3-root "/home/joe/code/futon3")

(defn- component-tree [component]
  {"name" (:name-key component)
   "children" (mapv component-tree (:children component))})

(deftest shared-conformance-corpus
  (let [corpus (json/read-str
                (slurp (io/file futon3-root
                                "test/fixtures/flexiarg-conformance.json")))]
    (doseq [{:strs [name source tree]} (get corpus "cases")]
      (let [block (slurp (io/file futon3-root source))]
        (is (= tree (mapv component-tree (projection/parse-tree block)))
            name)))))

(deftest nested-components-are-not-projection-peers
  (let [block (slurp (io/file futon3-root
                              "library/pattern-discipline/pattern-to-code-receipts.flexiarg"))
        clauses (projection/parse-components block)
        names (mapv :name-key clauses)
        then-clause (some #(when (= "then" (:name-key %)) %) clauses)]
    (is (= ["conclusion" "context" "if" "however" "then" "because" "next-steps"]
           names))
    (is (= ["evidence" "mechanism" "counterfactual"]
           (mapv :name-key (:children then-clause))))))

(defn- temp-dir []
  (doto (io/file (System/getProperty "java.io.tmpdir")
                 (str "flexiarg-projection-test-" (java.util.UUID/randomUUID)))
    .mkdirs))

(deftest parses-canonical-flexiarg-with-ordered-clauses
  (let [packets (projection/parse-file
                 "/home/joe/code/futon3/library/futon-theory/task-as-arrow.flexiarg"
                 {:futon3-root futon3-root})
        packet (first packets)]
    (is (= 1 (count packets)))
    (is (= :ok (:pattern/status packet)))
    (is (= "futon-theory/task-as-arrow" (:pattern/id packet)))
    (is (= "library/futon-theory/task-as-arrow.flexiarg"
           (:pattern/source-path packet)))
    (is (= ["⇒/箭"] (:pattern/sigils packet)))
    (is (some #{"task"} (:pattern/keywords packet)))
    (is (= ["futon-theory/curry-howard-operational"
            "futon-theory/proof-path"
            "futon-theory/four-types"]
           (:pattern/references packet)))
    (is (= "conclusion" (:name-key (first (:pattern/clauses packet)))))
    (is (= "BHK-ARROW-SEMANTICS"
           (:name (some #(when (= "bhk-arrow-semantics" (:slug %)) %)
                        (mapcat :children (:pattern/clauses packet))))))
    (is (str/includes? (:pattern/conclusion packet) "BHK arrow"))))

(deftest preserves-bespoke-clauses-as-children
  (let [packet (first (projection/parse-file
                       "/home/joe/code/futon3/library/structure/block-as-futonic-revolution.flexiarg"
                       {:futon3-root futon3-root}))
        top-level-names (set (map :name (:pattern/clauses packet)))
        child-names (set (map :name (mapcat :children (:pattern/clauses packet))))]
    (is (= :ok (:pattern/status packet)))
    (is (not (contains? top-level-names "APPLICATION-TO-WORKING-TREE")))
    (is (contains? child-names "APPLICATION-TO-WORKING-TREE"))
    (is (contains? child-names "ANTI-PATTERNS"))
    (is (contains? child-names "COMPOSITION-WITH-SIBLINGS"))))

(deftest anchored-extract-meta-does-not-match-body-prose
  (let [text (str "@flexiarg foo/bar\n"
                  "! conclusion:\n"
                  "  text\n"
                  "  + context: this body mentions @title fake-title but is not a directive\n")]
    (is (nil? (projection/extract-meta text "title")))))

(deftest malformed-blocks-produce-visible-errors
  (let [dir (temp-dir)
        file (io/file dir "bad.flexiarg")]
    (spit file "! conclusion:\n  missing header\n")
    (let [packet (first (projection/parse-file file {:futon3-root (.getPath dir)}))]
      (is (= :error (:pattern/status packet)))
      (is (= "missing" (:pattern/projection-version packet)))
      (is (= :missing-header (get-in packet [:pattern/error :kind]))))))

(deftest indented-block-headers-produce-visible-errors
  (let [dir (temp-dir)
        file (io/file dir "nested.multiarg")]
    (spit file (str "@arg demo/one\n"
                    "! conclusion:\n"
                    "  one\n"
                    "  @arg demo/two\n"
                    "  ! conclusion:\n"
                    "    two\n"))
    (let [packet (first (projection/parse-file file {:futon3-root (.getPath dir)}))]
      (is (= :error (:pattern/status packet)))
      (is (= "missing" (:pattern/projection-version packet)))
      (is (= :indented-header (get-in packet [:pattern/error :kind]))))))

(deftest projection-output-is-deterministic
  (let [dir (temp-dir)
        library-dir (doto (io/file dir "library" "demo") .mkdirs)
        file-a (io/file library-dir "alpha.flexiarg")
        file-b (io/file library-dir "beta.flexiarg")
        out-1 (io/file dir "out-1.edn")
        out-2 (io/file dir "out-2.edn")
        content-a (str "@flexiarg demo/alpha\n"
                       "@title Alpha\n"
                       "@sigils [🧪/測]\n"
                       "! conclusion:\n"
                       "  alpha\n"
                       "  + BECAUSE:\n"
                       "    because-a\n")
        content-b (str "@flexiarg demo/beta\n"
                       "@title Beta\n"
                       "! conclusion:\n"
                       "  beta\n"
                       "  + CONTEXT:\n"
                       "\n"
                       "    line-1\n"
                       "\n"
                       "    line-2\n"
                       "\n")]
    (spit file-a content-a)
    (spit file-b content-b)
    (let [packets-1 (projection/parse-roots {:futon3-root (.getPath dir)
                                             :source-roots ["library"]})
          packets-2 (projection/parse-roots {:futon3-root (.getPath dir)
                                             :source-roots ["library"]})]
      (projection/write-projections! out-1 packets-1)
      (projection/write-projections! out-2 packets-2)
      (is (= packets-1 packets-2))
      (is (= (slurp out-1) (slurp out-2)))
      (is (= "    line-1\n\n    line-2"
             (:text (some #(when (= "context" (:name-key %)) %)
                          (:pattern/clauses (second packets-1)))))))))
