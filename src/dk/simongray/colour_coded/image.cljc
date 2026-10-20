(ns dk.simongray.colour-coded.image
  "The colours of an image, e.g. a podcast's artwork, from the bytes of its
  file, for an app to colour its UI by, and small copies of the image.

  The platform decodes the image at a small size: ImageIO on the JVM, and
  the browser's own decoder in ClojureScript. On the JVM each function
  returns its result, and in ClojureScript a promise of it. The result is
  nil where the platform can't decode the image, e.g. WebP on the JVM, or
  anything in Node, which has no decoder, and for an image that's all
  transparent.

      (cc/hex (image/seed bytes))   ; e.g. \"#24476b\"

  For the theme of a whole site or app from an image, see
  theme/from-image. For small copies of an image, e.g. of artwork for a
  list, see thumbnails."
  (:require #?(:clj [clojure.string :as str])
            [dk.simongray.colour-coded.choose :as choose]
            [dk.simongray.colour-coded.contrast :as contrast]
            [dk.simongray.colour-coded.harmony :as harmony]
            [dk.simongray.colour-coded.quantise :as quantise])
  #?(:clj (:import [java.awt RenderingHints]
                   [java.awt.image BufferedImage]
                   [java.io ByteArrayInputStream ByteArrayOutputStream]
                   [javax.imageio IIOImage ImageIO ImageReader ImageWriteParam
                    ImageWriter]
                   [javax.imageio.stream MemoryCacheImageInputStream
                    MemoryCacheImageOutputStream])))

(defn then
  "What `f` returns for `x`, unless `x` is nil, or in ClojureScript a promise
  of what it returns for the value of the promise `x`."
  [x f]
  #?(:clj  (some-> x f)
     :cljs (.then x #(some-> % f))))

#?(:clj
   (defn with-reader
     "What `f` returns for an ImageReader of the image in the bytes `b`, or
     nil when ImageIO can't read the image or `f` throws. The reader has
     read no pixels yet, so `f` can check the size of the image first."
     [b f]
     (try
       (with-open [in (MemoryCacheImageInputStream. (ByteArrayInputStream. b))]
         (when-let [^ImageReader reader (first (iterator-seq (ImageIO/getImageReaders in)))]
           (try
             (.setInput reader in)
             (f reader)
             (finally
               (.dispose reader)))))
       (catch Exception _
         nil))))

#?(:cljs
   (defn with-bitmap
     "A promise of what `f` returns for an ImageBitmap of the image in the
     bytes `b`, or of nil where the browser can't decode the image or `f`
     throws, e.g. in Node, which has no decoder. The bitmap is closed once
     `f` returns, or once the promise that it returns settles."
     [b f]
     (if (and (exists? js/createImageBitmap) (exists? js/OffscreenCanvas))
       (-> (js/createImageBitmap (js/Blob. #js [b]))
           (.then (fn [bitmap]
                    (-> (js/Promise.resolve nil)
                        (.then #(f bitmap))
                        (.finally #(.close bitmap)))))
           (.catch (constantly nil)))
       (js/Promise.resolve nil))))

#?(:clj
   (defn- decoded-pixels
     "The pixels of the image in the bytes `b`, which ImageIO decodes at
     about `n` pixels a side, by subsampling, or nil when it can't."
     [b n]
     (with-reader b
       (fn [^ImageReader reader]
         (let [side  (max (.getWidth reader 0) (.getHeight reader 0))
               step  (max 1 (quot side n))
               param (doto (.getDefaultReadParam reader)
                       (.setSourceSubsampling step step 0 0))
               image (.read reader 0 param)]
           (vec (for [y     (range (.getHeight image))
                      x     (range (.getWidth image))
                      :let  [argb (.getRGB image x y)]]
                  (mapv #(bit-and (bit-shift-right argb %) 255) [16 8 0 24]))))))))

#?(:cljs
   (defn- drawn-pixels
     "A promise of the pixels of the image in the bytes `b`, which the
     browser draws at `n` pixels a side, or of nil where it can't, e.g. in
     Node, which has no decoder."
     [b n]
     (with-bitmap b
       (fn [bitmap]
         (let [context (.getContext (js/OffscreenCanvas. n n) "2d")]
           (.drawImage context bitmap 0 0 n n)
           (let [data (.-data (.getImageData context 0 0 n n))]
             (mapv (fn [i] (mapv #(aget data (+ i %)) [0 1 2 3]))
                   (range 0 (.-length data) 4))))))))

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

#?(:clj
   (defn- drawn
     "The `image` drawn at `width` by `height` with bilinear interpolation,
     keeping its transparency when it has any."
     ^BufferedImage [^BufferedImage image width height]
     (let [type (if (.hasAlpha (.getColorModel image))
                  BufferedImage/TYPE_INT_ARGB
                  BufferedImage/TYPE_INT_RGB)
           out  (BufferedImage. (int width) (int height) type)
           g    (.createGraphics out)]
       (try
         (.setRenderingHint g RenderingHints/KEY_INTERPOLATION
                            RenderingHints/VALUE_INTERPOLATION_BILINEAR)
         (.drawImage g image 0 0 (int width) (int height) nil)
         out
         (finally
           (.dispose g))))))

;; Chris Campbell, "The Perils of Image.getScaledInstance()", 2007: bilinear
;; interpolation in one step from a large image to a small one looks no
;; better than nearest neighbour (JDK-6574349), so halve it until the next
;; half would be too small
;; https://bugs.openjdk.org/browse/JDK-6574349
#?(:clj
   (defn- scaled-down
     "The `image` scaled down to `width`, in halves, with the height in
     proportion. An image no wider than `width` stays as it is."
     ^BufferedImage [^BufferedImage image width]
     (loop [^BufferedImage image image]
       (let [w (.getWidth image)
             h (.getHeight image)]
         (cond
           (<= w width)
           image

           (>= (quot w 2) width)
           (recur (drawn image (quot w 2) (max 1 (quot h 2))))

           :else
           (drawn image width (max 1 (Math/round (/ (* (double h) width) w)))))))))

#?(:clj
   (defn- encoded
     "The `image` as a map of its :type, :width, :height and :data, the
     bytes of a JPEG, or of a PNG when it has transparency."
     [^BufferedImage image]
     (let [alpha? (.hasAlpha (.getColorModel image))
           out    (ByteArrayOutputStream.)]
       (if alpha?
         (ImageIO/write image "png" out)
         (let [^ImageWriter writer (first (iterator-seq
                                           (ImageIO/getImageWritersByFormatName
                                            "jpeg")))
               param (doto (.getDefaultWriteParam writer)
                       (.setCompressionMode ImageWriteParam/MODE_EXPLICIT)
                       (.setCompressionQuality (float 0.85)))]
           (try
             (with-open [stream (MemoryCacheImageOutputStream. out)]
               (.setOutput writer stream)
               (.write writer nil (IIOImage. image nil nil) param))
             (finally
               (.dispose writer)))))
       {:type   (if alpha? "image/png" "image/jpeg")
        :width  (.getWidth image)
        :height (.getHeight image)
        :data   (.toByteArray out)})))

;; ImageReadParam's source subsampling reads every nth pixel as it decodes,
;; so a JPEG of 3000 pixels a side never takes the 36 MB of its full size.
;; It reads at twice the widest width, and no more than about 4 million
;; pixels, e.g. of an image far taller than it's wide.
#?(:clj
   (defn- decoded-thumbnails
     "The thumbnails of the image in the bytes `b` at the `widths`, as
     thumbnails gives them, which ImageIO decodes unless the image has more
     than `max-pixels`."
     [b widths max-pixels]
     (with-reader b
       (fn [^ImageReader reader]
         (let [w (.getWidth reader 0)
               h (.getHeight reader 0)]
           (when (and (<= (* (long w) h) max-pixels)
                      (not (and (= "gif" (str/lower-case (.getFormatName reader)))
                                (> (.getNumImages reader true) 1))))
             (let [step  (max 1
                              (quot w (* 2 (apply max widths)))
                              (long (Math/ceil (Math/sqrt (/ (* (double w) h) 4e6)))))
                   param (doto (.getDefaultReadParam reader)
                           (.setSourceSubsampling step step 0 0))
                   image (.read reader 0 param)
                   below (sort > (filter #(< % (.getWidth image)) widths))
                   above (remove (set below) widths)]
               (merge (zipmap below (map encoded (rest (reductions scaled-down image below))))
                      (when (seq above)
                        (zipmap above (repeat (encoded image))))))))))))

;; HTML, section 4.12.5.1.14: an imageSmoothingQuality of high lets the
;; browser filter as it scales down, where low may sample alone
;; https://html.spec.whatwg.org/multipage/canvas.html#image-smoothing
#?(:cljs
   (defn- drawn-thumbnail
     "A promise of the `bitmap` drawn `width` pixels wide, with the height in
     proportion, as thumbnails gives each thumbnail: a PNG when it has a
     pixel that isn't opaque, and a JPEG otherwise."
     [bitmap width]
     (let [h       (max 1 (js/Math.round (/ (* (.-height bitmap) width) (.-width bitmap))))
           canvas  (js/OffscreenCanvas. width h)
           context (.getContext canvas "2d")]
       (set! (.-imageSmoothingQuality context) "high")
       (.drawImage context bitmap 0 0 width h)
       (let [data   (.-data (.getImageData context 0 0 width h))
             alpha? (some #(< (aget data %) 255) (range 3 (.-length data) 4))
             type   (if alpha? "image/png" "image/jpeg")]
         (-> (.convertToBlob canvas #js {:type type :quality 0.85})
             (.then #(.arrayBuffer %))
             (.then (fn [buffer]
                      {:type type :width width :height h :data (js/Uint8Array. buffer)})))))))

#?(:cljs
   (defn- drawn-thumbnails
     "A promise of the thumbnails of the image in the bytes `b` at the
     `widths`, as thumbnails gives them, which the browser decodes, or of
     nil for an image of more than `max-pixels`."
     [b widths max-pixels]
     (with-bitmap b
       (fn [bitmap]
         (let [own (.-width bitmap)]
           (when (<= (* own (.-height bitmap)) max-pixels)
             (let [below (filter #(< % own) widths)
                   above (remove (set below) widths)
                   drawn (cond-> (vec below) (seq above) (conj own))]
               (.then (js/Promise.all (into-array (map #(drawn-thumbnail bitmap %) drawn)))
                      (fn [thumbnails]
                        (merge (zipmap below thumbnails)
                               (when (seq above)
                                 (zipmap above (repeat (last thumbnails))))))))))))))

;; imgproxy, Configuration options, Security: IMGPROXY_MAX_SRC_RESOLUTION
;; https://docs.imgproxy.net/3.22.x/configuration/options
(def max-pixels
  "The most pixels of an image that thumbnails decodes, 25 million, e.g.
  5000 by 5000. imgproxy refuses more than 16.8 million by default, to
  stop images that decode to more memory than their size suggests."
  25000000)

(defn thumbnails
  "The image in the bytes `b` scaled down to each of the `widths`, with the
  :max-pixels of `opts`, as a map of each width to the thumbnail's :type,
  :width, :height and :data, its bytes. On the JVM it returns the map, and
  in ClojureScript a promise of it.

  A thumbnail is a JPEG, or a PNG when the image has transparency. It's
  never wider than the image: the widths at or above the image's own share
  one thumbnail, the image at its own width.

  The platform decodes the image: ImageIO on the JVM, which reads JPEG,
  PNG, GIF and BMP, and the browser's own decoder in ClojureScript. The map
  is nil where it can't, e.g. for WebP on the JVM, or anything in Node, and
  for these:

  - an animated GIF on the JVM. A browser scales its first frame.
  - an image of more than :max-pixels, by default max-pixels. ImageIO
    reads the size before it decodes the image, but a browser decodes the
    whole image first.

  ImageIO ignores the EXIF orientation of a JPEG, so check it first where
  it matters."
  ([b widths]
   (thumbnails b widths {}))
  ([b widths opts]
   (let [most (:max-pixels opts max-pixels)]
     #?(:clj  (decoded-thumbnails b widths most)
        :cljs (drawn-thumbnails b widths most)))))

(comment
  ;; on the JVM, the bytes of an image file
  #?(:clj (def b (java.nio.file.Files/readAllBytes
                  (java.nio.file.Path/of "artwork.png" (make-array String 0)))))
  (seed b)
  (colours b)
  #_.)
