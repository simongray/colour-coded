(ns dk.simongray.colour-coded.gamut
  "Whether a colour lies in the gamut of a space, and the nearest colour
  that does.

  There are three ways to bring a colour into a gamut:

  - to-gamut, the gamut mapping of CSS Color 4, lowers its chroma in OKLCH
    at the same lightness and hue
  - clip clamps each channel, which shifts the lightness and the hue a
    little, but is what browsers still do (csswg-drafts issue 9449)
  - reduce-chroma lowers its chroma in a polar space of your choice, e.g.
    LCH, and keeps that space's lightness and hue exactly

  https://www.w3.org/TR/css-color-4/#gamut-mapping"
  (:require [dk.simongray.colour-coded :as cc]
            [dk.simongray.colour-coded.difference :as difference]
            [dk.simongray.colour-coded.space :as space]))

(def epsilon
  "How far outside its range a channel may lie and still count as inside
  it. Colour.js allows the same."
  0.000075)

(defn in-gamut?
  "Whether the colour `x` lies in the gamut of the `space`, by default
  sRGB. Every colour lies in the gamut of an unbounded space, e.g. Oklab."
  ([x]
   (in-gamut? x :srgb))
  ([x space]
   (if-let [g (space/gamut space)]
     (every? #(<= (- epsilon) (or % 0) (+ 1 epsilon))
             (:coords (cc/to x g)))
     (some? (cc/colour x)))))

(defn clip
  "The colour `x` in the `space`, by default sRGB, with each channel
  clamped to its range. Browsers show a colour outside the display's gamut
  this way today. HSL and HWB clamp in sRGB, and an unbounded space, e.g.
  Oklab, doesn't clamp."
  ([x]
   (clip x :srgb))
  ([x space]
   (if-let [g (space/gamut space)]
     (-> (cc/to x g)
         (update :coords #(mapv (fn [v] (min 1.0 (max 0.0 (or v 0.0)))) %))
         (cc/to space))
     (cc/to x space))))

(defn- with-chroma
  "The OKLCH colour `c` with the chroma `chroma`."
  [c chroma]
  (assoc-in c [:coords 1] chroma))

;; CSS Color 4, section 14.2.2: binary search gamut mapping with local
;; MINDE
(defn to-gamut
  "The colour `x` in the gamut of the `space`, by default sRGB, at the same
  OKLCH lightness and hue, by the gamut mapping of CSS Color 4: a binary
  search on its chroma, which takes the clipped colour once it's within a
  ΔEOK of 0.02."
  ([x]
   (to-gamut x :srgb))
  ([x space]
   (let [origin (cc/to x :oklch)
         [l c]  (:coords origin)
         jnd    0.02
         eps    0.0001
         clip*  #(clip % space)
         white  (cc/to {:space :srgb :coords [1.0 1.0 1.0] :alpha (:alpha origin)} space)
         black  (cc/to {:space :srgb :coords [0.0 0.0 0.0] :alpha (:alpha origin)} space)]
     (cond
       (nil? origin)                      nil
       (nil? (space/gamut space))         (cc/to x space)
       (>= (or l 0) 1)                    white
       (<= (or l 0) 0)                    black
       (in-gamut? origin space)           (cc/to x space)
       (< (difference/delta-e (clip* origin) origin) jnd) (clip* origin)
       :else
       (loop [lo 0.0 hi (or c 0.0) lo-in-gamut? true clipped (clip* origin)]
         (if (<= (- hi lo) eps)
           clipped
           (let [chroma  (/ (+ lo hi) 2)
                 current (with-chroma origin chroma)]
             (if (and lo-in-gamut? (in-gamut? current space))
               (recur chroma hi lo-in-gamut? clipped)
               (let [clipped (clip* current)
                     e       (difference/delta-e clipped current)]
                 (cond
                   (and (< e jnd) (< (- jnd e) eps)) clipped
                   (< e jnd)                         (recur chroma hi false clipped)
                   :else                             (recur lo chroma lo-in-gamut? clipped)))))))))))

(defn reduce-chroma
  "The colour `x` in the polar `space`, e.g. :lch or :oklch, with its chroma
  lowered until it lies in the gamut of `gamut-space`, by default sRGB,
  keeping its lightness and hue in that space exactly."
  ([x space]
   (reduce-chroma x space :srgb))
  ([x space gamut-space]
   (let [c (cc/to x space)]
     (if (or (nil? c) (in-gamut? c gamut-space))
       c
       (loop [lo 0.0 hi (or (get-in c [:coords 1]) 0.0) n 0]
         (let [mid (/ (+ lo hi) 2)]
           (cond
             (>= n 40)
             (let [reduced (assoc-in c [:coords 1] lo)]
               (if (in-gamut? reduced gamut-space)
                 reduced
                 (cc/to (clip reduced gamut-space) space)))

             (in-gamut? (assoc-in c [:coords 1] mid) gamut-space)
             (recur mid hi (inc n))

             :else
             (recur lo mid (inc n)))))))))
