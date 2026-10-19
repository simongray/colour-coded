(ns dk.simongray.colour-coded.vision-test
  (:require [clojure.test :refer [deftest is testing]]
            [dk.simongray.colour-coded :as cc]
            [dk.simongray.colour-coded.test-data :as data]
            [dk.simongray.colour-coded.vision :as vision]))

(deftest the-papers-matrices
  (doseq [[d ms] (get data/machado2009 "matrices")
          [i m]  (map-indexed vector ms)]
    (is (data/close? 1e-12 m (vision/matrix (keyword d) (/ i 10.0)))
        (str d " at " (/ i 10.0)))))

(deftest interpolation
  (let [ms (get-in data/machado2009 ["matrices" "deutan"])
        f  (fn [a b w] (mapv #(mapv (fn [x y] (+ (* (- 1 w) x) (* w y))) %1 %2) a b))]
    (is (data/close? 1e-12 (f (ms 8) (ms 9) 0.73) (vision/matrix :deutan 0.873))
        "the paper's example: 0.873 weighs the matrix of 0.9 by 0.73")
    (is (data/close? 1e-12 (f (ms 1) (ms 2) 0.5) (vision/matrix :deutan 0.15))
        "between the two nearest, where colour-science extrapolates from 0.2 and 0.3"))
  (is (= (vision/matrix :protan 1.0) (vision/matrix :protan 1.5)))
  (is (= (vision/matrix :protan 0.0) (vision/matrix :protan -1))))

(deftest simulations-agree-with-colour-science
  (doseq [{:strs [colour deficiency severity] :as m} (get data/machado2009 "simulations")]
    (is (data/close? 1e-9
                     (mapv #(min 1.0 (max 0.0 %)) (get m "srgb-linear"))
                     (:coords (vision/simulate colour (keyword deficiency) severity)))
        (str colour " " deficiency " at " severity ", clipped to sRGB"))))

(deftest simulating
  (is (= :srgb-linear (:space (vision/simulate "red" :protan))))
  (is (= 0.5 (:alpha (vision/simulate "rgb(255 0 0 / 50%)" :protan))))
  (is (= (:coords (cc/to "#24476b" :srgb-linear))
         (:coords (vision/simulate "#24476b" :deutan 0)))
      "no deficiency at 0")
  (doseq [d vision/deficiencies]
    (is (data/close? 1e-5 [1 1 1] (:coords (vision/simulate "white" d)))
        (str "white stays white with " d)))
  (is (data/close? 1e-12 [0 0.047413 0.968881] (:coords (vision/simulate "blue" :deutan)))
      "clipped to sRGB, where the red would be negative")
  (is (nil? (vision/simulate "nonsense" :protan))))

(deftest least-differences
  (let [{:keys [normal protan deutan tritan]} (vision/least-difference ["#e00000" "#008000"])]
    (testing "red and green come closer with a red-green deficiency"
      (is (< protan 0.15 normal))
      (is (< deutan 0.1 tritan))))
  (let [{:keys [normal protan deutan tritan]} (vision/least-difference ["#4080ff" "#40c040"])]
    (testing "blue and green come closer with a tritan deficiency"
      (is (< tritan 0.15 normal))
      (is (< tritan protan))
      (is (< tritan deutan))))
  (is (= {:normal nil :protan nil :deutan nil :tritan nil}
         (vision/least-difference ["red"]))))
