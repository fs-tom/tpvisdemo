(ns cesiumdemo.vega
  (:require  cljsjs.vega
             cljsjs.vega-lite
             cljsjs.vega-embed
             [vega-tools.validate :refer [check]]
             [promesa.core :as p]
             [reagent.core :as r]
             [cljs-bean.core :refer [bean ->clj ->js]]))


(defn- wrap-chart-constructor
  [chart]
  (fn [params] (chart (clj->js params))))

(defn parse
  "Parse a Vega specification.
  Returns a Promesa promise that resolves to a chart constructor, or rejects
  with a parsing error."
  ([spec] (parse spec nil))
  ([spec config]
   (p/promise (fn [resolve reject]
                (js/vega.parse (clj->js spec) (clj->js config)
                                  (fn [error chart]
                                    (if error
                                      (reject error)
                                      (resolve (wrap-chart-constructor chart)))))))))

(defn validate
  "Validate a Vega specification.
  Return a Promesa promise that resolves to the spec itself, or rejects with a
  validation error."
  [spec]
  (p/promise (fn [resolve reject]
               (if-let [error (check spec)]
                 (reject error)
                 (resolve spec)))))

(defn validate-and-parse
  "Validate and parse a Vega specification.
  Returns a Promise promise that resolves to a chart constructor, or rejects
  with a validation/parsing error."
  ([spec] (validate-and-parse spec nil))
  ([spec config]
   (->> (validate spec)
        (p/mapcat #(parse % config)))))

(def vlSpec
   (clj->js {:$schema "https://vega.github.io/schema/vega-lite/v5.json",
    :data {:values [
                    {:a "C", :b 2},
                    {:a "C", :b 7},
                    {:a "C", :b 4},
                    {:a "D", :b 1},
                    {:a "D", :b 2},
                    {:a "D", :b 6},
                    {:a "E", :b 8},
                    {:a "E", :b 4},
                    {:a "E", :b 7}
                    ]
           },
    :mark "bar",
    :encoding {
               :y {:field "a", :type "nominal"},
               :x {
                   :aggregate "average",
                   :field "b",
                   :type "quantitative",
                   :axis {:title "Average of b"}}}}))


(def dummy-data
  (clj->js
   (vec (for [i (range 10)
              t  ["a" "b"]]
          {:x i
           :trend t
           :value  (case t
                     "a" (* i 2)
                     (* i 0.5))}))))
(def area-spec
  (clj->js
   {"$schema" "https://vega.github.io/schema/vega-lite/v5.json",
    "width" 300, "height" 200,
    "data" #_{"url" "unemployment-across-industries.json"} {"values" dummy-data},
    "mark" "area",
    "encoding" {"x" {"field" "x"} #_{
                     "timeUnit" "yearmonth", "field" "date",
                     "axis" {"format" "%Y"}
                     },
                "y" {"field" "value" "aggregate" "sum"}#_{
                     "aggregate" "sum", "field" "count"
                     },
                "color" {
                         "field" "trend" #_"series",
                         "scale" {"scheme" "category20b"}
                         }}}))

(defn ->stacked-area-spec [x y trend & {:keys [init-data x-label y-label trend-label]}]
  (clj->js
   {;"$schema" "https://vega.github.io/schema/vega-lite/v5.json",
    "width" 300, "height" 200,
    "data"  {"values" init-data},
    "mark" "area",
    "encoding" {(or x-label "x") {"field" x},
                (or y-label "y") {"field" "value" "aggregate" "sum"}
                (or trend-label "trend") {
                         "field" "trend" #_"series",
                         "scale" {"scheme" "category20b"}
                         }}}))

(defn stringify [m]
  (cond
    (map? m)
    (into {} (for [[k v] m]
               [(str (name k)) (stringify v)]))
    (coll? m)
     (into (empty m) (map stringify m))
     (keyword? m)  (str (name m))
     :else m))

(def charts (r/atom {}))

(defn render!
  [k spec opts]
   (let [view (js/vegaEmbed k opts)]
     (.runAsync view)))

(defn vrender! [spec]
  (vega.View. (vega.parse spec) #js{:renderer "canvas" :container "#chart-root" :hover true :log-level vega.Warn}))

(defn vega-chart [name spec]
  (let [vw (keyword (str name "-view"))]
    (r/create-class
     {:display-name (str name)
      :reagent-render (fn [] [:div])
      :component-did-mount
      (fn [this]
        (let [promise (.then (js/vegaEmbed (r/dom-node this) spec)
                             (fn [view]
                               (swap! charts assoc vw view)
                               #_(.update view)))]
          ))
      #_:component-did-update
      #_(fn [this]
          (when-let [view (get @app-state vw)]
            (.update view)))
      #_:component-will-update
      #_(fn [this]
          (let [view (chart {:el (r/dom-node this)})
                _    (swap! app-state assoc :view view)]
            (.update view)))})))

(defn chart-root
  ([k chart-spec]
   [:div
    [vega-chart  k chart-spec]])
  ([] (chart-root "chart" area-spec)))