(ns oiiku-mongodb.test.validator-builtins
  (:require [oiiku-mongodb.validator :as v])
  (:use [clojure.test]))

(deftest validate-non-empty-string
  (let [validator (v/validator
                   (v/validate-non-empty-string :name))]
    (is (nil? (validator {:name "A non-empty string"})))
    (is (not (empty? (get-in (validator {:name ""}) [:attrs :name]))))
    (is (not (empty? (get-in (validator {:name nil}) [:attrs :name]))))
    (is (not (empty? (get-in (validator {:name 123}) [:attrs :name]))))
    (is (not (empty? (get-in (validator {}) [:attrs :name]))))))

(deftest validate-only-accept
  (let [validator (v/validator
                   (v/validate-only-accept :name :e-mail :age))]
    (is (nil? (validator {})))
    (is (nil? (validator {:name "foo"})))
    (is (nil? (validator {:name "foo" :e-mail "foo@bar.com"})))
    (is (nil? (validator {:age 123})))
    (is (not (empty? (:base (validator {:cake 123})))))
    (is (not (empty? (:base (validator {:cake 123 :name "foo"})))))))


(deftest validate-presence
  (let [validator (v/validator
                   (v/validate-presence :name))]
    (is (nil? (validator {:name nil})))
    (is (nil? (validator {:name false})))
    (is (nil? (validator {:name 0})))
    (is (nil? (validator {:name #{}})))
    (is (nil? (validator {:name '()})))
    (is (nil? (validator {:name {}})))
    (is (not (empty? (get-in (validator {}) [:attrs :name]))))
    (is (not (empty? (get-in (validator {:cake 123}) [:attrs :name]))))))