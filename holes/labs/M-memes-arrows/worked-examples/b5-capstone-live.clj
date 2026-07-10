;; b5-capstone-live.clj — the CAPSTONE (§12.7 B5).
;;
;; ONE arrow walked cascade → sorry → construction → fact, end-to-end, LIVE, in a single run —
;; the same endpoint-keyed object through every seam (H4 seed → H3 maturation → H5 substrate-2).
;; This is the live realisation of the §10.7 / reference-case thesis: correlation → conjecture →
;; proof → durable fact, one object, identity preserved.
;;
;; Run:  cd ~/code/futon3a && clojure -M holes/labs/M-memes-arrows/worked-examples/b5-capstone-live.clj

(require '[meme.writer :as writer]
         '[meme.identity :as identity]
         '[meme.substrate2 :as substrate2]
         '[clojure.data.json :as json]
         '[clojure.edn :as edn]
         '[clojure.java.io :as io])
(import '[java.net URI]
        '[java.net.http HttpClient HttpRequest HttpRequest$BodyPublishers HttpResponse$BodyHandlers])

(def FUTON1A "http://localhost:7071")
(def PENHOLDER (or (System/getenv "FUTON1A_PENHOLDER") "api"))
(def db-path "/tmp/b5-capstone.db")
(def client (HttpClient/newHttpClient))
(defn- send* [^HttpRequest r] (.send client r (HttpResponse$BodyHandlers/ofString)))
(defn http-post [url body]
  (send* (-> (HttpRequest/newBuilder (URI/create url))
             (.header "Content-Type" "application/json") (.header "X-Penholder" PENHOLDER)
             (.POST (HttpRequest$BodyPublishers/ofString body)) (.build))))
(defn http-get [url] (send* (-> (HttpRequest/newBuilder (URI/create url)) (.GET) (.build))))
(defn edn-body [s] (try (edn/read-string s) (catch Throwable _ nil)))

(io/delete-file db-path true)
(def ds (writer/ensure-db! db-path))

;; The one arrow — keyed by (have, want). The same key carries it through all four stages.
(def ep {:have "belief-mass-on-supports-tagged-cohort" :want "support-coverage-channel"})
(def the-key (identity/endpoint-key ep))

(println "\n=== B5 CAPSTONE — one arrow, four stages, live ===")
(println "endpoint key (the identity):" the-key)
(println)

(defn show [stage row]
  (println (format "  %-13s id=%-18s status=%-12s mode=%-12s payload?=%s"
                   stage (:id row) (name (:status row)) (name (:mode row)) (some? (:payload row))))
  (:id row))

;; STAGE 1 — CASCADE (a hunch: observed co-occurrence, no method)
(def id1 (show "1.cascade" (:arrow (identity/mint-or-unify! ds ep
                              {:mode :analogy :status :correlated
                               :scope-tags ["co-app:8"]
                               :rationale "observed co-occurrence across missions (the cascade)"}))))
;; STAGE 2 — SORRY (commit the goal; the typed hole)
(def id2 (show "2.sorry" (:arrow (identity/promote! ds ep :open))))
;; STAGE 3 — CONSTRUCTION (supply the method)
(def id3 (show "3.construct" (:arrow (identity/promote! ds ep :constructed
                              :mode :construction
                              :payload {:construction "futon2.aif.belief/predict-support-coverage"
                                        :cg "cg-17bbaa01-33fc-4a31-bcc6-568cc047f093"}))))

;; STAGE 4 — FACT (promote the :constructed arrow into substrate-2, live)
(def arrow-row (identity/find-by-endpoint ds ep))
(def doc (substrate2/arrow->sorry-doc ds arrow-row))
(def endpoint (first (:endpoints doc)))
(def payload (json/write-str {"hx/type" (:hx-type doc) "hx/endpoints" (:endpoints doc)
                              "hx/labels" (:labels doc) "hx/props" (:props doc)}))
(def post-resp (http-post (str FUTON1A "/api/alpha/hyperedge") payload))
(def back (edn-body (.body (http-get (str FUTON1A "/api/alpha/hyperedges?type=code/v05/sorry&limit=1000")))))
(def found (->> (:hyperedges back) (filter #(some #{endpoint} (:hx/endpoints %))) first))
(def hx-id (or (:hx/id found)            ; GET responses are EDN (keyword keys) — the reliable source
               (:hx/id (edn-body (.body post-resp)))))
(println (format "  4.fact        substrate-2 POST=%d hx/id=%s read-back=%s"
                 (.statusCode post-resp) hx-id (some? found)))

;; Verify it was ONE object the whole way: same row id across stages 1-3.
(def one-arrow? (= id1 id2 id3))
(def fact-ok? (and (= 200 (.statusCode post-resp)) (some? hx-id) (some? found)))
(println "\n=== the maturation, as one object ===")
(println "  correlation --> conjecture --> proof --> durable fact")
(println (format "  one endpoint-keyed arrow (id stable across stages 1-3): %s" one-arrow?))
(println (format "  promoted-from back-link in substrate-2: %s" (get-in found [:hx/props :promoted-from])))
(println (format "\nRESULT: one-arrow=%s reached-fact=%s => B5 CAPSTONE %s"
                 one-arrow? fact-ok? (if (and one-arrow? fact-ok?) "PASS" "FAIL")))
(when (and one-arrow? fact-ok?)
  (println "LOG-ROW |" (str (java.time.LocalDate/now)) "|" (:id arrow-row)
           "| belief-mass-on-supports-tagged-cohort -> support-coverage-channel |" hx-id "| (B5 capstone)"))
