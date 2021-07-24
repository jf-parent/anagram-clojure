(ns anagram.handler
  (:require
   [reitit.ring :as reitit-ring]
   [ring.middleware.json :refer [wrap-json-response wrap-json-body]]
   [ring.util.response :refer [response]]
   [anagram.middleware :refer [middleware]]
   [anagram.util :as util]
   [hiccup.page :refer [include-js include-css html5]]
   [clojure.data.json :as json]
   [config.core :refer [env]]))

(def mount-target
  [:div#app
   [:h2 "Welcome to anagram"]])

(defn head []
  [:head
   [:meta {:charset "utf-8"}]
   [:meta {:name "viewport"
           :content "width=device-width, initial-scale=1"}]
   (include-css (if (env :dev) "/css/site.css" "/css/site.min.css"))])

(defn loading-page []
  (html5
   (head)
   [:body {:class "body-container"}
    mount-target
    (include-js "/js/app.js")]))

(defn index-handler [request]
  {:status 200
   :headers {"Content-Type" "text/html"}
   :body (loading-page)})

(defn wrap-api-handler [handler]
  (-> handler
      wrap-json-response
      wrap-json-body))

(defn get-shuffled-word-api [request]
  (response {:anagram (util/shuffle-word (util/draw-word))}))

(defn get-top-answers-api [request]
  (response (util/get-top-answers (-> request :path-params :anagram))))

(defn post-get-score-api [request]
  (let [answer (-> request :path-params :answer)
        anagram (-> request :path-params :anagram)]
    ;; (println "Anagram:" anagram "Answer:" answer)
    (response {:score (util/score-word anagram answer)})))

(def app
  (reitit-ring/ring-handler
   (reitit-ring/router
    [["/" {:get {:handler index-handler}}]
     ["/api/get-score/:anagram/:answer" {:post (wrap-api-handler post-get-score-api)}]
     ["/api/get-top-answers/:anagram" {:get (wrap-api-handler get-top-answers-api)}]
     ["/api/get-shuffled-word/" {:get (wrap-api-handler get-shuffled-word-api)}]])
   (reitit-ring/routes
    (reitit-ring/create-resource-handler {:path "/" :root "/public"})
    (reitit-ring/create-default-handler))
   {:middleware middleware}))
