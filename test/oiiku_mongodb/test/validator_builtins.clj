(ns oiiku-mongodb.test.validator-builtins
  (:require [oiiku-mongodb.validator :as v])
  (:use [clojure.test]))

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