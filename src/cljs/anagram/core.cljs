(ns anagram.core
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require
   [reagent.core :as reagent]
   [alandipert.storage-atom :refer [local-storage]]
   [reagent.dom :as rdom]
   [reagent.session :as session]
   [cljs-http.client :as http]
   [cljs.core.async :refer [<!]]
   [reitit.frontend :as reitit]
   [clerk.core :as clerk]
   [goog.string :as gstring]
   [goog.string.format]
   [accountant.core :as accountant]))

;; -------------------------
;; Config

(def ENABLE-TRACING true)
(enable-console-print!)

;; -------------------------
;; Routes

(def router
  (reitit/router
   [["/" :index]]))

(defn path-for [route & [params]]
  (if params
    (:path (reitit/match-by-name router route params))
    (:path (reitit/match-by-name router route))))

;; -------------------------
;; Local storage

(def history (local-storage (reagent/atom []) :history))

;; -------------------------
;; Atoms

(def current-anagram (reagent/atom ""))
(def answer (reagent/atom ""))
(def current-score (reagent/atom -1))
(def last-top-answers (reagent/atom {}))
(def last-answer (reagent/atom {}))
(def timer (reagent/atom 0))
(defonce timer-fn (js/setInterval #(swap! timer inc) 1000))

(defn atom-watcher [key atom old-state new-state]
  "Add atom watcher helper function."
  (prn (str "[DEBUG]" key " " old-state " -> " new-state)))

;; -------------------------
;; Tracing

(when ENABLE-TRACING
  (add-watch current-anagram :current-anagram atom-watcher)
  (add-watch answer :answer atom-watcher)
  (add-watch current-score :current-score atom-watcher)
  (add-watch last-answer :last-quest atom-watcher))

;; -------------------------
;; Functions

(defn remove-first [p l]
  (flatten (conj (remove p l) (rest (filter p l)))))

(defn reset-state! []
  (reset! current-score -1)
  (reset! last-top-answers {})
  (reset! last-answer {}))

(defn format-timer-human-readable [total-seconds]
  (let [hour   (* 1 60 60)
        minute (* 1 60)
        second 1
        h      (int (quot total-seconds hour))
        mh     (mod  total-seconds hour)
        m      (int (quot mh minute))
        mm     (mod  mh minute)
        s      (int (quot mm second))]
    (cond
      (> h 0) (gstring/format "%02d:%02d:%02d" h m s)
      (> m 0) (gstring/format "%02d:%02d" m s)
      :else (gstring/format "%02d" s))))

;; -------------------------
;; API

(defn get-top-answers! [anagram]
  (go (let [response (<! (http/get (str (-> js/window .-location .-href) "api/get-top-answers/" anagram)))]
        (reset! last-top-answers (:top-answers (:body response))))))

(defn get-new-anagram! []
  (go (let [response (<! (http/get (str (-> js/window .-location .-href) "api/get-shuffled-word/")))]
        (reset! current-anagram (-> response :body :anagram)))))

(defn post-anagram-answer! [anagram answer]
  (go (let [response (<! (http/post (str (-> js/window .-location .-href) "api/get-score/" anagram "/" answer)))
            score (-> response :body :score)
            answer {:time (str (.getTime (js/Date.))) :score score :best-score (count anagram) :anagram anagram :answer answer :timer @timer}]
        (reset! current-score score)
        (reset! timer 0)
        (reset! last-answer answer)
        (swap! history conj answer))))

;; -------------------------
;; Handler

(defn shuffle-anagram [e]
  (set! (.-innerHTML (.-target e)) (apply str (shuffle (seq "ANAGRAM")))))

(defn clear-history []
  (reset-state!)
  (alandipert.storage-atom/clear-local-storage!))

(defn skip-anagram []
  (reset! timer 0)
  (get-new-anagram!))

(defn new-anagram []
  (get-new-anagram!))

(defn anagram-submit [current-anagram answer]
  (post-anagram-answer! @current-anagram @answer)
  (get-top-answers! @current-anagram)
  (new-anagram)
  (reset! answer ""))

;; -------------------------
;; Page components

(defn timer-component []
  [:div.timer
   [:div (str (format-timer-human-readable @timer))]])

(defn anagram []
  (loop [l (clojure.string/split @answer #"") a @current-anagram r [:h1]]
     (if (empty? a)
       r
       (if ((set l) (first a))
         (recur (remove-first #{(first a)} l) (rest a) (conj r [:span.anagram.strike (first a)]))
         (recur l (rest a) (conj r [:span.anagram (first a)]))))))

(defn anagram-question []
  [:div
   [:div
    [anagram]
    [:input {:type "text"
             :value @answer
             :on-change #(reset! answer (-> % .-target .-value))
             :placeholder "Answer"
             :on-key-press (fn [e]
                             (when (= 13 (.-charCode e))
                               (anagram-submit current-anagram answer)))}]
    [:input {:type "button" :value "Submit" :on-click #(anagram-submit current-anagram answer)}]
    [:input {:type "button" :value "Skip!" :on-click #(skip-anagram)}]]])

(defn top-answers []
  [:span.top-answers-container
   [:h3 "Top Answer"]
   [:ul
    (for [word @last-top-answers]
      [:li {:key word} [:a {:href (str "https://en.wiktionary.org/wiki/" word) :target "_blank"} word]])]])

(defn history-item [h]
   [:span (str (h :answer) " : " (h :anagram) " - " (h :score) "/" (h :best-score) " in " (h :timer))])

(defn history-container []
  [:span.history-container
   [:h3 "History " [:input {:type "button" :value "clear" :on-click #(clear-history)}]]
   (let [h (reverse @history)
         rest-answers (if (not-empty @last-answer) (rest h) h)]
     [:div
      (when (not-empty @last-answer)
        [:div.last-answer [history-item @last-answer]])
      [:hr.separator]
      (for [a rest-answers]
        [:div {:key (a :time)}[history-item a]])])])

(defn home-page []
  (fn []
    [:div.main
     [:h1.title [:span {:on-mouse-over shuffle-anagram} "ANAGRAM"]]
     [timer-component]
     [anagram-question]
     [:div
      [history-container]
      (when (>= @current-score 0)
        [top-answers])]]))

;; -------------------------
;; Translate routes -> page components

(defn page-for [route]
  (case route
    :index #'home-page))

;; -------------------------
;; Page mounting component

(defn current-page []
  (let [page (:current-page (session/get :route))]
    [:div [:header [page]]]))

;; -------------------------
;; Initialize app

(defn mount-root []
  (new-anagram)
  (rdom/render [current-page] (.getElementById js/document "app")))

(defn init! []
  (clerk/initialize!)
  (accountant/configure-navigation!
   {:nav-handler
    (fn [path]
      (let [match (reitit/match-by-path router path)
            current-page (:name (:data  match))
            route-params (:path-params match)]
        (reagent/after-render clerk/after-render!)
        (session/put! :route {:current-page (page-for current-page)
                              :route-params route-params})
        (clerk/navigate-page! path)))
    :path-exists?
    (fn [path]
      (boolean (reitit/match-by-path router path)))})
  (accountant/dispatch-current!)
  (mount-root))
