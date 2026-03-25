(ns anima-agent-clj.agent.ai-classifier-test
  "Unit tests for AI-powered task classifier."
  (:require [clojure.test :refer :all]
            [clojure.string :as str]
            [anima-agent-clj.agent.ai-classifier :as ai-classifier]))

;; ══════════════════════════════════════════════════════════════════════════════
;; Quick Pattern Classification Tests
;; ══════════════════════════════════════════════════════════════════════════════

(deftest test-quick-classify-greetings
  (testing "Quick classify greeting patterns"
    (are [msg expected-type] (= expected-type (:type (ai-classifier/quick-classify msg)))
      "hi" :simple-chat
      "hello" :simple-chat
      "Hello" :simple-chat
      "你好" :simple-chat
      "您好" :simple-chat
      "thanks" :simple-chat
      "谢谢" :simple-chat)))

(deftest test-quick-classify-status-queries
  (testing "Quick classify status query patterns"
    (are [msg expected-type] (= expected-type (:type (ai-classifier/quick-classify msg)))
      "status" :status-query
      "任务状态" :status-query
      "进度" :status-query)))

(deftest test-quick-classify-system-commands
  (testing "Quick classify system command patterns"
    (are [msg expected-type] (= expected-type (:type (ai-classifier/quick-classify msg)))
      "exit" :system-command
      "quit" :system-command
      "退出" :system-command
      "再见" :system-command)))

(deftest test-quick-classify-complex-tasks
  (testing "Quick classify complex task patterns"
    (are [msg expected-type] (= expected-type (:type (ai-classifier/quick-classify msg)))
      "write a function" :complex-task
      "Create a component" :complex-task
      "build a website" :complex-task
      "implement a service" :complex-task
      "design a system" :complex-task
      "帮我写个函数" :complex-task
      "请帮我创建一个网站" :complex-task
      "实现一个功能" :complex-task
      "fix the bug" :complex-task
      "修复这个错误" :complex-task)))

(deftest test-quick-classify-no-match
  (testing "Messages that don't match quick patterns return nil"
    (are [msg] (nil? (ai-classifier/quick-classify msg))
      "What is the weather today?"
      "Tell me about Clojure"
      "random text here")))

;; ══════════════════════════════════════════════════════════════════════════════
;; Classification Types Tests
;; ══════════════════════════════════════════════════════════════════════════════

(deftest test-classification-types-structure
  (testing "Classification types have required keys"
    (doseq [[type-key type-info] ai-classifier/classification-types]
      (is (keyword? type-key) "Type key should be keyword")
      (is (string? (:description type-info)) "Should have description")
      (is (keyword? (:handler type-info)) "Should have handler")
      (is (integer? (:priority type-info)) "Should have priority"))))

(deftest test-classification-type-handlers
  (testing "Classification types map to correct handlers"
    (is (= :dialog-agent (:handler (:simple-chat ai-classifier/classification-types))))
    (is (= :orchestrator (:handler (:complex-task ai-classifier/classification-types))))
    (is (= :status-handler (:handler (:status-query ai-classifier/classification-types))))
    (is (= :system-handler (:handler (:system-command ai-classifier/classification-types))))))

;; ══════════════════════════════════════════════════════════════════════════════
;; Classifier Creation and Status Tests
;; ══════════════════════════════════════════════════════════════════════════════

(deftest test-create-ai-classifier
  (testing "Create AI classifier with default options"
    (let [mock-client {:base-url "http://test"}
          classifier (ai-classifier/create-ai-classifier {:client mock-client})]
      (is (some? classifier))
      (is (= mock-client (:client classifier)))
      (is (= "zhipuai-coding-plan/glm-4.7-flashx" (:model classifier)))))

  (testing "Create AI classifier with custom model"
    (let [mock-client {:base-url "http://test"}
          classifier (ai-classifier/create-ai-classifier
                      {:client mock-client
                       :model "claude-3-haiku"})]
      (is (= "claude-3-haiku" (:model classifier)))))

  (testing "Create AI classifier requires client"
    (is (thrown? clojure.lang.ExceptionInfo
                 (ai-classifier/create-ai-classifier {})))))

(deftest test-classifier-status
  (testing "Classifier status returns correct information"
    (let [mock-client {:base-url "http://test"}
          classifier (ai-classifier/create-ai-classifier
                      {:client mock-client
                       :model "test-model"})]
      (let [status (ai-classifier/classifier-status classifier)]
        (is (string? (:id status)))
        (is (= :ready (:status status)))
        (is (= "test-model" (:model status)))
        (is (integer? (:cache-size status)))))))

(deftest test-clear-cache!
  (testing "Clear cache resets cache size"
    (let [mock-client {:base-url "http://test"}
          classifier (ai-classifier/create-ai-classifier {:client mock-client})]
      ;; Verify initial cache size
      (let [initial-status (ai-classifier/classifier-status classifier)]
        (is (= 0 (:cache-size initial-status))))
      ;; Clear cache should not throw
      (ai-classifier/clear-cache! classifier)
      ;; Verify cache is still empty
      (let [status (ai-classifier/classifier-status classifier)]
        (is (= 0 (:cache-size status)))))))

;; ══════════════════════════════════════════════════════════════════════════════
;; Smart Classify Tests (without AI calls)
;; ══════════════════════════════════════════════════════════════════════════════

(deftest test-smart-classify-uses-quick-patterns
  (testing "Smart classify uses quick patterns when available"
    ;; These should be classified by quick patterns, not AI
    (let [result (ai-classifier/smart-classify nil "exit" {:skip-ai? true})]
      (is (= :system-command (:type result)))
      (is (= 1.0 (:confidence result)))
      (is (:quick? result)))

    (let [result (ai-classifier/smart-classify nil "请帮我写个函数" {:skip-ai? true})]
      (is (= :complex-task (:type result)))
      (is (= 1.0 (:confidence result)))
      (is (:quick? result)))))

(deftest test-smart-classify-no-classifier
  (testing "Smart classify with no classifier and no quick match returns default"
    (let [result (ai-classifier/smart-classify nil "Some random message" {:skip-ai? true})]
      (is (= :simple-chat (:type result)))
      (is (= 0.5 (:confidence result)))
      (is (= "AI skipped" (:reasoning result))))))

;; ══════════════════════════════════════════════════════════════════════════════
;; Edge Cases
;; ══════════════════════════════════════════════════════════════════════════════

(deftest test-empty-message
  (testing "Empty message handling"
    (is (nil? (ai-classifier/quick-classify "")))
    (is (nil? (ai-classifier/quick-classify "   ")))))

(deftest test-unicode-message
  (testing "Unicode message handling"
    (is (= :simple-chat (:type (ai-classifier/quick-classify "你好"))))
    (is (= :complex-task (:type (ai-classifier/quick-classify "请帮我写个函数"))))))

(deftest test-mixed-language-message
  (testing "Mixed language message handling"
    (is (= :complex-task (:type (ai-classifier/quick-classify "Please help me 实现一个功能"))))))

;; ══════════════════════════════════════════════════════════════════════════════
;; Default Prompt Template Tests
;; ══════════════════════════════════════════════════════════════════════════════

(deftest test-default-prompt-template
  (testing "Default classification prompt exists and contains required content"
    (is (string? ai-classifier/default-classification-prompt))
    (is (str/includes? ai-classifier/default-classification-prompt "task classifier"))
    (is (str/includes? ai-classifier/default-classification-prompt "simple-chat"))
    (is (str/includes? ai-classifier/default-classification-prompt "complex-task"))))

;; ══════════════════════════════════════════════════════════════════════════════
;; Integration Note
;; ══════════════════════════════════════════════════════════════════════════════

(deftest ^:integration test-full-classification-requires-server
  (testing "Full classification requires OpenCode server (integration test)"
    ;; This test is tagged as :integration and will be skipped in normal test runs
    ;; To run integration tests: lein test :integration
    (is true "Placeholder for integration test")))
