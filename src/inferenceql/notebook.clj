(ns inferenceql.notebook
  (:import [java.io File InputStream]
           [org.jsoup Jsoup])
  (:require [clojure.pprint :as pprint]
            [com.stuartsierra.component :as component]
            [inferenceql.notebook.asciidoc :as asciidoc]
            [reitit.ring :as ring]
            [ring.adapter.jetty :as jetty]
            [ring.middleware.content-type :refer [wrap-content-type]]
            [ring.middleware.not-modified :refer [wrap-not-modified]]
            [ring.middleware.file :refer [wrap-file]]
            [ring.middleware.refresh :refer [wrap-refresh]]
            [ring.util.response :as response]))

(defn not-found-handler
  [_request]
  (-> (response/not-found "Not found")
      (response/header "Content-Type" "text/plain")))

(defrecord JettyServer [handler port]
  component/Lifecycle

  (start [component]
    (let [jetty-server (jetty/run-jetty handler {:port port :join? false})]
      (assoc component :server jetty-server)))

  (stop [{:keys [server]}]
    (.stop server)))

(defn wrap-debug [handler]
  (fn [request]
    (pprint/pprint request)
    (let [response (handler request)]
      (pprint/pprint response)
      response)))

(defn ->string
  [x]
  (condp = (type x)
    String x
    File (slurp x)
    InputStream (slurp x)
    (if (seq? x)
      (reduce str x)
      (throw (ex-info "Cannot convert to string: " (pr-str x)
                      {:value x})))))

(defn add-script
  [html selector url]
  (let [doc (Jsoup/parse html)]
    (-> doc
        (.select selector)
        (.first)
        (.appendElement "script")
        (.attr "type" "text/javascript")
        (.attr "crossorigin" true)
        (.attr "src" url))
    (str doc)))

(defn add-stylesheet
  [html url]
  (let [doc (Jsoup/parse html)]
    (-> doc
        (.select "head")
        (.first)
        (.appendElement "link")
        (.attr "rel" "stylesheet")
        (.attr "href" url))
    (str doc)))

(defn wrap-convert
  "Ring middleware that converts Asciidoc to HTML."
  [handler]
  (fn [request]
    (let [{:keys [status] :as response} (handler request)]
      (if (and (= 200 status)
               (= "text/plain" (response/get-header response "Content-Type")))
        (-> response
            (update :body #(-> % ->string asciidoc/->html))
            (update :headers assoc "Content-Type" "text/html")
            (update :headers dissoc "Content-Length"))
        response))))

(defn transform-html
  [html]
  (-> html
      (add-script "body" "js/main.js")
      (add-stylesheet "styles/github.css")))

(defn wrap-transform-html [handler]
  (fn [request]
    (let [{:keys [status] :as response} (handler request)]
      (if (and (= 200 status)
               (= "text/html" (response/get-header response "Content-Type")))
        (-> response
            (update :body #(-> % ->string transform-html))
            (update :headers dissoc "Content-Length"))
        response))))

(defn app
  [& {:keys [path]}]
  (ring/ring-handler
   (ring/router
    [["/styles/*" (ring/create-file-handler {:root "node_modules/highlight.js/styles"})]
     ["/js/*" (ring/create-file-handler {:root "out"})]])
   (-> #'not-found-handler
       (wrap-file path {:index-files? false})
       (wrap-content-type {:mime-types {"adoc" "text/plain"}})
       (wrap-not-modified)
       (wrap-convert)
       (wrap-transform-html)
       ;; (wrap-refresh path)
       )))

(defn jetty-server
  [& {:as opts}]
  (map->JettyServer opts))
