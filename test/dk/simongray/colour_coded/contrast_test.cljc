(ns dk.simongray.colour-coded.contrast-test
  (:require [clojure.test :refer [deftest is]]
            [dk.simongray.colour-coded :as cc]
            [dk.simongray.colour-coded.contrast :as contrast]
            [dk.simongray.colour-coded.gamut :as gamut]
            [dk.simongray.colour-coded.test-data :as data]))

(deftest ratios
  (is (= 21.0 (contrast/ratio "#000000" "#ffffff")))
  (is (= 1.0 (contrast/ratio "#a90000" "#a90000")))
  (is (= (contrast/ratio "#24476b" "white") (contrast/ratio "white" "#24476b")))
  (is (data/close? 0.01 4.54 (contrast/ratio "#767676" "white")) "the lightest grey that text on white can be"))

(deftest ratios-agree-with-colour-js
  (doseq [{:strs [a b] :as d} (get data/colorjs "differences")]
    ;; Colour.js takes the luminance from XYZ, which differs from WCAG's
    ;; coefficients in the fourth decimal
    (is (data/close? 0.002 (get d "contrast")
                     (contrast/ratio (gamut/clip (data/fixture-colour a))
                                     (gamut/clip (data/fixture-colour b)))))))

(deftest levels
  (is (= [:aaa :aa :large nil]
         [(contrast/level "black" "white")
          (contrast/level "#767676" "white")
          (contrast/level "#949494" "white")
          (contrast/level "#cccccc" "white")])))

(deftest black-or-white
  (is (= ["#ffffff" "#000000" "#ffffff"] (map (comp cc/hex contrast/black-or-white) ["#a90000" "#f0c040" "#000000"]))
      "as podcast-clj's contrast-color chose")
  (is (= "#000000" (cc/hex (contrast/black-or-white "#2277d3")))
      "by WCAG 2, though white reads better on this mid blue"))
