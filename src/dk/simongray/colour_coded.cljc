(ns dk.simongray.colour-coded
  "Colours as maps, converted between the colour spaces of CSS Color 4.

  A colour is a map of its :space, its :coords and its :alpha, e.g.
  {:space :oklch :coords [0.44 0.085 158.0] :alpha 1.0}, the shape that
  Colour.js uses. A missing component, which CSS writes as none, is nil. Every function of
  the library takes a CSS colour as well, e.g. \"#24476b\" or
  \"oklch(44% 0.085 158)\", which syntax/parse reads.

  The spaces are those of CSS Color 4, by these ids: :srgb, :srgb-linear,
  :display-p3, :display-p3-linear, :a98-rgb, :prophoto-rgb, :rec2020,
  :xyz-d65, :xyz-d50, :lab, :lch, :oklab, :oklch, :hsl and :hwb. Their
  coordinates have the ranges of CSS's sample code: red, green and blue
  from 0 to 1, the saturation and lightness of HSL from 0 to 100, the
  lightness of Lab and LCH from 0 to 100, and of Oklab and OKLCH from 0 to
  1, and hues in degrees.

  The other namespaces build on this one:

  - space, syntax, gamut, contrast, difference and mix, the rest of the
    core
  - choose, harmony, tone and target, ways to choose and generate colours
  - quantise and image, the colours of an image
  - theme, the colours of a site or an app, from one colour or one image"
  (:require [dk.simongray.colour-coded.syntax :as syntax]
            [dk.simongray.colour-coded.space :as space]))

(defn colour?
  "Whether `x` is a colour: a map of a known :space and a vector of :coords."
  [x]
  (and (map? x)
       (some? (space/space (:space x)))
       (vector? (:coords x))))

(defn colour
  "The colour map of `x`, a CSS colour string or a colour map, which gets an
  :alpha of 1 unless it has one, or nil for anything else."
  [x]
  (cond
    (colour? x) (if (contains? x :alpha) x (assoc x :alpha 1.0))
    (string? x) (syntax/parse x)))

(defn to
  "The colour `x` in the `space`, e.g. :oklch, which may lie outside the
  gamut of the space. A missing component counts as 0 in a conversion, and
  stays missing when `space` is the colour's own."
  [x space]
  (when-let [c (colour x)]
    (let [id (:id (space/space space))]
      (if (= id (:space c))
        c
        (assoc c
               :space id
               :coords (space/convert (:coords c) (:space c) id))))))

(defn components
  "The components of the colour `x` in the `space`, by default its own, as
  a map of their ids to their values, with the :alpha, e.g.
  {:l 0.44 :c 0.085 :h 158.0 :alpha 1.0} in :oklch."
  ([x]
   (components x (:space (colour x))))
  ([x space]
   (when-let [c (to x space)]
     (-> (map :id (:coords (space/space space)))
         (zipmap (:coords c))
         (assoc :alpha (:alpha c))))))

(defn with
  "The colour `x` in the `space`, with the components that the map `m` names
  replaced, like a relative colour in CSS.

  A key is the id of a component, or :alpha. A number sets the component,
  and a function gets its old value, with 0 for a missing one.

      (with seed :oklch {:l 0.33 :c #(min % 0.1) :h #(+ % 180)})
      ;; as oklch(from var(--seed) 33% min(c, 0.1) calc(h + 180))"
  [x space m]
  (when-let [c (to x space)]
    (let [value (fn [old v] (if (fn? v) (v (or old 0.0)) v))]
      (assoc c
             :coords (mapv (fn [{:keys [id angle?]} old]
                             (if (contains? m id)
                               (let [v (value old (get m id))]
                                 (if (and angle? (number? v)) (mod v 360) v))
                               old))
                           (:coords (space/space space))
                           (:coords c))
             :alpha (if (contains? m :alpha)
                      (value (:alpha c) (:alpha m))
                      (:alpha c))))))

(defn css
  "The colour `x` as a CSS string, e.g. rgb(36, 71, 107) or
  oklch(0.44 0.085 158)."
  [x]
  (some-> (colour x) syntax/serialise))

(defn- hex-pair
  "The byte `n` as two hexadecimal digits."
  [n]
  (let [digits "0123456789abcdef"]
    (str (nth digits (quot n 16)) (nth digits (rem n 16)))))

(defn hex
  "The colour `x` as a CSS hex colour, #rrggbb, or #rrggbbaa when it's
  translucent. Browsers clip each channel of a colour outside sRGB, and so
  does hex. To keep its lightness and hue instead, map it into sRGB first
  with gamut/to-gamut."
  [x]
  (when-let [{:keys [coords alpha]} (to x :srgb)]
    (let [byte #(Math/round (double (* 255 (min 1.0 (max 0.0 (or % 0.0))))))]
      (apply str "#" (map (comp hex-pair byte)
                          (cond-> coords
                            (and alpha (< alpha 1)) (conj alpha)))))))

(comment
  (to "#24476b" :oklch)
  ;; => {:space :oklch, :coords [0.3896… 0.0738… 250.84…], :alpha 1.0}

  (css (with "#24476b" :oklch {:l 0.33 :c #(min % 0.1) :h #(+ % 180)}))

  (hex "oklch(70% 0.3 140)")
  #_.)
