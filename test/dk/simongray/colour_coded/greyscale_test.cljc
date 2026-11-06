(ns dk.simongray.colour-coded.greyscale-test
  (:require [clojure.test :refer [deftest is]]
            [dk.simongray.colour-coded :as cc]
            [dk.simongray.colour-coded.greyscale :as greyscale]
            [dk.simongray.colour-coded.test-data :as data]))

;; an engine floors or rounds, and its numbers can sit a little either side
(deftest agrees-with-real-engines
  (let [{:strs [colours engines]} data/greyscale]
    (doseq [[engine {:strs [conversion greys]}] engines
            [colour grey]                       (map vector colours greys)
            :let [b (* 255 (greyscale/value colour (keyword conversion)))]]
      (is (contains? (hash-set (long (Math/floor (- b 0.01))) (long (Math/round (+ b 0.01)))) grey)
          (str colour " is " grey " in " engine ", from " b)))))

(deftest a-grey-stays-itself
  (doseq [x ["#000000" "#555555" "#808080" "#cccccc" "#ffffff"]
          k (keys greyscale/conversions)]
    (is (data/close? 2e-3 (first (:coords (cc/to x :srgb))) (greyscale/value x k))
        (str x " by " k))))

(deftest greys
  (is (= :srgb (:space (greyscale/grey "red" :pdf))))
  (is (= [0.3 0.3 0.3] (:coords (greyscale/grey "red" :pdf))))
  (is (= 0.5 (:alpha (greyscale/grey "rgb(255 0 0 / 50%)" :pdf))))
  (is (= "#4d4d4d" (cc/hex (greyscale/grey "red" :pdf)))))

(deftest outside-srgb-clips
  (is (= (greyscale/value "#00ff00" :pdf)
         (greyscale/value {:space :srgb :coords [-0.5 1.5 0.0]} :pdf))))

(deftest some-of-the-conversions
  (is (= #{:pdf :icc} (set (keys (greyscale/values "red" [:pdf :icc]))))))
