(ns fairbrook.key
  (:require [fairbrook.rule :as rule]))

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
    (reduce key-merge nil maps)))

(defn merge-with-key-fn
  "Returns a function taking two maps m1 and m2. When called, merges the two
  maps, and if a key collision occurs, associates the key k with
  (f k v1 v2) in the resulting map, where v1 and v2 are the values associated
  with k in m1 and m2."
  [f]
  (fn [m1 m2]
    (merge-with-key f m1 m2)))

(defn key-merge-with
  "A merge function which handles key collisions based on the value of the
  key. If a key occurs in more than one map and the key is contained within
  `rules`, the mapping(s) from the latter will be combined with the mapping in
  the result by calling ((get rules key) val-in-result val-in-latter). Will
  otherwise work as merge-with where `f` is the merge function if a collision
  occurs."
  [rules f & maps]
  (let [f* (fn [_ a b] (f a b))]
    (apply merge-with-key (rule/rule-fn rules f*) maps)))

(defn key-merge
  "A merge function which merges based on keys. If a key occurs in more than one
  map and the key is contained within `rules`, the mapping(s) from the latter
  will be combined with the mapping in the result by calling
  (f val-in-result val-in-latter). Will otherwise be the mapping from the
  latter."
  [rules & maps]
  (apply merge-with-key (rule/rule-fn rules) maps))
