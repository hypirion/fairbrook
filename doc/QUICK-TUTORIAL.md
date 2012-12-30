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

(merge-with merge-fn {:a 1, :b 2} {:a 2, :b 2})
#_=> {:a 2, :b [2 2]}

(merge-with merge-fn {:a 1, :b 4} {:a 2} {:a 1, :b 6})
#_=> {:a 1, :b 6}
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

Dispatch on type. Like `cond-fn`, accepts maps only. Undefined ordering.

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
  (rule/type-fn {[Number Number] vector,
                 [IPersistentVector Number] conj,
                 [Number IPersistentVector] #(conj %2 %1),
                 [IPersistentVector IPersistentVector] into}
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

The good part with this is that you can compose them with other functions. Look
at the combining part below.
