(ns dk.simongray.colour-coded-test
  (:require [clojure.test :refer [deftest is]]
            [dk.simongray.colour-coded :as cc]))

(deftest colours
  (is (= {:space :srgb :coords [1.0 0.0 0.0] :alpha 1.0} (cc/colour "red") (cc/colour {:space :srgb :coords [1.0 0.0 0.0]}))
      "a CSS colour or a map, with an alpha of 1 unless it has one")
  (is (every? nil? (map cc/colour ["not a colour" {:space :nowhere :coords [1 2 3]} 7 nil]))))

(deftest components-and-relative-colours
  (is (= {:l 0.44 :c 0.085 :h 158.0 :alpha 1.0} (cc/components "oklch(0.44 0.085 158)")))
  (is (= "oklch(0.33 0.085 338)"
         (cc/css (cc/with "oklch(0.44 0.085 158)" :oklch {:l 0.33 :c #(min % 0.1) :h #(+ % 180)})))
      "as oklch(from var(--seed) 33% min(c, 0.1) calc(h + 180))")
  (is (== 0 (:alpha (cc/with "red" :srgb {:alpha 0}))))
  (is (= 180.0 (get-in (cc/with "grey" :oklch {:h #(+ % 180)}) [:coords 2]))
      "a missing hue counts as 0, as none does in calc()"))

(deftest hex
  (is (= ["#24476b" "#ff0000" "#24476b80"] (map cc/hex ["#24476b" "red" "rgb(36 71 107 / 0.5)"])))
  (is (= "#00ff00" (cc/hex "color(display-p3 0 1 0)")) "clipped into sRGB, as browsers clip it"))
