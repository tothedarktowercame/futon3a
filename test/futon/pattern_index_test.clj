(ns futon.pattern-index-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [futon.notions :as notions]
            [futon.pattern-index :as index])
  (:import [java.io File]
           [java.nio ByteBuffer ByteOrder]
           [java.sql DriverManager]))

(defn- temp-db []
  (let [f (File/createTempFile "pattern-index-reader" ".sqlite")]
    (.delete f)
    (.getAbsolutePath f)))

(defn- float-blob [xs]
  (let [b (doto (ByteBuffer/allocate (* 4 (count xs)))
            (.order ByteOrder/LITTLE_ENDIAN))]
    (doseq [x xs] (.putFloat b (float x)))
    (.array b)))

(defn- rebuild! [db seq rows]
  (with-open [conn (DriverManager/getConnection (str "jdbc:sqlite:" db))
              st (.createStatement conn)]
    (.execute st "CREATE TABLE IF NOT EXISTS pattern_sigil (ordinal INTEGER PRIMARY KEY,id TEXT,tokipona TEXT,truth TEXT,rationale TEXT,hotwords TEXT,kind TEXT,source_path TEXT)")
    (.execute st "CREATE TABLE IF NOT EXISTS sigil_checkpoint (seq INTEGER PRIMARY KEY,library_head TEXT,embedding_seq INTEGER,rows_indexed INTEGER,finished_at TEXT)")
    (.execute st "CREATE TABLE IF NOT EXISTS pattern (id TEXT PRIMARY KEY,title TEXT)")
    (.execute st "CREATE TABLE IF NOT EXISTS embedding (pattern_id TEXT,model TEXT,vector BLOB)")
    (.execute st "CREATE TABLE IF NOT EXISTS checkpoint (model TEXT,library_head TEXT,seq INTEGER)")
    (.executeUpdate st "DELETE FROM pattern_sigil")
    (.executeUpdate st "DELETE FROM sigil_checkpoint")
    (.executeUpdate st "DELETE FROM pattern")
    (.executeUpdate st "DELETE FROM embedding")
    (.executeUpdate st "DELETE FROM checkpoint")
    (doseq [[ordinal {:keys [id rationale hotwords vector]}] (map-indexed vector rows)]
      (with-open [ps (.prepareStatement conn "INSERT INTO pattern_sigil VALUES (?,?,?,?,?,?,?,?)")]
        (doseq [[n value] [[1 ordinal] [2 id] [3 "toki"] [4 "真"] [5 rationale]
                           [6 (str "[\"" (str/join "\",\"" hotwords) "\"]")]
                           [7 "library"] [8 (str "library/" id ".flexiarg")]]]
          (.setObject ps n value))
        (.executeUpdate ps))
      (with-open [ps (.prepareStatement conn "INSERT INTO pattern VALUES (?,?)")]
        (.setString ps 1 id) (.setString ps 2 id) (.executeUpdate ps))
      (with-open [ps (.prepareStatement conn "INSERT INTO embedding VALUES (?,?,?)")]
        (.setString ps 1 id) (.setString ps 2 "mini") (.setBytes ps 3 (float-blob vector))
        (.executeUpdate ps)))
    (.executeUpdate st (format "INSERT INTO checkpoint VALUES ('mini','head',%d)" seq))
    (.executeUpdate st (format "INSERT INTO sigil_checkpoint VALUES (%d,'head',%d,%d,'now')"
                               seq seq (count rows)))))

(deftest readers-reload-when-checkpoint-moves
  (let [db (temp-db)
        first-row {:id "agent/first" :rationale "first rationale"
                   :hotwords ["first"] :vector [1.0 0.0]}
        second-row {:id "agent/second" :rationale "second rationale"
                    :hotwords ["second"] :vector [0.0 1.0]}]
    (binding [index/*checkpoint-ttl-ms* 0]
      (rebuild! db 1 [first-row])
      (is (= ["agent/first"] (mapv :id (notions/load-pattern-index db))))
      (is (= ["agent/first"] (mapv :id (index/load-embeddings db))))
      (rebuild! db 2 [first-row second-row])
      (testing "the same process sees both lexical and embedding additions"
        (is (= ["agent/first" "agent/second"]
               (mapv :id (notions/load-pattern-index db))))
        (is (= ["agent/first" "agent/second"]
               (mapv :id (index/load-embeddings db))))))))
