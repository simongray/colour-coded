(ns dk.simongray.colour-coded.impl
  "The conversion and interpolation of colour maps by the rules of CSS Color
  4, which syntax uses for color-mix(), and mix for the rest.

  A colour here is a map of its :space, :coords and :alpha, which these
  functions don't check.
  https://www.w3.org/TR/css-color-4/#interpolation"
  (:require [dk.simongray.colour-coded.space :as space]))

(defn to
  "The colour map `c` in the `space`."
  [c space]
  (let [id (:id (space/space space))]
    (if (= id (:space c))
      c
      (assoc c :space id :coords (space/convert (:coords c) (:space c) id)))))

;; CSS Color 4, section 13.3: a missing component stays missing in a space
;; whose component is analogous. When all the other components, the
;; analogous set, are missing, so are all the other components in the new
;; space, e.g. lab(50% none none) in LCH is lch(50% none none)
(defn carry-forward
  "The colour map `c` in the `space`, where each component that's analogous
  to one missing from `c` is missing too, e.g. the hue of OKLCH when HSL's
  is missing."
  [c space]
  (let [from       (:coords (space/space (:space c)))
        to-coords  (:coords (space/space space))
        shared     (set (keep :category from))
        analogous? (fn [{:keys [category]} others]
                     (and category (some #(= category (:category %)) others)))
        missing    (set (keep (fn [[{:keys [category]} v]] (when (nil? v) category))
                              (map vector from (:coords c))))
        rest-from  (remove #(analogous? (first %) to-coords) (map vector from (:coords c)))
        set-gone?  (and (seq rest-from) (every? (comp nil? second) rest-from))]
    (update (to c space) :coords
            #(mapv (fn [{:keys [category] :as coord} v]
                     (cond
                       (and category (missing category) (shared category)) nil
                       (and set-gone? (not (analogous? coord from)))       nil
                       :else                                               v))
                   to-coords
                   %))))

;; CSS Color 4, section 13.4
(defn- premultiply
  "The `coords` times the `alpha`, or divided by it when `f` is /, except
  the hue at the index `hue`. A missing alpha skips both, and an alpha of
  zero the division."
  [f coords alpha hue]
  (if (or (nil? alpha) (and (= f /) (zero? alpha)))
    coords
    (vec (map-indexed #(if (or (= %1 hue) (nil? %2)) %2 (f %2 alpha)) coords))))

;; CSS Color 4, section 13.5
(defn fix-hues
  "The hues `h1` and `h2`, in degrees, with 360 added to one of them so that
  a linear interpolation turns as the `method` says: :shorter, :longer,
  :increasing or :decreasing."
  [h1 h2 method]
  (let [d (- h2 h1)]
    (case method
      :shorter    (cond (> d 180)  [(+ h1 360) h2]
                        (< d -180) [h1 (+ h2 360)]
                        :else      [h1 h2])
      :longer     (cond (< 0 d 180)               [(+ h1 360) h2]
                        (and (< -180 d) (<= d 0)) [h1 (+ h2 360)]
                        :else                     [h1 h2])
      :increasing (if (< h2 h1) [h1 (+ h2 360)] [h1 h2])
      :decreasing (if (< h1 h2) [(+ h1 360) h2] [h1 h2]))))

(defn- lerp
  "The value `t` of the way from `a` to `b`, either of which may be
  missing, or nil when both are."
  [a b t]
  (cond
    (and (nil? a) (nil? b)) nil
    (nil? a)                b
    (nil? b)                a
    :else                   (+ a (* t (- b a)))))

(defn interpolate
  "A function that takes a progress from 0 to 1 and returns the colour that
  far from the colour map `a` to `b`, interpolated in the `space` with the
  `hue-method` of fix-hues, by the rules of CSS Color 4."
  [a b space hue-method]
  (let [hue      (space/hue-index space)
        ca       (carry-forward a space)
        cb       (carry-forward b space)
        alpha-a  (if (some? (:alpha ca)) (:alpha ca) (:alpha cb))
        alpha-b  (if (some? (:alpha cb)) (:alpha cb) (:alpha ca))
        fill     (fn [xs ys] (mapv #(if (nil? %1) %2 %1) xs ys))
        xs       (fill (:coords ca) (:coords cb))
        ys       (fill (:coords cb) (:coords ca))
        [xs ys]  (if (and hue (xs hue) (ys hue))
                   (let [[h1 h2] (fix-hues (xs hue) (ys hue) hue-method)]
                     [(assoc xs hue h1) (assoc ys hue h2)])
                   [xs ys])
        xs       (premultiply * xs alpha-a hue)
        ys       (premultiply * ys alpha-b hue)
        id       (:id (space/space space))]
    (fn [t]
      (let [alpha  (lerp alpha-a alpha-b t)
            coords (premultiply / (mapv #(lerp %1 %2 t) xs ys) alpha hue)]
        {:space  id
         :coords (cond-> coords
                   (and hue (coords hue)) (update hue mod 360))
         :alpha  alpha}))))
