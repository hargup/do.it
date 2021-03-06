(ns doit.events
  (:require [re-frame.core :as rf]
            [doit.db :as db]
            [day8.re-frame.http-fx]
            [ajax.core :as ajax]
            [doit.spec :as spec]
            [doit.util :as util]
            [cljsjs.toastr]
            [clojure.spec.alpha :as s]
            [day8.re-frame.tracing :refer-macros [fn-traced defn-traced]]))

(def todo-url "/api/todo/")

(def todo-list-url "/api/todo-list/")

(def db-spec-inspector
  (let [check-db-spec (fn [context db]
                        (if (s/valid? ::spec/app-db db)
                          context
                          (throw (s/explain-str ::spec/app-db db))))]
    (rf/->interceptor
     :id :db-spec-inspector
     :before (fn [context]
               (check-db-spec context (get-in context [:coeffects :db])))
     :after (fn [context]
              (check-db-spec context (get-in context [:effects :db]))))))

(defn token-headers-map [cofx]
  (let [token (get-in cofx [:db :user :token])]
    {"Authorization" (str "Bearer " token)}))

(defn request-failed [{:keys [db]} [_ err]]
  (let [status (:status err)
        body   (:body err)]
    (if (= status 403)
      {:dispatch [::sign-out]
       :flash {:type :error
               :msg "Session expired or user not invited"}}
      {:db    db
       :flash {:type :error
               :msg  (str "Request failed with response" body)}})))

(defn sign-in
  [cofx [_ token]]
  (let [url "/api/auth/verify-token/"]
    {:http-xhrio {:method          :post
                  :uri             url
                  :timeout         8000
                  :headers         (token-headers-map cofx)
                  :params          {:token token}
                  :format          (ajax/json-request-format)
                  :response-format (ajax/json-response-format {:keywords? true})
                  :on-success      [::save-auth-token token]
                  :on-failure      [::request-failed]}}))

(defn sign-out
  [cofx _]
  {:dispatch [::initialize-db]})

(defn save-auth-token
  [{:keys [db]} [_ token]]
  {:db         (assoc-in db [:user :token] token)
   :dispatch-n [[::get-todo-lists]
                [::get-todos]]})

(defn get-client-id-success
  [db [_ {:keys [client-id]}]]
  (assoc db :client-id client-id))

(defn get-client-id
  [cofx _]
  {:http-xhrio {:method          :get
                :uri             "/api/auth/client-id/"
                :timeout         8000
                :format          (ajax/json-request-format)
                :response-format (ajax/json-response-format {:keywords? true})
                :on-success      [::get-client-id-success]
                :on-failure      [::request-failed]}})

(defn get-todo-success
  [db [_ todos]]
  (assoc db :todos (util/->index-by-id todos)))

(defn assoc-todo-to-db
  [db [_ todo]]
  (assoc-in db [:todos (:id todo)] todo))

(defn add-todo
  [cofx [_ vals]]
  {:http-xhrio {:method          :post
                :uri             todo-url
                :params          vals
                :timeout         8000
                :headers         (token-headers-map cofx)
                :format          (ajax/json-request-format)
                :response-format (ajax/json-response-format {:keywords? true})
                :on-success      [::add-todo-success]
                :on-failure      [::request-failed]}})


(defn get-todos
  [cofx _]
  {:http-xhrio {:method          :get
                :uri             todo-url
                :timeout         8000
                :headers         (token-headers-map cofx)
                :format          (ajax/json-request-format)
                :response-format (ajax/json-response-format {:keywords? true})
                :on-success      [::get-todos-success]
                :on-failure      [::request-failed]}})


(defn update-todo
  [cofx [_ todo]]
  (let [url (str todo-url (:id todo) "/")]
    {:http-xhrio {:method          :put
                  :uri             url
                  :params          todo
                  :timeout         8000
                  :headers         (token-headers-map cofx)
                  :format          (ajax/json-request-format)
                  :response-format (ajax/json-response-format {:keywords? true})
                  :on-success      [::update-todo-success]
                  :on-failure      [::request-failed]}}))

(defn delete-todo-success
  [db [_ id]]
  (update-in db [:todos] dissoc id))

(defn delete-todo
  [cofx [_ id]]
  (let [url (str todo-url id "/")]
    {:http-xhrio {:method          :delete
                  :uri             url
                  :timeout         8000
                  :headers         (token-headers-map cofx)
                  :format          (ajax/json-request-format)
                  :response-format (ajax/json-response-format {:keywords? true})
                  :on-success      [::delete-todo-success id]
                  :on-failure      [::request-failed]}}))

(defn mark-done
  [{:keys [db]} [_ id]]
  (let [todo         (get-in db [:todos id])
        updated-todo (assoc todo :done true)]
    {:dispatch [::update-todo updated-todo]}))

(defn mark-undone
  [{:keys [db]} [_ id]]
  (let [todo         (get-in db [:todos id])
        updated-todo (assoc todo :done false)]
    {:dispatch [::update-todo updated-todo]}))

(defn get-todo-lists-success
  [db [_ todo-lists]]
  (assoc db :todo-lists (util/->index-by-id todo-lists)))

(defn get-todo-lists
  [cofx _]
  {:http-xhrio {:method          :get
                :uri             todo-list-url
                :timeout         8000
                :headers         (token-headers-map cofx)
                :format          (ajax/json-request-format)
                :response-format (ajax/json-response-format {:keywords? true})
                :on-success      [::get-todo-lists-success]
                :on-failure      [::request-failed]}})

(defn add-todo-list-success
  [db [_ todo-list]]
  (assoc-in db [:todo-lists (:id todo-list)] todo-list))

(defn add-todo-list
  [cofx [_ vals]]
  {:http-xhrio {:method          :post
                :uri             todo-list-url
                :timeout         8000
                :params          vals
                :headers         (token-headers-map cofx)
                :format          (ajax/json-request-format)
                :response-format (ajax/json-response-format {:keywords? true})
                :on-success      [::add-todo-list-success]
                :on-failure      [::request-failed]}})

(defn update-todo-list-success
  [db [_ todo-list]]
  (assoc-in db [:todo-lists (:id todo-list)] todo-list))

(defn update-todo-list
  [cofx [_ todo-list]]
  (let [url (str todo-list-url (:id todo-list) "/")]
    {:http-xhrio {:method          :put
                  :uri             url
                  :params          todo-list
                  :timeout         8000
                  :headers         (token-headers-map cofx)
                  :format          (ajax/json-request-format)
                  :response-format (ajax/json-response-format {:keywords? true})
                  :on-success      [::update-todo-list-success]
                  :on-failure      [::request-failed]}}))

(defn archive-todo-list
  [{:keys [db]} [_ id]]
  (let [todo-list     (get-in db [:todo-lists id])
        archived-list (assoc todo-list :archived true)]
    {:dispatch [::update-todo-list archived-list]}))

(defn delete-todo-list-success
  [db [_ id]]
  (let [db        (update-in db [:todo-lists] #(dissoc % id))
        old-todos (:todos db)
        new-todos (select-keys
                   old-todos
                   (remove #(= (:listid (get old-todos %)) id) (keys old-todos)))
        new-db    (assoc db :todos new-todos)]
    new-db))

(defn delete-todo-list
  [cofx [_ id]]
  (let [url (str todo-list-url id "/")]
    {:http-xhrio {:method          :delete
                  :uri             url
                  :timeout         8000
                  :headers         (token-headers-map cofx)
                  :format          (ajax/json-request-format)
                  :response-format (ajax/json-response-format {:keywords? true})
                  :on-success      [::delete-todo-list-success id]
                  :on-failure      [::request-failed]}}))

(defn invite-user-success
  [{:keys [db]} [_ {:keys [email]}]]
   {:db db
   :flash {:type :success
           :msg (str email " invited successfully")}})

(defn invite-user
  [cofx [_ email]]
  (let [url "/api/auth/invite-user/"]
    {:http-xhrio {:method          :post
                  :uri             url
                  :timeout         8000
                  :headers         (token-headers-map cofx)
                  :params          {:email email}
                  :format          (ajax/json-request-format)
                  :response-format (ajax/json-response-format {:keywords? true})
                  :on-success      [::invite-user-success]
                  :on-failure      [::request-failed]}}))


(defn registrations []

  (rf/reg-event-fx
   ::sign-in
   sign-in)

  (rf/reg-event-fx
   ::save-auth-token
   save-auth-token)

  (rf/reg-event-fx
   ::sign-out
   sign-out)

  (rf/reg-event-fx
   ::request-failed
   request-failed)

  (rf/reg-event-db
   ::get-client-id-success
   [db-spec-inspector]
   get-client-id-success)

  (rf/reg-event-db
   ::get-todos-success
   [db-spec-inspector]
   get-todo-success)

  (rf/reg-event-db
   ::add-todo-success
   [db-spec-inspector]
   assoc-todo-to-db)

  (rf/reg-event-db
   ::update-todo-success
   [db-spec-inspector]
   assoc-todo-to-db)

  (rf/reg-event-fx
   ::get-client-id
   get-client-id)

  (rf/reg-event-fx
   ::add-todo
   add-todo)

  (rf/reg-event-fx
   ::get-todos
   get-todos)

  (rf/reg-event-fx
   ::update-todo
   update-todo)

  (rf/reg-event-fx
   ::mark-done
   mark-done)

  (rf/reg-event-fx
   ::mark-undone
   mark-undone)

  (rf/reg-event-db
   ::delete-todo-success
   [db-spec-inspector]
   delete-todo-success)

  (rf/reg-event-fx
   ::delete-todo
   delete-todo)

  (rf/reg-event-db
   ::get-todo-lists-success
   [db-spec-inspector]
   get-todo-lists-success)

  (rf/reg-event-fx
   ::get-todo-lists
   get-todo-lists)

  (rf/reg-event-db
   ::add-todo-list-success
   [db-spec-inspector]
   add-todo-list-success)

  (rf/reg-event-fx
   ::add-todo-list
   add-todo-list)

  (rf/reg-event-db
   ::delete-todo-list-success
   [db-spec-inspector]
   delete-todo-list-success)

  (rf/reg-event-fx
   ::delete-todo-list
   delete-todo-list)

  (rf/reg-event-db
   ::update-todo-list-success
   [db-spec-inspector]
   update-todo-list-success)

  (rf/reg-event-fx
   ::update-todo-list
   update-todo-list)

  (rf/reg-event-fx
   ::archive-todo-list
   archive-todo-list)

  (rf/reg-event-fx
   ::invite-user
   invite-user)

  (rf/reg-event-fx
   ::invite-user-success
   invite-user-success)

  (rf/reg-event-db
   ::initialize-db
   (fn-traced [_ _]
              db/default-db))

  (rf/reg-fx
   :flash
   (fn [{:keys [type msg]}]
     (condp = type
       :success (.success js/toastr msg)
       :error   (.error js/toastr msg)))))

(defn toastr-init []
  (let [options {
                 :positionClass     "toast-top-center"
                 :showDuration      "300"
                 :hideDuration      "1000"
                 :timeOut           "2000"}]
    (aset js/toastr "options" (clj->js options))))

(defn init []
  (toastr-init)
  (registrations))
