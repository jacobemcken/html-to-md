(ns html-to-md.core
  (:require [hickory.core :as hickory]
            [html-to-md.hiccup-to-md :as hiccup-to-md]))

(defn get-hiccup-body
  [html]
  (->> html
       hickory/parse
       hickory/as-hiccup
       first
       hiccup-to-md/child-elements
       (filter #(= :body (first %)))
       first))

(defn convert
  [html]
  (-> html
      get-hiccup-body
      hiccup-to-md/convert))
