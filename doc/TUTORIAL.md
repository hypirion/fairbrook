# Fairbrook

## What is Fairbrook

Fairbrook is a library for doing map merges when a normal `merge` or a
`merge-with` doesn't suffice. While the phrase *"Fine-grained map manipulation
for the masses."* is the library's description, that's partly an overstatement:
The library only covers the "merge" part of map manipulation. The library
currently offers the following:

* Merging maps based on:
  * What key has had a "collision"
  * The type of the values merged:
     * Either by specifying the type both `isa?` element of
     * Or specify for both values independently of the other
  * The *path* to the value - e.g. `[:a :b]` for `1` within `{:a {:b 1}}`
  * "Multimethod dispatch"
     * One function taking both values as input
     * Two functions, each taking one value as input (either `or`ed or `and`ed
       together)
     * One function, taking key/path and the values as input
     * Or three functions, in same fashion as the point two point above
* Functions for easily keeping and/or merging metadata for values

  (While not being essential for many, some projects may need it. Currently,
  many, if not all, functions will destroy the metadata of one value if two
  values are merged together.)
* Simple and easy composition of everything mentioned above

In addition, the library is supported from 1.2 and up to the newest snapshot
of Clojure available, and has no dependencies to anything but Clojure.

