(ns futon3a.affect
  "Affect signal processor for MUSN activity streams.

   Subscribes to activity events, detects affect-laden utterances,
   tracks novel terms within a lookahead window, and emits
   affect/transition events when detected.

   Note: Affect detection uses keyword/phrase matching via nlp-interface,
   not ML or Stanford NLP. This keeps futon3a lightweight - heavy NLP
   dependencies stay isolated in futon1."
  (:require [clojure.string :as str]
            [nlp-interface.intent :as intent]
            [cheshire.core :as json]
            [org.httpkit.client :as http])
  (:import (java.time Instant)))

;; Affect intents we care about (subset from nlp-interface)
(def ^:private affect-intents
  #{:activation :attraction :joy :fatigue :anxiety
    :withdrawal :frustration :sadness :numbness
    :orientation :social :regulation})

;; Configuration
(def ^:private default-config
  {:lookahead-minutes 10      ; window for novel terms after affect
   :novelty-minutes (* 60 24 30)  ; 30 days - term is "novel" if not seen recently
   :max-pending 100           ; max pending affect events per actor
   :musn-url "http://localhost:6065"
   :futon1-url "http://localhost:8080"  ; futon1 API for NLP entity extraction
   :use-nlp-entities? true})

(defonce ^:private !config (atom default-config))
(defonce ^:private !pending (atom {}))  ; actor -> [{:affect :ts :expires-at :terms}]
(defonce ^:private !term-history (atom {}))  ; actor -> {term -> last-seen-ts}

(defn configure!
  "Update processor configuration."
  [opts]
  (swap! !config merge (select-keys opts [:lookahead-minutes :novelty-minutes
                                          :max-pending :musn-url
                                          :futon1-url :use-nlp-entities?])))

(defn state
  "Return current processor state for debugging."
  []
  {:config @!config
   :pending-counts (into {} (map (fn [[k v]] [k (count v)]) @!pending))
   :term-history-counts (into {} (map (fn [[k v]] [k (count v)]) @!term-history))})

;; Intent analysis (keyword-based, not ML)

(defn- affect-intent
  "Analyze text and return affect intent if detected, nil otherwise.
   Uses stemmed keyword matching against affect dictionaries."
  [text]
  (when (and text (not (str/blank? text)))
    (let [{:keys [type conf]} (intent/analyze text)]
      (when (contains? affect-intents type)
        {:type type
         :conf (double (or conf 0.0))}))))

;; NLP entity extraction via futon1

(defn- fetch-nlp-entities
  "Call futon1's NLP API to extract entities from text.
   Returns vector of lowercase entity labels, or nil on failure."
  [text]
  (let [{:keys [futon1-url]} @!config
        url (str futon1-url "/api/alpha/nlp/entities")]
    (try
      (let [resp @(http/post url
                             {:headers {"Content-Type" "application/json"}
                              :body (json/generate-string {:text text})
                              :timeout 5000})
            body (when (:body resp)
                   (json/parse-string (:body resp) true))]
        (when (and (= 200 (:status resp))
                   (seq (:labels body)))
          (:labels body)))
      (catch Exception e
        (println "[futon3a.affect] NLP fetch failed:" (.getMessage e))
        nil))))

;; Novelty tracking

(defn- extract-terms-fallback
  "Fallback term extraction using regex (words 6+ chars)."
  [text]
  (when text
    (->> (re-seq #"\b[a-zA-Z]{6,}\b" text)
         (map str/lower-case)
         distinct
         vec)))

(defn- extract-terms
  "Extract potential affect-related terms from text.
   Uses futon1's NLP API for proper entity extraction when available,
   falls back to regex-based extraction (6+ char words) otherwise."
  [text]
  (when text
    (let [{:keys [use-nlp-entities?]} @!config]
      (if use-nlp-entities?
        (or (fetch-nlp-entities text)
            (extract-terms-fallback text))
        (extract-terms-fallback text)))))

(defn- novel-term?
  "Check if term is novel for this actor (not seen in novelty window)."
  [actor term ts]
  (let [{:keys [novelty-minutes]} @!config
        history (get-in @!term-history [actor term])
        cutoff (when history
                 (.minusSeconds ts (* 60 (long novelty-minutes))))]
    (or (nil? history)
        (.isBefore history cutoff))))

(defn- record-terms!
  "Record that actor used these terms at ts."
  [actor terms ts]
  (swap! !term-history
         (fn [state]
           (reduce (fn [s term]
                     (assoc-in s [actor term] ts))
                   state
                   terms))))

;; Pending affect tracking

(defn- prune-expired
  "Remove expired pending affects."
  [pending ts]
  (filterv (fn [{:keys [expires-at]}]
             (and expires-at (.isAfter expires-at ts)))
           pending))

(defn- add-pending
  "Add a new pending affect event."
  [pending {:keys [affect ts actor event-id text]}]
  (let [{:keys [lookahead-minutes max-pending]} @!config
        expires-at (.plusSeconds ts (* 60 (long lookahead-minutes)))
        item {:affect affect
              :ts ts
              :expires-at expires-at
              :event-id event-id
              :text text
              :terms []}
        items (conj (vec pending) item)
        keep-from (max 0 (- (count items) (long max-pending)))]
    (subvec items keep-from)))

(defn- update-pending-terms
  "Add novel terms to all non-expired pending affects."
  [pending terms ts]
  (mapv (fn [item]
          (if (.isAfter (:expires-at item) ts)
            (update item :terms into terms)
            item))
        pending))

;; Transition detection

(defn- check-transitions!
  "Check for completed transitions and emit events.
   A transition fires when a pending affect has accumulated novel terms."
  [actor ts broadcast-fn]
  (let [pending (get @!pending actor [])
        expired (filterv (fn [{:keys [expires-at terms]}]
                           (and (.isBefore expires-at ts)
                                (seq terms)))
                         pending)]
    (doseq [{:keys [affect terms text event-id] :as item} expired]
      (let [transition {:event/type "affect/transition"
                        :actor actor
                        :affect {:type (name (:type affect))
                                 :conf (:conf affect)}
                        :terms (vec (distinct terms))
                        :trigger-text text
                        :trigger-event-id event-id
                        :at (str (:ts item))}]
        (broadcast-fn transition)))))

;; Event processing

(defn- parse-ts
  "Parse timestamp from various formats."
  [ts-val]
  (cond
    (instance? Instant ts-val) ts-val
    (string? ts-val) (try (Instant/parse ts-val)
                          (catch Exception _
                            (try
                              ;; Try parsing "Wed Jan 28 21:45:00 UTC 2026" format
                              (let [fmt (java.time.format.DateTimeFormatter/ofPattern
                                          "EEE MMM dd HH:mm:ss zzz yyyy"
                                          java.util.Locale/ENGLISH)]
                                (.toInstant (java.time.ZonedDateTime/parse ts-val fmt)))
                              (catch Exception _ (Instant/now)))))
    :else (Instant/now)))

(defn process-activity-event!
  "Process an incoming activity event for affect signals.

   event should have:
     :agent - actor identifier
     :at - timestamp
     :text or :content - the text to analyze (optional)
     :event/type - event type
     :session/id - session identifier

   broadcast-fn is called with transition events to emit."
  [event broadcast-fn]
  (let [actor (or (:agent event) (:actor event) "unknown")
        ts (parse-ts (:at event))
        ;; Look for text in various places
        text (or (:text event)
                 (:content event)
                 (get-in event [:metadata :text])
                 (get-in event [:metadata :prompt])
                 (get-in event [:payload :content]))
        event-id (or (:session/id event) (str (java.util.UUID/randomUUID)))]

    ;; Check for affect in the text
    (when-let [affect (affect-intent text)]
      (swap! !pending
             (fn [state]
               (let [current (get state actor [])
                     current (prune-expired current ts)
                     current (add-pending current {:affect affect
                                                   :ts ts
                                                   :actor actor
                                                   :event-id event-id
                                                   :text text})]
                 (assoc state actor current)))))

    ;; Extract and process novel terms
    (let [terms (extract-terms text)
          novel (filterv #(novel-term? actor % ts) terms)]
      (when (seq novel)
        (record-terms! actor novel ts)
        (swap! !pending
               (fn [state]
                 (let [current (get state actor [])
                       current (update-pending-terms current novel ts)]
                   (assoc state actor current))))))

    ;; Check for completed transitions
    (check-transitions! actor ts broadcast-fn)))

;; MUSN integration

(defn- post-to-musn!
  "Post an event to MUSN activity log."
  [event]
  (let [{:keys [musn-url]} @!config
        url (str musn-url "/musn/activity/log")]
    (try
      @(http/post url
                  {:headers {"Content-Type" "application/json"}
                   :body (json/generate-string event)})
      (catch Exception e
        (println "[futon3a.affect] Failed to post to MUSN:" (.getMessage e))))))

(defn broadcast-transition!
  "Default broadcast function - posts transition to MUSN."
  [transition]
  (println "[futon3a.affect] Transition detected:"
           (:actor transition) "->"
           (get-in transition [:affect :type])
           "terms:" (:terms transition))
  (post-to-musn! transition))

;; Public API

(defn process!
  "Process an activity event with default MUSN broadcast."
  [event]
  (process-activity-event! event broadcast-transition!))

(defn reset-state!
  "Clear all pending state (for testing)."
  []
  (reset! !pending {})
  (reset! !term-history {}))
