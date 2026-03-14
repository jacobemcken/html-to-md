(ns html-to-md.debug
  "Helper functions for debugging."
  (:require [clojure.walk :as walk]))

(defn no-infinity
  [elements]
  (->> elements
       (walk/prewalk (fn [e]
                       (if (seq? e)
                         (take 2 e)
                         e)))))

(defn simplify-unfold
  [f]
  (fn unfoldz
    [prefix-seq spacing all-elements]
    (->> all-elements
         (f prefix-seq spacing)
         (map #(update % :prefix no-infinity))
         (map #(dissoc % :left :right)))))