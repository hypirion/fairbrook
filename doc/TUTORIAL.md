# Fairbrook

## What is Fairbrook

Fairbrook is a library for doing map merges when a normal `merge` or a
`merge-with` doesn't suffice. While the phrase *"Fine-grained map manipulation
for the masses."* is the library's description, that's partly an overstatement:
The library only covers the "merge" part of map manipulation. For now, Fairbrook
is able to do the followin:

* Merging maps based on:
  * What key has had a "collision"
  * The type of the values merged:
     * Either by specifying the type both `isa?` element of
     * Or specify for both values independently of the other
  * The *path* to the value — e.g. `[:a :b]` for `1` within `{:a {:b 1}}`
  * "Multimethod dispatch"
     * One function taking both values as input
     * Two functions, each taking one value as input (either `or`ed or `and`ed
       together)
     * One function, taking key/path and the values as input
     * Or three functions, in same fashion as the point two point above
* Functions for easily keeping and/or merging metadata for values

  While not being essential for many, some projects may need it. Currently,
  many—if not all—functions will destroy the metadata of one value if two
  values are merged together.
* Simple and easy composition of everything mentioned above

In addition, the library is supported from 1.2 and up to the newest snapshot
of Clojure available, and has no dependencies to anything but Clojure.

### When to use Fairbrook

While many projects may at first sight have a need to do fine-grained map
merges, it may often be unneccesary and might add an additional level of
complexity. Before you use it, try and find some way around intricate
merging. It is not because Fairbrook is a bad library or increases incidental
complexity, but it's good to know that it is the best solution for your problem
before going with it.

### What this tutorial covers

This tutorial will cover every single function and macro within Fairbrook. Don't
worry, there aren't that many, and they are very easy to understand
conceptually. This tutorial will first explain how to use the functions
independently, and will then go into detail about how they can and were meant to
be used together. Used alone, they can solve many common merge problems. Used
together, they can solve more delicate merge tasks.

## Functions in Fairbrook

### `cond-fn`

At some point you may need to merge two maps in your Clojure application, where
some keys may or may not be within the map. As such, the obvious solution is of
course to do a `merge-with`, and perform a specific merge of the values which
may contain different data. Say we for instance have an internal "lunch tracker"
with Apache CouchDB as database, because our office is nice and cover our lunch
related expenses. A worker just put in the price paid along with the bill in the
system through some interface, and it is saved.

At some time, the system gets three notifications about bills simultaneously,
and delegates the work to three threads. For those not known to Apache CouchDB,
you have to include a revision key (named `_rev`) to avoid overwriting documents
people may have written to CouchDB. If try to update a document and the
`_rev`-key is not the same as the one in the database, you will get a
conflict. Of course this happens here, and we've taken such an issue into
account: We create a map and merge it with the document (represented as a
map). In this way, we can merge it together with the fetched data, try and put
it back into the database, and if it fails just retry: (We're currently not
using `user` or `bill`, but will later in the tutorial.)

```clj
(ns luncher.database-stuff
  (:require [fictive-time-db :as time]
            [fictive-couchdb-lib :as db :refer [my-db]]))

(defn merge-fn
  [new old]
  (cond (int? new)           (+ old new)
        (time/datetime? new) (time/latest old new)
		:else old)) ; has currently no effect

(defn add-bill [user price bill datetime]
  (let [new-data {:total price, :last-updated datetime}]
    (while (->> (db/get my-db :lunch-document)
	            (merge-with merge-fn new-data)
				(db/put! my-db)
				(db/conflict?))))
	:success)
```

It's neither hairy nor problematic at this point, but the `cond` within
`merge-fn` may grow if we intend to add functionality to this lunch
database. Let's see how we can do this with `fairbrook.rule/cond-fn`, and see if
it helps us any:

```clj
;; add this within the require: [fairbrook.rule :as rule]
(def merge-fn
  (rule/cond-fn [[(fn [new _] (int? new)) +]
                 [(fn [new _] (time/datetime? new)) time/latest]
				 [(fn [_ _] :else) (fn [_ old] old)]]))
```

As of right now, this looks even worse than the non-fairbrook merge function! We
can do some cosmetics to this currently ugly beast, but before we get there, let
us get to the basics first:

`cond-fn` returns a function instead of automatically dispatch on values —
therefore we have to change the `defn` into a `def`. `cond-fn` takes as first
argument a vector of vectors, and every vector within the vector must have two
elements: a *test* function and a *use* function. For example, in
`[(fn [new _] (int? new)) +]`, `(fn [new _] (int? new))` is the *test* function,
and `+` is the *use* function.

Whenever `merge-fn` is called, it will walk through every pair in order and call
the test function with the two values it was given. If the test function returns
a truthy value, the use function will be called with the two values and its
result will be returned. Finally, if none of the tests return a truthy value,
`merge-fn` will return the second argument. As such, `(merge foo bar)` is
exactly equal to `(merge-with (rule/cond-fn []) foo bar)` because none of the
tests (in which there are none of) return a truthy value.

We've not used the fact that the second element is automatically returned if
test returns truthy. If we add this into our function, we get the following
result:

```clj
(def merge-fn
  (rule/cond-fn [[(fn [new _] (int? new)) +]
                 [(fn [new _] (time/datetime? new)) time/latest]]))
```

Since our last test was the `:else` case of our earlier `cond`, we can omit it
as this was the "act as merge here" part. It's still a visual mess, but at least
it looks a bit better.

We can clean it up a bit more: If the order of the `cond-fn` *test* functions
doesn't matter, we can use a map to represent the different cases instead. Note
that you must be sure that the order the test functions are called really
doesn't matter if you go with a map, as the map or `cond-fn` may change the
order the test functions are called in. Since an int is not a datetime and vice
versa, we can do this here:

```clj
(def merge-fn
  (rule/cond-fn {(fn [new _] (int? new)) +,
                 (fn [new _] (time/datetime? new)) time/latest}))
```

It doesn't change much, but it tells the reader that the order of the test
functions doesn't matter.

The remaining ugly part is the `(fn [new _] ...)`. Since it is very common to do
tests which test the different values independently and then either `or`s or
`and`s it together, we have some convenience functions for that. Add
`[fairbrook.util :as u]` in your `:require`, and we can clean it up as much as
this:

```clj
(def merge-fn
  (rule/cond-fn {(u/and-fn int? (constantly true)) +,
                 (u/or-fn time/datetime? (constantly false)) time/latest}))
```

`fairbrook.util` contains `and-fn` and `or-fn`. `(and-fn foo bar)` is exactly
like writing `(fn [x y] (and (foo x) (bar y)))`, and `(or-fn foo bar)` is
equivalent, except it uses `or` instead of `and`.

The visual clutter is now related to `(constantly true)` and
`(constantly false)`. They are pretty large code-wise, and it would be nice if
we could remove these. While there's no utility function for `(constantly
false)`, `fairbrook.util` has a function named `_` which is exactly like
`(constantly true)`. Using `_` and converting the `u/or-fn` to `u/and-fn` makes
us stand with the following snippet:

```clj
;; Add a refer like this: [fairbrook.util :as u :refer [_]]
(def merge-fn
  (rule/cond-fn {(u/and-fn int? _)           +,
                 (u/and-fn time/datetime? _) time/latest}))
```

This is visually easy to read! However, there's even better news: I didn't tell
you about the fact that `cond-fn` can take a vector instead of a test
function. If `cond-fn` reads a vector instead, it will automatically expand it
to an conjunction: `[foo bar]` will be translated into `(fairbrook.util/and-fn
foo bar)`. As such, we finally end up with this merge function:

```clj
(def merge-fn
  (rule/cond-fn {[int? _]           +,
                 [time/datetime? _] time/latest}))
```

And if you compare it with this:

```clj
(defn merge-fn
  [new old]
  (cond (int? new)           (+ old new)
        (time/datetime? new) (time/latest old new)
		:else old))
```

I believe the former explains better what it is supposed to do.

#### Optional arguments

Our boss would like a more secure program, since we may decide to add new values
to the lunch document or decide to let `:total` be a map containing a total sum
per person rather than being an int. As such, we'd avoid to default to a normal
merge operation, and rather throw an error instead. This can be done this way:

```clj
(def merge-fn
  (rule/cond-fn {[int? int?]           +,
                 [time/datetime? time/datetime?] time/latest}
   u/err-fn))
```

`fairbrook.util/err-fn` is a function taking any arguments and throws a message
saying that the merge didn't match any rules with the arguments given.

We've now ensured that both values are ints before adding them, and we've also
ensured that both dates are in fact dates, before finding the latest one. If
none of the rules apply, the merge function will throw an error message telling
us what values caused the exception to be thrown, but we will unfortunately not
find out which key and which maps caused this error.

#### Final result

Changing from a normal "homebrewed" `merge-with` to Fairbrook, we end up with
the following result:

```clj
(ns luncher.database-stuff
  (:require [fictive-time-db :as time]
            [fictive-couchdb-lib :as db :refer [my-db]
			[fairbrook.rule :as rule]
			[fairbrook.util :as u]))

(def merge-fn
  (rule/cond-fn {[int? int?]                     +,
                 [time/datetime? time/datetime?] time/latest}
   u/err-fn))

(defn add-bill [user price bill datetime]
  (let [new-data {:total price, :last-updated datetime}]
    (while (->> (db/get my-db :lunch-document)
	            (merge-with merge-fn new-data)
				(db/put! my-db)
				(db/conflict?))))
	:success)
```

### `type-fn`

Our new solution to the merge problem looks better than the previous version,
but there's still issues we have to face: Why do we use a conditional function
to dispatch on type? Indeed, if there is another solution for type dispatching,
we should do so instead.

And of course there is: the function `type-fn` is just like `cond-fn`, but takes
types instead of functions. As such, changing the merge function over to
`type-fn` is very straightforward:

```clj
(import 'lib.fictive.time.Datetime)

(def merge-fn
  (rule/type-fn {[Number Number] +,
                 [Datetime Datetime] time/latest}
   err-fn))
```

`type-fn` acts more or less in the same way as `cond-fn` does: You pass in a map
containing a vector with two types and a use function if the values passed in
has these types. Contrary to `cond-fn`, it does not accept a vector as input; it
must have a map. And, as specified earlier, the order in which the tests will be
ran is completely random. As such, relying on an ordering will cause problems.

If the type of both values must match the same type, you may omit the vector
form and just insert the type instead. For our case, this means that `merge-fn`
can be even shorter, written like this:

```clj
(import 'lib.fictive.time.Datetime)

(def merge-fn
  (rule/type-fn {Number +, Datetime time/latest}
   err-fn))
```

Now we're getting short and succinct.

#### Own hierarchies (can be skipped)

In some cases, extending the global hierarchy may be needed. There's no magic
going on, you can do it exactly the same way you're used to: Use a keyword with
the namespace attached through the `::keyword` notation, and you're okay.

```clj
(derive Number ::number)
(derive Datetime ::datetime)

(def merge-fn
  (rule/type-fn {::number +, ::date time/latest}
   err-fn))
```

In extremely rare cases, you may want to use your own hierarchy. This is an
example of doing that:

```clj
(def my-hierarchy
  (-> (make-hierarchy)
      (derive Number :number)
	  (derive Datetime :datetime)))

(def merge-fn
  (rule/type-fn {:number +, :date time/latest}
   err-fn
   my-hierarchy))
```

However, using your own hierarchy should be used sparsely, as it makes it
difficult to compose `type-fn` with other rules. Use `::keywords` whenever
possible.
