(ns dk.simongray.colour-coded.describe-test
  (:require [clojure.test :refer [deftest is testing]]
            [dk.simongray.colour-coded.contrast :as contrast]
            [dk.simongray.colour-coded.describe :as describe]
            [dk.simongray.colour-coded.difference :as difference]
            [dk.simongray.colour-coded.syntax :as syntax]))

(deftest dark-or-light
  (is (describe/dark? "#24476b"))
  (is (describe/light? "#ffd000"))
  (testing "black and white text contrast equally between these greys"
    (is (= [true false] (map describe/dark? ["#757575" "#767676"]))))
  (testing "a translucent colour is as dark as it shows on the canvas"
    (is (describe/light? "rgb(0 0 0 / 0.2)") "a light grey on white")
    (is (describe/dark? "rgb(0 0 0 / 0.2)" "black"))
    (is (describe/dark? "rgb(255 255 255 / 0.2)" "#16181a"))
    (is (describe/light? "rgb(255 255 255 / 0.2)")))
  (testing "a colour is one or the other, as white or black text contrasts more"
    (doseq [hex (vals syntax/named-colours)]
      (is (not= (describe/dark? hex) (describe/light? hex)) hex)
      (is (= (describe/dark? hex)
             (>= (contrast/ratio hex "white") (contrast/ratio hex "black")))
          hex))))

(deftest names
  (testing "each named colour of CSS has its own name"
    (doseq [hex (vals syntax/named-colours)]
      (is (= hex (syntax/named-colours (describe/name hex))) hex)))
  (testing "the later of two names for one colour"
    (is (= ["grey" "cyan" "magenta"] (map describe/name ["#808080" "#00ffff" "#ff00ff"]))))
  (testing "the nearest name to a colour without one"
    (is (= "darkslategrey" (describe/name "#24476b")))
    (is (= "cornflowerblue" (describe/name "#3498db"))))
  (testing "names of your own, and another measure"
    (is (= "navy" (describe/name "#24476b" {"navy" "#000080" "sky" "#87ceeb"})))
    (is (= "red" (describe/name "#ff0101" syntax/named-colours difference/delta-e-2000))))
  (testing "a name whose colour isn't one is skipped"
    (is (= "sky" (describe/name "#24476b" {"navy" "not a colour" "sky" "#87ceeb"})))
    (is (nil? (describe/name "#24476b" {"navy" "not a colour"})))
    (is (nil? (describe/name "#24476b" {})))))
