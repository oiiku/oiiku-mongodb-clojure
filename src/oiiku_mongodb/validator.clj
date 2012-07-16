(ns oiiku-mongodb.validator)

(defn validator-required
  [attr]
  (fn [data]
    (let [value (data attr)]
      (if (coll? value)
        (if (empty? value) [attr "can't be blank"])
        (if (clojure.string/blank? value) [attr "can't be blank"])))))

(defn validator-only-accept
  [attrs-set]
  (fn [data]
    (let [faulty-attrs (apply dissoc data attrs-set)]
      (if (not (empty? faulty-attrs))
        (str "Invalid attributes specified " (apply str (interpose ", " faulty-attrs)))))))

(defn- format-attr-errors
  [errors result]
  (if (empty? errors)
    result
    (let [spec (first errors)
          field (first spec)
          error-msg (last spec)]
      (recur
       (rest errors)
       (assoc result field (if (contains? result field)
                             (conj (result field) error-msg)
                             [error-msg]))))))

(defn- format-errors
  ([errors] (format-errors (group-by coll? errors) {}))
  ([error-groups result]
     {:attrs (format-attr-errors (error-groups true) {})
      :base (error-groups false)}))

(defn- compact-base-errors
  [errors]
  (if (nil? (errors :base))
    (dissoc errors :base)
    errors))

(defn- compact-attr-errors
  [errors]
  (if (empty? (errors :attrs))
    (dissoc errors :attrs)
    errors))

(defn make-validator
  [& validators]
  (fn [data]
    (let [errors (remove nil? (map (fn [validator] (validator data)) validators))]
      (-> errors
          (format-errors)
          (compact-base-errors)
          (compact-attr-errors)))))