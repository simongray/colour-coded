(ns dk.simongray.colour-coded.quantise-test
  (:require [clojure.test :refer [deftest is]]
            [dk.simongray.colour-coded :as cc]
            [dk.simongray.colour-coded.quantise :as quantise]
            [dk.simongray.colour-coded.test-data :as data]))

(defn- hexes
  "The hex colours of the quantised `xs`."
  [xs]
  (map (comp cc/hex :colour) xs))

;; podcast-clj's tests of palette-of, which buckets replaces
(deftest buckets
  (is (= ["#2244a2" "#c03020"]
         (hexes (quantise/buckets (concat (repeat 6 [0x20 0x40 0xa0 255])
                                          (repeat 6 [0x24 0x48 0xa4 255])
                                          (repeat 5 [0xc0 0x30 0x20 255]))
                                  5))))
  (is (= ["#c03020"] (hexes (quantise/buckets (concat (repeat 6 [0x20 0x40 0xa0 0]) (repeat 5 [0xc0 0x30 0x20 255])) 5)))
      "opaque pixels alone")
  (is (= [] (quantise/buckets [[0 0 0 0]] 5)))
  (is (= ["#000000"] (hexes (quantise/buckets [[0 0 0 255] [255 255 255 255]] 1)))
      "a tie settled the same way on each platform")
  (is (data/close? 1e-9 [0.6 0.4]
                   (map :share (quantise/buckets (concat (repeat 6 [0 0 255 255]) (repeat 4 [255 0 0 255])) 5)))
      "with each colour's share"))

(deftest k-means
  (let [pixels (concat (repeat 60 [0x20 0x40 0xa0 255])
                       (repeat 50 [0xc0 0x30 0x20 255])
                       (repeat 40 [0xf0 0xf0 0xe8 255]))
        result (quantise/k-means pixels 3)]
    (is (= ["#2040a0" "#c03020" "#f0f0e8"] (hexes result)))
    (is (data/close? 1e-9 [0.4 (/ 1 3.0) (/ 4 15.0)] (map :share result)))
    (is (= 2 (count (quantise/k-means (take 110 pixels) 5))) "no more colours than the image has")
    (is (nil? (quantise/k-means [] 3)))))
