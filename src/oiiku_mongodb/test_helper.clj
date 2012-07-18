(ns oiiku-mongodb.test-helper
  (:require monger.core
            monger.collection))

(defn remove-all-collections
  [conn db]
  (doseq [coll-name (remove
                     (fn [coll-name]
                       (re-find #"system" coll-name))
                     (.toArray (.getCollectionNames db)))]
    (monger.core/with-connection conn
      (monger.core/with-db db
        (monger.collection/drop coll-name)))))