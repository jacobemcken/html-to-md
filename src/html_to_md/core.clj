(ns html-to-md.core
  (:require [hickory.core :as hickory]
            [html-to-md.hiccup-to-md :as hiccup-to-md]))

(defn get-hiccup-body
  "Takes a HTML string, converts it to Hiccup and return the `:body` element.
   Stripping the `:html` and `:head` tags."
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
  (hiccup-to-md/as-markdown (get-hiccup-body html)))
