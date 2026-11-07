(ns html-to-md.core
  (:require [hickory.core :as hickory]
            [html-to-md.hiccup-to-md :as hiccup-to-md]))

(defn ^:no-doc get-hiccup-body
  "Takes a HTML string, converts it to Hiccup and return the `:body` element.
   Stripping the `:html` and `:head` tags."
  [html]
  (->> html
       hickory/parse
       hickory/as-hiccup
       (filter #(= :html (first %)))
       first
       hiccup-to-md/child-elements
       (filter #(= :body (first %)))
       first))

(defn convert
  "Takes an HTML string which is then converted and returned as a markdown formatted string."
  [html-str]
  (hiccup-to-md/as-markdown (get-hiccup-body html-str)))

(defn -main
  "Main funtion used for `bbin` CLI integration.
   See `README.md` for installation guidance."
  [& args]
  (-> (slurp (first args))
      (convert)
      println)
  nil)
