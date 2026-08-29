(ns html-to-md.debug
  "Helper functions for debugging."
  (:require [clojure.string :as str]
            [clojure.walk :as walk]))

(defn no-infinity
  "Function to help inspect otherwise infinite sequences."
  [elements]
  (->> elements
       (walk/prewalk (fn [e]
                       (if (seq? e)
                         (take 2 e)
                         e)))))

(defn truncate-prefixes
  "Truncates prefixes to the number of text lines
   plus `arrange` consumes when it renders blank lines via `skip-first`,
   so multi-line text keeps every line (including blanks),
   while infinite prefix-seqs stay finite."
  [line-element]
  (let [text-lines (str/split (or (:text line-element) "") #"\n")]
    (update line-element :prefix (fn [prefix-seqs]
                                   (mapv #(take (inc (count text-lines)) %) prefix-seqs)))))

(defn simplify-unfold
  [f]
  (fn unfoldz
    [prefix-seq spacing all-elements]
    (->> all-elements
         (f prefix-seq spacing)
         (map truncate-prefixes)
         (map #(dissoc % :left :right)))))
