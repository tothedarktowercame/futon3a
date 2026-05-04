(ns futon.peripheral.pattern-author-test
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [futon.peripheral.pattern-author :as pa]))

(defn- temp-target []
  (str (System/getProperty "java.io.tmpdir")
       "/pattern-author-test-"
       (java.util.UUID/randomUUID)
       ".flexiarg"))

(def canonical-draft
  "@flexiarg test/canonical-draft\n@title Canonical Draft\n
! conclusion: A minimal canonical draft has all the required parts.

  + context: Authors need a working baseline for the Sokoban contract.

  + IF: The author is composing a new pattern under the peripheral.

  + HOWEVER: A draft missing required structure is refused at submission.

  + THEN: The peripheral validates and lands the draft as a canonical .flexiarg.

  + BECAUSE: Sokoban semantics keep the library's invariants honest at write time.

  + NEXT-STEPS:\n    - Iterate the peripheral toward Level-2 (algorithm-prep) and beyond.\n")

(def summary-as-conclusion-draft
  "@flexiarg test/summary-alias-draft\n@title Summary Alias Test\n
! summary: A draft using `! summary:` as a conclusion alias must also land.

  + context: The conclusion-aliases include conclusion/claim/summary/instantiated-by.\n")

(def missing-header-draft
  "! conclusion: This draft has no @flexiarg header and should be refused.\n
  + context: The peripheral must catch the missing-header case at G4.\n")

(def missing-conclusion-draft
  "@flexiarg test/missing-conclusion-draft\n@title Missing Conclusion\n
  + context: This draft has a header but no conclusion.\n
  + NEXT-STEPS:\n    - The peripheral must refuse this with :missing-conclusion.\n")

(def non-canonical-clause-draft
  "@flexiarg test/non-canonical-draft\n@title Non-canonical Top-level\n
! conclusion: This draft has a conclusion but introduces a bespoke top-level clause.

  + context: The bespoke clause should be substructure, not top-level.

  + INVENTED-CLAUSE: This is a non-canonical top-level clause that must be flagged.\n")

(deftest accepts-canonical-draft
  (let [target (temp-target)
        result (pa/submit-draft! {:author "test" :target-path target :draft-body canonical-draft})]
    (try
      (is (true? (:landed? result)))
      (is (empty? (:violations result)))
      (is (= "test/canonical-draft" (:pattern-id result)))
      (is (.exists (io/file target)))
      (is (= canonical-draft (slurp target)))
      (finally
        (.delete (io/file target))))))

(deftest accepts-summary-as-conclusion-alias
  (let [target (temp-target)
        result (pa/submit-draft! {:author "test" :target-path target :draft-body summary-as-conclusion-draft})]
    (try
      (is (true? (:landed? result)))
      (is (empty? (:violations result)) (str "Should accept :summary as conclusion alias; got: " (:violations result)))
      (finally
        (when (.exists (io/file target)) (.delete (io/file target)))))))

(deftest refuses-missing-header
  (let [target (temp-target)
        result (pa/submit-draft! {:author "test" :target-path target :draft-body missing-header-draft})]
    (is (false? (:landed? result)))
    (is (some #(= :missing-flexiarg-header (:kind %)) (:violations result)))
    (is (not (.exists (io/file target)))
        "Sokoban: nothing on disk after refusal")))

(deftest refuses-missing-conclusion
  (let [target (temp-target)
        result (pa/submit-draft! {:author "test" :target-path target :draft-body missing-conclusion-draft})]
    (is (false? (:landed? result)))
    (is (some #(= :missing-conclusion (:kind %)) (:violations result)))
    (is (not (.exists (io/file target)))
        "Sokoban: nothing on disk after refusal")))

(deftest refuses-non-canonical-top-level-clause
  (let [target (temp-target)
        result (pa/submit-draft! {:author "test" :target-path target :draft-body non-canonical-clause-draft})]
    (is (false? (:landed? result)))
    (let [vs (:violations result)
          nc (filter #(= :non-canonical-clause (:kind %)) vs)]
      (is (seq nc))
      (is (str/includes? (str (first nc)) "INVENTED-CLAUSE")))
    (is (not (.exists (io/file target)))
        "Sokoban: nothing on disk after refusal")))

(def with-substructure-draft
  "@flexiarg test/with-substructure\n@title With Rulebook Substructure\n
! conclusion: A draft with rulebook-recognised substructure clauses (CHECK under THEN, etc.) is admitted.

  + context: The parser is flat; substructure parses as siblings.

  + THEN: Operate as the rulebook says.
    + CHECK: Substructure under THEN per the rulebook is admitted by the Sokoban even though the parser flattens it.
")

(deftest accepts-rulebook-substructure
  (let [target (temp-target)
        result (pa/submit-draft! {:author "test" :target-path target :draft-body with-substructure-draft})]
    (try
      (is (true? (:landed? result))
          (str "Should accept rulebook-recognised substructure (CHECK under THEN); got: " (:violations result)))
      (finally
        (when (.exists (io/file target)) (.delete (io/file target)))))))

(deftest sokoban-no-leakage-on-empty-draft
  (let [target (temp-target)
        result (pa/submit-draft! {:author "test" :target-path target :draft-body ""})]
    (is (false? (:landed? result)))
    (is (seq (:violations result)))
    (is (not (.exists (io/file target)))
        "Sokoban: empty draft produces no file")))
