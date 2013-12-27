[![Build Status](https://travis-ci.org/hyPiRion/fairbrook.png)](https://travis-ci.org/hyPiRion/fairbrook)

# fairbrook

<img src="http://hypirion.com/imgs/fairbrook.png" alt="fairbrook logo"
 title="fairbrook" align="right" />

fairbrook is a Clojure library designed to make it easier to make more
fine-grained map merges. Its design is heavily based upon higher-order
functions: Most functions take one optional function as input and returns a
function. This is done to make it easy—yet simple—to design chains of rules.

fairbrook is compatible with Clojure 1.2 up to 1.5-RC1, and has no other
dependencies than Clojure itself. fairbrook is currently an inactive project,
but will be revived to attempt to revive Leiningen's merge policies. There will
be breaking changes, so do not rely on the current implementation.

If you want a taste, skim through the [examples][] for a better feel on what it
looks like in action.

[examples]: https://github.com/hyPiRion/fairbrook/blob/master/doc/EXAMPLES.md

## Usage

To use fairbrook within your own Clojure programs and libraries, add this to
your `project.clj` dependencies:

```clj
[fairbrook "0.1.0"]
```

To get a better understanding of fairbrook, have a look at the [tutorial][].
[tutorial]: https://github.com/hyPiRion/fairbrook/blob/master/doc/TUTORIAL.md

## License

The fairbrook logo is a closeup from the image *Falls Of The Ogwen, Nant
Frangon*, which is in the public domain because its copyright has expired.

Copyright © 2012-2013 Jean Niklas L'orange

Distributed under the Eclipse Public License, the same as Clojure.
