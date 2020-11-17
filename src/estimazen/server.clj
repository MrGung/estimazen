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
    [estimazen.views :as views]))


;; (timbre/set-level! :trace) ; Uncomment for more logging
(reset! sente/debug-mode?_ true)                            ; Uncomment for extra debug info

;;;; Define our Sente channel socket (chsk) server

(let [;; Serialization format, must use same val for client + server:
      packer :edn                                           ; Default packer, a good choice in most cases

      chsk-server (sente/make-channel-socket-server!
                    (get-sch-adapter) {:packer packer})

      {:keys [ch-recv send-fn connected-uids
              ajax-post-fn ajax-get-or-ws-handshake-fn]} chsk-server]

  (def ring-ajax-post ajax-post-fn)
  (def ring-ajax-get-or-ws-handshake ajax-get-or-ws-handshake-fn)
  (def ch-chsk ch-recv)                                     ; ChannelSocket's receive channel
  (def chsk-send! send-fn)                                  ; ChannelSocket's send API fn
  (def connected-uids connected-uids))                      ; Watchable, read-only atom


;; We can watch this atom for changes if we like
(add-watch connected-uids :connected-uids
  (fn [_ _ old new]
    (when (not= old new)
      (infof "Connected uids change: %s" new))))

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


;;;; Some server>user async push examples

(defonce broadcast-enabled?_ (atom true))

(defn start-example-broadcaster!
  "As an example of server>user async pushes, setup a loop to broadcast an
  event to all connected users every 10 seconds"
  []
  (let [broadcast!
        (fn [i]
          (let [uids (:any @connected-uids)]
            (debugf "Broadcasting server>user: %s uids" (count uids))
            (doseq [uid uids]
              (chsk-send! uid
                [:some/broadcast
                 {:what-is-this "An async broadcast pushed from server"
                  :how-often "Every 10 seconds"
                  :to-whom uid
                  :i i}]))))]

    (go-loop [i 0]
      (<! (async/timeout 10000))
      (when @broadcast-enabled?_ (broadcast! i))
      (recur (inc i)))))

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

(defmethod -event-msg-handler :estimazen/est-button
  [{[evt-id {:keys [btn-value]}] :event client-id :client-id :as all}]
  (debugf "Estimation: %s from %s" btn-value client-id))

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
(defn start-web-server! [& [port]]
  (stop-web-server!)
  (let [port (or port 0)                                    ; 0 => Choose any available port
        ring-handler (var main-ring-handler)

        [port stop-fn] (let [stop-fn (http-kit/run-server ring-handler {:port port})]
                         [(:local-port (meta stop-fn)) (fn [] (stop-fn :timeout 100))])

        uri (format "http://localhost:%s/" port)]

    (infof "Web server is running at `%s`" uri)
    (try
      (.browse (java.awt.Desktop/getDesktop) (java.net.URI. uri))
      (catch java.awt.HeadlessException _))

    (reset! web-server_ stop-fn)))

(defn stop! [] (stop-router!) (stop-web-server!))
(defn start! [] (start-router!) (start-web-server!) (start-example-broadcaster!))

(defn -main "For `lein run`, etc." [] (start!))

(comment
  (start!)
  (test-fast-server>user-pushes))
