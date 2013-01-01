# Example usage

For all examples, the following setup is used:

```clj
(ns some-namespace
  (:require [fairbrook.path :as path]
            [fairbrook.key :as key]
            [fairbrook.rule :as rule]
            [fairbrook.util :as u :refer [<<-]]
            [fairbrook.meta :as meta :refer [ff fm]]))
```

## Merge based on 2-ary conditionals

### Basic merge with `cond-fn`

A merge which makes a vector of two elements if they are equal, otherwise does
the "default" merge operation: Keeping the rightmost element.

```clj
(def merge-fn
  (rule/cond-fn [[(fn [a b] (= a b)) (fn [a b] [a b])]]))

(merge-with merge-fn {:a 1, :b 2, :c 5} {:a 2, :b 2})
#_=> {:a 2, :b [2 2], :c 5}

(merge-with merge-fn {:a 1, :b 4} {:a 2, :c 8} {:a 1, :b 6})
#_=> {:a 1, :b 6, :c 8}
```

A merge which makes a vector of two elements if they are equal, otherwise keeps
the resulting (left) value.

```clj
(def default-fn (fn [a b] a))

(def merge-fn
  (rule/cond-fn [[(fn [a b] (= a b)) (fn [a b] [a b])]]
    default-fn))

(merge-with merge-fn {:a 1, :b 2} {:a 2, :b 2})
#_=> {:a 1, :b [2 2]}

(merge-with merge-fn {:a 1, :b 4} {:a 2} {:a 1, :b 6})
#_=> {:a [1 1], :b 4}
```

With utility fns and non-anonymous functions.

```clj
(def default-fn u/left)

(def merge-fn
  (rule/cond-fn [[= vector]]
    default-fn))
;; Same functionality
```

With `default-fn` "injected" into `cond-fn`:

```clj
(def merge-fn
  (rule/cond-fn [[= vector]] u/left))
;; Same functionality
```

### Merge using `and-fn` and `or-fn`

`and-fn`'s and `or-fn`'s functionality

```clj
(def both-maps? (u/and-fn map? map?))
;;           == (fn [a b] (and (map? a) (map? b)))

(def kw+maps? (u/and-fn keyword? map? map?))
;;          == (fn [k a b] (and (keyword? k) (map? a) (map? b)))

(def some-map? (u/or-fn map? map?))
;;          == (fn [a b] (or (map? a) (map? b)))

(def or-kw-map-map? (u/or-fn keyword? map? map?))
;;               == (fn [k a b] (or (keyword? k) (map? a) (map? b)))
```

#### `or-fn` usage

Add element to `vector`, or create one if none of the elements are:

```clj
(def merge-fn
  (rule/cond-fn [[(u/or-fn vector? (constantly false)) conj]
                 [(u/or-fn (constantly false) vector?) #(conj %2 %1)]]
    vector))

(merge-with merge-fn {:a 1 :b [2] :c 3} {:a 4 :b 5 :c [6]})
#_=> {:a [1 4], :b [2 5], :c [6 3]}

(merge-with merge-fn {:a [2 3]} {:a [4 5]})
#_=> {:a [2 3 [4 5]]} ;; Order matters, first rule is first tested
```

#### `and-fn` usage

`u/_` is the same as writing `(constantly true)`.

```clj
(def merge-fn
  (rule/cond-fn [[(u/and-fn vector? u/_) conj]
                 [(u/and-fn u/_ vector?) #(conj %2 %1)]]
    vector))

;; Same functionality as the or-function.
```

### Merge with `and-fn` vector abbrev

`u/and-fn` is very often used in `cond-fn`. As such, the abbreviation `[f g]`
instead of a function is expanded into `(u/and-fn f g)` if it is a condition:

```clj
(def merge-fn
  (rule/cond-fn [[[vector? u_/] conj]
                 [[u/_ vector?] #(conj %2 %1)]]
    vector))

;; Same functionality as above
```

### Merge with map instead of vector

When the ordering of the conditions are irrelevant for the outcome, you may use
a map instead of vectors. The following function keeps the first non-nil element
it sees.

```clj
(def merge-fn
  (rule/cond-fn {[nil? nil?] u/right,
                 [boolean nil?] u/left,
                 [nil? boolean] u/right}
    u/left))
```

### `type-fn`

Dispatch on type. Like `cond-fn`, but accepts maps only. Undefined ordering.

This function adds elements into a set, where it is assumed that at most one of
them are a set, and not both.

```clj
(import 'clojure.lang.IPersistentSet)

(def merge-fn
  (rule/type-fn {[IPersistentSet Object] conj,
                 [Object IPersistentSet] #(conj %2 %1)}
    hash-set))

(merge-with merge-fn {:a #{1}, :b 5, :c 3} {:a 4, :b #{2}, :c 6})
#_=> {:a #{1 4}, :c #{3 6}, :b #{2 5}}

(merge-with merge-fn {:a #{1}} {:b #{2}})
;; Undefined, may be {:a #{1 #{2}}} or {:a #{2 #{1}}}.
```

When one wants to test that both elements are of the same type, the vector can
be omitted and one can just specify the type instead.

This function adds `Number`s into a `PersistentVector`, creating one if none
exist. Will concatenate two vectors if both values are vectors, and will throw
an error if no combination specified exists:

```clj
(import 'clojure.lang.IPersistentVector)

(def merge-fn
  (rule/type-fn {Number vector,
                 [IPersistentVector Number] conj,
                 [Number IPersistentVector] #(conj %2 %1),
                 IPersistentVector into}
    u/err-fn))

(merge-with merge-fn {:a 2, :b [3]} {:a 3.0, :b 4/5})
#_=> {:a [2 3.0], :b [3 4/5]}

(merge-with merge-fn {:a [1.0M], :b 1N} {:a [22], :b [42]})
#_=> {:a [1.0M 22], :b [42 1N]}

(merge-with merge-fn {:a 1} {:a :crash})
;; Exception Couldn't merge based on values: 1, :foo
;; sun.reflect.NativeConstructorAccessorImpl.newInstance0
;; (NativeConstructorAccessorImpl.java:-2)
```

## Merge based on key

### `key-merge`

To merge based on key and nothing else, use `key-merge`:

```clj
(def rules {:a +, :b *})

(key/key-merge rules {:a 1, :b 2} {:a 3, :b 4})
#_=> {:a 4, :b 8}

(key/key-merge rules {:a 4, :f 10} {:a 3, :f 22})
#_=> {:a 7, :f 22} ;; Defaults to rightmost
```

### `key-merge-with`

If you want to specify default function, use `key-merge-with`:

```clj
(def rules {:a +, :b *})

(key/key-merge-with rules max {:a 1, :c 4} {:a 3, :c -1})
#_=> {:a 4, :c 4}

(key/key-merge-with rules min {:b 4, :g 65} {:b 3, :g 89})
#_=> {:b 12, :g 65}
```

### `rule-fn` and `merge-with-key`

You can also use `rule-fn` and `merge-with-key` to do the job:

```clj
(def key-mfn
  (rule/rule-fn {:a concat, :b into}
    (fn [rule v1 v2] (+ v1 v2)))) ;; NB! 3-arity fn here

(key/merge-with-key key-mfn {:a [1 2], :b #{2 3}} {:a [3 4], :b #{3 6 7 2}})
#_=> {:a '(1 2 3 4), :b #{2 3 6 7}}

(key/merge-with-key key-mfn {:a [3 4], :c 2} {:a nil, :c 8})
#=_> {:a '(3 4), :c 10}
```

`u/right` and `u/left` is also 3-arity, taking the rightmost and leftmost
**value**:

```clj
(def key-mfn
  (rule/rule-fn {} u/right))

(key/merge-with-key key-mfn {:a [1 2], :b #{2 3}} {:a [3 4], :b #{3 6 7 2}})
#_=> {:a [3 4], :b #{2 3 6 7}}

(def key-mfn
  (rule/rule-fn {} u/left))

(key/merge-with-key key-mfn {:a [1 2], :b #{2 3}} {:a [3 4], :b #{3 6 7 2}})
#_=> {:a [1 2], :b #{2 3}}
```

The good part with this is that you can compose them with other functions. Look
at the combining part below.

## Recursive merging based on path

### `path-merge`

To recursively merge and specify merge function based on path, use `path-merge`:

```clj
(def rules {[:a :a] +, [:a :b] *, [:b :a] -, [:b :b] /})

(path/path-merge rules {:a {:a 1, :b 2} :b {:a 3, :b 4}}
                       {:a {:a 5, :b 6} :b {:a 7, :b 8}})
#_=> {:a {:a 6, :b 12}, :b {:a -4, :b 1/2}}

(path/path-merge rules {:a {:a 1, :c 2} :c {:a 3, :b 4}}
                       {:a {:a 5, :c 6} :c {:a 7, :b 8}})
#_=> {:a {:a 6, :c 6}, :c {:a 7, :b 8}}
;; Normal merge rules if the path is not a subpath in rules
```

### `path-merge-with`

To recursively merge as `path-merge`, but specify a default merge function, use
`path-merge-with`:

```clj
(def rules {[:a :a] +, [:a :b] *, [:b :a] -, [:b :b] /})

(path/path-merge-with rules u/left {:a {:a 1, :b 2} :b {:a 3, :b 4}}
                                   {:a {:a 5, :b 6} :b {:a 7, :b 8}})
#_=> {:a {:a 6, :b 12}, :b {:a -4, :b 1/2}}

(path/path-merge-with rules u/left {:a {:a 1, :c 2} :c {:a 3, :b 4}}
                                   {:a {:a 5, :c 6} :c {:a 7, :b 8}})
#_=> {:a {:a 6, :c 2}, :c {:a 3, :b 4}}
```

### Recursive merge manually

Needed for more complex mergings. `subpath?-fn` returns a fn checking whether a
path is a subpath of the previously given paths:

```clj
(def paths [[:a :b :c] [:a :b :d] [:c :b :a]])

(def subpath? (path/subpath?-fn paths))

(subpath? [:a]) #_=> true
(subpath? [:b]) #_=> false
(subpath? [:a :b]) #_=> true
(subpath? [:a :b :c]) #_=> false
(subpath? [:c :b]) #_=> true
(subpath? [:c :d]) #_=> false
```

Recursive merge function through `sub-merge-fn` and `merge-with-path`. This is
like `path-merge-with`, with `u/left` as default fn.

```clj
(def rules {[:a :b :c] +, [:a :c] *, [:c :b] into})

(def merge-fn
  (rule/rule-fn rules
    (rule/cond3-fn {[(path/subpath?-fn (keys rules)) u/_ u/_]
                    (path/sub-merge-fn #'merge-fn)}
      u/left)))

(path/merge-with-path merge-fn {:c {:b [1 2]}} {:c {:b [3 4]}})
#_=> {:c {:b [1 2 3 4]}}

(path/merge-with-path merge-fn {:a {:b {:c 10}, :c 10}} {:a {:b {:c 2}, :c 2}})
#_=> {:a {:b {:c 12}, :c 20}}

(path/merge-with-path merge-fn {:c {:not :subpath}} {:c {:a :b}})
#_=> {:c {:a :b, :not :subpath}}
```

## Chaining, combining, preparing

### In general

You may combine functions through chaining or as a tree. Here is an example
which utilizes both: If both values are integers, add them if both are odd. If
both are even, multiply them, and if not, find their positive difference. If the
values are maps, merge them, keeping the first found value. If one of them is a
set and the other is a float or ratio, add the number to the set. If both are
sets, take their union. Otherwise, create a new set if you have two numbers, and
default to the rightmost value if nothing of this is is true. (This sounds more
complicated than it really is.)

```clj
(import 'clojure.java.IPersistentSet)

(def int-stuff
  (rule/cond-fn {[odd? odd?] +,
                 [even? even?] *,
                 > -,
                 < #(- %2 %1)}
    u/err-fn)) ;; This won't happen, but better to be safe than sorry

(def merge-fn
  (rule/cond-fn {[integer? integer?] int-stuff,
                 [map? map?] (partial merge-with u/left)}
    (rule/type-fn {IPersistentSet into, ;; poor man's union
                   [IPersistentSet Number] conj,
                   [Number IPersistentSet] #(conj %2 %1),
                   Number hash-set}
      u/right)))
```

### `<<-`

When doing longer chains, `<<-` may be of value to avoid right tails. For
example, the merge function in the section about manual recursive merging can be
written as follows instead:

```clj
(def merge-fn
  (<<-
   (rule/rule-fn rules)
   (rule/cond3-fn {[(path/subpath?-fn (keys rules)) u/_ u/_]
                   (path/sub-merge-fn #'merge-fn)})
    u/left))
;; Expands into the original code.
```

### `fn3->fn2`

`rule-fn` and `cond3-fn` takes by default a 3-argument function. Bypass this
with the macro `fn3->fn2`.

Here is a function which checks keys first, and if none applies adds integers
together or picks the rightmost value:

```clj
(def merge-fn
  (<<-
   (rule/rule-fn {:a concat, :b into})
   (u/fn3->fn2
     (rule/cond-fn {[integer? integer?] +}))))

(key/merge-with-key merge-fn {:a [1 2], :b #{1 2}} {:a [3 4], :b #{3 -1}})
#_=> {:a '(1 2 3 4), :b #{-1 1 2 3}}

(key/merge-with-key merge-fn {:c 4, :d :foo} {:c 4, :d :bar})
#_=> {:c 8, :d :bar}
```

You could also chain within the `fn3->fn2` function like this:

```clj
(import 'clojure.lang.IPersistentSet)

(def merge-fn
  (<<-
   (rule/rule-fn {:a concat, :b into})
   (u/fn3->fn2
     (<<-
       (rule/cond-fn {[integer? integer?] +})
       (rule/type-fn {IPersistentSet into})))))

(key/merge-with-key merge-fn {:a #{1 2}, :c #{1 2}, :d 1}
                             {:a #{2 1}, :c #{1 2}, :d 4})
#_=> {:a '(1 2 1 2), :c #{1 2}, :d 5}

(key/merge-with-key merge-fn {:c [1 2 3]} {:c [4 5 6]})
#_=> {:c [4 5 6]}
```

You could of course also hook out again, if you want to. Say you want to test
some more keys after all other checks have ran, you can then hook it out by
giving the other `rule-fn` as second parameter to `fn3->fn2` (put in there by
`<<-`):

```clj
(import 'clojure.java.IPersistentSet)

(def merge-fn
  (<<-
   (rule/rule-fn {:a concat, :b into})
   (u/fn3->fn2
     (<<-
       (rule/cond-fn {[integer? integer?] +})
       (rule/type-fn {IPersistentSet into})))
   (rule/rule-fn {:c interleave})))


(key/merge-with-key merge-fn {:c #{1 2 3}} {:c #{4 5 6}})
#_=> {:c #{1 2 3 4 5 6}}

(key/merge-with-key merge-fn {:a #{1}, :c [1 2 3]} {:a [4 5 6], :c [4 5 6]})
#_=> {:a '(1 4 5 6), :c '(1 4 2 5 3 6)}
```

### `prep-args`

You may also want to use both paths and keys. This function recursively handles
this through `prep-args`:

```clj
(def f-rules {[:a :b] +, [:a :c] *, [:d] u/left})

(def subpath? (path/subpath?-fn (keys f-rules)))

(def merge-fn
  (<<-
    (rule/rule-fn f-rules)
    (rule/cond3-fn {[subpath? u/_ u/_] (path/sub-merge-fn #'merge-fn)})
    (u/prep-args [p v1 v2] [(peek p) v1 v2]
      (rule/rule-fn {:a *, :c +}))))

(path/merge-with-path merge-fn {:a {:b 2, :c 5}} {:a {:b 3, :c 4}})
#_=> {:a {:c 20, :b 5}}

(path/merge-with-path merge-fn {:a {:a 6}, :c 20} {:a {:a 10}, :c 13})
#_=> {:a {:a 60}, :c 33}

(path/merge-with-path merge-fn {:d {:a 15, :c 10}} {:d {:a 10, :c 40}})
#_=> {:d {:a 15, :c 10}}
```

`prep-args` also take two arguments, so if it is given two, it will "hook" out
again:

```clj
(def f-rules {[:c] *, [:d] u/left})
(def l-rules {[:a :b] +, [:a :c] *, [:a] vector})

(def subpath? (path/subpath?-fn (mapcat keys [f-rules l-rules])))

(def merge-fn
  (<<-
    (rule/rule-fn f-rules)
    (rule/cond3-fn {[subpath? u/_ u/_] (path/sub-merge-fn #'merge-fn)})
    (u/prep-args [p v1 v2] [(peek p) v1 v2]
      (rule/rule-fn {:a *, :c +}))
    (rule/rule-fn l-rules)))

(path/merge-with-path merge-fn {:a {:b 2, :c 5, :a 10}}
                               {:a {:b 3, :c 4, :a 10}})
#_=> {:a {:a 100, :c 9, :b 5}}

(path/merge-with-path merge-fn {:a {:c 2}, :c 10} {:a {:c 10} :c 2})
#_=> {:a {:c 12}, :c 20}
```
