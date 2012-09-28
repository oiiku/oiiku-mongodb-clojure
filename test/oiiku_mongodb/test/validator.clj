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

(deftest chain-validator
  (let [my-ref (ref nil)
        validator (v/validator
                   (v/chain
                    (fn [data] (if (contains? data :foo)
                                 (do (dosync (ref-set my-ref 1))
                                     (v/base-err "foo"))))
                    (fn [data] (if (contains? data :bar)
                                 (do (dosync (ref-set my-ref 2))
                                     (v/base-err "bar"))))
                    (fn [data] (if (contains? data :baz)
                                 (do (dosync (ref-set my-ref 3))
                                     (v/base-err "baz"))))))]
    (is (nil? (validator {})))
    (is (= @my-ref nil))
    (is (= (:base (validator {:foo 1})) ["foo"]))
    (is (= @my-ref 1))
    (is (= (:base (validator {:bar 1})) ["bar"]))
    (is (= @my-ref 2))
    (is (= (:base (validator {:baz 1})) ["baz"]))
    (is (= @my-ref 3))))

(deftest validate-record
  (let [dynamic-attrs-validator (v/validator
                                 (v/validate-non-empty-string :name))
        validator (v/validator
                   (v/validate-record :dynamic-attrs dynamic-attrs-validator))]
    (is (nil? (validator {})))
    (is (nil? (validator {:dynamic-attrs nil})))
    (is (nil? (validator {:dynamic-attrs {:name "foo"}})))
    (is (not (empty? (get-in (validator {:dynamic-attrs {:name ""}})
                             [:attr :dynamic-attrs :attr :name]))))))

(deftest validate-with-other-validators-before-and-after
  (let [dynamic-attrs-validator (v/validator
                                 (fn [data] (v/attr-err :name "can't be blank")))
        validator (v/validator
                   (fn [data] (v/attr-err :dynamic-attrs "will blow up"))
                   (v/validate-record :dynamic-attrs dynamic-attrs-validator)
                   (fn [data] (v/attr-err :dynamic-attrs "blew up")))]
    (is (= (validator {:dynamic-attrs {}})
           {:attr {:dynamic-attrs {:base ["will blow up" "blew up"]
                                   :attr {:name ["can't be blank"]}}}}))))