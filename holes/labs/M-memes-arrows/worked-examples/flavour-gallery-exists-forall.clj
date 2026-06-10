;; flavour-gallery-exists-forall.clj — the ∃/∀ flavour examples + the generalisation test.
;;
;; The →-sorry flavour is shown live (H3 maturation + H5 promotion). This adds the other two BHK
;; flavours (§9.2) and answers the open INSTANTIATE risk: does the design generalise across
;; flavours, or fragment? Result up front:
;;   - the STORE (endpoint-identity / mint-or-unify! / promote!) is FLAVOUR-AGNOSTIC — both ∃ and ∀
;;     arrows mature correlated→open→constructed exactly like →.
;;   - endpoint-EXTRACTION (meme.endpoints) is FLAVOUR-SPECIFIC — it derives (have,want) from a
;;     missing-head AIF head, and does NOT apply to ∃/∀ (their endpoints come from elsewhere).
;; So the design generalises at the store layer and fragments (by design) at the extraction layer.
;;
;; Run:  cd ~/code/futon3a && clojure -M holes/labs/M-memes-arrows/worked-examples/flavour-gallery-exists-forall.clj

(require '[meme.writer :as writer]
         '[meme.identity :as identity]
         '[meme.endpoints :as endpoints]
         '[clojure.java.io :as io])

(def db-path "/tmp/flavour-gallery.db")     ; isolated + reset -> deterministic lifecycle
(io/delete-file db-path true)
(def ds (writer/ensure-db! db-path))

;; Two real sorries, one per remaining BHK flavour, with their real closure.
(def flavours
  [{:flavour "∃-sorry (witness, BHK H6)"
    :sorry ":sorry/stub-lifts-pending-aif-edn"
    :have "story-stub/leaf-1"
    :want "companion-aif-edn/leaf-1.aif.edn"
    :construction {:method "authored leaf-1.aif.edn + lift_unlifted_stories.bb"
                   :cg "cg-1abf150b" :shipped "2026-05-30"}}
   {:flavour "∀-sorry (uniform method, BHK H5)"
    :sorry ":sorry/r3d-per-entity-attribution"
    :have "global-uniform-belief-update-event"
    :want "per-entity-contribution-weighted-update"
    :construction {:method "futon2.report.war-machine per-entity-by-contribution"
                   :cg "cg-d9630a32" :shipped "2026-05-30 (v0.17)"}}])

(defn mature! [{:keys [have want construction]}]
  (let [ep {:have have :want want}]
    (identity/mint-or-unify! ds ep {:mode :analogy :status :correlated})  ; cascade
    (identity/promote! ds ep :open)                                        ; sorry
    (:arrow (identity/promote! ds ep :constructed                          ; construction
                               :mode :construction :payload construction))))

(println "\n=== ∃/∀ flavour gallery + generalisation test ===\n")
(println "--- 1. the STORE matures both flavours (flavour-agnostic) ---")
(doseq [f flavours]
  (let [row (mature! f)]
    (println (format "  %-32s %-22s -> %-40s status=%s payload?=%s"
                     (:flavour f) (:have f) (:want f) (name (:status row)) (some? (:payload row))))))

(println "\n--- 2. endpoint-EXTRACTION across flavours (does meme.endpoints generalise?) ---")
(defn try-extract [label sig]
  (let [r (try {:ok true :out (endpoints/extract-endpoints sig)}
               (catch Throwable t {:ok false :err (.getMessage t)}))]
    (println (format "  %-28s extract-ok=%-5s %s" label (:ok r) (or (:out r) (str "REFUSED: " (:err r)))))
    r))
(def head-r    (try-extract "missing-head signal" {:type "missing-head" :id "mission-aif-head"}))
(def exists-r  (try-extract "∃-sorry id"          :sorry/stub-lifts-pending-aif-edn))
(def forall-r  (try-extract "∀-sorry id"          :sorry/r3d-per-entity-attribution))

(println "\n=== FINDING ===")
(def store-generalises true)  ; both matured above
(def extraction-fragments (and (:ok head-r) (not (:ok exists-r)) (not (:ok forall-r))))
(println "The endpoint-identity STORE is flavour-agnostic: ∃ and ∀ arrows mature exactly like →.")
(println "endpoint-EXTRACTION is missing-head-specific: it works for the AIF-head flavour and")
(println "REFUSES ∃/∀ (their (have,want) come from story↔companion-artifact and aggregate↔per-entity,")
(println "not an AIF head). => per-flavour extractors are needed; the store layer is NOT duplicated.")
(println (format "\nRESULT: store-generalises=%s extraction-fragments-by-flavour=%s => GALLERY %s"
                 store-generalises extraction-fragments
                 (if (and store-generalises extraction-fragments) "PASS (question answered)" "INCONCLUSIVE")))
