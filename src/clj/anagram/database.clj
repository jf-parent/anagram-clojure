(ns anagram.database
  (:require [taoensso.carmine :as car :refer (wcar)])
  (:import (java.io BufferedReader FileReader)))

(def server1-conn {:spec {:db 11}})
(defmacro wcar* [& body] `(car/wcar server1-conn ~@body))

(defn get-word [word]
  (wcar*
   (car/get word)))

(defn get-random-word []
  (wcar*
   (car/randomkey)))

;; (defn init-word [file-name]
;;   (with-open [rdr (BufferedReader. (FileReader. file-name))]
;;     (doseq [word (line-seq rdr)]
;;       (wcar*
;;        (car/set word (get-sig-string word))))))

;; (init-word "resources/english-simple.txt")
