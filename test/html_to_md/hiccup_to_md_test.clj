(ns html-to-md.hiccup-to-md-test
  (:require
   [clojure.string :as str]
   [clojure.test :refer [are deftest is testing]]
   [html-to-md.hiccup-to-md :as sut]))

(deftest splice
  (testing "Splice inline (text) elements"
    (are [expected-element inline-elements]
         (= expected-element
            (sut/splice inline-elements))

      '({:text "A" :left 0 :right 0}) '({:text "A" :left 0 :right 0} nil)
      '({:text "A" :left 0 :right 0}) '(nil {:text "A" :left 0 :right 0})

      '({:text "A" :left 0 :right 1}) '({:text "A" :left 0 :right 0}
                                        {:text ""  :left 0 :right 1})
      '({:text "A" :left 0 :right 1}) '({:text "A" :left 0 :right 0}
                                        {:text ""  :left 1 :right 0})
      '({:text "A" :left 0 :right 1}) '({:text "A" :left 0 :right 0}
                                        {:text ""  :left 1 :right 1})

      '({:text "AB" :left 0 :right 0})  '({:text "A" :left 0 :right 0}
                                          {:text "B" :left 0 :right 0})
      '({:text "AB" :left 1 :right 0})  '({:text "A" :left 1 :right 0}
                                          {:text "B" :left 0 :right 0})
      '({:text "AB" :left 0 :right 1})  '({:text "A" :left 0 :right 0}
                                          {:text "B" :left 0 :right 1})
      '({:text "AB" :left 1 :right 1})  '({:text "A" :left 1 :right 0}
                                          {:text "B" :left 0 :right 1})

      '({:text "A B" :left 1 :right 1}) '({:text "A" :left 1 :right 1}
                                          {:text "B" :left 0 :right 1})
      '({:text "A B" :left 1 :right 1}) '({:text "A" :left 1 :right 0}
                                          {:text "B" :left 1 :right 1})
      '({:text "A B" :left 1 :right 1}) '({:text "A" :left 1 :right 1}
                                          {:text "B" :left 1 :right 1})

      '({:text "A B" :left 0 :right 0}) '({:text "A" :left 0 :right 1}
                                          {:text "B" :left 0 :right 0})
      '({:text "A B" :left 0 :right 0}) '({:text "A" :left 0 :right 0}
                                          {:text "B" :left 1 :right 0})
      '({:text "A B" :left 0 :right 0}) '({:text "A" :left 0 :right 1}
                                          {:text "B" :left 1 :right 0})

      ;; This will only ever be needed if trimming multiple whitespace is undesired
      '({:text "A  B" :left 2 :right 3}) '({:text "A" :left 2 :right 1}
                                           {:text "B" :left 2 :right 3}))))

(deftest lists
  (testing "Unordered lists"
    (are [markdown-lines hiccup]
         (= (str/join "\n" markdown-lines)
            (sut/as-markdown hiccup))

      ;; A simple list
      ["-   Banana"
       "-   Apple"
       "-   Orange"]
      [:ul ;"\n"
       [:li "Banana"]
       [:li "Apple"] ;"\n"
       [:li "Orange"]]

      ;; Nested lists
      ["-   Fruit"
       "    -   Kiwi"
       "    -   Melon"
       "    -   Pear"
       "-   Dairy"
       "    -   Milk"
       "    -   Cheese"]
      [:ul
       [:li "Fruit"
        [:ul
         [:li "Kiwi"]
         [:li "Melon"] ;"\n"
         [:li "Pear"]]]
       [:li "Dairy"
        [:ul
         [:li "Milk"]
         [:li "Cheese"]]]])))

(deftest ordered-lists
  (testing "Ordered lists"
    (are [markdown-lines hiccup]
         (= (str/join "\n" markdown-lines)
            (sut/as-markdown hiccup))

      ;; A simple ordered list (ported from the old test case in scratch.md)
      ["1.  Elephant"
       "2.  Tiger"]
      [:ol "\n   "
       [:li "Elephant"]
       "\n   "
       [:li "Tiger"]]

      ;; Ordered list nested in an unordered list
      ["-   Fruit"
       "    1.  Apple"
       "    2.  Banana"]
      [:ul
       [:li "Fruit"
        [:ol
         [:li "Apple"]
         [:li "Banana"]]]]

      ;; Unordered list nested in an ordered list
      ["1.  Fruit"
       "    -   Apple"
       "    -   Banana"
       "2.  Dairy"]
      [:ol
       [:li "Fruit"
        [:ul
         [:li "Apple"]
         [:li "Banana"]]]
       [:li "Dairy"]]

      ;; Complex nested list combination
      ["-   Fruit"
       "    1.  Banana"
       "    2.  Apple"
       "        1.  Discovery"
       "        2.  Elstar"
       "        3.  Baldwin"
       "        4.  Gala"
       "    3.  Kiwi"
       "-   Dairy"]
      [:ul
       [:li "Fruit"
        [:ol
         [:li "Banana"]
         [:li "Apple"
          [:ol
           [:li "Discovery"]
           [:li "Elstar"]
           [:li "Baldwin"]
           [:li "Gala"]]]
         [:li "Kiwi"]]]
       [:li "Dairy"]])))

(deftest multi-line-text
  (testing "Multi-line text keeps every line"
    (are [markdown-lines hiccup]
         (= markdown-lines
            (str/split (sut/as-markdown hiccup) #"\n"))

      ["-   line1"
       "    line2"
       "    line3"]
      [:ul [:li "line1\nline2\nline3"]])))

(deftest blockquotes
  (testing "Different usages of blockquotes"
    (are [markdown-lines hiccup]
         (= markdown-lines
            (str/split (sut/as-markdown hiccup) #"\n"))

      ;; A simple blockquote
      ["> Some text"]
      [:blockquote "Some text"]

      ;; A blockquote with multiple paragraphs
      ["> Some text"
       "> "
       "> Some other text"]
      [:blockquote [:p "Some text"] [:p "Some other text"]]

      ;; A blockquote with a list
      ["> Some text"
       "> "
       "> -   List element"
       "> -   Another list element"]
      [:blockquote
       [:p "Some text"]
       [:ul
        [:li "List element"]
        [:li "Another list element"]]]
      )))

(deftest combined
  (testing "A combination of nested block elements"
    (are [markdown-lines hiccup]
         (= markdown-lines
            (str/split (sut/as-markdown hiccup) #"\n"))

      ; Lists and headings inside a blockquote
      ["> # Some heading"
       "> "
       "> This is the important list:"
       "> -   Fruit"
       ">     -   Apple"
       ">     -   Banana"]
      [:blockquote
       [:h1 "Some heading"]
       "This is the important list:"
       [:ul
        [:li "Fruit"
         [:ul
          [:li "Apple"]
          [:li "Banana"]]]]]

      ; A blockquote inside a list
      ["-   > X"
       "    > "
       "    > Y"
       "-   Z"
       "    "
       "    W"]
      [:ul
       [:li [:blockquote [:p "X"] [:p "Y"]]]
       [:li [:p "Z"] [:p "W"]]])))

(deftest headings
  (testing "Headings render with the correct number of #"
    (are [markdown hiccup]
         (= markdown (sut/as-markdown hiccup))

      "# Level one"     [:body [:h1 "Level one"]]
      "## Level two"    [:body [:h2 "Level two"]]
      "### Level three" [:body [:h3 "Level three"]]
      "#### Level four" [:body [:h4 "Level four"]]
      "##### Level five" [:body [:h5 "Level five"]]
      "###### Level six" [:body [:h6 "Level six"]])))

(deftest inline-formatting
  (testing "Inline formatting"
    (are [expected-markdown-lines hiccup]
         (= (str/join "\n" expected-markdown-lines)
            (sut/as-markdown hiccup))

      ;; Simple italic text
      ["_italic_ **strong** _**both**_"]


      ;; Nested formated
      [:span [:em "italic"] " " [:strong "strong"] " " [:em [:strong "both"]]]

      ;; Emphasis in lists
      ["-   _italic_ item"
       "-   normal item"]
      [:ul
       [:li [:em "italic"] " item"]
       [:li "normal item"]]

      ;; Emphasis in blockquotes
      ["> _important_"]
      [:blockquote [:p [:em "important"]]]

      ["> _important_ list:"
       "> "
       "> -   _first_ item"
       "> -   _second_ item"]
      [:blockquote
       [:p [:em "important"] " list:"]
       [:ul
        [:li [:em "first"] " item"]
        [:li [:em "second"] " item"]]]

      ;; Emphasis in nested structures
      ["> # Some _Title_"
       "> "
       "> _Introduction_ text"]
      [:blockquote
       [:h1 "Some " [:em "Title"]]
       [:p [:em "Introduction"] " text"]]

      ["-   _main_ item"
       "    -   _nested_ item"]
      [:ul
       [:li [:em "main"] " item"
        [:ul
         [:li [:em "nested"] " item"]]]])))

(deftest links
  (testing "Links"
    (are [expected-markdown-lines hiccup]
         (= (str/join "\n" expected-markdown-lines)
            (sut/as-markdown hiccup))

      ;; A simple link in a paragraph
      ["See [the docs](https://clojure.org)"]
      [:p "See " [:a {:href "https://clojure.org"} "the docs"]]

      ;; Link text with nested inline formatting
      ["[Some _italic_ link](https://clojure.org)"]
      [:a {:href "https://clojure.org"} "Some " [:em "italic"] " link"]

      ;; A link inside emphasis (the reverse nesting)
      ["_Some [link](https://clojure.org) here_"]
      [:p [:em "Some " [:a {:href "https://clojure.org"} "link"] " here"]]

      ;; Links in list items
      ["-   See [the docs](https://clojure.org)"]
      [:ul [:li "See " [:a {:href "https://clojure.org"} "the docs"]]]

      ;; A link without an :href attribute is plain text
      ["See Some link for more"]
      [:p "See " [:a "Some link"] " for more"]

      ;; Newlines in the link text are replaced by spaces
      ["[link text](https://clojure.org)"]
      [:a {:href "https://clojure.org"} "link\ntext"]

      ;; Links in blockquotes
      ["> See [quoted](https://clojure.org)"]
      [:blockquote [:p "See " [:a {:href "https://clojure.org"} "quoted"]]])))

(deftest code
  (testing "Inline code"
    (are [expected-markdown-lines hiccup]
         (= (str/join "\n" expected-markdown-lines)
            (sut/as-markdown hiccup))

      ;; Simple code in a paragraph
      ["Run `clj -X:test` now"]
      [:p "Run " [:code "clj -X:test"] " now"]

      ;; Code inside a link (the case the old ns docstring said didn't work)
      ["[See `core.clj`](https://clojure.org)"]
      [:a {:href "https://clojure.org"} "See " [:code "core.clj"]]

      ;; Code inside emphasis
      ["_Use `-X:test` here_"]
      [:p [:em "Use " [:code "-X:test"] " here"]]

      ;; Code inside strong
      ["**Run `clj -X:test`**"]
      [:p [:strong "Run " [:code "clj -X:test"]]]

      ;; Newlines in the code text are replaced by spaces
      ["`foo bar`"]
      [:p [:code "foo\nbar"]])))

(deftest line-breaks
  (testing "Line breaks render as newlines inside inline text"
    (are [expected-markdown-lines hiccup]
         (= (str/join "\n" expected-markdown-lines)
            (sut/as-markdown hiccup))

      ;; Simple br in a paragraph
      ["hello  "
       "world"]
      [:p "hello" [:br] "world"]

      ;; Br at the end of a paragraph
      ["hello  "]
      [:p "hello" [:br]]

      ;; Br inside a list item (prefix on both lines)
      ["-   line1  "
       "    line2"]
      [:ul [:li "line1" [:br] "line2"]]

      ;; Br inside a blockquote
      ["> line1  "
       "> line2"]
      [:blockquote [:p "line1" [:br] "line2"]]

      ;; Br inside a link (newline replaced by space)
      ["[line1 line2](https://clojure.org)"]
      [:a {:href "https://clojure.org"} "line1" [:br] "line2"])))

(deftest preformatted
  (testing "Preformatted code preserves whitespace in a fenced block"
    (are [expected-markdown-lines hiccup]
         (= (str/join "\n" expected-markdown-lines)
            (sut/as-markdown hiccup))

      ;; Multi-line with indentation preserved
      ["```"
       "  line1"
       "  line2"
       "```"]
      [:body [:pre "  line1\n  line2"]]

      ;; Single line
      ["```"
       "code"
       "```"]
      [:body [:pre "code"]]

      ;; Inside a blockquote
      ["> ```"
       "> line1"
       "> line2"
       "> ```"]
      [:body [:blockquote [:pre "line1\nline2"]]]

      ;; Inside a list
      ["-   ```"
       "    line1"
       "    line2"
       "    ```"]
      [:body [:ul [:li [:pre "line1\nline2"]]]]

      ;; Boundary newlines are trimmed
      ["```"
       "  line1"
       "  line2"
       "```"]
      [:body [:pre "\n  line1\n  line2\n"]]

      ;; All-whitespace content renders as empty
      [""]
      [:body [:pre "   "]])))

(deftest whitespace-between-blocks
  (testing "Whitespace between block elements produces a single blank line"
    (are [expected-markdown-lines hiccup]
     (= (str/join "\n" expected-markdown-lines)
        (sut/as-markdown hiccup))

      ;; Heading followed by a paragraph.
      ;; Regression: the space between </h1> and <p> used to add 2 extra blank lines.
      ["# Some heading"
       ""
       "Followed by some text"]
      [:body [:h1 "Some heading"] " " [:p "Followed by some text"]]

      ;; Two paragraphs
      ["A"
       ""
       "B"]
      [:body [:p "A"] " " [:p "B"]]

      ;; Whitespace between blocks inside a prefixed container (blockquote) is
      ;; dropped rather than rendered as stray blank lines.
      ["> A"
       "> "
       "> B"]
      [:body [:blockquote [:p "A"] " " [:p "B"]]]

      ;; Whitespace around a single block is trimmed
      ["A"]
      [:body " " [:p "A"] " "])))

(deftest horizontal-rule
  (testing "A horizontal rule renders as --- with blank lines around it"
    (are [expected-markdown-lines hiccup]
         (= (str/join "\n" expected-markdown-lines)
            (sut/as-markdown hiccup))

      ;; Heading, hr, and paragraph
      ["# Heading"
       ""
       "---"
       ""
       "Paragraph"]
      [:body [:h1 "Heading"] [:hr] [:p "Paragraph"]]

      ;; Standalone horizontal rule
      ["---"]
      [:body [:hr]]

      ;; Two consecutive horizontal rules
      ["---"
       ""
       "---"]
      [:body [:hr] [:hr]])))

(deftest whitespace-only-block
    (are [expected-markdown-lines hiccup]
     (= (str/join "\n" expected-markdown-lines)
        (sut/as-markdown hiccup))
      [""]
      [:body [:p "   "]]))

(deftest whitespace-only-inline
  (testing "Two whitespace text nodes in one element are dropped, not rendered"
    (are [expected-markdown-lines hiccup]
     (= (str/join "\n" expected-markdown-lines)
        (sut/as-markdown hiccup))

      ;; A list item holding only whitespace must not leak a stray "-   ".
      ;; Regression: `join` merges the two empty inlines into `{:text ""}`,
      ;; which a truthiness-based `has-content?` keeps ("" is truthy).
      [""]
      [:ul [:li " " " "]]

      ;; ...inside a blockquote it leaks a stray "> ".
      [""]
      [:blockquote [:p " " " "]]

      ;; ...inside emphasis the empty text becomes empty markers "__".
      [""]
      [:p [:em " " " "]]

      ;; ...emphasis inside a list item.
      [""]
      [:ul [:li [:em " " " "]]])))
