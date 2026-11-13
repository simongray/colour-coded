(ns dk.simongray.colour-coded.composite-test
  (:require [clojure.test :refer [deftest is testing]]
            [dk.simongray.colour-coded :as cc]
            [dk.simongray.colour-coded.composite :as composite]
            [dk.simongray.colour-coded.contrast :as contrast]
            [dk.simongray.colour-coded.mix :as mix]
            [dk.simongray.colour-coded.test-data :as data]))

(defn- percentages
  "The components and the alpha of the colour `c`, as whole percentages."
  [{:keys [coords alpha]}]
  (map #(Math/round (* 100.0 %)) (conj coords alpha)))

(deftest composites-agree-with-coloraide
  (doseq [{:strs [a b space coords alpha]} (get data/composite "composites")
          :let [ours (composite/over a b (keyword space))]]
    (testing (str a " over " b " in " space)
      (is (data/close? 1e-9 coords (:coords ours)))
      (is (data/close? 1e-12 alpha (:alpha ours))))))

(deftest published-values
  (testing "the W3C's ACT rule afw4f7: black at an alpha of 30% on white is 2.1:1"
    (let [grey (composite/over "rgb(0 0 0 / 0.3)" "white")]
      (is (data/close? 1e-12 [0.7 0.7 0.7] (:coords grey)))
      (is (= 1.0 (:alpha grey)))
      (is (= 21 (Math/round (* 10.0 (contrast/ratio grey "white")))))))
  (testing "which linear light doesn't give"
    (let [grey (composite/over "rgb(0 0 0 / 0.3)" "white" :srgb-linear)]
      (is (= 14 (Math/round (* 10.0 (contrast/ratio grey "white")))))))
  (testing "Compositing Level 2, section 9.1.15: roughly rgb(43% 0% 57% / 44%)"
    (is (= [43 0 57 44]
           (percentages (composite/over "rgb(0% 0% 100% / 25%)"
                                        "rgb(100% 0% 0% / 25%)"))))))

(deftest the-default-space
  (is (= :srgb (:space (composite/over "oklch(0.7 0.15 150 / 0.7)" "lab(40 20 -30)"))))
  (is (= (composite/over "rgb(0 0 0 / 0.3)" "white" :srgb)
         (composite/over "rgb(0 0 0 / 0.3)" "white"))))

(deftest opaque-and-transparent-colours
  (doseq [space [:srgb :oklab :lab :xyz-d65]
          :let [a "oklch(0.7 0.15 150)"
                b "rgb(200 30 60 / 0.4)"]]
    (testing (str "in " space)
      (is (= (cc/to a space) (composite/over a b space))
          "an opaque colour hides what's behind it")
      (is (data/close? 1e-12
                       (:coords (cc/to b space))
                       (:coords (composite/over "transparent" b space)))
          "a transparent colour hides nothing")
      (is (= 0.4 (:alpha (composite/over "transparent" b space)))))))

(deftest two-transparent-colours
  (doseq [space [:srgb :lab]]
    (is (= {:space space :coords [0.0 0.0 0.0] :alpha 0.0}
           (composite/over "lab(50 -40 30 / 0)" "rgb(255 0 0 / 0)" space)))))

(deftest missing-components
  (is (= (composite/over "rgb(0 30 60 / 0.4)" "rgb(10 200 0 / 0.7)")
         (composite/over "rgb(none 30 60 / 0.4)" "rgb(10 200 none / 0.7)"))
      "count as 0")
  (is (= (composite/over "rgb(200 30 60 / 0)" "rgb(10 200 30 / 0.7)")
         (composite/over "rgb(200 30 60 / none)" "rgb(10 200 30 / 0.7)"))
      "and so does a missing alpha")
  (is (= 1.0 (:alpha (composite/over {:space :srgb :coords [1 0 0]}
                                     "rgb(10 200 30 / 0.7)")))
      "though a map without an alpha is opaque"))

(deftest no-clipping
  (let [[_ _ blue] (:coords (composite/over "color(display-p3 0 1 0 / 0.5)"
                                            "color(display-p3 1 0 0)"))]
    (is (neg? blue) "a colour outside sRGB stays outside it")))

;; ColorAide composites in neither space, but over an opaque colour the
;; result is a mix of the two, which the fixtures of Colour.js cover
(deftest oklab-and-lab
  (doseq [space [:oklab :lab]
          :let [over-opaque (composite/over "rgb(200 30 60 / 0.4)" "#24476b" space)
                mixed       (mix/mix "#24476b" "rgb(200 30 60)" 0.4 space)]]
    (testing (str "in " space)
      (is (= space (:space over-opaque)))
      (is (data/close? 1e-12 (:coords mixed) (:coords over-opaque)))
      (is (= 1.0 (:alpha over-opaque))))))

(deftest layers
  (let [colours ["rgb(200 30 60 / 0.4)" "oklch(0.7 0.15 150 / 0.7)" "lab(40 20 -30 / 0.9)"]
        [a b c] colours]
    (doseq [space [:srgb :srgb-linear :oklab :lab :xyz-d65]
            :let [top-first    (composite/over a (composite/over b c space) space)
                  bottom-first (composite/over (composite/over a b space) c space)]]
      (testing (str "in " space ", however the layers are grouped")
        (is (data/close? 1e-12 (:coords top-first) (:coords bottom-first)))
        (is (data/close? 1e-12 (:alpha top-first) (:alpha bottom-first))))))
  (testing "from the bottom up"
    (is (= "#bf4080"
           (cc/hex (reduce #(composite/over %2 %1)
                           ["white" "rgb(0 0 255 / 50%)" "rgb(255 0 0 / 50%)"]))))))

(deftest spaces
  (is (= [nil nil nil nil]
         (map #(composite/over "red" "blue" %) [:oklch :lch :hsl :hwb]))
      "a polar space has a hue, which can't be weighted")
  (is (nil? (composite/over "red" "blue" :nope)))
  (is (nil? (composite/over "red" "nonsense")))
  (is (nil? (composite/over "nonsense" "red"))))
