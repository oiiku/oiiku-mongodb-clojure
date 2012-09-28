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
        {:attr {attr (assoc errors :_type :record-errors)}}))))

(defn- map-without-values
  [pred map]
  (persistent!
   (reduce
    (fn [result entry]
      (let [key (first entry)
            value (last entry)]
        (if (pred value)
          (assoc! result key value)
          result)))
    (transient {})
    map)))

(defn validate-record-list
  [attr validator]
  (fn [data]
    (if-let [records (attr data)]
      (if (and (sequential? records)
               (not (empty? records)))
        (let [all-errors-map
              (reduce
               (fn [result idx]
                 (assoc result idx (validator (nth records idx))))
               {}
               (range (count records)))
              errors-map (map-without-values #(not (nil? %)) all-errors-map)]
          (if (not (empty? errors-map))
            {:attr {attr (assoc errors-map :_type :record-list-errors)}}))))))

(defn validate-non-empty-string
  [attr]
  (fn [data]
    (if-let [value (attr data)]
      (if (or (not (= (class value) String))
              (empty? (.trim value)))
        (attr-err attr "must contain something other than blank spaces"))
      (attr-err attr "must be non-nil"))))

(defn validate-presence
  [attr]
  (fn [data]
    (if (not (contains? data attr))
      (attr-err attr "must be set"))))

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

(defmulti concat-record-type-errors (fn [x y] (:_type y)))
(defmethod concat-record-type-errors :record-errors
  [x y] (assoc (dissoc y :_type) :base x))
(defmethod concat-record-type-errors :record-list-errors
  [x y] {:attr (dissoc y :_type) :base x})

(defn- concat-attr-error
  [x y]
  (if (and (not (map? x)) (map? y))
    (concat-record-type-errors x y)
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