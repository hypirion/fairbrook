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

(comment
  ;; This is why we don't do test-driven development guys.
  (deftest test-path-merge-with
    (testing "that path-merge-with applies the default fn when merging non-rules"
      (are [rules f maps expected]
           (= (apply path-merge-with rules f maps) expected)

           {} (fn [k v v2] [k v v2])
           [{:a 10, :b :buxor} {:a :yxa} {:c :not-merged} {:b :xerxes}]
           #_=> {:a [[:a] 10 :yxa], :b [[:b] :buxor :xerxes], :c :not-merged}

           {} (fn [& r] r)
           [{:a :this} {:a :is, :f :foo} {:a :nested}]
           #_=> {:a '([:a] ([:a] :this :is) :nested), :f :foo}

           {[:a] +, [:c] *} (fn [_ a b] (* 2 a b))
           [{:a 10, :b 1} {:b 10, :c 9} {:a 11, :b 1, :c 3}]
           #_=> {:a 21, :b 40, :c 27}

           {[:a :b] +, [:a :c] *} (sub-merge-fn
                                   (fn [k v1 v2]
                                     (({[:a :b] +, [:a :c] *} k (fn [_ a] a))
                                      v1 v2)))
           [{:a {:b 10} :d {:woof true :bark false}} {:a {:b 2 :c 9}}
            {:a {:c 4 :d 4, :b 3} :d {:bark true, :chasing :cat}}]
           #_=> {:a {:b 15, :c 36, :d 4} :d {:woof true :bark true :chasing :cat}}

           {} (sub-merge-fn
               (fn [k v1 v2]
                 (({[:a :b] +, [:a :c] *} k (fn [_ a] a)) v1 v2)))
           [{:a {:b 10} :d {:woof true :bark false}} {:a {:b 2 :c 9}}
            {:a {:c 4 :d 4, :b 3} :d {:bark true, :chasing :cat}}]
           #_=> {:a {:b 15, :c 36, :d 4}
                 :d {:woof true :bark true :chasing :cat}}))))
