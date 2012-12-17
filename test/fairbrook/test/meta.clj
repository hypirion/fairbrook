(ns fairbrook.test.meta
  (:use [clojure.test]
        [fairbrook.meta]))

(defn- right [a b] b)
(defn- left [a b] a)

(def ^:private a (with-meta {:a 1} {:baz 10, :foo true}))
(def ^:private b (with-meta {:b 2} {:bar true, :foo false}))
(def ^:private c (with-meta {:c 3} {:quux [1 2 3], :baz "hello"}))
(def ^:private d (with-meta {:d 4} {:test true, :foo true}))

(deftest test-meta-ff
  (let [rl (ff right left)
        rl-res (rl a b)
        
        lr (ff left right)
        lr-res (lr a b)

        mmerge2 (ff merge merge)
        mmerge2-res (mmerge2 a b)]
    (testing "that ff applies data-fn correctly"
      (are [actual expected] (= actual expected)
           lr-res a
           rl-res b
           mmerge2-res (merge a b)))

    (testing "that ff applies metadata-fn correctly"
      (are [actual expected] (= (meta actual) expected)
           lr-res (meta b)
           rl-res (meta a)
           mmerge2-res (merge (meta a) (meta b))))))

(deftest test-meta-fm
  (let [r (fm right)
        r-res (r a b)
        
        l (fm left)
        l-res (l a b)

        mmerge (fm merge)
        mmerge-res (mmerge a b)]
    (testing "that fm applies data-fn correctly"
      (are [actual expected] (= actual expected)
           l-res a
           r-res b
           mmerge-res (merge a b)))

    (testing "that fm merges metadata correctly"
      (are [res] (= (meta res) (merge (meta a) (meta b)))
           r-res
           l-res
           mmerge-res))))

(deftest test-meta-duo-apply
  (let [keep-right-left (duo-apply [merge-with right]
                                   [merge-with left] [a b c d])
        keep-left-right (duo-apply [merge-with left]
                                   [merge-with right] [a b c d])
        keep-right-right (duo-apply [merge-with right]
                                    [merge-with right] [a b c d])
        keep-left-left (duo-apply [merge-with left]
                                  [merge-with left] [a b c d])]

    (testing "that duo-apply applies data-fn correctly for longer chains"
      (are [actual expected] (= actual (apply merge expected))
           keep-right-left [a b c d]
           keep-right-right [a b c d]
           keep-left-left [a b c d]
           keep-left-right [a b c d]))
    
    (testing "that duo-apply applies metadata-fn correctly for longer chains"
      (are [actual expected] (= (meta actual) (apply merge (map meta expected)))
           keep-left-right [a b c d]
           keep-right-right [a b c d]
           keep-left-left [d c b a]
           keep-right-left [d c b a])))
  (let [a (with-meta [1] {:a 1})
        b (with-meta {} {:a 2})
        c (with-meta [2] {:a 2, :c :a})]
    (testing "that duo-apply applies data-fn correctly for two elements"
      (are [actual expected] (= actual expected)
           (duo-apply [conj] [merge-with right]
                      [a b]) [1 {}]
           (duo-apply [concat] [merge-with left]
                      [a c]) [1 2]))
    
    (testing "that duo-apply applies metadata-fn correctly for two elements"
      (are [actual expected] (= (meta actual) expected)
           (duo-apply [conj] [merge-with right]
                      [a b]) {:a 2}
           (duo-apply [concat] [merge-with left]
                      [a c]) {:a 1, :c :a}))))
