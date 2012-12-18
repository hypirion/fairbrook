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

(defn key-fn
  "Returns a function which takes three arguments: k, v1 and v2. If k is a key
  in rules, applies its associated value on v1 and v2. If k is not contained
  within rules, then a default function taking k, v1 and v2 as arguments will be
  called. If no default function is given, will work as a normal merge when k is
  not a key in rules."
  ([rules]
     (key-fn rules (fn [_ _ x] x)))
  ([rules default]
     (fn [k v1 v2]
       (if-let [rule (get rules k)]
         (rule v1 v2)
         (default k v1 v2)))))

(defn key-merge-with
  "A merge function which handles key collisions based on the value of the
  key. If a key occurs in more than one map and the key is contained within
  `rules`, the mapping(s) from the latter will be combined with the mapping in
  the result by calling ((get rules key) val-in-result val-in-latter). Will
  otherwise work as merge-with where `f` is the merge function if a collision
  occurs."
  [rules f & maps]
  (let [f' (fn [_ a b] (f a b))]
    (apply merge-with-key (key-fn rules f') maps)))

(defn key-merge
  "A merge function which merges based on keys. If a key occurs in more than one
  map and the key is contained within `rules`, the mapping(s) from the latter
  will be combined with the mapping in the result by calling
  (f val-in-result val-in-latter). Will otherwise be the mapping from the
  latter."
  [rules & maps]
  (apply merge-with-key (key-fn rules) maps))
