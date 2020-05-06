(defproject patika "0.1.11"

  :description "Clojure routing library which is an abstraction over Liberator + Compojure"

  :url "https://github.com/ertugrulcetin/patika"

  :author "Ertuğrul Çetin"

  :license {:name "MIT License" :url "https://opensource.org/licenses/MIT"}

  :deploy-repositories [["clojars" {:sign-releases false :url "https://clojars.org/repo"}]
                        ["releases" {:sign-releases false :url "https://clojars.org/repo"}]
                        ["snapshots" {:sign-releases false :url "https://clojars.org/repo"}]]

  :dependencies [[org.clojure/clojure "1.10.1"]
                 [org.clojure/data.json "1.0.0"]
                 [org.clojure/tools.logging "1.0.0"]
                 [org.clojure/java.classpath "1.0.0"]
                 [org.clojure/tools.namespace "1.0.0"]
                 [compojure "1.6.1"]
                 [liberator "0.15.3"]])