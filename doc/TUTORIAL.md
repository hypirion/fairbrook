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

  While not being essential for many, some projects may need it. Currently,
  many—if not all—functions will destroy the metadata of one value if two
  values are merged together.
* Simple and easy composition of everything mentioned above

In addition, the library is supported from 1.2 and up to the newest snapshot
of Clojure available, and has no dependencies to anything but Clojure.

### When to use Fairbrook

While many project may at first sight have a need to do fine-grained map merges,
it may often be unneccesary and might add an additional level of
complexity. Before you use it, try and find some way around intricate
merging. It is not because Fairbrook is a bad library or increases incidental
complexity, but because you often have a better standpoint when looking at your
problem from different angles. If you after that consider Fairbrook as a good
solution, great!

### What this tutorial covers

This tutorial will cover every single function and macro within Fairbrook. Don't
worry, there aren't that many, and they are very easy to understand
conceptually. This tutorial will first explain how to use the functions
independently, and will then go into detail about how can and were meant to be
used together. Used alone, they can solve many common problems. Used together,
they can solve more delicate merge tasks.
