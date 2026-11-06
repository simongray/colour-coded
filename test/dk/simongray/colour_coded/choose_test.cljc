(ns dk.simongray.colour-coded.choose-test
  (:require [clojure.test :refer [deftest is]]
            [dk.simongray.colour-coded :as cc]
            [dk.simongray.colour-coded.choose :as choose]))

(defn- hexes
  "The scheme `m` with its colours as hex colours."
  [m]
  (update-vals m #(some-> % cc/hex)))

;; podcast-clj's tests of scheme
(deftest a-scheme-by-the-colours-of-an-image
  (is (= {:background "#a90000" :text "#ffffff" :accent "#f0c040" :on-accent "#000000" :seed "#a90000"}
         (hexes (choose/scheme ["#a90000" "#1a1a1a" "#f0c040"])))
      "an accent that stands apart from the background, where the dark grey doesn't")
  (is (= ["#002fff" "#ffffff"] ((juxt :accent :on-accent) (hexes (choose/scheme ["#ffd000" "#ffc010"]))))
      "or else the complement, when it stands apart")
  (is (= "#ffffff" (:accent (hexes (choose/scheme ["#a90000" "#a01010"]))))
      "or else the colour of the text, as the complement of dark red is only 2.7:1")
  (is (= "#000000" (:accent (hexes (choose/scheme ["#f4f4f4" "#eeeeee"])))) "and a grey its own complement")
  (is (nil? (:seed (choose/scheme ["#000000" "#ffffff"]))))
  (is (nil? (choose/scheme [])))
  (is (every? cc/colour? (vals (choose/scheme ["red" "blue"])))
      "colour maps in every role, whether given strings or not"))

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
