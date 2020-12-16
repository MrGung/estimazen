(ns estimazen.server
  (:require
    [ring.middleware.defaults :as defaults]
    [ring.middleware.anti-forgery :as anti-forgery]
    [compojure.core :as comp :refer (defroutes GET POST)]
    [compojure.route :as route]
    [ring.middleware.cors :as cors]
    [clojure.core.async :as async :refer (<! <!! >! >!! put! chan go go-loop)]
    [taoensso.encore :as encore :refer (have have?)]
    [taoensso.timbre :as timbre :refer (tracef debugf infof warnf errorf)]
    [taoensso.sente :as sente]
    [org.httpkit.server :as http-kit]
    [taoensso.sente.server-adapters.http-kit :refer (get-sch-adapter)]
    [estimazen.views :as views]
    [hiccup.core :as hiccup])
  (:gen-class))


;; (timbre/set-level! :trace) ; Uncomment for more logging
(reset! sente/debug-mode?_ true)                            ; Uncomment for extra debug info

;;;; Define our Sente channel socket (chsk) server

(let [;; Serialization format, must use same val for client + server:
      packer :edn                                           ; Default packer, a good choice in most cases

      chsk-server (sente/make-channel-socket-server!
                    (get-sch-adapter) {:packer packer :user-id-fn (fn [ring-req] (:client-id ring-req))})

      {:keys [ch-recv send-fn connected-uids
              ajax-post-fn ajax-get-or-ws-handshake-fn]} chsk-server]

  (def ring-ajax-post ajax-post-fn)
  (def ring-ajax-get-or-ws-handshake ajax-get-or-ws-handshake-fn)
  (def ch-chsk ch-recv)                                     ; ChannelSocket's receive channel
  (def chsk-send! send-fn)                                  ; ChannelSocket's send API fn
  (def connected-uids connected-uids))                      ; Watchable, read-only atom


;;;; Ring handlers

(defn login-handler
  "Here's where you'll add your server-side login/auth procedure (Friend, etc.).
  In our simplified example we'll just always successfully authenticate the user
  with whatever user-id they provided in the auth request."
  [ring-req]
  (let [{:keys [session params]} ring-req
        {:keys [user-id]} params]
    (debugf "Login request: %s" params)
    {:status 200 :session (assoc session :uid user-id)}))

(defroutes ring-routes
  (GET "/" ring-req (views/estimazen-landing-pg-handler ring-req))
  (GET "/chsk" ring-req (ring-ajax-get-or-ws-handshake ring-req))
  (POST "/chsk" ring-req (ring-ajax-post ring-req))
  (POST "/login" ring-req (login-handler ring-req))
  (route/resources "/")                                     ; Static files, notably public/main.js (our cljs target)
  (route/not-found "<h1>Page not found</h1>"))

(def main-ring-handler
  "**NB**: Sente requires the Ring `wrap-params` + `wrap-keyword-params`
  middleware to work. These are included with
  `ring.middleware.defaults/wrap-defaults` - but you'll need to ensure
  that they're included yourself if you're not using `wrap-defaults`.

  You're also STRONGLY recommended to use `ring.middleware.anti-forgery`
  or something similar."
  #_(-> #'ring-routes
      (defaults/wrap-defaults (assoc-in defaults/site-defaults [:security :anti-forgery] false))
      (cors/wrap-cors :access-control-allow-origin [#".*"]
        :access-control-allow-methods [:get :put :post :delete]
        :access-control-allow-credentials ["true"]))
  (ring.middleware.defaults/wrap-defaults
    ring-routes ring.middleware.defaults/site-defaults))


;;;; Sente event handlers

(defmulti -event-msg-handler
  "Multimethod to handle Sente `event-msg`s"
  :id)                                                      ; Dispatch on event-id


(defn event-msg-handler
  "Wraps `-event-msg-handler` with logging, error catching, etc."
  [{:as ev-msg :keys [id ?data event]}]
  (-event-msg-handler ev-msg))                              ; Handle event-msgs on a single thread

(defmethod -event-msg-handler
  :default                                                  ; Default/fallback case (no other matching handler)
  [{:as ev-msg :keys [event id ?data ring-req ?reply-fn send-fn]}]
  (let [session (:session ring-req)
        uid (:uid session)]
    (debugf "Unhandled event: %s" event)
    (when ?reply-fn
      (?reply-fn {:umatched-event-as-echoed-from-server event}))))

(defmethod -event-msg-handler
  :chsk/uidport-close
  [{:keys [event id ?data ring-req]}]
  (let [session (:session ring-req)
        uid (:uid session)]
    (debugf "Client disconnected: %s %s %s" event id ?data)))


(defonce estimations (atom {}))

(comment
  (do
    (println "current estimations")
    (clojure.pprint/pprint @estimations)
    (println "connected uids:")
    (clojure.pprint/pprint (:any @connected-uids)))
  (reset! connected-uids {:any #{} :ws #{} :ajax #{}}))


(defn broadcast [uids msg]
  (doseq [uid uids]
    (debugf "  broadcasting to %s: %s" uid msg)
    (chsk-send! uid msg)))

(defn estimation-to-int [est]
  (try
    (Integer/parseInt est)
    (catch Exception e
      ;; Some "max" value
      1000)))


(defmethod -event-msg-handler :estimazen/est-button
  [{[evt-id {:keys [btn-value]}] :event client-id :client-id :as all}]
  (swap! estimations assoc client-id btn-value)
  (let [current-estimations @estimations
        current-connected-uids (:any @connected-uids)
        ;; the number of estimations in the next round before the results of the previous round gets cleared.
        clearing-threshold-of-estimations (Math/ceil (/ (count current-connected-uids) 3))]

    (debugf "Estimation: %s from %s" btn-value client-id)
    (broadcast current-connected-uids [:estimazen/est-stats-estimated {:number-estimated (count current-estimations)}])
    (cond
      (>= (count current-estimations) (count current-connected-uids))
      (do
        (debugf "All clients voted - starting broadcast of results")
        (broadcast current-connected-uids [:estimazen/est-result
                                           {:estimations current-estimations
                                            :html (hiccup/html [:ul (for [est (sort-by estimation-to-int (map second current-estimations))] [:li est])])}])
        (reset! estimations {}))

      (> (count current-estimations) clearing-threshold-of-estimations)
      ;; the other clients - not the current one - now should reset their active-button.
      ;; since the current client was the initiator of the next round - don't reset its active button...
      (do (broadcast (remove #{client-id} current-connected-uids) [:estimazen/clear-active-button])
          (broadcast current-connected-uids [:estimazen/clear-result])))))




(add-watch connected-uids :connected-uids
  (fn [_ _ old-connected-uids new-connected-uids]
    (debugf "watch was called")
    (when (not= old-connected-uids new-connected-uids)
      (debugf "Connected uids change: %s" new-connected-uids)
      (broadcast (:any new-connected-uids) [:estimazen/est-stats-clients
                                            {:number-clients (-> new-connected-uids :any count)}]))))


(comment
  ;; experiments with close -
  ;; BUT: connected-uids is not cleared, client do not reconnect
  ;; -> does not work for cleaning connections
  (defn close []
    (broadcast (:any @connected-uids) [:chsk/close]))
  (close))

;;;; Sente event router (our `event-msg-handler` loop)

(defonce router_ (atom nil))
(defn stop-router! [] (when-let [stop-fn @router_] (stop-fn)))
(defn start-router! []
  (stop-router!)
  (reset! router_
    (sente/start-server-chsk-router!
      ch-chsk event-msg-handler)))

;;;; Init stuff

(defonce web-server_ (atom nil))                            ; (fn stop [])
(defn stop-web-server! [] (when-let [stop-fn @web-server_] (stop-fn)))
(defn start-web-server! [{:keys [port] :or {port 0}}]     ; port 0 => Choose any available port
  (stop-web-server!)
  (let [ring-handler (var main-ring-handler)
        [port stop-fn] (let [stop-fn (http-kit/run-server ring-handler {:port port})]
                         [(:local-port (meta stop-fn)) (fn [] (stop-fn :timeout 100))])

        uri (format "http://localhost:%s/" port)]

    (infof "Web server is running at `%s`" uri)
    ;; startup some browsers - but only during development - on my machine.
    (if (= (System/getenv "COMPUTERNAME") "USER-PC")
      (try
        (let [runtime (Runtime/getRuntime)
              url (java.net.URI. uri)]
          ;(.browse (java.awt.Desktop/getDesktop) url)
          (.exec runtime (format "C:\\Program Files (x86)\\Mozilla Firefox\\firefox.exe %s" uri))
          (.exec runtime (format "C:\\Program Files (x86)\\Google\\Chrome\\Application\\chrome.exe %s" uri)))
        (catch java.awt.HeadlessException _)))
    (reset! web-server_ stop-fn)))

(defn stop! [] (stop-router!) (stop-web-server!))
(defn start! [port] (start-router!) (start-web-server! port))

(defn -main "For `lein run`, etc."
  [& args]
  (start! {:port (or (some-> (first args) (Integer/parseInt))
                   0)}))

(comment
  (start! 56666)
  (test-fast-server>user-pushes))
