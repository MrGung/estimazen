(ns estimazen.views
  (:require
    [hiccup.page :as page]
    [ring.middleware.anti-forgery :as anti-forgery]))



(defn hinweise-zur-verwendung []
  [:ul#hinweise
   [:li "Funktioniert nur in der VDI."]
   [:li "Funktioniert nur zuverlässig im (neuen) Edge (der mit der Welle) und Chrome."]
   [:li "URL ändert sich bei jedem Mal (zumindest ist davon auszugehen). Bookmarken daher sinnlos."]
   [:li "Solange die Schätzungen in der List noch nicht angezeigt werden, kann man seine Schätzung durch Klick auf einen anderen Wert ändern. Auch Enthaltung ist möglich."]
   [:li "Die Buttonfarbe hat folgende Bedeutung:"
    [:ul
     [:li "Durchscheinendes Weiß => noch keine eigene Schätzung abgegeben."]
     [:li "Deckendes Weiß => die aktuell aktive eigene Schätzung."]
     [:li "Dunkelgrau => Ergebnisse wurden angezeigt, die Schätzung wurde berücksichtigt."]]]
   [:li "Wenn ein Drittel der Schätzer einen Wert abgegeben hat, wird die Ergebnisliste geleert, um so die nächste Runde zu signalisieren."]])



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
       [:div (hinweise-zur-verwendung)]
       (let [csrf-token #_(:anti-forgery-token ring-req) (force anti-forgery/*anti-forgery-token*)]
         [:div#sente-csrf-token {:data-csrf-token csrf-token}])
       [:p
        (for [btn-id [1 2 3 5 8 13]]
          [:button {:id (str "est-btn" btn-id), :type "button", :class "est-btn"} btn-id])
        [:button {:id (str "est-btn-abstent"), :type "button", :class "est-btn"} "Enthaltung"]]
       [:div#estimations
        [:p#est-stats "Schätzungen " [:span#est-stats-estimated "0"] "/" [:span#est-stats-clients "0"]]
        [:div#est-results]]
       [:p
        [:textarea {:id "output", :style "width: 100%; height: 200px;"}]]

       [:script {:src "main.js"}]]]]))


