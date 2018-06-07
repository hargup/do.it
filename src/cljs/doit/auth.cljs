(ns doit.auth
  (:refer-clojure :exclude [subs])
  (:require [cljs.core.async :refer [put! >! <! take chan buffer ]]
            [doit.config :as config]
            [doit.subs :as subs]
            [doit.events :as events]
            [re-frame.core :as rf]
            [goog.object]))

(defn token-headers-map [cofx]
  (let [token (get-in cofx [:db :user :token])]
    {"Authorization" (str "Bearer " token)}))

(defn get-auth-instance []
  (.getAuthInstance (goog.object/get js/gapi "auth2")))

(defn save-auth-token
  [{:keys [db]} [_ token]]
  {:db (assoc-in db [:user :token] token)
   :dispatch [::events/get-todos]})

(rf/reg-event-fx
 ::save-auth-token
 save-auth-token)

(defn sign-out-event
  [cofx _]
  {:dispatch [::events/initialize-db]})

(rf/reg-event-fx
 ::sign-out
 sign-out-event)

(defn sign-in-success [gapi-user]
  (let [token (.-id_token (.getAuthResponse gapi-user))]
    (rf/dispatch [::save-auth-token token])
    (prn "sign in success")))

(defn sign-in-failure []
  (prn "sign in failed"))

(defn sign-in []
  (-> (.signIn (get-auth-instance))
      (.then sign-in-success sign-in-failure)))

(defn sign-out []
  (-> (.signOut (get-auth-instance))
      (.then #(rf/dispatch [::sign-out]))))

(defn on-gapi-load []
  (prn "gapi auth2 loaded")
  (.init (goog.object/get js/gapi "auth2") (clj->js {:client_id @(rf/subscribe [::subs/client-id])})))

(defn init []
  (.load js/gapi "auth2" on-gapi-load))
