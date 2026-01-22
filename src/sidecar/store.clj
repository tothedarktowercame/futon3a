(ns sidecar.store
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.walk :as walk]
            [sidecar.validation :as v]))

(declare apply-event!)

(defn- now [] (java.time.Instant/now))

(defn- gen-id [prefix]
  (str prefix "-" (subs (str (java.util.UUID/randomUUID)) 0 8)))

(defn- log-root []
  (or (some-> (System/getenv "SIDECAR_LOG_ROOT") str/trim not-empty)
      (some-> (System/getProperty "SIDECAR_LOG_ROOT") str/trim not-empty)
      "log"))

(defn- audit-path []
  (io/file (log-root) "sidecar-audit.edn"))

(defn default-audit-path []
  (audit-path))

(defn- log-serializable [entry]
  (walk/postwalk
   (fn [value]
     (if (instance? java.time.Instant value)
       (java.util.Date/from ^java.time.Instant value)
       value))
   entry))

(defn- parse-log-line [line]
  (let [line (str/replace line
                          #"#object\\[java.time.Instant[^\\\"]*\"([^\"]+)\"\\]"
                          "#inst \"$1\"")]
    (try
      (walk/postwalk
       (fn [value]
         (if (instance? java.util.Date value)
           (.toInstant ^java.util.Date value)
           value))
       (edn/read-string line))
      (catch Throwable _ nil))))

(defn- append-audit! [entry]
  (let [path (audit-path)]
    (.mkdirs (.getParentFile path))
    (spit path (str (pr-str (log-serializable entry)) "\n") :append true)))

(defn- record-audit! [store entry]
  (swap! store update :audit (fnil conj []) entry)
  (append-audit! entry)
  entry)

(defn- record-success! [store event]
  (record-audit! store {:audit/type :success
                        :event event
                        :at (now)}))

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

(defn load-store-from-audit-log
  ([] (load-store-from-audit-log (default-audit-path)))
  ([path]
   (let [path (io/file path)
         store (new-store)]
    (when (.exists path)
      (with-open [r (io/reader path)]
        (doseq [line (line-seq r)]
          (when-not (str/blank? line)
            (when-let [entry (parse-log-line line)]
              (swap! store update :audit (fnil conj []) entry)
              (when (= :success (:audit/type entry))
                (apply-event! store (:event entry))))))))
    store)))

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
      (add-id (:promotion/id fact))
      (add-id (:proposal/id fact)))
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

(defn- failure-entries-for-id [store entity-id]
  (->> (audit-entries-for-id store entity-id)
       (remove #(= :success (:audit/type %)))))

(defn failure-reasons [store entity-id]
  (mapv (fn [entry]
          {:audit/type (:audit/type entry)
           :event/type (get-in entry [:event :event/type])
           :errors (:errors entry)
           :at (:at entry)})
        (failure-entries-for-id store entity-id)))

(defn failure-reasons-by-type [store entity-id]
  (->> (failure-reasons store entity-id)
       (group-by :audit/type)
       (into {} (map (fn [[audit-type entries]]
                       [audit-type (mapv #(dissoc % :audit/type) entries)])))))

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

(defn- fact-related? [snapshot entity-id fact]
  (or (= (:fact/id fact) entity-id)
      (= (:promotion/id fact) entity-id)
      (when-let [promotion (get-in snapshot [:promotions (:promotion/id fact)])]
        (= (:proposal/id promotion) entity-id))))

(defn- success-entries-for
  [records kind time-key entity-id]
  (let [records (case kind
                  :fact (mapcat val records)
                  (vals records))]
    (->> records
       (filter #(matches-entity? entity-id % kind))
       (mapv #(success-entry (case kind
                               :proposal :proposal/recorded
                               :promotion :promotion/recorded
                               :evidence :evidence/attached
                               :action :action/recorded
                               :fact :fact/materialized
                               :chain :chain/built)
                             (get % time-key)
                             %)))))

(defn event-timeline [store entity-id]
  (let [snapshot @store
        fact-success (->> (mapcat val (:facts snapshot))
                          (filter #(fact-related? snapshot entity-id %))
                          (mapv #(success-entry :fact/materialized
                                                (:fact/created-at %)
                                                %)))
        success (concat
                 (success-entries-for (:proposals snapshot) :proposal :proposal/created-at entity-id)
                 (success-entries-for (:promotions snapshot) :promotion :promotion/created-at entity-id)
                 (success-entries-for (:evidence snapshot) :evidence :evidence/created-at entity-id)
                 (success-entries-for (:actions snapshot) :action :action/created-at entity-id)
                 fact-success
                 (success-entries-for (:chains snapshot) :chain :chain/created-at entity-id))
        failures (mapv (fn [entry]
                         {:kind :failure
                          :event/type (get-in entry [:event :event/type])
                          :audit/type (:audit/type entry)
                          :errors (:errors entry)
                          :at (:at entry)
                          :event (:event entry)})
                       (failure-entries-for-id store entity-id))]
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
        (record-success! store event)
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
        (record-success! store event)
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
        (record-success! store event)
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
        (record-success! store event)
        {:ok true :action/id (:action/id action)}))))

(defn- append-fact-event! [store fact]
  (swap! store update-in [:facts (:fact/id fact)] (fnil conj []) fact))

(defn fact-events [store fact-id]
  (->> (get-in @store [:facts fact-id])
       (sort-by :fact/created-at)
       vec))

(defn- active-fact-event? [event]
  (contains? #{:fact :warrant} (:fact/event-type event)))

(defn latest-active-fact [store fact-id]
  (let [events (fact-events store fact-id)
        latest (last events)]
    (when (active-fact-event? latest)
      latest)))

(defn latest-active-state [store fact-id]
  (some-> (latest-active-fact store fact-id)
          (select-keys [:fact/id :fact/kind :fact/event-type
                        :fact/actor :fact/rationale :fact/created-at
                        :promotion/id :fact/event-id :fact/body])))

(defn active-fact-ids [store]
  (->> (:facts @store)
       (keep (fn [[fact-id events]]
               (let [latest (last (sort-by :fact/created-at events))]
                 (when (active-fact-event? latest)
                   fact-id))))
       sort
       vec))

(defn record-fact!
  [store fact opts]
  (let [promotion-id (:promotion/id opts)
        fact-id (:fact/id fact)
        fact (assoc fact
                    :fact/created-at (or (:fact/created-at fact) (now))
                    :fact/event-id (or (:fact/event-id fact) (gen-id "fact-event")))
        event {:event/type :fact/materialized
               :event/id (gen-id "event")
               :event/at (now)
               :fact fact}
        result (v/validate-event event)
        event-type (:fact/event-type fact)
        promotion (when promotion-id (get-in @store [:promotions promotion-id]))
        proposal-id (or (:proposal/id fact) (:proposal/id promotion))
        existing-events (get-in @store [:facts fact-id])
        existing-event-id? (some #(= (:fact/event-id %) (:fact/event-id fact)) existing-events)]
    (cond
      (not (:ok? result))
      (fail! store event (:errors result) :validation-failure)

      (and (contains? #{:fact :warrant} event-type)
           (nil? promotion-id))
      (fail! store event [{:field :promotion/id :type :missing :msg "promotion required for fact events"}]
             :boundary-violation)

      (and (contains? #{:fact :warrant} event-type)
           (nil? promotion))
      (fail! store event [{:field :promotion/id :type :missing :msg "promotion not found"}]
             :boundary-violation)

      (and promotion
           (:promotion/kind promotion)
           (not= (:promotion/kind promotion) (:fact/kind fact)))
      (fail! store event [{:field :fact/kind :type :mismatch :msg "fact kind mismatches promotion"}]
             :boundary-violation)

      (and (= :fact event-type) (seq existing-events))
      (fail! store event [{:field :fact/id :type :duplicate :msg "fact already recorded"}]
             :append-only-violation)

      (and (contains? #{:warrant :retired} event-type)
           (empty? existing-events))
      (fail! store event [{:field :fact/id :type :missing :msg "fact not found"}]
             :boundary-violation)

      existing-event-id?
      (fail! store event [{:field :fact/event-id :type :duplicate :msg "fact event already recorded"}]
             :append-only-violation)

      :else
      (let [fact (cond-> fact
                   promotion-id (assoc :promotion/id promotion-id)
                   proposal-id (assoc :proposal/id proposal-id))
            event (assoc event :fact fact)]
        (append-fact-event! store fact)
        (record-success! store event)
        {:ok true :fact/id (:fact/id fact)}))))

(defn record-bridge-triple!
  [store bridge opts]
  (let [fact {:fact/id (:bridge/id bridge)
              :fact/kind :bridge-triple
              :fact/body bridge
              :fact/created-at (:bridge/created-at bridge)
              :fact/event-type (or (:fact/event-type opts) :fact)
              :fact/actor (or (:fact/actor opts) (:bridge/actor bridge) "bridge-triple")
              :fact/rationale (or (:fact/rationale opts)
                                  (:bridge/rationale bridge)
                                  "bridge triple promotion")}]
    (if-let [result (record-fact! store fact opts)]
      (if (:ok result)
        (do
          (swap! store assoc-in [:bridge-triples (:bridge/id bridge)]
                 (assoc bridge :promotion/id (:promotion/id opts)))
          result)
        result)
      {:ok false :errors [{:field :bridge/id :type :invalid :msg "bridge write failed"}]})))

(def ^:private base-weights {:arrow 3.0 :bridge 2.0 :proposal 1.0})
(def ^:private softness-weights {:arrow 0.0 :bridge 0.5 :proposal 1.0})
(def ^:private sense-shift-allowed-gates {:arrow :typed-arrow :bridge :bridge-triple})
(def ^:private sense-shift-penalty 0.5)

(defn- step-evidence [store step]
  (case (:step/type step)
    :proposal (some-> (get-in @store [:proposals (:step/id step)])
                      (select-keys [:proposal/id :proposal/method :proposal/score :proposal/evidence]))
    :bridge (get-in @store [:bridge-triples (:step/id step)])
    :arrow {:arrow/id (:step/id step)}
    nil))

(defn- shift-annotation [step]
  (when (true? (:step/shift? step))
    (let [required (get sense-shift-allowed-gates (:step/type step))
          gate (:step/gate step)]
      {:shift/required required
       :shift/gate gate
       :shift/allowed? (= required gate)})))

(defn- step-score [store step]
  (let [annotation (shift-annotation step)
        shift-penalty (if (and annotation (:shift/allowed? annotation))
                        sense-shift-penalty
                        0.0)
        base-softness (get softness-weights (:step/type step) 0.0)]
    {:step/id (:step/id step)
     :step/type (:step/type step)
     :step/shift? (:step/shift? step)
     :step/gate (:step/gate step)
     :step/annotation annotation
     :score/base (get base-weights (:step/type step) 0.0)
     :score/shift-penalty shift-penalty
     :score/softness (+ base-softness shift-penalty)
     :step/evidence (step-evidence store step)}))

(defn- chain-score [store steps]
  (let [scored (mapv (fn [step] (step-score store step)) steps)
        base-total (reduce + 0.0 (map :score/base scored))
        shift-total (reduce + 0.0 (map :score/shift-penalty scored))
        softness-total (reduce + 0.0 (map :score/softness scored))
        average (if (seq scored)
                  (/ softness-total (count scored))
                  0.0)]
    {:score/base base-total
     :score/shift-penalty shift-total
     :softness/total softness-total
     :softness/average average
     :score/steps scored}))

(defn- sense-shift-issue [step]
  (when (true? (:step/shift? step))
    (let [required (get sense-shift-allowed-gates (:step/type step))]
      (cond
        (nil? required)
        {:field :step/type :type :invalid :msg "sense shift not allowed for step type"}

        (not= (:step/gate step) required)
        {:field :step/gate
         :type :invalid
         :msg "ungated sense shift"
         :detail {:required required :actual (:step/gate step)}}))))

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
                                  steps))
        sense-shift-error (first
                           (keep-indexed
                            (fn [idx step]
                              (when-let [issue (sense-shift-issue step)]
                                (assoc issue :detail (merge (:detail issue)
                                                            {:step/index idx
                                                             :step/id (:step/id step)
                                                             :step/type (:step/type step)}))))
                            steps))]
    (cond
      (not (:ok? result))
      (fail! store event (:errors result) :validation-failure)

      missing-step
      (fail! store event [missing-step] :boundary-violation)

      sense-shift-error
      (fail! store event [sense-shift-error] :drift)

      (get-in @store [:chains chain-id])
      (fail! store event [{:field :chain/id :type :duplicate :msg "chain already recorded"}]
             :append-only-violation)

      :else
      (let [scoring (chain-score store steps)
            chain-record (merge chain scoring)]
        (swap! store assoc-in [:chains chain-id] chain-record)
        (record-success! store event)
        {:ok true :chain/id chain-id :scoring scoring}))))

(defn apply-event!
  [store event]
  (case (:event/type event)
    :proposal/recorded (swap! store assoc-in [:proposals (get-in event [:proposal :proposal/id])]
                              (:proposal event))
    :promotion/recorded (swap! store assoc-in [:promotions (get-in event [:promotion :promotion/id])]
                               (:promotion event))
    :evidence/attached (swap! store assoc-in [:evidence (get-in event [:evidence :evidence/id])]
                              (:evidence event))
    :action/recorded (swap! store assoc-in [:actions (get-in event [:action :action/id])]
                            (:action event))
    :fact/materialized (append-fact-event! store (:fact event))
    :chain/built (let [chain (:chain event)
                       scoring (chain-score store (:chain/steps chain))
                       chain-record (merge chain scoring)]
                   (swap! store assoc-in [:chains (:chain/id chain)] chain-record))
    nil))
