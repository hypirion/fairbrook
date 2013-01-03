(ns fairbrook.test.combined
  (:use [clojure.test])
  (:require [fairbrook.path :as path]
            [fairbrook.rule :as rule]
            [fairbrook.util :as u]
            [fairbrook.key :as key]
            [fairbrook.meta :as meta])
  (:import [clojure.lang IPersistentVector IPersistentSet]))

(def ^:private union into) ; For clarity - 2-ary only

(deftest test-combined-fns
  (testing "that combining different functions works properly"
    (let [m-fn (u/<<-
                (rule/rule-fn {:a concat, :b *})
                (u/fn3->fn2
                 (u/<<-
                  (rule/type-fn {IPersistentSet union, Number +})
                  (rule/cond-fn [[vector? vector?] #(map vector % %2)])))
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
                     #(min %2 %3)})))
   (path/sub-merge-fn #'rec-fn)))

(deftest test-recursive-fns
  (testing "that path-sensitive merges are recursive and working"
    (are [maps expected]
         (= (reduce (path/merge-with-path-fn [] rec-fn) maps)
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


(def ^:private meta-merge-fn
  (u/<<-
   (rule/rule-fn {[:user :repl-options :init] (partial list 'do),
                  [:aliases] #(key/merge-with-key
                                (fn [k v1 v2]
                                  (println "The alias" k
                                           "is defined multiple times:\nas"
                                           v1 "and as" v2)
                                  (println "Will keep" v1 ","
                                           v2 "is discarded")
                                  v1)
                                %1 %2)
                  [:warn-on-reflection] #(or %1 %2)})
   (u/fn3->fn2
    (rule/cond-fn
     [(u/or-fn (comp :displace meta) (comp :replace meta))
      (meta/ff u/right
               (fn [left right] (merge (dissoc left :displace)
                                      (dissoc right :replace))))

      [(comp :reduce meta) u/_]
      (meta/ff (fn [left right]
                 (-> left meta :reduce
                     (reduce left right)))
               u/left)

      [nil? u/_] u/right
      [u/_ nil?] u/left

      [set? set?] union]))

   (rule/cond3-fn
    {[u/_ map? map?] (path/sub-merge-fn #'meta-merge-fn)})

   (u/fn3->fn2
    (rule/cond-fn
     [[coll? coll?]
      (rule/cond-fn
       {(u/or-fn (comp :prepend meta) (comp :prepend meta))
        (meta/ff concat #(merge %1 (select-keys %2 [:displace])))}
       concat)

      #(= (class %1) (class %2)) u/right]
     (fn [left right]
       (println left "and" right "have a type mismatch merging profiles.")
       right)))))

(def ^:private meta-merge (path/merge-with-path-fn [] meta-merge-fn))


(deftest test-lein-meta-merge-prototype
  (testing "that the prototype works on base cases"
    (are [maps expected] (= (reduce meta-merge maps) expected)

         [{:foo [1 2 3]} {:foo [7 8 9]}]
         #_=> {:foo '(1 2 3 7 8 9)}

         [{:foo (with-meta [1 2 3] {:displace true})}
          {:foo [7 8 9]}]
         #_=> {:foo [7 8 9]}

         [{:foo '[a b c]} {:foo (with-meta [:p :D :f] {:replace true})}]
         #_=> {:foo [:p :D :f]}

         [{:bar [1 2 3]} {:bar nil}]
         #_=> {:bar [1 2 3]}

         [{:bar nil} {:bar [7 8 9]}]
         #_=> {:bar [7 8 9]}

         [{:quux #{:a :b}} {:bar nil} {:quux #{1 2} :bar [2 3 4]}]
         #_=> {:quux #{:a :b 1 2}, :bar [2 3 4]}

         [{:a (with-meta #{1 2 3} {:reduce (constantly '?)})} {:a #{4 5 6}}]
         #_=> {:a '?}

         [{:a (with-meta [] {:reduce (constantly [])})} {:a [1 2]} {:a [3 4]}
          {:a (with-meta 'replaced {:replace true})}]
         #_=> {:a 'replaced}

         [{:a (with-meta [] {:reduce (constantly [])})} {:a [1 2]} {:a [3 4]}
          {:a (with-meta #{1 2 3} {:replace true})} {:a #{7 8 9}}]
         #_=> {:a []} ; reduce isn't lost, :replace is though

         [{:foo "We pick rightmost"} {:foo "as these are same class"}]
         #_=> {:foo "as these are same class"}))

  (testing "that recursing with path works nicely"
    (are [maps expected] (= (reduce meta-merge maps) expected)

         [{:user {:repl-options
                  {:init '(require 'clojure.pprint)}}}
          {:user {:repl-options
                  {:init '(println "take it easy, this works")}}}]
         #_=>
         {:user {:repl-options
                 {:init '(do (require 'clojure.pprint)
                             (println "take it easy, this works"))}}}

         [{:warn-on-reflection true} {:warn-on-reflection false}]
         #_=> {:warn-on-reflection true}

         ;; path-rules are done BEFORE anything else. Even though this test does
         ;; not make semantically sense, it's good to check that it is done
         [{:warn-on-reflection (with-meta [] {:displace true})}
          {:warn-on-reflection 'wont-see-this}]
         #_=> {:warn-on-reflection []}

         [{:warn-on-reflection (with-meta [] {:reduce (constantly '?)})}
          {:warn-on-reflection '[yeah, we get it, this won't be shown]}]
         #_=> {:warn-on-reflection []}))

  (testing "that aliases will warn if attempted to be overwritten"
    (are [maps expected-str]
         (= (with-out-str (reduce meta-merge maps)) expected-str)

         [{:aliases {"foo" "bar"}} {:aliases {"quux" "river"}}
          {:aliases {"dangit" "hey"}}]
         #_=> "" ; No collision.

         [{:aliases {"foo" "bar"}} {:aliases {"foo" "baz"}}]
#_=>
"The alias foo is defined multiple times:
as bar and as baz
Will keep bar , baz is discarded\n"

         [{:aliases {"a" "b"}} {:aliases {"a" "b"}} {:aliases {"a" "c"}}]
#_=>
"The alias a is defined multiple times:
as b and as b
Will keep b , b is discarded
The alias a is defined multiple times:
as b and as c
Will keep b , c is discarded\n"

         [{:aliases {"test" ["do" "midje," "test"]}}
          {:aliases {"test" "midje", "b" "d"}} {:aliases {"b" "c"}}]
#_=>
"The alias test is defined multiple times:
as [do midje, test] and as midje
Will keep [do midje, test] , midje is discarded
The alias b is defined multiple times:
as d and as c
Will keep d , c is discarded\n"

         [{:aliases (with-meta {"a" :b} {:displace true})} {:aliases {"a" :t}}]
         ;; metadata ignored within [:aliases]-merge
#_=>
"The alias a is defined multiple times:
as :b and as :t
Will keep :b , :t is discarded\n")))

(deftest test-example-combinations
  (testing "that \"recursive merge manually\" example work correctly"
    (def rules {[:a :b :c] +, [:a :c] *, [:c :b] into})
    (def merge-fn
      (rule/rule-fn rules
                    (rule/cond3-fn {[(path/subpath?-fn (keys rules)) u/_ u/_]
                                    (path/sub-merge-fn #'merge-fn)}
                                   u/left)))
    (are [a b expected]
         (= (path/merge-with-path merge-fn a b) expected)

         {:c {:b [1 2]}} {:c {:b [3 4]}}
         #_=> {:c {:b [1 2 3 4]}}

         {:a {:b {:c 10}, :c 10}} {:a {:b {:c 2}, :c 2}}
         #_=> {:a {:b {:c 12}, :c 20}}

         {:c {:not :subpath}} {:c {:a :b}}
         #_=> {:c {:a :b, :not :subpath}}))

  (testing "that the \"general chaining\" example works correctly"

    (import 'clojure.lang.IPersistentSet)

    (def int-stuff
      (rule/cond-fn {[odd? odd?] +,
                     [even? even?] *,
                     > -,
                     < #(- %2 %1)}
                    u/err-fn)) ;; This won't happen,
                               ;; but better to be safe than sorry

    (def merge-fn
      (rule/cond-fn {[integer? integer?] int-stuff,
                     [map? map?] (partial merge-with u/left)}
                    (rule/type-fn {IPersistentSet into, ;; poor man's union
                                   [IPersistentSet Number] conj,
                                   [Number IPersistentSet] #(conj %2 %1),
                                   Number hash-set}
                                  u/right)))
    (are [a b expected]
         (= (merge-with merge-fn a b) expected)

         {:a 1 :b {:almost :empty}} {:a 9 :b {}}
         #_=> {:a 10, :b {:almost :empty}}

         {:a 3 :b 4, :c 8} {:a 6 :b 1 :c 4}
         #_=> {:a 3, :b 3, :c 32}))

  (testing "that the `fn3->fn2` examples works correctly."
    (def merge-fn
      (u/<<-
       (rule/rule-fn {:a concat, :b into})
       (u/fn3->fn2
        (rule/cond-fn {[integer? integer?] +}))))

    (are [a b expected]
         (= (key/merge-with-key merge-fn a b) expected)

         {:a [1 2], :b #{1 2}} {:a [3 4], :b #{3 -1}}
         #_=> {:a '(1 2 3 4), :b #{-1 1 2 3}}

         {:c 4, :d :foo} {:c 4, :d :bar}
         #_=> {:c 8, :d :bar})

    (import 'clojure.lang.IPersistentSet)

    (def merge-fn
      (u/<<-
       (rule/rule-fn {:a concat, :b into})
       (u/fn3->fn2
        (u/<<-
         (rule/cond-fn {[integer? integer?] +})
         (rule/type-fn {IPersistentSet into})))))

    (are [a b expected]
         (= (key/merge-with-key merge-fn a b) expected)

         {:a #{1 2}, :c #{1 2}, :d 1} {:a #{2 1}, :c #{1 2}, :d 4}
         #_=> {:a '(1 2 1 2), :c #{1 2}, :d 5}

         {:c [1 2 3]} {:c [4 5 6]}
         #_=> {:c [4 5 6]})

    (import 'clojure.lang.IPersistentSet)

    (def merge-fn
      (u/<<-
       (rule/rule-fn {:a concat, :b into})
       (u/fn3->fn2
        (u/<<-
         (rule/cond-fn {[integer? integer?] +})
         (rule/type-fn {IPersistentSet into})))
       (rule/rule-fn {:c interleave})))

    (are [a b expected]
         (= (key/merge-with-key merge-fn a b) expected)

         {:c #{1 2 3}} {:c #{4 5 6}}
         #_=> {:c #{1 2 3 4 5 6}}

         {:a #{1}, :c [1 2 3]} {:a [4 5 6], :c [4 5 6]}
         #_=> {:a '(1 4 5 6), :c '(1 4 2 5 3 6)}))

  (testing "that the `prep-args` examples work as intended"

    (def f-rules {[:a :b] +, [:a :c] *, [:d] u/left})

    (def subpath? (path/subpath?-fn (keys f-rules)))

    (def merge-fn
      (u/<<-
       (rule/rule-fn f-rules)
       (rule/cond3-fn {[subpath? u/_ u/_] (path/sub-merge-fn #'merge-fn)})
       (u/prep-args [p v1 v2] [(peek p) v1 v2]
                    (rule/rule-fn {:a *, :c +}))))

    (are [a b expected]
         (= (path/merge-with-path merge-fn a b) expected)

         {:a {:b 2, :c 5}} {:a {:b 3, :c 4}}
         #_=> {:a {:c 20, :b 5}}

         {:a {:a 6}, :c 20} {:a {:a 10}, :c 13}
         #_=> {:a {:a 60}, :c 33}

         {:d {:a 15, :c 10}} {:d {:a 10, :c 40}}
         #_=> {:d {:a 15, :c 10}})

    (def f-rules {[:c] *, [:d] u/left})
    (def l-rules {[:a :b] +, [:a :c] *, [:a] vector})

    (def subpath? (path/subpath?-fn (mapcat keys [f-rules l-rules])))

    (def merge-fn
      (u/<<-
       (rule/rule-fn f-rules)
       (rule/cond3-fn {[subpath? u/_ u/_] (path/sub-merge-fn #'merge-fn)})
       (u/prep-args [p v1 v2] [(peek p) v1 v2]
                    (rule/rule-fn {:a *, :c +}))
       (rule/rule-fn l-rules)))

    (are [a b expected]
         (= (path/merge-with-path merge-fn a b) expected)

         {:a {:b 2, :c 5, :a 10}} {:a {:b 3, :c 4, :a 10}}
         #_=> {:a {:a 100, :c 9, :b 5}}

         {:a {:c 2}, :c 10} {:a {:c 10} :c 2}
         #_=> {:a {:c 12}, :c 20})))
