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

(defn type-fn
  "Returns a function which takes two arguments: v1 and v2. If there is one key
  r in rules such that the classes of v1 and v2 both `isa?` r, find the function
  f associated with r and return (f v1 v2). If there are multiple rules which
  satisfies this condition, any of these rules may be used. If there are none,
  (default v1 v2) is returned, or v2 if not default is supplied. h must be a
  hierarchy, and defaults to the global hierarchy if not supplied."
  ;; Should be some note telling people that they should return the same type
  ;; when merging more than two maps together, otherwise they may get funny
  ;; results.
  ([rules]
     (type-fn rules (fn [_ x] x)))
  ([rules default] ;; Q: Is there *no* way to fetch the global hierarchy?
     (let [rules (seq rules)]
       (fn [v1 v2]
         (let [c1 (class v1), c2 (class v2)]
           (loop [rs rules]
             (if (seq rs)
               (let [r (first rs), rst (rest rs)
                     k (key r), f (val r)]
                 (if (and (isa? c1 k) (isa? c2 k))
                   (f v1 v2)
                   (recur rst)))
               (default v1 v2)))))))
  ([rules default h]
     (let [rules (seq rules)]
       (fn [v1 v2]
         (let [c1 (class v1), c2 (class v2)]
           (loop [rs rules]
             (if (seq rs)
               (let [r (first rs), rst (rest rs)
                     k (key r), f (val r)]
                 (if (and (isa? h c1 k) (isa? h c2 k))
                   (f v1 v2)
                   (recur rst)))
               (default v1 v2))))))))

(defn type2-fn
  "Returns a function which takes two arguments: v1 and v2. If there is one key
  R = [r1 r2] in rules such that (isa? c1 r1 h) and (isa? c2 r2 h2) is
  satisfied, find the function f associated with R and return (f v1 v2). If
  there are multiple rules which satisfies this condition, any of these rules
  may be used. If there are none, (default v1 v2) is returned, or v2 if not
  default is supplied. h1 and h2 must be hierarchies, h1 defaults to the global
  hierarchy if not supplied, and h2 defaults to h1 if not supplied."
  ([rules]
     (type2-fn rules (fn [_ x] x)))
  ([rules default] ;; Q: Again, no way of fetching the global hierarchy?
     (let [rules (seq rules)]
       (fn [v1 v2]
         (let [c1 (class v1), c2 (class v2)]
           (loop [rs rules]
             (if (seq rs)
               (let [r (first rs), rst (rest rs)
                     k (key r), k1 (first k), k2 (second k) f (val r)]
                 (if (and (isa? c1 k1) (isa? c2 k2))
                   (f v1 v2)
                   (recur rst)))
               (default v1 v2)))))))

  ([rules default h1]
     (type2-fn rules default h1 h1))
  ([rules default h1 h2]
     (let [rules (seq rules)]
       (fn [v1 v2]
         (let [c1 (class v1), c2 (class v2)]
           (loop [rs rules]
             (if (seq rs)
               (let [r (first rs), rst (rest rs)
                     k (key r), k1 (first k), k2 (second k) f (val r)]
                 (if (and (isa? h1 c1 k1) (isa? h2 c2 k2))
                   (f v1 v2)
                   (recur rst)))
               (default v1 v2))))))))
