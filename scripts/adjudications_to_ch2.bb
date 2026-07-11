#!/usr/bin/env bb
;; adjudications_to_ch2.bb — B4: the escrow-seam emission bridge.
;;
;; Reads futon6/holes/fold-turn-adjudications.edn, selects PROPOSAL-LINKED
;; records with a boolean :success (deposit-psi / want-grain labels — the
;; loader's ingestion rule; :flight-slice-pass is deliberately skipped, no
;; subsumption), and emits validator-checked CH2 fold events to the sink.
;; :move/id carries the proposal hash FIRST so the scoreboard's S1/S2
;; hash-in-move-id attribution matches. Idempotent: move-ids already in the
;; sink are skipped, so re-running after each batch's adjudications is safe.
;;
;; Run:  cd ~/code/futon3a && bb scripts/adjudications_to_ch2.bb
(require '[clojure.edn :as edn]
         '[clojure.string :as str])
(load-file (str (fs/parent (fs/parent *file*)) "/src/meme/ch2.clj"))
(alias 'ch2 'meme.ch2)

(def adjudications-file "/home/joe/code/futon6/holes/fold-turn-adjudications.edn")
(def sink (str (fs/parent (fs/parent *file*)) "/data/ch2-discharge-events.edn"))

(defn batch-date [provenance]
  (or (re-find #"\d{4}-\d{2}-\d{2}" (str provenance)) "2026-07-11"))

(def records (edn/read-string (slurp adjudications-file)))
(def existing
  (if (fs/exists? sink)
    (into #{} (map (comp :move/id edn/read-string))
          (str/split-lines (slurp sink)))
    #{}))

(def emitted
  (vec
   (for [r records
         :when (and (:proposal/hash r)
                    (boolean? (:success r))
                    (:fold-turn/id r))
         :let [move-id (str (:proposal/hash r) "|" (:fold-turn/id r))]
         :when (not (existing move-id))]
     (let [ev (ch2/fold-event move-id
                              (str "futon6/fold-turn/" (:fold-turn/id r))
                              (str (batch-date (:provenance r)) "T00:00:00Z")
                              (:success r)
                              :used (vec (:used r))
                              :note (:evidence r))]
       (ch2/emit-fold-event! ev :sink sink)
       move-id))))

(println (count emitted) "CH2 fold events emitted ->" sink)
(doseq [m emitted] (println "  " m))
(println (count existing) "already present (skipped)")
