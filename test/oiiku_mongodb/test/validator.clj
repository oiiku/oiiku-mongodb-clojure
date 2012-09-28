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
                   (fn [data] {:attrs {:name ["Has an error"]}}))]
    (is (= (validator {}) {:attrs {:name ["Has an error"]}}))))

(deftest multiple-validators-returning-errors-on-base
  (let [validator (v/validator
                   (fn [data] {:base ["Has an error"]})
                   (fn [data] {:base ["Has more errors"]}))]
    (is (= (validator {}) {:base ["Has an error" "Has more errors"]}))))

(deftest multiple-validators-returning-errors-on-base-and-attr
  (let [validator (v/validator
                   (fn [data] {:base ["Has an error"]})
                   (fn [data] {:base ["Has more errors"]})
                   (fn [data] {:attrs {:name ["can't be blank"]}})
                   (fn [data] {:attrs {:name ["not an e-mail"] :age ["not a number"]}})
                   (fn [data] {:attrs {:email ["not a valid e-mail"]}
                               :base ["Yet another error"]}))]
    (is (= (validator {}) {:base ["Has an error" "Has more errors" "Yet another error"]
                           :attrs {:name ["can't be blank" "not an e-mail"]
                                  :age ["not a number"]
                                  :email ["not a valid e-mail"]}}))))

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
                             [:attrs :dynamic-attrs :attrs :name]))))))

(deftest validate-record-with-other-validators-before-and-after
  (let [dynamic-attrs-validator (v/validator
                                 (fn [data] (v/attr-err :name "can't be blank")))
        validator (v/validator
                   (fn [data] (v/attr-err :dynamic-attrs "will blow up"))
                   (v/validate-record :dynamic-attrs dynamic-attrs-validator)
                   (fn [data] (v/attr-err :dynamic-attrs "blew up")))]
    (is (= (validator {:dynamic-attrs {}})
           {:attrs {:dynamic-attrs {:base ["will blow up" "blew up"]
                                   :attrs {:name ["can't be blank"]}}}}))))

(deftest validate-record-list
  (let [auth-token-validator (v/validator
                              (v/validate-non-empty-string :token))
        validator (v/validator
                   (v/validate-record-list :auth-tokens auth-token-validator))]
    (is (nil? (validator {})))
    (is (nil? (validator {:auth-tokens []})))
    (is (nil? (validator {:auth-tokens [{:token "123"}]})))
    (is (not (empty? (get-in (validator {:auth-tokens [{:token ""}]})
                             [:attrs :auth-tokens 0 :attrs :token]))))))

(deftest validate-record-list-with-other-validators-before-and-after
  (let [auth-token-validator (v/validator
                              (fn [data] (v/attr-err :token "is too short")))
        validator (v/validator
                   (fn [data] (v/attr-err :auth-tokens "will blow up"))
                   (v/validate-record-list :auth-tokens auth-token-validator)
                   (fn [data] (v/attr-err :auth-tokens "blew up")))]
    (is (= (validator {:auth-tokens [{}]})
           {:attrs {:auth-tokens {:base ["will blow up" "blew up"]
                                 :attrs {0 {:attrs {:token ["is too short"]}}}}}}))))