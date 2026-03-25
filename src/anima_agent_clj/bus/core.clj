(ns anima-agent-clj.bus.core
  (:import [java.util UUID Date]))

(defrecord Message
           [id trace-id source target type priority payload metadata timestamp ttl])

(defprotocol IMessageBus
  (publish!   [this msg])
  (subscribe! [this topic handler])
  (close!     [this]))

(defn make-message
  [{:keys [trace-id priority] :as opts}]
  (map->Message
   (merge {:id        (str (UUID/randomUUID))
           :trace-id  (or trace-id (str (UUID/randomUUID)))
           :priority  (or priority 5)
           :metadata  {}
           :timestamp (Date.)
           :ttl       30000}
          opts)))
