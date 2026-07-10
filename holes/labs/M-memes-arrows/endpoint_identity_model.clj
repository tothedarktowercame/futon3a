;; endpoint_identity_model.clj — M-memes-arrows VERIFY core (logic-model-before-code).
;;
;; Checks the KEYSTONE design invariant (endpoint-identity: an arrow is identified by its
;; (have, want) pair, not its record-id; promotion advances state without re-minting) as a
;; core.logic + pldb model over an ABSTRACT store-trace — BEFORE any real store code, per
;; [[feedback_logic_model_before_code]] / futon3c.logic.outing-invariants house idiom.
;;
;; A design is VERIFIED iff (a) the conforming witness trace yields ZERO violations and
;; (b) each adversarial trace (one per invariant) is CAUGHT by its invariant.
;;
;; Run:
;;   cd ~/code/futon3a
;;   clojure -Sdeps '{:deps {org.clojure/core.logic {:mvn/version "1.1.0"}}}' \
;;           -M holes/labs/M-memes-arrows/endpoint_identity_model.clj
;;
;; Trace shape:
;;   {:nodes  #{...}                                  ; existing nodes (R1)
;;    :arrows [{:id :have :want :state :cons}]         ; the store SNAPSHOT (one row per arrow)
;;    :ops    [{:op :mint :id :have :want}             ; what mining/promotion DID
;;             {:op :promote :id :from :to}]}
;;
;; Invariants:
;;   I1 endpoint-uniqueness    no two distinct rows share (have, want)        [the keystone]
;;   I2 cons-iff-constructed   state=:constructed  <=>  cons=:yes
;;   I3 monotone-advance       no :promote op regresses or stalls state
;;   I4 unify-not-mint (C)     no :mint op for endpoints an existing row already holds
;;   I5 node-reuse (R1)        every endpoint is an existing node

(require '[clojure.core.logic :as l]
         '[clojure.core.logic.pldb :as pldb])

(pldb/db-rel arrowo     id have want state cons)
(pldb/db-rel nodeo      n)
(pldb/db-rel mint-opo   id have want)
(pldb/db-rel promote-opo id from to)
(pldb/db-rel bad-transo from to)            ; fixed table of forbidden state transitions

(def ^:private forbidden-transitions
  ;; backward + stall — everything that is NOT correlated->open->constructed forward
  [[:open :correlated] [:constructed :open] [:constructed :correlated]
   [:correlated :correlated] [:open :open] [:constructed :constructed]])

(defn build-db [{:keys [nodes arrows ops]}]
  (let [facts (concat
                (for [n nodes] [nodeo n])
                (for [{:keys [id have want state cons]} arrows]
                  [arrowo id have want state (or cons :no)])
                (for [o ops]
                  (case (:op o)
                    :mint    [mint-opo (:id o) (:have o) (:want o)]
                    :promote [promote-opo (:id o) (:from o) (:to o)]))
                (for [[a b] forbidden-transitions] [bad-transo a b]))]
    (reduce (fn [db [rel & args]] (apply pldb/db-fact db rel args)) pldb/empty-db facts)))

;; --- I1: endpoint-uniqueness (core join + disequality) ---
(defn q-i1 [db]
  (pldb/with-db db
    (l/run* [q]
      (l/fresh [a b h w sa sb ca cb]
        (arrowo a h w sa ca)
        (arrowo b h w sb cb)
        (l/!= a b)
        (l/== q {:v :endpoint-dup :a a :b b :have h :want w})))))

;; --- I2: construction iff :constructed ---
(defn q-i2 [db]
  (pldb/with-db db
    (l/run* [q]
      (l/fresh [id h w st c]
        (arrowo id h w st c)
        (l/conde
          [(l/== st :constructed) (l/== c :no)  (l/== q {:v :constructed-without-construction :id id})]
          [(l/!= st :constructed) (l/== c :yes) (l/== q {:v :construction-but-not-constructed :id id})])))))

;; --- I3: monotone state advance (promote op hits the forbidden-transition table) ---
(defn q-i3 [db]
  (pldb/with-db db
    (l/run* [q]
      (l/fresh [id from to]
        (promote-opo id from to)
        (bad-transo from to)
        (l/== q {:v :state-regression-or-stall :id id :from from :to to})))))

;; --- I4: unify-not-mint = Contract C (a mint for endpoints an existing, different row holds) ---
(defn q-i4 [db]
  (pldb/with-db db
    (l/run* [q]
      (l/fresh [mid h w other so co]
        (mint-opo mid h w)
        (arrowo other h w so co)
        (l/!= other mid)
        (l/== q {:v :mint-should-have-unified :mint mid :collides-with other :have h :want w})))))

;; --- I5: node-reuse / R1 (plain set check; absence is awkward in core.logic) ---
(defn q-i5 [{:keys [nodes arrows]}]
  (vec (for [a arrows :when (not (and (nodes (:have a)) (nodes (:want a))))]
         {:v :endpoint-not-an-existing-node :id (:id a)})))

(defn all-violations [trace]
  (let [db (build-db trace)]
    {:i1 (vec (q-i1 db)) :i2 (vec (q-i2 db)) :i3 (vec (q-i3 db))
     :i4 (vec (q-i4 db)) :i5 (q-i5 trace)}))

;; =============================================================================
;; Fixtures
;; =============================================================================

;; CONFORMING witness = the r3a arrow maturing correctly as ONE row:
;; belief-mass -> support-coverage, minted once, promoted correlated->open->constructed,
;; ends :constructed WITH a construction, endpoints are existing nodes, no duplicate, no
;; mint-for-existing-endpoints.
(def conforming
  {:nodes  #{:belief-mass :support-coverage}
   :arrows [{:id :A :have :belief-mass :want :support-coverage :state :constructed :cons :yes}]
   :ops    [{:op :mint :id :A :have :belief-mass :want :support-coverage}
            {:op :promote :id :A :from :correlated :to :open}
            {:op :promote :id :A :from :open :to :constructed}]})

;; One adversarial trace per invariant — each violates EXACTLY its target.
(def adversarial
  {;; two rows, same endpoints, different ids (the miner minted a duplicate)
   :i1 (update conforming :arrows conj
               {:id :B :have :belief-mass :want :support-coverage :state :open :cons :no})
   ;; :constructed but no construction
   :i2 (assoc-in conforming [:arrows 0 :cons] :no)
   ;; a promotion that regresses constructed -> open
   :i3 (update conforming :ops conj {:op :promote :id :A :from :constructed :to :open})
   ;; a mint for endpoints row :A already holds (Contract C breach: should have unified)
   :i4 (update conforming :ops conj {:op :mint :id :B :have :belief-mass :want :support-coverage})
   ;; an endpoint that is not a declared node
   :i5 (assoc-in conforming [:arrows 0 :want] :undeclared-node)})

;; =============================================================================
;; Driver
;; =============================================================================
(println "\n=== endpoint-identity logic-model (VERIFY core) ===\n")
(let [conf (all-violations conforming)
      conf-total (reduce + (map count (vals conf)))]
  (println (format "CONFORMING witness: %d violations  %s"
                   conf-total (if (zero? conf-total) "(PASS — zero, as required)" conf)))
  (println "\nADVERSARIAL traces (each must be CAUGHT by its own invariant):")
  (let [results
        (for [inv [:i1 :i2 :i3 :i4 :i5]]
          (let [v (all-violations (get adversarial inv))
                caught? (pos? (count (get v inv)))
                ;; isolation: did it trip ONLY its target invariant?
                others (->> (dissoc v inv) (filter (comp seq val)) (map key) vec)]
            (println (format "  [%s] %-8s caught=%-5s isolated=%-5s  %s"
                             (name inv)
                             (case inv :i1 "endpt-uniq" :i2 "cons-iff" :i3 "monotone"
                                       :i4 "unify(C)" :i5 "node(R1)")
                             caught? (empty? others)
                             (first (get v inv))))
            {:inv inv :caught? caught? :isolated (empty? others) :leaked-into others}))
        all-caught? (every? :caught? results)]
    (println (format "\nRESULT: conforming-clean=%s  all-adversarial-caught=%s  => model %s"
                     (zero? conf-total) all-caught?
                     (if (and (zero? conf-total) all-caught?) "VERIFIED" "FAILED")))))
