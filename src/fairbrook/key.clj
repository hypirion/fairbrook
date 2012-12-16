(ns fairbrook.key)

(defn merge-with-key
  "As merge-with, but adds the key to the function call: If a key occurs in more
  than one map, the mapping(s) will be combined by calling
  (f key val-in-result val-in-latter)."
  [f & maps]
  (let [merge-entry (fn [m e]
                      (let [k (key e), v (val e)]
                        (if (contains? m k)
                          (assoc m k (f k (get m k) v))
                          (assoc m k v))))
        key-merge (fn [m1 m2]
                    (reduce merge-entry (or m1 {}) (seq m2)))]
    (reduce key-merge maps)))

(defn key-merge-with
  "A merge function which handles key collisions based on their value. If a key
  occurs in more than one map and the key is contained within `rules`, the
  mapping(s) from the latter will be combined with the mapping in the result by
  calling ((get rules key) val-in-result val-in-latter). Will otherwise work as
  merge-with where `f` is the merge function if a collision occurs."
  [rules f & maps]
  (let [merge-entry (fn [m e]
                      (let [k (key e) v (val e)]
                        (if (contains? m k)
                          (if-let [rule (rules k)]
                            (assoc m k (rule (get m k) v))
                            (assoc m k (f (get m k) v)))
                          (assoc m k v))))
        kmerge (fn [m1 m2]
                 (reduce merge-entry (or m1 {}) (seq m2)))]
    (reduce kmerge maps)))

(defn key-merge
  "A merge function which merges based on keys. If a key occurs in more than one
  map and the key is contained within `rules`, the mapping(s) from the latter
  will be combined with the mapping in the result by calling
  (f val-in-result val-in-latter). Will otherwise be the mapping from the
  latter."
  [rules & maps]
  (let [merge-entry (fn [m e]
                      (let [k (key e) v (val e)]
                        (if-let [rule (and (contains? m k) (rules k))]
                          (assoc m k (rule (get m k) v))
                          (assoc m k v))))
        kmerge (fn [m1 m2]
                 (reduce merge-entry (or m1 {}) (seq m2)))]
    (reduce kmerge maps)))
