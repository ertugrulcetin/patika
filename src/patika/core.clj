(ns patika.core
  (:require [clojure.data.json :as json]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.tools.logging :as log]
            [clojure.java.classpath :as classpath]
            [clojure.tools.namespace.find :as ns-find]
            [liberator.representation :as rep]
            [liberator.core :as liberator]
            [compojure.core :as compojure])
  (:import (java.time ZoneId)
           (java.time.format DateTimeFormatter)))


(def media-types
  {:text      ["text/plain"]
   :json      ["application/json" "application/json; charset=utf-8" "application/json; charset=UTF-8"]
   :html      ["text/html" "text/html; charset=utf-8" "text/html; charset=UTF-8"]
   :xml       ["text/xml" "text/xml; charset=utf-8" "text/xml; charset=UTF-8" "text/xml-external-parsed-entity"]
   :multipart ["application/octet-stream" "multipart/form-data" "multipart/mixed" "multipart/related"]})


(defn- datetime->zonedtimestr
  [datetime]
  (-> datetime
      (.atZone (ZoneId/systemDefault))
      (.format (DateTimeFormatter/ISO_OFFSET_DATE_TIME))))


(extend-protocol rep/Representation
  clojure.lang.ExceptionInfo
  (as-response [this r]
    (let [m          (Throwable->map this)
          result-map (select-keys m [:cause :data])]
      (assoc r :status 500 :body (json/write-str result-map)))))


(extend-protocol
  json/JSONWriter

  java.util.UUID
  (-write [uuid* out]
    (json/-write (str uuid*) out))

  java.time.LocalDateTime
  (-write [time* out]
    (json/-write (datetime->zonedtimestr time*) out)))


(defn- body-as-string
  [ctx]
  (if-let [body (get-in ctx [:request :body])]
    (condp instance? body
      String body
      (slurp (io/reader body)))))


(defn- check-content-type
  [ctx content-types]
  (if (#{:put :post} (get-in ctx [:request :request-method]))
    (or
      (some #{(get-in ctx [:request :headers "content-type"])}
            content-types)
      [false {:message "Unsupported Content-Type"}])
    true))


(defn- parse-json
  [ctx key]
  (when (#{:put :post} (get-in ctx [:request :request-method]))
    (try
      (if-let [body (body-as-string ctx)]
        (let [data (json/read-str body)]
          [false {key data}])
        {:message "No body"})
      (catch Exception e
        (.printStackTrace e)
        {:message (format "IOException: %s" (.getMessage e))}))))


(defn- get-exception-message
  [ctx]
  (let [ex  (:exception ctx)
        msg (.getMessage ex)]
    (log/error ex)
    {:error msg}))


(defn- authorized?
  [ctx]
  true)


(defn- get-method-map
  [method]
  (if (= method :put)
    {:allowed-methods      [:put]
     :new?                 false
     :respond-with-entity? true}
    {:allowed-methods [method]}))


(defn- get-media-type-map
  [media-type]
  (case media-type
    :json {:available-media-types (:json media-types)
           :known-content-type?   #(check-content-type % (:json media-types))
           :malformed?            #(parse-json % :request-data)
           :handle-exception      #(get-exception-message %)}
    :text {:available-media-types (:text media-types)
           :handle-exception      (fn [ctx]
                                    (let [ex (:exception ctx)]
                                      (log/error ex)
                                      "Something went wrong"))}
    :html {:available-media-types (:html media-types)
           :handle-exception      (fn [ctx]
                                    (let [ex (:exception ctx)]
                                      (log/error ex)
                                      "Something went wrong"))}
    :xml {:available-media-types (:xml media-types)
          :handle-exception      (fn [ctx]
                                   (let [ex (:exception ctx)]
                                     (log/error ex)
                                     "Something went wrong"))}
    :multipart {:available-media-types (:multipart media-types)
                :handle-exception      (fn [ctx]
                                         (let [ex      (:exception ctx)
                                               err-msg (.getMessage ex)]
                                           (log/error ex)
                                           err-msg))}))


(defn- multi-params->map
  [params]
  (->> params
       (partition 2)
       (map vec)
       (into {})))


(defn- get-handle-ok-or-create-map
  [method media-type]
  (cond
    (and (= method :put) (= media-type :json))
    {:handle-ok (fn [& args] {:success true})}

    (and (= method :post) (= media-type :json))
    {:handle-created (fn [& args] {:success true})}))


(defn- get-redirect-map-based-on-auth
  [m]
  (when-let [path (or (:redirect-auth m) (:redirect-not-auth m))]
    {:authorized?        #(cond
                            (and (:redirect-auth m) (authorized? %))
                            {:redirect-required? true
                             :redirect-path      (:redirect-auth m)}

                            (and (:redirect-not-auth m) (not (authorized? %)))
                            {:redirect-required? true
                             :redirect-path      (:redirect-not-auth m)}

                            :else
                            {:redirect-required? false})
     :moved-temporarily? (fn [ctx]
                           {:location (or (:redirect-path ctx) "/")})}))


(defn- get-redirect-based-on-pred
  [m]
  (let [r (:redirect! m)]
    (if (fn? r)
      (let [result (atom nil)
            path   (atom nil)]
        {:exists?            (fn [ctx]
                               (let [[result-i path-i] (r ctx)]
                                 (reset! result result-i)
                                 (reset! path path-i))
                               (not @result))
         :existed?           (fn [_] @result)
         :moved-temporarily? (fn [_] {:location (or @path "/")})})
      (when-let [[result path] (:redirect! m)]
        {:exists?            (fn [_] (not result))
         :existed?           (fn [_] result)
         :moved-temporarily? (fn [_] {:location (or path "/")})}))))


(defn get-auth-and-redirect-maps
  [m]
  (let [auth-req      (and (:auth-fn m) {:authorized? (:auth-fn m)})
        redirect-auth (get-redirect-map-based-on-auth m)
        exit-maps     {:exists?  (fn [ctx] (not (:redirect-required? ctx)))
                       :existed? (fn [ctx] (:redirect-required? ctx))}
        redirect      (get-redirect-based-on-pred m)]
    (merge auth-req redirect-auth exit-maps redirect)))


(defmacro resource
  [name method endpoint-and-binding _ media-type & opts]
  (let [resource-name (symbol (str "resource-" name))]
    `(compojure/defroutes ~(vary-meta resource-name assoc :resource? true)
                          (~(case method
                              :get `compojure/GET
                              :post `compojure/POST
                              :put `compojure/PUT
                              :delete `compojure/DELETE)
                            ~(first endpoint-and-binding)
                            ~(if (seq (second endpoint-and-binding)) (second endpoint-and-binding) [])
                            (let [method-map#     ~(get-method-map method)
                                  type-map#       ~(get-media-type-map media-type)
                                  m#              ~(multi-params->map opts)
                                  handle-ok-maps# ~(get-handle-ok-or-create-map method media-type)
                                  auth-maps#      (get-auth-and-redirect-maps m#)
                                  r#              (merge method-map# type-map# handle-ok-maps# auth-maps# m#)]
                              (liberator/resource r#))))))


(defn- find-ns-symbols
  [resource-ns-path]
  (for [ns-symb (ns-find/find-namespaces (classpath/system-classpath))
        :when (str/starts-with? (name ns-symb) resource-ns-path)]
    ns-symb))


(defn- check-opts
  [opts]
  (cond
    (not (or (:resource-ns-path opts)
             (:resource-ns-vec opts)))
    (throw (Exception. ":resource-ns-path or :resource-ns-vec not defined! You need to define one of them."))

    (and (not (:resource-ns-path opts)) (= [] (:resource-ns-vec opts)))
    (throw (Exception. ":resource-ns-vec is empty vector!"))

    (and (:not-found-route opts) (nil? (resolve (:not-found-route opts))))
    (throw (Exception. "Could not resolve :not-found-route. Check your :not-found-route declaration."))))


(defn get-routes
  [opts]
  (check-opts opts)
  (let [ns-symbols    (or (:resource-ns-vec opts) (find-ns-symbols (:resource-ns-path opts)))
        resource-vars (->> ns-symbols
                           (map #(do (require %) %))
                           (reduce #(conj %1 (vals (ns-publics %2))) [])
                           flatten
                           (filter #(:resource? (meta %)))
                           vec)
        all-routes    (if-let [not-found-route (some-> (:not-found-route opts) resolve)]
                        (conj resource-vars not-found-route)
                        resource-vars)]
    (log/info (count all-routes) " routes found.")
    (apply compojure/routes all-routes)))