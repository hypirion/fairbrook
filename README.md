[![Build Status](https://travis-ci.org/hyPiRion/fairbrook.png)](https://travis-ci.org/hyPiRion/fairbrook)

# fairbrook

<img src="http://hypirion.com/imgs/fairbrook.png" alt="fairbrook logo"
 title="fairbrook" align="right" />

fairbrook is a Clojure library designed to make it easier to make more
fine-grained map merges. Its design is heavily based upon higher-order
functions: Most functions take one optional function as input and returns a
function. This is done to make it easy, yet simple, to design chains of rules.

fairbrook is currently compatible with clojure 1.2 up to 1.5-beta2, and has no
other dependencies. It is also still in the design phase, but the current API
for `0.1.0` is more or less finished. However, if you're interested in using it,
please be patient for a couple of more days, for sanity's sake :)

## Usage

To use fairbrook within your own Clojure programs and libraries, plug this in
within your `project.clj`:

```clj
[fairbrook "0.1.0"] ; (NB: not yet released)
```

To get a better understanding of fairbrook, have a look at the tutorial when it
is written.

## License

The fairbrook logo is a closeup from the image *Falls Of The Ogwen, Nant
Frangon*, which is in the public domain because its copyright has expired.

Copyright © 2012 Jean Niklas L'orange

Distributed under the Eclipse Public License, the same as Clojure.
