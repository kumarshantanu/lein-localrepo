(ns leiningen.test-localrepo
  (:require [leiningen.localrepo.internal :as in]
            [clojure.xml :as xml]
            [clojure.pprint :as ppr])
  (:use clojure.test))


(def xml-test-data
  [["<?xml version=\"1.0\" encoding=\"UTF-8\"?>
<project>project</project>" {:project ["project"]}]
   ["<?xml version=\"1.0\" encoding=\"UTF-8\"?>
<project>
  <elem>value 1</elem>
  <elem>value 2</elem>
</project>" {:project {:elem ["value 1" "value 2"]}}]
   ["<?xml version=\"1.0\" encoding=\"UTF-8\"?>
<project>
  <elem1>
    <child>Child</child>
  </elem1>
</project>" {:project {:elem1 {:child ["Child"]}}}]
   ["<?xml version=\"1.0\" encoding=\"UTF-8\"?>
<project>
  <elem1>
    <child1>Child 1 - instance 1</child1>
    <child1>Child 1 - instance 2</child1>
    <child2>Child 2</child2>
  </elem1>
</project>" {:project {:elem1 {:child1 ["Child 1 - instance 1"
                                          "Child 1 - instance 2"]
                                 :child2 ["Child 2"]}}}]])


(deftest test-xml-parsing
  (println "=================================================")
  (doseq [each-data xml-test-data]
    (let [[xml-str xml-data] each-data
          parsed-xml  (xml/parse (java.io.StringBufferInputStream.
                                  xml-str))
          parsed-data (in/xml-map parsed-xml)]
      (is (not (string? parsed-data)))
      (println "<<<<<<<<<<<<")
      (println xml-str)
      (ppr/pprint parsed-xml)
      (ppr/pprint parsed-data)
      (println ">>>>>>>>>>>>")
      (is (= xml-data parsed-data)))))
