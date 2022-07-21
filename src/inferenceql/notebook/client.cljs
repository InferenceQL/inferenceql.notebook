(ns inferenceql.notebook.client
  (:require ["@inferenceql/components" :as components]
            ["react" :as react]
            ["react-dom" :as react-dom]
            [clojure.string :as string]
            [cognitect.transit :as transit]))

(enable-console-print!)

(defn ^:export execute
  [_s]
  (clj->js
   {:columns ["name" "age" "color"]
    :rows [{:name "Disco" :age 16 :color "brown"}
           {:name "Henry" :age 14 :color "orange"}
           {:name "Zelda" :age 13 :color "black"}]}))

(doseq [code (js/window.document.querySelectorAll "pre code")]
  (let [query (.-innerText code)
        pre (.closest code "pre")
        parent (.-parentElement pre)
        div (.createElement js/window.document "div")]
    (.insertBefore parent div pre)
    (.remove pre)
    (let [props #js {:execute execute
                     :initialQuery (string/trim query)}]
      (react-dom/render (react/createElement components/Query props) div))))
