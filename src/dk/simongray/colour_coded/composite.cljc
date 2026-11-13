(ns dk.simongray.colour-coded.composite
  "One colour over another, as a browser draws a translucent colour on top of
  what lies behind it.

  These are its sources:

  - Compositing and Blending Level 1, for the formula: the simple alpha
    compositing of section 5.1, which section 9.1.4 gives again as the
    Porter-Duff operator source over
  - CSS Color 4, section 10.13, which requires that formula of a browser
    when it draws
  - the W3C's ACT rule afw4f7, Text has minimum contrast, for the default
    space, since the specification names none (fxtf-drafts issue 427)

  The tests compare over with what ColorAide computes, in
  test/resources/composite.json, which dev/composite.py writes.

  https://www.w3.org/TR/compositing-1/#simplealphacompositing
  https://www.w3.org/TR/compositing-1/#porterduffcompositingoperators_srcover
  https://www.w3.org/TR/css-color-4/#alpha
  https://www.w3.org/WAI/standards-guidelines/act/rules/afw4f7/
  https://github.com/w3c/fxtf-drafts/issues/427"
  (:require [dk.simongray.colour-coded :as cc]
            [dk.simongray.colour-coded.space :as space]))

;; Compositing and Blending Level 1, sections 5.1 and 9.1.4, with a as the
;; source and b as the backdrop: αo = αs + αb × (1 - αs), and
;; Co = (Cs × αs + Cb × αb × (1 - αs)) / αo
(defn over
  "The colour `a` composited over `b`, i.e. what shows where `a` lies on top
  of `b`, computed in the `space`, by default sRGB.

  The result isn't clipped, so a colour outside the gamut of `space` can stay
  outside it. For a polar `space`, e.g. :oklch, the result is nil, since the
  formula averages each component, and a hue is an angle.

  The specification leaves three things open, so these are judgements:

  - sRGB as the default, with its components gamma-encoded. The W3C's ACT
    rule afw4f7 puts the contrast of black at an alpha of 30% on white at
    2.1. That's the result in sRGB, while in linear light it's 1.4. mix
    defaults to Oklab instead, as color-mix() does.
  - Components of 0 where both colours have an alpha of 0, since the formula
    divides by the alpha of the result. CSS Color 4 does the same when it
    interpolates two such colours (section 13.5).
  - 0 for a missing component and for a missing alpha, as CSS Color 4 has it
    for a colour that a browser draws (section 4.4).

  To composite several layers, reduce them from the bottom up:

      (reduce #(over %2 %1) layers)"
  ([a b]
   (over a b :srgb))
  ([a b space]
   (let [ca (cc/to a space)
         cb (cc/to b space)]
     (when (and ca cb (not (space/hue-index space)))
       (let [alpha-a   (double (or (:alpha ca) 0))
             uncovered (* (or (:alpha cb) 0) (- 1 alpha-a))
             alpha     (+ alpha-a uncovered)]
         {:space  (:space ca)
          :coords (mapv (fn [x y]
                          (if (zero? alpha)
                            0.0
                            (/ (+ (* alpha-a (or x 0)) (* uncovered (or y 0)))
                               alpha)))
                        (:coords ca)
                        (:coords cb))
          :alpha  alpha})))))
