(defproject fairbrook "0.2.0-SNAPSHOT"
  :description "Fine-grained map manipulation for the masses."
  :url "http://github.com/hyPiRion/fairbrook"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.2.0"]]
  :aliases {"all" ["with-profile" "1.2:1.3:1.4:1.5"]}
  :plugins [[codox "0.6.4"]]
  :test-selectors {:default (complement :benchmark)
                   :benchmark :benchmark}
  :test-paths ["test/correctness"]
  :profiles {:1.2 {:dependencies [[org.clojure/clojure "1.2.0"]]}
             :1.3 {:dependencies [[org.clojure/clojure "1.3.0"]]}
             :1.4 {:dependencies [[org.clojure/clojure "1.4.0"]]}
             :1.5 {:dependencies [[org.clojure/clojure "1.5.1"]]}
             :dev {:dependencies [[criterium "0.3.1"]
                                  [org.clojure/clojure "1.4.0"]]
                   :test-paths ["test/benchmark"]}})
