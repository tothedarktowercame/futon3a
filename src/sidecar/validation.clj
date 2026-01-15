(ns sidecar.validation
  (:require [clojure.set :as set]
            [clojure.string :as str]))

(defn- blankish? [value]
  (or (nil? value)
      (and (string? value) (str/blank? value))))

(defn- instantish? [value]
  (or (instance? java.time.Instant value)
      (instance? java.util.Date value)))

(defn- add-error [errors field type msg & [detail]]
  (conj errors (cond-> {:field field :type type :msg msg}
                 detail (assoc :detail detail))))

(defn- require-fields [errors m fields]
  (reduce (fn [errs field]
            (if (contains? m field)
              errs
              (add-error errs field :missing "required field missing")))
          errors
          fields))

(defn- forbid-unknown [errors m allowed]
  (let [unknown (set/difference (set (keys m)) allowed)]
    (if (seq unknown)
      (add-error errors :unknown-fields :unknown "unknown fields present" (vec (sort unknown)))
      errors)))

(defn- require-string [errors m field]
  (if (blankish? (get m field))
    (add-error errors field :invalid "expected non-blank string")
    errors))

(defn- require-keyword [errors m field]
  (if (keyword? (get m field))
    errors
    (add-error errors field :invalid "expected keyword")))

(defn- require-enum [errors m field allowed]
  (let [value (get m field)]
    (if (contains? allowed value)
      errors
      (add-error errors field :invalid "unexpected enum value" (vec (sort allowed))))))

(defn- require-number-range [errors m field low high]
  (let [value (get m field)]
    (if (and (number? value) (<= low value) (<= value high))
      errors
      (add-error errors field :invalid "expected number in range" {:min low :max high}))))

(defn- require-mapish [errors m field]
  (let [value (get m field)]
    (if (map? value)
      errors
      (add-error errors field :invalid "expected map"))))

(defn- require-collection [errors m field]
  (let [value (get m field)]
    (if (coll? value)
      errors
      (add-error errors field :invalid "expected collection"))))

(defn- require-instantish [errors m field]
  (let [value (get m field)]
    (if (instantish? value)
      errors
      (add-error errors field :invalid "expected timestamp"))))

(def proposal-allowed-keys
  #{:proposal/id :proposal/kind :proposal/target-id :proposal/status
    :proposal/score :proposal/method :proposal/evidence :proposal/created-at})

(def promotion-allowed-keys
  #{:promotion/id :proposal/id :promotion/kind :promotion/target-id
    :promotion/decided-by :promotion/rationale :promotion/created-at})

(def evidence-allowed-keys
  #{:evidence/id :evidence/target :evidence/method :evidence/payload :evidence/created-at})

(def action-allowed-keys
  #{:action/id :action/type :action/actor :action/note :action/created-at})

(def fact-allowed-keys
  #{:fact/id :fact/kind :fact/body :fact/created-at})

(def chain-step-allowed-keys
  #{:step/type :step/id :step/shift? :step/gate :step/notes})

(def chain-step-types #{:arrow :bridge :proposal})
(def sense-shift-gates #{:typed-arrow :bridge-triple})

(defn validate-proposal [proposal]
  (let [errors (-> []
                   (require-fields proposal [:proposal/id :proposal/kind :proposal/status
                                             :proposal/score :proposal/method
                                             :proposal/evidence :proposal/created-at])
                   (forbid-unknown proposal proposal-allowed-keys)
                   (require-string proposal :proposal/id)
                   (require-keyword proposal :proposal/kind)
                   (require-enum proposal :proposal/status #{:pending :accepted :rejected})
                   (require-number-range proposal :proposal/score 0.0 1.0)
                   (require-string proposal :proposal/method)
                   (require-collection proposal :proposal/evidence)
                   (require-instantish proposal :proposal/created-at))]
    {:ok? (empty? errors) :errors errors}))

(defn validate-promotion [promotion]
  (let [errors (-> []
                   (require-fields promotion [:promotion/id :proposal/id :promotion/kind
                                              :promotion/decided-by :promotion/rationale
                                              :promotion/created-at])
                   (forbid-unknown promotion promotion-allowed-keys)
                   (require-string promotion :promotion/id)
                   (require-string promotion :proposal/id)
                   (require-keyword promotion :promotion/kind)
                   (require-string promotion :promotion/decided-by)
                   (require-string promotion :promotion/rationale)
                   (require-instantish promotion :promotion/created-at))]
    {:ok? (empty? errors) :errors errors}))

(defn- validate-evidence-target [errors target]
  (if (and (map? target)
           (contains? #{:proposal :promotion} (:type target))
           (string? (:id target))
           (not (str/blank? (:id target))))
    errors
    (add-error errors :evidence/target :invalid "expected {:type :proposal|:promotion :id <string>}")))

(defn validate-evidence [evidence]
  (let [errors (-> []
                   (require-fields evidence [:evidence/id :evidence/target :evidence/method
                                             :evidence/payload :evidence/created-at])
                   (forbid-unknown evidence evidence-allowed-keys)
                   (require-string evidence :evidence/id)
                   (require-string evidence :evidence/method)
                   (require-collection evidence :evidence/payload)
                   (require-instantish evidence :evidence/created-at))
        errors (validate-evidence-target errors (:evidence/target evidence))]
    {:ok? (empty? errors) :errors errors}))

(defn validate-action [action]
  (let [errors (-> []
                   (require-fields action [:action/id :action/type :action/created-at])
                   (forbid-unknown action action-allowed-keys)
                   (require-string action :action/id)
                   (require-keyword action :action/type)
                   (require-instantish action :action/created-at))]
    {:ok? (empty? errors) :errors errors}))

(defn validate-fact [fact]
  (let [errors (-> []
                   (require-fields fact [:fact/id :fact/kind :fact/created-at])
                   (forbid-unknown fact fact-allowed-keys)
                   (require-string fact :fact/id)
                   (require-keyword fact :fact/kind)
                   (require-instantish fact :fact/created-at))]
    {:ok? (empty? errors) :errors errors}))

(defn validate-chain-step [step]
  (let [errors (-> []
                   (require-fields step [:step/type :step/id])
                   (forbid-unknown step chain-step-allowed-keys)
                   (require-keyword step :step/type)
                   (require-string step :step/id)
                   (require-enum step :step/type chain-step-types))
        errors (if (true? (:step/shift? step))
                 (require-enum errors step :step/gate sense-shift-gates)
                 errors)]
    {:ok? (empty? errors) :errors errors}))

(defn validate-chain [steps]
  (let [steps (vec (or steps []))
        base-errors (if (seq steps)
                      []
                      [{:field :chain/steps :type :missing :msg "chain steps required"}])
        errors (reduce (fn [errs step]
                         (let [result (validate-chain-step step)]
                           (if (:ok? result)
                             errs
                             (conj errs {:field :chain/steps
                                         :type :invalid
                                         :msg "invalid chain step"
                                         :detail (:errors result)}))))
                       base-errors
                       steps)]
    {:ok? (empty? errors) :errors errors}))

(def event-specs
  {:proposal/recorded {:key :proposal :validator validate-proposal
                       :allowed #{:event/type :event/id :event/at :proposal}}
   :promotion/recorded {:key :promotion :validator validate-promotion
                        :allowed #{:event/type :event/id :event/at :promotion}}
   :evidence/attached {:key :evidence :validator validate-evidence
                       :allowed #{:event/type :event/id :event/at :evidence}}
   :action/recorded {:key :action :validator validate-action
                     :allowed #{:event/type :event/id :event/at :action}}
   :fact/materialized {:key :fact :validator validate-fact
                       :allowed #{:event/type :event/id :event/at :fact}}
   :chain/built {:key :chain :validator (fn [chain] (validate-chain (:chain/steps chain)))
                 :allowed #{:event/type :event/id :event/at :chain}}})

(defn validate-event [event]
  (let [event-type (:event/type event)
        spec (get event-specs event-type)
        envelope-errors (-> []
                            (require-string event :event/id)
                            (require-instantish event :event/at))
        errors (cond
                 (nil? event-type)
                 (conj envelope-errors {:field :event/type :type :missing :msg "event type required"})

                 (nil? spec)
                 (conj envelope-errors {:field :event/type :type :invalid :msg "unknown event type" :detail event-type})

                 :else
                 (let [errors (forbid-unknown envelope-errors event (:allowed spec))
                       payload (get event (:key spec))
                       result ((:validator spec) payload)]
                   (if (:ok? result)
                     errors
                     (conj errors {:field (:key spec)
                                   :type :invalid
                                   :msg "event payload invalid"
                                   :detail (:errors result)}))))]
    {:ok? (empty? errors) :errors errors}))
