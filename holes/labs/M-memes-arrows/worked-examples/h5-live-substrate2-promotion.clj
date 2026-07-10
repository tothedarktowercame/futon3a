;; h5-live-substrate2-promotion.clj — the LIVE counterpart to h5 (operator-greenlit, Joe 2026-06-09).
;;
;; Promotes a real :constructed meme arrow (r3a-support-coverage) into substrate-2 (futon1a :7071)
;; as a code/v05/sorry hyperedge, matching futon3c.watcher.file-ingest/post-hyperedge! exactly, and
;; reads it back. Idempotent: the endpoint is deterministic from the (have,want) key, so re-running
;; upserts the same hyperedge. Every live promotion is logged in README-memes-and-arrows.md §7.
;;
;; Run:  cd ~/code/futon3a && clojure -M holes/labs/M-memes-arrows/worked-examples/h5-live-substrate2-promotion.clj

(require '[meme.writer :as writer]
         '[meme.arrow :as arrow]
         '[meme.substrate2 :as substrate2]
         '[clojure.data.json :as json]
         '[clojure.java.io :as io])
(import '[java.net URI]
        '[java.net.http HttpClient HttpRequest HttpRequest$BodyPublishers HttpResponse$BodyHandlers])

(def FUTON1A "http://localhost:7071")
(def db-path "/tmp/h5-live-meme.db")
(def client (HttpClient/newHttpClient))

(defn http-send [^HttpRequest req]
  (.send client req (HttpResponse$BodyHandlers/ofString)))

;; futon1a only accepts writes from an allowed penholder; the established ingest
;; (futon3c.watcher.file-ingest) uses "api". Attribution is carried in the props.
(def PENHOLDER (or (System/getenv "FUTON1A_PENHOLDER") "api"))

(defn http-post [url body]
  (http-send (-> (HttpRequest/newBuilder (URI/create url))
                 (.header "Content-Type" "application/json")
                 (.header "X-Penholder" PENHOLDER)
                 (.POST (HttpRequest$BodyPublishers/ofString body))
                 (.build))))

(defn http-get [url]
  (http-send (-> (HttpRequest/newBuilder (URI/create url)) (.GET) (.build))))

;; 1. persist the real r3a :constructed arrow
(io/delete-file db-path true)
(def ds (writer/ensure-db! db-path))
(def written
  (writer/write-arrow! ds
    {:source "belief-mass-on-supports-tagged-cohort"
     :target "support-coverage-channel"
     :mode :construction
     :status :constructed
     :payload {:construction "futon2.aif.belief/predict-support-coverage"
               :cg "cg-17bbaa01-33fc-4a31-bcc6-568cc047f093"
               :shipped "2026-05-26"}
     :confidence 0.9
     :rationale "sorry/r3a-likelihood-support-coverage: construction shipped."
     :created-by "h5-live-substrate2-promotion"}))
(def arrow-row (arrow/get-arrow ds (:id written)))

;; 2. project to the code/v05/sorry doc (refuses non-constructed by construction)
(def doc (substrate2/arrow->sorry-doc ds arrow-row))
(def endpoint (first (:endpoints doc)))

(println "\n=== h5 LIVE substrate-2 promotion (operator-greenlit) ===")
(println "meme arrow-id :" (:id arrow-row) " (status" (:status arrow-row) ")")
(println "endpoint      :" endpoint)

;; 3. POST to futon1a (same payload shape as file-ingest/post-hyperedge!)
(def payload (json/write-str {"hx/type"      (:hx-type doc)
                              "hx/endpoints" (:endpoints doc)
                              "hx/labels"    (:labels doc)
                              "hx/props"     (:props doc)}))
(def post-resp (http-post (str FUTON1A "/api/alpha/hyperedge") payload))
(println "POST status   :" (.statusCode post-resp))
(let [b (.body post-resp)] (println "POST body     :" (subs b 0 (min 300 (count b)))))

;; 4. confirm — futon1a responses are EDN, not JSON. Parse the POST response for the created id,
;;    then read it back independently by endpoint.
(require '[clojure.edn :as edn])
(defn edn-body [s] (try (edn/read-string s) (catch Throwable _ nil)))
(def post-edn (edn-body (.body post-resp)))
(def created-id (or (:hx/id post-edn) (get-in post-edn [:hyperedge :hx/id])))
(def back (edn-body (.body (http-get (str FUTON1A "/api/alpha/hyperedges?type=code/v05/sorry&limit=1000")))))
(def found (->> (:hyperedges back)
                (filter #(some #{endpoint} (:hx/endpoints %)))
                first))
(def hx-id (or created-id (:hx/id found)))
(println "created hx/id :" hx-id)
(println "read-back found-by-endpoint:" (some? found)
         "| promoted-from:" (get-in found [:hx/props :promoted-from]))

(def ok? (and (= 200 (.statusCode post-resp)) (some? hx-id) (some? found)))
(println (format "\nRESULT: posted=%s read-back=%s => LIVE-PROMOTION %s"
                 (= 200 (.statusCode post-resp)) (some? found) (if ok? "PASS" "FAIL")))
(when ok?
  (println "LOG-ROW |" (str (java.time.LocalDate/now)) "|" (:id arrow-row)
           "| belief-mass-on-supports-tagged-cohort -> support-coverage-channel |" hx-id))
