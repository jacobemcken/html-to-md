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
  (testing "Emphasis formatting"
    (are [expected-markdown-lines hiccup]
         (= (str/join "\n" expected-markdown-lines)
            (sut/as-markdown hiccup))

      ;; Simple italic text
      ["_italic_"]
      [:em "italic"]

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
