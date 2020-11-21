(ns estimazen.views
  (:require
    [hiccup.page :as page]
    [ring.middleware.anti-forgery :as anti-forgery]))




(defn estimazen-landing-pg-handler [ring-req]
  (page/html5
    [:html {:lang "en"}
     [:head
      [:meta {:charset "UTF-8"}]
      [:title "estimazen - minimalist agile distributed estimation"]
      [:link {:href "css/style.css" :rel "stylesheet" :type "text/css"}]
      [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]]
     [:body
      [:div {:class "bg"}
       [:div#est-results]
       [:h1 "estimazen"]
       (let [csrf-token #_(:anti-forgery-token ring-req) (force anti-forgery/*anti-forgery-token*)]
         [:div#sente-csrf-token {:data-csrf-token csrf-token}])
       [:p
        (for [btn-id [1 2 3 5 8 13]]
          [:button {:id (str "est-btn" btn-id), :type "button", :class "est-btn"} btn-id])
        [:button {:id (str "est-btn-abstent"), :type "button", :class "est-btn"} "Enthaltung"]]
       [:p
         [:button {:id "est-btn-ready", :type "button"} "Bereit für nächste Schätzung"]]
       [:p
        [:button {:id "reconnect", :type "button"} "Reconnect"]]
       [:p
        [:textarea {:id "output", :style "width: 100%; height: 200px;"}]]
       [:script {:src "main.js"}]]]]))
