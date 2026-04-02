(ns html-to-md.hiccup-to-md
  "This namespace works on hiccup data structures.

   Got differenciated margins working. Started to implement inline formatting like links and code.
   
   Code inside a link doesn't work!!!!!! "
  (:require [clojure.string :as str]
            [html-to-md.debug :refer [no-infinity simplify-unfold]]))

(defn block?
  [element]
  (= :block (first element)))

(defn child-elements
  "Since hiccup attr(ibutes) are optional sometimes the position actually
   contain a child node instead, that needs to be re-attached."
  [[_tag attr & elements :as _element]]
  (cond-> elements
    (not (some #(% attr) [nil? map?])) (conj attr))) ; re-attach element when `attr` isn't attributes

(defn join
  "Takes two `flow` elements and joins them into one single `flow` element.
   Supports `nil` to represent nothing instead of an `inline` element.

   Does not support weird elements like `{:text \"some text\"}`
   or `{:left 1 :right 0 :text nil}`"
  [element-a element-b]
  (if-not (and element-a element-b)
    (or element-a element-b)
    {:left (:left element-a)
     :right (if (empty? (:text element-b))
              (max (:left element-b) (:right element-b))
              (:right element-b))
     :text (->> (list (:text element-a) (:text element-b))
                (keep not-empty)
                (str/join (apply str (repeat (max (or (:right element-a) 0)
                                                  (or (:left element-b) 0)) " "))))}))

(defn splice
  "Simplifies the intermediate Markdown structure by 'joining' all inline content."
  [all-elements]
  (loop [elements all-elements
         return-elements [] ;result
         ;; maybe user a better word than previous, what about "buffer"
         previous-element nil] ; "peek" into the previous element allows joining inline elements
    ;(println "===== (new splice)") (prn (no-infinity elements)) (prn (no-infinity return-elements)) (prn (no-infinity previous-element)) (println "=====")
    (if-not (seq elements)
      (cond-> return-elements
        previous-element (conj previous-element))

      (let [current-element (first elements)]
        (if-not (or (block? previous-element)
                    (block? current-element))
          (recur (rest elements)
                 return-elements
                 (join previous-element current-element))
          (recur (rest elements)
                 (into [] (concat return-elements (some-> previous-element (cons []))))
                 current-element))))))

(defn identify-tag-handler
  [element]
  (if (string? element)
    ::text
    (when (vector? element)
      (let [tag (first element)]
        (if (#{:span :div :small :body :nav :article :time :footer :section} tag)
          ::phrasing-content
          tag)))))

(comment
  (ns-unmap *ns* 'convert-element))
(defmulti render-elements
  (fn identify-element [_ctx element]
    (let [dispatch-value (identify-tag-handler element)]
      ;(println "got        " (no-infinity element)) (println "dispatch to" dispatch-value)
      (or dispatch-value
          (throw (ex-info "Unable to parse element" {:element element}))))))

(defmethod render-elements ::phrasing-content
  [ctx element]
  (->> (child-elements element)
       ;(map #(do (println "children" %) %))
       (mapcat #(render-elements ctx %))))

(defn as-block
  [attr elements]
  (some->> elements
           (concat [:block attr])
           (into [])))

(defmethod render-elements :p
  [ctx element]
  (some->> (child-elements element)
           (mapcat #(render-elements ctx %))
           splice
           (as-block {:spacing ::always})
           list))

(defmethod render-elements :ul
  [ctx element]
  (some->> (child-elements element)
           (mapcat #(render-elements ctx %))
           splice
           (as-block {:spacing ::block})
           list))

(defmethod render-elements :li
  [ctx element]
  (some->> (child-elements element)
           (mapcat #(render-elements ctx %))
           splice
           (as-block {:spacing ::text
                      :prefix (concat ["-   "] (repeat "    "))})
           list))

(defmethod render-elements :h1
  [ctx element]
  (some->> (child-elements element)
           (mapcat #(render-elements ctx %))
           splice
           (as-block {:spacing ::always
                      :prefix (concat ["# "] (repeat ""))})
           list))

(defmethod render-elements :blockquote
  [ctx element]
  (some->> (child-elements element)
           (mapcat #(render-elements ctx %))
           splice
           (as-block {:spacing ::block
                      :prefix (repeat "> ")})
           list))

(defmethod render-elements :em
  [ctx element]
  (some->> (child-elements element)
           (mapcat #(render-elements ctx %))
           splice
           (map #(update % :text (fn [t] (str "_" t "_"))))
           (into [])))

(defn calc-inline-margins
  "Calculate margins of inline content."
  [^CharSequence s]
  (let [len (.length s)]
    (if (zero? len)
      {:left 0 :right 0}
      {:left (if (Character/isWhitespace (.charAt s 0)) 1 0)
       :right (if (Character/isWhitespace (.charAt s (dec len))) 1 0)})))

(defn as-flow
  [s]
  (let [s-optimized (str/replace s #"\n\s+" "\n")
        s-trimmed (not-empty (str/trim s-optimized))]
    (if s-trimmed
      (assoc (calc-inline-margins s-optimized) :text s-trimmed)
      {:left 1 :right 1 :text ""}))) ; [:span "a"] " " [:span "b"]
;; => {:left 0 :right 0 :text "a"} {:left 1 :right 1} :text ""} {:left 0 :right 0 :text "b"}

(defmethod render-elements ::text
  [_ctx text-element]
  (-> text-element
      as-flow
      (cons '())))

(defn combine-prefix-seq
  [current-prefix-seq extra-prefix-seq]
  (into [] (cond-> current-prefix-seq
             (seq? extra-prefix-seq)
             (conj extra-prefix-seq))))

(defn skip-first
  "Takes a vector of collections and skips the first element in ALL collections."
  [colls]
  (mapv rest colls))

(defn calc-margins
  "Takes element margins and possible inherited margins along with the position of the element."
  [spacing inherited-margins first? last?]
  ;(println "CALC" spacing inherited-margins first? last?)
  {:before (if first? (:before inherited-margins) spacing)
   :after  (if last?  (:after  inherited-margins) spacing)})

(defn unfold
  "Flatten the intermediate Markdown structure.
   The returned value only needs to have vertical spacing applied and prefixed every line.

   `prefix-seqs` MUST be a vector at all times to maintain correct indenting."
  [prefix-seqs inherited-margins all-elements]
  (loop [elements all-elements
         return-elements '()] ;result
    ;(println "\n===== (new)") (prn (no-infinity elements)) (prn (no-infinity return-elements)) (prn (no-infinity prefix-seqs)) (println "=====")
    (if-not (seq elements)
      return-elements

      (let [[_tag {:keys [spacing prefix] :as _attr} & children :as current-element] (first elements)
            rest-elements (rest elements)
            first-element? (= all-elements elements)
            margins (calc-margins spacing inherited-margins first-element? (empty? rest-elements))
            new-prefix-seq (if first-element? prefix-seqs (skip-first prefix-seqs))]
        (if (block? current-element)
          (let [what (unfold (combine-prefix-seq new-prefix-seq prefix)
                             margins
                             children)]
            (recur rest-elements
                   (concat return-elements what)))
          (recur rest-elements
                 (let [text-element {:text (:text current-element)
                                     :margins margins
                                     :prefix new-prefix-seq}]
                   (concat return-elements [text-element]))))))))

(def unfoldz (simplify-unfold unfold))

(defn render-text
  [prefix-seqs text]
  (->> (str/split text #"\n")
       (conj prefix-seqs)
       (apply map str)))

(defn arrange
  [all-elements] ; TODO markdown elements
  (loop [elements all-elements
         [margin-to-previous previous-prefix-seqs] [::text []]
         text-lines (list)]
    (if-not (seq elements)
      (->> text-lines
           (str/join "\n"))

      (let [{:keys [prefix text margins] :as _element} (first elements)]
        ;(println " -- spacing -- " previous-spacing spacing)
        (recur (rest elements)
               [(:after margins) prefix]
               (concat text-lines
                       (when (or (some #(= ::always %) (list margin-to-previous (:before margins)))
                                 (every? #(= ::block %) (list margin-to-previous (:before margins))))
                         ;; when applyin spacing always use the least nesting (lowest prefix sequence count)
                         (let [prefix-seqs (apply min-key count [previous-prefix-seqs prefix])]
                           (render-text (skip-first prefix-seqs) "")))
                       (render-text prefix text)))))))

(defn as-markdown
  [hiccup]
  (->> (render-elements {} hiccup)
       (unfoldz '() {:before ::block :after ::block})
       arrange))
