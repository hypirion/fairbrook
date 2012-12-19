(ns fairbrook.rule)

(defn rule-fn
  "Returns a function which takes three arguments: r, v1 and v2. If r is a key
  in rules, applies its associated value on v1 and v2. If r is not contained
  within rules, then a default function taking r, v1 and v2 as arguments will be
  called. If no default function is given, will work as a normal merge when r is
  not a key in rules."
  ([rules]
     (rule-fn rules (fn [_ _ x] x)))
  ([rules default]
     (fn [r v1 v2]
       (if-let [rule (get rules r)]
         (rule v1 v2)
         (default r v1 v2)))))
