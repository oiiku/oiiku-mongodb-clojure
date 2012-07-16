(ns oiiku-mongodb.test-helper
  (:require monger.core
            monger.collection))

(defn remove-all-collections
  []
  (doseq [coll-name (remove
                     (fn [coll-name]
                       (re-find #"system" coll-name))
                     (.toArray (.getCollectionNames monger.core/*mongodb-database*)))]
    (monger.collection/drop coll-name)))