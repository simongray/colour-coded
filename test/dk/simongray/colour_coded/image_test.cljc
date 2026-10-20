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

#?(:clj
   (defn- written-image
     "The bytes of an image of `w` by `h` pixels in `format`, e.g. jpeg,
     blue on the left and red on the right, of the BufferedImage `type`,
     whose transparent kinds are transparent at the top."
     [w h format type]
     (let [img (java.awt.image.BufferedImage. w h type)
           out (java.io.ByteArrayOutputStream.)]
       (doseq [x (range w) y (range h)]
         (.setRGB img x y (unchecked-int (cond
                                           (< y 10)         0x00FFFFFF
                                           (< x (quot w 2)) 0xFF2040A0
                                           :else            0xFFC03020))))
       (javax.imageio.ImageIO/write img ^String format out)
       (.toByteArray out))))

;; podcast-clj's tests of its thumbnails, which these replace
#?(:clj
   (deftest thumbnails-of-an-image
     (let [rgb    java.awt.image.BufferedImage/TYPE_INT_RGB
           jpeg   (written-image 1000 500 "jpeg" rgb)
           result (image/thumbnails jpeg [64 128 256 512])
           sizes  #(update-vals % (juxt :type :width :height))]
       (is (= {64  ["image/jpeg" 64 32]
               128 ["image/jpeg" 128 64]
               256 ["image/jpeg" 256 128]
               512 ["image/jpeg" 512 256]}
              (sizes result))
           "each width, with the height in proportion")
       (let [decoded (javax.imageio.ImageIO/read (java.io.ByteArrayInputStream. (:data (get result 64))))
             rgb     (fn [x y] (let [c (.getRGB decoded x y)]
                                 (mapv #(bit-and (bit-shift-right c %) 255) [16 8 0])))
             near?   (fn [a b] (every? #(<= (abs %) 8) (map - a b)))]
         (is (= [64 32] [(.getWidth decoded) (.getHeight decoded)]) "a JPEG that decodes")
         (is (near? [0x20 0x40 0xA0] (rgb 8 24)) "blue on the left")
         (is (near? [0xC0 0x30 0x20] (rgb 56 24)) "and red on the right"))
       (let [small (image/thumbnails (written-image 100 50 "png" rgb) [64 128 256 512])]
         (is (= {64  ["image/jpeg" 64 32]
                 128 ["image/jpeg" 100 50]
                 256 ["image/jpeg" 100 50]
                 512 ["image/jpeg" 100 50]}
                (sizes small))
             "never wider than the image itself")
         (is (every? #(identical? (small 128) (small %)) [256 512])
             "and one copy for the widths above its own"))
       (is (= [64 1024] ((juxt :width :height) (get (image/thumbnails (written-image 200 3200 "png" rgb) [64]) 64)))
           "a tall image")
       (is (= [50 25000] ((juxt :width :height) (get (image/thumbnails (written-image 100 50000 "png" rgb) [64]) 64)))
           "decoded at no more than about 4 million pixels")
       (is (= "image/png" (:type (get (image/thumbnails (written-image 300 300 "png" java.awt.image.BufferedImage/TYPE_INT_ARGB) [64]) 64)))
           "a PNG for an image with transparency")
       (is (= 64 (:width (get (image/thumbnails (written-image 300 300 "gif" java.awt.image.BufferedImage/TYPE_BYTE_INDEXED) [64]) 64)))
           "a GIF of one frame")
       (is (nil? (image/thumbnails jpeg [64] {:max-pixels 1000})) "an image of more pixels than allowed")
       (is (nil? (image/thumbnails (byte-array (map unchecked-byte [0x52 0x49 0x46 0x46 0 0 0 0 0x57 0x45 0x42 0x50])) [64]))
           "WebP, which ImageIO doesn't read")
       (is (nil? (image/thumbnails (byte-array [1 2 3]) [64])) "bytes that aren't an image"))))

#?(:cljs
   (deftest thumbnails-of-an-image
     (async done
       (-> (image/thumbnails (js/Uint8Array. #js [0x89 0x50 0x4E 0x47]) [64])
           (.then #(is (nil? %) "none in Node, which has no decoder"))
           (.catch (fn [e] (is false (str "rejected: " e))))
           (.finally done)))))

#?(:cljs
   (deftest the-colours-of-an-image
     (async done
       (-> (image/colours (js/Uint8Array. #js [0x89 0x50 0x4E 0x47]))
           (.then #(is (nil? %) "none in Node, which has no decoder, or of bytes that aren't an image"))
           (.catch (fn [e] (is false (str "rejected: " e))))
           (.finally done)))))
