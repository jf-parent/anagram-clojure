(ns anagram.core
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require
   [reagent.core :as reagent]
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
;; State

(def current-anagram (reagent/atom ""))
(def current-score (reagent/atom 0))

;; -------------------------
;; API

(defn get-new-anagram []
  (go (let [response (<! (http/get (str (-> js/window .-location .-href) "get-shuffled-word/")))]
        (reset! current-anagram (-> response :body :anagram)))))

(defn post-anagram-answer [anagram answer]
  (println anagram answer)
  (go (let [response (<! (http/post (str (-> js/window .-location .-href) "get-score/" anagram "/" answer)))]
     (reset! current-score (-> response :body :score)))))

;; -------------------------
;; Handler

(defn new-anagram []
  (get-new-anagram))

(defn anagram-submit [current-anagram answer]
  (post-anagram-answer @current-anagram @answer)
  (new-anagram)
  (reset! answer ""))

;; -------------------------
;; Page components

(defn anagram-question [answer]
  [:div
   [:h2 @current-anagram]
   [:input {:type "button" :value "Skip"
            :on-click #(new-anagram)}]
   [:input {:type "text"
            :value @answer
            :on-change #(reset! answer (-> % .-target .-value)) :placeholder "Answer"
            :on-key-press (fn [e]
                            (when (= 13 (.-charCode e))
                              (anagram-submit current-anagram answer)))}]])


(defn home-page []
  (let [answer (reagent/atom "")]
    (fn []
      [:span.main
       [:h1 "Anagram"]
       [anagram-question answer]
       [:input {:type "button" :value "Submit" :on-click (fn [] (anagram-submit current-anagram answer))}]
       [:h3 @current-score]])))

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
