# OpenCode Clojure 客户端

一个全面的 Clojure 客户端库，用于与 [opencode-server](https://github.com/sst/opencode) REST API 交互。本库提供符合 Clojure 习惯的 API 封装，让您的 Clojure 应用轻松集成 AI 编程助手功能。

## 特性

- **完整 API 覆盖**: 实现所有 opencode-server 端点
- **地道 Clojure 风格**: 遵循 Clojure 最佳实践的函数式 API 设计
- **宏支持**: 便捷的宏操作
- **会话管理**: 创建、管理和交互编程会话
- **消息处理**: 发送提示并接收 AI 响应
- **文件操作**: 读取、写入和管理项目文件
- **配置管理**: 动态配置管理
- **异步支持**: 高性能异步操作
- **消息总线架构**: 通道与代理间的统一消息路由
- **多通道支持**: CLI、RabbitMQ 及可扩展的通道系统
- **流式支持**: 实时消息流能力

## 安装

在 `project.clj` 中添加依赖：

```clojure
[opencode-clj "0.1.0-SNAPSHOT"]
```

或在 `deps.edn` 中：

```clojure
opencode-clj {:mvn/version "0.1.0-SNAPSHOT"}
```

## 快速开始

### 1. 创建客户端

```clojure
(ns my-app.core
  (:require [opencode-clj.core :as opencode]))

;; 创建连接到 opencode-server 的客户端
(def client (opencode/client "http://127.0.0.1:9711"))
```

### 2. 使用宏

```clojure
(ns my-app.core
  (:require [opencode-clj.macros.core :as macros]))

;; 使用宏定义客户端
(macros/defopencode my-client "http://127.0.0.1:9711")
```

### 3. 基本对话示例

```clojure
(ns my-app.core
  (:require [opencode-clj.core :as opencode]
            [opencode-clj.macros.core :as macros]))

(macros/defopencode test-client "http://127.0.0.1:9711")

(defn test-basic-conversation []
  ;; 创建会话
  (let [session (opencode/create-session test-client {:title "测试对话"})]
    (println "创建会话:" (:id session))

    ;; 发送提示
    (let [response (opencode/send-prompt test-client
                                        (:id session)
                                        {:text "你好，能帮我写一个 Python hello world 函数吗？"}
                                        "user-chat-assistant")]
      (println "响应:" response))

    ;; 获取消息历史
    (let [messages (opencode/list-messages test-client (:id session))]
      (println "消息数量:" (count messages)))

    ;; 清理
    (opencode/delete-session test-client (:id session))))
```

## 消息总线架构

本库现在包含强大的消息总线架构，用于构建可扩展的多通道 AI 应用。

### 架构概览

```
用户 → 通道 → Bus.inbound → Agent → Bus.outbound → Dispatch → 通道 → 用户
```

### 核心组件

- **Bus**: 统一消息路由，包含入站/出站通道
- **Channel**: 消息平台接口（CLI、RabbitMQ 等）
- **Agent**: 消息处理，集成 OpenCode API
- **Dispatch**: 出站消息路由到各通道
- **Registry**: 通道注册和查找

### 快速示例

```clojure
(ns my-app.core
  (:require [opencode-clj.bus :as bus]
            [opencode-clj.agent :as agent]
            [opencode-clj.channel :as ch]
            [opencode-clj.channel.cli :as cli]
            [opencode-clj.channel.registry :as registry]
            [opencode-clj.channel.dispatch :as dispatch]
            [opencode-clj.channel.session :as session]))

;; 创建基础设施
(let [msg-bus (bus/create-bus)
      store (session/create-store)
      reg (registry/create-registry)
      stats (dispatch/create-dispatch-stats)

      ;; 创建 CLI 通道
      cli-ch (cli/create-cli-channel {:session-store store
                                      :bus msg-bus})

      ;; 创建代理
      msg-agent (agent/create-agent {:bus msg-bus
                                     :opencode-url "http://127.0.0.1:9711"})]

  ;; 注册并启动
  (registry/register reg cli-ch)
  (ch/start cli-ch)
  (agent/start-agent msg-agent)
  (dispatch/start-outbound-dispatch (:outbound-chan msg-bus) reg stats))
```

## 通道系统

### CLI 通道

交互式命令行界面：

```clojure
(require '[opencode-clj.channel.cli :as cli])

(def cli-ch (cli/create-cli-channel
             {:session-store store
              :bus msg-bus
              :prompt "ai> "}))

(ch/start cli-ch)
;; 现在可以从标准输入接受用户输入
```

### RabbitMQ 通道

消息队列集成，适用于分布式系统：

```clojure
(require '[opencode-clj.channel.rabbitmq :as rmq])

(def rmq-ch (rmq/create-rabbitmq-channel
             {:uri "amqp://guest:guest@localhost:5672"
              :exchange "opencode.messages"
              :queue "opencode.inbox"
              :bus msg-bus}))

(ch/start rmq-ch)
```

### 自定义通道

实现 Channel 协议以创建自定义集成：

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

### 路由键

基于会话的路由模式：

- `opencode.session.{session-id}` - 直接会话路由
- `opencode.user.{user-id}` - 用户级路由
- `opencode.broadcast` - 广播到所有

## 核心 API

### 客户端管理

```clojure
;; 带选项创建客户端
(def client (opencode/client "http://127.0.0.1:9711"
                            {:directory "/path/to/project"
                             :http-opts {:timeout 5000}}))
```

### 会话管理

```clojure
;; 列出所有会话
(opencode/list-sessions client)

;; 创建新会话
(opencode/create-session client {:title "我的编程会话"})

;; 获取会话详情
(opencode/get-session client session-id)

;; 更新会话
(opencode/update-session client session-id {:title "更新后的标题"})

;; 删除会话
(opencode/delete-session client session-id)

;; 分叉会话
(opencode/fork-session client session-id)

;; 分享会话
(opencode/share-session client session-id)
```

### 消息操作

```clojure
;; 发送提示给 AI
(opencode/send-prompt client session-id
                     {:text "帮我调试这段代码"}
                     "user-chat-assistant")

;; 列出会话中的消息
(opencode/list-messages client session-id)

;; 执行命令
(opencode/execute-command client session-id command)

;; 运行 shell 命令
(opencode/run-shell-command client session-id command)
```

### 文件操作

```clojure
;; 列出项目中的文件
(opencode/list-files client)

;; 读取文件内容
(opencode/read-file client file-path)

;; 在文件中查找文本
(opencode/find-text client search-pattern)

;; 按模式查找文件
(opencode/find-files client file-pattern)

;; 查找符号
(opencode/find-symbols client symbol-pattern)
```

### 配置

```clojure
;; 获取当前配置
(opencode/get-config client)

;; 更新配置
(opencode/update-config client new-config)

;; 列出可用的提供者
(opencode/list-providers client)

;; 列出可用的命令
(opencode/list-commands client)

;; 列出可用的代理
(opencode/list-agents client)
```

## CLI 应用

本库包含一个开箱即用的 CLI 应用：

```bash
# 启动交互式 CLI
lein run -m opencode-clj.cli-main

# 使用自定义选项
lein run -m opencode-clj.cli-main -- --url http://my-server:9711
lein run -m opencode-clj.cli-main -- --prompt 'ai> '
lein run -m opencode-clj.cli-main -- --help
```

### CLI 命令

- `help` - 显示可用命令
- `status` - 显示会话状态
- `history` - 显示对话历史
- `clear` - 清除对话历史
- `exit/quit/:q` - 退出 CLI

## 高级用法

### 聊天机器人宏系统

重新设计的聊天机器人宏系统提供简化、直观的 API 来管理与 AI 助手的对话。

#### 基本聊天机器人定义

```clojure
(ns my-app.core
  (:require [opencode-clj.macros.chatbot :as chatbot]))

;; 带配置定义聊天机器人
(chatbot/def-chatbot coding-assistant
  :base-url "http://127.0.0.1:9711"
  :default-agent "claude-3"
  :system-prompt "你是一个专业的编程助手"
  :temperature 0.7
  :max-tokens 4000)
```

#### 对话管理

```clojure
;; 自动会话管理的简单对话
(chatbot/with-chat-session [session coding-assistant]
  (let [response1 (chatbot/send-message session "你好，能帮我编程吗？")
        response2 (chatbot/send-message session "写一个 Python 阶乘函数")
        history (chatbot/get-conversation session)]
    (println "响应:" (chatbot/extract-message-text response1))
    (println "历史数量:" (count history))))
```

### 异步操作

```clojure
(ns my-app.core
  (:require [opencode-clj.macros.async :as async]
            [clojure.core.async :refer [<!!]]))

;; 执行异步操作
(let [result-chan (async/send-prompt-async client session-id prompt)]
  (println "响应:" (<!! result-chan)))
```

### 复杂工作流 DSL

```clojure
(ns my-app.core
  (:require [opencode-clj.macros.dsl :as dsl]))

;; 定义复杂工作流
(dsl/defworkflow code-review-workflow
  [client session-id file-path]
  (dsl/send-prompt "请审查这段代码的潜在问题")
  (dsl/wait-for-response)
  (dsl/send-prompt "你能建议一些改进吗？")
  (dsl/wait-for-response))
```

## 测试

运行测试套件：

```bash
lein test
```

运行特定测试：

```bash
lein test opencode-clj.core-test
lein test :only opencode-clj.core-test/test-client-creation
```

## 构建

构建项目：

```bash
lein deps
lein uberjar
```

## 配置

库支持多种配置选项：

- `:base-url` - OpenCode 服务器 URL（必需）
- `:directory` - 项目目录路径
- `:http-opts` - HTTP 客户端选项（超时、headers 等）

## 错误处理

所有函数返回成功映射或抛出异常：

```clojure
(try
  (let [response (opencode/send-prompt client session-id prompt)]
    (if (:success response)
      (println "成功:" response)
      (println "错误:" (:error response))))
  (catch Exception e
    (println "异常:" (.getMessage e))))
```

## 贡献

1. Fork 本仓库
2. 创建功能分支
3. 进行更改
4. 添加测试
5. 运行测试套件
6. 提交 Pull Request

## 许可证

本项目采用 MIT 许可证 - 详见 [LICENSE](LICENSE) 文件。

## 支持

- 问题反馈: [GitHub Issues](https://github.com/CD-ZTHC/opencode-clj/issues)
- 文档: [API 参考](https://github.com/CD-ZTHC/opencode-clj/wiki)
- OpenCode 服务器: [opencode-server](https://github.com/sst/opencode)
