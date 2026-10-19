(ns dk.simongray.colour-coded.choose-test
  (:require [clojure.test :refer [deftest is]]
            [dk.simongray.colour-coded.choose :as choose]))

(deftest a-seed-to-colour-by
  (is (= "#24476b" (choose/seed ["#ffffff" "#111111" "#24476b"]))
      "the first colour with a hue, past the white and the black that most of the artwork is")
  (is (= "#a90000" (choose/seed ["#a90000" "#24476b"])))
  (is (nil? (choose/seed ["#ffffff" "#808080" "#f4f4f0"])) "none among greys, or off-whites")
  (is (nil? (choose/seed []))))

(deftest contrast
  (is (= "#f0c040" (choose/first-with-contrast ["#1a1a1a" "#f0c040"] "#a90000" 3))
      "the first that stands apart from the background, as podcast-clj's accent")
  (is (nil? (choose/first-with-contrast ["#a01010"] "#a90000" 3)))
  (is (= "white" (choose/most-contrast ["#808080" "white" "#a90000"] "#24476b"))))

(deftest spreading
  (is (= ["#2040a0" "#c03020"] (choose/spread ["#2040a0" "#2448a4" "#c03020"] 0.05))
      "the second blue is too close to the first"))
