(ns meme.gates
  "Pure structural transition-invariants for the cascade→sorry→wiring inter-step
   process — the two gates E-fold-engine discovered, extending meme.identity's I1-I5:

     GROUNDING        on  :correlated → :open      (the sorry's endpoints are real in substrate-2)
     TERMINALS-MATCH  on  :open → :constructed      (wiring ports = the sorry's want-signature)
     CASCADE-WARRANT  on  :open → :constructed      (every wiring box's warrant ∈ the cascade)

   Pure (clojure.string only) so they unit-test in isolation and are reusable by
   meme.identity/promote!, inter_step.clj, and a future fold engine. Each gate
   returns {:ok true} or {:ok false :reason <kw> :detail <data>}; `gate!` throws
   on a non-ok result. Backward-compatible by construction: a caller that supplies
   no endpoints/wiring/cascade trips no new gate."
  (:require [clojure.string :as str]))

(defn warrant-patterns
  "Extract namespaced pattern-ids (ns/pat-name …) from a free-text warrant string."
  [s]
  (set (re-seq #"[a-z0-9]+(?:-[a-z0-9]+)*/[a-z0-9]+(?:-[a-z0-9]+)*" (or s ""))))

(defn parse-want-signature
  "\"fold : cascade-dict -> (Wiring, [PolicyHole])\" → {:dom \"cascade-dict\" :cod \"(Wiring, [PolicyHole])\"}."
  [sig]
  (when-let [m (re-matches #".*?:\s*(.+?)\s*->\s*(.+)" (or sig ""))]
    {:dom (str/trim (nth m 1)) :cod (str/trim (nth m 2))}))

(defn grounded?
  "GROUNDING (→:open): every endpoint claiming :in-map true resolves in substrate-2
   via ORACLE (endpoint→bool), and at least one :have endpoint is grounded."
  [endpoints oracle]
  (let [claimed (filter #(true? (:in-map %)) endpoints)
        false-claims (remove oracle claimed)
        haves (filter #(and (= :have (:role %)) (true? (:in-map %))) endpoints)]
    (cond
      (seq false-claims) {:ok false :reason :ungrounded-endpoint :detail (mapv :ref false-claims)}
      (empty? haves)     {:ok false :reason :no-grounded-have}
      :else              {:ok true})))

(defn terminals-match?
  "TERMINALS-MATCH (→:constructed): the wiring's in/out ports equal the sorry's
   want-signature domain/codomain."
  [want-signature wiring]
  (let [sig (parse-want-signature want-signature)
        ins (map :port (get-in wiring [:terminals :in]))
        outs (map :port (get-in wiring [:terminals :out]))
        cod (or (:cod sig) "")
        need-w (boolean (re-find #"(?i)wiring" cod))
        need-h (boolean (re-find #"(?i)hole" cod))]
    (if (and sig
             (some #(= % (:dom sig)) ins)
             (or (not need-w) (some #(re-find #"(?i)wiring" %) outs))
             (or (not need-h) (some #(re-find #"(?i)hole" %) outs)))
      {:ok true}
      {:ok false :reason :terminals-mismatch :detail {:sig sig :ins (vec ins) :outs (vec outs)}})))

(defn cascade-warrant-ok?
  "CASCADE-WARRANT (→:constructed): every wiring box's warrant ∈ the cascade pattern set."
  [cascade wiring]
  (let [cps (set cascade)
        bad (for [b (:boxes wiring)
                  :let [miss (remove cps (warrant-patterns (:warrant b)))]
                  :when (seq miss)]
              {:box (:id b) :unwarranted (vec miss)})]
    (if (empty? bad) {:ok true} {:ok false :reason :box-warrant-not-in-cascade :detail (vec bad)})))

(defn gate!
  "Throw ex-info unless RESULT is :ok; otherwise return it."
  [nm result extra]
  (when-not (:ok result)
    (throw (ex-info (str nm " gate failed: " (name (:reason result)))
                    (merge {:gate nm :reason (:reason result) :detail (:detail result)} extra))))
  result)
