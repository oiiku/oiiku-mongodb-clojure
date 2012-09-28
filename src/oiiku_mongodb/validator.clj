(ns oiiku-mongodb.validator
  (:require clojure.set))

(defn attr-err
  [attr err]
  {:attr {attr [err]}})

(defn base-err
  [err]
  {:base [err]})

(defn chain
  [& validators]
  (fn [data]
    (some #(% data) validators)))

(defn validate-record
  [attr validator]
  (fn [data]
    (if-let [record (attr data)]
      (if-let [errors (validator record)]
        {:attr {attr errors}}))))

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
            extraneous-attrs (clojure.set/difference provided-attrs attrs)]
        (if (not (empty? extraneous-attrs))
          (base-err (str "Unknown attributes: "
                         (apply str (interpose ", " (map name extraneous-attrs))))))))))

(defn- merge-base-errors
  [result error]
  (if-let [new-error (:base error)]
    (assoc result :base (into (:base result) new-error))
    result))

(defn- concat-attr-error
  [x y]
  (if (and (not (map? x)) (map? y))
    (assoc y :base x)
    (if (map? x)
      (assoc x :base (concat (:base x) y))
      (concat x y))))

(defn- merge-attr-errors
  [result error]
  (if-let [new-error (:attr error)]
    (let [existing-error (:attr result)]
      (assoc result :attr (merge-with concat-attr-error existing-error new-error)))
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