(ns dk.simongray.colour-coded.image-test
  (:require [clojure.test :refer [deftest is #?(:cljs async)]]
            [dk.simongray.colour-coded :as cc]
            [dk.simongray.colour-coded.image :as image]
            #?@(:clj [[dk.simongray.colour-coded.quantise :as quantise]
                      [dk.simongray.colour-coded.test-data :as data]
                      [dk.simongray.colour-coded.theme :as theme]])))

(defn- hexes
  "The scheme `m` with its colours as hex colours."
  [m]
  (update-vals m #(if (vector? %) (mapv cc/hex %) (some-> % cc/hex))))

;; podcast-clj's tests of scheme
(deftest a-scheme-by-a-palette
  (is (= {:background "#a90000" :text "#ffffff" :accent "#f0c040" :accent-text "#000000"
          :complement "#00a9a9" :seed "#a90000" :palette ["#a90000" "#1a1a1a" "#f0c040"]}
         (hexes (image/scheme ["#a90000" "#1a1a1a" "#f0c040"])))
      "an accent that stands apart from the background, where the dark grey doesn't")
  (is (= ["#002fff" "#ffffff"] ((juxt :accent :accent-text) (hexes (image/scheme ["#ffd000" "#ffc010"]))))
      "or else the complement, when it stands apart")
  (is (= "#ffffff" (:accent (hexes (image/scheme ["#a90000" "#a01010"]))))
      "or else the colour of the text, as the complement of dark red is only 2.7:1")
  (is (= "#000000" (:accent (hexes (image/scheme ["#f4f4f4" "#eeeeee"])))) "and a grey its own complement")
  (is (nil? (:seed (image/scheme ["#000000" "#ffffff"]))))
  (is (nil? (image/scheme []))))

#?(:clj
   (defn- png
     "The bytes of a PNG image of `w` by `h` pixels of the `type` of
     BufferedImage, whose colour at x is the ARGB that `f` returns for it."
     [w h f type]
     (let [img (java.awt.image.BufferedImage. w h type)
           out (java.io.ByteArrayOutputStream.)]
       (doseq [x (range w) y (range h)]
         (.setRGB img x y (unchecked-int (f x))))
       (javax.imageio.ImageIO/write img "png" out)
       (.toByteArray out))))

#?(:clj
   (deftest the-colours-of-an-image
     (let [b (png 300 200 #(if (< % 210) 0xFF2040A0 0xFFC03020) java.awt.image.BufferedImage/TYPE_INT_RGB)]
       (is (= "#2040a0" (cc/hex (image/dominant-colour b))))
       (is (= ["#2040a0" "#c03020"] (map (comp cc/hex :colour) (image/palette b))))
       (is (data/close? 0.01 [0.7 0.3] (map :share (image/palette b))))
       (is (= "#2040a0" (cc/hex (image/seed b))))
       (is (= {:background "#2040a0" :text "#ffffff" :accent "#ffffff" :accent-text "#000000"
               :complement "#a08020" :seed "#2040a0" :palette ["#2040a0" "#c03020"]}
              (hexes (image/colours b)))
           "white for the accent, as the red is only 1.6:1 against the blue")
       (is (= 2 (count (image/palette b 5 quantise/k-means))))
       (is (= "#2040a0" (cc/hex (:seed (theme/from-image b)))) "a theme from the image")
       (is (empty? (remove :ok? (theme/report (theme/from-image b))))))
     (is (nil? (image/dominant-colour (png 10 10 (constantly 0x00FF0000) java.awt.image.BufferedImage/TYPE_INT_ARGB)))
         "an image that's all transparent")
     (is (= [nil nil nil] [(image/pixels (byte-array [1 2 3])) (image/colours (byte-array [1 2 3])) (theme/from-image (byte-array [1 2 3]))])
         "bytes that aren't an image")))

#?(:cljs
   (deftest the-colours-of-an-image
     (async done
       (-> (image/colours (js/Uint8Array. #js [0x89 0x50 0x4E 0x47]))
           (.then #(is (nil? %) "none in Node, which has no decoder, or of bytes that aren't an image"))
           (.catch (fn [e] (is false (str "rejected: " e))))
           (.finally done)))))
