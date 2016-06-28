(ns oc.lib.sqs
  (:require [amazonica.aws.sqs :as sqs]
            [com.stuartsierra.component :as component]
            [taoensso.timbre :as timbre]
            [manifold.stream :as s]
            [manifold.time :as t]
            [manifold.deferred :as d]
            [oc.email.config :as c]))

(def creds
  {:access-key c/aws-access-key-id
   :secret-key c/aws-secret-access-key})

(defn get-message
  "Get a single message from SQS"
  [queue-url]
  (-> (sqs/receive-message creds
                           :queue-url queue-url
                           :wait-time-seconds 2 ; how long to long poll for
                           :max-number-of-messages 1)
      :messages first))

(defn delete-message!
  "Delete a previously received message so it cannot be retrieved by other consumers"
  [creds queue-url msg]
  (timbre/debug "Deleteing message from queue:" queue-url)
  (sqs/delete-message creds (assoc msg :queue-url queue-url)))

(defn process
  "Yield a deferred that will ultimately call `msg-delete` with message put into it.
  If `msg-handler` throws, `msg-delete` will not be called and an error will be logged."
  [msg-handler msg-delete]
  (let [res (d/deferred)]
    (-> res
        (d/chain msg-handler msg-delete)
        (d/catch #(timbre/error "Failed to process SQS message:" %)))
    res))

(defn dispatch-message
  "Check for a message and, if one is available, put it into the given deferrred"
  [queue-url deferred]
  (try
    (timbre/trace "Checking for message in queue:" queue-url)
    (when-let [m (get-message queue-url)]
      (timbre/trace "Got message from queue:" queue-url)
      (d/success! deferred m))
    (catch Throwable e
      (timbre/error "Exception while polling SQS:" e)
      (throw e))))

(defrecord SQSListener [queue-url message-handler]
  ;; Implement the Lifecycle protocol
  component/Lifecycle
  (start [component]
    (timbre/info "Starting SQSListener")
    (let [delete!   (partial delete-message! creds queue-url)
          handle!   (partial message-handler component)
          processor (fn [] (process handle! delete!))
          retriever (t/every 3000 #(dispatch-message queue-url (processor)))]
      (assoc component :retriever retriever)))

  (stop [component]
    (timbre/info "Stopping SQSListener")
    (when-let [r (:retriever component)] (r))
    (dissoc component :retriever)))

(defn sqs-listener [queue-url message-handler]
  (map->SQSListener {:queue-url queue-url :message-handler message-handler}))