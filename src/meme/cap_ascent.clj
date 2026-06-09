(ns meme.cap-ascent
  "WM policy ascent seam for arrows that advance capability ids."
  (:require [clojure.edn :as edn]
            [clojure.string :as str])
  (:import [java.net URI URLEncoder]
           [java.net.http HttpClient HttpRequest HttpRequest$BodyPublishers HttpResponse$BodyHandlers]))

(def default-base-url "http://localhost:7071")
(def default-penholder "api")

(defn endpoint-id [cap-id]
  (str "scope/capability/" (name cap-id)))

(defn- encode-path-segment [s]
  (-> (URLEncoder/encode (str s) "UTF-8")
      (str/replace "+" "%20")))

(defn- http-client []
  (HttpClient/newHttpClient))

(defn- send! [^HttpRequest req]
  (.send (http-client) req (HttpResponse$BodyHandlers/ofString)))

(defn- parse-edn-body [s]
  (when (seq s)
    (edn/read-string {:readers *data-readers*} s)))

(defn fetch-capability
  "Live-read the substrate-2 capability overlay."
  ([cap-id] (fetch-capability cap-id {}))
  ([cap-id {:keys [base-url]
            :or {base-url default-base-url}}]
   (let [entity-id (endpoint-id cap-id)
         url (str (str/replace base-url #"/+$" "")
                  "/api/alpha/entity/"
                  (encode-path-segment entity-id))
         resp (send! (-> (HttpRequest/newBuilder (URI/create url))
                         (.GET)
                         (.build)))
         body (parse-edn-body (.body resp))]
     (if-let [entity (:entity body)]
       entity
       (throw (ex-info "unknown capability id; refusing arrow cap advancement"
                       {:reason :capability/unknown
                        :cap-id (name cap-id)
                        :entity-id entity-id
                        :status (.statusCode resp)
                        :body body}))))))

(defn- target-status [cap-entity]
  (if (true? (get-in cap-entity [:props :capability/frontier?]))
    :claimed
    :satisfied))

(defn- proposed-flip-event [endpoint-key cap-id cap-entity target]
  {:event :capability/proposed-flip
   :cap-id (name cap-id)
   :capability-id (:id cap-entity)
   :endpoint-key endpoint-key
   :target-status target
   :frontier? (true? (get-in cap-entity [:props :capability/frontier?]))
   :source "meme.identity/promote!"})

(defn plan
  "Validate a capability and produce the status/event writes needed for ascent."
  [cap-id endpoint-key opts]
  (let [cap-entity (fetch-capability cap-id opts)
        target (target-status cap-entity)
        current (get-in cap-entity [:props :capability/status])
        frontier? (true? (get-in cap-entity [:props :capability/frontier?]))
        event (when frontier?
                (proposed-flip-event endpoint-key cap-id cap-entity target))
        already-target? (= target current)]
    {:cap-id (name cap-id)
     :endpoint-key endpoint-key
     :capability cap-entity
     :frontier? frontier?
     :current-status current
     :target-status target
     :already-target? already-target?
     :operation (if already-target? :noop :write)
     :event event}))

(defn- updated-capability-payload [plan]
  (let [cap (:capability plan)
        target (:target-status plan)
        props (cond-> (assoc (:props cap) :capability/status target)
                (= target :claimed) (assoc :capability/claimed? true))]
    {:id (:id cap)
     :name (:name cap)
     :type (:type cap)
     :external-id (:external-id cap)
     :props props}))

(defn- post-edn! [base-url path penholder payload]
  (let [url (str (str/replace base-url #"/+$" "") path)
        resp (send! (-> (HttpRequest/newBuilder (URI/create url))
                        (.header "Content-Type" "application/edn")
                        (.header "X-Penholder" penholder)
                        (.POST (HttpRequest$BodyPublishers/ofString (pr-str payload)))
                        (.build)))
        body (parse-edn-body (.body resp))]
    (when-not (<= 200 (.statusCode resp) 299)
      (throw (ex-info "capability ascent write failed"
                      {:status (.statusCode resp)
                       :body body
                       :path path
                       :payload payload})))
    body))

(defn- proposed-flip-doc [plan]
  {:hx/type :meme/capability-proposed-flip
   :hx/endpoints [(:id (:capability plan))]
   :hx/labels [:meme :wm-policy :capability-ascent]
   :hx/props {:capability/id (:cap-id plan)
              :capability/current-status (:current-status plan)
              :capability/target-status (:target-status plan)
              :meme/endpoint-key (:endpoint-key plan)
              :meme/source "meme.identity/promote!"}})

(defn execute!
  "Apply a validated cap-ascent plan. Dry-run returns intended writes only."
  [plan {:keys [write? base-url penholder]
         :or {write? true
              base-url default-base-url
              penholder default-penholder}}]
  (cond
    (= :noop (:operation plan))
    (assoc plan :write? false :applied? false)

    (not write?)
    (assoc plan
           :write? false
           :applied? false
           :dry-run? true
           :intended-entity (updated-capability-payload plan)
           :intended-event (when (:event plan) (proposed-flip-doc plan)))

    :else
    (let [entity-resp (post-edn! base-url "/api/alpha/entity" penholder
                                 (updated-capability-payload plan))
          event-resp (when (:event plan)
                       (post-edn! base-url "/api/alpha/hyperedge" penholder
                                  (proposed-flip-doc plan)))]
      (assoc plan
             :write? true
             :applied? true
             :entity-response entity-resp
             :event-response event-resp))))

(defn advance!
  "Validate, route, and optionally write the capability ascent side-effect."
  [cap-id endpoint-key opts]
  (execute! (plan cap-id endpoint-key opts) opts))
