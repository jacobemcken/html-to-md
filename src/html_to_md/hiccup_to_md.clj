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
        (if (#{:span :div :small :body :nav :article :time :li :footer :section} tag)
          ::phrasing-content
          tag)))))

(defmulti convert
  (fn [element]
    (or (identify-tag-handler element)
        (throw (ex-info "Unable to parse element" {:element element})))))

(defn get-text
  [element]
  (some->> (child-elements element)
           (map convert)
           (str/join "")))

(defn some-wrap
  "Takes an element and calculates its text representation.
   If it has a text the function f is applied (usually for wrapping text)."
  [element f]
  (when-let [text (get-text element)]
    (f text)))

(defmethod convert :a
  [[_tag attr & _elements :as element]]
  (when-let [link-text (get-text element)]
    (if (map? attr)
      (str "[" link-text "]"
           "(" (:href attr) ")")
      link-text)))

(defmethod convert :img
  [[_tag attr & _elements :as _element]]
  (when (map? attr)
    (str "![" (:alt attr) "]"
         "(" (:src attr) ")")))

(defmethod convert :ol
  [element]
  (->> (child-elements element)
       (remove string?)
       (map (fn [idx li-element]
              (str idx ". " (convert li-element))) (drop 1 (range)))
       (str/join "\n")))

(defmethod convert :ul
  [element]
  (->> (child-elements element)
       (keep #(when-not (string? %)
                (str "- " (convert %))))
       (str/join "\n")))

(defmethod convert ::phrasing-content
  [element]
  (->> (child-elements element)
       (map convert)
       (str/join "")))

(defmethod convert :code
  [element]
  (some-wrap element #(str "`" % "`")))

(defmethod convert :pre
  [element]
  (some-wrap element #(str "```\n" % "\n```")))

(defmethod convert :blockquote
  [element]
  (some-wrap element #(->> (str/split % #"\n")
                           (map str/trim)
                           (str/join "\n> ")
                           (str "\n\n> "))))

(defmethod convert :abbr
  [[_tag attr & _elements :as element]]
  (when-let [abbr-text (get-text element)]
    (str abbr-text (when (map? attr) (str " (" (:title attr) ")")))))

(defmethod convert :br
  [_element]
  "\n")

(defmethod convert :hr
  [_element]
  "\n\n---\n\n")

(defmethod convert :em
  [element]
  (some-wrap element #(str "_" % "_")))

(defmethod convert :strong
  [element]
  (some-wrap element #(str "**" % "**")))

(defmethod convert :p
  [element]
  (some-wrap element #(str % "\n\n")))

(defmethod convert :h1
  [element]
  (some-wrap element #(str "# " % "\n\n")))

(defmethod convert :h2
  [element]
  (some-wrap element #(str "## " % "\n\n")))

(defmethod convert :h3
  [element]
  (some-wrap element #(str "### " % "\n\n")))

(defmethod convert :h4
  [element]
  (some-wrap element #(str "#### " % "\n\n")))

(defmethod convert :h5
  [element]
  (some-wrap element #(str "##### " % "\n\n")))

(defmethod convert :h6
  [element]
  (some-wrap element #(str "###### " % "\n\n")))

(defmethod convert ::text
  [text-element]
  text-element)
