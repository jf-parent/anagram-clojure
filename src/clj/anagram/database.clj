;; (ns anagram.database
;;   (:require [taoensso.carmine :as car :refer (wcar)]
;;             [taoensso.nippy :as nippy])
;;   (:import (java.io BufferedReader FileReader)))

;; (def server1-conn {:spec {:db 11}})
;; (defmacro wcar* [& body] `(car/wcar server1-conn ~@body))

;; (defn get-words-from-sig [word]
;;   (wcar* (car/get word)))

;; (defn get-random-word []
;;   (let [randomkey (wcar* (car/randomkey))]
;;     (first (shuffle (wcar* (car/get randomkey))))))

;; (defn get-sig-word [string]
;;   (into {} (map #(hash-map (first %) (count %)) (partition-by identity (sort string)))))

;; (defn get-sig-string [string]
;;   (apply str (map #(str (first %) (count %)) (partition-by identity (sort string)))))

;; (defn init-word [file-name]
;;   (with-open [rdr (BufferedReader. (FileReader. file-name))]
;;     (doseq [word (line-seq rdr)]
;;       (when (> (count word) 2)
;;         (let [sig (get-sig-string word)
;;               words (conj (or (wcar* (car/get sig)) []) word)]
;;           (wcar*
;;            (car/set sig words)))))))

;; (init-word "resources/english-simple.txt")
