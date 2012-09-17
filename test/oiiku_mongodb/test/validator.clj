(ns oiiku-mongodb.test.validator
  (:require [oiiku-mongodb.validator :as v])
  (:use [clojure.test]))

(deftest validator-with-one-attribute-error
  (let [validator (v/make-validator
                   (fn [data] {"some-attr" (str "test" (data "foo"))}))
        errors (validator {"foo" "bar"})]
    (is (= errors {:attrs {"some-attr" ["testbar"]}}))))

(deftest validator-with-multiple-attribute-errors
  (let [validator (v/make-validator
                   (fn [data] {"some-attr" (str "test" (data "foo"))})
                   (fn [data] {"some-attr" (str "test2" (data "foo"))})
                   (fn [data] {"other-attr" (str "test3" (data "foo"))}))
        errors (validator {"foo" "bar"})]
    (is (= errors {:attrs {"some-attr" ["testbar" "test2bar"]
                           "other-attr" ["test3bar"]}}))))

(deftest validator-with-attr-errors-for-multiple-attrs
  (let [validator (v/make-validator
                   (fn [data] {"some-attr" "test" "lolwut" "hai"})
                   (fn [data] {"lolwut" "other"}))
        errors (validator {"foo" "bar"})]
    (is (= errors {:attrs {"some-attr" ["test"]
                           "lolwut" ["hai" "other"]}}))))

(deftest validator-with-multiple-attr-errors-for-one-attr
  (let [validator (v/make-validator
                   (fn [data] {"some-attr" "test" "lolwut" ["hai" "thar"]})
                   (fn [data] {"lolwut" "other"}))
        errors (validator {"foo" "bar"})]
    (is (= errors {:attrs {"some-attr" ["test"]
                           "lolwut" ["hai" "thar" "other"]}}))))

(deftest validator-with-one-base-error
  (let [validator (v/make-validator
                   (fn [data] (str "test" (data "foo"))))
        errors (validator {"foo" "bar"})]
    (is (= errors {:base ["testbar"]}))))