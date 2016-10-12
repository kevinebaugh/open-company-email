(ns oc.email
  (:gen-class)
  (:require [com.stuartsierra.component :as component]
            [manifold.stream :as stream]
            [taoensso.timbre :as timbre]
            [oc.email.config :as c]
            [oc.lib.sentry-appender :as sentry]
            [oc.lib.sqs :as sqs]
            [oc.email.mailer :as mailer]))

(defn system [config-options]
  (let [{:keys [sqs-queue sqs-msg-handler]} config-options]
    (component/system-map
      :sqs (sqs/sqs-listener sqs-queue sqs-msg-handler))))

(defn sqs-handler [sys msg]
  (let [msg-body (read-string (:body msg))
        error (if (:test-error msg-body) (/ 1 0) false)] ; test Sentry error reporting
    (timbre/info "Received message from SQS.")
    (timbre/tracef "\nMessage from SQS: %s\n" msg-body)
    (if (= (:type msg-body) "invite")
      (mailer/send-invite msg-body)
      (mailer/send-snapshot msg-body)))
  msg)

(defn -main []

  ;; Log errors to Sentry
  (if c/dsn
    (timbre/merge-config!
      {:level     :info
       :appenders {:sentry (sentry/sentry-appender c/dsn)}})
    (timbre/merge-config! {:level :debug}))

  ;; Uncaught exceptions go to Sentry
  (Thread/setDefaultUncaughtExceptionHandler
   (reify Thread$UncaughtExceptionHandler
     (uncaughtException [_ thread ex]
       (timbre/error ex "Uncaught exception on" (.getName thread) (.getMessage ex)))))

  (println (str "\n"
    (when c/intro? (str (slurp (clojure.java.io/resource "oc/assets/ascii_art.txt")) "\n"))
    "OpenCompany Email Service\n\n"
    "AWS SQS queue: " c/aws-sqs-email-queue "\n"
    "Sentry: " c/dsn "\n\n"
    (when c/intro? "Ready to serve...\n")))

  (component/start (system {:sqs-queue c/aws-sqs-email-queue
                            :sqs-msg-handler sqs-handler}))

  (deref (stream/take! (stream/stream)))) ; block forever


(comment

  ;; SQS message payload
  (def snapshot (json/decode (slurp "./opt/samples/updates/green-labs.json")))
  (def message 
    {:subject "GreenLabs Update"
     :to "change@changeme.com,change2@changeme.com"
     :note "Howdy folks!"
     :reply-to "hange@changeme.com"
     :company-slug "green-labs"
     :snapshot snapshot})

  (require '[amazonica.aws.sqs :as sqs2])
  
  ;; send a test SQS message
  (sqs2/send-message
     {:access-key c/aws-access-key-id
      :secret-key c/aws-secret-access-key}
    c/aws-sqs-email-queue
    message)

  ;; send a test message that will cause an exception
  (sqs2/send-message 
     {:access-key c/aws-access-key-id
      :secret-key c/aws-secret-access-key}
    c/aws-sqs-email-queue
    {:test-error true})

  )