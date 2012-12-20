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
          {:a 3, :b true}] #_=> {:a 6, :b false}

         {Number *, clojure.lang.IPersistentVector into}
         [{1 2 3 [4 5 6]} {4 9 3 [0 9 7]}
          {1 8 4 3 3 [-1]}] #_=> {1 16, 4 27, 3 [4 5 6 0 9 7 -1]}

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

    ;; TODO: More tests...
    ))
