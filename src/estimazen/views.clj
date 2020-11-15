(ns estimazen.views
  (:require [hiccup.core :as hiccup]
            [hiccup.page :as page]))



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
       [:h1 "estimazen"]
       (let [csrf-token (:anti-forgery-token ring-req)]
         [:div#sente-csrf-token {:data-csrf-token csrf-token}])
       [:div {:id "sente-csrf-token", :data-csrf-token "csrf-token"}
        [:p
         (for [btn-id [1 2 3 5 8 13]]
           [:button {:id (str "est-btn" btn-id), :type "button", :class "est-btn"} btn-id])]
        [:p
         [:button {:id "est-btn-ready", :type "button"} "Bereit für nächste Schätzung"
          [:textarea {:id "output", :style "width: 100%; height: 200px;"}]]]
        [:script {:src "main.js"}]]]]]))


(defn landing-pg-handler [ring-req]
  (hiccup/html
    [:h1 "Sente reference example"]
    (let [csrf-token (:anti-forgery-token ring-req)]
      [:div#sente-csrf-token {:data-csrf-token csrf-token}])
    [:p "An Ajax/WebSocket" [:strong " (random choice!)"] " has been configured for this example"]
    [:hr]
    [:p [:strong "Step 1: "] " try hitting the buttons:"]
    [:p
     [:button#btn1 {:type "button"} "chsk-send! (w/o reply)"]
     [:button#btn2 {:type "button"} "chsk-send! (with reply)"]]
    [:p
     [:button#btn3 {:type "button"} "Test rapid server>user async pushes"]
     [:button#btn4 {:type "button"} "Toggle server>user async broadcast push loop"]]
    [:p
     [:button#btn5 {:type "button"} "Disconnect"]
     [:button#btn6 {:type "button"} "Reconnect"]]
    ;;
    [:p [:strong "Step 2: "] " observe std-out (for server output) and below (for client output):"]
    [:textarea#output {:style "width: 100%; height: 200px;"}]
    ;;
    [:hr]
    [:h2 "Step 3: try login with a user-id"]
    [:p "The server can use this id to send events to *you* specifically."]
    [:p
     [:input#input-login {:type :text :placeholder "User-id"}]
     [:button#btn-login {:type "button"} "Secure login!"]]
    ;;
    [:hr]
    [:h2 "Step 4: want to re-randomize Ajax/WebSocket connection type?"]
    [:p "Hit your browser's reload/refresh button"]
    [:script {:src "main.js"}]))



