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

  (testing "that path-merge will crash when trying to merge non-maps"
    (are [rules maps]
         (thrown? Exception (apply path-merge rules maps))

         {[:a] *}
         [{:b {:c 10}} {:b {:c :d}}]

         {[:a :b] concat}
         [{:a {:b [1 2 3]}} {:a {:b [4 5 6]}} {:a :foo}]

         {[3 4 5] +}
         [{3 {4 {5 6}} :b 10} {3 {4 {5 7}} :b 90}]

         {[0 1 2] into}
         [{:r 10} {0 {1 {2 []}}} {0 {2 {3 10} 1 {2 [10]}}} {:r {:g {:f 10}}}]

         {}
         [{1 0} {1 0}])))
