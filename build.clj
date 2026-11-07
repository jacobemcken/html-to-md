(ns build
  (:require [babashka.deps-deploy :as dd]
            [clojure.tools.build.api :as b]))

(def lib 'dk.emcken/html-to-md)
(def version "0.8.0")
(def class-dir "target/classes")
(def jar-file (format "target/%s-%s.jar" (name lib) version))

;; delay to defer side effects (artifact downloads)
(def basis (delay (b/create-basis {:project "deps.edn"})))

(def pom
  [[:description "Convert HTML to markdown text."]
   [:url "https://github.com/jacobemcken/html-to-md"]
   [:licenses
    [:license
     [:name "The MIT License (MIT)"]]]
   [:developers
    [:developer
     [:name "Jacob Emcken"]
     [:url "https://www.emcken.dk"]]]])

(defn clean [_]
  (b/delete {:path "target"}))

(defn jar [_]
  (b/write-pom {:class-dir class-dir
                :lib lib
                :version version
                :basis @basis
                :src-dirs ["src"]
                :pom-data pom})
  (b/copy-dir {:src-dirs ["src" "resources"]
               :target-dir class-dir})
  (b/jar {:class-dir class-dir
          :jar-file jar-file}))

(defn install [_]
  (b/install {:basis basis
              :lib lib
              :version version
              :class-dir class-dir
              :jar-file jar-file}))

(defn deploy
  "Deploy the JAR to Clojars."
  [_]
  (dd/deploy {:installer :remote
              :artifact (b/resolve-path jar-file)
              :pom-file (b/pom-path {:lib lib
                                     :class-dir class-dir})}))
