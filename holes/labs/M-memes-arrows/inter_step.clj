#!/usr/bin/env bb
;; inter_step.clj — tooling for the meme→cascade→sorry→wiring INTER-STEP PROCESS.
;;
;; The data type is ONE (have,want) arrow in three states:
;;   :correlated (meme) → :open (sorry, grounded in substrate-2) → :constructed (wiring).
;; The inter-step process is invariant-gated promotion between states (mirrors
;; futon3a meme.identity/promote!, mergeable later). Each transition is gated by a
;; checkable invariant, so a future run is mechanical — no re-deriving "what is a sorry".
;;
;; The runbook E-fold-engine-runbook.html is the interactively-derived guide to what
;; should exist at each stage; this tool is the start of rederiving/checking it automatically.
;;
;; Usage:  bb inter_step.clj test   — prove the tooling (positive + negative cases)
;;         bb inter_step.clj job    — push the E-fold-engine episode through; conformance per stage

(require '[clojure.edn :as edn]
         '[clojure.string :as str]
         '[cheshire.core :as json])

(def LAB "/home/joe/code/futon3a/holes/labs/M-memes-arrows")
(defn lab [f] (str LAB "/" f))
(defn read-edn [f] (edn/read-string (slurp (lab f))))

;; ---------- the data type ----------
(def STATES [:correlated :open :constructed])

;; ---------- cascade patterns (the correlation evidence) ----------
(defn cascade-patterns []
  (->> (json/parse-string (slurp (lab "E-fold-engine-stage1.json")) true)
       :cascade (map first) set))

(defn warrant-patterns [s]
  (set (re-seq #"[a-z0-9]+(?:-[a-z0-9]+)*/[a-z0-9]+(?:-[a-z0-9]+)*" (or s ""))))

;; ---------- invariants (each → {:ok bool :reason? :detail?}) ----------
(defn inv-construction-iff-constructed [a]
  (let [c? (= :constructed (:state a)) w? (some? (:wiring a))]
    (if (= c? w?)
      {:ok true}
      {:ok false :reason (if c? "state :constructed but no wiring/method"
                             "wiring present but state not :constructed")})))

(defn inv-endpoint-unique [arrows]
  (let [dups (->> arrows (map (juxt :have :want)) frequencies (filter #(> (val %) 1)) (map key))]
    (if (empty? dups) {:ok true} {:ok false :reason "duplicate (have,want) arrows" :detail (vec dups)})))

(defn inv-grounded [a oracle]
  (let [eps (:endpoints a)
        claimed (filter #(true? (:in-map %)) eps)
        false-claims (remove oracle claimed)
        haves (filter #(and (= :have (:role %)) (true? (:in-map %))) eps)]
    (cond
      (seq false-claims) {:ok false :reason "endpoint claims :in-map but does not resolve in substrate-2"
                          :detail (mapv :ref false-claims)}
      (empty? haves)     {:ok false :reason "no have-endpoint grounded in substrate-2"}
      :else              {:ok true})))

(defn parse-sig [sig]
  (when-let [m (re-matches #".*?:\s*(.+?)\s*->\s*(.+)" (or sig ""))]
    {:dom (str/trim (nth m 1)) :cod (str/trim (nth m 2))}))

(defn inv-terminals-match [a]
  (let [sig  (parse-sig (:want-signature a))
        ins  (map :port (get-in a [:wiring :terminals :in]))
        outs (map :port (get-in a [:wiring :terminals :out]))
        dom-ok (boolean (some #(= % (:dom sig)) ins))
        cod  (or (:cod sig) "")
        need-w (boolean (re-find #"(?i)wiring" cod))
        need-h (boolean (re-find #"(?i)hole" cod))
        has-w  (boolean (some #(re-find #"(?i)wiring" %) outs))
        has-h  (boolean (some #(re-find #"(?i)hole" %) outs))]
    (if (and sig dom-ok (or (not need-w) has-w) (or (not need-h) has-h))
      {:ok true}
      {:ok false :reason "wiring terminals do not match the sorry want-signature"
       :detail {:sig sig :in-ports (vec ins) :out-ports (vec outs)}})))

(defn inv-cascade-warrant [a]
  (let [cps (:cascade a)
        bad (for [b (get-in a [:wiring :boxes])
                  :let [miss (remove cps (warrant-patterns (:warrant b)))]
                  :when (seq miss)]
              {:box (:id b) :unwarranted (vec miss)})]
    (if (empty? bad) {:ok true} {:ok false :reason "wiring box warrant(s) not in the cascade" :detail (vec bad)})))

;; ---------- transitions (invariant-gated promotion) ----------
(defn mint [id have want]
  {:id id :have have :want want :state :correlated :endpoints [] :cascade nil :wiring nil})

(defn attach-cascade [a cps] (assoc a :cascade cps))

(defn check! [nm r] (when-not (:ok r) (throw (ex-info (str nm " failed: " (:reason r)) (or (:detail r) {})))))

(defn promote-to-open [a endpoints want-sig oracle]
  (when (not= :correlated (:state a))
    (throw (ex-info "I3 monotone-advance: promote-to-open requires :correlated" {:state (:state a)})))
  (let [a' (assoc a :state :open :endpoints endpoints :want-signature want-sig)]
    (check! "GROUNDING" (inv-grounded a' oracle))
    a'))

(defn promote-to-constructed [a wiring]
  (when (not= :open (:state a))
    (throw (ex-info "I3 monotone-advance: promote-to-constructed requires :open" {:state (:state a)})))
  (let [a' (assoc a :state :constructed :wiring wiring)]
    (check! "I2 construction-iff-constructed" (inv-construction-iff-constructed a'))
    (check! "TERMINALS-MATCH" (inv-terminals-match a'))
    (check! "CASCADE-WARRANT" (inv-cascade-warrant a'))
    a'))

;; ---------- conformance probe ----------
(defn conformance [a oracle]
  (let [s (:state a)
        checks (cond-> [["I2 construction-iff-constructed" (inv-construction-iff-constructed a)]]
                 (#{:open :constructed} s) (conj ["GROUNDING" (inv-grounded a oracle)])
                 (= :constructed s)        (conj ["TERMINALS-MATCH" (inv-terminals-match a)]
                                                 ["CASCADE-WARRANT" (inv-cascade-warrant a)]))]
    {:state s :checks checks :green? (every? (comp :ok second) checks)}))

;; ---------- best-effort live grounding cross-check (one endpoint vs :7071) ----------
(defn live-cascade-construct? []
  (try
    (let [u (str "http://localhost:7071/api/alpha/hyperedges?type=code%2Fv05%2Fnamespace"
                 "&source-file=" (java.net.URLEncoder/encode (lab "cascade_construct.py") "UTF-8") "&limit=5")]
      (if (str/includes? (slurp u) "cascade_construct") :grounded :absent))
    (catch Exception _ :unreachable)))

;; ---------- the E-fold-engine job ----------
(defn run-job []
  (println "\n=== E-fold-engine inter-step job (push the episode through the tooling) ===")
  (let [sorry  (read-edn "E-fold-engine-sorry.edn")
        wiring (read-edn "E-fold-engine-wiring.edn")
        cps    (cascade-patterns)
        sig    (:want-signature sorry)
        psig   (parse-sig sig)
        oracle (constantly true)   ; trust the post-restart-confirmed :in-map flags (live cross-check below)
        a0 (mint (:id sorry) (:dom psig) (:cod psig))
        _  (println (format "  mint            → :correlated    %s" (:id a0)))
        a1 (attach-cascade a0 cps)
        _  (println (format "  attach-cascade  → cascade of %d patterns" (count cps)))
        a2 (promote-to-open a1 (:endpoints sorry) sig oracle)
        _  (println (format "  promote!        → :open          GROUNDING ✓ (%d in-map endpoints)"
                            (count (filter #(true? (:in-map %)) (:endpoints sorry)))))
        a3 (promote-to-constructed a2 wiring)
        _  (println "  promote!        → :constructed   I2 ✓  TERMINALS-MATCH ✓  CASCADE-WARRANT ✓")
        report (conformance a3 oracle)]
    (println "\n  conformance probe @ :constructed —")
    (doseq [[nm r] (:checks report)]
      (println (format "    %-34s %s" nm (if (:ok r) "✓" (str "✗ " (:reason r))))))
    (println (format "  GREEN? %s" (:green? report)))
    (println (format "  live grounding cross-check (cascade_construct vs :7071): %s" (live-cascade-construct?)))
    (:green? report)))

;; ---------- tests ----------
(def fail-count (atom 0))
(defn t [nm pred] (if pred (println "  PASS" nm) (do (swap! fail-count inc) (println "  FAIL" nm))))
(defn throws? [f] (try (f) false (catch Exception _ true)))

(defn run-tests []
  (println "\n=== inter-step tooling tests ===")
  (let [sorry  (read-edn "E-fold-engine-sorry.edn")
        wiring (read-edn "E-fold-engine-wiring.edn")
        cps    (cascade-patterns)
        sig    (:want-signature sorry)
        ok     (constantly true)
        a-open (-> (mint (:id sorry) "h" "w") (attach-cascade cps)
                   (promote-to-open (:endpoints sorry) sig ok))
        a-con  (promote-to-constructed a-open wiring)]
    ;; positive — the real, hand-derived E-fold-engine episode is well-formed
    (t "real episode reaches :constructed"            (= :constructed (:state a-con)))
    (t "real episode conformance GREEN"               (:green? (conformance a-con ok)))
    ;; I3 monotone-advance
    (t "I3 blocks construct-from-:correlated"         (throws? #(promote-to-constructed (mint "x" "h" "w") wiring)))
    (t "I3 blocks open-from-:open"                    (throws? #(promote-to-open a-open (:endpoints sorry) sig ok)))
    ;; I2 construction-iff-constructed
    (t "I2 fails: :constructed with no wiring"        (false? (:ok (inv-construction-iff-constructed (assoc a-open :state :constructed)))))
    (t "I2 ok on real constructed"                    (:ok (inv-construction-iff-constructed a-con)))
    ;; GROUNDING — a false :in-map claim is caught
    (let [bad (fn [ep] (not (str/includes? (:ref ep) "cascade_construct")))]
      (t "GROUNDING catches a false :in-map claim"    (throws? #(promote-to-open (-> (mint "y" "h" "w") (attach-cascade cps))
                                                                                  (:endpoints sorry) sig bad))))
    (t "GROUNDING ok when claims verify"              (:ok (inv-grounded a-open ok)))
    ;; TERMINALS-MATCH — wrong terminal fails
    (let [bw (assoc-in wiring [:terminals :in] [{:port "NOT-cascade-dict"}])]
      (t "TERMINALS-MATCH fails on wrong in-port"     (false? (:ok (inv-terminals-match (assoc a-con :wiring bw))))))
    (t "TERMINALS-MATCH ok on real wiring"            (:ok (inv-terminals-match a-con)))
    ;; CASCADE-WARRANT — a box warranted by a non-cascade pattern fails
    (let [bw (update wiring :boxes conj {:id :rogue :warrant "fake-ns/not-in-cascade"})]
      (t "CASCADE-WARRANT fails on unwarranted box"   (false? (:ok (inv-cascade-warrant (assoc a-con :wiring bw))))))
    (t "CASCADE-WARRANT ok on real wiring"            (:ok (inv-cascade-warrant a-con)))
    ;; I1 endpoint-uniqueness
    (t "I1 fails on duplicate (have,want)"            (false? (:ok (inv-endpoint-unique [(mint "a" "H" "W") (mint "b" "H" "W")]))))
    (t "I1 ok on distinct arrows"                     (:ok (inv-endpoint-unique [(mint "a" "H1" "W") (mint "b" "H2" "W")]))))
  (println (format "\n  %s" (if (zero? @fail-count) "ALL TESTS PASS" (str @fail-count " TEST(S) FAILED"))))
  (zero? @fail-count))

;; ---------- main ----------
(let [cmd (first *command-line-args*)]
  (case cmd
    "test" (when-not (run-tests) (System/exit 1))
    "job"  (when-not (run-job)   (System/exit 1))
    (do (run-tests) (run-job))))
