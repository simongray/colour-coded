(ns dk.simongray.colour-coded.tone-test
  (:require [clojure.test :refer [deftest is]]
            [dk.simongray.colour-coded :as cc]
            [dk.simongray.colour-coded.contrast :as contrast]
            [dk.simongray.colour-coded.gamut :as gamut]
            [dk.simongray.colour-coded.tone :as tone]
            [dk.simongray.colour-coded.test-data :as data]))

;; The bars of the podcast app's look.css
(def bar {:l 0.33 :c 0.1 :turn 180})
(def on-bar {:l 0.97 :c 0.012 :turn 180})
(def on-bar-2 {:l 0.84 :c 0.035 :turn 180})
(def bar-rule {:l 0.68 :c 0.05 :turn 180})

(deftest a-token-of-a-seed
  (let [c (tone/tone bar "#a90000")
        [l chroma h] (:coords c)]
    (is (gamut/in-gamut? c))
    (is (data/close? 1e-9 0.33 l) "at its lightness")
    (is (<= chroma 0.1) "with no more chroma than it takes")
    (is (data/same-hue? 1e-6 (+ 180 (get-in (cc/to "#a90000" :oklch) [:coords 2])) h)
        "and its hue turned")))

(deftest relative-css
  (is (= "oklch(from var(--seed) 33% min(c, 0.1) calc(h + 180))" (tone/relative-css bar "--seed")))
  (is (= "lch(from var(--seed) 40 min(c, 60) h)" (tone/relative-css {:space :lch :l 40 :c 60} "--seed"))))

(deftest the-contrast-of-the-bars-holds-at-every-hue
  (is (<= 10 (:ratio (tone/worst-contrast on-bar bar))))
  (is (<= 6.8 (:ratio (tone/worst-contrast on-bar-2 bar))))
  (is (<= 3.9 (:ratio (tone/worst-contrast bar-rule bar)))))

(deftest tokens-in-lch-keep-their-contrast
  (let [ratios (fn [a b]
                 (for [h (range 0 360 5)
                       :let [seed {:space :oklch :coords [0.6 0.2 h]}]]
                   (contrast/ratio (tone/tone a seed) (tone/tone b seed))))
        spread #(/ (apply max %) (apply min %))]
    (is (< (spread (ratios {:space :lch :l 40 :c 6} {:space :lch :l 100 :c 0})) 1.01)
        "within a percent for a near-grey")
    (is (< (spread (ratios {:space :lch :l 40 :c 60} {:space :lch :l 100 :c 0})) 1.08)
        "and a few percent for a vivid colour")
    (is (> (spread (ratios {:l 0.5 :c 0.15} {:l 1 :c 0})) 1.15)
        "where tokens in OKLCH differ by more")))
