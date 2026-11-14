(ns dk.simongray.colour-coded.contrast
  "The contrast of two colours by WCAG 2.2, the text colour that reads best
  on one, and a colour at the contrast that you ask for.

  WCAG 2.2 asks for these contrast ratios:

  - 4.5 for text, and 3 for large text, i.e. 18 points, or 14 points bold
    (success criterion 1.4.3)
  - 3 for the parts of a control and for graphics (1.4.11)
  - 7 and 4.5 at level AAA (1.4.6)

  The ratio overstates the contrast of dark colours, which APCA addresses.
  APCA's licence forbids changing it and using it commercially, so it isn't
  here, but any function of two colours can stand in for ratio where an app
  wants its own measure.

  WCAG defines the ratio for opaque colours, so ratio flattens a translucent
  colour first, as a browser draws it. These are the sources for that:

  - CSS Color 6, an Editor's Draft, section 2.2.2, for the order: the
    background over an opaque canvas, and then the text over the result
  - WCAG 2.2, note 3 of the ratio's definition, for white as the canvas,
    which CSS Color 6 leaves open (csswg-drafts issue 7358)
  - the W3C's ACT rule afw4f7, Text has minimum contrast, whose ratios for
    translucent text are those of the text flattened in sRGB

  https://www.w3.org/TR/WCAG22/#dfn-contrast-ratio
  https://drafts.csswg.org/css-color-6/#contrast-transparency
  https://github.com/w3c/csswg-drafts/issues/7358
  https://www.w3.org/WAI/standards-guidelines/act/rules/afw4f7/"
  (:require [dk.simongray.colour-coded :as cc]
            [dk.simongray.colour-coded.composite :as composite]
            [dk.simongray.colour-coded.gamut :as gamut]
            [dk.simongray.colour-coded.space :as space]))

(def black
  "Black, in sRGB."
  {:space :srgb :coords [0.0 0.0 0.0] :alpha 1.0})

(def white
  "White, in sRGB."
  {:space :srgb :coords [1.0 1.0 1.0] :alpha 1.0})

;; WCAG 2.2, the definition of relative luminance, whose threshold has
;; matched sRGB's 0.04045 since 2021
(defn luminance
  "The relative luminance of the colour `x`, from 0 for black to 1 for
  white, by WCAG 2.2, from its sRGB channels. It ignores alpha."
  [x]
  (when-let [{:keys [coords]} (cc/to x :srgb)]
    (let [[r g b] (map #(space/srgb->linear (or % 0)) coords)]
      (+ (* 0.2126 r) (* 0.7152 g) (* 0.0722 b)))))

;; CSS Color 6, Editor's Draft, section 2.2.2, with the canvas that WCAG 2.2
;; assumes where no background is specified
(defn ratio
  "The contrast ratio of the colour `a` on the colour `b`, from 1 to 21,
  with `b` on the `canvas`, by default white. Text wants 4.5, and a control 3.

  The order and the `canvas` only matter for a translucent colour, which
  composite/over flattens in sRGB: `b` over the `canvas`, and then `a` over
  the result. White is a judgement, so pass the page's own colour where you
  know it, e.g. in a dark theme. The result is nil where `b` is still
  translucent on the `canvas`."
  ([a b]
   (ratio a b white))
  ([a b canvas]
   (let [ground (composite/over b canvas)
         la     (luminance (composite/over a ground))
         lb     (luminance ground)]
     (when (and la lb (= 1.0 (:alpha ground)))
       (let [[l1 l2] (sort > (map #(max 0.0 %) [la lb]))]
         (/ (+ l1 0.05) (+ l2 0.05)))))))

(defn wcag-level
  "The level of WCAG 2.2 that the contrast of the colour `a` on `b` meets, as
  ratio measures it with the `canvas`: :aaa for 7 or more, :aa for 4.5,
  :large for 3, which is enough for large text and for controls, or nil
  where it meets none."
  ([a b]
   (wcag-level a b white))
  ([a b canvas]
   (when-let [r (ratio a b canvas)]
     (cond
       (>= r 7)   :aaa
       (>= r 4.5) :aa
       (>= r 3)   :large))))

;; CSS Color 5, section 8: contrast-color() leaves the measure to the
;; browser, and resolves a tie to white
(defn black-or-white
  "Black or white, whichever contrasts more on the colour `x` by WCAG 2.2,
  and white on a tie. A translucent `x` lies on the `canvas`, by default
  white, as in ratio. contrast-color() of CSS Color 5 may choose otherwise,
  since its measure is the browser's."
  ([x]
   (black-or-white x white))
  ([x canvas]
   (when-let [dark (ratio black x canvas)]
     (if (> dark (ratio white x canvas))
       black
       white))))

;; Black and white text contrast equally at a relative luminance of about
;; 0.179, just under an L* of 50. Taking that for the line between dark and
;; light is a judgement: Material prefers white text on any tone below 60
;; https://github.com/material-foundation/material-color-utilities/blob/main/typescript/dynamiccolor/dynamic_color.ts
(defn dark?
  "Whether the colour `x` is dark, i.e. white text has more contrast on it
  than black text, or as much. A translucent `x` lies on the `canvas`, by
  default white, as in ratio, so it's as dark as it shows there."
  ([x]
   (dark? x white))
  ([x canvas]
   (= white (black-or-white x canvas))))

(defn light?
  "Whether the colour `x` is light, i.e. black text has more contrast on it
  than white text. A translucent `x` lies on the `canvas`, by default white,
  as in ratio, so it's as light as it shows there."
  ([x]
   (light? x white))
  ([x canvas]
   (= black (black-or-white x canvas))))

(defn- at-lightness
  "The colour `x` at the OKLCH lightness `l`, with its chroma lowered until
  it lies in sRGB."
  [x l]
  (gamut/reduce-chroma (cc/with x :oklch {:l l}) :oklch))

(defn- search
  "The lightness at which the colour `x` has a contrast ratio of just `r` or
  more with the `background`, searched for between `from`, where the
  contrast is least, and `to`, where it's most, or nil where even `to`
  falls short."
  [x background r from to]
  (when (>= (ratio (at-lightness x to) background) r)
    (loop [near from far to n 0]
      (let [mid (/ (+ near far) 2)]
        (cond
          (>= n 30)                                       far
          (>= (ratio (at-lightness x mid) background) r)  (recur near mid (inc n))
          :else                                           (recur mid far (inc n)))))))

;; The method of Adobe's Leonardo: a search for the lightness at which a
;; colour has the contrast asked for, since the same lightness has a
;; different contrast at each hue. The default is that of WCAG 2.2's 1.4.3
;; https://github.com/adobe/leonardo
(defn at-ratio
  "The colour `x` at the OKLCH lightness where its contrast ratio with the
  `background` is `r` or just over, or nil where no lightness reaches `r`.

  By default `r` is 4.5, which WCAG 2.2 asks of text. The colour keeps its hue
  and as much chroma as sRGB allows. Where a darker and a lighter colour would
  both do, it's the one nearer the lightness of `x`. A translucent
  `background` lies on white, as in ratio."
  ([x background]
   (at-ratio x background 4.5))
  ([x background r]
   (let [ground (composite/over background white)]
     (when (and (cc/colour x) ground)
       (let [l-bg      (or (get-in (cc/to ground :oklch) [:coords 0]) 0.0)
             l-x       (or (get-in (cc/to x :oklch) [:coords 0]) 0.0)
             lightness (->> [(search x ground r l-bg 0.0) (search x ground r l-bg 1.0)]
                            (remove nil?)
                            (sort-by #(abs (- % l-x)))
                            first)]
         (some->> lightness (at-lightness x)))))))

(defn at-ratios
  "The colour `x` at each contrast ratio of `rs` with the `background`, as
  at-ratio finds it. By default the ratios are [1.5 3 4.5 7], a ramp from a
  decoration to AAA's text."
  ([x background]
   (at-ratios x background [1.5 3 4.5 7]))
  ([x background rs]
   (when (and (cc/colour x) (cc/colour background))
     (mapv #(at-ratio x background %) rs))))
