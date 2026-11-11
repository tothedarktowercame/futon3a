(ns futon.pattern-index
  "Read-only Clojure boundary for the pattern-index SQLite sidecar."
  (:require [clojure.data.json :as json])
  (:import [java.nio ByteBuffer ByteOrder]
           [java.sql DriverManager]))

(def default-db-path
  (or (System/getenv "FUTON3A_PATTERN_INDEX_DB")
      (str (System/getProperty "user.home")
           "/code/storage/futon3a/pattern-index.sqlite")))
(def ^:dynamic *db-path* default-db-path)

(def ^:dynamic *checkpoint-ttl-ms* 1000)
(defonce ^:private !checkpoint (atom nil))
(defonce ^:private !rows (atom {}))

(defn- connection [path]
  (DriverManager/getConnection (str "jdbc:sqlite:" path)))

(defn- latest-row [conn sql]
  (with-open [st (.createStatement conn)
              rs (.executeQuery st sql)]
    (when (.next rs)
      (let [md (.getMetaData rs)]
        (into {}
              (for [i (range 1 (inc (.getColumnCount md)))]
                [(keyword (.getColumnLabel md i)) (.getObject rs i)]))))))

(defn checkpoint
  "Newest embedding and sigil checkpoint, checked at most once per second."
  ([] (checkpoint *db-path*))
  ([path]
   (let [now (System/currentTimeMillis)
         cached @!checkpoint]
     (if (and cached (= path (:path cached))
              (< (- now (:at cached)) *checkpoint-ttl-ms*))
       (:value cached)
       (let [value (with-open [conn (connection path)]
                     {:embedding (latest-row conn
                                             (str "SELECT model,library_head,seq "
                                                  "FROM checkpoint ORDER BY seq DESC LIMIT 1"))
                      :sigil (latest-row conn
                                         (str "SELECT library_head,embedding_seq,seq "
                                              "FROM sigil_checkpoint ORDER BY seq DESC LIMIT 1"))})]
         (reset! !checkpoint {:path path :at now :value value})
         value)))))

(defn- cached-rows [path kind stamp load-fn]
  (let [key [path kind]
        cached (get @!rows key)]
    (if (= stamp (:stamp cached))
      (:rows cached)
      (let [rows (load-fn)]
        (swap! !rows assoc key {:stamp stamp :rows rows})
        rows))))

(defn load-patterns
  "TSV-compatible row maps, reloaded when sigil_checkpoint.seq moves."
  ([] (load-patterns *db-path*))
  ([path]
   (let [ckpt (checkpoint path)
         stamp (get-in ckpt [:sigil :seq])]
     (cached-rows
      path :patterns stamp
      #(with-open [conn (connection path)
                   st (.createStatement conn)
                   rs (.executeQuery st (str "SELECT id,tokipona,truth,rationale,hotwords "
                                             "FROM pattern_sigil ORDER BY ordinal"))]
         (loop [out []]
           (if (.next rs)
             (recur (conj out {:id (.getString rs 1)
                               :pattern (.getString rs 1)
                               :tokipona (or (.getString rs 2) "")
                               :sigil (or (.getString rs 3) "")
                               :rationale (.getString rs 4)
                               :hotwords (vec (json/read-str (.getString rs 5)))}))
             out)))))))

(defn- blob-vector [^bytes bytes]
  (let [buffer (doto (ByteBuffer/wrap bytes) (.order ByteOrder/LITTLE_ENDIAN))]
    (loop [out []]
      (if (.hasRemaining buffer)
        (recur (conj out (double (.getFloat buffer))))
        out))))

(defn load-embeddings
  "MiniLM-compatible entry maps, reloaded when checkpoint.seq moves."
  ([] (load-embeddings *db-path*))
  ([path]
   (let [ckpt (checkpoint path)
         stamp (get-in ckpt [:embedding :seq])
         model (get-in ckpt [:embedding :model])]
     (cached-rows
      path :embeddings [model stamp]
      #(with-open [conn (connection path)
                   ps (.prepareStatement conn
                                         (str "SELECT p.id,p.title,e.vector FROM embedding e "
                                              "JOIN pattern p ON p.id=e.pattern_id "
                                              "WHERE e.model=? ORDER BY p.id"))]
         (.setString ps 1 model)
         (with-open [rs (.executeQuery ps)]
           (loop [out []]
             (if (.next rs)
               (recur (conj out {:id (.getString rs 1)
                                 :title (.getString rs 2)
                                 :type "pattern"
                                 :vector (blob-vector (.getBytes rs 3))}))
               out))))))))
