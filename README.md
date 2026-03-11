# OpenCode Clojure Client

A comprehensive Clojure client library for interacting with the [opencode-server](https://github.com/sst/opencode) REST API. This library provides idiomatic Clojure wrappers for all opencode-server functionality, making it easy to integrate AI-powered coding assistance into your Clojure applications.

## Features

- **Full API Coverage**: Complete implementation of all opencode-server endpoints
- **Idiomatic Clojure**: Clean, functional API design following Clojure best practices
- **Macro Support**: Convenient macros for common operations
- **Session Management**: Create, manage, and interact with coding sessions
- **Message Handling**: Send prompts and receive AI responses
- **File Operations**: Read, write, and manage project files
- **Configuration**: Dynamic configuration management
- **Async Support**: Asynchronous operations for better performance
- **Message Bus Architecture**: Unified message routing between channels and agents
- **Multi-Channel Support**: CLI, RabbitMQ, and extensible channel system
- **Streaming Support**: Real-time message streaming capabilities

## Installation

Add the following dependency to your `project.clj`:

```clojure
[opencode-clj "0.1.0-SNAPSHOT"]
```

Or in your `deps.edn`:

```clojure
opencode-clj {:mvn/version "0.1.0-SNAPSHOT"}
```

## Quick Start

### 1. Create a Client

```clojure
(ns my-app.core
  (:require [opencode-clj.core :as opencode]))

;; Create a client connected to your opencode-server
(def client (opencode/client "http://127.0.0.1:9711"))
```

### 2. Using Macros for Convenience

```clojure
(ns my-app.core
  (:require [opencode-clj.macros.core :as macros]))

;; Define a client using the macro
(macros/defopencode my-client "http://127.0.0.1:9711")
```

### 3. Basic Conversation Example

```clojure
(ns my-app.core
  (:require [opencode-clj.core :as opencode]
            [opencode-clj.macros.core :as macros]))

(macros/defopencode test-client "http://127.0.0.1:9711")

(defn test-basic-conversation []
  ;; Create a session
  (let [session (opencode/create-session test-client {:title "Test Conversation"})]
    (println "Created session:" (:id session))

    ;; Send a prompt
    (let [response (opencode/send-prompt test-client
                                        (:id session)
                                        {:text "Hello, can you help me write a Python hello world function?"}
                                        "user-chat-assistant")]
      (println "Response:" response))

    ;; Get message history
    (let [messages (opencode/list-messages test-client (:id session))]
      (println "Message count:" (count messages)))

    ;; Clean up
    (opencode/delete-session test-client (:id session))))
```

## Message Bus Architecture

The library now includes a powerful message bus architecture for building scalable, multi-channel AI applications.

### Architecture Overview

```
User → Channel → Bus.inbound → Agent → Bus.outbound → Dispatch → Channel → User
```

### Core Components

- **Bus**: Unified message routing with inbound/outbound channels
- **Channel**: Messaging platform interface (CLI, RabbitMQ, etc.)
- **Agent**: Message processing with OpenCode API integration
- **Dispatch**: Outbound message routing to channels
- **Registry**: Channel registration and lookup

### Quick Example

```clojure
(ns my-app.core
  (:require [opencode-clj.bus :as bus]
            [opencode-clj.agent :as agent]
            [opencode-clj.channel :as ch]
            [opencode-clj.channel.cli :as cli]
            [opencode-clj.channel.registry :as registry]
            [opencode-clj.channel.dispatch :as dispatch]
            [opencode-clj.channel.session :as session]))

;; Create infrastructure
(let [msg-bus (bus/create-bus)
      store (session/create-store)
      reg (registry/create-registry)
      stats (dispatch/create-dispatch-stats)

      ;; Create CLI channel
      cli-ch (cli/create-cli-channel {:session-store store
                                      :bus msg-bus})

      ;; Create agent
      msg-agent (agent/create-agent {:bus msg-bus
                                     :opencode-url "http://127.0.0.1:9711"})]

  ;; Register and start
  (registry/register reg cli-ch)
  (ch/start cli-ch)
  (agent/start-agent msg-agent)
  (dispatch/start-outbound-dispatch (:outbound-chan msg-bus) reg stats))
```

## Channel System

### CLI Channel

Interactive command-line interface:

```clojure
(require '[opencode-clj.channel.cli :as cli])

(def cli-ch (cli/create-cli-channel
             {:session-store store
              :bus msg-bus
              :prompt "ai> "}))

(ch/start cli-ch)
;; Now accepts user input from stdin
```

### RabbitMQ Channel

Message queue integration for distributed systems:

```clojure
(require '[opencode-clj.channel.rabbitmq :as rmq])

(def rmq-ch (rmq/create-rabbitmq-channel
             {:uri "amqp://guest:guest@localhost:5672"
              :exchange "opencode.messages"
              :queue "opencode.inbox"
              :bus msg-bus}))

(ch/start rmq-ch)
```

### Custom Channels

Implement the Channel protocol for custom integrations:

```clojure
(require '[opencode-clj.channel :as ch])

(defrecord MyChannel [config running?]
  ch/Channel
  (start [this] ...)
  (stop [this] ...)
  (send-message [this target message opts] ...)
  (channel-name [this] "my-channel")
  (health-check [this] @running?))
```

### Routing Keys

Session-based routing patterns:

- `opencode.session.{session-id}` - Direct session routing
- `opencode.user.{user-id}` - User-level routing
- `opencode.broadcast` - Broadcast to all

## Core API

### Client Management

```clojure
;; Create client with options
(def client (opencode/client "http://127.0.0.1:9711"
                            {:directory "/path/to/project"
                             :http-opts {:timeout 5000}}))
```

### Session Management

```clojure
;; List all sessions
(opencode/list-sessions client)

;; Create new session
(opencode/create-session client {:title "My Coding Session"})

;; Get session details
(opencode/get-session client session-id)

;; Update session
(opencode/update-session client session-id {:title "Updated Title"})

;; Delete session
(opencode/delete-session client session-id)

;; Fork session
(opencode/fork-session client session-id)

;; Share session
(opencode/share-session client session-id)
```

### Messaging

```clojure
;; Send prompt to AI
(opencode/send-prompt client session-id
                     {:text "Help me debug this code"}
                     "user-chat-assistant")

;; List messages in session
(opencode/list-messages client session-id)

;; Execute command
(opencode/execute-command client session-id command)

;; Run shell command
(opencode/run-shell-command client session-id command)
```

### File Operations

```clojure
;; List files in project
(opencode/list-files client)

;; Read file content
(opencode/read-file client file-path)

;; Find text in files
(opencode/find-text client search-pattern)

;; Find files by pattern
(opencode/find-files client file-pattern)

;; Find symbols
(opencode/find-symbols client symbol-pattern)
```

### Configuration

```clojure
;; Get current configuration
(opencode/get-config client)

;; Update configuration
(opencode/update-config client new-config)

;; List available providers
(opencode/list-providers client)

;; List available commands
(opencode/list-commands client)

;; List available agents
(opencode/list-agents client)
```

## Advanced Usage

### New Chatbot Macro System

The redesigned chatbot macro system provides a simplified, intuitive API for managing conversations with AI assistants.

#### Basic Chatbot Definition

```clojure
(ns my-app.core
  (:require [opencode-clj.macros.chatbot :as chatbot]))

;; Define a chatbot with configuration
(chatbot/def-chatbot coding-assistant
  :base-url "http://127.0.0.1:9711"
  :default-agent "claude-3"
  :system-prompt "You are an expert programming assistant"
  :temperature 0.7
  :max-tokens 4000)
```

#### Conversation Management

```clojure
;; Simple conversation with automatic session management
(chatbot/with-chat-session [session coding-assistant]
  (let [response1 (chatbot/send-message session "Hello, can you help me with programming?")
        response2 (chatbot/send-message session "Write a Python function to calculate factorial")
        history (chatbot/get-conversation session)]
    (println "Response:" (chatbot/extract-message-text response1))
    (println "History count:" (count history))))
```

#### State Management

```clojure
;; Manage conversation state
(chatbot/with-chat-session [session coding-assistant]
  (chatbot/with-conversation-state [state {:mode :coding :language :python}]
    (chatbot/update-state! state {:current-topic "functions"})
    (let [current-state (chatbot/get-state state)]
      (println "Current state:" current-state))))
```

#### Message Handlers

```clojure
;; Define custom message handlers
(chatbot/def-message-handler handle-code-request
  [msg session]
  (when (clojure.string/includes? (clojure.string/lower-case (:text msg)) "code")
    (println "Detected code request")
    (chatbot/send-message session (:text msg) :agent "claude-3")))

;; Use the handler
(handle-code-request {:text "Can you write some code?"} session)
```

#### Conversation Pipeline

```clojure
;; Create message processing pipeline
(def message-pipeline
  (chatbot/conversation-pipeline [input _]
    :preprocess preprocess-message
    :process handle-message
    :postprocess format-response))

;; Use the pipeline
(let [result (message-pipeline {:text "Hello"})]
  (println "Processed:" result))
```

#### Multimodal Messages

```clojure
;; Create multimodal messages
(let [image-message (chatbot/multimodal-message
                     :text "Analyze this image"
                     :image-path "/path/to/image.png"
                     :audio-path "/path/to/audio.wav")]
  (println "Multimodal message:" image-message))
```

### Async Operations

```clojure
(ns my-app.core
  (:require [opencode-clj.macros.async :as async]
            [clojure.core.async :refer [<!!]]))

;; Perform async operations
(let [result-chan (async/send-prompt-async client session-id prompt)]
  (println "Response:" (<!! result-chan)))
```

### Legacy Chatbot Macros (Deprecated)

```clojure
(ns my-app.core
  (:require [opencode-clj.macros.chatbot :as chatbot]))

;; Legacy chatbot definition (deprecated)
(chatbot/defchatbot my-bot "http://127.0.0.1:9711"
  :system-prompt "You are a helpful coding assistant specialized in Clojure."
  :temperature 0.7)
```

### DSL for Complex Workflows

```clojure
(ns my-app.core
  (:require [opencode-clj.macros.dsl :as dsl]))

;; Define complex workflows
(dsl/defworkflow code-review-workflow
  [client session-id file-path]
  (dsl/send-prompt "Please review this code for potential issues")
  (dsl/wait-for-response)
  (dsl/send-prompt "Can you suggest improvements?")
  (dsl/wait-for-response))
```

## CLI Application

The library includes a ready-to-use CLI application:

```bash
# Start interactive CLI
lein run -m opencode-clj.cli-main

# With custom options
lein run -m opencode-clj.cli-main -- --url http://my-server:9711
lein run -m opencode-clj.cli-main -- --prompt 'ai> '
lein run -m opencode-clj.cli-main -- --help
```

### CLI Commands

- `help` - Show available commands
- `status` - Show session status
- `history` - Show conversation history
- `clear` - Clear conversation history
- `exit/quit/:q` - Exit the CLI

## Testing

Run the test suite:

```bash
lein test
```

Run specific tests:

```bash
lein test opencode-clj.core-test
lein test :only opencode-clj.core-test/test-client-creation
```

## Building

Build the project:

```bash
lein deps
lein uberjar
```

## Configuration

The library supports various configuration options:

- `:base-url` - OpenCode server URL (required)
- `:directory` - Project directory path
- `:http-opts` - HTTP client options (timeout, headers, etc.)

## Error Handling

All functions return either success maps or throw exceptions:

```clojure
(try
  (let [response (opencode/send-prompt client session-id prompt)]
    (if (:success response)
      (println "Success:" response)
      (println "Error:" (:error response))))
  (catch Exception e
    (println "Exception:" (.getMessage e))))
```

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Add tests
5. Run the test suite
6. Submit a pull request

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Support

- Issues: [GitHub Issues](https://github.com/CD-ZTHC/opencode-clj/issues)
- Documentation: [API Reference](https://github.com/CD-ZTHC/opencode-clj/wiki)
- OpenCode Server: [opencode-server](https://github.com/sst/opencode)
