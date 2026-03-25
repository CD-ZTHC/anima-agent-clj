(ns anima-agent-clj.utils
  "Utility functions for error handling and validation.

   Functions: handle-response, success?, error?, extract-error-message,
              with-default-params, validate-required, parse-model-string"
  (:require [clojure.string :as str]))

(defn handle-response
  "Handle API response, return data on success or throw exception on error"
  [response]
  (if (:success response)
    (:data response)
    (do (prn response)
        (case (:error response)
          :bad-request (throw (ex-info "Bad request" response))
          :not-found (throw (ex-info "Resource not found" response))
          :server-error (throw (ex-info "Server error" response))
          (throw (ex-info "Unknown error" response))))))

(defn success?
  "Check if response was successful"
  [response]
  (:success response))

(defn error?
  "Check if response was an error"
  [response]
  (not (:success response)))

(defn extract-error-message
  "Extract error message from response"
  [response]
  (or (get-in response [:data :message])
      (get-in response [:data :errors])
      (str (:error response))
      "Unknown error"))

(defn with-default-params
  "Merge user params with default params"
  [defaults params]
  (merge defaults params))

(defn validate-required
  "Validate that required parameters are present"
  [params required-keys]
  (let [missing (remove #(some? (get params %)) required-keys)]
    (when (seq missing)
      (throw (ex-info (str "Missing required parameters: " (str/join ", " missing))
                      {:missing missing
                       :provided params})))
    params))

(defn parse-model-string
  "Parse a model string in 'provider/model' format into {:providerID provider :modelID model}.

   Examples:
   (parse-model-string \"zhipuai-coding-plan/glm-4.7-flashx\")
   => {:providerID \"zhipuai-coding-plan\" :modelID \"glm-4.7-flashx\"}

   (parse-model-string \"openai/gpt-4\")
   => {:providerID \"openai\" :modelID \"gpt-4\"}

   If the string doesn't contain '/', returns nil."
  [model-str]
  (when (and (string? model-str) (str/includes? model-str "/"))
    (let [[provider model] (str/split model-str #"/" 2)]
      (when (and (not (str/blank? provider)) (not (str/blank? model)))
        {:providerID provider :modelID model}))))
