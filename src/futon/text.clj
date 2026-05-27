(ns futon.text
  (:require [clojure.set :as set]
            [clojure.string :as str]))

(defn tokenize
  [text]
  (->> (str/split (str/lower-case (or text "")) #"[^a-z0-9]+")
       (remove str/blank?)
       (remove #(< (count %) 3))
       set))

(defn overlap-count
  [query-tokens text]
  (count (set/intersection query-tokens (tokenize text))))
