(ns dk.simongray.colour-coded.choose
  "Colours chosen from among others, e.g. from the palette of an image or
  from colours that a designer made."
  (:refer-clojure :exclude [spread])
  (:require [dk.simongray.colour-coded :as cc]
            [dk.simongray.colour-coded.contrast :as contrast]
            [dk.simongray.colour-coded.difference :as difference]))

(defn chroma
  "The chroma of the colour `x` in OKLCH: 0 for a grey, and about 0.3 at
  most in sRGB."
  [x]
  (or (get-in (cc/to x :oklch) [:coords 1]) 0.0))

(defn chromatic?
  "Whether the colour `x` has an OKLCH chroma of at least `c`, by default
  0.04, which off-whites and near-greys don't."
  ([x]
   (chromatic? x 0.04))
  ([x c]
   (>= (chroma x) c)))

;; Material 3 derives a whole scheme from one source colour, which a grey
;; can't be, since it has no hue; an image whose most common colour is
;; white or black still has one further down its palette
;; https://m3.material.io/styles/color/dynamic/user-generated-source
(defn seed
  "The first chromatic colour of the `palette`, which lists the most common
  first, to derive the colours of a UI from, or nil where every colour is
  about grey."
  [palette]
  (first (filter chromatic? palette)))

(defn first-with-contrast
  "The first colour of `xs` whose contrast ratio with the `background` is at
  least `r`, or nil. WCAG 2.2 asks for 3 for a control."
  [xs background r]
  (first (filter #(>= (contrast/ratio background %) r) xs)))

(defn most-contrast
  "The colour of `xs` that contrasts most with the `background`."
  [xs background]
  (when (seq xs)
    (apply max-key #(contrast/ratio background %) xs)))

(defn spread
  "The colours of `xs` that differ by a ΔEOK of at least `d` from every
  colour kept before them, e.g. 0.1 for colours that are clearly apart."
  [xs d]
  (reduce (fn [kept x]
            (if (every? #(>= (difference/delta-e x %) d) kept)
              (conj kept x)
              kept))
          []
          xs))
