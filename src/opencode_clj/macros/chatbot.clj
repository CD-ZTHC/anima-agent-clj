(ns opencode-clj.macros.chatbot
  "Chatbot macro system for simplified conversation management.

   Core macros:
   - def-chatbot: Define chatbot configuration
   - with-chat-session: Session context with auto cleanup
   - send-message: Send message to session
   - get-conversation: Get conversation history

   State management:
   - with-conversation-state: State context
   - update-state!: Update state
   - get-state: Get current state"
  (:require [opencode-clj.core :as opencode]
            [opencode-clj.client :as client]
            [clojure.string :as str]))

;; ============================================================================
;; Core Macros
;; ============================================================================

(defmacro def-chatbot
  "Define chatbot configuration.

   Options: :base-url, :default-agent, :system-prompt, :temperature, :max-tokens"
  [name & config]
  (let [config-map (apply hash-map config)]
    `(def ~name
       (merge {:base-url "http://127.0.0.1:9711"
               :temperature 0.7
               :max-tokens 4000}
              ~config-map))))

(defmacro with-chat-session
  "Execute operations in session context with automatic cleanup."
  [[session-sym bot-sym] & body]
  `(let [client# (opencode/client (:base-url ~bot-sym))
         session-result# (opencode/create-session client#)]
     (if (:id session-result#)
       (let [~session-sym (assoc session-result# :client client# :config ~bot-sym)]
         (try
           (println "Chat session created:" (client/session-id ~session-sym))
           ~@body
           (finally
             (try
               (opencode/delete-session client# (client/session-id ~session-sym))
               (println "Chat session cleaned up:" (client/session-id ~session-sym))
               (catch Exception e#
                 (println "Warning: Failed to clean up session:" (.getMessage e#)))))))
       (throw (ex-info "Failed to create session" session-result#)))))

(defmacro send-message
  "Send message to session. Options: :agent, :wait"
  [session message & options]
  (let [options-map (apply hash-map options)]
    `(let [client# (:client ~session)
           config# (:config ~session)
           agent-name# (or (:agent ~options-map)
                           (:default-agent config#)
                           (-> (opencode/list-agents client#) last :name))]
       (opencode/send-prompt client# (client/session-id ~session) {:text ~message} agent-name#))))

(defmacro get-conversation
  "Get conversation history for session."
  [session]
  `(let [client# (:client ~session)]
     (opencode/list-messages client# (client/session-id ~session))))

;; ============================================================================
;; Message Handlers
;; ============================================================================

(defmacro def-message-handler
  "Define a message handler function."
  [name [msg-sym session-sym] & body]
  `(defn ~name [~msg-sym ~session-sym] ~@body))

(defmacro def-command-handler
  "Define a command handler function."
  [name [args-sym session-sym] & body]
  `(defn ~name [~args-sym ~session-sym] ~@body))

;; ============================================================================
;; State Management
;; ============================================================================

(defmacro with-conversation-state
  "Execute operations in state context."
  [[state-sym initial-state] & body]
  `(let [~state-sym (atom ~initial-state)] ~@body))

(defmacro update-state!
  "Update conversation state."
  [state & updates]
  `(swap! ~state merge ~(apply hash-map updates)))

(defmacro get-state
  "Get current conversation state."
  [state]
  `@~state)

;; ============================================================================
;; Advanced Features
;; ============================================================================

(defmacro conversation-pipeline
  "Define conversation processing pipeline."
  [[input-sym _] & steps]
  (let [step-pairs (partition 2 steps)
        step-fns (mapv second step-pairs)]
    `(fn [~input-sym]
       (reduce (fn [acc# step-fn#] (step-fn# acc#)) ~input-sym ~step-fns))))

(defmacro multimodal-message
  "Create multimodal message with text, image, or audio."
  [& content]
  `(merge {:parts []} ~(apply hash-map content)))

;; ============================================================================
;; Utility Functions
;; ============================================================================

(defn extract-message-text
  "Extract text content from message response."
  [response]
  (if-let [parts (:parts response)]
    (->> parts (map :text) (filter some?) (str/join "\n"))
    (:text response)))

(defn get-session-id
  "Get session ID from session object."
  [session]
  (client/session-id session))
