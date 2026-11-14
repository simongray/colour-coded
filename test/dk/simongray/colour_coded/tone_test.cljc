(ns dk.simongray.colour-coded.tone-test
  (:require [clojure.test :refer [deftest is testing]]
            [dk.simongray.colour-coded :as cc]
            [dk.simongray.colour-coded.contrast :as contrast]
            [dk.simongray.colour-coded.epaper :as epaper]
            [dk.simongray.colour-coded.gamut :as gamut]
            [dk.simongray.colour-coded.tone :as tone]
            [dk.simongray.colour-coded.test-data :as data]))

;; The bars of the podcast app's look.css
(def bar {:l 0.33 :c 0.1 :turn 180})
(def on-bar {:l 0.97 :c 0.012 :turn 180})
(def on-bar-2 {:l 0.84 :c 0.035 :turn 180})
(def bar-rule {:l 0.68 :c 0.05 :turn 180})

(deftest a-token-of-a-seed
  (let [c (tone/tone "#a90000" bar)
        [l chroma h] (:coords c)]
    (is (gamut/in-gamut? c))
    (is (data/close? 1e-9 0.33 l) "at its lightness")
    (is (<= chroma 0.1) "with no more chroma than it takes")
    (is (data/same-hue? 1e-6 (+ 180 (get-in (cc/to "#a90000" :oklch) [:coords 2])) h)
        "and its hue turned")))

(deftest relative-css
  (is (= "oklch(from var(--seed) 33% min(c, 0.1) calc(h + 180))" (tone/relative-css "--seed" bar)))
  (is (= "lch(from var(--seed) 40 min(c, 60) h)" (tone/relative-css "--seed" {:space :lch :l 40 :c 60}))))

;; a bar that's darker under a dark seed
(def band {:l [0.22 0.34] :c 0.1 :turn 180})

(deftest a-token-with-a-range-of-lightness
  (let [l #(first (:coords (tone/tone % band)))]
    (is (data/close? 1e-9 0.22 (l "oklch(10% 0.1 30)")) "no darker than the least")
    (is (data/close? 1e-9 0.28 (l "oklch(28% 0.1 30)")) "the seed's own between the two")
    (is (data/close? 1e-9 0.34 (l "oklch(80% 0.1 30)")) "and no lighter than the most"))
  (is (data/close? 1e-9 46 (first (:coords (tone/tone "oklch(80% 0.1 30)" {:space :lch :l [25 46] :c 60}))))
      "in the token's own space")
  (is (= "oklch(from var(--seed) clamp(0.22, l, 0.34) min(c, 0.1) calc(h + 180))"
         (tone/relative-css "--seed" band)))
  (is (= "lch(from var(--seed) clamp(25, l, 46) min(c, 60) h)"
         (tone/relative-css "--seed" {:space :lch :l [25 46] :c 60})))
  (testing "its contrast is that of the end where it's least"
    (let [worst (tone/worst-contrast on-bar band)]
      (is (data/close? 1e-9 (:ratio (tone/worst-contrast on-bar {:l 0.34 :c 0.1 :turn 180})) (:ratio worst)))
      (is (<= 0.4 (:lightness worst)) "which a light seed reaches"))))

(deftest the-contrast-of-the-bars-holds-at-every-hue
  (is (<= 10 (:ratio (tone/worst-contrast on-bar bar))))
  (is (<= 6.8 (:ratio (tone/worst-contrast on-bar-2 bar))))
  (is (<= 3.9 (:ratio (tone/worst-contrast bar-rule bar)))))

(deftest tokens-in-lch-keep-their-contrast
  (let [ratios (fn [a b]
                 (for [h (range 0 360 5)
                       :let [seed {:space :oklch :coords [0.6 0.2 h]}]]
                   (contrast/ratio (tone/tone seed a) (tone/tone seed b))))
        spread #(/ (apply max %) (apply min %))]
    (is (< (spread (ratios {:space :lch :l 40 :c 6} {:space :lch :l 100 :c 0})) 1.01)
        "within a percent for a near-grey")
    (is (< (spread (ratios {:space :lch :l 40 :c 60} {:space :lch :l 100 :c 0})) 1.08)
        "and a few percent for a vivid colour")
    (is (> (spread (ratios {:l 0.5 :c 0.15} {:l 1 :c 0})) 1.15)
        "where tokens in OKLCH differ by more")))

(deftest tokens-on-a-shade
  (doseq [seed  ["#24476b" "#a90000" "#00a000" "grey"]
          token [{:shade 5} {:shade 5 :c 0.1} {:shade 12 :c 0.1 :turn 180}
                 {:shade 5 :c 30 :space :lch}]]
    (is (= (:shade token) (epaper/shade (tone/tone seed token)))
        (str seed " " token)))
  (is (= "#ffffff" (cc/hex (tone/tone "red" {:shade 15 :c 0}))))
  (is (= {:a "#555555"} (update-vals (tone/tones "red" {:a {:shade 5 :c 0}}) cc/hex))))

(deftest relative-css-of-a-shade
  (is (= "oklch(from var(--seed) 44.9533% min(c, 0.1) h)"
         (tone/relative-css "--seed" {:shade 5 :c 0.1}))
      "the lightness of the shade's grey")
  (testing "it lands within a shade or two, by the clipping of a browser"
    (doseq [shade [3 4 5 8 12]
            hue   (range 0 360 30)
            :let  [seed   {:space :oklch :coords [0.5 0.3 hue] :alpha 1.0}
                   colour (gamut/clip (tone/unmapped seed {:shade shade :c 0.12}))]
            s     (epaper/shades colour)]
      (is (<= (abs (- s shade)) 2) (str "shade " shade " at hue " hue)))))
