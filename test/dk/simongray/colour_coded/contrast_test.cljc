(ns dk.simongray.colour-coded.contrast-test
  (:require [clojure.test :refer [deftest is testing]]
            [dk.simongray.colour-coded :as cc]
            [dk.simongray.colour-coded.composite :as composite]
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

(defn- tenths
  "The ratio `r` in tenths, the precision at which the W3C publishes one."
  [r]
  (Math/round (* 10.0 r)))

(deftest translucent-colours
  (testing "the W3C's ACT rule afw4f7, failed examples 4 and 7"
    (is (= 21 (tenths (contrast/ratio "rgba(0,0,0,.3)" "#FFF"))))
    (is (= 42 (tenths (contrast/ratio "rgba(90, 90, 90, 0.8)" "white"))))
    (is (= 23 (tenths (contrast/ratio "rgba(90, 90, 90, 0.8)" "black")))))
  (testing "the first colour as it shows on the second"
    (is (= (contrast/ratio (composite/over "rgb(200 30 60 / 0.4)" "#24476b") "#24476b")
           (contrast/ratio "rgb(200 30 60 / 0.4)" "#24476b")))
    (is (not= (contrast/ratio "rgb(0 0 0 / 0.3)" "#24476b")
              (contrast/ratio "#24476b" "rgb(0 0 0 / 0.3)"))
        "so the order matters"))
  (testing "the second colour as it shows on the canvas"
    (is (= (contrast/ratio "white" (composite/over "rgb(0 0 0 / 0.9)" "white"))
           (contrast/ratio "white" "rgb(0 0 0 / 0.9)")))
    (is (= 21.0 (contrast/ratio "white" "rgb(0 0 0 / 0.9)" "black")))
    (is (= 1.0 (contrast/ratio "white" "rgb(255 255 255 / 0.1)")))
    (is (< 14 (contrast/ratio "white" "rgb(255 255 255 / 0.1)" "#121212"))
        "as in a dark theme"))
  (testing "a canvas that leaves the second colour translucent"
    (is (nil? (contrast/ratio "white" "rgb(0 0 0 / 0.9)" "rgb(0 0 0 / 0.5)")))
    (is (= 21.0 (contrast/ratio "white" "black" "rgb(0 0 0 / 0.5)"))
        "which an opaque colour never shows"))
  (testing "neither matters for opaque colours"
    (is (= (contrast/ratio "#24476b" "#f0c040")
           (contrast/ratio "#f0c040" "#24476b")
           (contrast/ratio "#24476b" "#f0c040" "black"))))
  (testing "the luminance alone ignores alpha"
    (is (= (contrast/luminance "black") (contrast/luminance "rgb(0 0 0 / 0.3)")))))

(deftest wcag-levels
  (is (= [:aaa :aa :large nil]
         [(contrast/wcag-level "black" "white")
          (contrast/wcag-level "#767676" "white")
          (contrast/wcag-level "#949494" "white")
          (contrast/wcag-level "#cccccc" "white")]))
  (is (nil? (contrast/wcag-level "rgb(0 0 0 / 0.3)" "white"))
      "translucent text as it shows")
  (is (= [nil :aaa]
         [(contrast/wcag-level "white" "rgb(255 255 255 / 0.1)")
          (contrast/wcag-level "white" "rgb(255 255 255 / 0.1)" "#121212")])
      "on a canvas"))

(deftest black-or-white
  (is (= ["#ffffff" "#000000" "#ffffff"] (map (comp cc/hex contrast/black-or-white) ["#a90000" "#f0c040" "#000000"]))
      "as podcast-clj's contrast-color chose")
  (is (= "#000000" (cc/hex (contrast/black-or-white "#2277d3")))
      "by WCAG 2, though white reads better on this mid blue")
  (is (= ["#000000" "#ffffff"]
         [(cc/hex (contrast/black-or-white "rgb(0 0 0 / 0.2)"))
          (cc/hex (contrast/black-or-white "rgb(0 0 0 / 0.2)" "black"))])
      "a translucent colour as it shows on the canvas"))
