(ns fairbrook.test.combined
  (:use [clojure.test])
  (:require [fairbrook.path :as path]
            [fairbrook.rule :as rule]
            [fairbrook.util :as u]
            [fairbrook.key :as key])
  (:import [clojure.lang IPersistentVector IPersistentSet]))

(deftest test-combined-fns
  (testing "that combining different functions works properly"
    (let [m-fn (u/<<-
                (rule/rule-fn {:a concat, :b *})
                (u/fn3->fn2
                 (u/<<-
                  (rule/type-fn {IPersistentSet into, Number +})
                  (rule/cond-fn [[#(and (vector? %)
                                        (vector? %2)) #(map vector % %2)]])))
                (rule/rule-fn {:c u/left}))]
      (are [maps expected] (= (reduce (key/merge-with-key-fn m-fn) maps)
                              expected)

           [{:a [1 2 3]} {:a [4 5 6]}]
           #_=> {:a '(1 2 3 4 5 6)}

           [{:b 3, :g nil, :d 10} {:b 7, :d 32} {:g 9}]
           #_=> {:b 21, :g 9, :d 42}

           [{:foo #{1 5 7}, :bar 9} {:bar 10} {:quux [1 2], :foo #{0}}
            {:quux [3 4], :foo #{2 6}, :bar 1}]
           #_=> {:foo #{0 1 2 5 6 7}, :bar 20, :quux '([1 3] [2 4])}

           [{:c nil, :quux 4} {:c 2, :quux [1 2 3]} {:bar [4 5] :c 4}
            {:quux [6 7 8], :c 12, :bar '[a b c]}]
           #_=> {:c nil, :quux '([1 6] [2 7] [3 8]), :bar '([4 a] [5 b])}

           [{:c [1 2]} {:c [3 4]}]
           #_=> {:c '([1 3] [2 4])}

           [{:c #{1 3 5 'c}} {:c '#{5 a b c}}]
           #_=> {:c '#{1 3 5 a b c}}))

    (let [m-fn (u/<<-
                (rule/type2-fn {[IPersistentVector Number] conj
                                [Number IPersistentVector] #(conj %2 %1)
                                [IPersistentVector IPersistentVector] into})
                (rule/cond-fn {#(and (odd? %) (odd? %2)) *})
                (rule/type-fn {Number +}))]

      (are [maps expected]  (= (reduce #(merge-with m-fn %1 %2) maps)
                               expected)

           [{:a 1 :b [] :c [1 2]} {:b 1} {:a [3 4]} {:c [2 3] :a 3 :b [8 9]}]
           #_=> {:a [3 4 1 3], :b [1 8 9], :c [1 2 2 3]}

           [{:a 4, :b 5, :c 8} {:a 3, :b 9, :c 8} {:a 1, :b 2, :c 8}]
           #_=> {:a 7, :b 47, :c 24}

           (take 5 (cycle [{:a 1 :b [2]} {:a [3] :b 4}]))
           #_=> {:a [3 1 1 3 1], :b [2 4 2 4 2]}

           (repeat 5 {:a 3, :b 2})
           #_=> {:a 243, :b 10}

           (map (partial array-map :a) (range 10))
           #_=> {:a 4545}))))
