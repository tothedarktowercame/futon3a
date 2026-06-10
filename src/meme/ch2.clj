(ns meme.ch2
  "CH2 discharge-event emission for live meme arrow construction."
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(def default-sink
  "data/ch2-discharge-events.edn")

(defn discharge-event
  [move-id sorry-ref ts]
  {:ch2/discharge-event true
   :move/id move-id
   :discharged? true
   :at ts
   :sorry-ref sorry-ref})

(defn- valid-sorry-ref? [x]
  (and (string? x)
       (boolean (re-matches #".+/sorry/meme-arrow-.+" x))))

(defn discharge-event? [x]
  (and (map? x)
       (true? (:ch2/discharge-event x))
       (true? (:discharged? x))
       (string? (:move/id x))
       (valid-sorry-ref? (:sorry-ref x))
       (not (contains? x :peradam))
       (not (contains? x :q))))

(defn- append-edn-line! [sink event]
  (let [f (io/file sink)]
    (when-let [parent (.getParentFile f)]
      (.mkdirs parent))
    (spit f (str (pr-str event) "\n") :append true)))

(defn emit-discharge-event!
  [event & {:keys [sink]
            :or {sink default-sink}}]
  (when-not (discharge-event? event)
    (throw (ex-info "refusing to emit non-CH2 discharge event"
                    {:reason :ch2/invalid-discharge-event
                     :event event})))
  (append-edn-line! sink event)
  event)

(defn read-events
  "Read an append-only EDN-line CH2 event sink."
  [sink]
  (let [f (io/file sink)]
    (if (.exists f)
      (->> (str/split-lines (slurp f))
           (remove str/blank?)
           (mapv edn/read-string))
      [])))
