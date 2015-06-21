(ns ^:figwheel-always heritage.core
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [om.core :as om]
            [cljs.core.async :refer [<! put!]]
            [milia.api.dataset :as api]
            [milia.api.io :as io]
            [milia.utils.remote :as milia-remote]
            [hatti.ona.forms :refer [flatten-form]]
            [hatti.ona.post-process :refer [integrate-attachments!]]
            [hatti.shared :as shared]
            [hatti.utils :refer [json->cljs]]
            [hatti.views :as views]
            [hatti.views.dataview]
            [ankha.core :as ankha]))

;; CONFIG
(enable-console-print!)
(swap! milia-remote/hosts merge {:ui "localhost:8000"
                                 :data "ona.io"
                                 :ona-api-server-protocol "https"})
(def dataset-id "49501") ;; Cultural Heritage
(def mapbox-tiles
  [{:url "http://{s}.tile.openstreetmap.fr/hot/{z}/{x}/{y}.png"
    :name "Humanitarian OpenStreetMap Team"
    :attribution "&copy;  <a href=\"http://osm.org/copyright\">
                  OpenStreetMap Contributors.</a>
                  Tiles courtesy of
                  <a href=\"http://hot.openstreetmap.org/\">
                  Humanitarian OpenStreetMap Team</a>."}])
(def auth-token nil)

(def private-fields
  ["surveyor_id" "site_id" "local_contact" "security" "security_comment"])


(defn has-value [key value]
  (fn [m]
    (= value (m key))))


(defn remove-private-fields [form]
  (let [r (for [i private-fields]
            (filter  (has-value :full-name i) form))
        flatr (flatten r)]
    (remove (set flatr) form)))

;(def public-form (remove-private-fields form))



;(go
 ;(let [form-chan (api/form auth-token dataset-id)
  ;     form (-> (<! form-chan) :body flatten-form)
   ;    public-form (remove-private-fields form)]
   ;(om/root ankha/inspector public-form
    ;        {:target (. js/document (getElementById "app"))})))


;; define your app data so that it doesn't get over-written on reload
(go
 (let [data-chan (api/data auth-token dataset-id :raw? true)
       form-chan (api/form auth-token dataset-id)
       info-chan (api/metadata auth-token dataset-id)
       data (-> (<! data-chan) :body json->cljs)
       form (-> (<! form-chan) :body flatten-form)
       public-form (remove-private-fields form)
       info (-> (<! info-chan) :body)]
  ; (.log js/console (clj->js form))))
  (shared/update-app-data! shared/app-state data :rerank? true)
   (shared/transact-app-state! shared/app-state [:dataset-info] (fn [_] info))
  (shared/transact-app-state! shared/app-state [:views :all] (fn [_] [:map :table :details]))
   (integrate-attachments! shared/app-state public-form)
   (om/root views/tabbed-dataview
            shared/app-state
            {:target (. js/document (getElementById "map"))
             :shared {:flat-form public-form
                      :map-config {:mapbox-tiles mapbox-tiles}}})))
