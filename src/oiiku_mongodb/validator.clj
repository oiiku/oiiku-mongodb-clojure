(ns oiiku-mongodb.validator)

(defn validator-required
  [attr]
  (fn [data]
    (let [value (data attr)]
      (if (coll? value)
        (if (empty? value) {attr "can't be blank"})
        (if (clojure.string/blank? value) {attr "can't be blank"})))))

(defn validator-only-accept
  [attrs-set]
  (fn [data]
    (let [faulty-attrs (apply dissoc data attrs-set)]
      (if (not (empty? faulty-attrs))
        (str "Invalid attributes specified " (apply str (interpose ", " faulty-attrs)))))))

(defn- format-attr-map-errors
  [result attr-map-errors]
  (if (empty? attr-map-errors)
    result
    (let [error-map (first attr-map-errors)
          attr (first error-map)
          error (last error-map)
          error-list (result attr [])]
      (recur
       (assoc result attr (if (coll? error)
                            (into error-list error)
                            (conj error-list error)))
       (dissoc attr-map-errors attr)))))

(defn- format-attr-errors
  "Thurns this:
     [{\"some-attr\" \"test\" \"lolwut\" [\"hai\" \"thar\"]} {\"lolwut\" \"other\"}]

   into this:
     {\"some-attr\" {\"base\" [\"test\"]} \"lolwut\" {\"base\" [\"hai\" \"thar\" \"other\"]}}"
  ([errors] (format-attr-errors errors {}))
  ([errors result]
     (if (empty? errors)
       (zipmap (keys result) (map (fn [r] {"base" r}) (vals result)))
       (recur (rest errors) (format-attr-map-errors result (first errors))))))

(defn- format-errors
  [errors]
  (let [error-groups (group-by map? errors)]
    {:attrs (format-attr-errors (error-groups true))
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

(defn chain
  [& validators]
  (fn
    chainer
    ([data] (chainer data validators))
    ([data validators]
       (if (not (empty? validators))
         (if-let [error ((first validators) data)]
           error
           (recur data (rest validators)))))))