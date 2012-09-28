(ns oiiku-mongodb.validator
  (:require clojure.set))

(defn attr-err
  [attr err]
  {:attr {attr [err]}})

(defn validate-non-empty-string
  [attr]
  (fn [data]
    (if-let [value (attr data)]
      (if (or (not (= (class value) String))
              (empty? (.trim value)))
        (attr-err attr "must contain something other than blank spaces"))
      (attr-err attr "must be non-nil"))))

(defn validate-only-accept
  [& attrs]
  (let [attrs (set attrs)]
    (fn [data]
      (let [provided-attrs (set (keys data))
            extraneous-attrs (clojure.set/difference attrs provided-attrs)]
        (if (not (empty? extraneous-attrs))
          (str "Unknown attributes " (apply str (interpose ", " extraneous-attrs))))))))

(defn- merge-base-errors
  [result error]
  (if (contains? error :base)
    (assoc result :base (into (:base result) (:base error)))
    result))

(defn- merge-attr-errors
  [result error]
  (if (contains? error :attr)
    (assoc result :attr (merge-with concat (:attr result) (:attr error)))
    result))

(defn- merge-error
  [result error]
  (-> result
      (merge-base-errors error)
      (merge-attr-errors error)))

(defn validator
  "Creates a new validator."
  [& validators]
  (fn [data]
    (let [errors (remove nil? (map #(% data) validators))]
      (if (> (count errors) 0)
        (reduce merge-error errors)))))