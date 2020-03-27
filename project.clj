(defproject patika "0.1.0"

  :description "Clojure routing library which is an abstraction over Liberator + Compojure"

  :url "https://github.com/ertugrulcetin/patika"

  :author "Ertuğrul Çetin"

  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url  "https://www.eclipse.org/legal/epl-2.0/"}

  :deploy-repositories [["clojars" {:sign-releases false :url "https://clojars.org"}]
                        ["releases" {:sign-releases false :url "https://clojars.org"}]
                        ["snapshots" {:sign-releases false :url "https://clojars.org"}]]

  :dependencies [[org.clojure/clojure "1.10.1"]
                 [org.clojure/data.json "1.0.0"]
                 [org.clojure/tools.logging "1.0.0"]
                 [org.clojure/java.classpath "1.0.0"]
                 [org.clojure/tools.namespace "1.0.0"]
                 [liberator "0.15.3"]])
