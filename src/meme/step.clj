(ns meme.step
  "Pure transition kernel shared by live meme promotion and rollout simulation.")

(defn endpoint-key [{:keys [have want]}]
  [have want])

(defn advances-cap [arrow-or-leaf]
  (or (:advances-cap arrow-or-leaf)
      (:advances_cap arrow-or-leaf)))

(defn cap-key [cap-id]
  (when cap-id (name cap-id)))

(defn cap-entity [state cap-id]
  (get-in state [:cap-overlay (cap-key cap-id)]))

(defn cap-frontier? [cap]
  (true? (get-in cap [:props :capability/frontier?])))

(defn cap-target-status [cap]
  (if (cap-frontier? cap) :claimed :satisfied))

(defn- update-cap [cap target]
  (update cap :props
          (fn [props]
            (cond-> (assoc (or props {}) :capability/status target)
              (= target :claimed) (assoc :capability/claimed? true)))))

(defn- arrow-from-state [state leaf]
  (let [key (endpoint-key leaf)]
    (merge {:have (:have leaf)
            :want (:want leaf)
            :status :open}
           (get-in state [:arrows key])
           (select-keys leaf [:have :want :advances-cap :move/id :move/class
                              :move/terminal?]))))

(defn step
  "Pure state transition.

   State shape:
   {:arrows {[have want] {:have :want :status :advances-cap ...}}
    :cap-overlay {cap-id {:id :props {...}}}
    :reachable #{scope-id ...}
    :truncated? boolean
    :trace [...]}

   Leaf shape is a move/arrow map with :have, :want, optional :advances-cap,
   optional :to-state, and optional :move/terminal?."
  [state leaf]
  (if (:truncated? state)
    (update state :trace (fnil conj []) {:op :truncated-carry
                                         :move/id (:move/id leaf)})
    (let [key (endpoint-key leaf)
          arrow (arrow-from-state state leaf)
          to-state (or (:to-state leaf) :constructed)
          cap-id (advances-cap arrow)
          cap (cap-entity state cap-id)
          terminal? (true? (:move/terminal? leaf))]
      (cond
        terminal?
        (-> state
            (assoc :truncated? true)
            (update :trace (fnil conj [])
                    {:op :terminal
                     :move/id (:move/id leaf)
                     :have (:have leaf)
                     :want (:want leaf)}))

        (and (= :constructed to-state) cap-id (nil? cap))
        (throw (ex-info "unknown capability id in pure step"
                        {:reason :capability/unknown
                         :cap-id (cap-key cap-id)
                         :move/id (:move/id leaf)}))

        :else
        (let [cap-target (when (and (= :constructed to-state) cap)
                           (cap-target-status cap))]
          (cond-> state
            true
            (assoc-in [:arrows key]
                      (assoc arrow
                             :status to-state
                             :constructed? (= :constructed to-state)))

            (= :constructed to-state)
            (update :reachable (fnil conj #{}) (:want leaf))

            cap-target
            (assoc-in [:cap-overlay (cap-key cap-id)] (update-cap cap cap-target))

            true
            (update :trace (fnil conj [])
                    (cond-> {:op :step
                             :move/id (:move/id leaf)
                             :have (:have leaf)
                             :want (:want leaf)
                             :from (:status arrow)
                             :to to-state}
                      cap-target
                      (assoc :advances-cap (cap-key cap-id)
                             :capability/target-status cap-target
                             :capability/frontier? (cap-frontier? cap))))))))))
