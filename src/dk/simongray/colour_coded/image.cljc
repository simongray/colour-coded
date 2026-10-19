(ns dk.simongray.colour-coded.image
  "The colours of an image, e.g. a podcast's artwork, from the bytes of its
  file, for an app to colour its UI by.

  The platform decodes the image at a small size: ImageIO on the JVM, and
  the browser's own decoder in ClojureScript. On the JVM each function
  returns its result, and in ClojureScript a promise of it. The result is
  nil where the platform can't decode the image, e.g. WebP on the JVM, or
  anything in Node, which has no decoder, and for an image that's all
  transparent.

      (cc/hex (image/seed bytes))   ; e.g. \"#24476b\"

  For the theme of a whole site or app from an image, see
  theme/from-image."
  (:require [dk.simongray.colour-coded.choose :as choose]
            [dk.simongray.colour-coded.contrast :as contrast]
            [dk.simongray.colour-coded.harmony :as harmony]
            [dk.simongray.colour-coded.quantise :as quantise])
  #?(:clj (:import [java.io ByteArrayInputStream]
                   [javax.imageio ImageIO ImageReader]
                   [javax.imageio.stream MemoryCacheImageInputStream])))

(defn then
  "What `f` returns for `x`, unless `x` is nil, or in ClojureScript a promise
  of what it returns for the value of the promise `x`."
  [x f]
  #?(:clj  (some-> x f)
     :cljs (.then x #(some-> % f))))

#?(:clj
   (defn- decoded-pixels
     "The pixels of the image in the bytes `b`, which ImageIO decodes at
     about `n` pixels a side, by subsampling, or nil when it can't."
     [b n]
     (try
       (with-open [in (MemoryCacheImageInputStream. (ByteArrayInputStream. b))]
         (when-let [^ImageReader reader (first (iterator-seq (ImageIO/getImageReaders in)))]
           (try
             (.setInput reader in)
             (let [side  (max (.getWidth reader 0) (.getHeight reader 0))
                   step  (max 1 (quot side n))
                   param (doto (.getDefaultReadParam reader)
                           (.setSourceSubsampling step step 0 0))
                   image (.read reader 0 param)]
               (vec (for [y     (range (.getHeight image))
                          x     (range (.getWidth image))
                          :let  [argb (.getRGB image x y)]]
                      (mapv #(bit-and (bit-shift-right argb %) 255) [16 8 0 24]))))
             (finally
               (.dispose reader)))))
       (catch Exception _
         nil))))

#?(:cljs
   (defn- drawn-pixels
     "A promise of the pixels of the image in the bytes `b`, which the
     browser draws at `n` pixels a side, or of nil where it can't, e.g. in
     Node, which has no decoder."
     [b n]
     (if (and (exists? js/createImageBitmap) (exists? js/OffscreenCanvas))
       (-> (js/createImageBitmap (js/Blob. #js [b]))
           (.then (fn [bitmap]
                    (let [context (.getContext (js/OffscreenCanvas. n n) "2d")]
                      (.drawImage context bitmap 0 0 n n)
                      (.close bitmap)
                      (let [data (.-data (.getImageData context 0 0 n n))]
                        (mapv (fn [i] (mapv #(aget data (+ i %)) [0 1 2 3]))
                              (range 0 (.-length data) 4))))))
           (.catch (constantly nil)))
       (js/Promise.resolve nil))))

(defn pixels
  "The pixels of the image in the bytes `b`, decoded at about `n` pixels a
  side, by default 64 on the JVM and 32 in ClojureScript, as vectors of red,
  green, blue and alpha from 0 to 255. The bytes are a byte array on the
  JVM, and in ClojureScript a typed array, an ArrayBuffer or a Blob."
  ([b]
   (pixels b #?(:clj 64 :cljs 32)))
  ([b n]
   #?(:clj  (decoded-pixels b n)
      :cljs (drawn-pixels b n))))

(defn palette
  "At most `n` colours, by default 5, of the image in the bytes `b`, the
  most common first, as maps of the :colour and its :share of the opaque
  pixels. The quantiser `f` finds them, by default quantise/buckets, or
  e.g. quantise/k-means."
  ([b]
   (palette b 5))
  ([b n]
   (palette b n quantise/buckets))
  ([b n f]
   (then (pixels b) #(not-empty (f % n)))))

(defn dominant-colour
  "The colour that most of the image in the bytes `b` has, e.g. for a player
  to tint its screen by the artwork."
  [b]
  (then (palette b 1) #(:colour (first %))))

;; Material 3 derives a whole scheme from one source colour, which a grey
;; can't be, since it has no hue; artwork whose most common colour is
;; white or black still has one further down its palette
;; https://m3.material.io/styles/color/dynamic/user-generated-source
(defn seed
  "The colour of the image in the bytes `b` to derive the colours of a UI
  from: the most common of its five commonest colours that has a hue, or
  nil where they're all about grey."
  [b]
  (then (palette b) #(choose/seed (map :colour %))))

;; WCAG 2.2, success criterion 1.4.11: a control has a contrast of 3:1
(defn scheme
  "The colours of a UI by the colours of an image, its `palette`, the most
  common first: a map of these, or nil for an empty palette.

  - :background, the most common colour, to tint by
  - :text, black or white, whichever reads better on the background
  - :accent, for controls, e.g. a progress bar: the first other colour
    whose contrast with the background is 3:1, which WCAG 2.2 asks of a
    control, or else the complement when its contrast is, or else the text
    colour
  - :accent-text, black or white, whichever reads better on the accent
  - :complement, the background's hue turned halfway round in HSL
  - :seed, the first colour with a hue, by choose/seed, or nil
  - :palette, the colours of the palette"
  [palette]
  (when-let [background (first palette)]
    (let [complement (harmony/complement background :hsl)
          apart?     #(>= (contrast/ratio background %) 3)
          accent     (or (choose/first-with-contrast (rest palette) background 3)
                         (when (apart? complement) complement)
                         (contrast/black-or-white background))]
      {:background  background
       :text        (contrast/black-or-white background)
       :accent      accent
       :accent-text (contrast/black-or-white accent)
       :complement  complement
       :seed        (choose/seed palette)
       :palette     (vec palette)})))

(defn colours
  "The colours for an app to colour its UI by the image in the bytes `b`,
  e.g. the artwork of a show: the scheme of its five commonest colours."
  [b]
  (then (palette b) #(scheme (map :colour %))))

(comment
  ;; on the JVM, the bytes of an image file
  #?(:clj (def b (java.nio.file.Files/readAllBytes
                  (java.nio.file.Path/of "artwork.png" (make-array String 0)))))
  (seed b)
  (colours b)
  #_.)
