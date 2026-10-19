(ns dk.simongray.colour-coded.harmony
  "Colours of related hues: the classical harmonies of the colour wheel,
  turned in OKLCH unless a space is given.

  The harmonies go back to Itten (The Art of Color, 1961), whose wheel is
  the painter's. Turned in OKLCH, a colour keeps its lightness and its
  chroma, while a turn of HSL's hue changes how light a colour looks
  (Ottosson, A perceptual color space for image processing, 2020). The
  evidence for the harmonies is mixed: Schloss and Palmer (2011) found that
  people rate pairs of similar hues as more harmonious, but prefer a figure
  whose hue contrasts with its ground.
  https://bottosson.github.io/posts/oklab/
  https://pmc.ncbi.nlm.nih.gov/articles/PMC3037488"
  (:refer-clojure :exclude [complement])
  (:require [dk.simongray.colour-coded :as cc]
            [dk.simongray.colour-coded.space :as space]))

(defn rotate
  "The colour `x` with its hue turned by `degrees`, in the polar `space`, by
  default OKLCH, e.g. :hsl to turn it as most colour pickers do."
  ([x degrees]
   (rotate x degrees :oklch))
  ([x degrees space]
   (let [i (space/hue-index space)]
     (some-> (cc/to x space)
             (update-in [:coords i] #(when % (mod (+ % degrees) 360)))))))

(defn turns
  "The colour `x`, and the colours whose hues are turned from it by each of
  `degrees`, in the polar `space`, by default OKLCH."
  ([x degrees]
   (turns x degrees :oklch))
  ([x degrees space]
   (mapv #(rotate x % space) (cons 0 degrees))))

(defn complement
  "The colour `x` with its hue turned halfway round, in the polar `space`,
  by default OKLCH."
  ([x]
   (complement x :oklch))
  ([x space]
   (rotate x 180 space)))

(defn analogous
  "The colour `x` and its neighbours, 30° to either side, in the `space`."
  ([x]
   (analogous x :oklch))
  ([x space]
   (turns x [-30 30] space)))

(defn split-complement
  "The colour `x` and the two hues 30° to either side of its complement, in
  the `space`."
  ([x]
   (split-complement x :oklch))
  ([x space]
   (turns x [150 210] space)))

(defn triad
  "The colour `x` and the two whose hues are a third of a turn from it, in
  the `space`."
  ([x]
   (triad x :oklch))
  ([x space]
   (turns x [120 240] space)))

(defn square
  "The colour `x` and the three whose hues are a quarter, a half and three
  quarters of a turn from it, in the `space`."
  ([x]
   (square x :oklch))
  ([x space]
   (turns x [90 180 270] space)))

(defn wheel
  "The colour `x` and others, `n` in all, with their hues spaced evenly
  round the wheel of the `space`."
  ([x n]
   (wheel x n :oklch))
  ([x n space]
   (turns x (map #(* % (/ 360.0 n)) (range 1 n)) space)))
