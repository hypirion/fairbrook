;; This is the wanted result (ish)
(ns name.space
  (:require [fairbrook.meta :as meta :refer [ff]]
            [fairbrook.path :as path]
            [fairbrook.type :as type]
            [fairbrook.cond :as cond] ; (as long this doesn't clash)
            [fairbrook.key :as key]
            [fairbrook.util :as util]))

(def mega-merge-fn
  (<<-
   (ff)
   (path/path-fn {[:a :cost] +, [:b :a] *})            
   (util/prep-args (fn [[a & r]] (cons (last a) r))
                   (key/key-fn {:c /}))
   (util/fn3->fn2
    (type/type-fn {clojure.lang.IPersistentSet (ff clojure.set/union),
                   clojure.lang.IPersistentVector (ff into)}))
   (util/fn3->fn2
    (cond/cond-fn [[(fn [a b] (not-every? map? [a b])) (fn [a b] b)]]))
   (path/merge-with-path-fn mega-merge-fn)))

(defn mega-merge
  [& maps]
  (meta/mono-reduce
   (path/merge-with-path-fn mega-merge-fn)
   maps))
