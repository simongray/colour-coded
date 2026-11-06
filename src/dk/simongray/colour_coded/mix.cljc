(ns dk.simongray.colour-coded.mix
  "Colours between colours, interpolated by the rules that CSS Color 4
  gives gradients and color-mix():

  - in Oklab, unless a space is given
  - with premultiplied alpha
  - with a missing component taken from the other colour
  - with a hue that turns the shorter way round, unless a method says
    otherwise

  https://www.w3.org/TR/css-color-4/#interpolation"
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
