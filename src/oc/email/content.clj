(ns oc.email.content
  (:require [clojure.string :as s]
            [clj-time.format :as f]
            [hiccup.core :as h]
            [clojure.walk :refer (keywordize-keys)]
            [oc.email.config :as config]
            [oc.lib.utils :as utils]))

(def monthly-period (f/formatter "YYYY-MM"))

(def monthly-date (f/formatter "MMM YYYY"))

(defn- logo [snapshot]
  [:table {:class "row header"} 
    [:tr
      [:th {:class "small-12 large-12 first last columns"} 
        [:table
          [:tr
            [:th
              [:center {:data-parsed ""}
                [:img {:class "float-center logo"
                       :align "center"
                       :style "background-color: #ffffff;border: solid 1px rgba(78, 90, 107, 0.2);"
                       :height "50px"
                       :width "50px"
                       :src (:logo snapshot)
                       :alt (str (:name snapshot) " logo")}]]]]]]]])

(defn- company-name [snapshot]
  [:table {:class "row header"}
    [:tr
      [:th {:class "small-12 large-12 first last columns"}
        [:p {:class "text-center company-name"} (:name snapshot)]]]])

(defn- title [snapshot]
  [:table {:class "row header"}
    [:tr
      [:th {:class "small-12 large-12 first last columns"}
        [:p {:class "text-center title"} (:title snapshot)]]]])

(defn- message [snapshot]
  [:table {:class "row note"}
    [:tr
      [:th {:class "small-12 large-12 first last columns note"}
        (:note snapshot)]]])

(defn- spacer
  ([pixels] (spacer pixels ""))
  ([pixels class-name]
  [:table {:class (str "row " class-name)}
    [:tr
      [:th {:class "small-12 large-12 first last columns"}
        [:table
          [:tr
            [:th
              [:table {:class "spacer"}
                [:tr
                  [:td {:height (str "font-size:" pixels "px")
                        :style (str "font-size:" pixels "px;line-height:" pixels "px;")} " "]]]]
            [:th {:class "expander"}]]]]]]))

(defn- topic-image [image-url]
  [:tr
    [:th
      [:img {:src image-url}]]])

(defn- content-topic [snapshot topic-name topic topic-url]
  (let [body? (not (s/blank? (:body topic)))
        image-url (:image-url topic)]
    [:table {:class "row topic"}
      (when image-url (topic-image image-url))
       [:tr
        [:th {:class "small-12 large-12 columns first last"}
          (spacer 24)
          [:p {:class "topic-title"} (s/upper-case (:title topic))]
          (spacer 1)
          [:p {:class "topic-headline"} (:headline topic)]
          (when body? (spacer 2))
          (when body? (:body topic))
          (spacer 20)]
        [:th {:class "expander"}]]]))

(defn- metric
  ([label value] (metric label value false))
  ([label value currency]
  (let [formatted-value (if (number? value)
                          (.format (java.text.NumberFormat/getInstance java.util.Locale/US) (int value))
                          value)
        final-value (if currency (str currency formatted-value) formatted-value)] ; TODO negative currency
                                                                                  ; TODO max 3 digits
    [:table {:class "metric"}
      [:tr
        [:td
          [:p {:class "metric"} final-value]
          [:p {:class "label"} label]]]])))

(defn- growth-metric [growth-metric metadata]
  (let [slug (:slug growth-metric)
        metadatum (first (filter #(= (:slug %) slug) metadata))
        interval (:interval metadatum)
        unit (:unit metadatum)
        metric-name (:name metadatum)
        currency (if (= unit "currency") "$" false) ; TODO other currencies
        period (f/parse monthly-period (:period growth-metric)) ; TODO weekly, quarterly
        date (s/upper-case (f/unparse monthly-date period)) ; TODO weekly, quarterly
        label (str metric-name " - " date)
        value (:value growth-metric)] ; TODO %
    (when (number? value)
      (metric label value currency))))

(defn growth-metrics [topic currency]
  (let [data (:data topic)
        metadata (:metrics topic)
        period (last (sort (set (map :period data)))) ; latest period
        metrics (filter #(= period (:period %)) data)] ; metrics for latest period
    [:table {:class "growth-metrics"}
      [:tr
        (into [:td]
          (map #(growth-metric % metadata) metrics))]]))

(defn calc-runway [cash burn-rate]
  (int (* (/ cash burn-rate) 30)))

(defn- finance-metrics [topic currency]
  (let [finances (last (sort-by :period (:data topic)))
        period (f/parse monthly-period (:period finances))
        date (s/upper-case (f/unparse monthly-date period))
        cash? (:cash finances)
        cash (:cash finances)
        revenue? (:revenue finances)
        revenue (:revenue finances)
        costs? (:costs finances)
        costs (:costs finances)
        cash-flow? (and revenue? costs?)
        cash-flow (- revenue costs)
        runway? (and cash? costs? (or (not revenue?) (> costs revenue)))
        runway (when runway? (calc-runway cash cash-flow))]
    [:table {:class "finances-metrics"}
      [:tr
        [:td
          (when cash? (metric (str "CASH - " date) cash "$")) ; TODO other currencies
          (when revenue? (metric (str "REVENUE - " date) revenue "$")) ; TODO other currencies
          (when costs? (metric (str "COSTS - " date) costs "$")) ; TODO other currencies
          (when cash-flow? (metric (str "CASH FLOW - " date) cash-flow "$")) ; TODO other currencies
          (when runway? (metric (str "RUNWAY - " date) (utils/get-rounded-runway runway)))]]]))

(defn- data-topic [snapshot topic-name topic topic-url]
  (let [currency (:currency snapshot)
        data? (empty? (:data snapshot))
        body? (s/blank? (:body topic))]
    [:table {:class "row topic"}
      [:tr
        [:th {:class "small-12 large-12 columns first last"}
          (spacer 24)
          [:p {:class "topic-title"} (s/upper-case (:title topic))]
          (spacer 1)
          [:p {:class "topic-headline"} (:headline topic)]
          (when data? (spacer 15))
          (when data?
            (if (= topic-name "finances")
              (finance-metrics topic currency)
              (growth-metrics topic currency)))
          (when body? (spacer 20))
          (when body? [:p (:body topic)])
          (spacer 20)
          [:a {:class "topic-read-more", :href topic-url} "SEE MORE"]
          (spacer 30)]
        [:th {:class "expander"}]]]))

(defn- topic [snapshot topic-name topic]
  (let [company-slug (:company-slug snapshot)
        snapshot-slug (:slug snapshot)
        topic-url (s/join "/" [config/web-url company-slug "updates" snapshot-slug topic-name])]
    (if (:data topic)
      (data-topic snapshot topic-name topic topic-url)
      (content-topic snapshot topic-name topic topic-url))))

(defn- content [snapshot]
  (let [logo? (not (s/blank? (:logo snapshot)))
        title? (not (s/blank? (:title snapshot)))]
    [:td
      (spacer 30 "header")
      (company-name snapshot)
      (when title? (spacer 10 "header"))
      (when title? (title snapshot))
      (spacer 22 "header")
      [:table
        [:tr
          (into [:td] 
            (interleave
              (map #(topic snapshot % (snapshot (keyword %))) (:sections snapshot))
              (repeat (spacer 15 "header"))))]]]))

(defn- note [snapshot]
  [:table {:class "note"}
    [:tr
      [:td
        (spacer 20 "note")
        (message snapshot)
        (spacer 22 "note")]]])

(defn- footer []
  [:table {:class "footer"}
    [:tr
      [:td
        [:center
          [:table {:class "container"}
            [:tr
              [:td
                (spacer 31 "footer")
                [:table {:class "row footer"}
                  [:tr
                    [:th {:class "small-1 large-2 first columns"}]
                    [:th {:class "small-11 large-8 columns"} 
                      [:p {:class "text-center"}
                        "Powered by "
                        [:a {:href "http://opencompany.com/"} "OpenCompany"]]]
                    [:th {:class "small-1 large-2 last columns"}]]]
                (spacer 28 "footer")]]]]]]])

(defn- body [snapshot]
  [:body
    [:table {:class "body"}
      [:tr
        [:td {:class "float-center", :align "center", :valign "top"}
          (when-not (s/blank? (:note snapshot)) (note snapshot))
          [:center
            [:table {:class "container"}
              [:tr
                (content snapshot)]]]
          (footer)]]]])

(defn- head [snapshot]
  [:html {:xmlns "http://www.w3.org/1999/xhtml"} 
    [:head 
      [:meta {:http-equiv "Content-Type", :content "text/html; charset=utf-8"}]
      [:meta {:name "viewport", :content "width=device-width"}]
      [:title (str (:name snapshot) " Update")]
      [:link {:rel "stylesheet", :href "resources/css/foundation.css"}]
      [:link {:rel "stylesheet", :href "resources/css/opencompany.css"}]
      [:link {:href "http://fonts.googleapis.com/css?family=Domine", :rel "stylesheet", :type "text/css"}]
      [:link {:href "http://fonts.googleapis.com/css?family=Open+Sans", :rel "stylesheet", :type "text/css"}]]
    (body snapshot)])

(defn html [snapshot]
  (str
    "<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Transitional//EN\" \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd\">"
    (h/html (head (keywordize-keys snapshot)))))

(comment
  
  ;; For REPL testing

  (require '[oc.email.content :as content] :reload)

  ;; Recreate hiccup from various HTML fragments

  (defn clean-html [data]
    (-> data
      (s/replace "  " "")
      (s/replace "\n" "")
      (s/replace "\t" "")))

  (def data (clean-html (slurp "./resources/head.html")))
  (-> (hickory/parse data) hickory/as-hiccup first)

  (def data (clean-html (slurp "./resources/body.html")))
  (-> (hickory/parse data) hickory/as-hiccup first (nth 3))

  (def data (clean-html (slurp "./resources/spacer.html")))
  (-> (hickory/parse data) hickory/as-hiccup first (nth 3) (nth 2))

  (def data (clean-html (slurp "./resources/note.html")))
  (-> (hickory/parse data) hickory/as-hiccup first (nth 3) (nth 2))

  (def data (clean-htnml (slurp "./resources/logo.html")))
  (-> (hickory/parse data) hickory/as-hiccup first (nth 3) (nth 2))

  (def data (clean-html (slurp "./resources/name.html")))
  (-> (hickory/parse data) hickory/as-hiccup first (nth 3) (nth 2))

  (def data (clean-html (slurp "./resources/title.html")))
  (-> (hickory/parse data) hickory/as-hiccup first (nth 3) (nth 2))

  (def data (clean-html (slurp "./resources/topic.html")))
  (-> (hickory/parse data) hickory/as-hiccup first (nth 3) (nth 2))

  (def data (clean-html (slurp "./resources/data-topic.html")))
  (-> (hickory/parse data) hickory/as-hiccup first (nth 3) (nth 2))

  (def data (clean-html (slurp "./resources/footer.html")))
  (-> (hickory/parse data) hickory/as-hiccup first (nth 3) (nth 2))

  (spit "./hiccup.html" (email/html {}))

  ;; Generate test email HTML content from various snapshots

  (def note "Hi all, here’s the latest info. Recruiting efforts paid off! Retention is down though, we’ll fix it. Let me know if you want to discuss before we meet next week.")
  (def snapshot (json/decode (slurp "./opt/samples/green-labs.json")))
  (spit "./hiccup.html" (content/html (-> snapshot (assoc :note note) (assoc :company-slug "green-labs"))))

  (def note "Hi all, here’s the latest info. Recruiting efforts paid off! Retention is down though, we’ll fix it. Let me know if you want to discuss before we meet next week.")
  (def snapshot (json/decode (slurp "./opt/samples/buff.json")))
  (spit "./hiccup.html" (content/html (-> snapshot (assoc :note note) (assoc :company-slug "buff"))))

  )