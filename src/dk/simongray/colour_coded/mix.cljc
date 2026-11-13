(ns dk.simongray.colour-coded.mix
  "Colours between colours, interpolated by the rules that CSS Color 4
  gives gradients and color-mix():

  - in Oklab, unless a space is given
  - with premultiplied alpha
  - with a missing component taken from the other colour
  - with a hue that turns the shorter way round, unless a method says
    otherwise

  A gradient through several colours places its stops as CSS Images 3 does,
  in sections 3.4.2 and 3.4.3.

  https://www.w3.org/TR/css-color-4/#interpolation
  https://www.w3.org/TR/css-images-3/#color-stop-fixup"
  (:require [dk.simongray.colour-coded :as cc]
            [dk.simongray.colour-coded.impl :as impl]
            [dk.simongray.colour-coded.space :as space]))

(def hue-methods
  "The ways that a hue can turn from one colour to another, by CSS Color 4."
  #{:shorter :longer :increasing :decreasing})

(defn interpolate
  "A function that takes a progress from 0 to 1 and returns the colour that
  far from the colour `a` to `b`. It interpolates in the `space`, by default
  Oklab, and turns a hue by the `hue-method`, one of hue-methods, by default
  :shorter."
  ([a b]
   (interpolate a b :oklab))
  ([a b space]
   (interpolate a b space :shorter))
  ([a b space hue-method]
   (let [ca (cc/colour a)
         cb (cc/colour b)]
     (when (and ca cb (space/space space) (hue-methods hue-method))
       (impl/interpolate ca cb space hue-method)))))

(defn mix
  "The colour `p` of the way from the colour `a` to `b`, where `p` runs from 0
  to 1 and is by default 0.5. It interpolates in the `space`, by default Oklab,
  with the `hue-method` of interpolate. It's color-mix(in space, a, b p) with p
  as a percentage."
  ([a b]
   (mix a b 0.5))
  ([a b p]
   (mix a b p :oklab))
  ([a b p space]
   (mix a b p space :shorter))
  ([a b p space hue-method]
   (when-let [f (interpolate a b space hue-method)]
     (f p))))

(defn steps
  "The `n` colours evenly spaced from the colour `a` to `b`, both included,
  interpolated in the `space`, by default Oklab, with the `hue-method` of
  interpolate."
  ([a b n]
   (steps a b n :oklab))
  ([a b n space]
   (steps a b n space :shorter))
  ([a b n space hue-method]
   (when-let [f (interpolate a b space hue-method)]
     (if (< n 2)
       (mapv f (take n [0]))
       (mapv #(f (/ % (double (dec n)))) (range n))))))

(defn- stop
  "The stop `x` of a gradient, a colour or a pair of a colour and a position,
  as a map of its :colour and its :position, or nil for anything else. A stop
  without a position has nil for one."
  [x]
  (let [[c position] (if (sequential? x) x [x])]
    (when-let [colour (cc/colour c)]
      (when (or (nil? position) (number? position))
        {:colour colour :position position}))))

;; CSS Images 3, section 3.4.3, whose three steps these are
(defn- with-ends
  "The positions `ps`, where nil is a missing one, with 0 for the first and 1
  for the last where those are missing."
  [ps]
  (-> (vec ps)
      (update 0 #(or % 0.0))
      (update (dec (count ps)) #(or % 1.0))))

(defn- in-order
  "The positions `ps` with each one that lies before an earlier one moved up
  to it."
  [ps]
  (mapv (fn [p earlier] (when p (apply max p (remove nil? earlier))))
        ps
        (reductions conj [] ps)))

(defn- evenly-spaced
  "The positions `ps` with each missing one spaced evenly between those on
  either side of it."
  [ps]
  (let [known (keep-indexed #(when %2 %1) ps)]
    (mapv (fn [i p]
            (or p
                (let [a (last (take-while #(< % i) known))
                      b (first (drop-while #(< % i) known))]
                  (+ (ps a) (* (- (ps b) (ps a)) (/ (- i a) (double (- b a))))))))
          (range)
          ps)))

(defn- stops
  "The stops `xs` of a gradient as maps of :colour and :position, without
  those that aren't colours, each with the position that CSS gives it."
  [xs]
  (when-let [given (seq (keep stop xs))]
    (map #(assoc %1 :position %2)
         given
         (-> (map :position given) with-ends in-order evenly-spaced))))

(defn- segments
  "The `stops` of a gradient in adjacent pairs, as maps of where each pair
  begins and ends, :from and :to, and of :f, the function that interpolates
  between its colours in the `space` by the `hue-method`. A single stop pairs
  with itself."
  [stops space hue-method]
  (map (fn [a b]
         {:from (:position a)
          :to   (:position b)
          :f    (impl/interpolate (:colour a) (:colour b) space hue-method)})
       stops
       (or (next stops) stops)))

;; CSS Images 3, section 3.4.2, which doesn't say which of two stops at one
;; position is the colour there. Here it's the later one
(defn gradient
  "A function that takes a position from 0 to 1 and returns the colour there
  on a gradient through the colours `xs`, interpolated in the `space`, by
  default Oklab, with the `hue-method` of interpolate.

  The colours are the stops of a CSS gradient, each a colour or a pair of a
  colour and its position, e.g. [\"white\" 0.2]. CSS's rules place them:

  - the first stop is at 0 and the last at 1, unless they have a position
  - a stop before an earlier one moves up to it
  - the rest are spaced evenly between their neighbours
  - the colour changes at once where two stops share a position
  - before the first stop and after the last, the colour is theirs

  A browser draws a gradient in sRGB when every colour is written as a hex
  colour, a name, rgb(), hsl() or hwb(), so pass :srgb to match it.

      ((gradient [\"red\" [\"white\" 0.2] \"blue\"] :srgb) 0.1)
      ;; as linear-gradient(red, white 20%, blue), a tenth of the way along"
  ([xs]
   (gradient xs :oklab))
  ([xs space]
   (gradient xs space :shorter))
  ([xs space hue-method]
   (when (and (space/space space) (hue-methods hue-method))
     (when-let [pairs (some-> (stops xs) (segments space hue-method))]
       (fn [p]
         (let [{:keys [from to f]} (or (last (take-while #(<= (:from %) p) pairs))
                                       (first pairs))]
           (f (cond
                (< p from)  0
                (= from to) 1
                :else       (min 1 (/ (- p from) (- to from)))))))))))

(defn gradient-steps
  "The `n` colours evenly spaced along the gradient through the colours `xs`,
  both ends included, in the `space` and with the `hue-method` of gradient."
  ([xs n]
   (gradient-steps xs n :oklab))
  ([xs n space]
   (gradient-steps xs n space :shorter))
  ([xs n space hue-method]
   (when-let [f (gradient xs space hue-method)]
     (mapv #(f (/ % (double (max 1 (dec n))))) (range n)))))
