(ns html-to-md.hiccup-to-md1
  "This namespace works on hiccup data structures."
  (:require [clojure.string :as str]))

(defn inline?
  [element]
  (= :inline (first element)))

(defn block?
  [element]
  (= :block (first element)))

(defn child-elements
  [[_tag attr & elements :as _element]]
  (cond-> elements
    (not (some #(% attr) [nil? map?])) (conj attr))) ; re-attach element when `attr` isn't attributes

(defn identify-tag-handler
  [element]
  (if (string? element)
    ::text
    (when (vector? element)
      (println "got" element)
      (let [tag (first element)]
        (if (#{:span :div :small :body :nav :article :time :footer :section} tag)
          ::phrasing-content
          (do (println tag)
              tag))))))

(defmulti convert-element
  (fn identify-element [_ctx element]
    (or (identify-tag-handler element)
        (throw (ex-info "Unable to parse element" {:element element})))))

(defmethod convert-element ::phrasing-content
  [ctx element]
  (->> (child-elements element)
       (mapcat #(convert-element ctx %))))

(defn as-block
  [elements]
  (some-> elements
    (conj :block)
    (vec)))

(defmethod convert-element :p
  [ctx element]
  (some->> (child-elements element)
           (mapcat #(convert-element ctx %))
           as-block
           list))

(defn calc-inline-margins
  "Calculate margins of inline content."
  [^CharSequence s]
  (let [len (.length s)]
    (cond
      (zero? len) [0 0]
      :else [(if (Character/isWhitespace (.charAt s 0)) 1 0)
             (if (Character/isWhitespace (.charAt s (dec len))) 1 0)])))

(defn as-inline
  [s]
  (let [s-optimized (str/replace s #"\n\s+" "\n")
        s-trimmed (not-empty (str/trim s-optimized))]
    (if s-trimmed
      [:inline (calc-inline-margins s-optimized) s-trimmed]
      [:inline [1 1] ""]))) ; [:span "a"] " " [:span "b"] => [:inline [0 0] "a"] [:inline [1 1] ""] [:inline [0 0] "b"]

(defmethod convert-element ::text
  [_ctx text-element]
  (-> text-element
      as-inline
      (cons '())))

(defn join
  "Takes two `inline` elements and joins them into one single `inline` element.
   Supports `nil` to represent nothing instead of an `inline` element.

   Does not support weird elements like `[:inline [nil nil] \"some text\"]`
   or `[:inline [1 0] nil]` or `[:inline [1 0] \"\"]`"
  [[_ [c-left c-right] c-text :as _carry]
   [_ [i-left i-right] i-text :as _item]]
  [:inline
   [(or c-left i-left 0) (or i-right c-right 0)]
   (str c-text
        (when (every? not-empty (list c-text i-text))
          (apply str (repeat (max (or c-right 0)
                                  (or  i-left 0)) " ")))
        i-text)])


(defn splice
  "Simplifies the intermediate Markdown structure by 'joining' all inline content."
  [all-elements]
  (loop [elements all-elements
         return-elements [] ;result
         previous-element nil]
    (println "===== (new)") (prn elements) (prn return-elements) (prn previous-element) (println "=====")
    (if-not (seq elements)
      (cond-> return-elements
        previous-element (conj previous-element))

      (let [[_ & children :as current-element] (first elements)]
        (if (block? current-element)
          (let [optimized-elements (splice children)] ; TODO handle empty element(s) if possible (can they be skipped/ignored)
            (recur (rest elements)
                   (into [] (concat return-elements (some-> previous-element (cons []))))
                   (as-block optimized-elements)))

          (if (block? previous-element)
            (recur (rest elements)
                   (some-> return-elements
                           (conj previous-element))
                   current-element)

            (do
              (println "** no blocks")
              (recur (rest elements)
                     return-elements
                     (join previous-element current-element)))))))))
