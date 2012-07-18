(ns oiiku-mongodb.test.db
  (:require [oiiku-mongodb.db :as db]
            oiiku-mongodb.test-helper)
  (:use [clojure.test])
  (:import [org.bson.types ObjectId]))

(def conn (db/create-connection))
(def db (db/create-db conn "oiiku-mongodb-tests"))

(use-fixtures
 :each
 (fn [f]
   (oiiku-mongodb.test-helper/reset-db conn db)
   (f)))

(deftest inserting
  (let [inserter (db/make-insert "my-coll" (fn [data]))
        [result inserted] (inserter conn db {:foo "bar"})]
    (is result)
    (is (= (inserted :foo) "bar"))
    (is (contains? inserted :_id))))

(deftest find-one
  (let [inserter (db/make-insert "my-coll" (fn [data]))
        [result inserted] (inserter conn db {:foo "bar"})
        finder (db/make-find-one "my-coll")
        found (finder conn db {:foo "bar"})]
    (is (= inserted found))))

(deftest find-all
  (let [inserter (db/make-insert "my-coll" (fn [data]))
        [result inserted-a] (inserter conn db {:foo "bar"})
        [result inserted-b] (inserter conn db {:foo "baz"})
        [result inserted-c] (inserter conn db {:foo "bar"})
        finder (db/make-find-all "my-coll")
        found (finder conn db {:foo "bar"})]
    (is (= (count found) 2))
    ;; TODO: Don't make the test depend on (unspecified) order
    (is (= (nth found 0) inserted-a))
    (is (= (nth found 1) inserted-c))))

(deftest find-one-non-existing
  (let [finder (db/make-find-one "my-coll")
        found (finder conn db {:foo "bar"})]
    (is (nil? found))))

(deftest deleting
  (let [inserter (db/make-insert "my-coll" (fn [data]))
        [result inserted] (inserter conn db {:foo "bar"})
        deleter (db/make-delete "my-coll")]
    (deleter conn db (inserted :_id))
    (let [finder (db/make-find-one "my-coll")
          found (finder conn db {:foo "bar"})]
      (is (nil? found)))))

(deftest querying-by-id
  (let [inserter (db/make-insert "my-coll" (fn [data]))
        [result inserted] (inserter conn db {:foo "bar"})
        finder (db/make-find-one "my-coll")
        found (finder conn db {:_id (inserted :_id)})]
    (is (= inserted found))))

(deftest querying-in-by-id
  (let [inserter (db/make-insert "my-coll" (fn [data]))
        [result inserted-a] (inserter conn db {})
        [result inserted-b] (inserter conn db {})
        [result inserted-c] (inserter conn db {})
        finder (db/make-find-all "my-coll")
        found (finder conn db {:_id {:$in [(inserted-a :_id) (inserted-c :_id)]}})]
    (is (= (count found) 2))
    ;; TODO: Don't make the test depend on (unspecified) order
    (is (= (nth found 0) inserted-a))
    (is (= (nth found 1) inserted-c))))

(deftest querying-ne-by-id
  (let [inserter (db/make-insert "my-coll" (fn [data]))
        [result inserted-a] (inserter conn db {})
        [result inserted-b] (inserter conn db {})
        finder (db/make-find-all "my-coll")
        found (finder conn db {:_id {:$ne (inserted-a :_id)}})
        ]
    (is (= (count found) 1))
    (is (= (nth found 0) inserted-b))))

(deftest failing-validation-with-errors
  (let [inserter (db/make-insert
                  "my-coll"
                  (fn [data] {:attrs [(str "test" (data "foo"))]}))
        [result inserted] (inserter conn db {"foo" "bar"})]
    (is (not result))
    (is (= inserted {:attrs ["testbar"]}))))

(deftest validator-gets-attrs-with-stringified-keys
  (let [inserter (db/make-insert
                  "my-coll"
                  (fn [data] {:attrs [(str "test" (data "foo"))]}))
        [result inserted] (inserter conn db {:foo "bar"})]
    (is (not result))
    (is (= inserted {:attrs ["testbar"]}))))

(deftest processing-on-insert
  (let [inserter (db/make-insert
                  "my-coll"
                  (fn [data])
                  (fn [data] (assoc data :otherfoo (str (data "foo") "test"))))
        [result inserted] (inserter conn db {:foo "bar"})]
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
        [result inserted] (inserter conn db {:foo {"test" 123}})]
    (is result)
    (is (= (inserted :foo) {"test" 123}))))

(deftest perform-ensure-index
  (db/perform-ensure-index conn db {"my-coll" [{:foo 1} {:unique true}]})
  (let [inserter (db/make-insert "my-coll" (fn [data]))
        [result-a inserted-a] (inserter conn db {:foo "test"})
        [result-b inserted-b] (inserter conn db {:foo "test"})]
    (is result-a)
    (is (not result-b))))