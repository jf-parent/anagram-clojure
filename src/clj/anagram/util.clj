(ns anagram.util
  (:require [anagram.database :as db]
            [clojure.math.combinatorics :as combo]))

(defn get-sig-word [string]
  (into {} (map #(hash-map (first %) (count %)) (partition-by identity (sort string)))))

(defn sig-to-string [sig]
  (apply str (reduce-kv #(str %1 %2 %3) "" sig)))

(defn get-alt-answer [anagram]
  (db/get-words-from-sig (sig-to-string (get-sig-word anagram))))

(defn get-comb-anagram [anagram n]
  (dedupe (map #(apply str %) (combo/combinations anagram n))))

(defn get-top-answers [anagram]
  (let [anagram-len (count anagram)]
    {:top1 (get-alt-answer anagram)
     :top2 (mapcat get-alt-answer (get-comb-anagram anagram (- anagram-len 1)))
     :top3 (mapcat get-alt-answer (get-comb-anagram anagram (- anagram-len 2)))}))

(defn score-word [anagram soumission]
  (let [anagram-sig (get-sig-word anagram)
        soumission-sig (get-sig-word soumission)
        soumission-sig-string (sig-to-string soumission-sig)
        sig-match (every? true?
                   (reduce-kv #(conj %1 (>= (anagram-sig %2 0) %3)) [] soumission-sig))]
    (if sig-match
      (if (some #{soumission} (db/get-words-from-sig soumission-sig-string))
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
