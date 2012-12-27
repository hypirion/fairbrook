(ns fairbrook.test.path
  (:use [clojure.test]
        [fairbrook.path]))

(deftest test-path-merge
  (testing "that path-merge recurses properly"
    (are [rules maps expected]
         (= (apply path-merge rules maps) expected)

         {[:a] +} [{:a 1} {:a 2}]
         #_=> {:a 3}

         {[:a :b :c :d :e] into}
         [{:a {:b {:c {:d {:e [1 2 3]}}}}} {:a {:b {:c {:d {:e [4 5 6]}}}}}]
         #_=> {:a {:b {:c {:d {:e [1 2 3 4 5 6]}}}}}

         {[:a :e] +, [:a :f] -}
         [{:a {:e 10, :f 30}} {:a {:e 5, :g 90}}]
         #_=> {:a {:e 15, :f 30, :g 90}}

         {[:a :b] *, [:a] +}
         [{:a 10} {:a 30}]
         #_=> {:a 40}

         {[:a] merge, [:a :b] +}
         [{:a {:b 10}} {:a {:b 20}}]
         #_=> {:a {:b 20}}))

  (testing "that path-merge picks right value when not recursing"
    (are [rules maps expected]
         (= (apply path-merge rules maps) expected)

         {[:a] *}
         [{:b {:c 10}} {:b {:c :d}}]
         #_=> {:b {:c :d}}

         {[:a :b] concat}
         [{:a {:b [1 2 3], :c 1}} {:a {:b [4 5 6]}} {:a {:c 2}}]
         #_=> {:a {:b [1 2 3 4 5 6], :c 2}}

         {[3 4 5] +}
         [{3 {4 {5 6}} :b 10} {3 {4 {5 7}} :b 90}]
         #_=> {3 {4 {5 13}} :b 90}

         {[0 1 2] into}
         [{:r 10} {0 {1 {2 []}}} {0 {2 {3 10} 1 {2 [10]}}} {:r {:g {:f 10}}}]
         #_=> {:r {:g {:f 10}}, 0 {1 {2 [10]}, 2 {3 10}}}

         {}
         [{1 0} {1 0}]
         #_=> {1 0})))

(deftest test-path-merge-with
  (testing "that path-merge-with applies the default fn when merging non-rules"
    (are [rules f maps expected]
         (= (apply path-merge-with rules f maps) expected)

         {} (fn [v v2] [v v2])
         [{:a 10, :b :buxor} {:a :yxa} {:c :not-merged} {:b :xerxes}]
         #_=> {:a [10 :yxa], :b [:buxor :xerxes], :c :not-merged}

         {} (fn [& r] r)
         [{:a :this} {:a :is, :f :foo} {:a :nested}]
         #_=> {:a '((:this :is) :nested), :f :foo}

         {[:a] +, [:c] *} (fn [a b] (* 2 a b))
         [{:a 10, :b 1} {:b 10, :c 9} {:a 11, :b 1, :c 3}]
         #_=> {:a 21, :b 40, :c 27}

         {[:a :b] +, [:a :c] *} (fn [_ x] x)
         [{:a {:b 10} :d {:woof true :bark false}} {:a {:b 2 :c 9}}
          {:a {:c 4 :d 4, :b 3} :d {:bark true, :chasing :cat}}]
         #_=> {:a {:b 15, :c 36, :d 4}
               :d {:bark true :chasing :cat}}

         {[:r :e] into, [:g :b] zipmap} (fn [x _] x)
         [{:g {:b [1 3 5 7 9]}, :a :keep} {:r {:e [2 3], :h :foo}}
          {:g {:b [2 4 6 8 10]}, :a :discard, :r {:e [5 6] :h :bar}}]
         #_=> {:g {:b {1 2 3 4 5 6 7 8 9 10}},
               :r {:e [2 3 5 6] :h :foo}, :a :keep})))
