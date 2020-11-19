(ns estimazen.client
  "Official Sente reference example: client"
  {:author "Peter Taoussanis (@ptaoussanis)"}

  (:require
    [clojure.string :as str]
    [cljs.core.async :as async :refer (<! >! put! chan)]
    [taoensso.encore :as encore :refer-macros (have have?)]
    [taoensso.timbre :as timbre :refer-macros (tracef debugf infof warnf errorf)]
    [taoensso.sente :as sente :refer (cb-success?)])

  (:require-macros
    [cljs.core.async.macros :as asyncm :refer (go go-loop)]))

;; (timbre/set-level! :trace) ; Uncomment for more logging

;;;; Util for logging output to on-screen console

(def output-el (.getElementById js/document "output"))
(defn ->output! [fmt & args]
  (let [msg (apply encore/format fmt args)]
    (timbre/debug msg)
    (aset output-el "value" (str "• " (.-value output-el) "\n" msg))
    (aset output-el "scrollTop" (.-scrollHeight output-el))))

(->output! "ClojureScript appears to have loaded correctly.")

;;;; Define our Sente channel socket (chsk) client

(def ?csrf-token
  (when-let [el (.getElementById js/document "sente-csrf-token")]
    (.getAttribute el "data-csrf-token")))



(if ?csrf-token
  (->output! "CSRF token detected in HTML, great! %s" ?csrf-token)
  (->output! "CSRF token NOT detected in HTML, default Sente config will reject requests"))

(let [;; Serializtion format, must use same val for client + server:
      packer :edn                                           ; Default packer, a good choice in most cases

      {:keys [chsk ch-recv send-fn state]} (sente/make-channel-socket-client!
                                             "/chsk"        ; Must match server Ring routing URL
                                             ?csrf-token
                                             {:type :auto
                                              :packer packer})]
  (def chsk chsk)
  (def ch-chsk ch-recv)                                     ; ChannelSocket's receive channel
  (def chsk-send! send-fn)                                  ; ChannelSocket's send API fn
  (def chsk-state state))                                   ; Watchable, read-only atom


;;;; Sente event handlers

(defmulti -event-msg-handler
  "Multimethod to handle Sente `event-msg`s"
  :id)                                                      ; Dispatch on event-id


(defn event-msg-handler
  "Wraps `-event-msg-handler` with logging, error catching, etc."
  [{:as ev-msg :keys [id ?data event]}]
  (-event-msg-handler ev-msg))

(defmethod -event-msg-handler
  :default                                                  ; Default/fallback case (no other matching handler)
  [{:as ev-msg :keys [event]}]
  (->output! "Unhandled event: %s" event))

(defmethod -event-msg-handler :chsk/state
  [{:as ev-msg :keys [?data]}]
  (let [[old-state-map new-state-map] (have vector? ?data)]
    (if (:first-open? new-state-map)
      (->output! "Channel socket successfully established!: %s" new-state-map)
      (->output! "Channel socket state change: %s" new-state-map))))

(defmethod -event-msg-handler :chsk/recv
  [{:as ev-msg :keys [?data]}]
  (->output! "Push event from server: %s" ?data))

(defmethod -event-msg-handler :chsk/handshake
  [{:as ev-msg :keys [?data]}]
  (let [[?uid ?csrf-token ?handshake-data] ?data]
    (->output! "Handshake (uid, csrf-token, handshake-data): %s" ?data)))

;; TODO Add your (defmethod -event-msg-handler <event-id> [ev-msg] <body>)s here...
(defmethod -event-msg-handler :estimazen/est-result
  [{:as ev-msg :keys [estimations html]}]
  (->output! "Push event from server: %s" estimations)
  (when-let [results-el (.getElementById js/document "est-results")]
    (->output! "Displaying results")
    (set! (.-innerHTML results-el) html)))





;;;; Sente event router (our `event-msg-handler` loop)

(defonce router_ (atom nil))
(defn stop-router! [] (when-let [stop-f @router_] (stop-f)))
(defn start-router! []
  (stop-router!)
  (reset! router_
    (sente/start-client-chsk-router!
      ch-chsk event-msg-handler)))

;;;; UI events

(when-let [target-els (.getElementsByClassName js/document "est-btn")]
  (doseq [target-el target-els]
    (->output! "Registered Btn %s" (.-textContent target-el))
    (.addEventListener target-el "click"
      (fn [ev]
        (->output! "est-Button was clicked: %s" (.-textContent target-el))
        (chsk-send! [:estimazen/est-button {:btn-value (.-textContent target-el) :had-a-callback? "nope"}])))))

(when-let [target-el (.getElementById js/document "btn-login")]
  (.addEventListener target-el "click"
    (fn [ev]
      (let [user-id (.-value (.getElementById js/document "input-login"))]
        (if (str/blank? user-id)
          (js/alert "Please enter a user-id first")
          (do
            (->output! "Logging in with user-id %s" user-id)

            ;;; Use any login procedure you'd like. Here we'll trigger an Ajax
            ;;; POST request that resets our server-side session. Then we ask
            ;;; our channel socket to reconnect, thereby picking up the new
            ;;; session.

            (sente/ajax-lite "/login"
              {:method :post
               :headers {:X-CSRF-Token (:csrf-token @chsk-state)}
               :params  {:user-id (str user-id)}}

              (fn [ajax-resp]
                (->output! "Ajax login response: %s" ajax-resp)
                (let [login-successful? true] ; Your logic here

                  (if-not login-successful?
                    (->output! "Login failed")
                    (do
                      (->output! "Login successful")
                      (sente/chsk-reconnect! chsk))))))))))))

;;;; Init stuff

(defn start! [] (start-router!))

(defonce _start-once (start!))
