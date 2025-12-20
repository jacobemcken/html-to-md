(ns html-to-md.hiccup-to-md-test
  (:require [clojure.test :refer [are deftest testing]]
            [html-to-md.hiccup-to-md :as sut]))

(deftest child-elements
  (testing "Identifying children correctly regardless of element having attributes"
    (are [child-elements element]
         (= child-elements
            (sut/child-elements element))

      [[:span {:class "red"} "A"] "B" [:span "C"]]
      [:div {:class "blue"} [:span {:class "red"} "A"] "B" [:span "C"]]

      ["A" "B" [:span "C"]]
      [:div "A" "B" [:span "C"]]

      nil
      [:div {:class "blue"}]

      nil
      [:div])))

(deftest get-text
  (are [text element]
       (= text (sut/get-text element))

    "Hi there"
    [:div [:span "Hi"] " " [:span "there"]]

    "Hi there"
    [:div "Hi " [:span "there"]]

    "Hi there"
    [:div "Hi" " there"]

    "Hi there"
    [:div "Hi there"]))

(deftest some-wrap
  (testing "Helper function for many tags like h1, em, code etc."
    (are [text element]
         (= text (sut/some-wrap element #(str "*" % "*")))

      "*Hi there*"
      [:div [:span "Hi"] " " [:span "there"]]

      "*Hi there*"
      [:div "Hi " [:span "there"]]

      "*Hi there*"
      [:div "Hi" " there"]

      "*Hi there*"
      [:div "Hi there"]

      nil
      [:div {:class "red"}])))

(deftest ahref-convert
  (testing "Converting HTML link to markdown"
    (are [link-text link-hiccup]
         (= link-text
            (sut/convert link-hiccup))

      "[Some link](https://clojure.org)"
      [:a {:href "https://clojure.org"} "Some link"]

      "[Some link](https://clojure.org)"
      [:a {:href "https://clojure.org"} [:span "Some"] " " [:span "link"]])))
