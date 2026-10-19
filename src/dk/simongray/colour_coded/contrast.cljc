(ns dk.simongray.colour-coded.contrast
  "The contrast of two colours by WCAG 2.2, and the text colour that reads
  best on one.

  WCAG 2.2 asks for these contrast ratios:

  - 4.5 for text, and 3 for large text, i.e. 18 points, or 14 points bold
    (success criterion 1.4.3)
  - 3 for the parts of a control and for graphics (1.4.11)
  - 7 and 4.5 at level AAA (1.4.6)

  The ratio overstates the contrast of dark colours, which APCA addresses.
  APCA's licence forbids changing it and using it commercially, so it isn't
  here, but any function of two colours can stand in for ratio where an app
  wants its own measure.

  https://www.w3.org/TR/WCAG22/#dfn-contrast-ratio"
  (:require [dk.simongray.colour-coded :as cc]
            [dk.simongray.colour-coded.space :as space]))

;; WCAG 2.2, the definition of relative luminance, whose threshold has
;; matched sRGB's 0.04045 since 2021
(defn luminance
  "The relative luminance of the colour `x`, from 0 for black to 1 for
  white, by WCAG 2.2, from its sRGB channels."
  [x]
  (let [[r g b] (map #(space/srgb->linear (or % 0)) (:coords (cc/to x :srgb)))]
    (+ (* 0.2126 r) (* 0.7152 g) (* 0.0722 b))))

(defn ratio
  "The contrast ratio of the colours `a` and `b`, from 1 to 21. Text wants
  4.5, and a control 3."
  [a b]
  (let [[l1 l2] (sort > (map #(max 0.0 (luminance %)) [a b]))]
    (/ (+ l1 0.05) (+ l2 0.05))))

(defn level
  "The level of WCAG 2.2 that the contrast of the colours `a` and `b`
  meets: :aaa for 7 or more, :aa for 4.5, :large for 3, which is enough
  for large text and for controls, or nil."
  [a b]
  (let [r (ratio a b)]
    (cond
      (>= r 7)   :aaa
      (>= r 4.5) :aa
      (>= r 3)   :large)))

(def black
  "Black, in sRGB."
  {:space :srgb :coords [0.0 0.0 0.0] :alpha 1.0})

(def white
  "White, in sRGB."
  {:space :srgb :coords [1.0 1.0 1.0] :alpha 1.0})

;; CSS Color 5, section 8: contrast-color() leaves the measure to the
;; browser, and resolves a tie to white
(defn black-or-white
  "Black or white, whichever contrasts more with the colour `x` by WCAG
  2.2, and white on a tie. contrast-color() of CSS Color 5 may choose
  otherwise, since its measure is the browser's."
  [x]
  (if (> (ratio x black) (ratio x white))
    black
    white))
