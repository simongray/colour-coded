(ns dk.simongray.colour-coded.epaper
  "Colours for e-paper, which land on the same grey on any monochrome panel
  and keep a hue on a colour panel.

      (map (comp cc/hex :colour) (palette))  ; four colours, ready to use
      (at-shade \"#0072b2\" 5)                ; one of your own, on a grey

  An E Ink panel shows 16 greys, its shades, so a device turns a colour into a
  grey by a conversion it doesn't document (see greyscale), and then into 4
  bits. Engines floor or round to 8 bits. After that, some devices round to the
  nearest of #000000, #111111 … #ffffff, and others keep the top 4 bits. A
  colour has a shade when it lands on the same grey by every conversion and
  each of those ways of reducing it. Other colours may shift between shades on
  different devices, or dither into a stipple on those that dither.

  By default a colour's shade holds on any e-reader, since these functions ask
  for every conversion. If you know your device's conversion, pass it to allow
  more chroma, e.g. (at-shade x 5 [:rec601]). A shade ignores alpha, so it holds
  only for an opaque colour.

  A grey on its shade's own value, e.g. #555555, is a flat tone wherever a
  device rounds or uses an ordered dither. Rockchip's error diffusion keeps
  the low 4 bits as its error, so no grey but black and white is flat on all
  of them.

  https://www.eink.com/brand/detail/Kaleido3
  https://shopkits.eink.com/en/product/detail/13.3''Kaleido3ePaperDisplay
  https://github.com/TinkerBoard-Android/rockchip-android-hardware-rk35-einkhwc
  https://github.com/koreader/koreader-base/blob/master/ffi/blitbuffer.lua"
  (:require [dk.simongray.colour-coded :as cc]
            [dk.simongray.colour-coded.contrast :as contrast]
            [dk.simongray.colour-coded.difference :as difference]
            [dk.simongray.colour-coded.gamut :as gamut]
            [dk.simongray.colour-coded.greyscale :as greyscale]
            [dk.simongray.colour-coded.space :as space]
            [dk.simongray.colour-coded.vision :as vision]))

;; The MobileRead wiki gives Carta's contrast. E Ink publishes no reflectance,
;; contrast or gamut for Kaleido 3, and no measurement of them turned up
;; https://wiki.mobileread.com/wiki/E_Ink_display
(def panels
  "The numbers that simulate takes for each panel:

  - :contrast, the ratio of its white to its black
  - :saturation, the share of a colour's saturation that it keeps, 0 for a
    monochrome panel

  The numbers for :kaleido-3 are estimates, good for comparing colours and
  no more. A panel is a plain map, so you can pass your own measurements."
  {:carta     {:contrast 15.0 :saturation 0.0}
   :kaleido-3 {:contrast 9.0 :saturation 0.5}})

(defn shade?
  "Whether `n` is a shade: a whole number from 0 for black to 15 for white."
  [n]
  (and (number? n) (<= 0 n 15) (== n (Math/floor n))))

(defn shade->grey
  "The grey of the shade `n`, in sRGB."
  [n]
  (when (shade? n)
    (let [v (/ n 15.0)]
      {:space :srgb :coords [v v v] :alpha 1.0})))

;; An engine's numbers can sit a little either side of these weights, by the 6
;; decimals that a PDF writes or its fixed-point weights, so #555555 can floor
;; to 84 in MuPDF, and #00ff33 at 155.499 can round to 156 in Pillow
(defn- landing
  "The shades that a grey's value `v` can land on. A conversion that
  `truncates?` only keeps the top 4 bits. Any other can floor or round to 8
  bits, and then keep the top 4 bits or round to the nearest shade. Rounding
  both times comes last."
  [v truncates?]
  (let [b       (* v 255.0)
        top     #(min 15 (long (/ % 16.0)))
        nearest #(long (Math/round (/ % 17.0)))]
    (if truncates?
      [(top (+ b 1e-6))]
      (let [low  (max 0 (Math/floor (- b 0.01)))
            high (Math/round (+ b 0.01))]
        [(top low) (top high) (nearest low) (nearest high)]))))

(defn landings
  "A map of each conversion in `ks`, by default all of them, to the shades
  that the colour `x` lands on by it."
  ([x]
   (landings x (keys greyscale/conversions)))
  ([x ks]
   (some->> (greyscale/values x ks)
            (map (fn [[k v]]
                   [k (landing v (:truncates? (greyscale/conversions k)))]))
            (into {}))))

(defn shades
  "The shades that the colour `x` can land on, by each conversion in `ks`, by
  default all of them, and each way of reducing a grey to 4 bits, as a sorted
  set."
  ([x]
   (shades x (keys greyscale/conversions)))
  ([x ks]
   (some->> (landings x ks)
            (vals)
            (into (sorted-set) cat))))

(defn shade
  "The one shade that the colour `x` lands on, by each conversion in `ks`, by
  default all of them, or nil where it can land on more than one, e.g. 5 for
  #355b92 and nil for #0072b2."
  ([x]
   (shade x (keys greyscale/conversions)))
  ([x ks]
   (let [ss (shades x ks)]
     (when (= 1 (count ss))
       (first ss)))))

(defn same-grey?
  "Whether the colours `a` and `b` land alike by each conversion in `ks`, by
  default all of them, and each way of reducing a grey to 4 bits, e.g. to paint
  a grey drawing without changing it on a monochrome panel.

  This asks more than having the same shades. A grey off the 16 even values
  can round to one shade and truncate to the next, e.g. #d4d4d4, and a colour
  has to do the same."
  ([a b]
   (same-grey? a b (keys greyscale/conversions)))
  ([a b ks]
   (let [landed (landings a ks)]
     (boolean (and (seq landed) (= landed (landings b ks)))))))

(defn- bisect
  "The point from 0 to 1 at which the function `f` crosses to true, for an
  `f` that's false below it and true above it."
  [f]
  (let [halve (fn [[lo hi]]
                (let [mid (/ (+ lo hi) 2)]
                  (if (f mid) [lo mid] [mid hi])))]
    (second (nth (iterate halve [0.0 1.0]) 16))))

(defn- window
  "The range of grey values, from 0 to 1, that land on the shade `n` by every
  way of reducing them that landing lists, as its start and its end, which lies
  outside it. A conversion that `truncates?` only keeps the top bits."
  [n truncates?]
  (mapv #(/ % 255)
        (if truncates?
          [(* 16.0 n) (+ (* 16.0 n) 16)]
          [(max (- (* 17.0 n) 8) (* 16.0 n))
           (min (+ (* 17.0 n) 8.5) (+ (* 16.0 n) 15.5))])))

(defn- candidates
  "The colours to try of the chroma `c` and hue `h` on the shade `n` by the
  conversions `ks`, nearest the shade's own grey first, or nil where the
  conversions can't all fit in the shade's window at this chroma."
  [c h n ks]
  (let [colour  (fn [l] {:space :oklch :coords [l c h] :alpha 1.0})
        greys   #(greyscale/values (colour %) ks)
        bounds  #(window n (get-in greyscale/conversions [% :truncates?]))
        inside? (fn [[k v]] (>= v (first (bounds k))))
        past?   (fn [[k v]] (>= v (second (bounds k))))
        centre  #(let [vs (vals (greys %))]
                   (/ (+ (apply max vs) (apply min vs)) 2))
        from    (bisect #(every? inside? (greys %)))
        to      (bisect #(some past? (greys %)))
        home    (bisect #(>= (centre %) (/ n 15.0)))
        nearest (-> home (max from) (min to))]
    (when (< from to)
      (->> (map #(+ from (* % (- to from))) [0 0.25 0.5 0.75 1])
           (cons nearest)
           (sort-by #(abs (- % nearest)))
           (map colour)))))

(defn- to-bytes
  "The colour `x` in sRGB at the 8 bits that a device gets."
  [x]
  (update (cc/to x :srgb) :coords
          (partial mapv #(/ (Math/round (* 255.0 %)) 255.0))))

(defn- landed
  "The colour `x` in 8 bits, where it lies in sRGB and lands on the shade `n`
  by the conversions `ks`, or nil."
  [x n ks]
  (let [rounded (to-bytes x)]
    (when (and (gamut/in-gamut? x) (= n (shade rounded ks)))
      rounded)))

;; This scans the chromas because those that land on a shade have gaps, and
;; halving can end up as much as 0.03 short of the most
(defn at-shade
  "The colour `x` on the shade `n`, in sRGB, keeping its hue in OKLCH and as
  much of its chroma as lets it land there by each conversion in `ks`, by
  default all of them. A colour already on the shade stays as it is.

  For the most chroma of a hue, give it a vivid colour, e.g.
  oklch(50% 0.4 258), though muted colours look better on large areas."
  ([x n]
   (at-shade x n (keys greyscale/conversions)))
  ([x n ks]
   (let [c (cc/to x :oklch)]
     (when (and (shade? n) (seq (greyscale/values c ks)))
       (let [[_ chroma hue] (:coords c)
             n       (long n)
             hue     (or hue 0.0)
             chromas (concat (range (or chroma 0.0) 0 -0.0025) [0.0])
             search  (fn [chroma]
                       (some #(landed % n ks) (candidates chroma hue n ks)))]
         (some-> (or (landed c n ks) (some search chromas))
                 (assoc :alpha (:alpha c))))))))

(defn least-gap
  "The fewest shades between any two colours of `xs`, wherever each lands by
  the conversions in `ks`, by default all of them, or nil for fewer than two.
  At 0, a pair can look the same on a monochrome panel. Each shade is about 5
  of L*, so colours that must be told apart by their grey alone want several
  shades between them."
  ([xs]
   (least-gap xs (keys greyscale/conversions)))
  ([xs ks]
   (let [ss (mapv #(shades % ks) xs)]
     (some->> (for [i (range (count ss))
                    j (range (inc i) (count ss))
                    a (ss i)
                    b (ss j)]
                (abs (- a b)))
              (seq)
              (apply min)))))

(defn- panel-channels
  "The red, green and blue, each on one of 16 even values, that a panel with
  the `saturation` gets from the colour `c` by the `conversion`."
  [c saturation conversion]
  (if (zero? saturation)
    (let [n (peek (get (landings c [conversion]) conversion))]
      (vec (repeat 3 (/ n 15.0))))
    (mapv #(/ (Math/round (* 15.0 %)) 15.0) (:coords (gamut/clip c)))))

;; Monochrome panels are the common case, so Carta is the default, and a colour
;; with a shade looks the same by any conversion
(defn simulate
  "The colour `x` as the `panel`, by default :carta, shows it by the
  `conversion`, by default greyscale/default-conversion, relative to the panel's
  white, in linear sRGB.

  A panel is a key of panels or a map like them. On a monochrome panel, the
  conversion turns the colour into a grey. A colour panel reduces each channel
  to 16 values instead, which is why Kaleido 3 has 4,096 colours. Reflectance
  follows the linear light of sRGB, whose curve is close to the gamma of 2.2
  that SiPix's patent aims for. This is an approximation, for comparing colours.
  https://patents.google.com/patent/US8456414"
  ([x]
   (simulate x :carta))
  ([x panel]
   (simulate x panel greyscale/default-conversion))
  ([x panel conversion]
   (let [c (cc/colour x)
         {:keys [contrast saturation]} (get panels panel panel)]
     (when (and c
                (contains? greyscale/conversions conversion)
                (number? contrast)
                (pos? contrast)
                (number? saturation))
       (let [channels (panel-channels c saturation conversion)
             linear   (mapv space/srgb->linear channels)
             y        (contrast/luminance {:space :srgb-linear :coords linear})
             black    (/ 1.0 contrast)]
         {:space  :srgb-linear
          :coords (mapv #(+ black (* (- 1 black)
                                     (+ y (* saturation (- % y)))))
                        linear)
          :alpha  (:alpha c)})))))

(defn ratio
  "The contrast ratio of the colours `a` and `b` as the `panel`, by default
  :carta, shows them by the `conversion`, by default
  greyscale/default-conversion. It uses WCAG's formula, which allows for a
  screen's flare, so it's on the cautious side: black on white is 9 on a panel
  with a contrast of 15."
  ([a b]
   (ratio a b :carta))
  ([a b panel]
   (ratio a b panel greyscale/default-conversion))
  ([a b panel conversion]
   (contrast/ratio (simulate a panel conversion) (simulate b panel conversion))))

;; The first three are the blue, yellow and red of Paul Tol's high-contrast
;; scheme, which he made for a monochrome printout, but his colours can each
;; land on two shades, so these keep only their hues. Teal sits in the widest
;; gap left between those hues, and does better than green with a red-green
;; deficiency. They run from 3 to 12 because a dark colour that turns black
;; still shows, but a pale one nearly vanishes on white, and more so if it
;; bleaches. That range fits four colours at 3 shades apart.
;; https://sronpersonalpages.nl/~pault/
(def hues
  "The hues that palette takes by default, in the order to use them, so that
  as many as you take from the front stay apart. Each is a map of its :name,
  a :colour, which gives it its hue and chroma, and its :shade."
  [{:name :blue   :colour "oklch(50% 0.4 255)" :shade 3}
   {:name :yellow :colour "oklch(50% 0.4 84)"  :shade 12}
   {:name :red    :colour "oklch(50% 0.4 12)"  :shade 6}
   {:name :teal   :colour "oklch(50% 0.4 195)" :shade 9}])

;; Shade 13 is the darkest that keeps black text at 4.5 on Kaleido 3, and it's
;; fine if it bleaches, since the text still shows
(defn palette
  "The colours of the `hues`, by default those of hues, on e-paper by each
  conversion in `ks`, by default all of them. Each colour is a map of these:

  - :name and :shade, those of the hue
  - :colour, for lines and fills, which needs a black outline or a large area
    on the light shades, where it's pale against white
  - :highlight, the same hue on shade 13, for a background behind black text

  Take as many as you need from the front. By default they stay apart on any
  e-reader, in grey and in colour, and with a colour vision deficiency, which
  report checks for colours of your own. It skips a hue that has no colour on
  its shade."
  ([]
   (palette hues))
  ([hues]
   (palette hues (keys greyscale/conversions)))
  ([hues ks]
   (when (every? greyscale/conversions ks)
     (vec (for [{:keys [colour shade] :as hue} hues
                :let  [on-shade (at-shade colour shade ks)
                       pale     (cc/with colour :oklch {:c #(min % 0.08)})]
                :when on-shade]
            (assoc (select-keys hue [:name :shade])
                   :colour on-shade
                   :highlight (at-shade pale 13 ks)))))))

;; A ΔEOK of 0.1 is clearly apart, as in choose/spread, and 3 shades are about
;; 15 of L*
(defn report
  "Whether the colours `xs` stay apart on e-paper, by each conversion in `ks`,
  by default all of them, as a map for each check:

  - :check, one of :on-a-shade, how many of them land on one grey, :gap, the
    least-gap between them, or their least ΔEOK as each of :normal, :protan,
    :deutan and :tritan vision and :kaleido-3 show them
  - :value, and :wants, the least that it should be
  - :ok?, whether it is

  A non-colour in `xs` lands on no grey, so it fails :on-a-shade."
  ([xs]
   (report xs (keys greyscale/conversions)))
  ([xs ks]
   (when (every? greyscale/conversions ks)
     (let [on-one   (count (filter #(shade % ks) xs))
           on-panel (map #(simulate % :kaleido-3) xs)
           checks   (concat [[:on-a-shade on-one (count xs)]
                             [:gap (least-gap xs ks) 3]]
                            (for [[k d] (vision/least-difference xs)] [k d 0.1])
                            [[:kaleido-3 (difference/least on-panel) 0.1]])]
       (for [[check value wants] checks
             :when               (some? value)]
         {:check check
          :value value
          :wants wants
          :ok?   (>= value wants)})))))

(comment
  (map (comp cc/hex :colour) (palette))

  (remove :ok? (report (map :colour (palette))))
  ;; => ()

  (shades "#0072b2")
  ;; => #{5 6}

  (cc/hex (at-shade "oklch(50% 0.4 258)" 5))
  (cc/hex (at-shade "oklch(50% 0.4 258)" 5 [:rec601]))

  (cc/hex (simulate "#325a94" :kaleido-3))

  (ratio "#555555" "white")
  #_.)
