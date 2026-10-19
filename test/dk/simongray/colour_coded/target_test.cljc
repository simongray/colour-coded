(ns dk.simongray.colour-coded.target-test
  (:require [clojure.test :refer [deftest is]]
            [dk.simongray.colour-coded :as cc]
            [dk.simongray.colour-coded.contrast :as contrast]
            [dk.simongray.colour-coded.target :as target]
            [dk.simongray.colour-coded.test-data :as data]))

(deftest colours-at-a-contrast
  (doseq [x  ["#24476b" "#ffd000" "#a90000" "#2e8b57"]
          bg ["white" "#111111"]
          r  [3 4.5 7]
          :let [c (target/at-contrast x bg r)]]
    (is (data/close? 0.01 r (contrast/ratio c bg)) (str x " on " bg " at " r))
    (is (data/same-hue? 3 (get-in (cc/to x :oklch) [:coords 2]) (get-in (cc/to c :oklch) [:coords 2]))
        "at the colour's own hue")))

(deftest unreachable-contrast
  (is (nil? (target/at-contrast "red" "#777777" 7)) "a mid grey is 4.5 at most from black or white"))

(deftest scales
  (is (= 4 (count (remove nil? (target/scale "#24476b" "white" [1.5 3 4.5 7]))))))
