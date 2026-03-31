(ns html-to-md.hiccup-to-md-test
  (:require
   [clojure.string :as str]
   [clojure.test :refer [are deftest is testing]]
   [html-to-md.hiccup-to-md :as sut]))

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
    (are [expected hiccup]
         (= expected (sut/as-markdown hiccup))

      ;; Simple italic text
      "*italic*"
      [:em "italic"])))
