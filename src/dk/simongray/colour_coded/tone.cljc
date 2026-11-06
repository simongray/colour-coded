(ns dk.simongray.colour-coded.tone
  "Colours from one seed, each at a fixed lightness, so that their contrast
  holds whatever the seed's hue.

  A token is a map of these, e.g. {:l 0.33 :c 0.1 :turn 180}, which CSS
  writes as oklch(from var(--seed) 33% min(c, 0.1) calc(h + 180)):

  - :space, its polar space, by default :oklch
  - :l, its lightness in that space
  - :shade, in place of :l, one of the 16 greys of an e-paper panel, which
    epaper describes
  - :c, the most chroma that it takes from the seed
  - :turn, the degrees that its hue turns from the seed's

  In :lch, the lightness is CIELAB's L*, like the tones of Material 3. L*
  follows WCAG's luminance, but for CIELAB's adaptation to the white of
  D50, so a pair of tokens keeps its contrast at every hue, to within a few
  percent for vivid colours and less for near-greys. In :oklch, the hues
  look more even, but the contrast varies more with the hue. The function
  worst-contrast says how far it goes.
  https://github.com/material-foundation/material-color-utilities/blob/main/typescript/hct/hct.ts"
  (:require [dk.simongray.colour-coded :as cc]
            [dk.simongray.colour-coded.contrast :as contrast]
            [dk.simongray.colour-coded.epaper :as epaper]
            [dk.simongray.colour-coded.space :as space]
            [dk.simongray.colour-coded.syntax :as syntax]
            [dk.simongray.colour-coded.gamut :as gamut]))

(defn lightness
  "The lightness of the `token` in its space: its :l, or that of the grey of
  its :shade. It's nil where the token has neither, there's no such shade, or
  its space has no chroma. The other functions here return nil for such a token
  too."
  [{:keys [space l shade] :or {space :oklch} :as token}]
  (when (space/coord-index space :c)
    (if shade
      (some-> (epaper/shade->grey shade) (cc/to space) :coords first)
      l)))

(defn unmapped
  "The colour that the `token` takes from the `seed`, in the token's space,
  which may lie outside sRGB."
  [{:keys [space c turn] :or {space :oklch turn 0} :as token} seed]
  (when-let [l (lightness token)]
    (when-let [{[_ chroma hue] :coords} (cc/to seed space)]
      {:space  space
       :coords [l
                (min (or chroma 0.0) (or c ##Inf))
                (mod (+ (or hue 0.0) turn) 360)]
       :alpha  1.0})))

(defn tone
  "The colour that the `token` takes from the `seed`, in the token's space,
  with its chroma lowered until it lies in sRGB, so that its lightness and
  hue stay those of the token. With a :shade, it's the colour on that shade
  by epaper/at-shade instead, in sRGB."
  [{:keys [shade] :as token} seed]
  (when-let [colour (unmapped token seed)]
    (if shade
      (epaper/at-shade colour shade)
      (gamut/reduce-chroma colour (:space token :oklch)))))

(defn tones
  "The colours that the map of `tokens` takes from the `seed`, by the same
  keys."
  [tokens seed]
  (when (cc/colour seed)
    (update-vals tokens #(tone % seed))))

(defn relative-css
  "The `token` as a relative colour of CSS from the custom property `prop`,
  e.g. oklch(from var(--seed) 33% min(c, 0.1) calc(h + 180)). A browser
  clips it where it lies outside the display's gamut. A token with a :shade
  takes the lightness of that shade's grey, but its colour usually lands a shade
  or two away, since only epaper/at-shade searches for a colour that lands
  there."
  [{:keys [space c turn] :or {space :oklch turn 0} :as token} prop]
  (when-let [l (lightness token)]
    (let [n syntax/format-number]
      (str (name space) "(from var(" prop ") "
           (if (= space :oklch) (str (n (* 100 l)) "%") (n l)) " "
           (if c (str "min(c, " (n c) ")") "c") " "
           (if (zero? turn) "h" (str "calc(h + " (n turn) ")"))
           ")"))))

(defn worst-contrast
  "The least contrast ratio of the tokens `a` and `b` over seeds of every
  hue, from grey to vivid, as a map of these:

  - :ratio
  - :hue and :chroma, the seed's in OKLCH
  - :gamut, :map where tone maps the tokens into sRGB, or :clip where a
    browser clips them"
  [a b]
  (when (and (lightness a) (lightness b))
    (->> (for [h      (range 360)
               chroma (map #(* 0.04 %) (range 11))
               :let   [seed {:space :oklch :coords [0.5 chroma h] :alpha 1.0}]
               [g f]  [[:map tone] [:clip #(gamut/clip (unmapped %1 %2))]]]
           {:ratio  (contrast/ratio (f a seed) (f b seed))
            :hue    h
            :chroma chroma
            :gamut  g})
         (apply min-key :ratio))))

(comment
  (def bar {:l 0.33 :c 0.1 :turn 180})
  (def on-bar {:l 0.97 :c 0.012 :turn 180})
  (cc/hex (tone bar "#a90000"))
  (relative-css bar "--seed")
  (worst-contrast on-bar bar)
  #_.)
