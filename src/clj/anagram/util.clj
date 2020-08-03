(ns anagram.util
  (:require [clojure.math.combinatorics :as combo])
  (:import (java.io BufferedReader FileReader)))

(def WORD-MAP (atom {}))
(def WORD-KEY (atom []))

(defn get-words-from-sig [word]
  (@WORD-MAP word))

(defn get-random-word []
  (let [randomkey (rand-nth @WORD-KEY)]
    (first (shuffle (@WORD-MAP randomkey)))))

(defn get-sig-word [string]
  (into {} (map #(hash-map (first %) (count %)) (partition-by identity (sort string)))))

(defn sig-to-string [sig]
  (apply str (reduce-kv #(str %1 %2 %3) "" sig)))

(defn init-word [file-name]
  (with-open [rdr (BufferedReader. (FileReader. file-name))]
    (doseq [word (line-seq rdr)]
      (when (> (count word) 2)
        (let [sig (sig-to-string (get-sig-word word))
              words (conj (or (@WORD-MAP sig) []) word)]
          (swap! WORD-MAP conj {sig words})
          (swap! WORD-KEY conj sig))))))

(init-word "resources/english-simple.txt")

(defn get-alt-answer [anagram]
  (get-words-from-sig (sig-to-string (get-sig-word anagram))))

(defn get-comb-anagram [anagram n]
  (map #(apply str %) (combo/combinations anagram n)))

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
      (if (some #{soumission} (get-words-from-sig soumission-sig-string))
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
  (get-random-word))
