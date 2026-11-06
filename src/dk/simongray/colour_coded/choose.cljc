(ns dk.simongray.colour-coded.choose
  "Colours chosen from among others, e.g. the colours of an image or those that
  a designer made, and a small scheme for a UI chosen from them."
  (:refer-clojure :exclude [spread])
  (:require [dk.simongray.colour-coded :as cc]
            [dk.simongray.colour-coded.contrast :as contrast]
            [dk.simongray.colour-coded.difference :as difference]
            [dk.simongray.colour-coded.harmony :as harmony]))

(defn chroma
  "The chroma of the colour `x` in OKLCH: 0 for a grey, and about 0.3 at
  most in sRGB."
  [x]
  (when-let [c (cc/to x :oklch)]
    (or (get-in c [:coords 1]) 0.0)))

(defn chromatic?
  "Whether the colour `x` has an OKLCH chroma of at least `c`, by default
  0.04, which off-whites and near-greys don't."
  ([x]
   (chromatic? x 0.04))
  ([x c]
   (let [own (chroma x)]
     (and (some? own) (>= own c)))))

;; Material 3 derives a whole scheme from one source colour, which a grey
;; can't be, since it has no hue; an image whose most common colour is
;; white or black still has one further down its palette
;; https://m3.material.io/styles/color/dynamic/user-generated-source
(defn seed
  "The first chromatic colour of `xs`, which lists the most common first, to
  derive the colours of a UI from, or nil where every colour is about grey."
  [xs]
  (first (filter chromatic? xs)))

;; WCAG 2.2, success criterion 1.4.3
(defn first-with-contrast
  "The first colour of `xs` whose contrast ratio with the `background` is at
  least `r`, or nil. By default `r` is 4.5, which WCAG 2.2 asks of text. It
  asks 3 of a control."
  ([xs background]
   (first-with-contrast xs background 4.5))
  ([xs background r]
   (first (filter #(when-let [own (contrast/ratio background %)] (>= own r)) xs))))

(defn most-contrast
  "The colour of `xs` that contrasts most with the `background`."
  [xs background]
  (let [colours (filter #(contrast/ratio background %) xs)]
    (when (seq colours)
      (apply max-key #(contrast/ratio background %) colours))))

(defn spread
  "The colours of `xs` that differ from every colour kept before them by a ΔEOK
  of at least `d`, by default 0.1, which is clearly apart."
  ([xs]
   (spread xs 0.1))
  ([xs d]
   (reduce (fn [kept x]
             (if (every? #(>= (difference/delta-e x %) d) kept)
               (conj kept x)
               kept))
           []
           (filter cc/colour xs))))

;; WCAG 2.2, success criterion 1.4.11: a control has a contrast of 3:1
(defn scheme
  "The colours of a UI by the colours `xs`, which list the most common first,
  e.g. those of an image: a map of these, or nil for no colours.

  - :background, the most common colour, to tint by
  - :text, black or white, whichever reads better on the background
  - :accent, for controls, e.g. a progress bar: the first other colour
    whose contrast with the background is 3:1, which WCAG 2.2 asks of a
    control, or else the background's complement in HSL when its contrast
    is, or else the text colour
  - :on-accent, black or white, whichever reads better on the accent
  - :seed, the first colour with a hue, by seed, or nil"
  [xs]
  (let [colours (keep cc/colour xs)]
    (when-let [background (first colours)]
      (let [text   (contrast/black-or-white background)
            others (concat (rest colours) [(harmony/complement background :hsl)])
            accent (or (first-with-contrast others background 3) text)]
        {:background background
         :text       text
         :accent     accent
         :on-accent  (contrast/black-or-white accent)
         :seed       (seed colours)}))))
