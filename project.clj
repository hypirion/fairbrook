(defproject fairbrook "0.1.0-SNAPSHOT"
  :description "Fine-grained map manipulation for the masses."
  :url "http://github.com/hyPiRion/fairbrook"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.4.0"]]
  :aliases {"all" ["with-profile" "dev:1.2:1.3:1.5"]}
  :profiles {:1.2 {:dependencies [[org.clojure/clojure "1.2.0"]]}
             :1.3 {:dependencies [[org.clojure/clojure "1.3.0"]]}
             :1.5 {:dependencies [[org.clojure/clojure "1.5.0-beta2"]]}})
