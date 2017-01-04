(defproject oiiku-mongodb "0.4.0-SNAPSHOT"
  :description "Common utils for MongoDB"
  :license {:name "New BSD license"}
  :url "https://github.com/oiiku/oiiku-mongodb-clojure"
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [com.novemberain/monger "1.6.0"]
                 [bultitude "0.2.2"]]
  :deploy-repositories
  {"releases" {:url "http://148.251.86.208:8081/nexus/content/repositories/releases"}
   "snapshots" {:url "http://148.251.86.208:8081/nexus/content/repositories/snapshots"}})
