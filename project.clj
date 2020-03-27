(defproject patika "0.1.0"

  :description "FIXME: write description"

  :url "https://github.com/ertugrulcetin/patika"

  :author "Ertuğrul Çetin"

  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url  "https://www.eclipse.org/legal/epl-2.0/"}

  :deploy-repositories [["releases" {:sign-releases false :url "https://clojars.org"}]
                        ["snapshots" {:sign-releases false :url "https://clojars.org"}]]

  :dependencies [[org.clojure/clojure "1.10.1"]
                 [org.clojure/data.json "1.0.0"]
                 [org.clojure/tools.logging "1.0.0"]
                 [org.clojure/java.classpath "1.0.0"]
                 [org.clojure/tools.namespace "1.0.0"]
                 [liberator "0.15.3"]]

  :plugins [[lein-ancient "0.6.15"]]

  :min-lein-version "2.5.3"

  :repl-options {:init-ns patika.core}

  :source-paths ["src"]

  :test-paths ["test"]

  :resource-paths ["resources"]

  :target-path "target/%s/"

  :main ^:skip-aot patika.core

  :clean-targets ^{:protect false} ["target"])
