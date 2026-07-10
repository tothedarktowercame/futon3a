;; endpoint_extraction_spike.clj — M-memes-arrows VERIFY, hook EP.
;;
;; Contract C's hard precondition (claude-5): can we derive a CLEAN (have, want) endpoint
;; pair from a typed `missing-head` signal? If yes, a freshly-minted miner token unifies onto
;; the existing arrow by endpoint-match (logic-model I4); if no, Contract C degrades to
;; synthetic-id-proxy. This spike shows extraction works AND validates it against a REAL
;; resolved missing-head sorry whose (have, want, construction) are documented.
;;
;; Run:  cd ~/code/futon3a && clojure -M holes/labs/M-memes-arrows/endpoint_extraction_spike.clj
;;
;; The key fact that makes this implementable (claude-5): a `missing-head` ALWAYS means the
;; same typed thing — "the head computes LOCALLY (have) but is not readable by the WM head
;; (want)." The codomain is fixed by the head's type + the WM-read contract, so (have, want)
;; is a pure function of the head id.

(require '[clojure.string :as str])

;; The AIF head registry — the typed heads' CANONICAL ids (stands in for scan-aif-heads).
(def known-head-ids #{"mission-aif-head" "wm-aif-head" "metabolic-balance" "self-watch" "commit-hygiene"})

(defn extract-head-id-naive
  "FIRST ATTEMPT (regex only) — what the spike showed is INSUFFICIENT: it handles the miner's
   `aif-head-missing-<id>` convention but mangles the legacy `<id>-not-served` convention."
  [sig]
  (cond
    (and (map? sig) (= "missing-head" (:type sig))) (:id sig)
    (keyword? sig) (-> (name sig) (str/replace #"^aif-head-missing-" ""))
    :else (str sig)))

(defn canonicalize-head-id
  "THE FIX (registry-resolved): map any source/convention to a CANONICAL head id by matching
   against the typed head registry — not by string-munging. This is what makes unify-by-endpoint
   actually catch same-endpoint/different-id collisions (claude-5's Contract-C requirement)."
  [sig]
  (let [raw (extract-head-id-naive sig)]
    (or (known-head-ids raw)
        ;; resolve a decorated id (`<head>-not-served`, `aif-head-missing-<head>`, …) to the
        ;; canonical head it names: the known head-id that is a substring of the raw id.
        (some #(when (str/includes? raw %) %) known-head-ids)
        raw)))

(def extract-head-id canonicalize-head-id)

(defn missing-head->endpoints
  "The extraction. A missing-head's (have, want) is fixed by the typed head id:
   have = the head's local typed computation (it computes locally — that's why it's a
          construction-hole and not a wish), want = the head as a WM-head-readable surface."
  [sig]
  (let [h (extract-head-id sig)]
    {:have (str "aif-head/" h "/local")
     :want (str "aif-head/" h "/wm-readable")}))

(println "\n=== endpoint-extraction spike (Contract C precondition) ===\n")

;; --- 1. Extraction on the real head, from three different real sources ---
(def sources
  {:live-priority   {:type "missing-head" :id "mission-aif-head" :note "computes locally; not served"}
   :miner-mint      :sorry/aif-head-missing-mission-aif-head      ; what loop_learning would mint
   :legacy-registry :sorry/mission-aif-head-not-served})          ; the real sorrys.edn entry

(println "Extracted endpoints, by source — RAW ids, registry-resolved (all three should agree):")
(def extracted (into {} (for [[k s] sources] [k (missing-head->endpoints s)])))
(doseq [[k e] extracted] (println (format "  %-16s -> %s" (name k) e)))
(def agree? (= 1 (count (distinct (vals extracted)))))
(println (format "  => all sources agree on (have, want): %s" agree?))

;; --- 2. Validate against the REAL resolved sorry's documented endpoints + construction ---
;; :sorry/mission-aif-head-not-served (futon2/resources/sorrys.edn):
;;   rationale: head "computes locally ... but its outputs are not yet a queryable surface"  => have/want
;;   resolution: "the WM head now READS the mission-AIF head's local computation in-process;
;;                scan-aif-heads un-stubbed -> :available? true, missing-count 0"             => construction
(def ground-truth
  {:have "aif-head/mission-aif-head/local"      ; "computes locally"
   :want "aif-head/mission-aif-head/wm-readable" ; "queryable surface readable by the WM head"
   :construction "WM head reads the head's local computation in-process (scan-aif-heads un-stubbed)"})

(def match? (= (select-keys ground-truth [:have :want])
               (missing-head->endpoints {:type "missing-head" :id "mission-aif-head"})))
(println (format "\nGround-truth validation vs real resolved :sorry/mission-aif-head-not-served:"))
(println (format "  extracted == documented (have, want): %s" match?))
(println (format "  documented construction (the :constructed payload): %s" (:construction ground-truth)))

;; --- 3. The payoff: two DIFFERENT mint-ids extract the SAME (have, want) => I4 unifies ---
(def historical-id :sorry/mission-aif-head-not-served)            ; 2026-05-27, hand-curated
(def fresh-miner-id :sorry/aif-head-missing-mission-aif-head)     ; what the auto-miner mints
;; The FINDING: naive regex-only extraction FAILS to unify these two conventions; the
;; registry-resolved canonicalisation succeeds. This is exactly the same-endpoint/different-id
;; collision claude-5 said Contract C must catch — and why extraction must hit the head
;; registry, not string-munge.
(defn endpoints-via [f sig] (let [h (f sig)] [(str "aif-head/" h "/local") (str "aif-head/" h "/wm-readable")]))
(def naive-unify?     (= (endpoints-via extract-head-id-naive historical-id)
                         (endpoints-via extract-head-id-naive fresh-miner-id)))
(def canonical-unify? (= (missing-head->endpoints historical-id)
                         (missing-head->endpoints fresh-miner-id)))
(println "\nContract C / logic-model I4 payoff (historical hand-curated id vs fresh miner mint):")
(println (format "  historical id : %s   (naive extracts head: %s)" historical-id (extract-head-id-naive historical-id)))
(println (format "  miner mint id : %s   (naive extracts head: %s)" fresh-miner-id (extract-head-id-naive fresh-miner-id)))
(println (format "  NAIVE (regex-only) unify-by-endpoint     : %s  <- FAILS: legacy convention mangled (the finding)" naive-unify?))
(println (format "  CANONICAL (registry-resolved) unify      : %s  <- both -> mission-aif-head -> one arrow" canonical-unify?))

(println (format "\nRESULT: extraction-clean=%s  ground-truth-match=%s  canonical-unify=%s  => EP %s"
                 agree? match? canonical-unify?
                 (if (and agree? match? canonical-unify?) "PASS (with named requirement: canonical-id resolution via the head registry, NOT regex)" "FAIL")))
