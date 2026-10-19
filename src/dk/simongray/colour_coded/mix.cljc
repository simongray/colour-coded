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
            [dk.simongray.colour-coded.impl :as impl]))

(defn interpolate
  "A function that takes a progress from 0 to 1 and returns the colour that
  far from the colour `a` to `b`, interpolated in the `space`, by default
  Oklab, with a hue that turns as the `hue-method` says: :shorter, the
  default, :longer, :increasing or :decreasing."
  ([a b]
   (interpolate a b :oklab))
  ([a b space]
   (interpolate a b space :shorter))
  ([a b space hue-method]
   (impl/interpolate (cc/colour a) (cc/colour b) space hue-method)))

(defn mix
  "The colour `p`, from 0 to 1, of the way from the colour `a` to `b`,
  interpolated in the `space`, by default Oklab, with the `hue-method` of
  interpolate. It's color-mix(in space, a, b p) with p as a percentage."
  ([a b p]
   (mix a b p :oklab))
  ([a b p space]
   (mix a b p space :shorter))
  ([a b p space hue-method]
   ((interpolate a b space hue-method) p)))

(defn steps
  "The `n` colours evenly spaced from the colour `a` to `b`, both included,
  interpolated in the `space`, by default Oklab, with the `hue-method` of
  interpolate."
  ([a b n]
   (steps a b n :oklab))
  ([a b n space]
   (steps a b n space :shorter))
  ([a b n space hue-method]
   (let [f (interpolate a b space hue-method)]
     (if (< n 2)
       (mapv f (take n [0]))
       (mapv #(f (/ % (double (dec n)))) (range n))))))
