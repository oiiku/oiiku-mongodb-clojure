(ns oiiku-mongodb.test.db
  (:require [oiiku-mongodb.db :as db]
            oiiku-mongodb.test-helper)
  (:use [clojure.test])
  (:import [org.bson.types ObjectId]))

(def db (db/create-db "oiiku-mongodb-tests"))

(use-fixtures
 :each
 (fn [f]
   (oiiku-mongodb.test-helper/reset-db db)
   (f)))

(deftest inserting
  (let [inserter (db/make-insert "my-coll" (fn [data]))
        [result inserted] (inserter db {:foo "bar"})]
    (is result)
    (is (= (inserted :foo) "bar"))
    (is (contains? inserted :_id))))

(deftest find-one
  (let [inserter (db/make-insert "my-coll" (fn [data]))
        [result inserted] (inserter db {:foo "bar"})
        finder (db/make-find-one "my-coll")
        found (finder db {:foo "bar"})]
    (is (= inserted found))))

(deftest find-all
  (let [inserter (db/make-insert "my-coll" (fn [data]))
        [result inserted-a] (inserter db {:foo "bar"})
        [result inserted-b] (inserter db {:foo "baz"})
        [result inserted-c] (inserter db {:foo "bar"})
        finder (db/make-find-all "my-coll")
        found (finder db {:foo "bar"})]
    (is (= (count found) 2))
    ;; TODO: Don't make the test depend on (unspecified) order
    (is (= (nth found 0) inserted-a))
    (is (= (nth found 1) inserted-c))))

(deftest find-one-non-existing
  (let [finder (db/make-find-one "my-coll")
        found (finder db {:foo "bar"})]
    (is (nil? found))))

(deftest deleting
  (let [inserter (db/make-insert "my-coll" (fn [data]))
        [result inserted] (inserter db {:foo "bar"})
        deleter (db/make-delete "my-coll")]
    (deleter db (inserted :_id))
    (let [finder (db/make-find-one "my-coll")
          found (finder db {:foo "bar"})]
      (is (nil? found)))))

(deftest querying-by-id
  (let [inserter (db/make-insert "my-coll" (fn [data]))
        [result inserted] (inserter db {:foo "bar"})
        finder (db/make-find-one "my-coll")
        found (finder db {:_id (inserted :_id)})]
    (is (= inserted found))))

(deftest querying-in-by-id
  (let [inserter (db/make-insert "my-coll" (fn [data]))
        [result inserted-a] (inserter db {})
        [result inserted-b] (inserter db {})
        [result inserted-c] (inserter db {})
        finder (db/make-find-all "my-coll")
        found (finder db {:_id {:$in [(inserted-a :_id) (inserted-c :_id)]}})]
    (is (= (count found) 2))
    ;; TODO: Don't make the test depend on (unspecified) order
    (is (= (nth found 0) inserted-a))
    (is (= (nth found 1) inserted-c))))

(deftest querying-ne-by-id
  (let [inserter (db/make-insert "my-coll" (fn [data]))
        [result inserted-a] (inserter db {})
        [result inserted-b] (inserter db {})
        finder (db/make-find-all "my-coll")
        found (finder db {:_id {:$ne (inserted-a :_id)}})
        ]
    (is (= (count found) 1))
    (is (= (nth found 0) inserted-b))))

(deftest failing-validation-with-errors
  (let [inserter (db/make-insert
                  "my-coll"
                  (fn [data] {:attrs [(str "test" (data :foo))]}))
        [result inserted] (inserter db {:foo "bar"})]
    (is (not result))
    (is (= inserted {:attrs ["testbar"]}))))

(deftest validator-gets-attrs-with-stringified-keys
  (let [inserter (db/make-insert
                  "my-coll"
                  (fn [data] {:attrs [(str "test" (data :foo))]}))
        [result inserted] (inserter db {:foo "bar"})]
    (is (not result))
    (is (= inserted {:attrs ["testbar"]}))))

(deftest processing-on-insert
  (let [inserter (db/make-insert
                  "my-coll"
                  (fn [data])
                  (fn [data] (assoc data :otherfoo (str (data :foo) "test"))))
        [result inserted] (inserter db {:foo "bar"})]
    (is result)
    (is (= (inserted :otherfoo) "bartest"))
    (is (contains? inserted :_id))))

(deftest serializing
  (let [oid-a (ObjectId.)
        oid-b (ObjectId.)
        oid-c (ObjectId.)
        record {:_id oid-a :users [oid-b oid-c] :foo "bar"}
        res (db/serialize record)]
    (is (= res {:id (.toString oid-a)
                :users [(.toString oid-b) (.toString oid-c)]
                :foo "bar"}))))

(deftest nested-map-with-string-keys
  (let [inserter (db/make-insert "my-coll" (fn [data]))
        [result inserted] (inserter db {:foo {"test" 123}})]
    (is result)
    (is (= (inserted :foo) {"test" 123}))))

(deftest perform-ensure-index
  (db/perform-ensure-index db {"my-coll" [{:foo 1} {:unique true}]})
  (let [inserter (db/make-insert "my-coll" (fn [data]))
        [result-a inserted-a] (inserter db {:foo "test"})
        [result-b inserted-b] (inserter db {:foo "test"})]
    (is result-a)
    (is (not result-b))))

(deftest getting-name
  (is (= (db/get-db-name db) "oiiku-mongodb-tests")))

(deftest updating
  (let [inserter (db/make-insert "my-coll" (fn [data]))
        updater (db/make-update-by-id "my-coll")
        finder (db/make-find-one "my-coll")
        [valid inserted] (inserter db {:foo "bar"})]
    (updater db (inserted :_id) {:foo "baz"})
    (is (= ((finder db {:_id (inserted :_id)}) :foo) "baz"))
    (updater db (inserted :_id) {:test 123})
    (is (= ((finder db {:_id (inserted :_id)}) :test) 123))))

(deftest save-by-id
  (let [inserter (db/make-insert "my-coll" (fn [data]))
        saver (db/make-save-by-id "my-coll" (fn [data]))
        [valid inserted] (inserter db {:foo "bar"})
        [valid updated] (saver db (inserted :_id) {:bar "baz"})]
    (is (= valid true))
    (is (= updated {:bar "baz" :_id (inserted :_id)}))))

(deftest save-by-id-with-processor
  (let [inserter (db/make-insert "my-coll" (fn [data]))
        saver (db/make-save-by-id "my-coll"
                                  (fn [data])
                                  (fn [data] {:bar "baz" :foo (data :foo)}))
        [valid inserted] (inserter db {})
        [valid updated] (saver db (inserted :_id) {:foo "bar"})]
    (is (= valid true))
    (is (= updated {:foo "bar" :bar "baz" :_id (inserted :_id)}))))

(deftest if-valid-oid-oid-is-valid
  (let [oid (ObjectId.)
        result (db/if-valid-oid
                oid
                (fn [object-id] object-id)
                (fn [] "nope" ))]
    (is (identical? result oid))))

(deftest if-valid-oid-oid-is-invalid
  (let [result (db/if-valid-oid
                "123"
                (fn [object-id] object-id)
                (fn [] "nope" ))]
    (is (= result "nope"))))

(deftest if-valid-oid-oid-is-valid-but-then-is-nil
(let [oid (ObjectId.)
      result (db/if-valid-oid
              oid
              (fn [object-id])
              (fn [] "nope" ))]
    (is (= result "nope"))))
