(ns dk.simongray.colour-coded.gamut-test
  (:require [clojure.test :refer [deftest is testing]]
            [dk.simongray.colour-coded :as cc]
            [dk.simongray.colour-coded.gamut :as gamut]
            [dk.simongray.colour-coded.test-data :as data]))

(deftest the-gamut-mapping-of-css-agrees-with-colour-js
  (doseq [{:strs [coords mapped] in? "in-gamut"} (get data/colorjs "gamut")
          :let [c {:space :oklch :coords coords}]]
    (testing (str "oklch " coords)
      (is (= in? (gamut/in-gamut? c)))
      (is (data/close? 1e-6
                       (:coords (gamut/to-gamut c))
                       (:coords (cc/to {:space :oklch :coords mapped} :srgb)))))))

(deftest mapping-keeps-the-lightness-and-the-hue
  (let [c        "oklch(70% 0.3 140)"
        [l _ h]  (:coords (cc/to (gamut/to-gamut c) :oklch))]
    (is (gamut/in-gamut? (gamut/to-gamut c)))
    (is (data/close? 0.01 0.7 l))
    (is (data/same-hue? 3 140 h) "within what the clipping of CSS's mapping allows"))
  (is (= "#ffffff" (cc/hex (gamut/to-gamut "oklch(120% 0.2 30)"))) "white, beyond its lightness")
  (is (= "#000000" (cc/hex (gamut/to-gamut "oklch(0 0.2 30)")))))

(deftest clipping
  (is (= [1.0 0.0 0.0] (:coords (gamut/clip {:space :srgb :coords [1.2 -0.1 0.0]}))))
  (is (gamut/in-gamut? (gamut/clip "color(display-p3 0 1 0)")))
  (is (= :oklab (:space (gamut/clip "oklab(0.5 0.4 0.4)" :oklab))) "an unbounded space doesn't clamp"))

(deftest reducing-the-chroma-keeps-the-lightness-and-the-hue-exactly
  (doseq [h (range 0 360 30)
          :let [c (gamut/reduce-chroma {:space :lch :coords [40 150 h]} :lch)]]
    (is (gamut/in-gamut? c))
    (is (data/close? 1e-9 [40 h] [(get-in c [:coords 0]) (get-in c [:coords 2])]))))
