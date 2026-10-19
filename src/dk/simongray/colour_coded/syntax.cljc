(ns dk.simongray.colour-coded.syntax
  "CSS colours, parsed from the syntax of CSS Color 4 and 5, and serialised
  by the rules of CSS Color 4.

  The parser reads:

  - hex colours, the named colours and transparent
  - rgb(), rgba(), hsl(), hsla(), hwb(), lab(), lch(), oklab(), oklch()
    and color(), in the modern syntax and the legacy one with commas, with
    none for a missing component
  - color-mix(), of any number of colours

  It doesn't read relative colours, calc() or system colours.
  https://www.w3.org/TR/css-color-4/
  https://www.w3.org/TR/css-color-5/#color-mix"
  (:require [clojure.string :as str]
            [dk.simongray.colour-coded.impl :as impl]
            [dk.simongray.colour-coded.space :as space]))

;; CSS Color 4, section 6.1
(def named-colours
  "The named colours of CSS Color 4, as hex colours, by their names."
  {"aliceblue" "#f0f8ff" "antiquewhite" "#faebd7" "aqua" "#00ffff"
   "aquamarine" "#7fffd4" "azure" "#f0ffff" "beige" "#f5f5dc"
   "bisque" "#ffe4c4" "black" "#000000" "blanchedalmond" "#ffebcd"
   "blue" "#0000ff" "blueviolet" "#8a2be2" "brown" "#a52a2a"
   "burlywood" "#deb887" "cadetblue" "#5f9ea0" "chartreuse" "#7fff00"
   "chocolate" "#d2691e" "coral" "#ff7f50" "cornflowerblue" "#6495ed"
   "cornsilk" "#fff8dc" "crimson" "#dc143c" "cyan" "#00ffff"
   "darkblue" "#00008b" "darkcyan" "#008b8b" "darkgoldenrod" "#b8860b"
   "darkgray" "#a9a9a9" "darkgreen" "#006400" "darkgrey" "#a9a9a9"
   "darkkhaki" "#bdb76b" "darkmagenta" "#8b008b" "darkolivegreen" "#556b2f"
   "darkorange" "#ff8c00" "darkorchid" "#9932cc" "darkred" "#8b0000"
   "darksalmon" "#e9967a" "darkseagreen" "#8fbc8f" "darkslateblue" "#483d8b"
   "darkslategray" "#2f4f4f" "darkslategrey" "#2f4f4f"
   "darkturquoise" "#00ced1" "darkviolet" "#9400d3" "deeppink" "#ff1493"
   "deepskyblue" "#00bfff" "dimgray" "#696969" "dimgrey" "#696969"
   "dodgerblue" "#1e90ff" "firebrick" "#b22222" "floralwhite" "#fffaf0"
   "forestgreen" "#228b22" "fuchsia" "#ff00ff" "gainsboro" "#dcdcdc"
   "ghostwhite" "#f8f8ff" "gold" "#ffd700" "goldenrod" "#daa520"
   "gray" "#808080" "green" "#008000" "greenyellow" "#adff2f"
   "grey" "#808080" "honeydew" "#f0fff0" "hotpink" "#ff69b4"
   "indianred" "#cd5c5c" "indigo" "#4b0082" "ivory" "#fffff0"
   "khaki" "#f0e68c" "lavender" "#e6e6fa" "lavenderblush" "#fff0f5"
   "lawngreen" "#7cfc00" "lemonchiffon" "#fffacd" "lightblue" "#add8e6"
   "lightcoral" "#f08080" "lightcyan" "#e0ffff"
   "lightgoldenrodyellow" "#fafad2" "lightgray" "#d3d3d3"
   "lightgreen" "#90ee90" "lightgrey" "#d3d3d3" "lightpink" "#ffb6c1"
   "lightsalmon" "#ffa07a" "lightseagreen" "#20b2aa" "lightskyblue" "#87cefa"
   "lightslategray" "#778899" "lightslategrey" "#778899"
   "lightsteelblue" "#b0c4de" "lightyellow" "#ffffe0" "lime" "#00ff00"
   "limegreen" "#32cd32" "linen" "#faf0e6" "magenta" "#ff00ff"
   "maroon" "#800000" "mediumaquamarine" "#66cdaa" "mediumblue" "#0000cd"
   "mediumorchid" "#ba55d3" "mediumpurple" "#9370db"
   "mediumseagreen" "#3cb371" "mediumslateblue" "#7b68ee"
   "mediumspringgreen" "#00fa9a" "mediumturquoise" "#48d1cc"
   "mediumvioletred" "#c71585" "midnightblue" "#191970" "mintcream" "#f5fffa"
   "mistyrose" "#ffe4e1" "moccasin" "#ffe4b5" "navajowhite" "#ffdead"
   "navy" "#000080" "oldlace" "#fdf5e6" "olive" "#808000"
   "olivedrab" "#6b8e23" "orange" "#ffa500" "orangered" "#ff4500"
   "orchid" "#da70d6" "palegoldenrod" "#eee8aa" "palegreen" "#98fb98"
   "paleturquoise" "#afeeee" "palevioletred" "#db7093" "papayawhip" "#ffefd5"
   "peachpuff" "#ffdab9" "peru" "#cd853f" "pink" "#ffc0cb" "plum" "#dda0dd"
   "powderblue" "#b0e0e6" "purple" "#800080" "rebeccapurple" "#663399"
   "red" "#ff0000" "rosybrown" "#bc8f8f" "royalblue" "#4169e1"
   "saddlebrown" "#8b4513" "salmon" "#fa8072" "sandybrown" "#f4a460"
   "seagreen" "#2e8b57" "seashell" "#fff5ee" "sienna" "#a0522d"
   "silver" "#c0c0c0" "skyblue" "#87ceeb" "slateblue" "#6a5acd"
   "slategray" "#708090" "slategrey" "#708090" "snow" "#fffafa"
   "springgreen" "#00ff7f" "steelblue" "#4682b4" "tan" "#d2b48c"
   "teal" "#008080" "thistle" "#d8bfd8" "tomato" "#ff6347"
   "turquoise" "#40e0d0" "violet" "#ee82ee" "wheat" "#f5deb3"
   "white" "#ffffff" "whitesmoke" "#f5f5f5" "yellow" "#ffff00"
   "yellowgreen" "#9acd32"})

;; CSS Color 4, section 5.2
(defn parse-hex
  "The colour of the CSS hex colour `s`, e.g. #24476b, #fff or #ffffff80, as
  an sRGB colour, or nil when it isn't one."
  [s]
  (when-let [[_ digits] (re-matches #"^#([0-9a-fA-F]{8}|[0-9a-fA-F]{6}|[0-9a-fA-F]{3,4})$" s)]
    (let [pairs (if (<= (count digits) 4)
                  (map #(str % %) digits)
                  (map #(apply str %) (partition 2 digits)))
          [r g b a] (map #(/ #?(:clj (Integer/parseInt % 16) :cljs (js/parseInt % 16))
                             255.0)
                         pairs)]
      {:space :srgb :coords [r g b] :alpha (or a 1.0)})))

;; CSS Syntax 3, section 4.3.12: a number, then a percent sign, a unit or
;; nothing
(def component-pattern
  "The syntax of a component of a colour function: a CSS number, and its
  unit or percent sign, if any."
  #"^([+-]?(?:\d+\.?\d*|\.\d+)(?:[eE][+-]?\d+)?)(%|[a-z]*)$")

;; CSS Values 4, section 7.1: the units of an angle, in degrees
(def angle-units
  "The degrees in each unit of a CSS angle."
  {"deg" 1.0 "grad" 0.9 "rad" (/ 180 Math/PI) "turn" 360.0})

(defn- component
  "The value of the component `token`, by the `spec` of its place in the
  function, a map of these:

  - :percent and :number, the factors of a percentage and of a number
  - :angle?, whether it's a hue
  - :min and :max, the range that it's clamped to

  None returns nil, for a missing component, and an invalid token
  ::invalid."
  [token {:keys [percent number angle?] lo :min hi :max :or {number 1.0}}]
  (let [[_ n unit] (re-matches component-pattern token)
        v          (some-> n parse-double)
        v          (cond
                     (= token "none")                   nil
                     (nil? v)                           ::invalid
                     (= unit "%")                       (if percent (* v percent) ::invalid)
                     (= unit "")                        (* v number)
                     (and angle? (angle-units unit))    (* v (angle-units unit))
                     :else                              ::invalid)]
    (cond
      (not (number? v)) v
      angle?            (mod v 360)
      :else             (cond-> v
                          lo (max lo)
                          hi (min hi)))))

;; CSS Color 4, sections 5.1, 7, 8, 9 and 10, for the ranges and the
;; clamping at parsed-value time
(def functions
  "The spec of each colour function of CSS, by its name: the space of its
  colours, and the specs of its components and its alpha, for component to
  read."
  (let [rgb   {:percent 0.01 :number (/ 1 255.0) :min 0.0 :max 1.0}
        hue   {:angle? true}
        alpha {:percent 0.01 :min 0.0 :max 1.0}]
    {"rgb"   {:space :srgb :components [rgb rgb rgb] :alpha alpha}
     "hsl"   {:space :hsl :components [hue {:percent 1.0 :min 0.0} {:percent 1.0}] :alpha alpha}
     "hwb"   {:space :hwb :components [hue {:percent 1.0} {:percent 1.0}] :alpha alpha}
     "lab"   {:space      :lab
              :components [{:percent 1.0 :min 0.0 :max 100.0} {:percent 1.25} {:percent 1.25}]
              :alpha      alpha}
     "lch"   {:space      :lch
              :components [{:percent 1.0 :min 0.0 :max 100.0} {:percent 1.5 :min 0.0} hue]
              :alpha      alpha}
     "oklab" {:space      :oklab
              :components [{:percent 0.01 :min 0.0 :max 1.0} {:percent 0.004} {:percent 0.004}]
              :alpha      alpha}
     "oklch" {:space      :oklch
              :components [{:percent 0.01 :min 0.0 :max 1.0} {:percent 0.004 :min 0.0} hue]
              :alpha      alpha}
     "color" {:components (repeat 3 {:percent 0.01}) :alpha alpha}}))

(def color-spaces
  "The spaces that color() names, by the names it gives them."
  {"srgb"              :srgb
   "srgb-linear"       :srgb-linear
   "display-p3"        :display-p3
   "display-p3-linear" :display-p3-linear
   "a98-rgb"           :a98-rgb
   "prophoto-rgb"      :prophoto-rgb
   "rec2020"           :rec2020
   "xyz"               :xyz-d65
   "xyz-d50"           :xyz-d50
   "xyz-d65"           :xyz-d65})

(defn- arguments
  "The components and the alpha in the arguments `body` of the colour
  function `f`, as a pair: split by commas in the legacy syntax, where `f`
  allows it, or else by spaces and a slash."
  [f body]
  (if (and (str/includes? body ",") (#{"rgb" "hsl"} f))
    (let [parts (mapv str/trim (str/split body #","))]
      (when (#{3 4} (count parts))
        [(subvec parts 0 3) (get parts 3)]))
    (let [[main alpha & more] (str/split body #"/")]
      (when-not more
        [(str/split (str/trim main) #"\s+") (some-> alpha str/trim)]))))

(declare parse)

(defn- split-commas
  "The parts of `s` between the commas outside parentheses, trimmed."
  [s]
  (let [[parts current] (reduce (fn [[parts current depth] ch]
                                  (cond
                                    (and (= ch \,) (zero? depth)) [(conj parts current) "" depth]
                                    (= ch \()                     [parts (str current ch) (inc depth)]
                                    (= ch \))                     [parts (str current ch) (dec depth)]
                                    :else                         [parts (str current ch) depth]))
                                [[] "" 0]
                                s)]
    (mapv str/trim (conj parts current))))

(def mix-spaces
  "The spaces that color-mix() interpolates in, by the names it gives
  them."
  (assoc color-spaces
         "lab" :lab "lch" :lch "oklab" :oklab "oklch" :oklch "hsl" :hsl "hwb" :hwb))

(defn- mix-method
  "The space and the hue method of the interpolation method `s` of
  color-mix(), e.g. \"in oklch longer hue\", as a pair, or nil."
  [s]
  (let [[in id method hue & more] (str/split s #"\s+")
        space                     (mix-spaces id)
        method                    (if method
                                    (when (and (= hue "hue") (space/hue-index space))
                                      (#{:shorter :longer :increasing :decreasing}
                                       (keyword method)))
                                    :shorter)]
    (when (and (= in "in") space method (empty? more))
      [space method])))

(defn- mix-argument
  "The colour and the percentage of the argument `s` of color-mix(), e.g.
  \"red 30%\", as a pair with nil for an omitted percentage, or nil when
  either is invalid."
  [s]
  (let [[_ p before] (re-matches #"^(\d*\.?\d+)%\s+(.+)$" s)
        [_ after q]  (re-matches #"^(.+?)\s+(\d*\.?\d+)%$" s)
        [c p]        (cond before [before p] after [after q] :else [s nil])
        p            (some-> p parse-double)]
    (when-let [colour (parse c)]
      (when (or (nil? p) (<= 0 p 100))
        [colour p]))))

(defn- mix-percentages
  "The percentages `ps` of the colours of color-mix(), each nil where it's
  omitted, with the omitted ones sharing what the rest leave of 100."
  [ps]
  (let [given   (reduce + (keep identity ps))
        omitted (count (filter nil? ps))
        share   (when (pos? omitted) (max 0.0 (/ (- 100.0 given) omitted)))]
    (mapv #(if (nil? %) share %) ps)))

;; CSS Color 5, sections 3.2 and 3.3, with the normalization of mix
;; percentages of CSS Values 5, which lets them sum to zero: the colours
;; mix in turn, each into the mix of those before by its share, and a sum
;; under 100% makes the mix translucent
(defn- parse-mix
  "The colour of color-mix() with the arguments `body`, or nil when they're
  invalid."
  [body]
  (let [parts          (split-commas body)
        method?        (str/starts-with? (first parts) "in ")
        [space method] (if method? (mix-method (first parts)) [:oklab :shorter])
        args           (map mix-argument (if method? (rest parts) parts))]
    (when (and space (seq args) (every? some? args))
      (let [ps    (mix-percentages (map second args))
            sum   (reduce + ps)
            mixed (reduce (fn [[acc w] [c p]]
                            [((impl/interpolate acc c space method)
                              (if (pos? (+ w p)) (/ p (+ w p)) 0.5))
                             (+ w p)])
                          [(impl/carry-forward (ffirst args) space) (first ps)]
                          (map vector (map first (rest args)) (rest ps)))]
        (update (first mixed) :alpha #(some-> % (* (min 1.0 (/ sum 100)))))))))

(defn- parse-colour-function
  "The colour of the CSS colour function `f`, other than color-mix(), with
  the arguments `body`, or nil when they're invalid."
  [f body]
  (let [f                       (case f "rgba" "rgb" "hsla" "hsl" f)
        {:keys [components] :as spec} (functions f)
        [tokens alpha]          (when spec (arguments f body))
        [space-id tokens]       (if (= f "color")
                                  [(color-spaces (first tokens)) (rest tokens)]
                                  [(:space spec) tokens])
        values                  (map component tokens components)
        a                       (if alpha (component alpha (:alpha spec)) 1.0)]
    (when (and space-id
               (= 3 (count tokens))
               (not-any? #{::invalid} (cons a values)))
      {:space space-id :coords (vec values) :alpha a})))

(defn parse
  "The colour of the CSS colour `s`, e.g. \"#24476b\", \"rebeccapurple\" or
  \"oklch(44% 0.085 158)\", as a map of its :space, :coords and :alpha, or
  nil when the parser can't read it."
  [s]
  (when (string? s)
    (let [s (-> (str/replace s #"/\*.*?\*/" "") str/trim str/lower-case)]
      (cond
        (str/starts-with? s "#") (parse-hex s)
        (named-colours s)        (parse-hex (named-colours s))
        (= s "transparent")      {:space :srgb :coords [0.0 0.0 0.0] :alpha 0.0}
        :else                    (when-let [[_ f body] (re-matches #"^([a-z0-9-]+)\((.*)\)$" s)]
                                   (if (= f "color-mix")
                                     (parse-mix (str/trim body))
                                     (parse-colour-function f (str/trim body))))))))

(defn format-number
  "The number `x` as a CSS number, rounded to `digits` significant digits,
  by default 6, without trailing zeros, and the same on every platform."
  ([x]
   (format-number x 6))
  ([x digits]
   (if (or (zero? x) (not (< (abs x) 1e15)))
     "0"
     (let [places   (-> (- digits (Math/ceil (Math/log10 (abs x))))
                        (max 0)
                        (min 12)
                        long)
           n        (Math/round (* (abs x) (Math/pow 10 places)))
           s        (str n)
           s        (str (apply str (repeat (- (inc places) (count s)) "0")) s)
           whole    (subs s 0 (- (count s) places))
           fraction (str/replace (subs s (- (count s) places)) #"0+$" "")]
       (str (when (and (neg? x) (pos? n)) "-")
            whole
            (when (seq fraction) (str "." fraction)))))))

(defn- serialise-alpha
  "The alpha `a` as the end of a colour function: nothing when it's 1, or
  else a slash and the alpha, or none."
  [a]
  (cond
    (nil? a)  " / none"
    (== a 1)  ""
    :else     (str " / " (format-number a))))

(defn- serialise-rgb
  "The sRGB `coords` and alpha `a` as rgb() or rgba(), the form in which CSS
  serialises a legacy colour, with each channel from 0 to 255."
  [coords a]
  (let [channels (map #(Math/round (double (* 255 (min 1.0 (max 0.0 %))))) coords)]
    (if (== a 1)
      (str "rgb(" (str/join ", " channels) ")")
      (str "rgba(" (str/join ", " channels) ", " (format-number a) ")"))))

;; CSS Color 4, section 16
(defn serialise
  "The `colour`, a map of its :space, :coords and :alpha, as CSS:

  - sRGB, HSL and HWB as rgb() or rgba(), the legacy form, unless a
    component is missing
  - Lab, LCH, Oklab and OKLCH as their own functions
  - the rest as color()"
  [{:keys [coords alpha] :as colour}]
  (let [id      (:id (space/space (:space colour)))
        missing (some nil? coords)
        number  #(if (nil? %) "none" (format-number %))
        named   (into {} (map (fn [[k v]] [v k])) (dissoc color-spaces "xyz"))]
    (cond
      (and (#{:srgb :hsl :hwb} id) (not missing) (some? alpha))
      (serialise-rgb (space/convert coords id :srgb) alpha)

      (#{:lab :lch :oklab :oklch} id)
      (str (name id) "(" (str/join " " (map number coords)) (serialise-alpha alpha) ")")

      (named id)
      (str "color(" (named id) " " (str/join " " (map number coords)) (serialise-alpha alpha) ")")

      (#{:hsl :hwb} id)
      (serialise (assoc colour :space :srgb :coords (space/convert coords id :srgb)))

      :else
      (serialise (assoc colour :space :xyz-d65 :coords (space/convert coords id :xyz-d65))))))
