(ns meme.identity
  "Endpoint-keyed arrow identity and Contract-C promotion."
  (:require [meme.arrow :as arrow]
            [meme.cap-ascent :as cap-ascent]
            [meme.core :as core]
            [meme.endpoints :as endpoints]))

(def state-order
  [:correlated :open :constructed])

(def state-rank
  (zipmap state-order (range)))

(defn endpoint-key [{:keys [have want]}]
  [have want])

(defn advances-cap [arrow-row]
  (:advances_cap arrow-row))

(defn normalize-endpoints
  "Accept explicit {:have :want} or a missing-head signal/id."
  [x]
  (if (and (map? x) (:have x) (:want x))
    (select-keys x [:have :want])
    (endpoints/extract-endpoints x)))

(defn- ensure-endpoint-entities! [ds {:keys [have want]}]
  {:source (core/ensure-entity! ds have :kind "aif-endpoint")
   :target (core/ensure-entity! ds want :kind "aif-endpoint")})

(defn- endpoint-entity-ids [ds {:keys [have want]}]
  (when-let [source (core/find-entity-by-name ds have)]
    (when-let [target (core/find-entity-by-name ds want)]
      {:source-id (:id source)
       :target-id (:id target)})))

(defn arrows-by-endpoint [ds endpoint]
  (let [endpoint (normalize-endpoints endpoint)]
    (if-let [{:keys [source-id target-id]} (endpoint-entity-ids ds endpoint)]
      (->> (arrow/arrows-from ds source-id)
           (filter #(= target-id (:target_id %)))
           vec)
      [])))

(defn find-by-endpoint [ds endpoint]
  (first (arrows-by-endpoint ds endpoint)))

(defn- add-scope-tag [tags token-id]
  (let [tag (str "absorbed-token:" (name token-id))]
    (vec (distinct (conj (vec (or tags [])) tag)))))

(defn- absorb-token! [ds arrow-row token-id]
  (if token-id
    (do
      (arrow/update-arrow! ds (:id arrow-row)
                           {:scope-tags (add-scope-tag (:scope-tags arrow-row) token-id)})
      (arrow/get-arrow ds (:id arrow-row)))
    arrow-row))

(defn- merge-advances-cap! [ds arrow-row cap-id]
  (let [existing (advances-cap arrow-row)]
    (cond
      (nil? cap-id)
      arrow-row

      (nil? existing)
      (do
        (arrow/update-arrow! ds (:id arrow-row) {:advances-cap cap-id})
        (arrow/get-arrow ds (:id arrow-row)))

      (= existing cap-id)
      arrow-row

      :else
      (throw (ex-info "endpoint arrow already advances a different capability"
                      {:reason :capability/conflicting-advances-cap
                       :arrow-id (:id arrow-row)
                       :existing existing
                       :requested cap-id})))))

(defn mint-or-unify!
  "Create an endpoint-keyed arrow, or return the existing row for the same
   (have,want). Duplicate mint attempts are represented as :op :unify, not
   :op :mint, so Contract C has no duplicate mint to catch."
  [ds endpoint {:keys [mode status payload scope-tags advances-cap confidence rationale created-by token-id]
                :or {mode :untyped status :correlated}}]
  (let [endpoint (normalize-endpoints endpoint)]
    (if-let [existing (find-by-endpoint ds endpoint)]
      (let [with-cap (merge-advances-cap! ds existing advances-cap)
            absorbed (absorb-token! ds with-cap token-id)]
        {:arrow absorbed
         :created? false
         :unified? true
         :op {:op :unify
              :id (:id absorbed)
              :token-id token-id
              :have (:have endpoint)
              :want (:want endpoint)}})
      (let [{:keys [source target]} (ensure-endpoint-entities! ds endpoint)
            created (arrow/create-arrow!
                     ds
                     {:source-id (:id source)
                      :target-id (:id target)
                      :mode mode
                      :payload payload
                      :scope-tags scope-tags
                      :advances-cap advances-cap
                      :confidence confidence
                      :status status
                      :rationale rationale
                      :created-by (or created-by "meme.identity/mint-or-unify!")})
            row (arrow/get-arrow ds (:id created))]
        {:arrow row
         :created? true
         :unified? false
         :op {:op :mint
              :id (:id row)
              :have (:have endpoint)
              :want (:want endpoint)}}))))

(defn- monotone-promotion? [from to]
  (let [from-rank (state-rank from)
        to-rank (state-rank to)]
    (and from-rank to-rank (< from-rank to-rank))))

(defn- cap-ascent-if-needed! [arrow-row endpoint cap-ascent-opts]
  (when-let [cap-id (advances-cap arrow-row)]
    (cap-ascent/advance! cap-id (endpoint-key endpoint) cap-ascent-opts)))

(defn promote!
  "Promote an existing endpoint-keyed arrow in place."
  [ds endpoint to-state & {:keys [mode payload rationale created-by token-id cap-ascent]
                           :or {cap-ascent {:write? true}}}]
  (let [endpoint (normalize-endpoints endpoint)
        existing (or (find-by-endpoint ds endpoint)
                     (throw (ex-info "cannot promote missing endpoint-keyed arrow"
                                     {:endpoint endpoint})))
        from-state (:status existing)]
    (when (and (= :constructed to-state)
               (advances-cap existing))
      ;; Reject unknown capability ids before mutating the local arrow state.
      (cap-ascent/plan (advances-cap existing) (endpoint-key endpoint) cap-ascent))
    (when (and (not= from-state to-state)
               (not (monotone-promotion? from-state to-state)))
      (throw (ex-info "promotion must strictly advance state"
                      {:id (:id existing)
                       :from from-state
                       :to to-state})))
    (if (= from-state to-state)
      {:arrow existing
       :cap-ascent (when (= :constructed to-state)
                     (cap-ascent-if-needed! existing endpoint cap-ascent))
       :op {:op :noop
            :id (:id existing)
            :state from-state
            :have (:have endpoint)
            :want (:want endpoint)}}
      (let [updates (cond-> {:status to-state
                             :payload payload}
                      mode (assoc :mode mode)
                      rationale (assoc :rationale rationale)
                      created-by (assoc :created-by created-by))
            _ (arrow/update-arrow! ds (:id existing) updates)
            updated (absorb-token! ds (arrow/get-arrow ds (:id existing)) token-id)]
        {:arrow updated
         :cap-ascent (when (= :constructed to-state)
                       (cap-ascent-if-needed! updated endpoint cap-ascent))
         :op {:op :promote
              :id (:id updated)
              :from from-state
              :to to-state
              :have (:have endpoint)
              :want (:want endpoint)}}))))

(defn- entity-names [ds]
  (set (map :name (core/list-entities ds))))

(defn- arrow-probe-row [ds arrow-row]
  (let [source (core/get-entity ds (:source_id arrow-row))
        target (core/get-entity ds (:target_id arrow-row))]
    {:id (:id arrow-row)
     :have (:name source)
     :want (:name target)
     :source-id (:source_id arrow-row)
     :target-id (:target_id arrow-row)
     :state (:status arrow-row)
     :cons (if (some? (:payload arrow-row)) :yes :no)
     :source-present? (some? source)
     :target-present? (some? target)}))

(defn store-trace
  "Build the live trace shape consumed by the conformance probe."
  [ds ops]
  {:nodes (entity-names ds)
   :arrows (mapv #(arrow-probe-row ds %) (arrow/list-arrows ds {:limit 10000}))
   :ops (vec ops)})

(defn- i1-endpoint-uniqueness [{:keys [arrows]}]
  (->> arrows
       (filter #(and (:have %) (:want %)))
       (group-by (juxt :have :want))
       (mapcat (fn [[[have want] rows]]
                 (when (< 1 (count rows))
                   [{:v :endpoint-dup
                     :ids (mapv :id rows)
                     :have have
                     :want want}])))
       vec))

(defn- i2-construction-iff-constructed [{:keys [arrows]}]
  (vec
   (keep (fn [{:keys [id state cons]}]
           (cond
             (and (= state :constructed) (= cons :no))
             {:v :constructed-without-construction :id id}

             (and (not= state :constructed) (= cons :yes))
             {:v :construction-but-not-constructed :id id}))
         arrows)))

(defn- i3-monotone-advance [{:keys [ops]}]
  (vec
   (keep (fn [{:keys [op id from to]}]
           (when (and (= op :promote)
                      (not (monotone-promotion? from to)))
             {:v :state-regression-or-stall
              :id id
              :from from
              :to to}))
         ops)))

(defn- i4-unify-not-mint [{:keys [arrows ops]}]
  (let [by-endpoint (group-by (juxt :have :want) arrows)]
    (vec
     (mapcat
      (fn [{:keys [op id have want]}]
        (when (= op :mint)
          (for [row (get by-endpoint [have want])
                :when (not= id (:id row))]
            {:v :mint-should-have-unified
             :mint id
             :collides-with (:id row)
             :have have
             :want want})))
      ops))))

(defn- i5-node-reuse [{:keys [nodes arrows]}]
  (vec
   (keep (fn [{:keys [id have want source-present? target-present?]}]
           (when-not (and source-present?
                          target-present?
                          (contains? nodes have)
                          (contains? nodes want))
             {:v :endpoint-not-an-existing-node
              :id id
              :have have
              :want want}))
         arrows)))

(defn all-violations [trace]
  {:i1 (i1-endpoint-uniqueness trace)
   :i2 (i2-construction-iff-constructed trace)
   :i3 (i3-monotone-advance trace)
   :i4 (i4-unify-not-mint trace)
   :i5 (i5-node-reuse trace)})

(defn probe
  "Run I1-I5 against the live store and the supplied operation log."
  [ds ops]
  (let [trace (store-trace ds ops)
        violations (all-violations trace)]
    {:trace trace
     :violations violations
     :violation-count (reduce + (map count (vals violations)))}))
