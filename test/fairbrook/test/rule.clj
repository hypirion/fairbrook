(ns fairbrook.test.rule
  (:use [clojure.test]
        [fairbrook.rule]))

(deftest test-type-fn
  (testing "that type-fn dispatches correctly on type"
    (are [rules maps expected]
         (= (reduce #(merge-with (type-fn rules) %1 %2) maps)
            expected)

         {Number +, Boolean #(and %1 %2)}
         [{:a 1, :b false} {:a 2, :b true}
          {:a 3, :b true}]
         #_=> {:a 6, :b false}

         {Number *, clojure.lang.IPersistentVector into}
         [{1 2 3 [4 5 6]} {4 9 3 [0 9 7]}
          {1 8 4 3 3 [-1]}]
         #_=> {1 16, 4 27, 3 [4 5 6 0 9 7 -1]}

         {clojure.lang.IPersistentMap merge, clojure.lang.IPersistentSet into,
          clojure.lang.Sequential cons}
         [{:a {1 2}, :b #{1 2 5 9}, :c ()} {:a {3 4, 1 3}, :b #{2 3 4},
                                            :c '(1 2 3), :d 'foo}
          {:a {1 2, :foo :bar}, :b #{1 2 3}, :c '(bar zap quux)}]
         #_=> {:a {1 2, 3 4, :foo :bar}, :b #{1 2 3 4 5 9},
               :c '((() 1 2 3) bar zap quux), :d 'foo}

         {clojure.lang.Seqable into, Number *}
         [{:a [], :b (), :c 5} {:a '(1 2 3), :b [1 2 3], :c 6} {:a [], :b [2]}
          {:c 14, :r '[bloop]} {:a {:is :seqable} :r '[zap foo]}]
         #_=> {:a [1 2 3 [:is :seqable]], :b '(2 3 2 1), :c (* 5 6 14)
               :r '[bloop zap foo]})

    (testing "that type-fn defaults to rightmost element when no type matches"
      (are [rules maps expected]
           (= (reduce #(merge-with (type-fn rules) %1 %2) maps)
              expected)

           {}
           [{:a 1, :b 2, :c :d} {:a 5} {:a 3} {:b 4} {:c 4, :b 9}]
           #_=> {:a 3, :b 9, :c 4}

           {Number +, clojure.lang.IPersistentVector into}
           [{:a 1, :b [], :c nil, :d :F} {:a 2, :b (), :c 1, :d []}
            {:a [1 2], :b [1], :c 3, :d [8 7]} {:a 2, :b [2], :c 5 :d :G}]
           #_=> {:a 2, :b [1 2], :c 9, :d :G}

           {clojure.lang.IPersistentList cons}
           [{:a 1, :b '(1), :c 4} {:a '(a b c), :b '(2 3)}
            {:a '(d e f) :c ()} {:e 3, :a nil, :c '(1 2 3)}]
           #_=> {:a nil, :b '((1) 2 3), :c '(() 1 2 3), :e 3}

           {Object (fn [a _] a)}
           [{:a 1, :b :foo, :c 'f, [] {}} {:a 2, :b nil, :c :g, [] []}
            {:a nil, :b :bar, :c :d, [] #{}} {:a 1 :b 2 :c nil [] '()}]
           #_=> {:a 1, :b :bar, :c nil, [] {}}))))

(deftest test-cond-fn
  (let [num? number?
        rules [(fn [a b] (and (num? a) (>= 1 a))) (constantly :one)
               (fn [a b] (and (num? a) (>= 2 a))) (constantly :two)
               (fn [a b] (and (num? a) (>= 3 a))) (constantly :three)
               (fn [a b] (and (num? a) (>= 4 a))) (constantly :four)]]

    (let [merge-fn (cond-fn rules)]
      (testing "that cond-fn doesn't merge if no collision occurs"
        (are [maps expected]
             (= (apply merge-with merge-fn maps) expected)

             [{:a 1} {:b 2} {:c 3} {:e 4}]
             #_=> {:a 1, :b 2, :c 3, :e 4}

             [{1 2 3 4} {5 6 7 8}]
             #_=> {1 2 3 4 5 6 7 8}))

      (testing "that cond-fn iterates over tests in order when given a vector"
        (are [a b expected]
             (= (merge-with merge-fn a b) expected)

             {1 1, 2 2} {1 :a, 2 2}
             #_=> {1 :one, 2 :two}

             {3 3, 4 4} {3 1, 4 2}
             #_=> {3 :three, 4 :four}))

      (testing "that cond-fn defaults to rightmost when no default fn is given"
        (is (= (merge-with merge-fn {:boo :baah} {:boo :foo}) {:boo :foo}))
        (is (= (merge-with merge-fn {1 :one} {1 :uno}) {1 :uno}))))

    (let [merge-fn (cond-fn rules vector)]
      (testing "that cond-fn invokes optional fn if no test return true"
        (are [a b expected]
             (= (merge-with merge-fn a b) expected)

             {1 1, 2 2} {1 :a, 2 2}
             #_=> {1 :one, 2 :two}

             {3 3, 4 4} {3 1, 4 2}
             #_=> {3 :three, 4 :four}

             {:boo :baah} {:boo :foo}
             #_=> {:boo [:baah :foo]}

             {1 :one} {1 :uno}
             #_=> {1 [:one :uno]}

             {:a :b, :c :d} {:a :b}
             #_=> {:a [:b :b], :c :d})))

    (let [rules {(fn [a b] (and (num? a) (= 1 a))) (constantly :one)
                 (fn [a b] (and (num? a) (= 2 a))) (constantly :two)
                 (fn [a b] (and (num? a) (= 3 a))) (constantly :three)
                 (fn [a b] (and (num? a) (= 4 a))) (constantly :four)}
          merge-fn (cond-fn rules vector)]

      (testing "that cond-fn accepts maps instead of vectors"
        (are [a b expected]
             (= (merge-with merge-fn a b) expected)

             {1 1, 2 2} {1 :a, 2 2}
             #_=> {1 :one, 2 :two}

             {3 3, 4 4} {3 1, 4 2}
             #_=> {3 :three, 4 :four}))

      (testing "that cond-fn accepts maps and a default argument"
        (are [a b expected]
             (= (merge-with merge-fn a b) expected)

             {:boo :baah} {:boo :foo}
             #_=> {:boo [:baah :foo]}

             {1 :one} {1 :uno}
             #_=> {1 [:one :uno]}

             {:a :b, :c :d} {:a :b}
             #_=> {:a [:b :b], :c :d}))))

  (let [rules [[map? map?] (constantly :map)
               [number? number?] (constantly :number)
               [map? number?] (constantly :map-number)
               [number? map?] (constantly :number-map)]
        merge-fn (cond-fn rules (constantly :default))]

    (testing "that cond-fn expands test vectors properly"
      (are [a b expected]
           (= (merge-with merge-fn a b) expected)

           {:a {:a :map}, :b {:another :map}} {:a {:mappy :map}, :b 120}
           #_=> {:a :map, :b :map-number}

           {:a 190, :b -109.0} {:a {:? :!} :b 7/6}
           #_=> {:a :number-map, :b :number}

           {:a "a string", :b #"a regex"} {:a 1, :b {:a :map}}
           #_=> {:a :default, :b :default}))))
