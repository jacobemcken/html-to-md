(ns html-to-md.core-test
  (:require [clojure.test :refer [deftest are testing]]
            [html-to-md.core :as sut]))

(deftest html-entity-unescape
  (testing "HTML entities in source are decoded in the markdown output"
    (are [expected html]
         (= expected (sut/convert html))

      ;; The reported case: angle brackets in code
      "`<link>`"
      "<code>&lt;link&gt;</code>"

      ;; Angle brackets in plain text
      "a < b"
      "<p>a &lt; b</p>"

      ;; Ampersand
      "a & b"
      "<p>a &amp; b</p>"

      ;; Literal &lt; in source (double-escaped in HTML)
      "&lt;link&gt;"
      "<p>&amp;lt;link&amp;gt;</p>"

      ;; Quotation mark
      "say \"hello\""
      "<p>say &quot;hello&quot;</p>")))
