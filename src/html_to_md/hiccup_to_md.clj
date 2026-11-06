(ns html-to-md.hiccup-to-md
  "This namespace works on hiccup data structures.

   There is a lot of infinite sequences, that can make debugging a bit annoying.
   Consider using html-to-md.debug/no-infinity to reduce output.

   Got differenciated margins working."
  (:require [clojure.string :as str]
            [hickory.render :as hickory-render]
            [html-to-md.debug :refer [simplify-unfold]]))

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
                                                  (or (:left element-b) 0)) " ")))
                not-empty)}))

(defn has-content?
  "Whether an element has relevant content for the output.

   Relies on correct trimming of elements.
   Most goes through `as-flow` but tags like `:pre` should preserve whitespace."
  [element]
  (or (block? element) (:text element)))

(defn splice
  "Simplifies the intermediate Markdown structure by 'joining' all inline content.
   Empty elements not relevant for spacing are dropped."
  [all-elements]
  (loop [elements all-elements
         return-elements [] ;result
         ;; maybe user a better word than previous, what about "buffer"
         previous-element nil] ; "peek" into the previous element allows joining inline elements
    ;(println "===== (new splice)") (prn (no-infinity elements)) (prn (no-infinity return-elements)) (prn (no-infinity previous-element)) (println "=====")
    (if-not (seq elements)
      (cond-> return-elements
        (has-content? previous-element) (conj previous-element))

      (let [current-element (first elements)]
        (if-not (or (block? previous-element)
                    (block? current-element))
          (recur (rest elements)
                 return-elements
                 (join previous-element current-element))
          (recur (rest elements)
                 (cond-> return-elements
                   (has-content? previous-element)
                   (conj previous-element))
                 current-element))))))

(defn identify-tag-handler
  [ctx element]
  (let [tag (and (vector? element) (first element))] ; HTML-tag name (if element isn't text)
    (cond
      (::fn ctx) ::fn
      (string? element) ::text
      (#{:article :aside :body :center :footer :header :ins :main :nav :section :small :span :sub :sup :time} tag) ::phrasing-content
      (#{:abbr :acronym} tag) :abbr
      (#{:b :big :mark :strong} tag) :strong
      (#{:s :del} tag) :s
      (#{:canvas :iframe :link :script :style :svg :template} tag) ::ignore
      ;; The following tags are still undecided or not implemented
      (#{:figure :button :label :summary :details :meta :dl :dt :dd} tag) ::phrasing-content
      (#{:h1 :h2 :h3 :h4 :h5 :h6} tag) ::heading
      (#{:p :div} tag) :p
      (#{:code :kbd} tag) :code
      :else tag)))

(comment
  (ns-unmap *ns* 'render-elements)
  (require '[html-to-md.debug :refer [no-infinity]])
  )
(defmulti render-elements
  (fn identify-element [ctx element]
    (let [dispatch-value (identify-tag-handler ctx element)]
      ;(println "got        " (no-infinity element)) (println "dispatch to" dispatch-value)
      ;(println (some-> (:li _ctx) deref no-infinity))
      (or dispatch-value
          (throw (ex-info "Unable to parse element" {:element element}))))))

(defmethod render-elements ::ignore
  [_ctx _element]
  nil)

(defmethod render-elements :default
  [_ctx _element]
  nil)

(defmethod render-elements ::phrasing-content
  [ctx element]
  (->> (child-elements element)
       ;(map #(do (println "children" %) %))
       (mapcat #(render-elements ctx %))
       ;; splice again after unpacking to support nested formatting like emphasised code
       splice))

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

(defmethod render-elements :table
  [_ctx element]
  ;; Render the table back to raw HTML and emit it as a single inline block.
  [(as-block {:spacing ::always}
             [{:left 0 :right 0
               :text (hickory-render/hiccup-to-html [element])}])])

(def unordered-list-prefixes
  (repeat (concat ["-   "] (repeat "    "))))

(def ordered-list-prefixes
  (->> (range)
       (map #(concat [(str % ".  ")] (repeat "    ")))))

(defmethod render-elements :ol
  [ctx element]
  (let [new-ctx (assoc ctx :li (atom ordered-list-prefixes))]
    (some->> (child-elements element)
             (mapcat #(render-elements new-ctx %))
             splice
             (as-block {:spacing ::block})
             list)))

(defmethod render-elements :ul
  [ctx element]
  (let [new-ctx (assoc ctx :li (atom unordered-list-prefixes))]
    (some->> (child-elements element)
             (mapcat #(render-elements new-ctx %))
             splice
             (as-block {:spacing ::block})
             list)))

(defmethod render-elements :li
  [ctx element]
  (some->> (child-elements element)
           (mapcat #(render-elements ctx %))
           splice
           (as-block {:spacing ::text
                      :prefix (first (swap! (:li ctx) rest))})
           list))

(def heading-levels
  {:h1 1 :h2 2 :h3 3 :h4 4 :h5 5 :h6 6})

(defmethod render-elements ::heading
  [ctx element]
  (let [level (heading-levels (first element))]
    (some->> (child-elements element)
             (mapcat #(render-elements ctx %))
             splice
             (as-block {:spacing ::always
                        :prefix (concat [(apply str (concat (repeat level "#") [" "]))] (repeat ""))})
             list)))

(defmethod render-elements :hr
  [_ctx _element]
  [(as-block {:spacing ::always}
             [{:left 0 :right 0 :text "---"}])])

;; This is used for an element to temparily override default behavior
;; E.g. inside pre tags where tags behave differently
(defmethod render-elements ::fn
  [ctx element]
  ((::fn ctx) ctx element))

;; Permitted content: Phrasing content
;; Source: https://developer.mozilla.org/en-US/docs/Web/HTML/Guides/Content_categories#phrasing_content
(defmethod render-elements :pre
  [ctx element]
  (let [new-ctx (assoc ctx ::fn (fn special-render-for-pre
                                  [ctx element]
                                  (if (string? element)
                                    (list {:left 0 :right 0 :text element})
                                    (->> (child-elements element)
                                         (mapcat #(render-elements ctx %))
                                         splice))))
        raw-text (->> (child-elements element)
                      (mapcat #(render-elements new-ctx %))
                      splice
                      first
                      :text)]
    (when (re-find #"\S" raw-text) ; skip preformatted text that is entirely whitespace
      (->> (as-block {:spacing ::block}
                     [{:left 0 :right 0
                       :text (str "```\n" raw-text "\n```")}])
           list))))

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
           (mapv #(update % :text (fn [t] (str "_" t "_"))))))

(defmethod render-elements :abbr
  [ctx element]
  (let [title (:title (second element))]
    (some->> (child-elements element)
             (mapcat #(render-elements ctx %))
             splice
             (mapv #(update % :text (fn [t] (str t (when title (str " (" title ")")))))))))

(defmethod render-elements :s
  [ctx element]
  (some->> (child-elements element)
           (mapcat #(render-elements ctx %))
           splice
           (mapv #(update % :text (fn [t] (str "~~" t "~~"))))))

(defmethod render-elements :strong
  [ctx element]
  (some->> (child-elements element)
           (mapcat #(render-elements ctx %))
           splice
           (mapv #(update % :text (fn [t] (str "**" t "**"))))))

(defmethod render-elements :a
  ;; Renders a link as `[text](href)`.
  ;; Newlines in the link text are replaced by spaces,
  ;; as a Markdown link must fit on a single line.
  ;; A link without an `:href` attribute is rendered as plain text.
  [ctx element]
  (let [href (when (map? (second element)) (:href (second element)))]
    (some->> (child-elements element)
             (mapcat #(render-elements ctx %))
             splice
             (mapv #(update % :text
                            (fn [t]
                              (if href
                                (str "[" (str/replace t #"\s*\n\s*" " ") "](" href ")")
                                t)))))))

(defmethod render-elements :code
  ;; Wraps code in backticks but avoids continuesly wrapping nested code.
  [ctx element]
  (let [new-ctx (assoc ctx ::in-code true)]
    (some->> (child-elements element)
             (mapcat #(render-elements new-ctx %))
             splice
             (mapv #(update % :text
                            (fn [t]
                              (let [sanitized-t (str/replace t #"\s*\n\s*" " ")]
                                (if (::in-code ctx)
                                  sanitized-t
                                  (str "`" sanitized-t "`")))))))))

(defmethod render-elements :br
  [_ctx _element]
  [{:left 0 :right 0 :text "  \n"}])

(defmethod render-elements :img
  [_ctx element]
  (let [{:keys [alt src]} (second element)]
    [{:left 0 :right 0 :text (str "!" "[" (or alt "") "](" src ")")}]))

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
      {:left 1 :right 1 :text nil}))) ; [:span "a"] " " [:span "b"]
;; => {:left 0 :right 0 :text "a"} {:left 1 :right 1} :text nil} {:left 0 :right 0 :text "b"}

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

(defn decode-html-entities
  "Reverses escaping of HTML entities done during HTML->Hiccup."
  [s]
  (-> s
      (str/replace "&lt;" "<")
      (str/replace "&gt;" ">")
      (str/replace "&quot;" "\"")
      (str/replace "&amp;" "&")))

(defn render-text
  [prefix-seqs text]
  (->> (str/split (decode-html-entities (or text "")) #"\n")
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
       ;; `unfoldz` is a debug version of `unfold`,
       ;; truncating infinite prefix sequences to ease local debugging in REPL.
       (unfoldz '() {:before ::block :after ::block})
       arrange))
