(ns dk.simongray.colour-coded.space-test
  (:require [clojure.test :refer [deftest is testing]]
            [dk.simongray.colour-coded.space :as space]
            [dk.simongray.colour-coded.test-data :as data]))

(defn- in-srgb?
  "Whether the sRGB coordinates `rgb` lie inside sRGB."
  [rgb]
  (every? #(<= -0.000001 % 1.000001) rgb))

(deftest conversions-agree-with-colour-js
  (doseq [{:strs [from coords to]} (get data/colorjs "conversions")
          [id expected]            to
          :let                     [k    (keyword id)
                                    ours (space/convert coords (keyword from) k)
                                    hue  (space/hue-index k)]]
    (testing (str from " " coords " in " id)
      (if hue
        (do (is (data/close? 1e-9 (assoc ours hue nil) (assoc expected hue nil)))
            (is (data/same-hue? 1e-6 (ours hue) (expected hue))))
        (is (data/close? 1e-9 ours expected))))))

(deftest conversions-round-trip
  (doseq [{:strs [from coords]} (get data/colorjs "conversions")
          :when                 (in-srgb? (space/convert coords (keyword from) :srgb))
          :let                  [rgb (space/convert coords (keyword from) :srgb)]
          id                    (keys space/spaces)]
    (is (data/close? 1e-9 rgb (space/convert (space/convert rgb :srgb id) id :srgb))
        (str "sRGB " rgb " through " id))))

(deftest greys-have-no-hue
  (let [[_ c h] (space/convert [0.5 0.5 0.5] :srgb :oklch)]
    (is (nil? h) "the hue of a grey is missing")
    (is (= 0.0 c) "and its chroma, powerless as it is, is 0"))
  (is (nil? (first (space/convert [1.0 1.0 1.0] :srgb :hsl))))
  (is (nil? (first (space/convert (space/convert [100 0 0] :lch :srgb) :srgb :hsl)))
      "nor does white after a round of conversions, which leaves it a hair off grey")
  (is (= 0.0 (second (space/convert [0.4 0.4 0.4] :srgb :lch)))
      "and the chroma of a powerless hue is 0"))

(deftest a-missing-coordinate-counts-as-zero
  (is (= (space/convert [0.5 0 0] :oklab :srgb) (space/convert [0.5 nil nil] :oklab :srgb)))
  (is (= [0.5 nil nil] (space/convert [0.5 nil nil] :oklab :oklab)) "unless the space is the same"))

(deftest lineage
  (is (= [:hsl :srgb :srgb-linear :xyz-d65] (space/lineage :hsl)))
  (is (= [:prophoto-rgb :prophoto-rgb-linear :xyz-d50 :xyz-d65] (space/lineage :prophoto-rgb))))
