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
   [accountant.core :as accountant]))

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

;; (alandipert.storage-atom/remove-local-storage! :history)
;; (alandipert.storage-atom/clear-local-storage!)

(def history-local-storage (local-storage (atom []) :history))

(println @history-local-storage)

;; -------------------------
;; State

(def current-anagram (reagent/atom ""))
(def current-score (reagent/atom -1))
(def last-top-answers (reagent/atom {}))
(def last-answer (reagent/atom {}))
(def history (reagent/atom @history-local-storage))
(def timer (reagent/atom 0))
(defonce timer-fn (js/setInterval #(swap! timer inc) 1000))

;; -------------------------
;; API

(defn get-top-answers [anagram]
  (go (let [response (<! (http/get (str (-> js/window .-location .-href) "get-top-answers/" anagram)))]
        (reset! last-top-answers (:body response)))))

(defn get-new-anagram []
  (go (let [response (<! (http/get (str (-> js/window .-location .-href) "get-shuffled-word/")))]
        (reset! current-anagram (-> response :body :anagram)))))

(defn post-anagram-answer [anagram answer]
  (println anagram answer)
  (go (let [response (<! (http/post (str (-> js/window .-location .-href) "get-score/" anagram "/" answer)))
            score (-> response :body :score)
            answer {:time (str (.getTime (js/Date.))) :score score :best-score (count anagram) :anagram anagram :answer answer :timer @timer}]
        (reset! current-score score)
        (reset! timer 0)
        (reset! last-answer answer)
        (swap! history-local-storage conj answer)
        (reset! history @history-local-storage))))

;; -------------------------
;; Handler

(defn shuffle-anagram [e]
  (set! (.-innerHTML (.-target e)) (apply str (shuffle (seq "ANAGRAM")))))

(defn new-anagram []
  (get-new-anagram))

(defn anagram-submit [current-anagram answer]
  (post-anagram-answer @current-anagram @answer)
  (get-top-answers @current-anagram)
  (new-anagram)
  (reset! answer ""))

;; -------------------------
;; Page components

(defn timer-component []
  [:div.timer
   [:div (str @timer " sec")]])

(defn anagram-question [answer]
  [:div
   [:h1.anagram @current-anagram]
   [:div
    [:input {:type "text"
             :value @answer
             :on-change #(reset! answer (-> % .-target .-value))
             :placeholder "Answer"
             :on-key-press (fn [e]
                             (when (= 13 (.-charCode e))
                               (anagram-submit current-anagram answer)))}]
    [:input {:type "button" :value "Submit" :on-click #(anagram-submit current-anagram answer)}]
    [:input {:type "button" :value "Skip!" :on-click #(new-anagram)}]]])

(defn top-answers []
  [:span.top-answers-container
   [:h3 "Top Answer"]
   (for [keyval @last-top-answers]
     [:div {:key (key keyval)}
      [:div (key keyval)]
      [:ul
       (for [word (val keyval)]
         [:li {:key word} word])]])])

(defn history-item [h]
   [:span (str (h :answer) " : " (h :anagram) " - " (h :score) "/" (h :best-score) " in " (h :timer) "sec")])

(defn history-container []
  [:span.history-container
   [:h3 "History"]
   (let [h (reverse @history)
         rest-answers (if (not-empty @last-answer) (rest h) h)]
     [:div
      (when (not-empty @last-answer)
        [:div.last-answer [history-item @last-answer]])
      [:hr.separator]
      (for [a rest-answers]
        [:div {:key (a :time)}[history-item a]])])])

(defn home-page []
  (let [answer (reagent/atom "")]
    (fn []
      [:div.main
       [:h1.title [:span {:on-mouse-over shuffle-anagram} "ANAGRAM"]]
       [timer-component]
       [anagram-question answer]
       [:div [history-container]
        (when (>= @current-score 0)
          [top-answers])]])))

;; -------------------------
;; Translate routes -> page components

(defn page-for [route]
  (case route
    :index #'home-page))

;; -------------------------
;; Page mounting component

(defn current-page []
  (fn []
    (let [page (:current-page (session/get :route))]
      [:div
       [:header
        [page]]])))

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
        (clerk/navigate-page! path)
        ))
    :path-exists?
    (fn [path]
      (boolean (reitit/match-by-path router path)))})
  (accountant/dispatch-current!)
  (mount-root))
