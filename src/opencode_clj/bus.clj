(ns opencode-clj.bus
  "Unified message bus for routing messages between channels and agent.

   Core types:
   - InboundMessage: Channel → Agent (user input)
   - OutboundMessage: Agent → Channel (AI response)
   - Bus: Holds inbound/outbound core.async channels

   Message flow:
   Channel.recv → Bus.inbound → Agent → Bus.outbound → Dispatch → Channel.send"
  (:require [clojure.core.async :as async])
  (:import [java.util UUID Date]))

;; ══════════════════════════════════════════════════════════════════════════════
;; Message Types
;; ══════════════════════════════════════════════════════════════════════════════

(defrecord InboundMessage
           [id              ; Message unique ID
            channel         ; Source channel name (e.g. "cli", "rabbitmq")
            sender-id       ; Sender identifier
            chat-id         ; Chat/conversation identifier
            content         ; Message text content
            session-key     ; Session routing key
            media           ; Attached media [{:type :url} ...]
            metadata])      ; Additional metadata {:routing-key :headers ...}

(defrecord OutboundMessage
           [id              ; Message unique ID
            channel         ; Target channel name
            account-id      ; Target account identifier
            chat-id         ; Chat/conversation identifier
            content         ; Response text content
            media           ; Attached media
            stage           ; :chunk or :final
            reply-target])  ; Reply target (routing-key, sender, etc.)

;; ══════════════════════════════════════════════════════════════════════════════
;; Message Constructors
;; ══════════════════════════════════════════════════════════════════════════════

(defn make-inbound
  "Create an InboundMessage.

   Required: :channel, :content
   Optional: :sender-id, :chat-id, :session-key, :media, :metadata"
  [{:keys [channel sender-id chat-id content session-key media metadata]
    :or {sender-id "unknown"
         media []
         metadata {}}}]
  (->InboundMessage
   (str (UUID/randomUUID))
   channel
   sender-id
   chat-id
   content
   session-key
   media
   metadata))

(defn make-outbound
  "Create an OutboundMessage.

   Required: :channel, :content
   Optional: :account-id, :chat-id, :media, :stage, :reply-target"
  [{:keys [channel account-id chat-id content media stage reply-target]
    :or {account-id "default"
         media []
         stage :final}}]
  (->OutboundMessage
   (str (UUID/randomUUID))
   channel
   account-id
   chat-id
   content
   media
   stage
   reply-target))

;; ══════════════════════════════════════════════════════════════════════════════
;; Bus
;; ══════════════════════════════════════════════════════════════════════════════

(defrecord Bus
           [inbound-chan     ; core.async channel: Channel → Agent
            outbound-chan])  ; core.async channel: Agent → Channel

(defn create-bus
  "Create a new message bus with inbound/outbound channels.

   Options:
     :inbound-buf   - inbound channel buffer size (default: 100)
     :outbound-buf  - outbound channel buffer size (default: 100)"
  ([]
   (create-bus {}))
  ([{:keys [inbound-buf outbound-buf]
     :or {inbound-buf 100
          outbound-buf 100}}]
   (->Bus
    (async/chan inbound-buf)
    (async/chan outbound-buf))))

(defn close-bus
  "Close both bus channels."
  [bus]
  (async/close! (:inbound-chan bus))
  (async/close! (:outbound-chan bus)))

;; ══════════════════════════════════════════════════════════════════════════════
;; Bus Operations
;; ══════════════════════════════════════════════════════════════════════════════

(defn publish-inbound!
  "Publish an InboundMessage to the bus. Blocking."
  [bus msg]
  (async/>!! (:inbound-chan bus) msg))

(defn publish-outbound!
  "Publish an OutboundMessage to the bus. Blocking."
  [bus msg]
  (async/>!! (:outbound-chan bus) msg))

(defn publish-inbound-async!
  "Publish an InboundMessage to the bus. Non-blocking (go block)."
  [bus msg]
  (async/go (async/>! (:inbound-chan bus) msg)))

(defn publish-outbound-async!
  "Publish an OutboundMessage to the bus. Non-blocking (go block)."
  [bus msg]
  (async/go (async/>! (:outbound-chan bus) msg)))

(defn consume-inbound!
  "Consume one InboundMessage from the bus. Blocking."
  [bus]
  (async/<!! (:inbound-chan bus)))

(defn consume-outbound!
  "Consume one OutboundMessage from the bus. Blocking."
  [bus]
  (async/<!! (:outbound-chan bus)))