(ns oiiku-mongodb.db
  (:require [monger.core :as mg]
            [monger.collection :as mc]
            [monger.query :as mq]
            monger.conversion
            bultitude.core
            clojure.walk)
  (:import [org.bson.types ObjectId]))

(defmacro when-valid
  "Takes two arguments: a statement that returns nil or any kind of truthy
   error message, and a statement that performs database insertion.

   Returns [true {data ...}] or [false {validation errors ...}].

   Example:

     (def insert
       (let [inserter-fn (db/make-insert \"users\")]
         (fn [db data]
           (when-valid (validator data)
                       (inserter-fn db data)))))"
  [validate do-db]
  `(if-let [errors# ~validate]
     [false errors#]
     [true ~do-db]))

(defmacro
  ^{:private true}
  with-db
  [db & body]
  `(mg/with-connection (:conn ~db)
     (mg/with-db (:db ~db)
       (do ~@body))))

(defrecord Db [conn db])

(defn create-db
  ([db-name]
     (create-db db-name {}))
  ([db-name & opts]
     (let [conn (apply monger.core/connect opts)
           db (mg/get-db conn db-name)]
       (Db. conn db))))

(defn get-db-name
  "Takes a create-db object and returns the name of its database."
  [db]
  (.getName (:db db)))

(defmulti oid type)
(defmethod oid String [id]
  (if (ObjectId/isValid id)
    (ObjectId. id)))
(defmethod oid ObjectId [id] id)

(defn perform-ensure-index
  [db all]
  (with-db db
    (doseq [[collection indexes] all]
      (mc/ensure-index
       collection
       (nth indexes 0)
       (nth indexes 1 {})))))

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

(defn- perform-save
  "Will save (create or update/replace) a document, depending on whether
   or not an id is passed. Returns the result with the attributes as symbols,
   not strings."
  ([db collection data]
     (with-db db
       (let [record (mc/save-and-return collection data)]
         (zipmap (map keyword (keys record)) (vals record)))))
  ([db collection data object-id]
     (perform-save db collection (assoc data :_id object-id))))

(defn- perform-update
  ([db collection criteria data]
     (perform-update db collection criteria data false))
  ([db collection criteria data upsert]
     (with-db db
       (mc/update collection criteria data :upsert upsert))))

(defn make-update
  [collection]
  (fn [db criteria data]
    (perform-update db collection criteria data)))

(defn- perform-upsert
  [db collection criteria data]
  (perform-update db collection criteria data true))

(defn make-upsert
  [collection]
  (fn [db criteria data]
    (perform-upsert db collection criteria data)))

(defn make-insert
  [collection]
  (fn [db data]
    (perform-save db collection data)))

(defmacro duplicate-key-guard
  [& body]
  `(try
     (do ~@body)
     (catch com.mongodb.MongoException$DuplicateKey e#
       ::duplicate-key)))

(defn make-save-by-id
  [collection]
  (fn [db id data]
    (perform-save db collection data (oid id))))

(defn make-update-by-id
  [collection]
  (fn [db id data]
    (with-db db
      (mc/update-by-id collection id data))))

(defn make-find-one
  [collection]
  (fn [db q]
    (with-db db
      (if-let [result (mc/find-one-as-map collection q)]
        result))))

(defn make-find-all-with-fields
 )

(defn make-find-all
  ([collection]
     (fn [db q]
       (with-db db
         (mc/find-maps collection q))))
  ([collection fields]
     (fn [db q fields]
       (with-db db
         (mc/find-maps collection q fields)))))

(defn make-paginate
  [collection]
  (fn [db query limit offset]
    (with-db db
      (let [count (mc/count collection query)
            documents (mq/with-collection collection
                        (mq/find query)
                        (mq/limit limit)
                        (mq/skip offset))]
        {:count count
         :documents documents}))))

(defn make-count
  [collection]
  (defn inner-make-count
    ([db]
       (with-db db
         (mc/count collection)))
    ([db criteria]
       (with-db db
         (mc/count collection criteria)))))

(defn make-delete
  [collection]
  (fn [db id]
    (with-db db
      (mc/remove-by-id collection (oid id)))))

(defn drop-database
  [db]
  (.dropDatabase (:conn db) (get-db-name db)))

(defn if-valid-oid
  "Executes 'then' if oid is valid. Executes 'else' if oid is invalid or
   'then' returns a falsy value."
  [the-oid then else]
  (if-let [the-oid (oid the-oid)]
    (or (then the-oid) (else))
    (else)))
