(ns dk.simongray.colour-coded.mix-test
  (:require [clojure.test :refer [deftest is testing]]
            [dk.simongray.colour-coded :as cc]
            [dk.simongray.colour-coded.mix :as mix]
            [dk.simongray.colour-coded.space :as space]
            [dk.simongray.colour-coded.test-data :as data]))

(deftest mixes-agree-with-colour-js
  (doseq [{:strs [a b space mixed]} (get data/colorjs "mixes")
          :let [k    (keyword space)
                ours (:coords (mix/mix (data/fixture-colour a) (data/fixture-colour b) 0.3 k))
                hue  (space/hue-index k)]]
    (testing (str "in " space)
      (if hue
        (do (is (data/close? 1e-9 (assoc ours hue nil) (assoc mixed hue nil)))
            (is (data/same-hue? 1e-6 (ours hue) (mixed hue))))
        (is (data/close? 1e-9 ours mixed))))))

(deftest hue-methods
  (let [hue #(get-in (mix/mix "oklch(0.6 0.1 30)" "oklch(0.6 0.1 330)" 0.5 :oklch %) [:coords 2])]
    (is (data/close? 1e-9 [0.0 180.0 180.0 0.0] (map hue [:shorter :longer :increasing :decreasing])))))

(deftest missing-components
  (is (data/close? 1e-9 [0.6 0.1 240.0]
                   (:coords (mix/mix "oklch(0.6 0.1 none)" "oklch(0.6 0.1 240)" 0.3 :oklch)))
      "take the other colour's")
  (is (= 240.0 (get-in (mix/mix "white" "oklch(0.6 0.1 240)" 0.5 :oklch) [:coords 2]))
      "including a grey's hue, which is missing"))

(deftest steps
  (let [s (mix/steps "black" "white" 5 :srgb)]
    (is (= 5 (count s)))
    (is (= ["#000000" "#404040" "#808080" "#bfbfbf" "#ffffff"] (map cc/hex s)))))

(deftest gradients-agree-with-colour-js-on-each-segment
  (doseq [{:strs [a b space mixed]} (get data/colorjs "mixes")
          :let     [k     (keyword space)
                    [a b] (map data/fixture-colour [a b])
                    hue   (space/hue-index k)
                    g     (mix/gradient [a b a] k)]
          position [0.15 0.85]
          :let     [ours (:coords (g position))]]
    (testing (str "in " space " at " position)
      (if hue
        (do (is (data/close? 1e-9 (assoc ours hue nil) (assoc mixed hue nil)))
            (is (data/same-hue? 1e-6 (ours hue) (mixed hue))))
        (is (data/close? 1e-9 ours mixed))))))

;; The example of CSS Images 3, section 3.4.3, with 100px as 1, and without
;; its fourth gradient, which mixes units
(def fixed-up-gradients
  "Gradients, each with the colours at positions that show where CSS puts its
  stops."
  [[["red" ["white" 0.2] "blue"]
    {0.1 "#ff8080" 0.2 "#ffffff" 0.6 "#8080ff"}]
   [[["red" 0.4] "white" "black" "blue"]
    {0.2 "#ff0000" 0.6 "#ffffff" 0.7 "#808080" 0.8 "#000000" 1 "#0000ff"}]
   [[["red" -0.5] "white" "blue"]
    {-0.5 "#ff0000" 0.25 "#ffffff" 1 "#0000ff"}]
   [[["red" 0.2] ["white" 0] ["blue" 0.4]]
    {0.1 "#ff0000" 0.2 "#ffffff" 0.3 "#8080ff" 0.4 "#0000ff"}]
   [["red" ["white" -0.5] ["black" 1.5] "blue"]
    {-1 "#ff0000" 0 "#ffffff" 0.75 "#808080" 1.5 "#0000ff"}]
   [[["red" 0.8] ["white" 0] "black" ["blue" 1]]
    {0.5 "#ff0000" 0.8 "#ffffff" 0.85 "#808080" 0.9 "#000000" 1 "#0000ff"}]])

(deftest gradients-place-their-stops-as-css-does
  (doseq [[xs colours] fixed-up-gradients
          :let         [g (mix/gradient xs :srgb)]
          [position hex] colours]
    (is (= hex (cc/hex (g position))) (str (pr-str xs) " at " position))))

(deftest gradients
  (testing "two stops at one position change colour at once"
    (let [g (mix/gradient ["red" ["white" 0.5] ["black" 0.5] "blue"] :srgb)]
      (is (= ["#ffffff" "#000000" "#000000"] (map (comp cc/hex g) [0.4999999 0.5 0.5000001])))))
  (testing "one colour is the whole gradient"
    (is (= ["#ff0000" "#ff0000"] (map cc/hex (mix/gradient-steps ["red"] 2)))))
  (testing "two colours are the steps between them"
    (is (= (mix/steps "red" "blue" 7 :oklch :longer)
           (mix/gradient-steps ["red" "blue"] 7 :oklch :longer))))
  (testing "a hue turns the shorter way between each pair"
    (let [hues (map #(get-in % [:coords 2])
                    (mix/gradient-steps ["oklch(0.7 0.1 350)" "oklch(0.7 0.1 10)" "oklch(0.7 0.1 350)"] 5 :oklch))]
      (is (data/close? 1e-9 [350.0 0.0 10.0 0.0 350.0] hues))))
  (testing "steps"
    (is (= ["#000000" "#808080" "#ffffff" "#808080" "#000000"]
           (map cc/hex (mix/gradient-steps ["black" "white" "black"] 5 :srgb))))
    (is (= [] (mix/gradient-steps ["red" "blue"] 0)))
    (is (= ["#ff0000"] (map cc/hex (mix/gradient-steps ["red" "blue"] 1))))))
