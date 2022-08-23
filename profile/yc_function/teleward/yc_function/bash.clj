(ns teleward.yc-function.bash
  ;; https://cloud.yandex.ru/docs/functions/concepts/function-invoke#request
  (:require
   [cheshire.core :as json]
   [clojure.tools.logging :as log]
   [teleward.config :as config]
   [teleward.logging :as logging]
   [teleward.processing :as processing]
   [teleward.yc-function.state :as state]
   [teleward.telegram :as tg])
  (:import java.util.Base64)
  (:gen-class))


(defn b64-decode ^String [^String string]
  (-> (Base64/getDecoder)
      (.decode string)
      (String. "UTF-8")))


(def config-overrides
  {:logging
   {:console {:target "err"}
    :file nil}})


(defn get-context []
  (let [config
        (-> config-overrides
            config/make-config
            config/validate-config!)

        {:keys [logging
                telegram]}
        config

        me
        (tg/get-me telegram)

        state
        (state/make-state)

        {:keys [telegram]}
        config]

    (logging/init-logging logging)

    {:me me
     :telegram telegram
     :state state
     :config config}))


(defn reply [status body]
  (println
   (json/generate-string
    {:statusCode status
     :body body
     :isBase64Encoded false})))


(defn -main [& _]

  (let [context
        (get-context)

        yc-request
        (json/parse-stream *in* keyword)

        {:keys [
                ;; httpMethod
                ;; headers
                ;; path
                ;; queryStringParameters
                body
                isBase64Encoded]}
        yc-request

        body-decoded
        (if isBase64Encoded
          (b64-decode body)
          body)

        update-entry
        (json/parse-string body-decoded keyword)]

    (log/debug "Payload" update-entry)

    (try
      (processing/process-update context update-entry)
      (processing/process-pending-users context)
      (reply 200 "OK")
      (catch Throwable e
        (log/errorf e "Unhandled exception")
        (reply 500 "Server Error")))))
