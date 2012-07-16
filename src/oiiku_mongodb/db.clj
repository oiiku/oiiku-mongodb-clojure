(ns oiiku-mongodb.db
  (:require [monger.core :as mg]
            [monger.collection :as mc]
            [monger.query :as mq])
  (:import [org.bson.types ObjectId]))

(defn connect
  [db-name]
  (mg/connect!)
  (mg/set-db! (mg/get-db db-name)))

(defn oid
  "Creates a MongoDB ObjectId from a string."
  [id]
  (ObjectId. id))

(defn- stringify-oids
  "Converts all instances of ObjectId into strings."
  [map]
  (clojure.walk/postwalk
   (fn [x]
     (if (= (type x) org.bson.types.ObjectId)
       (.toString x)
       x))
   map))

(defn serialize
  [record]
  (-> record
      (assoc :id (record :_id))
      (dissoc :_id)
      stringify-oids))

(defn- perform-insert
  [collection data]
  (let [data (assoc data :_id (ObjectId.))
        record (mc/insert-and-return collection data)]
    (zipmap (map keyword (keys record)) (vals record))))

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