(ns sidecar.store
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [sidecar.validation :as v]))

(defn- now [] (java.time.Instant/now))

(defn- gen-id [prefix]
  (str prefix "-" (subs (str (java.util.UUID/randomUUID)) 0 8)))

(defn- log-root []
  (or (some-> (System/getenv "SIDECAR_LOG_ROOT") str/trim not-empty)
      "log"))

(defn- audit-path []
  (io/file (log-root) "sidecar-audit.edn"))

(defn- append-audit! [entry]
  (let [path (audit-path)]
    (.mkdirs (.getParentFile path))
    (spit path (str (pr-str entry) "\n") :append true)))

(defn- record-audit! [store entry]
  (swap! store update :audit (fnil conj []) entry)
  (append-audit! entry)
  entry)

(defn new-store []
  (atom {:proposals {}
         :promotions {}
         :evidence {}
         :actions {}
         :facts {}
         :bridge-triples {}
         :chains {}
         :audit []}))

(defn audit-log [store]
  (:audit @store))

(defn- event-entity-ids [event]
  (let [ids (transient #{})
        add-id (fn [value]
                 (when (string? value)
                   (conj! ids value)))]
    (doseq [[key id-key] [[:proposal :proposal/id]
                          [:promotion :promotion/id]
                          [:evidence :evidence/id]
                          [:action :action/id]
                          [:fact :fact/id]
                          [:chain :chain/id]]]
      (when-let [payload (get event key)]
        (add-id (get payload id-key))))
    (when-let [promotion (:promotion event)]
      (add-id (:proposal/id promotion)))
    (when-let [evidence (:evidence event)]
      (when-let [target (:evidence/target evidence)]
        (add-id (:id target))))
    (when-let [fact (:fact event)]
      (add-id (:promotion/id fact)))
    (when-let [chain (:chain event)]
      (doseq [step (:chain/steps chain)]
        (add-id (:step/id step))))
    (persistent! ids)))

(defn audit-entries-for-id [store entity-id]
  (->> (:audit @store)
       (filter (fn [entry]
                 (contains? (event-entity-ids (:event entry)) entity-id)))
       (sort-by :at)
       vec))

(defn failure-reasons [store entity-id]
  (mapv (fn [entry]
          {:audit/type (:audit/type entry)
           :event/type (get-in entry [:event :event/type])
           :errors (:errors entry)
           :at (:at entry)})
        (audit-entries-for-id store entity-id)))

(defn- success-entry [event-type at entity]
  {:kind :success
   :event/type event-type
   :at at
   :entity entity})

(defn- matches-entity? [entity-id record kind]
  (case kind
    :proposal (= (:proposal/id record) entity-id)
    :promotion (or (= (:promotion/id record) entity-id)
                   (= (:proposal/id record) entity-id))
    :evidence (or (= (:evidence/id record) entity-id)
                  (= (get-in record [:evidence/target :id]) entity-id))
    :action (= (:action/id record) entity-id)
    :fact (or (= (:fact/id record) entity-id)
              (= (:promotion/id record) entity-id))
    :chain (or (= (:chain/id record) entity-id)
               (some #(= (:step/id %) entity-id) (:chain/steps record)))
    false))

(defn- success-entries-for
  [records kind time-key entity-id]
  (->> records
       vals
       (filter #(matches-entity? entity-id % kind))
       (mapv #(success-entry (case kind
                               :proposal :proposal/recorded
                               :promotion :promotion/recorded
                               :evidence :evidence/attached
                               :action :action/recorded
                               :fact :fact/materialized
                               :chain :chain/built)
                             (get % time-key)
                             %))))

(defn event-timeline [store entity-id]
  (let [snapshot @store
        success (concat
                 (success-entries-for (:proposals snapshot) :proposal :proposal/created-at entity-id)
                 (success-entries-for (:promotions snapshot) :promotion :promotion/created-at entity-id)
                 (success-entries-for (:evidence snapshot) :evidence :evidence/created-at entity-id)
                 (success-entries-for (:actions snapshot) :action :action/created-at entity-id)
                 (success-entries-for (:facts snapshot) :fact :fact/created-at entity-id)
                 (success-entries-for (:chains snapshot) :chain :chain/created-at entity-id))
        failures (mapv (fn [entry]
                         {:kind :failure
                          :event/type (get-in entry [:event :event/type])
                          :audit/type (:audit/type entry)
                          :errors (:errors entry)
                          :at (:at entry)
                          :event (:event entry)})
                       (audit-entries-for-id store entity-id))]
    (->> (concat success failures)
         (sort-by :at)
         vec)))

(defn- fail! [store event errors category]
  (record-audit! store {:audit/type category
                        :event event
                        :errors errors
                        :at (now)})
  {:ok false :errors errors})

(defn record-proposal!
  [store proposal]
  (let [proposal (assoc proposal :proposal/created-at (or (:proposal/created-at proposal) (now)))
        event {:event/type :proposal/recorded
               :event/id (gen-id "event")
               :event/at (now)
               :proposal proposal}
        result (v/validate-event event)]
    (cond
      (not (:ok? result))
      (fail! store event (:errors result) :validation-failure)

      (get-in @store [:proposals (:proposal/id proposal)])
      (fail! store event [{:field :proposal/id :type :duplicate :msg "proposal already recorded"}]
             :append-only-violation)

      :else
      (do
        (swap! store assoc-in [:proposals (:proposal/id proposal)] proposal)
        {:ok true :proposal/id (:proposal/id proposal)}))))

(defn record-promotion!
  [store promotion]
  (let [promotion (assoc promotion :promotion/created-at (or (:promotion/created-at promotion) (now)))
        event {:event/type :promotion/recorded
               :event/id (gen-id "event")
               :event/at (now)
               :promotion promotion}
        result (v/validate-event event)
        proposal-id (:proposal/id promotion)]
    (cond
      (not (:ok? result))
      (fail! store event (:errors result) :validation-failure)

      (nil? (get-in @store [:proposals proposal-id]))
      (fail! store event [{:field :proposal/id :type :missing :msg "proposal not found"}]
             :boundary-violation)

      (get-in @store [:promotions (:promotion/id promotion)])
      (fail! store event [{:field :promotion/id :type :duplicate :msg "promotion already recorded"}]
             :append-only-violation)

      :else
      (do
        (swap! store assoc-in [:promotions (:promotion/id promotion)] promotion)
        {:ok true :promotion/id (:promotion/id promotion)}))))

(defn record-evidence!
  [store evidence]
  (let [evidence (assoc evidence :evidence/created-at (or (:evidence/created-at evidence) (now)))
        event {:event/type :evidence/attached
               :event/id (gen-id "event")
               :event/at (now)
               :evidence evidence}
        result (v/validate-event event)
        target (:evidence/target evidence)
        target-ok? (case (:type target)
                     :proposal (get-in @store [:proposals (:id target)])
                     :promotion (get-in @store [:promotions (:id target)])
                     nil)]
    (cond
      (not (:ok? result))
      (fail! store event (:errors result) :validation-failure)

      (not target-ok?)
      (fail! store event [{:field :evidence/target :type :missing :msg "target not found"}]
             :boundary-violation)

      (get-in @store [:evidence (:evidence/id evidence)])
      (fail! store event [{:field :evidence/id :type :duplicate :msg "evidence already recorded"}]
             :append-only-violation)

      :else
      (do
        (swap! store assoc-in [:evidence (:evidence/id evidence)] evidence)
        {:ok true :evidence/id (:evidence/id evidence)}))))

(defn record-action!
  [store action]
  (let [action (assoc action :action/created-at (or (:action/created-at action) (now)))
        event {:event/type :action/recorded
               :event/id (gen-id "event")
               :event/at (now)
               :action action}
        result (v/validate-event event)]
    (cond
      (not (:ok? result))
      (fail! store event (:errors result) :validation-failure)

      (get-in @store [:actions (:action/id action)])
      (fail! store event [{:field :action/id :type :duplicate :msg "action already recorded"}]
             :append-only-violation)

      :else
      (do
        (swap! store assoc-in [:actions (:action/id action)] action)
        {:ok true :action/id (:action/id action)}))))

(defn record-fact!
  [store fact opts]
  (let [promotion-id (:promotion/id opts)
        fact (assoc fact :fact/created-at (or (:fact/created-at fact) (now)))
        event {:event/type :fact/materialized
               :event/id (gen-id "event")
               :event/at (now)
               :fact fact}
        result (v/validate-event event)
        promotion (when promotion-id (get-in @store [:promotions promotion-id]))]
    (cond
      (not (:ok? result))
      (fail! store event (:errors result) :validation-failure)

      (nil? promotion-id)
      (fail! store event [{:field :promotion/id :type :missing :msg "promotion required for fact writes"}]
             :boundary-violation)

      (nil? promotion)
      (fail! store event [{:field :promotion/id :type :missing :msg "promotion not found"}]
             :boundary-violation)

      (and (:promotion/kind promotion)
           (not= (:promotion/kind promotion) (:fact/kind fact)))
      (fail! store event [{:field :fact/kind :type :mismatch :msg "fact kind mismatches promotion"}]
             :boundary-violation)

      (get-in @store [:facts (:fact/id fact)])
      (fail! store event [{:field :fact/id :type :duplicate :msg "fact already recorded"}]
             :append-only-violation)

      :else
      (do
        (swap! store assoc-in [:facts (:fact/id fact)] (assoc fact :promotion/id promotion-id))
        {:ok true :fact/id (:fact/id fact)}))))

(defn record-bridge-triple!
  [store bridge opts]
  (let [fact {:fact/id (:bridge/id bridge)
              :fact/kind :bridge-triple
              :fact/body bridge
              :fact/created-at (:bridge/created-at bridge)}]
    (if-let [result (record-fact! store fact opts)]
      (if (:ok result)
        (do
          (swap! store assoc-in [:bridge-triples (:bridge/id bridge)]
                 (assoc bridge :promotion/id (:promotion/id opts)))
          result)
        result)
      {:ok false :errors [{:field :bridge/id :type :invalid :msg "bridge write failed"}]})))

(def ^:private softness-weights {:arrow 0.0 :bridge 0.5 :proposal 1.0})

(defn- chain-softness [steps]
  (let [by-step (mapv (fn [step]
                        {:step/id (:step/id step)
                         :step/type (:step/type step)
                         :softness (get softness-weights (:step/type step) 0)})
                      steps)
        total (reduce + 0.0 (map :softness by-step))
        average (if (seq by-step)
                  (/ total (count by-step))
                  0.0)]
    {:softness/total total
     :softness/average average
     :softness/steps by-step}))

(defn build-chain!
  [store chain]
  (let [chain-id (or (:chain/id chain) (gen-id "chain"))
        chain (assoc chain
                     :chain/id chain-id
                     :chain/created-at (or (:chain/created-at chain) (now)))
        steps (:chain/steps chain)
        event {:event/type :chain/built
               :event/id (gen-id "event")
               :event/at (now)
               :chain chain}
        result (v/validate-event event)
        missing-step (first (keep (fn [step]
                                    (case (:step/type step)
                                      :proposal (when-not (get-in @store [:proposals (:step/id step)])
                                                  {:field :step/id :type :missing :msg "proposal not found"})
                                      :bridge (when-not (get-in @store [:bridge-triples (:step/id step)])
                                                {:field :step/id :type :missing :msg "bridge triple not found"})
                                      nil))
                                  steps))]
    (cond
      (not (:ok? result))
      (fail! store event (:errors result) :validation-failure)

      missing-step
      (fail! store event [missing-step] :boundary-violation)

      (get-in @store [:chains chain-id])
      (fail! store event [{:field :chain/id :type :duplicate :msg "chain already recorded"}]
             :append-only-violation)

      :else
      (let [softness (chain-softness steps)
            chain-record (merge chain softness)]
        (swap! store assoc-in [:chains chain-id] chain-record)
        {:ok true :chain/id chain-id :softness softness}))))
