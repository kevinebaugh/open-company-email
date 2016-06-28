;; Based on https://github.com/yeller/yeller-timbre-appender/blob/master/src/yeller/timbre_appender.clj
(ns oc.lib.sentry-appender
  (:require [raven-clj.core :as sentry]
            [raven-clj.interfaces :as sentry-interfaces]))

(defn extract-ex-data [throwable]
  (if-let [data (ex-data throwable)]
    {:ex-data data}
    {}))

(defn extract-arg-data [raw-args]
  (if-let [m (first (filter map? raw-args))]
    m
    {}))

(defn extract-data [throwable raw-args]
  (let [arg-data (extract-arg-data raw-args)
        ex-data (extract-ex-data throwable)]
    (merge
      arg-data
      {:custom-data (merge ex-data (:custom-data arg-data {}))})))

(defn sentry-appender
  "Create a Sentry timbre appender.
   (make-sentry-appender \"YOUR SENTRY DSN\")"
  [dsn]
  (assert dsn "sentry-appender requires a dsn")
  (merge
   {:doc "A timbre appender that sends errors to getsentry.com"
    :min-level :error
    :enabled? true
    :async? true
    :rate-limit nil
    :fn (fn [args]
          (let [throwable @(:?err_ args)
                data      (extract-data throwable @(:vargs_ args))]
            (when throwable
              (sentry/capture
               dsn
               (-> {:message (.getMessage throwable)}
                   (assoc-in [:extra :exception-data] data)
                   (sentry-interfaces/stacktrace throwable))))))}))