(ns fairbrook.test.key
  (:use [clojure.test]
        [fairbrook.key])
  (:require [fairbrook.rule :as rule]
            [fairbrook.util :as u]))

(deftest test-key-merge
  (testing "that key-merge works as specified"
    (let [rules {:a into, :b concat, :d zipmap}]
      (are [maps expected]
           (= (apply key-merge rules maps) expected)

           []
           #_=> nil

           [{}]
           #_=> {}

           [{:a :b :b :c :d :e}]
           #_=> {:a :b :b :c :d :e}

           [{:a (list 4 5)} {:a [3 2]} {:a #{1}}]
           #_=> {:a '(1 2 3 4 5)}

           [{:b [1 2]} {:b '(3 4)}]
           #_=> {:b '(1 2 3 4)}

           [{:d [:a :b :c]} {:d [1 2 3]}]
           {:d {:a 1, :b 2, :c 3}}

           [{:c [8 9]} {:c [10 11]}]
           #_=> {:c [10 11]}

           [{:a 1} {:b 2} {:c 3} {:d 4}]
           #_=> {:a 1, :b 2, :c 3, :d 4}

           [{:a [1 2]} {:b '(2 3)} {:a [5 6], :b '(8 9)}]
           #_=> {:a [1 2 5 6], :b '(2 3 8 9)}

           [{:c :foo, :d []} {:c :bar, :d [7 8 9]}]
           #_=> {:c :bar, :d {}}))))

(deftest test-key-merge-with
    (testing "that key-merge-with works as specified"
      (let [rules {:a into, :b concat, :d zipmap}
            default +]
      (are [maps expected]
           (= (apply key-merge-with rules default maps) expected)

           []
           #_=> nil

           [{}]
           #_=> {}

           [{:a :b :b :c :d :e}]
           #_=> {:a :b :b :c :d :e}

           [{:a (list 4 5)} {:a [3 2]} {:a #{1}}]
           #_=> {:a '(1 2 3 4 5)}

           [{:b [1 2]} {:b '(3 4)}]
           #_=> {:b '(1 2 3 4)}

           [{:d [:a :b :c]} {:d [1 2 3]}]
           {:d {:a 1, :b 2, :c 3}}

           [{:c 17} {:c 18} {:c 19}]
           #_=> {:c 54}

           [{:a 1} {:b 2} {:c 3} {:d 4}]
           #_=> {:a 1, :b 2, :c 3, :d 4}

           [{:a [1 2]} {:b '(2 3)} {:a [5 6], :b '(8 9)}]
           #_=> {:a [1 2 5 6], :b '(2 3 8 9)}

           [{:c -1021, :d []} {:c 1022, :d [7 8 9]}]
           #_=> {:c 1, :d {}}))))

(deftest test-merge-with-key
  (testing "that merge-with-key invokes f with 3 arguments on collision"
    (let [f (fn [a b c] (cond (= a :a) b, (= a :b) [b c], :else [a b c]))]
      (are [maps expected]
           (= (apply merge-with-key f maps) expected)

           []
           #_=> nil

           [{}]
           #_=> {}

           [{:a :b :b :c :d :e}]
           #_=> {:a :b :b :c :d :e}

           [{:a '(4 5)} {:a [3 2]} {:a #{1}}]
           #_=> {:a '(4 5)}

           [{:b [1 2]} {:b '(3 4)}]
           #_=> {:b [[1 2] '(3 4)]}

           [{:c 17} {:c 18} {:c 19}]
           #_=> {:c [:c [:c 17 18] 19]}

           [{:a 1} {:b 2} {:c 3} {:d 4}]
           #_=> {:a 1, :b 2, :c 3, :d 4}

           [{:a [1 2]} {:b '(2 3)} {:a [5 6], :b '(8 9)}]
           #_=> {:a [1 2], :b '[(2 3) (8 9)]}))))
