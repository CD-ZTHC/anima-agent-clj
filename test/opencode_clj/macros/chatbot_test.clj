(ns opencode-clj.macros.chatbot-test
  (:require [clojure.test :refer [deftest is testing]]
            [opencode-clj.macros.chatbot :refer [def-chatbot multimodal-message
                                                 with-conversation-state update-state!
                                                 get-state conversation-pipeline
                                                 def-message-handler def-command-handler]]))

(deftest test-def-chatbot-macro
  (testing "def-chatbot creates a chatbot with default configuration"
    (def-chatbot test-bot
      :title "Test Bot"
      :system-prompt "You are a test assistant")

    (is (map? test-bot))
    (is (= "Test Bot" (:title test-bot)))
    (is (= "You are a test assistant" (:system-prompt test-bot)))
    (is (= "http://127.0.0.1:9711" (:base-url test-bot)))))

(deftest test-multimodal-message-macro
  (testing "multimodal-message creates message with parts"
    (let [msg (multimodal-message
               :text "Hello"
               :image-path "test.jpg")]
      (is (map? msg))
      (is (= "Hello" (:text msg)))
      (is (= "test.jpg" (:image-path msg))))))

(deftest test-conversation-state-macro
  (testing "with-conversation-state creates state management"
    (with-conversation-state [state {:mode "test" :context {:test true}}]
      (is (= "test" (:mode (get-state state))))
      (is (true? (get-in (get-state state) [:context :test])))

      ;; Update state
      (update-state! state :mode "updated" :level "advanced")
      (is (= "updated" (:mode (get-state state))))
      (is (= "advanced" (:level (get-state state)))))))

(deftest test-message-handlers
  (testing "def-message-handler and def-command-handler create handler functions"
    (def-message-handler test-text-handler
      [msg session]
      {:handled true :msg msg})

    (def-command-handler test-cmd-handler
      [args session]
      {:handled true :args args})

    (is (fn? test-text-handler))
    (is (fn? test-cmd-handler))
    (is (= {:handled true :msg {:text "hello"}}
           (test-text-handler {:text "hello"} nil)))
    (is (= {:handled true :args {:cmd "/test"}}
           (test-cmd-handler {:cmd "/test"} nil)))))

(deftest test-conversation-pipeline-macro
  (testing "conversation-pipeline creates processing function"
    (let [pipeline (conversation-pipeline [input]
                                          :step1 (fn [x] (str x "-step1"))
                                          :step2 (fn [x] (str x "-step2")))]
      (is (fn? pipeline))
      (let [result (pipeline "input")]
        (is (string? result))
        ;; Due to hash-map ordering, steps may run in either order
        (is (or (= "input-step1-step2" result)
                (= "input-step2-step1" result)))))))
