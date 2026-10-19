(ns dk.simongray.colour-coded.space
  "The colour spaces of CSS Color 4 as data, and the conversion of
  coordinates between them.

  A space is a map of its :id, its :coords, its :base, and the functions
  :to-base and :from-base, which convert a vector of coordinates to and
  from those of the base. Each space's chain of bases ends at xyz-d65, the
  only space without a base. A conversion goes up one space's chain to the
  first space that the other's chain shares, then down the other's.

  Each coordinate is a map of these:

  - :id
  - :range, which a percentage of CSS is a share of
  - :category, the coordinates of other spaces it's analogous to, which
    interpolation uses
  - :angle?, true for a hue

  The matrices and transfer functions are those of CSS Color 4's sample
  code, section 19, under the W3C Software and Document License:
  https://www.w3.org/TR/css-color-4/#color-conversion-code")

(defn multiply
  "The 3×3 matrix `m` times the vector `v`."
  [m v]
  (mapv (fn [[a b c]]
          (+ (* a (v 0)) (* b (v 1)) (* c (v 2))))
        m))

(defn signed
  "The function `f` of the absolute value of `x`, with the sign of `x`, so
  that a transfer function extends below 0."
  [f x]
  (if (neg? x)
    (- (f (- x)))
    (f x)))

;; CSS Color 4, section 10.2
(defn srgb->linear
  "The channel `x` of sRGB as linear light."
  [x]
  (signed #(if (<= % 0.04045)
             (/ % 12.92)
             (Math/pow (/ (+ % 0.055) 1.055) 2.4))
          x))

(defn linear->srgb
  "The channel `x` of linear light as sRGB."
  [x]
  (signed #(if (> % 0.0031308)
             (- (* 1.055 (Math/pow % (/ 1 2.4))) 0.055)
             (* 12.92 %))
          x))

;; CSS Color 4, section 10.5
(defn prophoto->linear
  "The channel `x` of ProPhoto RGB as linear light."
  [x]
  (signed #(if (<= % (/ 16 512.0))
             (/ % 16)
             (Math/pow % 1.8))
          x))

(defn linear->prophoto
  "The channel `x` of linear light as ProPhoto RGB."
  [x]
  (signed #(if (>= % (/ 1 512.0))
             (Math/pow % (/ 1 1.8))
             (* 16 %))
          x))

(defn power
  "A transfer function that raises a channel to the power `p` and keeps its
  sign, for a98-rgb and rec2020."
  [p]
  (fn [x]
    (signed #(Math/pow % p) x)))

(def d50
  "The white point D50 in XYZ, with the values of CSS Color 4."
  [(/ 0.3457 0.3585) 1.0 (/ (- 1.0 0.3457 0.3585) 0.3585)])

(defn rgb-space
  "The space `id` of red, green and blue from 0 to 1, over the space `base`,
  which `to` and `from` convert to and from: transfer functions of a
  channel, or matrices."
  [id base to from]
  (let [convert (fn [f] (if (vector? f) #(multiply f %) #(mapv f %)))]
    {:id        id
     :base      base
     :to-base   (convert to)
     :from-base (convert from)
     :bounded?  true
     :coords    [{:id :r :range [0 1] :category :reds}
                 {:id :g :range [0 1] :category :greens}
                 {:id :b :range [0 1] :category :blues}]}))

(defn xyz-space
  "The CIE XYZ space `id`, which the matrices `to` and `from` convert to and
  from XYZ D65, both nil for xyz-d65 itself."
  [id to from]
  {:id        id
   :base      (when to :xyz-d65)
   :to-base   (when to #(multiply to %))
   :from-base (when from #(multiply from %))
   :coords    [{:id :x :range [0 1] :category :reds}
               {:id :y :range [0 1] :category :greens}
               {:id :z :range [0 1] :category :blues}]})

(defn hue
  "The hue in degrees of the opponent coordinates `a` and `b`, from 0 up to
  360."
  [a b]
  (mod (/ (* 180 (Math/atan2 b a)) Math/PI) 360))

(defn polar
  "The space `id` of lightness, chroma and hue over the space `base`, with
  the lightness and chroma ranging over `l-range` and `c-range`.

  At a chroma of `epsilon` or less, CSS Color 4 makes the hue powerless, so
  missing, and the chroma 0."
  [id base l-range c-range epsilon]
  {:id        id
   :base      base
   :to-base   (fn [[l c h]]
                (let [r (/ (* (or h 0) Math/PI) 180)]
                  [l (* c (Math/cos r)) (* c (Math/sin r))]))
   :from-base (fn [[l a b]]
                (let [c (Math/sqrt (+ (* a a) (* b b)))]
                  (if (> c epsilon)
                    [l c (hue a b)]
                    [l 0.0 nil])))
   :coords    [{:id :l :range l-range :category :lightness}
               {:id :c :range c-range :category :colourfulness}
               {:id :h :range [0 360] :category :hue :angle? true}]})

;; CSS Color 4, section 9.1: CIE Lab, relative to D50
(defn xyz->lab
  "The XYZ D50 `xyz` as CIE Lab."
  [xyz]
  (let [e (/ 216 24389.0)
        k (/ 24389 27.0)
        [fx fy fz] (map (fn [v w]
                          (let [v (/ v w)]
                            (if (> v e) (Math/cbrt v) (/ (+ (* k v) 16) 116))))
                        xyz d50)]
    [(- (* 116 fy) 16) (* 500 (- fx fy)) (* 200 (- fy fz))]))

(defn lab->xyz
  "The CIE Lab `lab` as XYZ D50."
  [[l a b]]
  (let [e  (/ 216 24389.0)
        k  (/ 24389 27.0)
        fy (/ (+ l 16) 116)
        fx (+ (/ a 500) fy)
        fz (- fy (/ b 200))
        cube #(* % % %)]
    (mapv * [(if (> (cube fx) e) (cube fx) (/ (- (* 116 fx) 16) k))
             (if (> l (* k e)) (cube (/ (+ l 16) 116)) (/ l k))
             (if (> (cube fz) e) (cube fz) (/ (- (* 116 fz) 16) k))]
          d50)))

;; CSS Color 4, section 9.2: Oklab, by way of LMS, with the matrices that
;; the spec recalculated for 64-bit precision
(def xyz->lms
  "The matrix from XYZ D65 to the LMS of Oklab."
  [[0.8190224379967030 0.3619062600528904 -0.1288737815209879]
   [0.0329836539323885 0.9292868615863434 0.0361446663506424]
   [0.0481771893596242 0.2642395317527308 0.6335478284694309]])

(def lms->oklab
  "The matrix from the cube roots of LMS to Oklab."
  [[0.2104542683093140 0.7936177747023054 -0.0040720430116193]
   [1.9779985324311684 -2.4285922420485799 0.4505937096174110]
   [0.0259040424655478 0.7827717124575296 -0.8086757549230774]])

(def lms->xyz
  "The matrix from the LMS of Oklab to XYZ D65."
  [[1.2268798758459243 -0.5578149944602171 0.2813910456659647]
   [-0.0405757452148008 1.1122868032803170 -0.0717110580655164]
   [-0.0763729366746601 -0.4214933324022432 1.5869240198367816]])

(def oklab->lms
  "The matrix from Oklab to the cube roots of LMS."
  [[1.0 0.3963377773761749 0.2158037573099136]
   [1.0 -0.1055613458156586 -0.0638541728258133]
   [1.0 -0.0894841775298119 -1.2914855480194092]])

;; CSS Color 4, section 7.1
(defn hsl->srgb
  "The HSL `hsl` as sRGB."
  [[h s l]]
  (let [h (or h 0)
        s (/ s 100)
        l (/ l 100)
        f (fn [n]
            (let [k (mod (+ n (/ h 30)) 12)
                  a (* s (min l (- 1 l)))]
              (- l (* a (max -1 (min (- k 3) (- 9 k) 1))))))]
    [(f 0) (f 8) (f 4)]))

(defn rgb-hue
  "The hue in degrees that HSL and HWB give the sRGB `rgb`, or nil for a
  grey, including one that the rounding of a conversion leaves a hair away
  from grey."
  [[r g b]]
  (let [hi (max r g b)
        lo (min r g b)
        d  (- hi lo)]
    (when (>= d 1e-10)
      (mod (* 60 (cond
                   (== hi r) (/ (- g b) d)
                   (== hi g) (+ (/ (- b r) d) 2)
                   :else     (+ (/ (- r g) d) 4)))
           360))))

;; CSS Color 4, section 7.2, with the fix of csswg issue 9222 for a colour
;; whose saturation would be negative
(defn srgb->hsl
  "The sRGB `rgb` as HSL, with no hue and no saturation for a grey."
  [rgb]
  (let [hi (apply max rgb)
        lo (apply min rgb)
        l  (/ (+ hi lo) 2)
        h  (rgb-hue rgb)
        s  (if (or (nil? h) (zero? l) (== l 1))
             0
             (/ (- hi l) (min l (- 1 l))))
        [h s] (if (neg? s) [(some-> h (+ 180) (mod 360)) (- s)] [h s])]
    (if (and h (> (* 100 s) 0.001))
      [h (* 100 s) (* 100 l)]
      [nil 0.0 (* 100 l)])))

;; CSS Color 4, section 8
(defn hwb->srgb
  "The HWB `hwb` as sRGB."
  [[h w b]]
  (let [w (/ w 100)
        b (/ b 100)]
    (if (>= (+ w b) 1)
      (let [grey (/ w (+ w b))]
        [grey grey grey])
      (mapv #(+ w (* % (- 1 w b))) (hsl->srgb [h 100 50])))))

;; Unlike HSL's, the hue isn't turned for a colour far outside sRGB:
;; browsers and Colour.js don't turn it, and the web-platform-tests expect
;; it not to be
(defn srgb->hwb
  "The sRGB `rgb` as HWB, with no hue for a grey."
  [rgb]
  (let [w (* 100 (apply min rgb))
        b (* 100 (- 1 (apply max rgb)))
        h (rgb-hue rgb)]
    (if (and h (< (+ w b) 99.999))
      [h w b]
      [nil w (if (< (+ w b) 100) (- 100 w) b)])))

(def spaces
  "The spaces of CSS Color 4 by their ids, and the linear forms of a98-rgb,
  prophoto-rgb and rec2020, which CSS doesn't name."
  (into {}
        (map (juxt :id identity))
        [(xyz-space :xyz-d65 nil nil)
         (xyz-space :xyz-d50
                    [[0.955473421488075 -0.02309845494876471 0.06325924320057072]
                     [-0.0283697093338637 1.0099953980813041 0.021041441191917323]
                     [0.012314014864481998 -0.020507649298898964 1.330365926242124]]
                    [[1.0479297925449969 0.022946870601609652 -0.05019226628920524]
                     [0.02962780877005599 0.9904344267538799 -0.017073799063418826]
                     [-0.009243040646204504 0.015055191490298152 0.7518742814281371]])
         (rgb-space :srgb-linear :xyz-d65
                    [[(/ 506752 1228815.0) (/ 87881 245763.0) (/ 12673 70218.0)]
                     [(/ 87098 409605.0) (/ 175762 245763.0) (/ 12673 175545.0)]
                     [(/ 7918 409605.0) (/ 87881 737289.0) (/ 1001167 1053270.0)]]
                    [[(/ 12831 3959.0) (/ -329 214.0) (/ -1974 3959.0)]
                     [(/ -851781 878810.0) (/ 1648619 878810.0) (/ 36519 878810.0)]
                     [(/ 705 12673.0) (/ -2585 12673.0) (/ 705 667.0)]])
         (rgb-space :srgb :srgb-linear srgb->linear linear->srgb)
         (rgb-space :display-p3-linear :xyz-d65
                    [[(/ 608311 1250200.0) (/ 189793 714400.0) (/ 198249 1000160.0)]
                     [(/ 35783 156275.0) (/ 247089 357200.0) (/ 198249 2500400.0)]
                     [0.0 (/ 32229 714400.0) (/ 5220557 5000800.0)]]
                    [[(/ 446124 178915.0) (/ -333277 357830.0) (/ -72051 178915.0)]
                     [(/ -14852 17905.0) (/ 63121 35810.0) (/ 423 17905.0)]
                     [(/ 11844 330415.0) (/ -50337 660830.0) (/ 316169 330415.0)]])
         (rgb-space :display-p3 :display-p3-linear srgb->linear linear->srgb)
         (rgb-space :a98-rgb-linear :xyz-d65
                    [[(/ 573536 994567.0) (/ 263643 1420810.0) (/ 187206 994567.0)]
                     [(/ 591459 1989134.0) (/ 6239551 9945670.0) (/ 374412 4972835.0)]
                     [(/ 53769 1989134.0) (/ 351524 4972835.0) (/ 4929758 4972835.0)]]
                    [[(/ 1829569 896150.0) (/ -506331 896150.0) (/ -308931 896150.0)]
                     [(/ -851781 878810.0) (/ 1648619 878810.0) (/ 36519 878810.0)]
                     [(/ 16779 1248040.0) (/ -147721 1248040.0) (/ 1266979 1248040.0)]])
         (rgb-space :a98-rgb :a98-rgb-linear (power (/ 563 256.0)) (power (/ 256 563.0)))
         (rgb-space :prophoto-rgb-linear :xyz-d50
                    [[0.79776664490064230 0.13518129740053308 0.03134773412839220]
                     [0.28807482881940130 0.71183523424187300 0.00008993693872564]
                     [0.0 0.0 0.82510460251046020]]
                    [[1.34578688164715830 -0.25557208737979464 -0.05110186497554526]
                     [-0.54463070512490190 1.50824774284514680 0.02052744743642139]
                     [0.0 0.0 1.21196754563894520]])
         (rgb-space :prophoto-rgb :prophoto-rgb-linear prophoto->linear linear->prophoto)
         (rgb-space :rec2020-linear :xyz-d65
                    [[(/ 63426534 99577255.0) (/ 20160776 139408157.0) (/ 47086771 278816314.0)]
                     [(/ 26158966 99577255.0) (/ 472592308 697040785.0) (/ 8267143 139408157.0)]
                     [0.0 (/ 19567812 697040785.0) (/ 295819943 278816314.0)]]
                    [[(/ 30757411 17917100.0) (/ -6372589 17917100.0) (/ -4539589 17917100.0)]
                     [(/ -19765991 29648200.0) (/ 47925759 29648200.0) (/ 467509 29648200.0)]
                     [(/ 792561 44930125.0) (/ -1921689 44930125.0) (/ 42328811 44930125.0)]])
         ;; BT.1886's transfer function, which CSS Color 4 has used since
         ;; csswg issue 12574
         (rgb-space :rec2020 :rec2020-linear (power 2.4) (power (/ 1 2.4)))
         {:id        :lab
          :base      :xyz-d50
          :to-base   lab->xyz
          :from-base xyz->lab
          :coords    [{:id :l :range [0 100] :category :lightness}
                      {:id :a :range [-125 125] :category :opponent-a}
                      {:id :b :range [-125 125] :category :opponent-b}]}
         (polar :lch :lab [0 100] [0 150] 0.0015)
         {:id        :oklab
          :base      :xyz-d65
          :to-base   #(multiply lms->xyz (mapv (fn [x] (* x x x))
                                               (multiply oklab->lms %)))
          :from-base #(multiply lms->oklab (mapv (fn [x] (Math/cbrt x))
                                                 (multiply xyz->lms %)))
          :coords    [{:id :l :range [0 1] :category :lightness}
                      {:id :a :range [-0.4 0.4] :category :opponent-a}
                      {:id :b :range [-0.4 0.4] :category :opponent-b}]}
         (polar :oklch :oklab [0 1] [0 0.4] 0.000004)
         {:id        :hsl
          :base      :srgb
          :to-base   hsl->srgb
          :from-base srgb->hsl
          :gamut     :srgb
          :coords    [{:id :h :range [0 360] :category :hue :angle? true}
                      {:id :s :range [0 100] :category :colourfulness}
                      {:id :l :range [0 100] :category :lightness}]}
         {:id        :hwb
          :base      :srgb
          :to-base   hwb->srgb
          :from-base srgb->hwb
          :gamut     :srgb
          :coords    [{:id :h :range [0 360] :category :hue :angle? true}
                      {:id :w :range [0 100]}
                      {:id :b :range [0 100]}]}]))

(defn space
  "The space whose id is `k`, or `k` itself when it's a space, or nil."
  [k]
  (if (map? k) k (get spaces k)))

(defn lineage
  "The ids of the space `k` and of its bases, the space itself first."
  [k]
  (->> (space k)
       (iterate #(some-> % :base space))
       (take-while some?)
       (map :id)))

(defn- convert-between
  "The coordinates `coords` of the space `from` in the different space `to`."
  [coords from to]
  (let [up     (lineage from)
        down   (lineage to)
        shared (first (filter (set down) up))
        coords (mapv #(or % 0) coords)
        coords (reduce #((:to-base (space %2)) %1)
                       coords
                       (take-while #(not= % shared) up))]
    (reduce #((:from-base (space %2)) (mapv (fn [x] (or x 0)) %1))
            coords
            (reverse (take-while #(not= % shared) down)))))

(defn convert
  "The coordinates `coords` of the space `from` in the space `to`. A missing
  coordinate, nil, counts as 0, like none in CSS. A conversion to the
  same space returns `coords` as they are."
  [coords from to]
  (if (= (:id (space from)) (:id (space to)))
    coords
    (convert-between coords from to)))

(defn coord-index
  "The index of the coordinate `id` of the space `k`, or nil."
  [k id]
  (first (keep-indexed #(when (= id (:id %2)) %1) (:coords (space k)))))

(defn hue-index
  "The index of the hue of the space `k`, or nil for a space without one."
  [k]
  (first (keep-indexed #(when (:angle? %2) %1) (:coords (space k)))))

(defn gamut
  "The id of the space whose gamut bounds the space `k`: its own for a space
  of red, green and blue, sRGB for HSL and HWB, or nil for an unbounded
  space, e.g. Oklab."
  [k]
  (let [s (space k)]
    (cond
      (:bounded? s) (:id s)
      (:gamut s)    (:gamut s))))
