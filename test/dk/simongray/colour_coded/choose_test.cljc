(ns dk.simongray.colour-coded.choose-test
  (:require [clojure.test :refer [deftest is testing]]
            [dk.simongray.colour-coded :as cc]
            [dk.simongray.colour-coded.choose :as choose]
            [dk.simongray.colour-coded.difference :as difference]))

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
  (is (= "white" (choose/most-contrast ["#808080" "white" "#a90000"] "#24476b")))
  (is (= "rgb(0 0 0 / 0.8)"
         (choose/first-with-contrast ["rgb(0 0 0 / 0.3)" "rgb(0 0 0 / 0.8)"] "white"))
      "a translucent colour as it shows on the background")
  (is (= "#555555" (choose/most-contrast ["rgb(0 0 0 / 0.3)" "#555555"] "white"))))

(deftest spreading
  (is (= ["#2040a0" "#c03020"] (choose/spread ["#2040a0" "#2448a4" "#c03020"] 0.05))
      "the second blue is too close to the first"))

(deftest the-nearest-of-a-list
  (is (= "orange" (choose/nearest "#ff9900" ["red" "orange" "yellow"])))
  (is (= "red" (choose/nearest "#dd1100" ["red" "orange" "yellow"] difference/delta-e-2000)))
  (is (nil? (choose/nearest "red" []))))

(deftest the-nearest-name
  (testing "each named colour of CSS has its own name"
    (doseq [hex (vals cc/named-colours)]
      (is (= hex (cc/named-colours (choose/nearest hex))) hex)))
  (testing "the later of two names for one colour"
    (is (= ["grey" "cyan" "magenta"] (map choose/nearest ["#808080" "#00ffff" "#ff00ff"]))))
  (testing "the nearest name to a colour without one"
    (is (= "darkslategrey" (choose/nearest "#24476b")))
    (is (= "cornflowerblue" (choose/nearest "#3498db"))))
  (testing "names of your own, and another measure"
    (is (= "navy" (choose/nearest "#24476b" {"navy" "#000080" "sky" "#87ceeb"})))
    (is (= "red" (choose/nearest "#ff0101" cc/named-colours difference/delta-e-2000))))
  (testing "a name whose colour isn't one is skipped"
    (is (= "sky" (choose/nearest "#24476b" {"navy" "not a colour" "sky" "#87ceeb"})))
    (is (nil? (choose/nearest "#24476b" {"navy" "not a colour"})))
    (is (nil? (choose/nearest "#24476b" {})))))
