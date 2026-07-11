(ns meme.ch2
  "CH2 discharge-event emission for live meme arrow construction."
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(def default-sink
  "data/ch2-discharge-events.edn")

(defn fold-event
  "A CH2 fold event. `discharged?` true = the move closed its sorry; false = a
  RECORDED FAILED fold — the β term of closure-folds.edn's recording discipline
  (claude-1, 2026-06-10: the signal is only dense if BOTH outcomes are written).
  Negatives must carry evidence of the attempt: a non-empty `:used` pattern
  vector and/or a `:note`."
  [move-id sorry-ref ts discharged? & {:keys [used note]}]
  (cond-> {:ch2/discharge-event true
           :move/id move-id
           :discharged? (boolean discharged?)
           :at ts
           :sorry-ref sorry-ref}
    used (assoc :used (vec used))
    note (assoc :note note)))

(defn discharge-event
  [move-id sorry-ref ts]
  (fold-event move-id sorry-ref ts true))

(defn- valid-sorry-ref? [x]
  ;; Two ref grammars: meme-arrow sorries (the original CH2 seam) and
  ;; fold-turn deposit refs (escrow-lane adjudications — deposits carry
  ;; :proposal/hash attribution but no meme-arrow id; B4, 2026-07-11,
  ;; flagged during FLIGHTS-2026-07-10 when flight adjudications had no
  ;; emittable ref form).
  (and (string? x)
       (boolean (or (re-matches #".+/sorry/meme-arrow-.+" x)
                    (re-matches #".+/fold-turn/ft-.+" x)))))

(defn- valid-used? [x]
  (or (nil? x)
      (and (vector? x) (seq x) (every? string? x))))

(defn fold-event? [x]
  (boolean
   (and (map? x)
        (true? (:ch2/discharge-event x))
        (boolean? (:discharged? x))
        (string? (:move/id x))
        (valid-sorry-ref? (:sorry-ref x))
        (valid-used? (:used x))
        ;; a recorded FAILURE without evidence of the attempt is not a record
        (or (true? (:discharged? x))
            (seq (:used x))
            (string? (:note x)))
        (not (contains? x :peradam))
        (not (contains? x :q)))))

(defn discharge-event? [x]
  (and (fold-event? x) (true? (:discharged? x))))

(defn- append-edn-line! [sink event]
  (let [f (io/file sink)]
    (when-let [parent (.getParentFile f)]
      (.mkdirs parent))
    (spit f (str (pr-str event) "\n") :append true)))

(defn emit-fold-event!
  "Emit any valid CH2 fold event — positive (discharged) or negative (recorded
  failed fold with attempt evidence)."
  [event & {:keys [sink]
            :or {sink default-sink}}]
  (when-not (fold-event? event)
    (throw (ex-info "refusing to emit non-CH2 fold event"
                    {:reason :ch2/invalid-fold-event
                     :event event})))
  (append-edn-line! sink event)
  event)

(defn emit-discharge-event!
  "Strict-positive emitter (back-compat): refuses `:discharged? false` events;
  use `emit-fold-event!` to record failed folds."
  [event & {:keys [sink]
            :or {sink default-sink}}]
  (when-not (discharge-event? event)
    (throw (ex-info "refusing to emit non-CH2 discharge event"
                    {:reason :ch2/invalid-discharge-event
                     :event event})))
  (emit-fold-event! event :sink sink))

(defn read-events
  "Read an append-only EDN-line CH2 event sink."
  [sink]
  (let [f (io/file sink)]
    (if (.exists f)
      (->> (str/split-lines (slurp f))
           (remove str/blank?)
           (mapv edn/read-string))
      [])))
