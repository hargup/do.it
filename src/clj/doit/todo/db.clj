(ns doit.todo.db
  (:require [clojure.java.jdbc :as jdbc]
            [doit.config :as config]))

(defn add-todo!
  [values]
  (jdbc/insert! config/db :todo values))
