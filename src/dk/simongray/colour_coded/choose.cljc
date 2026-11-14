(ns dk.simongray.colour-coded.choose
  "Colours chosen from among others, e.g. the colours of an image, those that
  a designer made or the named colours of CSS, and a small scheme for a UI
  chosen from them."
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

(defn- hue-between
  "The degrees between the hues `a` and `b`, the shorter way round."
  [a b]
  (let [d (mod (abs (- a b)) 360)]
    (min d (- 360 d))))

(defn- with-hues
  "The entries of the `palette` whose colour has a hue, each with its
  :chroma and its :hue in OKLCH."
  [palette]
  (for [{:keys [colour share] :as entry} palette
        :when (and (number? share) (chromatic? colour))
        :let  [[_ c h] (:coords (cc/to colour :oklch))]]
    (assoc entry :chroma c :hue h)))

(defn- share-near
  "The share of an image that the `entries` within 15° of the `hue` cover."
  [entries hue]
  (transduce (comp (filter #(<= (hue-between hue (:hue %)) 15))
                   (map :share))
             +
             entries))

;; Material ranks the colours of a wallpaper by how much of it lies within
;; 15° of the hue of each and by its chroma, and leaves out near-greys and
;; hues on 1% of it or less. Its hue and chroma are HCT's, and these are
;; OKLCH's, so the weights are a judgement: 0.6 for the share, and 0.4 for
;; the chroma, which counts in full from 0.2
;; https://github.com/material-foundation/material-color-utilities/blob/main/typescript/score/score.ts
(defn ranked
  "The colours of the `palette` that have a hue, the best to derive the
  colours of a UI from first. The palette is maps of a :colour and its
  :share of an image, as quantise and image/palette return them.

  A colour ranks by the share of the image near its hue and by its chroma,
  so a vivid detail can come before the dull ground that seed returns. Greys
  are left out, and so is a hue on 1% of the image or less.

      (first (ranked (quantise/k-means pixels 8)))"
  [palette]
  (let [entries (with-hues palette)]
    (->> entries
         (map #(assoc % :near (share-near entries (:hue %))))
         (filter #(< 0.01 (:near %)))
         (sort-by #(- (+ (* 0.6 (:near %))
                         (* 0.4 (min 1.0 (/ (:chroma %) 0.2))))))
         (mapv :colour))))

;; WCAG 2.2, success criterion 1.4.3
(defn first-with-contrast
  "The first colour of `xs` whose contrast ratio with the `background` is at
  least `r`, or nil. By default `r` is 4.5, which WCAG 2.2 asks of text. It
  asks 3 of a control. A translucent `background` lies on white, as in
  contrast/ratio."
  ([xs background]
   (first-with-contrast xs background 4.5))
  ([xs background r]
   (first (filter #(when-let [own (contrast/ratio % background)] (>= own r)) xs))))

(defn most-contrast
  "The colour of `xs` that contrasts most with the `background`. A translucent
  `background` lies on white, as in contrast/ratio."
  [xs background]
  (let [colours (filter #(contrast/ratio % background) xs)]
    (when (seq colours)
      (apply max-key #(contrast/ratio % background) colours))))

;; CSS Color 4, section 6.1, says itself that its names are spread unevenly
;; over sRGB and aren't consistent, so a name is a rough label. It has two
;; names for some colours, e.g. gray and grey. min-key returns the last of
;; equals, so sorting the names makes it the later one in the alphabet
;; https://www.w3.org/TR/css-color-4/#named-colors
(defn nearest
  "The colour nearest to the colour `x` among the colours `xs`, by ΔEOK or by
  the measure `f`, a function of two colours, e.g. difference/delta-e-2000.

  Where `xs` is a map of names to colours, the result is the name of the
  nearest one. By default `xs` is the named colours of CSS, so one argument
  returns the name nearest to a colour, e.g. \"darkslategrey\". No measure is
  known to agree best with the names that people give, so ΔEOK is a judgement
  there."
  ([x]
   (nearest x cc/named-colours))
  ([x xs]
   (nearest x xs difference/delta-e))
  ([x xs f]
   (let [pairs (if (map? xs)
                 (sort-by (comp str key) xs)
                 (map (juxt identity identity) xs))]
     (when (cc/colour x)
       (some->> (seq (filter (comp cc/colour second) pairs))
                (apply min-key #(f x (second %)))
                first)))))

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
