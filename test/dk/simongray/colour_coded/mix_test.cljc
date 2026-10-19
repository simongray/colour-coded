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
