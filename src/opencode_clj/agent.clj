(ns opencode-clj.agent
  "Message processing agent.

   The Agent consumes InboundMessages from the Bus, processes them
   (e.g., calls OpenCode API), and publishes OutboundMessages back
   to the Bus.

   Flow: Bus.inbound → Agent.process-message → Bus.outbound

   Usage:
   (def agent (create-agent {:bus bus :opencode-client client}))
   (start-agent agent)
   ;; ... messages flow through ...
   (stop-agent agent)"
  (:require
   [clojure.core.async :as async]
   [clojure.string :as string]
   [opencode-clj.bus :as bus]
   [opencode-clj.channel.session :as session]
   [opencode-clj.messages :as messages]
   [opencode-clj.sessions :as sessions]))

;; ══════════════════════════════════════════════════════════════════════════════
;; Agent Record
;; ══════════════════════════════════════════════════════════════════════════════

(defrecord Agent
           [bus              ; Bus instance
            opencode-client  ; OpenCode API client {:base-url "..."}
            session-manager  ; SessionStore for managing sessions
            running?         ; atom<boolean>
            worker])         ; atom - holds the async go block

;; ══════════════════════════════════════════════════════════════════════════════
;; Message Processing
;; ══════════════════════════════════════════════════════════════════════════════

(defn- get-or-create-opencode-session
  "Get or create an OpenCode session for processing."
  [opencode-client session-manager inbound-msg]
  (let [session-key (:session-key inbound-msg)
        local-session (when session-key
                        (session/get-session-by-routing-key session-manager session-key))]
    (if (and local-session (get-in local-session [:context :opencode-session-id]))
      (get-in local-session [:context :opencode-session-id])
      (try
        (let [result (sessions/create-session opencode-client)]
          (when (and result (get-in result [:id]))
            (when-let [chat-id (:chat-id inbound-msg)]
              (session/update-session-context
               session-manager chat-id
               {:opencode-session-id (get-in result [:id])}))
            (get-in result [:id])))
        (catch Exception e
          (println "Failed to create OpenCode session:" (.getMessage e))
          nil)))))

(defn process-message
  "Process a single InboundMessage.

   1. Get or create OpenCode session
   2. Send message to OpenCode API
   3. Build OutboundMessage with response
   4. Publish to bus/outbound

   Returns the OutboundMessage on success, or error map on failure."
  [agent inbound-msg]
  (let [{:keys [bus opencode-client session-manager]} agent
        channel-name (:channel inbound-msg)
        content (:content inbound-msg)]
    (try
      (let [opencode-session-id (get-or-create-opencode-session
                                 opencode-client session-manager inbound-msg)]
        (if opencode-session-id
          ;; Send to OpenCode API
          (let [response (messages/send-prompt opencode-client
                                               opencode-session-id
                                               content)]
            (let [response-text (cond
                                  ;; Extract text from response parts (direct format)
                                  (:parts response)
                                  (let [parts (:parts response)
                                        reasoning (->> parts
                                                       (filter #(= "reasoning" (:type %)))
                                                       (map :text)
                                                       (string/join "\n"))
                                        text (->> parts
                                                  (filter #(= "text" (:type %)))
                                                  (map :text)
                                                  (string/join "\n"))]
                                    (if (not-empty reasoning)
                                      (str "【Reasoning】\n" reasoning "\n【End Reasoning】\n\n" text)
                                      text))

                                  ;; Extract text from response messages (nested format)
                                  (get-in response [:data :messages])
                                  (->> (get-in response [:data :messages])
                                       (mapcat :parts)
                                       (filter #(or (= "text" (:type %))
                                                    (= "reasoning" (:type %))))
                                       (map :text)
                                       (string/join "\n"))

                                  ;; Direct content
                                  (:content response)
                                  (:content response)

                                  ;; Fallback
                                  :else (str response))

                  outbound (bus/make-outbound
                            {:channel channel-name
                             :account-id (get-in inbound-msg [:metadata :account-id] "default")
                             :chat-id (:chat-id inbound-msg)
                             :content response-text
                             :reply-target (:sender-id inbound-msg)
                             :stage :final})]
              (bus/publish-outbound! bus outbound)
              outbound))

          ;; Failed to get session
          (let [error-msg (bus/make-outbound
                           {:channel channel-name
                            :content "Error: Failed to create OpenCode session. Make sure opencode-server is running."
                            :reply-target (:sender-id inbound-msg)
                            :stage :final})]
            (bus/publish-outbound! bus error-msg)
            error-msg)))

      (catch Exception e
        (println "Agent error processing message:" (.getMessage e))
        (let [error-msg (bus/make-outbound
                         {:channel channel-name
                          :content (str "Error: " (.getMessage e))
                          :reply-target (:sender-id inbound-msg)
                          :stage :final})]
          (bus/publish-outbound! bus error-msg)
          error-msg)))))

;; ══════════════════════════════════════════════════════════════════════════════
;; Agent Lifecycle
;; ══════════════════════════════════════════════════════════════════════════════

(defn start-agent
  "Start the agent - begins consuming messages from bus/inbound.
   Returns the agent."
  [agent]
  (when-not @(:running? agent)
    (reset! (:running? agent) true)
    (let [worker (async/go-loop []
                   (when @(:running? agent)
                     (when-let [msg (async/<! (:inbound-chan (:bus agent)))]
                       (try
                         (process-message agent msg)
                         (catch Exception e
                           (println "Agent loop error:" (.getMessage e))))
                       (recur))))]
      (reset! (:worker agent) worker)))
  agent)

(defn stop-agent
  "Stop the agent - stops consuming messages.
   Returns the agent."
  [agent]
  (reset! (:running? agent) false)
  (when-let [worker @(:worker agent)]
    (async/close! worker))
  agent)

;; ══════════════════════════════════════════════════════════════════════════════
;; Constructor
;; ══════════════════════════════════════════════════════════════════════════════

(defn create-agent
  "Create a new message processing agent.

   Options:
     :bus              - Bus instance (required)
     :opencode-client  - OpenCode client map {:base-url \"...\"}
     :session-manager  - SessionStore instance (will create if not provided)
     :opencode-url     - OpenCode server URL (default: http://127.0.0.1:9711)"
  [{:keys [bus opencode-client session-manager opencode-url]
    :or {opencode-url "http://127.0.0.1:9711"}}]
  (let [client-map (or opencode-client
                       {:base-url opencode-url})
        sm (or session-manager (session/create-store))]
    (->Agent
     bus
     client-map
     sm
     (atom false)
     (atom nil))))
