(ns dk.simongray.colour-coded.harmony-test
  (:require [clojure.test :refer [deftest is]]
            [dk.simongray.colour-coded :as cc]
            [dk.simongray.colour-coded.harmony :as harmony]
            [dk.simongray.colour-coded.test-data :as data]))

(defn- hues
  "The OKLCH hues of the colours `xs`."
  [xs]
  (map #(get-in (cc/to % :oklch) [:coords 2]) xs))

(deftest the-complement
  (is (= ["#00a9a9" "#a08020" "#808080"] (map (comp cc/hex #(harmony/complement % :hsl)) ["#a90000" "#2040A0" "#808080"]))
      "in HSL, as podcast-clj's complementary-color had it, with a grey its own")
  (let [[l c h] (:coords (harmony/complement "oklch(0.5 0.1 30)"))]
    (is (data/close? 1e-9 [0.5 0.1 210.0] [l c h]) "in OKLCH, at the same lightness and chroma")))

(deftest the-harmonies
  (let [seed "oklch(0.6 0.12 40)"]
    (is (data/close? 1e-9 [40.0 10.0 70.0] (hues (harmony/analogous seed))))
    (is (data/close? 1e-9 [40.0 190.0 250.0] (hues (harmony/split-complement seed))))
    (is (data/close? 1e-9 [40.0 160.0 280.0] (hues (harmony/triad seed))))
    (is (data/close? 1e-9 [40.0 130.0 220.0 310.0] (hues (harmony/square seed))))
    (is (data/close? 1e-9 [40.0 112.0 184.0 256.0 328.0] (hues (harmony/wheel seed 5))))))
