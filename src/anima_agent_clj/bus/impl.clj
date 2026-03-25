(ns anima-agent-clj.bus.impl
  "All bus variants share one implementation; only buffer strategy differs."
  (:require [clojure.core.async :as a]
            [anima-agent-clj.bus.core :as core]))

(defn- make-bus [buf]
  (let [ch (a/chan buf)]
    (reify core/IMessageBus
      (publish!   [_ msg]   (a/put! ch msg))
      (subscribe! [_ _ handler]
        (a/go-loop []
          (when-let [msg (a/<! ch)]
            (handler msg)
            (recur))))
      (close! [_] (a/close! ch)))))

(defn inbound-bus  [] (make-bus (a/dropping-buffer 1000)))
(defn outbound-bus [] (make-bus (a/sliding-buffer  1000)))
(defn control-bus  [] (make-bus (a/sliding-buffer    10)))
(defn internal-bus [] (make-bus 100))
