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

(defn prepare-type-rules
  ^:private
  ([cases] ;; again the darn hierarchy issue.
     (let [prepare-case
           (fn [tf]
             (let [t (key tf), f (val tf)]
               (if (vector? t)
                 (let [t1 (first t), t2 (second t)]
                   [(fn [c1 c2] (and (isa? c1 t1) (isa? c2 t2))) f])
                 [(fn [c1 c2] (and (isa? c1 t) (isa? c2 t))) f])))]
       (into {} (map prepare-case cases))))
  ([cases h]
     (let [prepare-case
           (fn [tf]
             (let [t (key tf), f (val tf)]
               (if (vector? t)
                 (let [t1 (first t), t2 (second t)]
                   [(fn [c1 c2] (and (isa? h c1 t1) (isa? h c2 t2))) f])
                 [(fn [c1 c2] (and (isa? h c1 t) (isa? h c2 t))) f])))]
       (into {} (map prepare-case cases)))))

(defn dispatch-type-fn
  ^:private
  [rules default]
  (fn [v1 v2]
    (let [c1 (class v1), c2 (class v2)]
      (loop [rs rules]
        (if (seq rs)
          (let [r (first rs), rst (rest rs)
                k (key r), f (val r)]
            (if (k c1 c2)
              (f v1 v2)
              (recur rst)))
          (default v1 v2))))))

(defn type-fn
  "Returns a function which takes two arguments: v1 and v2. If there is one key
  r in rules such that the classes of v1 and v2 both `isa?` r, find the function
  f associated with r and return (f v1 v2). If there are multiple rules which
  satisfies this condition, any of these rules may be picked. If there are none,
  (default v1 v2) is returned, or v2 if not default is supplied. h must be a
  hierarchy, and defaults to the global hierarchy if not supplied."
  ;; Should be some note telling people that they should return the same type
  ;; when merging more than two maps together, otherwise they may get funny
  ;; results.
  ([rules]
     (type-fn rules (fn [_ x] x)))
  ([rules default] ;; Q: Is there *no* way to fetch the global hierarchy?
     (let [rules (prepare-type-rules rules)]
       (dispatch-type-fn rules default)))
  ([rules default h]
     (let [rules (prepare-type-rules rules h)]
       (dispatch-type-fn rules default))))

(defn prepare-cond-seq
  "Prepares the conditional sequence."
  ^:private
  [cases]
  (let [prepare-case
        (fn [tf]
          (let [t (first tf), f (second tf)]
            (if (vector? t)
              (let [t1 (first t)
                    t2 (second t)]
                [(fn [v1 v2] (and (t1 v1) (t2 v2))) f])
              tf)))]
    (doall (map prepare-case cases))))

(defn cond-fn
  "Returns a function which takes two arguments, v1 and v2. Walks over every
  test in order: If test is a vector, will call the first element of the vector
  with v1 and the second with v2. Otherwise calls test with v1 and v2.  If any
  test (or BOTH calls if it is a vector) returns a truthy value, calls its
  respective f with v1 and v2. If none of the tests returns a truthy value,
  default is called with v1 and v2. If default is not specified, v2 is
  returned. The tests may be sent as a vector of vectors, or a map if the order
  of the tests doesn't matter."
  {:arglists '([[[test f]+]] [[[test f]+] default])}
  ([test-fs]
     (cond-fn test-fs (fn [_ x] x)))
  ([test-fs default]
     (let [test-fs (prepare-cond-seq test-fs)]
       (fn [v1 v2]
         (loop [[[test f] & r] test-fs] ;; Different fns depending on map/vec?
           (cond (nil? test)  (default v1 v2)
                 (test v1 v2) (f v1 v2)
                 :otherwise   (recur r)))))))

(defn prepare-cond3-seq
  "Prepares the 3-arity conditional sequence."
  ^:private
  [cases]
  (let [prepare-case
        (fn [tf]
          (let [t (first tf), f (second tf)]
            (if (vector? t)
              (let [[tk t1 t2] t]
                [(fn [k v1 v2] (and (tk k) (t1 v1) (t2 v2))) f])
              tf)))]
    (doall (map prepare-case cases))))

(defn cond3-fn
  "Returns a function which takes three arguments, k, v1 and v2. Walks over
  every test in order, and if any test returns a truthy value, calls its
  respective f. If none of the tests returns a truthy value, default is called
  with k, v1 and v2. The tests may sent as a vector of vectors, or a map if the
  order of the tests doesn't matter. If default is not specified, v2 is
  returned."
  {:arglists '([[[test f]+]] [[[test f]+] default])}
  ([test-fs]
     (cond3-fn test-fs (fn [_ _ x] x)))
  ([test-fs default]
     (let [test-fs (prepare-cond3-seq test-fs)]
       (fn [k v1 v2]
         (loop [[[test f] & r] test-fs] ;; Different fns depending on map/vec?
           (cond (nil? test)  (default k v1 v2)
                 (test k v1 v2) (f k v1 v2)
                 :otherwise   (recur r)))))))
