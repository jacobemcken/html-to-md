(ns html-to-md.hiccup-to-md2
  "This namespace works on hiccup data structures.
   
   Version 2 is about skipping the intermediate nested data structute
   and go straight for the sequence with prefixes for rendering.
   
   There is some value to representing blocks as nested datastrcutures.
   All elements within the block share the prefix and top and bottom margin.
   Multiple inline flow elements on a single line only requires a single prefix.
   Also, the top and bottom margin only affects the first and last line of content (which could be the same line).
   
   All adjecent inline flow needs to be glued (spiced?) togehter for to easily skip the nesting.
   Because then a single flow element represents the content of an entire block."
  (:require [clojure.string :as str]))

(require '[clojure.walk :as walk])
(defn no-infinity
  [elements]
  (->> elements
       (walk/prewalk (fn [e]
                       (if (seq? e)
                         (take 4 e)
                         e)))))
(defn block?
  [element]
  (= :block (first element)))

(defn child-elements
  "Since hiccup attr(ibutes) are obtional sometimes they actually contain
   a child node instead, that needs to be re-attached."
  [[_tag attr & elements :as _element]]
  (cond-> elements
    (not (some #(% attr) [nil? map?])) (conj attr))) ; re-attach element when `attr` isn't attributes

(defn join
  "Takes two `flow` elements and joins them into one single `flow` element.
   Supports `nil` to represent nothing instead of an `inline` element.

   Does not support weird elements like `{:text \"some text\"}`
   or `{:left 1 :right 0 :text nil}` or `{:left 1 :right 0 :text \"\"}`" ;; TODO why not this last one containing empty string - needs example in test case
  [element-a element-b]
  (if-not (and element-a element-b)
    (or element-a element-b)
    {:left (:left element-a)
     :right (:right element-b)
     :text (str (:text element-a)
                (apply str (repeat (max (or (:right element-a) 0)
                                        (or (:left element-b) 0)) " "))
                (:text element-b))}))

(comment
  (join {:left 1 :right 1 :text "abaaaa"}
        {:left 0 :right 1 :text "ldjsf"})
  (join nil {:left 1 :right 1 :text "ldjsf"})
  (join nil {:left 1 :right 0 :text "ldjsf"})
  (join nil {:left 0 :right 1 :text "ldjsf"})
  (join {:left 0 :right 1 :text "ldjsf"} nil))

(defn splice
  "Simplifies the intermediate Markdown structure by 'joining' all inline content."
  [all-elements]
  (loop [elements all-elements
         return-elements [] ;result
         ;; maybe user a better word than previous, what about "buffer"
         previous-element nil] ; "peek" into the previous element allows joining inline elements
    (println "===== (new splice)") (prn (no-infinity elements)) (prn (no-infinity return-elements)) (prn (no-infinity previous-element)) (println "=====")
    (if-not (seq elements)
      (cond-> return-elements
        previous-element (conj previous-element))

      (let [current-element (first elements)]
        (if-not (or (block? previous-element)
                    (block? current-element))
          (do (println "inline") (recur (rest elements)
                                        return-elements
                                        (join previous-element current-element)))
          (do (println "found block") (recur (rest elements)
                                             (into [] (concat return-elements (some-> previous-element (cons []))))
                                             current-element)))))))
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
(defmulti convert-element
  (fn identify-element [_ctx element]
    (let [dispatch-value (identify-tag-handler element)]
      (println "got        " (no-infinity element)) (println "dispatch to" dispatch-value)
      (or dispatch-value
          (throw (ex-info "Unable to parse element" {:element element}))))))

(defmethod convert-element ::phrasing-content
  [ctx element]
  (->> (child-elements element)
       ;(map #(do (println "children" %) %))
       (mapcat #(convert-element ctx %))))

(defn as-block
  [attr elements]
  (some->> elements
           (concat [:block attr])
           (into [])))

(defmethod convert-element :p
  [ctx element]
  (some->> (child-elements element)
           (mapcat #(convert-element ctx %))
           splice
           (as-block {:spacing 2})
           list))

(defmethod convert-element :ul
  [ctx element]
  (some->> (child-elements element)
           (mapcat #(convert-element ctx %))
           splice
           (as-block {:spacing 2})
           list))

(defmethod convert-element :li
  [ctx element]
  (some->> (child-elements element)
           (mapcat #(convert-element ctx %))
           splice
           (as-block {:prefix (concat ["- "] (repeat "  ")) :spacing 1})
           list))

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

(defmethod convert-element ::text
  [_ctx text-element]
  (-> text-element
      as-flow
      (cons '())))


;; Should blocks contain text (already rendered) or should they contain raw `inline` elements

(comment
  (def a
    [:p [:span [:span "a"] " b"
         [:p "c" [:span " d"] " e"]
         [:span "f"]]])
  (convert-element {} a)

  (def b [:span "hej" " ho"])
  (def b2 [:span ""])
  (def b3 [:span "a" [:span "b"] " c"])
  (convert-element {} b3)

  (def b4 [:span "a" [:span "b"] [:p "c " [:span "d"]] " e"])
  (convert-element {} b4)

  (def b5 [:ul [:li "b"] [:li "c "]])
  (no-infinity (convert-element {} b5))
  (def b6 [:ul [:li [:p "a\nb"] [:p "c\nd"]]])
  (no-infinity (convert-element {} b6))
  (no-infinity (unfoldz (repeat nil) (convert-element {} b6)))
  (def lol (convert-element {} b5))
  
  (def b7 [:ul [:li [:p "a\nb"]] [:li [:p "e\nf"]]])
(no-infinity (unfoldz (repeat nil) (convert-element {} b7)))

  (def c [:span [:span "hej" " ho"] " lol"])

  (def d [:p [:span "hej" " ho"] " lol"])

  (def e
    [:p [:span "hej"] " ho"
     [:p "wazzup!"]
     [:span "ola"] " ho"])

  (def f [:p [:span "hej" " ho"] [:p "  "] " lol"])


  ;; 1. identify `inline` and `blocks`
  ;; 2. glue together

  (convert-element {} b)
  (convert-element {} b2)
  (convert-element {} c)

  (convert-element {} d)
  (convert-element {} e)
  (convert-element {} f)

  *e
  )

(defn combine-prefix-seq
  [current-prefix-seq extra-prefix-seq]
  (into [] (cond-> current-prefix-seq
             (seq? extra-prefix-seq)
             (conj extra-prefix-seq))))

(defn unfold
  "Flatten the intermediate Markdown structure.
   The returned value only needs to have vertical spacing applied and prefixed every line.
   
   `prefix-seqs` MUST be a vector at all times to maintain correct indenting."
  [prefix-seqs parent-spacing all-elements]
  (loop [elements all-elements
         return-elements []] ;result
    (println "\n===== (new)") (prn (no-infinity elements)) (prn (no-infinity return-elements)) (prn (no-infinity prefix-seqs))(println "=====")
    (if-not (seq elements)
      return-elements

      (let [first-iteration? (= all-elements elements)
            current-element (first elements)]
        (if (block? current-element)
          (let [[_ attr & children] current-element
                [spacing new-prefix-seq] (if first-iteration?
                                           [parent-spacing (combine-prefix-seq prefix-seqs (:prefix attr))]
                                           [(:spacing attr) (combine-prefix-seq (mapv rest prefix-seqs) (:prefix attr))])
                flow-elements (unfold new-prefix-seq spacing children)]
            (println "end block")
            (recur (rest elements)
                   (concat return-elements flow-elements)))
          (recur (rest elements)
                 (conj return-elements (assoc current-element
                                              :prefix prefix-seqs
                                              :spacing (when first-iteration? parent-spacing)))))))))

(comment
  ;; I need to POC lists within lists
  ;; li (list elements) are "special" blocks with only 1x newline
  ;; (instead of 2x which is normal for p, blockqoute and lists ol & ul)
  ;; after `convert-element` :inline will never be nested but only subsequent (with left/right margins)
  ;; can (and should ) block spacing be introduced during "convert element"?
  ;; inline content without any newlines is coupled to the spacing, because it contains an implicit newline
  ;; then maybe a block should only use a single newline as spacing (because 1 is implicit)
  ;; but then list elements (li) should have no spacing because 1 is always implicit?
  ;; w2 illustrates this problem
  ;; - Also, many nested blocks ul, li, p, blockqoute should only trigger a single "spacing" to the content before.
  ;;   Simplest example is a blockqoute with a p inside.
  ;; - A block (... with a block etc.) without inline content is NOTHING and should be ignored
  ;;   It might become a single space though?
  ;; I think I fixed the spacing... but the prefixing solution requires all nested text both inline and blocks
  ;; to be raw text with newlines applied - I think.

  (defn unfoldz
    [prefix-seq all-elements]
    (->> all-elements
         (unfold prefix-seq 2)
         (map #(update % :prefix no-infinity))
         (map #(dissoc % :left :right))))
  )

;; insert block spacings (newlines)

;; HTML to Markdown is converting a complex format to a simpler (less flexible) format
;; this strategies for simplifying content must be in place.
;; Text in Markdown only exist in a paragraph context (e.g. Markdown doesn't know about div and span tags)
;; A strategy could be to group all subsequent text outside paragraphs (p-tags) as a paragrap
;; so it becomes a paragraph and doesn't interfere with the actual paragraphs.

;; Create an inline and a nested data example of lists

(comment
  ;; Example 1 -simplest
  [:ul [:li "A\naa\naaa"] [:li "B\nb"]]

  [:block {:spacing 2}
   [:block {:spacing 1 :prefix '("-   " "    ")} "A\naa\naaa"]
   [:block {:spacing 1 :prefix '("-   " "    ")} "B\nb"]]

  [{:spacing 2 :prefix '("-   " "    ") :text "A\naa\naaa"}
   {:spacing 1 :prefix '("-   " "    ") :text "B\nb"}]

  ;; Example 2 -simplest
  [:ul
   [:li "A\na" [:ul
                [:li "B\nb"]
                [:li "C\nc"]]]
   [:li "D\nd"]]
  
    (def b8
      [:ul
       [:li "A\na" [:ul
                    [:li "B\nb"]
                    [:li "C\nc"]]]
       [:li "D\nd"]])
  (no-infinity (unfoldz [] (convert-element {} b8)))

  [:block {:spacing 2}
   [:block   {:spacing 1 :prefix '("-   " "    ")} "A\na"
    [:block  {:spacing 2}
     [:block {:spacing 1 :prefix '("    ","-   " "    ","    ")} "B\nb"]
     [:block {:spacing 1 :prefix '("    ","-   " "    ","    ")} "C\nc"]]
    [:block  {:spacing 1 :prefix '("-   " "    ")} "D\nd"]]]

  [{:spacing 2 :prefix '("-   " "    ") :text "A\na"}
   {:spacing 1 :prefix '("    ","-   " "    ","    ") :text "B\nb"}
   {:spacing 1 :prefix '("    ","-   " "    ","    ") :text "C\nc"}
   {:spacing 1 :prefix '("-   " "    ") :text "D\nd"}]
  )

(defn render-text
  [prefix-seqs text]
  (some->> (str/split text #"\n") ;; TODO is some->> necessary here - if so include test cases exposing it
           (apply map str prefix-seqs)))

(defn render-elements
  [all-elements]
  (loop [elements all-elements
         spacing-prefix (repeat nil)
         text-lines '()]
    (if-not (seq elements)
      (->> text-lines
           rest ; remove the initial spacing line caused by initial spacing-prefix
           (str/join "\n" ))

      (let [{:keys [prefix text spacing] :as _element} (first elements)]
        (recur (rest elements)
               (when (<= 2 spacing) prefix)
               (concat text-lines
                       (when spacing-prefix
                         (map str spacing-prefix '("\n")))
                       (render-text prefix text)))
        )
      )))

(conj [1 2 3] 4)
;; 2. dimensions of complexity
;; - Prefix related to nesting: lists within blockqoutes within lists
;; - Spacing
;;   - Horizontal between inline content
;;   - Vertical between block content