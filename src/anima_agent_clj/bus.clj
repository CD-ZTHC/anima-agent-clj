(ns anima-agent-clj.bus
  "Unified message bus. Wraps the four cluster buses
   and provides domain-level constructors."
  (:require [clojure.core.async :as a]
            [anima-agent-clj.bus.core   :as core]
            [anima-agent-clj.bus.impl   :as impl])
  (:import [java.util UUID]))

;; ──────────────────────────────────────────────────────────────────────────────
;; Domain message types
;; ──────────────────────────────────────────────────────────────────────────────

(defrecord InboundMessage
           [id channel sender-id chat-id content session-key media metadata])

(defrecord OutboundMessage
           [id channel account-id chat-id content media stage reply-target])

(defn make-inbound
  [{:keys [channel sender-id chat-id content session-key media metadata]
    :or   {sender-id "unknown" media [] metadata {}}}]
  (->InboundMessage (str (UUID/randomUUID))
                    channel sender-id chat-id content
                    session-key media metadata))

(defn make-outbound
  [{:keys [channel account-id chat-id content media stage reply-target]
    :or   {account-id "default" media [] stage :final}}]
  (->OutboundMessage (str (UUID/randomUUID))
                     channel account-id chat-id content
                     media stage reply-target))

;; ──────────────────────────────────────────────────────────────────────────────
;; Bus lifecycle
;; ──────────────────────────────────────────────────────────────────────────────

(defrecord Bus [inbound outbound internal control])

(defn create-bus []
  (->Bus (impl/inbound-bus)
         (impl/outbound-bus)
         (impl/internal-bus)
         (impl/control-bus)))

(defn close-bus [bus]
  (run! core/close! (vals bus)))

;; ──────────────────────────────────────────────────────────────────────────────
;; Publishing
;; ──────────────────────────────────────────────────────────────────────────────

(defn publish-inbound!  [bus msg] (core/publish! (:inbound  bus) msg))
(defn publish-outbound! [bus msg] (core/publish! (:outbound bus) msg))
(defn publish-internal! [bus msg] (core/publish! (:internal bus) msg))
(defn publish-control!  [bus msg] (core/publish! (:control  bus) msg))

;; ──────────────────────────────────────────────────────────────────────────────
;; Subscribing
;; ──────────────────────────────────────────────────────────────────────────────

(defn subscribe-inbound!  [bus handler] (core/subscribe! (:inbound  bus) nil handler))
(defn subscribe-outbound! [bus handler] (core/subscribe! (:outbound bus) nil handler))
(defn subscribe-internal! [bus handler] (core/subscribe! (:internal bus) nil handler))
(defn subscribe-control!  [bus handler] (core/subscribe! (:control  bus) nil handler))
