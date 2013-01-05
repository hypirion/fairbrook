# Meta

## Rationale

Being a map manipulation tool, having utility functions for metadata seems a bit
out of place. Why should this be in Fairbrook?

The answer is partly related to how `merge` and `merge-with` works, and partly
related to how metadata is stored: `merge` and `merge-with` keeps the metadata
of the first map, and discards the metadata of the other maps. This is normal
for all forms of data structure manipulation, but when doing more fine-grained
manipulations, one may want to keep the metadata in some way. Metadata is stored
in maps, and manipulating metadata would thus require some map merging if you
have two metadata maps.

## The workhorses `ff` and `fm`

There are two functions in `fairbrook.meta` which is more frequently used than
all the others:

### `ff`

`fairbrook.meta/ff` is a function frequently used when merging two pieces of
data and you want to apply one function on the data, and a second one on the
metadata. `ff` stands for *function-function*, a function for the data, and a
function for the metadata. It takes two arguments, both functions are of
two-arity:

```clj
(def foo
  (ff into (fn [l r] (merge l (select-keys r [:bar])))))

(let [a (with-meta [1 2 3] {:bar 4, :baz 5})
      b (with-meta [4 5 6] {:bar 99, :baz 88})]
  (def frob (foo a b)))

frob
#_=> [1 2 3 4 5 6]

(meta frob)
#_=> {:bar 99, :baz 5}
```

### `fm`

As it is common to just merge the metadata, fairbrook has a "utility" function
for that. `fairbrook.meta/fm` stands for *function-merge*, and `(fm foo)` is
just a short way of saying `(ff foo merge)`:

```clj
(meta ((fm list) ^{:a 1, :b 2} [] ^{:a 5, c 8} {}))
#_=> {:a 5, :c 8, :b 2}
```
