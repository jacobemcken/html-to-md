(ns html-to-md.hiccup-to-md
  "This namespace works on hiccup data structures."
  (:require [clojure.string :as str]))

(defn child-elements
  [[_tag attr & elements :as _element]]
  (cond-> elements
    (not (some #(% attr) [nil? map?])) (conj attr))) ; re-attach element when `attr` isn't attributes

(defn identify-tag-handler
  [element]
  (if (string? element)
    ::text
    (when (vector? element)
      (let [tag (first element)]
        (if (#{:span :div :small :body :nav :article :time :footer :section} tag)
          ::phrasing-content
          tag)))))

(defmulti convert-element
  (fn identify-element [_ctx element]
    (or (identify-tag-handler element)
        (throw (ex-info "Unable to parse element" {:element element})))))

(defn get-text
  [ctx element]
  (some->> (child-elements element)
           (map #(convert-element ctx %))
           (str/join "")))

(defn some-wrap
  "Takes an element and calculates its text representation.
   If it has a text the function f is applied (usually for wrapping text)."
  [ctx element f]
  (when-let [text (get-text ctx element)]
    (f text)))

(defmethod convert-element :a
  [ctx [_tag attr & _elements :as element]]
  (when-let [link-text (get-text ctx element)]
    (if (map? attr)
      (str "[" link-text "]"
           "(" (:href attr) ")")
      link-text)))

(defmethod convert-element :img
  [_ctx [_tag attr & _elements :as _element]]
  (when (map? attr)
    (str "![" (:alt attr) "]"
         "(" (:src attr) ")")))

(defmethod convert-element :li
  [ctx element]
  (let [{:keys [list-nesting render] :or {list-nesting 0}} ctx]
    (render list-nesting (get-text ctx element))))

(defn unorderen-list-item
  [nesting text]
  (str (apply str (take nesting (repeat "    "))) "- " text))

(defn orderen-list-item
  [no-atom nesting text]
  (str (apply str (take nesting (repeat "    ")))
       (swap! no-atom (fnil inc 0))
       ". " text))

(defmethod convert-element :ul
  [orig-ctx element]
  (let [ctx (-> orig-ctx
                (update :list-nesting (fnil inc -1))
                (assoc :render unorderen-list-item))]
    (->> (child-elements element)
         (keep #(when-not (string? %)
                  (convert-element ctx %)))
         (str/join "\n"))))

(defmethod convert-element :ol
  [orig-ctx element]
  (let [ctx (-> orig-ctx
                (update :list-nesting (fnil inc -1))
                (assoc :render (let [no-atom (atom 0)]
                                 (partial orderen-list-item no-atom))))]
    (->> (child-elements element)
         (keep #(when-not (string? %)
                  (convert-element ctx %)))
         (str/join "\n"))))

(defmethod convert-element ::phrasing-content
  [ctx element]
  (->> (child-elements element)
       (map #(convert-element ctx %))
       (str/join "")))

(defmethod convert-element :code
  [ctx element]
  (some-wrap ctx element #(str "`" % "`")))

(defmethod convert-element :pre
  [ctx element]
  (some-wrap ctx element #(str "```\n" % "\n```")))

(defmethod convert-element :blockquote
  [ctx element]
  (some-wrap ctx element #(->> (str/split % #"\n")
                               (map str/trim)
                               (str/join "\n> ")
                               (str "\n\n> "))))

(defmethod convert-element :abbr
  [ctx [_tag attr & _elements :as element]]
  (when-let [abbr-text (get-text ctx element)]
    (str abbr-text (when (map? attr) (str " (" (:title attr) ")")))))

(defmethod convert-element :br
  [_ctx _element]
  "\n")

(defmethod convert-element :hr
  [_ctx _element]
  "\n\n---\n\n")

(defmethod convert-element :em
  [ctx element]
  (some-wrap ctx element #(str "_" % "_")))

(defmethod convert-element :strong
  [ctx element]
  (some-wrap ctx element #(str "**" % "**")))

(defmethod convert-element :p
  [ctx element]
  (some-wrap ctx element #(str % "\n\n")))

(defmethod convert-element :h1
  [ctx element]
  (some-wrap ctx element #(str "# " % "\n\n")))

(defmethod convert-element :h2
  [ctx element]
  (some-wrap ctx element #(str "## " % "\n\n")))

(defmethod convert-element :h3
  [ctx element]
  (some-wrap ctx element #(str "### " % "\n\n")))

(defmethod convert-element :h4
  [ctx element]
  (some-wrap ctx element #(str "#### " % "\n\n")))

(defmethod convert-element :h5
  [ctx element]
  (some-wrap ctx element #(str "##### " % "\n\n")))

(defmethod convert-element :h6
  [ctx element]
  (some-wrap ctx element #(str "###### " % "\n\n")))

(defmethod convert-element ::text
  [_ctx text-element]
  text-element)
