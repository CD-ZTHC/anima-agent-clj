(ns opencode-clj.projects
  "Project management for opencode-server.

   Functions: list-projects, current-project"
  (:require [opencode-clj.client :as http]
            [opencode-clj.utils :as utils]))

(defn list-projects
  "List all projects"
  [client & [params]]
  (-> (http/get-request client "/project" params)
      utils/handle-response))

(defn current-project
  "Get the current project"
  [client & [params]]
  (-> (http/get-request client "/project/current" params)
      utils/handle-response))