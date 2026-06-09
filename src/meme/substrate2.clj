(ns meme.substrate2
  "Pure, non-live projection of constructed meme arrows to substrate-2 docs."
  (:require [meme.core :as core]
            [meme.identity :as identity])
  (:import [java.security MessageDigest]))

(defn- sha256 [s]
  (let [md (MessageDigest/getInstance "SHA-256")
        bytes (.digest md (.getBytes (str s) "UTF-8"))]
    (apply str (map #(format "%02x" (bit-and % 0xff)) bytes))))

(defn- endpoint-slug [endpoint-key]
  (subs (sha256 (pr-str endpoint-key)) 0 16))

(defn- arrow-endpoints [ds arrow-row]
  (let [source (core/get-entity ds (:source_id arrow-row))
        target (core/get-entity ds (:target_id arrow-row))]
    (when-not (and source target)
      (throw (ex-info "arrow endpoints must resolve to existing meme entities"
                      {:arrow-id (:id arrow-row)
                       :source-id (:source_id arrow-row)
                       :target-id (:target_id arrow-row)})))
    {:have (:name source)
     :want (:name target)}))

(defn- constructed-arrow? [arrow-row]
  (and (= :constructed (:status arrow-row))
       (some? (:payload arrow-row))))

(defn arrow->sorry-doc
  "Project a constructed meme arrow to a substrate-2 code/v05/sorry doc.

   This is deliberately pure/non-live. It mirrors the one-endpoint
   code/v05/sorry convention from futon3c.watcher.file-ingest without
   posting to futon1a."
  [ds arrow-row & {:keys [label source-file]
                   :or {label "futon3a"
                        source-file "meme.db"}}]
  (when-not (constructed-arrow? arrow-row)
    (throw (ex-info "only :constructed arrows with payload may promote to substrate-2 sorry docs"
                    {:reason :boundary/non-constructed-arrow
                     :arrow-id (:id arrow-row)
                     :status (:status arrow-row)
                     :payload? (some? (:payload arrow-row))})))
  (let [endpoint-pair (arrow-endpoints ds arrow-row)
        endpoint-key (identity/endpoint-key endpoint-pair)
        endpoint (str label "/sorry/meme-arrow-" (endpoint-slug endpoint-key))
        title (str (:have endpoint-pair) " -> " (:want endpoint-pair))]
    {:hx-type "code/v05/sorry"
     :endpoints [endpoint]
     :labels ["v05" "phase-4.5" label "meme-arrow-promotion"]
     :props {"repo" label
             "phase" 4.5
             "source-file" source-file
             "sorry/endpoint" endpoint
             "sorry/registry-id" (str "meme/arrow/" (:id arrow-row))
             "sorry/status" ":constructed"
             "sorry/title" title
             "sorry/t" 0
             "meme/arrow-id" (:id arrow-row)
             "meme/have" (:have endpoint-pair)
             "meme/want" (:want endpoint-pair)
             "meme/mode" (some-> (:mode arrow-row) name)
             "promoted-from" endpoint-key}}))
