(ns build
  (:require [clojure.tools.build.api :as b]))

(def lib 'dk.emcken/html-to-md)
(def version "0.8.0")
(def class-dir "target/classes")
(def jar-file (format "target/%s-%s.jar" (name lib) version))

;; delay to defer side effects (artifact downloads)
(def basis (delay (b/create-basis {:project "deps.edn"})))

(defn clean [_]
  (b/delete {:path "target"}))

(defn jar [_]
  (b/write-pom {:class-dir class-dir
                :lib lib
                :version version
                :basis @basis
                :src-dirs ["src"]})
  (b/copy-dir {:src-dirs ["src" "resources"]
               :target-dir class-dir})
  (b/jar {:class-dir class-dir
          :jar-file jar-file}))

(defn install [_]
  (jar nil)
  (b/install {:basis basis
              :lib lib
              :version version
              :class-dir class-dir
              :jar-file (str "target/" (name lib) "-" version ".jar")}))