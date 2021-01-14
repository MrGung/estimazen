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

(defn get-csrf-token []
  (let [?csrf-token (when-let [el (.getElementById js/document "sente-csrf-token")]
                      (.getAttribute el "data-csrf-token"))]
    (if ?csrf-token
      (->output! "CSRF token detected in HTML, great! %s" ?csrf-token)
      (->output! "CSRF token NOT detected in HTML, default Sente config will reject requests"))
    ?csrf-token))


(defn init-channel []
  (let [;; Serializtion format, must use same val for client + server:
        packer :edn                                         ; Default packer, a good choice in most cases

        {:keys [chsk ch-recv send-fn state]} (sente/make-channel-socket-client!
                                               "/chsk"      ; Must match server Ring routing URL
                                               (get-csrf-token)
                                               {:type :auto
                                                :packer packer})]
    (def chsk chsk)
    (def ch-chsk ch-recv)                                   ; ChannelSocket's receive channel
    (def chsk-send! send-fn)                                ; ChannelSocket's send API fn
    (def chsk-state state)))                                ; Watchable, read-only atom


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

(defmethod -event-msg-handler :chsk/handshake
  [{:as ev-msg :keys [?data]}]
  (let [[?uid ?csrf-token ?handshake-data] ?data]
    (->output! "Handshake (uid, csrf-token, handshake-data): %s" ?data)))


(defmulti push-msg-handler (fn [[id _]] id))

(defmethod -event-msg-handler :chsk/recv
  [{:as ev-msg :keys [?data]}]
  (->output! ":chsk/recv recievied: %s" ?data)
  (push-msg-handler ?data))

(declare remove-marks-from-buttons!)
(declare mark-active-button-sealed!)
(defmethod push-msg-handler :estimazen/est-result
  [[id {:keys [estimations html]}]]
  (->output! "Estimations recieved from server: %s" estimations)
  (when-let [results-el (.getElementById js/document "est-results")]
    (->output! "Displaying results...")
    (-> results-el
      (.-innerHTML)
      (set! html))
    (->output! "  ..."))
  (mark-active-button-sealed!))

(defn clear-results []
  (->output! "Clearing result of previous estimation")
  (when-let [results-el (.getElementById js/document "est-results")]
    (-> results-el
      (.-innerHTML)
      (set! ""))))
(defmethod push-msg-handler :estimazen/clear-result
  [_] (clear-results))


(defmethod push-msg-handler :estimazen/clear-active-button
  [_]
  (->output! "Clearing active button")
  (remove-marks-from-buttons!))

(defmethod push-msg-handler :estimazen/est-stats-estimated
  [[id {:keys [number-estimated]}]]
  (->output! "Number-Estimated recieved from server: %s" number-estimated)
  (when-let [results-el (.getElementById js/document "est-stats-estimated")]
    (-> results-el
      (.-innerHTML)
      (set! number-estimated))))

(defmethod push-msg-handler :estimazen/est-stats-clients
  [[id {:keys [number-clients]}]]
  (->output! "Number-Clients recieved from server: %s" number-clients)
  (when-let [results-el (.getElementById js/document "est-stats-clients")]
    (-> results-el
      (.-innerHTML)
      (set! number-clients))))


(comment
  (sente/chsk-reconnect! chsk)

  (in-ns 'estimazen.client)
  (if-let [results-el (.getElementById js/document "est-results")]
    (-> results-el
      (.-style)
      (.-display)
      (set! "visible")))
  (event-msg-handler
    {:id :chsk/recv
     :?data [:estimazen/est-result {:estimations [] :html "<ul></ul>"}]})
  (do))


;;;; Sente event router (our `event-msg-handler` loop)

(defonce router_ (atom nil))
(defn stop-router! [] (when-let [stop-f @router_] (stop-f)))
(defn start-router! []
  (stop-router!)
  (reset! router_
    (sente/start-client-chsk-router!
      ch-chsk event-msg-handler)))

;;;; UI events

(defn get-classes [el]
  (-> el (.getAttribute "class") (str/split " ")))
(defn set-classes! [el classes]
  (-> el (.setAttribute "class" (str/join " " classes))))

(defonce active-btn-class "active-btn")
(defonce sealed-btn-class "sealed-btn")
(defn remove-marks-from-buttons! []
  (let [remove-class (fn [class]
                       (when-let [target-els (.getElementsByClassName js/document class)]
                         (doseq [target-el target-els]
                           (set-classes! target-el (->> target-el get-classes (remove #{class}))))))]
    (run! remove-class [active-btn-class sealed-btn-class])))
(defn mark-button-active [button-el]
  (remove-marks-from-buttons!)
  (->output! "Marking active button: %s" (.-textContent button-el))
  (set-classes! button-el (-> button-el get-classes (conj active-btn-class))))
(defn mark-active-button-sealed! []
  (doseq [button-el (.getElementsByClassName js/document active-btn-class)]
    (->output! "Marking active button sealed: %s" (.-textContent button-el))
    (set-classes! button-el (-> button-el get-classes (->> (remove #{active-btn-class})) (conj sealed-btn-class)))))


(defn on-click-voting-button [target-el ev]
  (->output! "est-Button was clicked: %s" (.-textContent target-el))
  (mark-button-active target-el)
  (clear-results)
  (chsk-send! [:estimazen/est-button {:btn-value (.-textContent target-el) :had-a-callback? "nope"}]))

;;;;; Register for UI-Events

(defn register-voting-buttons []
  (when-let [target-els (.getElementsByClassName js/document "est-btn")]
    (doseq [target-el target-els]
      (->output! "Registered Btn %s" (.-textContent target-el))
      (.addEventListener target-el "click" (partial on-click-voting-button target-el)))))


;;;; Init stuff

(defn start! []
  (->output! "~~> start!ing")
  (init-channel)
  (start-router!)
  (register-voting-buttons))



(defonce _start-once (start!))
