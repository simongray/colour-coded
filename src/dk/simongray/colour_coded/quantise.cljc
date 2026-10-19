(ns dk.simongray.colour-coded.quantise
  "The few colours that stand for the many pixels of an image.

  The pixels are a sequence of vectors of red, green, blue and alpha from 0
  to 255, as image/pixels, a canvas or ImageIO gives them. Only the opaque
  ones count, with an alpha of 128 or more. Each function returns maps of
  a :colour and its :share of the opaque pixels, the largest share first."
  (:require [dk.simongray.colour-coded.gamut :as gamut]
            [dk.simongray.colour-coded.space :as space]))

(defn opaque
  "The pixels of `pixels` with an alpha of 128 or more."
  [pixels]
  (filter #(>= (nth % 3) 128) pixels))

(defn- rgb
  "The mean red, green and blue of the `pixels`, each from 0 to 255, rounded
  down, as an sRGB colour."
  [pixels]
  (let [n (count pixels)]
    {:space  :srgb
     :coords (mapv (fn [i] (/ (quot (reduce + (map #(nth % i) pixels)) n) 255.0))
                   [0 1 2])
     :alpha  1.0}))

;; The popularity method, which Heckbert's median cut replaced, on a grid
;; of 8×8×8 cells of sRGB: Heckbert, Color image quantization for frame
;; buffer display, SIGGRAPH 1982, https://doi.org/10.1145/965145.801294
(defn buckets
  "At most `n` colours of the `pixels`, each the mean of a bucket of those
  that agree in the top three bits of each channel, the most common first.
  It's fast and stable, but splits a cluster that straddles the edge of a
  bucket, and counts in sRGB, which isn't perceptual."
  [pixels n]
  (let [opaque (opaque pixels)
        total  (count opaque)
        bucket (fn [[r g b]] [(quot r 32) (quot g 32) (quot b 32)])
        groups (sort-by (juxt (comp - count) (comp bucket first))
                        (vals (group-by bucket opaque)))]
    (mapv (fn [group]
            {:colour (rgb group)
             :share  (/ (count group) (double total))})
          (take n groups))))

(defn- oklab
  "The colour of the sRGB bytes `r`, `g` and `b` in Oklab, as a vector."
  [r g b]
  (space/convert [(/ r 255.0) (/ g 255.0) (/ b 255.0)] :srgb :oklab))

(defn- distance2
  "The square of the distance of the points `p` and `q`."
  [p q]
  (reduce + (map #(let [d (- %1 %2)] (* d d)) p q)))

(defn- cells
  "The opaque `pixels` grouped into cells of 5 bits a channel, as maps of
  each cell's mean colour, as a :point in Oklab, and its :weight, the
  number of its pixels."
  [pixels]
  (for [group (vals (group-by (fn [[r g b]] [(quot r 8) (quot g 8) (quot b 8)])
                              (opaque pixels)))
        :let  [{[r g b] :coords} (rgb group)]]
    {:point  (oklab (* 255 r) (* 255 g) (* 255 b))
     :weight (count group)}))

(defn- initial-centres
  "The `n` points of the `cells` that are heavy and far apart, the heaviest
  first, each the cell with the greatest weight times its squared distance
  to the nearest chosen so far: a deterministic form of k-means++."
  [cells n]
  (loop [centres [(:point (apply max-key :weight cells))]]
    (if (>= (count centres) (min n (count cells)))
      centres
      (let [score     (fn [{:keys [point weight]}]
                        (* weight (apply min (map #(distance2 point %) centres))))
            candidate (apply max-key score cells)]
        (if (zero? (score candidate))
          centres
          (recur (conj centres (:point candidate))))))))

(defn- assign
  "The `cells` grouped by the index of their nearest of the `centres`."
  [cells centres]
  (group-by (fn [{:keys [point]}]
              (apply min-key #(distance2 point (centres %)) (range (count centres))))
            cells))

(defn- centre
  "The weighted mean point of the `cells`."
  [cells]
  (let [w (reduce + (map :weight cells))]
    (mapv (fn [i] (/ (reduce + (map #(* (:weight %) ((:point %) i)) cells)) w))
          [0 1 2])))

;; Celebi, Improving the performance of k-means for color quantization,
;; Image and Vision Computing 29, 2011, https://arxiv.org/abs/1101.0395
(defn k-means
  "At most `n` colours of the `pixels`, by weighted k-means in Oklab over
  cells of 5 bits a channel, from far-apart centres, the most common
  first. Unlike buckets, it puts a colour where a cluster of pixels is."
  [pixels n]
  (let [cells (cells pixels)
        total (reduce + (map :weight cells))]
    (when (seq cells)
      (loop [centres (initial-centres cells n) i 0]
        (let [groups (assign cells centres)
              moved  (mapv #(if-let [g (groups %)] (centre g) (centres %))
                           (range (count centres)))]
          (if (or (= moved centres) (>= i 10))
            (->> groups
                 (map (fn [[k g]]
                        {:colour (gamut/clip {:space :oklab :coords (centres k) :alpha 1.0})
                         :share  (/ (reduce + (map :weight g)) (double total))}))
                 (sort-by (comp - :share))
                 vec)
            (recur moved (inc i))))))))
