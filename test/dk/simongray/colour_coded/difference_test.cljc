(ns dk.simongray.colour-coded.difference-test
  (:require [clojure.test :refer [deftest is testing]]
            [dk.simongray.colour-coded.difference :as difference]
            [dk.simongray.colour-coded.test-data :as data]))

(deftest ciede2000-by-sharma-wu-and-dalal
  (is (= 34 (count data/ciede2000)))
  (doseq [[l1 a1 b1 l2 a2 b2 expected] data/ciede2000]
    (is (data/close? 0.00005 expected
                     (difference/delta-e-2000 {:space :lab :coords [l1 a1 b1]}
                                              {:space :lab :coords [l2 a2 b2]}))
        (str [l1 a1 b1] " and " [l2 a2 b2]))))

(deftest differences-agree-with-colour-js
  (doseq [{:strs [a b] :as d} (get data/colorjs "differences")
          [k f]               [["delta-e" difference/delta-e]
                               ["delta-e-76" difference/delta-e-76]
                               ["delta-e-2000" difference/delta-e-2000]]]
    (testing k
      (is (data/close? 1e-9 (get d k) (f (data/fixture-colour a) (data/fixture-colour b)))))))

(deftest nearest
  (is (= "orange" (difference/nearest "#ff9900" ["red" "orange" "yellow"])))
  (is (= "red" (difference/nearest "#dd1100" ["red" "orange" "yellow"] difference/delta-e-2000)))
  (is (nil? (difference/nearest "red" []))))

(deftest least
  (is (= (difference/delta-e "orange" "#ff9900")
         (difference/least ["red" "orange" "#ff9900" "blue"])))
  (is (= (difference/delta-e-2000 "red" "blue")
         (difference/least ["red" "blue"] difference/delta-e-2000)))
  (is (nil? (difference/least ["red"])))
  (is (nil? (difference/least []))))
