(ns musn.core
  (:require [clojure.data.json :as json]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(defonce ^:private state (atom {:sessions {}}))

(defn- now []
  (java.time.Instant/now))

(defn- gen-session-id []
  (str "musn-" (subs (str (java.util.UUID/randomUUID)) 0 8)))

(defn- log-root []
  (or (some-> (System/getenv "MUSN_LOG_ROOT") not-empty)
      "log"))

(defn- log-path [sid]
  (io/file (log-root) (str sid ".edn")))

(defn- append-log! [sid entry]
  (let [path (log-path sid)]
    (.mkdirs (.getParentFile path))
    (spit path (str (pr-str entry) "\n") :append true)))

(defn- require-session [sid]
  (let [session (get-in @state [:sessions sid])]
    (when-not session
      (throw (ex-info "unknown session" {:session/id sid})))
    session))

(defn- futon1-url []
  (or (some-> (System/getenv "MUSN_FUTON1_URL") not-empty)
      "http://localhost:8080/api/alpha"))

(defn- futon1-profile []
  (some-> (System/getenv "MUSN_FUTON1_PROFILE") str/trim not-empty))

(defn- fetch-json
  [url headers]
  (let [conn (.openConnection (java.net.URL. url))]
    (doseq [[k v] headers]
      (.setRequestProperty conn k v))
    (with-open [r (io/reader (.getInputStream conn))]
      (json/read r :key-fn keyword))))

(defn- tokenize [text]
  (->> (str/split (str/lower-case (or text "")) #"[^a-z0-9._-]+")
       (remove str/blank?)
       distinct
       vec))

(defn- candidate-id [entity]
  (or (:external-id entity)
      (:name entity)
      (:id entity)))

(defn- score-candidate [tokens entity]
  (let [haystack (str/lower-case (str (candidate-id entity)))]
    (count (filter #(str/includes? haystack %) tokens))))

(defn- futon1-candidates
  [intent limit]
  (let [url (str (str/replace (futon1-url) #"/+$" "") "/patterns/registry")
        headers (cond-> {"accept" "application/json"}
                  (futon1-profile) (assoc "x-profile" (futon1-profile)))
        payload (fetch-json url headers)
        entities (get-in payload [:registry :entities])
        tokens (tokenize intent)
        scored (->> (or entities [])
                    (map (fn [entity]
                           (let [score (score-candidate tokens entity)]
                             (assoc entity :score score))))
                    (sort-by (juxt (comp - :score) :name))
                    (take (max 1 limit))
                    vec)]
    (mapv (fn [entity]
            {:id (candidate-id entity)
             :name (:name entity)
             :score (:score entity)})
          scored)))

(defn- default-guidance [intent]
  (let [limit (or (some-> (System/getenv "MUSN_PATTERN_LIMIT") not-empty Integer/parseInt)
                  4)
        candidates (try
                     (futon1-candidates intent limit)
                     (catch Throwable _ []))
        ids (->> candidates (map :id) (remove nil?) vec)]
    {:intent (or intent "unspecified")
     :candidates ids
     :candidate-details candidates
     :constraints []}))

(defn create-session!
  ([]
   (create-session! {}))
  ([opts]
   (let [opts (or opts {})
         sid (or (:session/id opts) (gen-session-id))
         guidance-fn (or (:guidance-fn opts) default-guidance)
         session {:session/id sid
                  :turn 0
                  :guidance-fn guidance-fn
                  :turn-state nil}]
     (swap! state assoc-in [:sessions sid] session)
     (append-log! sid {:event/type :session/create
                       :at (now)
                       :session/id sid})
     {:session/id sid})))

(defn start-turn!
  [sid opts]
  (let [session (require-session sid)
        turn (inc (:turn session))
        intent (:intent opts)
        guidance ((:guidance-fn session) intent)
        turn-state {:turn turn
                    :intent intent
                    :guidance guidance
                    :selections []
                    :actions []
                    :evidence []}]
    (swap! state assoc-in [:sessions sid]
           (assoc session
                  :turn turn
                  :turn-state turn-state))
    (append-log! sid {:event/type :turn/start
                      :at (now)
                      :session/id sid
                      :turn turn
                      :guidance guidance})
    {:session/id sid :turn turn :guidance guidance}))

(defn select!
  [sid opts]
  (let [session (require-session sid)
        turn-state (:turn-state session)
        turn (:turn opts)
        pattern-id (:pattern/id opts)
        reason (:reason opts)
        candidates (:candidates opts)
        entry {:event/type :turn/select
               :at (now)
               :session/id sid
               :turn (or turn (:turn turn-state))
               :pattern/id pattern-id
               :reason reason
               :candidates (vec (or candidates []))}]
    (swap! state update-in [:sessions sid :turn-state :selections]
           (fnil conj []) entry)
    (append-log! sid entry)
    entry))

(defn action!
  [sid opts]
  (let [session (require-session sid)
        turn-state (:turn-state session)
        turn (:turn opts)
        pattern-id (:pattern/id opts)
        action (:action opts)
        note (:note opts)
        files (:files opts)
        entry {:event/type :turn/action
               :at (now)
               :session/id sid
               :turn (or turn (:turn turn-state))
               :pattern/id pattern-id
               :action action
               :note note
               :files (vec (or files []))}]
    (swap! state update-in [:sessions sid :turn-state :actions]
           (fnil conj []) entry)
    (append-log! sid entry)
    entry))

(defn evidence!
  [sid opts]
  (let [session (require-session sid)
        turn-state (:turn-state session)
        turn (:turn opts)
        pattern-id (:pattern/id opts)
        files (:files opts)
        note (:note opts)
        entry {:event/type :evidence/add
               :at (now)
               :session/id sid
               :turn (or turn (:turn turn-state))
               :pattern/id pattern-id
               :files (vec (or files []))
               :note note}]
    (swap! state update-in [:sessions sid :turn-state :evidence]
           (fnil conj []) entry)
    (append-log! sid entry)
    entry))

(defn end-turn!
  [sid opts]
  (let [session (require-session sid)
        turn-state (:turn-state session)
        final-turn (or (:turn opts) (:turn turn-state))
        selections (get turn-state :selections [])
        actions (get turn-state :actions [])
        evidence (get turn-state :evidence [])
        entry {:event/type :turn/end
               :at (now)
               :session/id sid
               :turn final-turn
               :summary (merge {:selection-count (count selections)
                                :action-count (count actions)
                                :evidence-count (count evidence)}
                               (:summary opts))}]
    (swap! state assoc-in [:sessions sid :turn-state] nil)
    (append-log! sid entry)
    entry))

(defn session-state
  [sid]
  (select-keys (require-session sid) [:session/id :turn :turn-state]))
