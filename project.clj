(defproject oiiku-mongodb "0.4.0-SNAPSHOT"
  :description "Common utils for MongoDB"
  :license {:name "New BSD license"}
  :url "https://github.com/oiiku/oiiku-mongodb-clojure"
  :dependencies [[org.clojure/clojure "1.4.0"]
                 [com.novemberain/monger "1.1.0"]
                 [bultitude "0.1.7"]]
  :deploy-repositories
  {"releases" {:url "http://augustl.com:8081/nexus/content/repositories/releases"}
   "snapshots" {:url "http://augustl.com:8081/nexus/content/repositories/snapshots"}})