(ns open-company-email.content
  (:require [hiccup.core :as h]
            [clj-time.format :as f]
            [clojure.walk :refer (keywordize-keys)]))

(def pretty-date (f/formatter "MMMM dd, yyyy"))

(defn- logo-name [snapshot]
  [:table {:class "row header"}
    [:tbody
      [:tr
        [:th {:class "small-6 large-6 first columns"}
          [:img {:class "float-right logo"
                 :style "background-color: #ffffff;border: solid 1px rgba(78, 90, 107, 0.2);"
                 :height "50"
                 :width "50"
                 :src (:logo snapshot)}]]
        [:th {:class "small-6 large-6 last columns"}
          [:h4 {:class "name"} (:name snapshot)]]]]])

(defn- title [snapshot]
  [:table {:class "row header"}
    [:tbody
      [:tr
        [:th {:class "small-12 large-12 first last columns"}
          [:p {:class "text-center title"} (:title snapshot)]]]]])

(defn- message [snapshot]
  [:table {:class "row header"}
    [:tbody
      [:tr
        [:th {:class "small-12 large-12 first last columns content"}
          "Hi all, here’s the latest info. Recruiting efforts paid off! Retention is down though, we’ll fix it. Let me know if you want to discuss before we meet next week."]]]])

(defn- attribution [snapshot]
  (let [author (get-in snapshot [:author :name])
        date (f/unparse pretty-date (f/parse (:created-at snapshot)))]
    [:table {:class "row header"}
      [:tbody
        [:tr
          [:th {:class "small-12 large-12 first last columns"}
            [:p {:class "text-center content"} (str "— " author " / " date)]]]]]))

(defn- spacer
  ([pixels] (spacer pixels ""))
  ([pixels class]
  [:table {:class (str "row " class)}
    [:tbody
      [:tr
        [:th {:class "small-12 large-12 columns first last"}
          [:table
            [:tbody
              [:tr
                [:th
                  [:table {:class "spacer"}
                    [:tbody
                      [:tr
                        [:td {:height (str pixels "px")
                              :style (str "font-size:" pixels "px;line-height:" pixels "px;")} " "]]]]]
                [:th {:class "expander"}]]]]]]]]))

(defn- topic [snapshot]
  [:table {:class "row topic"}
    [:tbody
      [:tr
        [:th {:class "small-12 large-12 columns first last"}
          (spacer 24)
          [:p {:class "topic-title"} "TEAM"]
          (spacer 1)
          [:p {:class "topic-headline"} "We reached 50 team members mark in September! 🙌"]
          (spacer 2)]]
      [:tr
        [:th {:class "small-12 large-12 columns first last topic"}
          [:p "This feels like a big milestone and a special moment to reflect on."]
          (spacer 20)]]
      [:tr
        [:th {:class "small-12 large-12 columns first last"}
          [:a {:class "topic-read-more", :href "http://cnn.com/"} "READ MORE"]
          (spacer 30)
          [:th {:class "expander"}]]]]])

(defn- content
  [snapshot]
  [:td
    (spacer 52 "header")
    (logo-name snapshot)
    (spacer 37 "header")
    (title snapshot)
    (spacer 23 "header")
    (message snapshot)
    (spacer 16 "header")
    (attribution snapshot)
    (spacer 49 "header")
    (topic snapshot)
    (spacer 25 "header")
    (topic snapshot)])

(defn- body
  [snapshot]
  [:body
    [:table {:class "body", :snapshot-made-with-foundation ""}
      [:tbody
        [:tr
          [:td {:class "center", :align "center", :valign "top"}
            [:center
              [:table {:class "container"}
                [:tbody
                  [:tr
                    (content snapshot)]]]]]]]]])

(defn- head
  [snapshot]
  [:html {:xmlns "http://www.w3.org/1999/xhtml"} 
    [:head 
      [:meta {:http-equiv "Content-Type", :content "text/html; charset=utf-8"}]
      [:meta {:name "viewport", :content "width=device-width"}]
      [:link {:rel "stylesheet", :href "css/foundation.css"}]
      [:link {:rel "stylesheet", :href "css/opencompany.css"}]
      [:link {:href "http://fonts.googleapis.com/css?family=Domine", :rel "stylesheet", :type "text/css"}]
      [:link {:href "http://fonts.googleapis.com/css?family=Open+Sans", :rel "stylesheet", :type "text/css"}]
      (body snapshot)]])

(defn html
  [snapshot]
  (str
    "<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Strict//EN\" \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd\">"
    (h/html (head (keywordize-keys snapshot)))))

(comment

  ;; Recreate hiccup from various HTML fragments

  (def data (slurp "./resources/head.html"))
  (-> (hickory/parse data) hickory/as-hiccup first)

  (def data (slurp "./resources/body.html"))
  (-> (hickory/parse data) hickory/as-hiccup first (nth 3))

  (def data (slurp "./resources/spacer.html"))
  (-> (hickory/parse data) hickory/as-hiccup first (nth 3) (nth 2))

  (def data (slurp "./resources/logo-name.html"))
  (-> (hickory/parse data) hickory/as-hiccup first (nth 3) (nth 2))

  (def data (slurp "./resources/title.html"))
  (-> (hickory/parse data) hickory/as-hiccup first (nth 3) (nth 2))

  (def data (slurp "./resources/message.html"))
  (-> (hickory/parse data) hickory/as-hiccup first (nth 3) (nth 2))

  (def data (slurp "./resources/attribution.html"))
  (-> (hickory/parse data) hickory/as-hiccup first (nth 3) (nth 2))

  (def data (slurp "./resources/topic.html"))
  (-> (hickory/parse data) hickory/as-hiccup first (nth 3) (nth 2))

  (require '[open-company-email.content :as email] :reload)
  (spit "./resources/hiccup.html" (email/html {}))

  ;; Generate test email HTML content from various snapshots

  (require '[open-company-email.content :as email] :reload)
  (def snapshot (json/decode (slurp "./resources/snapshots/buffer.json")))
  (spit "./resources/hiccup.html" (email/html snapshot))

  (def snapshot (json/decode (slurp "./resources/snapshots/open.json")))
  (spit "./resources/hiccup.html" (email/html snapshot))

  )