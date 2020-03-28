# patika

Patika is a new Clojure routing library which is an abstraction over [Liberator](https://clojure-liberator.github.io/liberator/) + [Compojure](https://github.com/weavejester/compojure).

## Installation
[![Clojars Project](https://img.shields.io/clojars/v/patika.svg)](https://clojars.org/patika)

## Usage
```clojure
(require '[patika.core :refer [resource get-routes]])
```

## Let's work on a full example
```clojure
(ns patika.api.common
  (:require [patika.core :refer [resource get-routes]]
            [compojure.core :as c]
            [compojure.route :as r]
            [ring.adapter.jetty :refer [run-jetty]]
            [liberator.representation :as rep]))


;; Response returns text/plain
(resource hello
          :get ["/hello"]
          :content-type :text
          :handle-ok (fn [ctx] "Hello World"))


;; Response returns plain HTML
(resource home
          :get ["/"]
          :content-type :html
          :handle-ok (fn [ctx] "<html><body>Hello Patika!</body></html>"))


;; Response returns JSON, no need manuel JSON transformation!
(resource users
          :get ["/users"]
          :content-type :json
          :handle-ok (fn [ctx] [{:name "Ertuğrul" :age 28} {:name "Çetin" :age 22}]))


;; PUT example, response returns -> {:success? true} in JSON format
(resource create-user
          :put ["/users"]
          :content-type :json
          :put! (fn [ctx]
                  (let [data (clojure.walk/keywordize-keys (:request-data ctx))]
                    (create-user! (:email data) (:password data))))
          :handle-ok (fn [ctx] {:success? true}))


;; POST example, response returns -> {:success? true :user-id id} in JSON format
;; Also, manuel exception handling with :handle-exception
(resource activate-user
          :post ["/users/:id" [id]]
          ;;You can use coercion like this ["/users/:id" [id :<< compojure.coercions/as-int]]
          :content-type :json
          :post! (fn [ctx] (activate-user-by-id id))
          :handle-created (fn [ctx] {:success? true :user-id id})
          ;;Optional, if you want to handle exception. If you don't set your own, default one will be used.
          :handle-exception (fn [ctx] (println "Error ocurred: " (:exception ctx))))


;; Route with AUTHORIZATION -> :auth-fn
(resource send-event
          :put ["/events"]
          :content-type :json
          ;;If :auth-fn returns TRUTHY value, then it proceeds. If not, client gets 401 HTTP error.
          :auth-fn (fn [ctx] (-> ctx :request :headers (get "x-auth-token")))
          :put! #(create-event %)
          :handle-ok (fn [_] {:success? true}))


;; Redirect and Cookie Set example -> :redirect! and :as-response
(resource dictionary
          :get ["/dictionary/:word" [word]]
          :content-type :html
          ;;If first value of vector returns `true` then there will be redirection to /word-does-not-exist path.
          :redirect! [(not (get dictionary-map word)) "/word-does-not-exist"]
          :handle-ok (fn [_]
                       (let [details   (get-word-details word)
                             word-data {:word    word
                                        :details details
                                        :body    (generate-html)}]
                         word-data))
          :as-response (fn [word-data ctx]
                         (-> (rep/as-response (:body word-data) ctx)
                             (assoc-in [:headers "Set-Cookie"] (str "word=" (:word word-data) ";details=" (:details word-data))))))


;; Uploading some file, multipart data
(resource upload-file
          :post ["/upload"]
          :content-type :multipart
          :post! (fn [ctx]
                   (let [file (-> ctx :request :params (get "file") :tempfile)]
                     (with-open [rdr (io/reader file)]
                       ...)))
          :handle-exception #(.getMessage (:exception %)))


;; Generating sitemap.xml
(resource sitemap
          :get ["/sitemap.xml"]
          :content-type :xml
          :handle-ok (fn [_] (generate-sitemap-string)))


;; If provided routes do not exists, they will be redirected to this one. 
(c/defroutes not-found
             (r/not-found "404!"))


(defn run-dev-server
  [port]
  ;;Scans namespaces then filters namespaces start with "patika.api." and registers routes automatically.
  ;;You can also manually register routes instead of using :resource-ns-path
  ;;-> {:resource-ns-vec '[patika.api.common patika.api.users patika.api.segments ..]}
  (run-jetty (get-routes {:resource-ns-path "patika.api."
                          :not-found-route  'patika.api.common/not-found})
             {:port port :join? false}))


(run-dev-server 3000)
```


## License

Copyright © 2020 Ertuğrul Çetin

This program and the accompanying materials are made available under the
terms of the Eclipse Public License 2.0 which is available at
http://www.eclipse.org/legal/epl-2.0.

This Source Code may also be made available under the following Secondary
Licenses when the conditions for such availability set forth in the Eclipse
Public License, v. 2.0 are satisfied: GNU General Public License as published by
the Free Software Foundation, either version 2 of the License, or (at your
option) any later version, with the GNU Classpath Exception which is available
at https://www.gnu.org/software/classpath/license.html.
