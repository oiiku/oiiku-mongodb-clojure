(ns oiiku-mongodb.test-helper
  (:require monger.core
            monger.collection))

(defn reset-db
  "The details of how the database is reset are private. You should not assume
   that the current implementation won't change in the future, all you should
   assume is that after running this task, the database is as good as new."
  [db]
  (doseq [coll-name (remove
                     (fn [coll-name]
                       (re-find #"system" coll-name))
                     (.toArray (.getCollectionNames (:db db))))]
    (monger.core/with-connection (:conn db)
      (monger.core/with-db (:db db)
        (monger.collection/drop coll-name)))))