(ns fairbrook.test.combined
  (:use [clojure.test])
  (:require [fairbrook.path :as path]
            [fairbrook.rule :as rule]
            [fairbrook.util :as u]
            [fairbrook.key :as key])
  (:import [clojure.lang IPersistentVector IPersistentSet]))

(def ^:private union into) ; For clarity - 2-ary only

(deftest test-combined-fns
  (testing "that combining different functions works properly"
    (let [m-fn (u/<<-
                (rule/rule-fn {:a concat, :b *})
                (u/fn3->fn2
                 (u/<<-
                  (rule/type-fn {IPersistentSet union, Number +})
                  (rule/cond-fn [[[vector? vector?] #(map vector % %2)]])))
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
                (rule/type-fn {[IPersistentVector Number] conj
                               [Number IPersistentVector] #(conj %2 %1)
                               [IPersistentVector IPersistentVector] into})
                (rule/cond-fn {[odd? odd?] *})
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

(def ^:private rec-fn
  (u/<<-
   (rule/rule-fn {[:dish :price] +,
                  [:dish :name] #(str % " with " %2)})
   (u/prep-args
    [a b c] [(peek a) b c]
    (u/<<-
     (rule/rule-fn {:allergies union, :tip +})
     (rule/cond3-fn {(fn [k & _] (= k :cheapest))
                     #(min %2 %3)})))   ; Sorry, hard to find proper use cases
                                        ; for cond3
   (path/sub-merge-fn #'rec-fn)))

(deftest test-recursive-fns
  (testing "that path-sensitive merges are recursive and working"
    (are [maps expected]
         (= (reduce (path/merge-from-root rec-fn) maps)
            expected)

         [{:dish {:name "sandwich" :price 1}}
          {:dish {:name "salami" :price 2}}]
         #_=> {:dish {:name "sandwich with salami" :price 3}}

         [{:dish {:name "pasta carbonara"
                  :allergies #{:egg :lactose} :price 10}}
          {:dish {:name "feta salad" :allergies #{:lactose} :price 5}}]
         #_=> {:dish {:name "pasta carbonara with feta salad",
                      :allergies #{:egg :lactose}, :price 15}}

         [{:tip 10 :cheapest 10}
          {:dish {:name "side bacon", :allergies #{:bacon}, :price 5}
           :cheapest 5, :tip 2}
          {:dish {:name "back bacon", :allergies #{:bacon}, :price 7}
           :cheapest 7, :tip 4}]
         #_=> {:tip 16, :cheapest 5,
               :dish {:name "side bacon with back bacon", :allergies #{:bacon}
                      :price 12}}

         [{:dish {:name "peking duck" :price 40 :tip 10
                  :types {:main-dish true}}}
          {:dish {:name "spring onions and cucumber sticks" :price 20, :tip 4
                  :types {:side-dish true}}}
          {:dish {:name "Barolo", :price 40, :tip 10 :types {:wine true}}}]
         #_=>
         {:dish
          {:price 100, :tip 24, :name,
           "peking duck with spring onions and cucumber sticks with Barolo"
           :types {:main-dish true, :side-dish true, :wine true}}}

         [{:allergies #{:birch :cat}} {:allergies #{}} {:allergies #{:cat}}]
         #_=> {:allergies #{:birch :cat}})))
