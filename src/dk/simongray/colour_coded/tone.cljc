(ns dk.simongray.colour-coded.tone
  "Colours from one seed, each at a fixed lightness, so that their contrast
  holds whatever the seed's hue.

  A token is a map of these, e.g. {:l 0.33 :c 0.1 :turn 180}, which CSS
  writes as oklch(from var(--seed) 33% min(c, 0.1) calc(h + 180)):

  - :space, its polar space, by default :oklch
  - :l, its lightness in that space. A pair of the least and the most, e.g.
    [0.22 0.34], keeps the seed's own lightness between the two, which CSS
    writes as clamp(0.22, l, 0.34). The contrast of such a token holds as far
    as it does at both ends.
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

(defn- lightness-range?
  "Whether `l` is a pair of the least and the most lightness, in that order."
  [l]
  (and (sequential? l) (= 2 (count l)) (every? number? l) (apply <= l)))

(defn lightness
  "The lightness of the `token` in its space: its :l, a number or a pair of
  the least and the most, or that of the grey of its :shade.

  It's nil where the token has none of those, there's no such shade, or its
  space has no chroma. The other functions here return nil for such a token
  too."
  [{:keys [space l shade] :or {space :oklch} :as token}]
  (when (space/coord-index space :c)
    (if shade
      (some-> (epaper/shade->grey shade) (cc/to space) :coords first)
      (when (or (number? l) (lightness-range? l))
        l))))

(defn- kept-lightness
  "The lightness that a token of the lightness `l` takes from a seed whose
  lightness is `own`: `l` itself, or `own` kept within the range that `l` is."
  [l own]
  (if (number? l)
    l
    (min (second l) (max (first l) own))))

(defn unmapped
  "The colour that the `token` takes from the `seed`, in the token's space,
  which may lie outside sRGB."
  [seed {:keys [space c turn] :or {space :oklch turn 0} :as token}]
  (when-let [l (lightness token)]
    (when-let [{[own chroma hue] :coords} (cc/to seed space)]
      {:space  space
       :coords [(kept-lightness l (or own 0.0))
                (min (or chroma 0.0) (or c ##Inf))
                (mod (+ (or hue 0.0) turn) 360)]
       :alpha  1.0})))

(defn tone
  "The colour that the `token` takes from the `seed`, in the token's space,
  with its chroma lowered until it lies in sRGB, so that its lightness and
  hue stay those of the token. With a :shade, it's the colour on that shade
  by epaper/at-shade instead, in sRGB."
  [seed {:keys [shade] :as token}]
  (when-let [colour (unmapped seed token)]
    (if shade
      (epaper/at-shade colour shade)
      (gamut/reduce-chroma colour (:space token :oklch)))))

(defn tones
  "The colours that the map of `tokens` takes from the `seed`, by the same
  keys."
  [seed tokens]
  (when (cc/colour seed)
    (update-vals tokens #(tone seed %))))

(defn- css-lightness
  "The lightness `l` of a token in the `space` as a relative colour of CSS
  has it: a percentage in OKLCH, and clamp() of the seed's own for a range."
  [l space]
  (let [n syntax/format-number]
    (cond
      (not (number? l)) (str "clamp(" (n (first l)) ", l, " (n (second l)) ")")
      (= space :oklch)  (str (n (* 100 l)) "%")
      :else             (n l))))

(defn relative-css
  "The `token` as a relative colour of CSS from the custom property `prop`,
  e.g. oklch(from var(--seed) 33% min(c, 0.1) calc(h + 180)).

  A browser clips it where it lies outside the display's gamut. A token with a
  :shade takes the lightness of that shade's grey, but its colour usually lands
  a shade or two away, since only epaper/at-shade searches for a colour that
  lands there."
  [prop {:keys [space c turn] :or {space :oklch turn 0} :as token}]
  (when-let [l (lightness token)]
    (let [n syntax/format-number]
      (str (name space) "(from var(" prop ") "
           (css-lightness l space) " "
           (if c (str "min(c, " (n c) ")") "c") " "
           (if (zero? turn) "h" (str "calc(h + " (n turn) ")"))
           ")"))))

(defn- seed-lightnesses
  "The lightnesses of the seeds that worst-contrast tries for the tokens `a`
  and `b`: every tenth where either keeps the seed's own, and otherwise one,
  since neither takes it."
  [a b]
  (if (every? (comp number? lightness) [a b])
    [0.5]
    (map #(* 0.1 %) (range 11))))

(defn worst-contrast
  "The least contrast ratio of the tokens `a` and `b` over seeds of every
  hue, from grey to vivid, as a map of these:

  - :ratio
  - :hue, :chroma and :lightness, the seed's in OKLCH
  - :gamut, :map where tone maps the tokens into sRGB, or :clip where a
    browser clips them

  Where a token's lightness is a range, the seeds run from black to white
  too."
  [a b]
  (when (and (lightness a) (lightness b))
    (->> (for [h      (range 360)
               chroma (map #(* 0.04 %) (range 11))
               l      (seed-lightnesses a b)
               :let   [seed {:space :oklch :coords [l chroma h] :alpha 1.0}]
               [g f]  [[:map tone] [:clip #(gamut/clip (unmapped %1 %2))]]]
           {:ratio     (contrast/ratio (f seed a) (f seed b))
            :hue       h
            :chroma    chroma
            :lightness l
            :gamut     g})
         (apply min-key :ratio))))

(comment
  (def bar {:l 0.33 :c 0.1 :turn 180})
  (def on-bar {:l 0.97 :c 0.012 :turn 180})
  (cc/hex (tone "#a90000" bar))
  (relative-css "--seed" bar)
  (worst-contrast on-bar bar)

  ;; a bar that's darker under a dark seed
  (def band {:l [0.22 0.34] :c 0.1 :turn 180})
  (relative-css "--seed" band)
  (worst-contrast on-bar band)
  #_.)
