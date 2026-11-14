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

(deftest dark-or-light
  (is (contrast/dark? "#24476b"))
  (is (contrast/light? "#ffd000"))
  (testing "black and white text contrast equally between these greys"
    (is (= [true false] (map contrast/dark? ["#757575" "#767676"]))))
  (testing "a translucent colour is as dark as it shows on the canvas"
    (is (contrast/light? "rgb(0 0 0 / 0.2)") "a light grey on white")
    (is (contrast/dark? "rgb(0 0 0 / 0.2)" "black"))
    (is (contrast/dark? "rgb(255 255 255 / 0.2)" "#16181a"))
    (is (contrast/light? "rgb(255 255 255 / 0.2)")))
  (testing "a colour is one or the other, as white or black text contrasts more"
    (doseq [hex (vals cc/named-colours)]
      (is (not= (contrast/dark? hex) (contrast/light? hex)) hex)
      (is (= (contrast/dark? hex)
             (>= (contrast/ratio "white" hex) (contrast/ratio "black" hex)))
          hex))))

(deftest colours-at-a-ratio
  (doseq [x  ["#24476b" "#ffd000" "#a90000" "#2e8b57"]
          bg ["white" "#111111"]
          r  [3 4.5 7]
          :let [c (contrast/at-ratio x bg r)]]
    (is (data/close? 0.01 r (contrast/ratio c bg)) (str x " on " bg " at " r))
    (is (data/same-hue? 3 (get-in (cc/to x :oklch) [:coords 2]) (get-in (cc/to c :oklch) [:coords 2]))
        "at the colour's own hue")))

(deftest translucent-colours-at-a-ratio
  (let [bg "rgb(0 0 0 / 0.2)"]
    (is (data/close? 0.01 4.5 (contrast/ratio (contrast/at-ratio "#888888" bg) bg))
        "a background as it shows on white"))
  (let [c (contrast/at-ratio "rgb(136 136 136 / 0.9)" "white")]
    (is (= 0.9 (:alpha c)))
    (is (data/close? 0.01 4.5 (contrast/ratio c "white")) "a colour as it shows"))
  (is (nil? (contrast/at-ratio "rgb(136 136 136 / 0.3)" "white"))
      "or nil, where it's too faint at any lightness"))

(deftest an-unreachable-ratio
  (is (nil? (contrast/at-ratio "red" "#777777" 7)) "a mid grey is 4.5 at most from black or white"))

(deftest colours-at-ratios
  (is (= 4 (count (remove nil? (contrast/at-ratios "#24476b" "white" [1.5 3 4.5 7]))))))
