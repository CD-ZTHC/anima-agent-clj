(ns chatbot-demo
  "Chatbot macro usage examples"
  (:require [opencode-clj.macros.chatbot :as chatbot]
            [clojure.string :as str]))

;; ============================================================================
;; Define Chatbots
;; ============================================================================

(chatbot/def-chatbot coding-assistant
  :base-url "http://127.0.0.1:9711"
  :default-agent "claude-3"
  :system-prompt "You are an expert programming assistant"
  :temperature 0.7)

(chatbot/def-chatbot creative-writer
  :base-url "http://127.0.0.1:9711"
  :default-agent "claude-3"
  :system-prompt "You are a creative writer"
  :temperature 0.9)

;; ============================================================================
;; Basic Conversation Demo
;; ============================================================================

(defn demo-basic-conversation []
  (println "\n=== Basic Conversation ===")
  (chatbot/with-chat-session [session coding-assistant]
    (let [response (chatbot/send-message session "Hello, introduce yourself")]
      (println "Response:" (chatbot/extract-message-text response)))

    (let [response (chatbot/send-message session "Write a Python factorial function")]
      (println "Response:" (chatbot/extract-message-text response)))

    (let [history (chatbot/get-conversation session)]
      (println "History count:" (count history)))))

;; ============================================================================
;; State Management Demo
;; ============================================================================

(defn demo-state-management []
  (println "\n=== State Management ===")
  (chatbot/with-chat-session [session coding-assistant]
    (chatbot/with-conversation-state [state {:language :python :level :beginner}]
      (let [current (chatbot/get-state state)]
        (println "Initial state:" current))

      ;; Update state
      (chatbot/update-state! state :level :intermediate :topic "functions")
      (println "Updated state:" (chatbot/get-state state))

      ;; Use state in message
      (let [state (chatbot/get-state state)
            response (chatbot/send-message session
                                           (str "Teach me " (name (:language state))
                                                " " (name (:topic state)) " at "
                                                (name (:level state)) " level"))]
        (println "Response:" (chatbot/extract-message-text response))))))

;; ============================================================================
;; Message Handlers Demo
;; ============================================================================

(chatbot/def-message-handler handle-code-request
  [msg session]
  (when (str/includes? (str/lower-case (:text msg)) "code")
    (println "Detected code request")
    (chatbot/send-message session (:text msg))))

(chatbot/def-command-handler handle-help
  [args session]
  (chatbot/send-message session "Available commands: help, status, quit"))

(defn demo-handlers []
  (println "\n=== Message Handlers ===")
  (chatbot/with-chat-session [session coding-assistant]
    ;; Test message handler
    (handle-code-request {:text "Write some code"} session)

    ;; Test command handler
    (handle-help {} session)))

;; ============================================================================
;; Pipeline Demo
;; ============================================================================

(def message-pipeline
  (chatbot/conversation-pipeline [input]
                                 :preprocess (fn [msg] (update msg :text str/trim))
                                 :enrich (fn [msg] (assoc msg :timestamp (System/currentTimeMillis)))
                                 :format (fn [msg] (str "[" (:timestamp msg) "] " (:text msg)))))

(defn demo-pipeline []
  (println "\n=== Pipeline ===")
  (let [result (message-pipeline {:text "  hello world  "})]
    (println "Pipeline result:" result)))

;; ============================================================================
;; Multimodal Demo
;; ============================================================================

(defn demo-multimodal []
  (println "\n=== Multimodal Messages ===")
  (let [image-msg (chatbot/multimodal-message
                   :text "Analyze this image"
                   :image-path "/path/to/image.png")
        audio-msg (chatbot/multimodal-message
                   :text "Transcribe this audio"
                   :audio-path "/path/to/audio.wav")]
    (println "Image message:" image-msg)
    (println "Audio message:" audio-msg)))

;; ============================================================================
;; Run All Demos
;; ============================================================================

(defn run-all []
  (println "=== Chatbot Macros Demo ===")
  (demo-basic-conversation)
  (demo-state-management)
  (demo-handlers)
  (demo-pipeline)
  (demo-multimodal)
  (println "\n=== Demo Complete ==="))

(comment
  (run-all))
