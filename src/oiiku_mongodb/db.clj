(ns oiiku-mongodb.db
  (:require [monger.core :as mg]
            [monger.collection :as mc]
            [monger.query :as mq]
            bultitude.core)
  (:import [org.bson.types ObjectId]))

(defn create-connection
  ([] (monger.core/connect))
  ([opts] (monger.core/connect opts)))

(defn create-db
  [conn db-name]
  (mg/get-db conn db-name))

(defn oid
  "Creates a MongoDB ObjectId from a string."
  [id]
  (ObjectId. id))

(defn perform-ensure-index
  [conn db all]
  (mg/with-connection conn
    (mg/with-db db
      (doseq [[collection indexes] all]
        (mc/ensure-index
         collection
         (nth indexes 0)
         (nth indexes 1 {}))))))

(defn ensure-indexes
  "Creates indexes if they don't exist, by looking for an 'indexes' var
   in the provided namespaces.

   The 'indexes' var is a map where the keys are collection names and the
   values are specifications of the index to be made in the form of:

     {\"my-collection\"
      [{:attribute-name 1}, {:unique true}]
      [{:other-attr 1 :yet-another-attr 2}}"
  [conn db namespace-prefix]
  (let [nses (bultitude.core/namespaces-on-classpath :prefix namespace-prefix)]
    (doseq [ns nses]
      (if-let [indexes (ns-resolve ns 'indexes)]
        (perform-ensure-index conn db @indexes)))))

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
  [conn db collection data]
  (mg/with-connection conn
    (mg/with-db db
      (let [data (assoc data :_id (ObjectId.))
            record (mc/insert-and-return collection data)]
        (zipmap (map keyword (keys record)) (vals record))))))

(defn make-insert
  ([collection validator]
     (make-insert collection validator (fn [data] data)))
  ([collection validator processor]
     (fn [conn db data]
       (let [data-str (clojure.walk/stringify-keys data)
             errors (validator data-str)]
         (if (empty? errors)
           (try
             [true (perform-insert conn db collection (processor data-str))]
             (catch com.mongodb.MongoException$DuplicateKey e
               [false {:base ["Duplicate value not allowed"]}]))
           [false errors])))))

(defn make-find-one
  [collection]
  (fn [conn db q]
    (mg/with-connection conn
      (mg/with-db db
        (if-let [result (mc/find-one-as-map collection q)]
          result)))))

(defn make-find-all
  [collection]
  (fn [conn db q]
    (mg/with-connection conn
      (mg/with-db db
        (mc/find-maps collection q)))))

(defn make-paginate
  [collection]
  (fn [conn db query limit offset]
    (mg/with-connection conn
      (mg/with-db db
        (let [count (mc/count collection query)
              documents (mq/with-collection collection
                          (mq/find query)
                          (mq/limit limit)
                          (mq/skip offset))]
          {:count count
           :documents documents})))))

(defn make-delete
  [collection]
  (fn [conn db id]
    (mg/with-connection conn
      (mg/with-db db
        (mc/remove-by-id collection id)))))