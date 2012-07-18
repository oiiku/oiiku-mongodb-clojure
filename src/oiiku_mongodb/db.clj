(ns oiiku-mongodb.db
  (:require [monger.core :as mg]
            [monger.collection :as mc]
            [monger.query :as mq]
            bultitude.core)
  (:import [org.bson.types ObjectId]))

(defn create-db
  ([db-name]
     (create-db db-name {}))
  ([db-name & opts]
     (let [conn (apply monger.core/connect opts)
           db (mg/get-db conn db-name)]
       {:conn conn :db db})))

(defn get-db-name
  "Takes a create-db object and returns the name of its database."
  [db]
  (.getName (db :db)))

(defn oid
  "Creates a MongoDB ObjectId from a string."
  [id]
  (ObjectId. id))

(defn perform-ensure-index
  [db all]
  (mg/with-connection (db :conn)
    (mg/with-db (db :db)
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
  [db namespace-prefix]
  (let [nses (bultitude.core/namespaces-on-classpath :prefix namespace-prefix)]
    (doseq [ns nses]
      (if-let [indexes (ns-resolve ns 'indexes)]
        (perform-ensure-index db @indexes)))))

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
  [db collection data]
  (mg/with-connection (db :conn)
    (mg/with-db (db :db)
      (let [data (assoc data :_id (ObjectId.))
            record (mc/insert-and-return collection data)]
        (zipmap (map keyword (keys record)) (vals record))))))

(defn make-insert
  ([collection validator]
     (make-insert collection validator (fn [data] data)))
  ([collection validator processor]
     (fn [db data]
       (let [data-str (clojure.walk/stringify-keys data)
             errors (validator data-str)]
         (if (empty? errors)
           (try
             [true (perform-insert db collection (processor data-str))]
             (catch com.mongodb.MongoException$DuplicateKey e
               [false {:base ["Duplicate value not allowed"]}]))
           [false errors])))))

(defn make-find-one
  [collection]
  (fn [db q]
    (mg/with-connection (db :conn)
      (mg/with-db (db :db)
        (if-let [result (mc/find-one-as-map collection q)]
          result)))))

(defn make-find-all
  [collection]
  (fn [db q]
    (mg/with-connection (db :conn)
      (mg/with-db (db :db)
        (mc/find-maps collection q)))))

(defn make-paginate
  [collection]
  (fn [db query limit offset]
    (mg/with-connection (db :conn)
      (mg/with-db (db :db)
        (let [count (mc/count collection query)
              documents (mq/with-collection collection
                          (mq/find query)
                          (mq/limit limit)
                          (mq/skip offset))]
          {:count count
           :documents documents})))))

(defn make-delete
  [collection]
  (fn [db id]
    (mg/with-connection (db :conn)
      (mg/with-db (db :db)
        (mc/remove-by-id collection id)))))