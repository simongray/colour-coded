(ns dk.simongray.colour-coded.describe
  "Colours in words: whether one is dark or light, and the name nearest to it.

  A colour is dark where white text has more contrast with it than black
  text, by WCAG 2.2's ratio, and light otherwise. It's the choice that
  contrast/black-or-white makes between the two. They contrast equally at a
  relative luminance of about 0.179, just under an L* of 50. Taking that for
  the line between dark and light is a judgement: Material prefers white text
  on any tone below 60.

  The names are the named colours of CSS Color 4, unless others are given.
  The specification says itself that they're spread unevenly over sRGB and
  aren't consistent, e.g. darkgray is lighter than gray, so a name is a rough
  label. The nearest one is nearest by ΔEOK, which is a judgement too, since
  no measure is known to agree best with the names that people give.

  https://www.w3.org/TR/WCAG22/#dfn-contrast-ratio
  https://github.com/material-foundation/material-color-utilities/blob/main/typescript/dynamiccolor/dynamic_color.ts
  https://www.w3.org/TR/css-color-4/#named-colors"
  (:refer-clojure :exclude [name])
  (:require [dk.simongray.colour-coded :as cc]
            [dk.simongray.colour-coded.contrast :as contrast]
            [dk.simongray.colour-coded.difference :as difference]
            [dk.simongray.colour-coded.syntax :as syntax]))

(defn dark?
  "Whether the colour `x` is dark, i.e. white text has more contrast with it
  than black text, or as much."
  [x]
  (= contrast/white (contrast/black-or-white x)))

(defn light?
  "Whether the colour `x` is light, i.e. black text has more contrast with it
  than white text."
  [x]
  (= contrast/black (contrast/black-or-white x)))

;; CSS Color 4, section 6.1, has two names for some colours, e.g. gray and
;; grey. min-key returns the last of equals, so sorting the names makes it
;; the later one in the alphabet: grey, cyan and magenta
(defn name
  "The name nearest to the colour `x` among the `names`, a map of each name
  to its colour, by default the named colours of CSS. It's nearest by ΔEOK,
  or by the measure `f`, a function of two colours, e.g.
  difference/delta-e-2000.

  A name of CSS is a colour too, so difference/delta-e of `x` and its name
  says how near the name is."
  ([x]
   (name x syntax/named-colours))
  ([x names]
   (name x names difference/delta-e))
  ([x names f]
   (let [named (filter (comp cc/colour val) names)]
     (when (and (cc/colour x) (seq named))
       (key (apply min-key #(f x (val %)) (sort-by (comp str key) named)))))))

(comment
  (dark? "#24476b")
  (name "#24476b")
  (difference/delta-e "#24476b" (name "#24476b"))
  (name "#24476b" {"navy" "#000080" "sky" "#87ceeb"})
  #_.)
