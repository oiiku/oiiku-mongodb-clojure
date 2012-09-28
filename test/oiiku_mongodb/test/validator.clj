(ns oiiku-mongodb.test.validator
  (:require [oiiku-mongodb.validator :as v])
  (:use [clojure.test]))

(deftest single-validator-not-returning-any-error
  (let [validator (v/validator
                   (fn [data]))]
    (is (nil? (validator {})))
    (is (nil? (validator {:foo "bar"})))))

(deftest single-validator-returning-errors-on-base
  (let [validator (v/validator
                   (fn [data] {:base ["Has an error"]}))]
    (is (= (validator {}) {:base ["Has an error"]}))))

(deftest single-validator-returning-errors-on-attr
  (let [validator (v/validator
                   (fn [data] {:attr {:name ["Has an error"]}}))]
    (is (= (validator {}) {:attr {:name ["Has an error"]}}))))

(deftest multiple-validators-returning-errors-on-base
  (let [validator (v/validator
                   (fn [data] {:base ["Has an error"]})
                   (fn [data] {:base ["Has more errors"]}))]
    (is (= (validator {}) {:base ["Has an error" "Has more errors"]}))))

(deftest multiple-validators-returning-errors-on-base-and-attr
  (let [validator (v/validator
                   (fn [data] {:base ["Has an error"]})
                   (fn [data] {:base ["Has more errors"]})
                   (fn [data] {:attr {:name ["can't be blank"]}})
                   (fn [data] {:attr {:name ["not an e-mail"] :age ["not a number"]}})
                   (fn [data] {:attr {:email ["not a valid e-mail"]}
                               :base ["Yet another error"]}))]
    (is (= (validator {}) {:base ["Has an error" "Has more errors" "Yet another error"]
                           :attr {:name ["can't be blank" "not an e-mail"]
                                  :age ["not a number"]
                                  :email ["not a valid e-mail"]}}))))

(deftest validate-non-empty-string
  (let [validator (v/validator
                   (v/validate-non-empty-string :name))]
    (is (nil? (validator {:name "A non-empty string"})))
    (is (not (empty? (get-in (validator {:name ""}) [:attr :name]))))
    (is (not (empty? (get-in (validator {:name nil}) [:attr :name]))))
    (is (not (empty? (get-in (validator {:name 123}) [:attr :name]))))
    (is (not (empty? (get-in (validator {}) [:attr :name]))))))

(deftest validate-only-accept
  (let [validator (v/validator
                   (v/validate-only-accept :name :e-mail :age))]
    (is (nil? (validator {})))
    (is (nil? (validator {:name "foo"})))
    (is (nil? (validator {:name "foo" :e-mail "foo@bar.com"})))
    (is (nil? (validator {:age 123})))
    (is (not (empty? (:base (validator {:cake 123})))))
    (is (not (empty? (:base (validator {:cake 123 :name "foo"})))))))