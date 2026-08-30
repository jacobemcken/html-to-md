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

(deftest multi-line-text
  (testing "Multi-line text keeps every line"
    (are [markdown-lines hiccup]
         (= markdown-lines
            (str/split (sut/as-markdown hiccup) #"\n"))

      ["-   line1"
       "    line2"
       "    line3"]
      [:ul [:li "line1\nline2\nline3"]])))

(deftest blockqoutes
  (testing "Unordered lists"
    (are [markdown-lines hiccup]
         (= markdown-lines
            (str/split (sut/as-markdown hiccup) #"\n"))

      ;; A simple blockqoute
      ["> Some text"]
      [:blockquote "Some text"]

      ;; A blockqoute with multiple paragraphs
      ["> Some text"
       "> "
       "> Some other text"]
      [:blockquote [:p "Some text"] [:p "Some other text"]])))

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

      ; A blockqoute inside a list
      ["-   > X"
       "    > "
       "    > Y"
       "-   Z"
       "    "
       "    W"]
      [:ul
       [:li [:blockquote [:p "X"] [:p "Y"]]]
       [:li [:p "Z"] [:p "W"]]])))

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
