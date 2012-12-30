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
                 [nil? boolean] u/right}))
```
