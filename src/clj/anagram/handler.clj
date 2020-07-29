(ns anagram.handler
  (:require
   [reitit.ring :as reitit-ring]
   [anagram.middleware :refer [middleware]]
   [anagram.util :as util]
   [hiccup.page :refer [include-js include-css html5]]
   [clojure.data.json :as json]
   [config.core :refer [env]]))

(def mount-target
  [:div#app
   [:h2 "Welcome to anagram"]
   [:p "please wait while Figwheel is waking up ..."]
   [:p "(Check the js console for hints if nothing exciting happens.)"]])

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

(defn index-handler
  [_request]
  {:status 200
   :headers {"Content-Type" "text/html"}
   :body (loading-page)})

(defn get-shuffled-word-api [request]
  {:status 200
   :headers {"Content-Type" "application/json"}
   :body (json/write-str {:anagram (util/shuffle-word (util/draw-word))})})

(defn post-get-score-api [request]
  (let [answer (-> request :path-params :answer)
        anagram (-> request :path-params :anagram)]
    (println "Anagram:" anagram "Answer:" answer)
    {:status 200
     :headers {"Content-Type" "application/json"}
     :body (json/write-str {:score (util/score-word anagram answer)})}))

(def app
  (reitit-ring/ring-handler
   (reitit-ring/router
    [["/" {:get {:handler index-handler}}]
     ["/get-score/:anagram/:answer" {:post {:handler post-get-score-api}}]
     ["/get-shuffled-word/" {:get {:handler get-shuffled-word-api}}]
     ["/items"
      ["" {:get {:handler index-handler}}]
      ["/:item-id" {:get {:handler index-handler
                          :parameters {:path {:item-id int?}}}}]]
     ["/about" {:get {:handler index-handler}}]])
   (reitit-ring/routes
    (reitit-ring/create-resource-handler {:path "/" :root "/public"})
    (reitit-ring/create-default-handler))
   {:middleware middleware}))
