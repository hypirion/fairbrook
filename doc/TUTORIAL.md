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
  (:require [fictive-time-lib :as time]
            [fictive-couchdb-lib :as db :refer [my-db]]))

(defn merge-fn
  [new old]
  (cond (number? new)        (+ old new)
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
  (rule/cond-fn [[(fn [new _] (number? new)) +]
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
`[(fn [new _] (number? new)) +]`, `(fn [new _] (number? new))` is the *test* function,
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
  (rule/cond-fn [[(fn [new _] (number? new)) +]
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
  (rule/cond-fn {(fn [new _] (number? new))        +,
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
  (rule/cond-fn {(u/and-fn number? (constantly true))        +,
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
  (rule/cond-fn {(u/and-fn number? _)        +,
                 (u/and-fn time/datetime? _) time/latest}))
```

This is visually easy to read! However, there's even better news: I didn't tell
you about the fact that `cond-fn` can take a vector instead of a test
function. If `cond-fn` reads a vector instead, it will automatically expand it
to an conjunction: `[foo bar]` will be translated into `(fairbrook.util/and-fn
foo bar)`. As such, we finally end up with this merge function:

```clj
(def merge-fn
  (rule/cond-fn {[number? _]        +,
                 [time/datetime? _] time/latest}))
```

And if you compare it with this:

```clj
(defn merge-fn
  [new old]
  (cond (number? new)        (+ old new)
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
  (rule/cond-fn {[number? number?]               +,
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
  (:require [fictive-time-lib :as time]
            [fictive-couchdb-lib :as db :refer [my-db]]
            [fairbrook.rule :as rule]
            [fairbrook.util :as u]))

(def merge-fn
  (rule/cond-fn {[number? number?]               +,
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
   u/err-fn))
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
   u/err-fn))
```

Now we're getting short and succinct.

#### Own hierarchies (can be skipped on first read)

In some cases, extending the global hierarchy may be needed. There's no magic
going on, you can do it exactly the same way you're used to: Use a keyword with
the namespace attached through the `::keyword` notation, and you're okay.

```clj
(derive Number ::number)
(derive Datetime ::datetime)

(def merge-fn
  (rule/type-fn {::number +, ::date time/latest}
   u/err-fn))
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
   u/err-fn
   my-hierarchy))
```

However, using your own hierarchy should be used sparsely, as it makes it
difficult to compose `type-fn` with other rules. Use `::keywords` whenever
possible.

### Merging with keys

Using `cond-fn` or `type-fn` is fine as long as all the keys with these
properties behave in the same way. If every pair of numbers should be added or
every pair collections concatenated, this is the correct way to go. However,
real life is seldom like that, and most of the times we should do merging based
on what exact key we should merge on instead.

Our boss would now like to keep track of the highest bill ever received as
well. `cond-fn` and `type-fn` are unable handle this, as both `:total` and
`:highest` are of the same type.

```clj
(ns luncher.database-stuff
  (:require [fictive-time-lib :as time]
            [fictive-couchdb-lib :as db :refer [my-db]]
            [fairbrook.rule :as rule]
            [fairbrook.util :as u])
  (:import [lib.fictive.time Datetime]))

(def merge-fn
  (rule/type-fn {Number +, Datetime time/latest}
   u/err-fn))

(defn add-bill [user price bill datetime]
  (let [new-data {:total price, :last-updated datetime}]
    (while (->> (db/get my-db :lunch-document)
                (merge-with merge-fn new-data)
                (db/put! my-db)
                (db/conflict?))))
    :success)
```

In our current program, as shown above, we use the normal `merge-with`
function. The problem with `merge-with` is that it doesn't give us the key which
has a collision. Fairbrook solves this issue by implementing a function named
`fairbrook.key/merge-with-key`, which will give us the key as well whenever a
collision occurs.

However, there are some other functions available here which we will have a look
at before using `merge-with-key` directly: `fairbrook.key/key-merge` and
`fairbrook.key/key-merge-with`.

`key-merge` takes in a map of keys which are associated with a function as well
as some maps to perform the key merge on, and will dispatch based on key. If no
key is matched, then the rightmost value will be picked. `key-merge-with` works
like `key-merge`, but requires a default function taking the two values which
has collided.

For our use-case, we could use `key-merge` like this:

```clj
(defn merge-bills
  [new-data lunch-doc]
  (key/key-merge {:total +, :highest max, :last-updated time/latest}
      new-data lunch-doc))

(defn add-bill [user price bill datetime]
  (let [new-data {:total price, :highest price, :last-updated datetime}]
    (while (->> (db/get my-db :lunch-document)
                (merge-bills new-data)
                (db/put! my-db)
                (db/conflict?))))
    :success)
```

And we could use `key-merge-with` like this:

```clj
(defn merge-bills
  [new-data lunch-doc]
  (key/key-merge-with {:total +, :highest max, :last-updated time/latest}
    u/err-fn, new-data lunch-doc))
```

Where `u/err-fn` would be our merge function if neither `:total`, `:highest` or
`:last-updated` had a collision and had to be merged.

`key-merge` and `key-merge-with` are nice additions if you only need to merge on
keys. This fit our current need nicely, but again, if we need to extend the
merging process to something more complicated, we're out of luck. This is where
`merge-with-key` comes into play: `merge-with-key` works exactly like
`merge-with`, but will give the key to the function invoked as well whenever a
collision occurs. For instance, `(merge-with-key (fn [k v1 v2] (vector k v1 v2))
{:a 1} {:a 2})` will return `{:a [:a 1 2]}`.

That being said, creating functions like `(fn [k v1 v2] ...)` is a bit ugly, and
most use the key to determine which function to use on the values. It's very
rare that the key itself has to be included as result value.

With that in mind, `fairbrook.rule/rule-fn` will solve the problem of
ours. `rule-fn` takes a map and an optional default function, just like
`cond-fn` and `type-fn` does. However, `rule-fn` returns a function which takes
three arguments: If the first argument is within the map, it will then call the
associated value with the second and third argument. If the first argument isn't
within the map, `(default second third)` will be called if given. If not, the
third argument will be returned.

This rather technical description isn't that much helpful without an example of
this in use:

```clj
(def func
  (rule/rule-fn {:a +, :b *, :c -}))

(func :a 3 2) #_=> 5
(func :b 3 2) #_=> 6
(func :c 3 2) #_=> 1
(func :d 3 2) #_=> 2
```

As such, `merge-with-key` and `rule-fn` is like bread and butter: They
complement each other nicely. You may wonder why it isn't called `key-fn`
instead of `rule-fn`. This is because there is in fact another function which
fits just as nicely with `rule-fn` as `merge-with-key`. We'll have a look at
that one later on.

If we now decide to use `merge-with-key` and implement the function as intended,
we end up with the following code:

```clj
(ns luncher.database-stuff
  (:require [fictive-time-lib :as time]
            [fictive-couchdb-lib :as db :refer [my-db]]
            [fairbrook.key :as key]
            [fairbrook.rule :as rule]
            [fairbrook.util :as u]))

(def merge-fn
  (rule/rule-fn {:total +, :highest max, :last-updated time/latest}
   u/err-fn))

(defn add-bill [user price bill datetime]
  (let [new-data {:total price, :highest price, :last-updated datetime}]
    (while (->> (db/get my-db :lunch-document)
                (key/merge-with-key merge-fn new-data)
                (db/put! my-db)
                (db/conflict?))))
    :success)
```

This is certainly less verbose than implementing the whole deal yourself!

### Merging nested maps

We have until now only dealt with single leveled map merging: We are only
merging maps together, and we can decide what function to use based on key, type
or some conditional. We've not had the need to merge nested maps yet, but of
course this is an issue one could end up in in the real world.

... coincidentally, our boss want us to do this as well: He now wants us to put
bills under each user to get better control of how much each person uses, stored
in a set. How do we do this with Fairbrook? One way would be to use
`merge-with-key` and then have multiple different `rule-fn`s nested within
eachother, using a function named `fairbrook.key/merge-with-key-fn`. However,
that would turn both verbose and ugly pretty fast.

A better solution would be to utilize `fairbrook.path`, which contain functions
for creating recursive merging within maps without losing your
head. `fairbrook.path/path-merge` can bootstrap recursive path merging, given
you know the *path* to every possible key collision up front. A *path* is a
sequence of keys, which shows the path to the value we're looking for in a
nested structure: It's the vectors you pass to `get-in`, `assoc-in` and
`update-in`.

`path-merge` works somewhat like `key-merge`, but with paths instead. If a
collision occurs and the path is contained within the rule map, the function
associated with the path will be invoked with the values clashing. Unlike
`key-merge`, `path-merge` recursively merges values if the path is a subpath,
and continues. If it has to merge paths neither being subpaths nor paths will
pick the left value and continue on.

The code below with `path-merge` is more or less the same as the one with
`key-merge`, but with square brackets wrapped around and the new case added:

```clj
(ns luncher.database-stuff
  (:require [fictive-time-lib :as time]
            [fictive-couchdb-lib :as db :refer [my-db]]
            [fairbrook.path :as path]
            [fairbrook.rule :as rule]
            [fairbrook.util :as u]))

(defn add-bill [user price bill datetime]
  (let [path-rules {[:total] +, [:highest] max, [:last-updated] time/last,
                    [:bills user] clojure.set/union}
        new-data {:total price, :highest price, :last-updated datetime,
                  :bills {user #{bill}}]
    (while (->> (db/get my-db :lunch-document)
                (path/path-merge path-rules new-data)
                (db/put! my-db)
                (db/conflict?))))
    :success)
```

Note that `path-rules` must be defined within `add-bill`, as `user` is not known
compile-time.

There is a slight problem with using `path-merge` however: `path-merge` does
not take any optional argument, and as such, `err-fn` cannot be chosen as
default action. Of course, `fairbrook.path/path-merge-with` is the solution,
which takes an optional argument. The result is that we just change this line:

```clj
                (path/path-merge path-rules new-data)
```

into this:

```clj
                (path/path-merge path-rules u/err-fn new-data)
```

And now, our solution is succinct and our boss is happy. What more could be ask
for?

#### More control through `merge-with-path`

This part is not needed to understand easy chaining, but if there is a need for
more complex and recursive merges, this is worthwhile reading as this is the
basis for it.

If we need more control of things, `fairbrook.path/merge-with-path` is the
appropriate tool. `merge-with-path` takes the merge function and the maps to
merge. It is identical to `merge-with-key`, except that instead of giving the
specific *key*, it will give the current *path* (being `[k]` instead of `k`). As
such, it's very useless unless you have some function which gives you the
possibility to merge stuff—another function is needed to perform such a
task. That other function is `fairbrook.path/sub-merge-fn`, which takes the
function to perform. The function it returns takes three arguments: a path p and
two maps. It will then merge the maps and, if a collision occurs, call f with
`(conj collision-key p)` and the values colliding. The trick now is to send it
the `rule-fn` which has this `sub-merge-fn` as its second argument—recursion. A
simple `def` merge function may for the unexperienced look like this:

```clj
(declare merge-fn)

(def merge-fn
  (rule/rule-fn {[:a :b] +, [:b] -}
    (path/sub-merge-fn merge-fn))) ; incorrect
```

However, the `merge-fn` called to `sub-merge-fn` is the unbound version of
`merge-fn`, leading to an IllegalStateException. The solution for this is to
just sharp-quote it, until Rich introduces some sort of dataflow variable in
Clojure:

```clj
(def merge-fn
  (rule/rule-fn {[:a :b] +, [:b] -}
    (path/sub-merge-fn #'merge-fn))) ; correct
```

For rules designed at runtime, more tricks has to be done. One may think that
sharp-quoting the assigned variable may work, but unfortunately not—it will
only result in a RuntimeException because the var is unresolvable. So this will
not work:

```clj
(let [g [:a :b]
      merge-fn (rule/rule-fn {g +, [:b] -}
                 (path/sub-merge-fn #'merge-fn))] ; incorrect
  (path/merge-with-path merge-fn {:a {:b 2} :b 5} {:a {:b 3} :b 1}))
```

The solution is to wrap the merge function within a named anonymous function and
call it like so:

```clj
(let [g [:a :b]
      merge-fn (fn m-fn [p v1 v2]
                 ((rule/rule-fn {g +, [:b] -}
                    (path/sub-merge-fn m-fn))
                  p v1 v2))] ; correct
  (path/merge-with-path merge-fn {:a {:b 2} :b 5} {:a {:b 3} :b 1}))
```

And, since this is a bit ugly, it's common to wrap it within a `defn` with an
appropriate name:

```clj
(defn make-merge-fn [rules]
  (fn m-fn [p v1 v2]
    ((rule/rule-fn rules
                  (path/sub-merge-fn m-fn))
                  p v1 v2)))

(let [g [:a :b]
      merge-fn (make-merge-fn {g +, [:b] -})] ; more evident
  (path/merge-with-path merge-fn {:a {:b 2} :b 5} {:a {:b 3} :b 1}))
```

It's not a pretty solution when done at runtime, but when combining and
"chaining" rules, it turns more composable than having a special function doing
things for you. (It is also not as horrible to read as one may expect.)

Going from the more elegant `path-merge-with`, the solution to our original
problem turns into this:

```clj
(ns luncher.database-stuff
  (:require [fictive-time-lib :as time]
            [fictive-couchdb-lib :as db :refer [my-db]]
            [fairbrook.path :as path]
            [fairbrook.rule :as rule]
            [fairbrook.util :as u]))

(defn make-mfn
  [rules]
  (fn m-fn [p v1 v2]
    ((rule/rule-fn rules
                  (path/sub-merge-fn m-fn))
                  p v1 v2)))

(defn add-bill [user price bill datetime]
  (let [path-rules {[:total] +, [:highest] max, [:last-updated] time/last,
                    [:bills user] clojure.set/union}
        new-data {:total price, :highest price, :last-updated datetime,
                  :bills {user #{bill}}
        m-fn (make-mfn path-rules)]
    (while (->> (db/get my-db :lunch-document)
                (path/merge-with-path m-fn new-data)
                (db/put! my-db)
                (db/conflict?))))
    :success)
```

Which essentially does the same thing as the `path-merge-with`.

## Combining and chaining

With the basics at hand, the combining part of Fairbrook is close. We will leave
the lunch example for now, but will revisit it in the more advanced parts of of
combining. As of right now, we'll just have a taste of how one combine
functions.

We start off with the current case: We want to merge two maps, but we know
there's one key collision. They values may either be Longs or vectors, and we
want to get a vector with all the elements as a result. However, since we're not
sure what the values actually are at compile-time, we'll use `type-fn` to easily
glue the values together as intended:

```clj
(import 'clojure.lang.IPersistentVector)

(merge-with
  (rule/type-fn {Long vector,
                 IPersistentVector into,
                 [IPersistentVector Long], conj
                 [Long IPersistentVector] #(conj %2 %1)}
    u/err-fn)
  map1
  map2) ;;etc.
```

And--naturally--as our functionality increases, so do the complexity of the
system as a whole. For unknown reasons, we would like to add numbers together
whenever they both are odd. This is easily done by wrapping the `type-fn` within
a `cond-fn`:

```clj
(rule/cond-fn {[odd? odd?] +}
  (rule/type-fn {Long vector,
                 IPersistentVector into,
                 [IPersistentVector Long], conj
                 [Long IPersistentVector] #(conj %2 %1)}
   u/err-fn))
```

Or, another completely viable solution, is to add the `cond-fn` *within* the
`type-fn`:

```clj
(rule/type-fn {Long (rule/cond-fn {[odd? odd?] +} vector),
               IPersistentVector into,
               [IPersistentVector Long], conj
               [Long IPersistentVector] #(conj %2 %1)}
 u/err-fn))
```

Currently, both are equivalent. As the latter is a bit more succinct, we'll use
that one.

And, without you even twitching your eye, you've learnt chaining and combining!
Basic chaining usually on the following form:

```clj
(some-fn ruleset
  (some-other-fn other-ruleset
    (some-third-fn third-ruleset ;; And possibly even more
      default-fn)))
```

And combining is on the following form:

```clj
(some-fn {pattern
          (some-other-fn {other-pattern
                          (some-third-fn ...)}
           other-default-fn)}
 default-fn)
```

The functionality shouldn't come as a surprise, of course. Neither should the
fact that you can put the functions and function calls within eachother, as you
know they expect and return functions. The result is that you can arbritrarily
combine and compose functions, which is usually a *nice-to-have* thing. However,
there are some issues at hand:

1. The arity of the functions will be the same: `rule-fn` and `cond3-fn` expects
   a function taking three arguments as the optional default function. Every
   other function expects two arguments, and as such, functions of different
   arities cannot be combined without some "clever" and possibly very nasty
   tricks.

2. Related to arity: What happens if you want to chain with `rule-fn`, then
   `cond-fn` and then `rule-fn` again? You need to somehow pass the first
   argument to the second and third fn-call, but not the second. Again, this
   seems very hairy.

3. What would you do if you want to merge based on path first, then key? You
   have to somehow pop off the last element in the argument. And again, what do
   you want to do if you somehow want to revert these changes again? (Very
   likely if you're doing recursive merging)

Luckily, Fairbrook comes with batteries included and helps you out of that tar
pit without much hassle. We'll cover how to deal with these issues one at a
time, but first we'll have a look at how to make chaining "prettier".

### Removing right tails

Recall that you can take a second argument to all merge functions ending with
`-fn`, the default argument. What happens if you have, say eight of these
chained together?

```clj
(1-fn r1
  (2-fn r2
    (3-fn r3
      (4-fn r4
        (5-fn r5
          (6-fn r6
            (7-fn r7
              8-fn)))))))
```

You will end up with a "right-tailed" lisp function, as many people consider
somewhat unreadable and unpleasant for the eye. We could solve this by using
`->>`, but then the function order would have to be reversed—making it easy to
read, but difficult to understand the ordering.

The solution for this problem, if it ever arises, is named `fairbrook.util/<<-`.
It is exactly the same as `->>`, but in reverse. That is, `(<<- (a 1) (b 2) (c
3))` is turned into `(a 1 (b 2 (c 3)))`. As such, the previous example would be
turned into the following snippet:

```clj
;; (require '[fairbrook.util :refer [<<-]])
(<<- (1-fn r1)
     (2-fn r2)
     (3-fn r3)
     (4-fn r4)
     (5-fn r5)
     (6-fn r6)
     (7-fn r7)
      8-fn)
```

### Going from 3-arity to 2-arity

Solving problem 1 is rather trivial with `fairbrook.util/fn3->fn2`—you wrap the
function of 2-arity with this macro, and the problem should be out of your
world. For example, if we first want to merge based on key, and afterwards merge
on type, this would be a solution:

```clj
;; (require '[fairbrook.util :as [u] :refer [<<-]])
(rule/rule-fn {:foo foo-fn, :bar bar-fn, :baz baz-fn}
  (u/fn3->fn2
    (rule/type-fn {[type1 type2] f} ;; etc...
       u/err-fn)))
```

And, well, that's it! If you have multiple rules that has 2-arity, consider
chaining them within the `fn3->fn2`, as it makes it easier to read.

#### Hook back to 3-arity

All fine and well jumping from 3-arity to 2-arity, but how would one jump back
to 3-arity again? `fn3->fn2` provides such a mechanism as well: An optional
second argument which is the "hookback" function. Say we for instance want to
merge based on key first, then some conditional, then back to key. In Fairbrook,
this is solved as this:

```clj
(rule/rule-fn {:foo foo-fn, :bar bar-fn}
  (u/fn3->fn2
    (rule/type-fn {[type1 type2] f})
    (rule/rule-fn {:baz baz-fn}
      u/err-fn)))
```

Here, a modified function returned by the second argument (`rule-fn {:baz
baz-fn} ...`) is appended into the form of the first argument (`type-fn
{[type1 type2] fn}`). So what happens is that when `type-fn` ends up calling the
default function because no pair matches, it calls the modified `rule-fn` which
already has the first argument. As such, the `rule-fn` is "hooked back" and
everything works as normal.

With `<<-`, this may look a bit more evident and clear:

```clj
(<<-
  (rule/rule-fn {:foo foo-fn, :bar bar-fn})
  (u/fn3->fn2
    (rule/type-fn {[type1 type2] fn}))
  (rule/rule-fn {:baz baz-fn})
  u/err-fn)
```
