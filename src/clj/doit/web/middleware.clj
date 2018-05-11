(ns doit.web.middleware
  (:require [clojure.algo.generic.functor :refer [fmap]]
            [doit.web.util :as web-util]
            [doit.logging :as log]
            [ring.util.response :as res]
            [cheshire.generate :refer [add-encoder encode-str]])
  (:import org.httpkit.server.AsyncChannel))

;; This is neccessary because http-kit adds some AsyncChannel
;; to the request map if it is a websockets connection upgrade,
;; which can't be JSON serialized.
(def standard-ring-request-keys
  [:server-port :server-name :remote-addr :uri :query-string :scheme
   :headers :request-method :body :params])

(add-encoder AsyncChannel encode-str)
(add-encoder java.io.InputStream encode-str)

(defn wrap-log-request-response
  [handler]
  (fn [request]
    (let [response (handler request)]
      (log/debug {:event    ::request-response
                  :request  (select-keys request standard-ring-request-keys)
                  :response (if (instance? java.io.InputStream (:body response))
                              (assoc response :body-type :input-stream)
                              response)})
      response)))

(defn wrap-error-logging
  [handler]
  (fn [request]
    (let [loggable-request (select-keys request standard-ring-request-keys)]
      (try
        (if-let [response (handler request)]
          response
          (do
            (log/error {:event   ::nil-response
                        :request loggable-request})
            web-util/error-internal-server-error))
        (catch Throwable ex
          (log/error ex {:event   ::unhandled-exception
                         :request loggable-request})
          web-util/error-internal-server-error)))))


(defn wrap-validate
  [handler]
  (fn [request]
    (let [loggable-request (select-keys request standard-ring-request-keys)]
      (try
        (handler request)
        (catch Exception ex
          (if (= :validation-failed
                 (:event (ex-data ex)))
            (do (log/info (merge (ex-data ex)
                                 {:event   ::validation-failed
                                  :request loggable-request}))
                web-util/error-bad-request)
            (throw ex)))))))
