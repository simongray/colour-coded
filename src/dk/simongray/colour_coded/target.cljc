(ns dk.simongray.colour-coded.target
  "Colours found by the contrast that they should have, by the method of
  Adobe's Leonardo: a search for the lightness at which a colour has the
  contrast asked for with a background, since the same lightness has a
  different contrast at each hue.
  https://github.com/adobe/leonardo"
  (:require [dk.simongray.colour-coded :as cc]
            [dk.simongray.colour-coded.contrast :as contrast]
            [dk.simongray.colour-coded.gamut :as gamut]))

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
  (when (>= (contrast/ratio (at-lightness x to) background) r)
    (loop [near from far to n 0]
      (let [mid (/ (+ near far) 2)]
        (cond
          (>= n 30)                                          far
          (>= (contrast/ratio (at-lightness x mid) background) r) (recur near mid (inc n))
          :else                                              (recur mid far (inc n)))))))

(defn at-contrast
  "The colour `x` at the OKLCH lightness where its contrast ratio with the
  `background` is `r`, or just over, keeping its hue and as much chroma as
  sRGB allows, or nil where no lightness reaches `r`. Of a darker and a
  lighter answer, it's the one whose lightness is nearer to that of `x`."
  [x background r]
  (let [l-bg      (or (get-in (cc/to background :oklch) [:coords 0]) 0.0)
        l-x       (or (get-in (cc/to x :oklch) [:coords 0]) 0.0)
        lightness (->> [(search x background r l-bg 0.0) (search x background r l-bg 1.0)]
                       (remove nil?)
                       (sort-by #(abs (- % l-x)))
                       first)]
    (some->> lightness (at-lightness x))))

(defn scale
  "The colour `x` at each contrast ratio of `rs` with the `background`, as
  at-contrast finds it, e.g. a ramp of a brand colour from [1.5 3 4.5 7]."
  [x background rs]
  (mapv #(at-contrast x background %) rs))
