(ns oc.email.config
  "Namespace for the configuration parameters."
  (:require [environ.core :refer (env)]
            [taoensso.timbre :as timbre]
            [taoensso.timbre.appenders.core :as appenders]))

;; ----- Sentry -----

(defonce dsn (or (env :open-company-sentry-api) false))

;; ----- AWS SQS / SES -----

(defonce aws-access-key-id (env :aws-access-key-id))
(defonce aws-secret-access-key (env :aws-secret-access-key))
(defonce aws-endpoint (env :aws-endpoint))

(defonce aws-sqs-email-queue (env :aws-sqs-email-queue))

;; ----- Email -----

(defonce email-from-domain (env :email-from-domain))

;; ----- Logging (see https://github.com/ptaoussanis/timbre) -----

(def log-config
  "Example (+default) Timbre v4 config map.

  APPENDERS
    An appender is a map with keys:
      :min-level       ; Level keyword, or nil (=> no minimum level)
      :enabled?        ;
      :async?          ; Dispatch using agent? Useful for slow appenders
      :rate-limit      ; [[ncalls-limit window-ms] <...>], or nil
      :output-fn       ; Optional override for inherited (fn [data]) -> string
      :fn              ; (fn [data]) -> side effects, with keys described below

    An appender's fn takes a single data map with keys:
      :config          ; Entire config map (this map, etc.)
      :appender-id     ; Id of appender currently dispatching
      :appender        ; Entire map of appender currently dispatching

      :instant         ; Platform date (java.util.Date or js/Date)
      :level           ; Keyword
      :error-level?    ; Is level e/o #{:error :fatal}?
      :?ns-str         ; String, or nil
      :?file           ; String, or nil  ; Waiting on CLJ-865
      :?line           ; Integer, or nil ; Waiting on CLJ-865

      :?err_           ; Delay - first-arg platform error, or nil
      :vargs_          ; Delay - raw args vector
      :hostname_       ; Delay - string (clj only)
      :msg_            ; Delay - args string
      :timestamp_      ; Delay - string
      :output-fn       ; (fn [data]) -> formatted output string
                       ; (see `default-output-fn` for details)

      :context         ; *context* value at log time (see `with-context`)
      :profile-stats   ; From `profile` macro

  MIDDLEWARE
    Middleware are simple (fn [data]) -> ?data fns (applied left->right) that
    transform the data map dispatched to appender fns. If any middleware returns
    nil, NO dispatching will occur (i.e. the event will be filtered).

  The `example-config` source code contains further settings and details.
  See also `set-config!`, `merge-config!`, `set-level!`."

  {:level :info  ; e/o #{:trace :debug :info :warn :error :fatal :report}

   ;; Control log filtering by namespaces/patterns. Useful for turning off
   ;; logging in noisy libraries, etc.:
   ;; :ns-whitelist  [] #_["my-app.foo-ns"]
   ;; :ns-blacklist  [] #_["taoensso.*"]

   :middleware [] ; (fns [data]) -> ?data, applied left->right

   ;; Clj only:
   :timestamp-opts timbre/default-timestamp-opts ; {:pattern _ :locale _ :timezone _}

   :output-fn timbre/default-output-fn ; (fn [data]) -> string

   :appenders {:spit (appenders/spit-appender {:fname "/var/log/oc/oc-email.log"})}})