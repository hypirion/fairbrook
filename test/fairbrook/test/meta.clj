(ns fairbrook.test.meta
  (:use [clojure.test]
        [fairbrook.meta]))

(defn right [a b] b)
(defn left [a b] a)

(def a ^{:baz 10, :foo true} {:a 1})
(def b ^{:bar true, :foo false} {:b 2})
(def c ^{:quux [1 2 3], :baz "hello"} {:c 3})
(def d ^{:test true, :foo true} {:d 4})

(deftest test-meta-ff
  (let [rl (ff right left)
        rl-res (rl a b)
        
        lr (ff left right)
        lr-res (lr a b)

        mmerge2 (ff merge merge)
        mmerge2-res (mmerge2 a b)]
    (testing "that ff applies data-fn correctly for two args"
      (are [actual expected] (= actual expected)
           lr-res a
           rl-res b
           mmerge2-res (merge a b)))

    (testing "that ff applies metadata-fn correctly for two args"
      (are [actual expected] (= (meta actual) expected)
           lr-res (meta b)
           rl-res (meta a)
           mmerge2-res (merge (meta a) (meta b)))))
  (let [conc-empty (ff concat (constantly {}))
        conc-empty-res (conc-empty ^{:foo :bar}             [:a :b :c]
                                   ^{:baz 176}              [1 42 187]
                                   ^{:spam 10 :single true} [5])

        union-+ (ff (fn [& args] (reduce into args)) ; <- poor man's union
                    (partial merge-with +))
        union-+-res (union-+ ^{:foo 154 :zip 19}       #{:a :b :c}
                             ^{:foo 46 :bar 10}        #{1 42 187}
                             ^{:bar 20 :is :untouched} #{1 :a})

        conj-m-init (ff conj (partial merge {:foo 213, :a "egg"}))
        conj-m-init-res (conj-m-init ^{:foo :bar, :baz 123} [1 2 3]
                                     ^{:b 10}               [4]
                                     ^{:doc "none"}         #{10})]
    (testing "that ff applies data-fn correctly for multiple arguments"
      (are [actual expected] (= actual expected)
           conc-empty-res  [:a :b :c 1 42 187 5]
           union-+-res     #{:a :b :c 1 42 187}
           conj-m-init-res [1 2 3 [4] #{10}]))
    
    (testing "that ff applies metadata-fn correctly for multiple arguments"
      (are [actual expected] (= (meta actual) expected)
           conc-empty-res {}
           union-+-res {:foo 200, :bar 30, :zip 19, :is :untouched}
           conj-m-init-res {:foo :bar, :a "egg" :baz 123,
                            :b 10, :doc "none"}))))

(deftest test-meta-fm
  (let [r (fm right)
        r-res (r a b)
        
        l (fm left)
        l-res (l a b)

        mmerge (fm merge)
        mmerge-res (mmerge a b)]
    (testing "that fm applies data-fn correctly for two args"
      (are [actual expected] (= actual expected)
           l-res a
           r-res b
           mmerge-res (merge a b)))

    (testing "that fm merges metadata correctly for two args"
      (are [res] (= (meta res) (merge (meta a) (meta b)))
           r-res
           l-res
           mmerge-res)))

  (let [concat- (fm concat)
        concat-res (concat- ^{:a 1} [1 2 3]
                            ^{:b 2} [nil nil nil]
                            ^{:b 1, :c 3} [:D])

        disj- (fm disj)
        disj-res (disj- ^{:type :set} #{'(1 2) [3]}
                        ^{:type :vec, :count 1} [3]
                        (with-meta '(1 2) {:type :list, :count 2}))

        largest (fm (partial max-key count))
        largest-res (largest ^{:type :set, :mood :happy} #{:D :-D}
                             ^{:type :vec, :state :empty} []
                             (with-meta '(1 2 3 4 5 6)
                               {:type :list, :contains :ints})
                             ^{:type :map, :meta? false} {:a :b, :foo :bar})]
    
    (testing "that fm applies data-fn correctly for multiple arguments"
      (are [actual expected] (= actual expected)
           concat-res [1 2 3 nil nil nil :D]
           disj-res #{}
           largest-res '(1 2 3 4 5 6)))

    (testing "that fm mergest metadata correctly for multiple arguments"
      (are [actual expected] (= (meta actual) expected)
           concat-res {:a 1, :b 1, :c 3}
           disj-res {:type :list, :count 2}
           largest-res {:type :map, :mood :happy, :state :empty,
                        :contains :ints, :meta? false}))))

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
  (let [a ^{:a 1} [1]
        b ^{:a 2} {}
        c ^{:a 2, :c :a} [2]]
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
