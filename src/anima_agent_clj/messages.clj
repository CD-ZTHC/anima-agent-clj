(ns anima-agent-clj.messages
  "Messaging functions for opencode-server.

   Functions: list-messages, get-message, send-prompt, execute-command,
              run-shell-command, revert-message, unrevert-messages,
              respond-to-permission"
  (:require [anima-agent-clj.client :as http]
            [anima-agent-clj.utils :as utils]))

(defn list-messages
  "List messages for a session"
  [client session-id & [params]]
  (-> (http/get-request client (str "/session/" (http/session-id session-id) "/message") params)
      utils/handle-response))

(defn get-message
  "Get a specific message from a session"
  [client session-id message-id & [params]]
  (-> (http/get-request client (str "/session/" (http/session-id session-id) "/message/" message-id) params)
      utils/handle-response))

(defn send-prompt
  "Create and send a new message to a session.

   Supported arities:
   - (send-prompt client session-id message)
   - (send-prompt client session-id message agent-or-opts)
   - (send-prompt client session-id message agent opts)

   Parameters:
   - client: OpenCode client
   - session-id: Session ID (string or keyword)
   - message: Message content (string, {:text \"...\"}, or {:parts [...]})
   - agent: Optional agent name (string)
   - opts: Optional map with :model key to specify model

   Examples:
   (send-prompt client session-id \"Hello\")
   (send-prompt client session-id \"Hello\" \"my-agent\")
   (send-prompt client session-id \"Hello\" nil {:model \"claude-3-haiku\"})
   (send-prompt client session-id \"Hello\" \"agent\" {:model \"claude-3-haiku\"})"
  ([client session-id message]
   (send-prompt client session-id message nil nil))
  ([client session-id message agent-or-opts]
   (if (map? agent-or-opts)
     ;; New usage: opts map contains :model or :agent
     (send-prompt client session-id message (:agent agent-or-opts) agent-or-opts)
     ;; Old usage: agent string
     (send-prompt client session-id message agent-or-opts nil)))
  ([client session-id message agent opts]
   ;; Normalize message to ensure parts have required :type field
   (let [normalized-message (cond
                              (string? message)
                              {:parts [{:type "text" :text message}]}
                              (and (map? message) (:text message) (not (:parts message)))
                              {:parts [{:type "text" :text (:text message)}]}
                              (and (map? message) (:parts message))
                              message
                              :else
                              (throw (ex-info "Invalid message format. Expected string, {:text \"...\"}, or {:parts [...]"
                                              {:message message})))
         ;; Build request body with optional agent and model
         ;; Parse model string "provider/model" into providerID and modelID
         ;; For plain model names (no slash), use default provider "zhipuai-coding-plan"
         body (let [parsed-model (when-let [model-str (:model opts)]
                                   (or (utils/parse-model-string model-str)
                                       {:providerID "zhipuai-coding-plan" :modelID model-str}))]
                (cond-> normalized-message
                  agent (assoc :agent agent)
                  parsed-model (merge parsed-model)))]
     (-> (http/post-request client (str "/session/" (http/session-id session-id) "/message") body)
         utils/handle-response))))

(defn execute-command
  "Send a new command to a session"
  [client session-id {:keys [arguments command agent model message-id]}]
  (utils/validate-required {:arguments arguments :command command}
                           [:arguments :command])
  (let [body (cond-> {:arguments arguments :command command}
               agent (assoc :agent agent)
               model (assoc :model model)
               message-id (assoc :messageID message-id))]
    (-> (http/post-request client (str "/session/" (http/session-id session-id) "/command") body)
        utils/handle-response)))

(defn run-shell-command
  "Run a shell command"
  [client session-id {:keys [agent command]}]
  (utils/validate-required {:agent agent :command command}
                           [:agent :command])
  (-> (http/post-request client (str "/session/" (http/session-id session-id) "/shell")
                         {:agent agent :command command})
      utils/handle-response))

(defn revert-message
  "Revert a message"
  [client session-id {:keys [message-id part-id]}]
  (utils/validate-required {:message-id message-id}
                           [:message-id])
  (let [body (cond-> {:messageID message-id}
               part-id (assoc :partID part-id))]
    (-> (http/post-request client (str "/session/" (http/session-id session-id) "/revert") body)
        utils/handle-response)))

(defn unrevert-messages
  "Restore all reverted messages"
  [client session-id & [params]]
  (-> (http/post-request client (str "/session/" (http/session-id session-id) "/unrevert") params)
      utils/handle-response))

(defn respond-to-permission
  "Respond to a permission request"
  [client session-id permission-id response]
  (when-not (contains? #{"once" "always" "reject"} response)
    (throw (ex-info "Invalid response. Must be 'once', 'always', or 'reject'"
                    {:response response})))
  (-> (http/post-request client (str "/session/" (http/session-id session-id) "/permissions/" permission-id)
                         {:response response})
      utils/handle-response))
