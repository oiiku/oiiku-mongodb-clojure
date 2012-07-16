(ns oiiku-mongodb.db
  (:require [monger.core :as mg]
            [monger.collection :as mc]
            [monger.query :as mq])
  (:import [org.bson.types ObjectId]))

(defn connect
  [db-name]
  (mg/connect!)
  (mg/set-db! (mg/get-db db-name)))


(defn serialize
  [record]
  (-> record
      (assoc "id" (.toString (record :_id)))
      (dissoc :_id)
      clojure.walk/stringify-keys))

(defn- perform-insert
  [collection data]
  (let [data (assoc data :_id (ObjectId.))]
    (clojure.walk/keywordize-keys (mc/insert-and-return collection data))))

(defn make-insert
  ([collection validator]
     (make-insert collection validator (fn [data] data)))
  ([collection validator processor]
     (fn [data]
       (let [data-str (clojure.walk/stringify-keys data)
             errors (validator data-str)]
         (if (empty? errors)
           [true (perform-insert collection (processor data-str))]
           [false errors])))))

(defn make-find-one
  [collection]
  (fn [q]
    (if-let [result (mc/find-one-as-map collection q)]
      result)))

(defn make-find-all
  [collection]
  (fn [q]
    (mc/find-maps collection q)))

(defn make-paginate
  [collection]
  (fn [query limit offset]
    (let [count (mc/count collection query)
          documents (mq/with-collection collection
                      (mq/find query)
                      (mq/limit limit)
                      (mq/skip offset))]
      {:count count
       :documents documents})))

(defn make-delete
  [collection]
  (fn [id]
    (mc/remove-by-id collection id)))