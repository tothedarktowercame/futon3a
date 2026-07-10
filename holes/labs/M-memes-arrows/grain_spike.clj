;; grain_spike.clj — M-memes-arrows-patterns-diagrams DERIVE/VERIFY spike.
;; "Build some examples, otherwise we're talking about vague concepts" (Joe, 2026-06-09).
;;
;; Writes THREE real §7-status examples through the real meme.* API into a scratch
;; SQLite DB, reads them back, and tests the grain question: can `arrows` alone
;; distinguish :open (RHS specified, no construction) from :correlated (RHS reached
;; empirically, no construction) — both have payload=nil — or does the lifecycle need
;; the proposals/promotions tables the schema already ships?
;;
;; Run:  cd ~/code/futon3a && clojure -M holes/labs/M-memes-arrows/grain_spike.clj
;; Throwaway DB at /tmp/meme-grain-spike.db (deleted + recreated each run).

(require '[meme.schema :as schema]
         '[meme.core :as core]
         '[meme.arrow :as arrow]
         '[next.jdbc :as jdbc]
         '[clojure.java.io :as io])

(def db-path "/tmp/meme-grain-spike.db")
(io/delete-file db-path true)
(def ds (schema/datasource db-path))
(schema/ensure-db! ds)

(println "\n=== Writing 3 real §7-status examples through the real meme API ===\n")

;; -------------------------------------------------------------------------
;; EXAMPLE 1 — :correlated (cascade). REAL DATA: top co-application pair from
;; pattern_phylogeny (co-app weight 8 across missions). Two patterns that co-fire;
;; NO method produces the RHS from the LHS. RHS reached empirically.
;; -------------------------------------------------------------------------
(def corr
  (arrow/assert-arrow! ds
    "construct-an-explicit-witness" "reduce-to-known-result" :analogy
    :rationale "Co-applied across 8 missions (pattern_phylogeny co-application). Correlation only — no construction turns a witness into a reduction."
    :scope-tags ["pattern-cascade"]
    :created-by "grain-spike"))
;; attestation-derived confidence (8 co-apps, no construction): bump off the 0.3 default
(arrow/strengthen-arrow! ds (:id corr) 0.3) ; -> 0.6

;; -------------------------------------------------------------------------
;; EXAMPLE 2 — :open (hole/sorry). REAL DATA: :sorry/r3a-likelihood-coupling-density
;; in its ORIGINAL open state. RHS is SPECIFIED (the predictor to build); construction
;; ABSENT. Modeled TWO ways to test the grain: (a) as an arrows row, (b) as a proposal.
;; -------------------------------------------------------------------------
(def lhs "coupling-density-channel-measured-structurally") ; coupling edges / max edges
(def rhs "predict-coupling-density-from-belief-mass")       ; the codomain to construct
;; (a) as an arrow: payload=nil, mode :untyped ("asserted without construction")
(def open-arrow
  (arrow/assert-arrow! ds lhs rhs :untyped
    :rationale "sorry/r3a-likelihood-coupling-density (OPEN): RHS specified (predict-coupling-density from belief mass on cross-section-edge entities); construction absent."
    :scope-tags ["sorry" "wm-channel"]
    :created-by "grain-spike"))
;; (b) as a proposal: kind 'arrow', status pending — the schema's native "grounded, not yet a fact"
(def src-ent (core/ensure-entity! ds lhs))
(def tgt-ent (core/ensure-entity! ds rhs))
(def open-proposal
  (jdbc/execute-one! ds
    ["INSERT INTO proposals (id, kind, target_id, status, score, method, evidence, created_by, created_at, updated_at)
      VALUES (?,?,?,?,?,?,?,?,datetime('now'),datetime('now'))"
     "prop-coupling-density" "arrow" (:id tgt-ent) "pending" 0.0
     "sorry-miner"
     (str "{:lhs \"" lhs "\" :rhs \"" rhs "\" :construction nil :demand :rhs-specified-no-construction}")
     "grain-spike"]
    {:return-keys true}))

;; -------------------------------------------------------------------------
;; EXAMPLE 3 — :constructed (wiring diagram). REAL DATA:
;; :sorry/r3a-likelihood-support-coverage RESOLVED — construction shipped as
;; futon2.aif.belief/predict-support-coverage (cg-17bbaa01). RHS reached BY A METHOD.
;; Modeled as an arrow WITH payload + mode :construction, then a promotion-to-fact row.
;; -------------------------------------------------------------------------
(def constructed
  (arrow/assert-arrow! ds
    "belief-mass-on-supports-tagged-cohort" "support-coverage-channel" :construction
    :payload {:construction "futon2.aif.belief/predict-support-coverage"
              :cg "cg-17bbaa01-33fc-4a31-bcc6-568cc047f093"
              :shipped "2026-05-26"}
    :rationale "sorry/r3a-likelihood-support-coverage (CONSTRUCTED): predict-support-coverage filters belief by the supports-tagged cohort. Construction present."
    :scope-tags ["sorry" "wm-channel"]
    :created-by "grain-spike"))
(arrow/strengthen-arrow! ds (:id constructed) 0.2) ; payload present -> 0.7 default, bump to 0.9
;; promotion: explicit crossing to the facts store (substrate-2 code/v05/sorry vertex)
(jdbc/execute-one! ds
  ["INSERT INTO promotions (id, proposal_id, promoted_kind, target_id, decided_by, rationale, created_at)
    VALUES (?,?,?,?,?,?,datetime('now'))"
   "promo-support-coverage" "prop-support-coverage" "fact"
   (:target-id constructed) "operator"
   "Construction shipped + verified; promote arrow to substrate-2 code/v05/sorry fact-side vertex."]
  {:return-keys true})

;; -------------------------------------------------------------------------
;; READ BACK + OBSERVE
;; -------------------------------------------------------------------------
(println "--- arrows table (read back via meme.arrow/list-arrows) ---")
(doseq [a (arrow/list-arrows ds)]
  (println (format "  %-12s %-44s -> %-40s payload?=%s conf=%.2f"
                   (name (:mode a))
                   (subs (str (:source_id a)) 0 (min 12 (count (str (:source_id a)))))
                   (subs (str (:target_id a)) 0 (min 12 (count (str (:target_id a)))))
                   (some? (:payload a)) (double (:confidence a)))))

(println "\n--- §7 status as resolved by the data (the grain answer) ---")
(defn classify [a]
  (let [payload? (some? (:payload a))
        mode (:mode a)]
    (cond
      payload?                         :constructed
      (= :untyped mode)                :open-or-correlated  ; <-- ambiguous in arrows alone!
      :else                            :correlated)))
(doseq [a (arrow/list-arrows ds)]
  (println (format "  %-22s mode=%-12s payload?=%s  => %s"
                   (:rationale-tag a "") (name (:mode a)) (some? (:payload a)) (classify a))))

(println "\n--- proposals table (the :open home) ---")
(doseq [p (jdbc/execute! ds ["SELECT id,kind,status,method,evidence FROM proposals"])]
  (println "  " (:proposals/id p) (:proposals/status p) (:proposals/method p) "\n      evidence:" (:proposals/evidence p)))

(println "\n--- promotions table (the :constructed -> fact crossing) ---")
(doseq [p (jdbc/execute! ds ["SELECT id,promoted_kind,decided_by,rationale FROM promotions"])]
  (println "  " (:promotions/id p) "->" (:promotions/promoted_kind p) "by" (:promotions/decided_by p)))

(println "\n=== GRAIN OBSERVATION ===")
(println "arrows.payload cleanly splits :constructed (payload) from not-constructed (nil).")
(println "BUT :open vs :correlated are BOTH payload=nil + mode-ish — arrows alone can't")
(println "distinguish 'RHS is an aspirational goal' (:open) from 'RHS is an observed node'")
(println "(:correlated). The schema's OWN answer: :open lives in `proposals` (pending,")
(println "RHS named, no construction); :correlated is an active `arrows` row; :constructed")
(println "is an `arrows` row WITH payload + a `promotions` row to the fact store.")
(println "=> Lifecycle: proposal(:open) --supply construction--> arrow(:constructed) --> promotion(fact).")
(println "=> :correlated is a SEPARATE thing; a high-attestation correlated arrow with no")
(println "   construction is what SEEDS an :open proposal (§7's attestation×no-construction=demand).")
(println "\nspike ok ->" db-path)
