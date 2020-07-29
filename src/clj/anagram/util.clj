(ns anagram.util
  (:require [anagram.database :as db]))

(defn get-sig-string [string]
  (into {} (map #(hash-map (first %) (count %)) (partition-by identity (sort string)))))

(defn score-word [anagram soumission]
  (let [anagram-sig (get-sig-string anagram)
        soumission-sig (get-sig-string soumission)
        sig-match (every? true?
                   (reduce-kv #(conj %1 (>= (anagram-sig %2 0) %3)) [] soumission-sig))]
    (if sig-match
      (if (db/get-word soumission)
        (count soumission)
        0)
      0)))

(defn shuffle-word [word]
  {:pre [(> (count word) 2)]}
  (let [anagram (apply str (shuffle (seq word)))]
    (if (= word anagram)
      (shuffle-word word)
      anagram)))

(defn draw-word []
  (db/get-random-word))
