;; # colour-coded
;;
;; This page applies every part of the library to examples, from the
;; themes that most apps want down to the colour spaces underneath. Each
;; example shows the calls to the library, and draws what they return
;; below them. Start it with `clojure -X:demo`, and it updates whenever you
;; save a change to the code.
^{:nextjournal.clerk/visibility {:code :hide :result :hide}}
(ns demo
  (:require [clojure.string :as str]
            [dk.simongray.colour-coded :as cc]
            [dk.simongray.colour-coded.choose :as choose]
            [dk.simongray.colour-coded.contrast :as contrast]
            [dk.simongray.colour-coded.difference :as difference]
            [dk.simongray.colour-coded.gamut :as gamut]
            [dk.simongray.colour-coded.harmony :as harmony]
            [dk.simongray.colour-coded.image :as image]
            [dk.simongray.colour-coded.mix :as mix]
            [dk.simongray.colour-coded.quantise :as quantise]
            [dk.simongray.colour-coded.syntax :as syntax]
            [dk.simongray.colour-coded.target :as target]
            [dk.simongray.colour-coded.theme :as theme]
            [dk.simongray.colour-coded.tone :as tone]
            [dk.simongray.colour-coded.vision :as vision]
            [nextjournal.clerk :as clerk])
  (:import [java.awt Color GradientPaint Polygon Rectangle RenderingHints]
           [java.awt.geom Ellipse2D$Double]
           [java.awt.image BufferedImage]
           [java.io ByteArrayOutputStream]
           [java.util Base64 Locale]
           [javax.imageio ImageIO]))

;; The code that draws the results is hidden, since it's about this page
;; and not the library.

^{::clerk/visibility {:code :hide :result :hide}}
(defn fmt
  "The `pattern` filled with the `args` as String/format does, with a point
  for the decimals whatever the locale."
  [pattern & args]
  (String/format Locale/ROOT pattern (to-array args)))

^{::clerk/visibility {:code :hide :result :hide}}
(defn swatch
  "Hiccup of a swatch of the colour `x`, labelled with its hex colour, or
  with the `label`."
  ([x]
   (swatch x (cc/hex x)))
  ([x label]
   [:div {:style {:background    (cc/hex x)
                  :color         (cc/hex (contrast/black-or-white x))
                  :width         "7.5rem"
                  :min-height    "4rem"
                  :border-radius "6px"
                  :padding       "0.4rem"
                  :box-sizing    "border-box"
                  :display       "flex"
                  :align-items   "flex-end"
                  :font          "11px/1.3 ui-monospace, monospace"
                  :box-shadow    "inset 0 0 0 1px rgba(0,0,0,0.1)"}}
    label]))

^{::clerk/visibility {:code :hide :result :hide}}
(defn row
  "Hiccup of the hiccup `items` side by side."
  [items]
  (into [:div {:style {:display "flex" :flex-wrap "wrap" :gap "0.5rem" :align-items "flex-start"}}]
        items))

^{::clerk/visibility {:code :hide :result :hide}}
(defn wide
  "The hiccup `h` as HTML as wide as the page allows."
  [h]
  (clerk/html {::clerk/width :wide} h))

^{::clerk/visibility {:code :hide :result :hide}}
(defn swatches
  "The colours `xs` as swatches, labelled with their hex colours or with the
  `labels`."
  ([xs]
   (wide (row (map swatch xs))))
  ([xs labels]
   (wide (row (map swatch xs labels)))))

^{::clerk/visibility {:code :hide :result :hide}}
(defn strip
  "Hiccup of the colours `xs` as one strip, e.g. the steps of a gradient."
  [xs]
  [:div {:style {:display "flex" :height "2.25rem" :border-radius "6px" :overflow "hidden"}}
   (for [x xs]
     [:div {:style {:flex 1 :background (cc/hex x)}}])])

^{::clerk/visibility {:code :hide :result :hide}}
(defn labelled
  "Rows of a label and hiccup, from the pairs `rows`."
  [rows]
  (wide
   [:div {:style {:display "grid" :grid-template-columns "11rem 1fr" :gap "0.5rem" :align-items "center"
                  :font "13px system-ui"}}
    (for [[label h] rows]
      (list [:div label] h))]))

^{::clerk/visibility {:code :hide :result :hide}}
(defn bold
  "Hiccup of the text `s` in bold, in the colour around it."
  [s]
  [:span {:style {:font-weight 600}} s])

^{::clerk/visibility {:code :hide :result :hide}}
(defn code
  "Hiccup of the text `s` as preformatted code."
  [s]
  (clerk/html [:pre {:style {:font-size "12px" :max-height "24rem" :overflow "auto"}} s]))

^{::clerk/visibility {:code :hide :result :hide}}
(defn data-url
  "The PNG bytes `b` as a data URL, for an img."
  [b]
  (str "data:image/png;base64," (.encodeToString (Base64/getEncoder) b)))

^{::clerk/visibility {:code :hide :result :hide}}
(defn cover
  "Hiccup of the cover whose PNG bytes are `b`, `n` pixels wide."
  [b n]
  [:img {:src (data-url b) :width n :style {:border-radius "6px" :display "block"}}])

^{::clerk/visibility {:code :hide :result :hide}}
(defn page
  "Hiccup of a small page in the colours of the `theme` at the `level` of
  contrast and in the `appearance`."
  [t level appearance]
  (let [c (get (theme/hex t level) appearance)]
    [:div {:style {:background (:background c) :color (:text c) :padding "0.9rem" :border-radius "10px"
                   :width "15rem" :font "13px/1.4 system-ui" :box-shadow "inset 0 0 0 1px rgba(127,127,127,0.25)"}}
     [:div {:style {:color (:text-muted c) :font-size "11px" :text-transform "uppercase" :letter-spacing "0.06em"}}
      (str (name appearance) ", " (name level))]
     [:div {:style {:font-size "16px" :font-weight 600 :margin "0.2rem 0"}} "Episode 42"]
     [:div {:style {:color (:text-muted c)}} "18 October · 54 min"]
     [:div {:style {:background (:surface c) :padding "0.6rem" :border-radius "8px" :margin "0.6rem 0"}}
      "Notes with a " [:span {:style {:color (:link c) :text-decoration "underline"}} "link"] "."
      [:div {:style {:border-top (str "1px solid " (:divider c)) :margin "0.5rem 0"}}]
      [:div {:style {:border (str "1px solid " (:border c)) :border-radius "6px" :padding "0.25rem 0.5rem"
                     :color (:text-muted c) :background (:background c)}}
       "Search"]]
     [:span {:style {:background (:accent c) :color (:on-accent c) :border-radius "6px" :padding "0.3rem 0.8rem"
                     :outline (str "2px solid " (:focus c)) :outline-offset "2px" :display "inline-block"}}
      "Play"]]))

^{::clerk/visibility {:code :hide :result :hide}}
(defn theme-rows
  "Rows of the light and dark pages of each theme, after its hiccup label,
  from the pairs `rows` of a label and a theme."
  [rows]
  (wide
   [:div {:style {:display "grid" :gap "0.75rem"}}
    (for [[label t] rows]
      (row (cons [:div {:style {:width "8rem"}} label]
                 (for [appearance [:light :dark]]
                   (page t :standard appearance)))))]))

^{::clerk/visibility {:code :hide :result :hide}}
(defn player
  "Hiccup of a small player for the cover `b`, titled `title`, in the
  colours of the `scheme` of image/colours."
  [title b scheme]
  (let [{:keys [background text accent accent-text seed]}
        (update-vals scheme #(if (vector? %) % (some-> % cc/hex)))]
    [:div {:style {:background background :color text :border-radius "8px" :padding "0.6rem" :width "13rem"
                   :font "13px system-ui" :box-shadow "inset 0 0 0 1px rgba(0,0,0,0.1)"}}
     [:div {:style {:display "flex" :gap "0.5rem" :align-items "center"}}
      (cover b 40)
      [:div (bold title) [:div {:style {:opacity 0.8}} (str "seed " seed)]]]
     [:div {:style {:height "4px" :background accent :margin "0.5rem 0" :border-radius "2px"}}]
     [:span {:style {:background accent :color accent-text :padding "0.15rem 0.5rem" :border-radius "4px"}}
      "Play"]]))

^{::clerk/visibility {:code :hide :result :hide}}
(defn shares
  "Hiccup of the quantised colours `xs` as a strip, each as wide as its
  share."
  [xs]
  [:div {:style {:display "flex" :height "2rem" :width "24rem" :border-radius "6px" :overflow "hidden"
                 :box-shadow "inset 0 0 0 1px rgba(0,0,0,0.1)"}}
   (for [{:keys [colour share]} xs]
     [:div {:style {:width (str (* 100 share) "%") :background (cc/hex colour)}}])])

^{::clerk/visibility {:code :hide :result :hide}}
(defn bar
  "Hiccup of a player's bar in the colours `t` of the bar tokens, by the
  `seed`."
  [seed t]
  (let [t (update-vals t cc/hex)]
    [:div {:style {:background (:bar t) :color (:on-bar t) :padding "0.6rem 0.8rem" :border-radius "6px"
                   :width "10rem" :font "13px system-ui"}}
     [:div {:style {:display "flex" :gap "0.4rem" :align-items "center"}}
      [:div {:style {:width "1.6rem" :height "1.6rem" :border-radius "4px" :background (cc/hex seed)}}]
      (bold "Now playing")]
     [:div {:style {:color (:on-bar-2 t) :margin-top "0.3rem"}} "Episode 42 · 54 min"]
     [:div {:style {:border-top (str "1px solid " (:bar-rule t)) :margin-top "0.4rem"}}]]))

^{::clerk/visibility {:code :hide :result :hide}}
(defn contrast-grid
  "A grid of the `samples`, each a map of a :background and the ratio and
  level of black and white text on it."
  [samples]
  (let [cell (fn [fg bg [r level]]
               [:div
                [:div {:style {:background (cc/hex bg) :color fg :padding "0.5rem 0.8rem"
                               :border-radius "6px" :font-size "16px"
                               :box-shadow "inset 0 0 0 1px rgba(0,0,0,0.15)"}}
                 "Aa text"]
                [:div {:style {:margin-top "0.2rem"}}
                 (fmt "%.2f, %s" r (or (some-> level name) "fails"))]])]
    (clerk/html
     [:div {:style {:display "grid" :grid-template-columns "8rem 10rem 10rem" :gap "0.75rem 1rem"
                    :align-items "start" :font "13px system-ui"}}
      (list [:div] (bold "black text") (bold "white text"))
      (for [{:keys [background black white]} samples]
        (list [:div {:style {:padding-top "0.6rem" :font-family "ui-monospace, monospace"}} background]
              (cell "black" background black)
              (cell "white" background white)))])))

^{::clerk/visibility {:code :hide :result :hide}}
(defn painting
  "The bytes of a PNG image of `w` by `h` pixels that `paint!` draws with a
  Graphics2D."
  [w h paint!]
  (let [img (BufferedImage. w h BufferedImage/TYPE_INT_ARGB)
        g   (.createGraphics img)
        out (ByteArrayOutputStream.)]
    (.setRenderingHint g RenderingHints/KEY_ANTIALIASING RenderingHints/VALUE_ANTIALIAS_ON)
    (paint! g)
    (.dispose g)
    (ImageIO/write img "png" out)
    (.toByteArray out)))

^{::clerk/visibility {:code :hide :result :hide}}
(defn fill!
  "Fill the `shape` with the hex colour `hex` on the Graphics2D `g`."
  [g hex shape]
  (.setColor g (Color/decode hex))
  (.fill g shape))

;; The examples use four made-up covers instead of real podcast artwork:
;; Kandinsky's yellow triangle, red square and blue circle on paper, a
;; night with a moon, a forest, and greys with one red dot. The map
;; `artworks` holds each as a PNG image.

^{::clerk/visibility {:code :hide :result :hide}}
(def artworks
  (array-map
   "Bauhaus" (painting 300 300 (fn [g]
                                 (fill! g "#efe9dc" (Rectangle. 0 0 300 300))
                                 (fill! g "#e8b931" (Polygon. (int-array [30 140 85]) (int-array [140 140 40]) 3))
                                 (fill! g "#c8382d" (Rectangle. 165 55 100 100))
                                 (fill! g "#2b4c9b" (Ellipse2D$Double. 85.0 165.0 115.0 115.0))))
   "Night"   (painting 300 300 (fn [g]
                                 (.setPaint g (GradientPaint. (float 0) (float 0) (Color/decode "#0d1b3e")
                                                              (float 0) (float 300) (Color/decode "#3b2a6b")))
                                 (.fill g (Rectangle. 0 0 300 300))
                                 (fill! g "#f2a541" (Ellipse2D$Double. 190.0 40.0 70.0 70.0))))
   "Forest"  (painting 300 300 (fn [g]
                                 (fill! g "#dfe8d0" (Rectangle. 0 0 300 300))
                                 (doseq [[i hex] (map-indexed vector ["#1f4d2b" "#2e6b3a" "#4f8a3c" "#7aa95c" "#a9c97f"])]
                                   (fill! g hex (Rectangle. (* i 60) (- 120 (* i 15)) 60 (+ 180 (* i 15)))))))
   "Greys"   (painting 300 300 (fn [g]
                                 (fill! g "#f4f4f2" (Rectangle. 0 0 300 300))
                                 (fill! g "#2b2b2b" (Rectangle. 40 40 220 150))
                                 (fill! g "#8a8a8a" (Rectangle. 40 210 220 50))
                                 (fill! g "#d23c2c" (Ellipse2D$Double. 220.0 215.0 30.0 30.0))))))

^{::clerk/visibility {:code :hide :result :show}}
(clerk/html (row (for [[title b] artworks]
                   [:figure {:style {:margin 0 :font "12px system-ui"}}
                    (cover b 120)
                    [:figcaption title]])))

;; From here on, each example shows its code, and the page draws its
;; result below it.

{::clerk/visibility {:code :show :result :hide}}

;; ## Themes from an image
;;
;; Give `theme/from-image` an image, and it returns the colours of a whole
;; site or app: ten roles, in light and dark, at two levels of contrast.
;; Each page below uses nothing but its theme's colours.

(def cover-themes
  (update-vals artworks theme/from-image))

^{::clerk/visibility {:code :hide :result :show}}
(theme-rows (for [[title t] cover-themes]
              [(cover (artworks title) 80) t]))

;; ## Themes from a colour
;;
;; With `theme/from-seed`, a theme comes from one colour instead:

(def seed-themes
  (map theme/from-seed ["#24476b" "#e8b931" "#c8382d" "#7b2cbf" "#808080"]))

^{::clerk/visibility {:code :hide :result :show}}
(theme-rows (for [t seed-themes]
              [(swatch (:seed t)) t]))

;; The level :more, which the CSS uses under prefers-contrast: more, sits
;; here beside :standard:

(def green
  (theme/from-seed "#2e8b57"))

^{::clerk/visibility {:code :hide :result :show}}
(wide (row (for [level [:standard :more] appearance [:light :dark]]
             (page green level appearance))))

;; A theme's report checks every pair of roles that must contrast. These
;; are the six closest pairs in the yellow seed's theme:

(def closest
  (->> (theme/report (theme/from-seed "#e8b931"))
       (sort-by :ratio)
       (take 6)))

^{::clerk/visibility {:code :hide :result :show}}
(clerk/table (for [{:keys [roles level appearance ratio wants ok?]} closest]
               {:roles      (str/join " on " (map name roles))
                :level      (name level)
                :appearance (name appearance)
                :ratio      (fmt "%.2f" ratio)
                :wants      wants
                :ok?        ok?}))

;; This is a theme's CSS, for a web page:

(def page-css
  (theme/css (theme/from-seed "#24476b")))

^{::clerk/visibility {:code :hide :result :show}}
(code page-css)

;; ## The colours of an image
;;
;; From an image, `image/seed` returns the colour to derive a UI from. For a player, `image/colours` returns a small scheme: a
;; background, its text, and an accent that stands apart from it.

(def schemes
  (update-vals artworks image/colours))

^{::clerk/visibility {:code :hide :result :show}}
(wide (row (for [[title scheme] schemes]
             (player title (artworks title) scheme))))

;; The night's seed is its dark blue, the commonest colour with a hue, not
;; its orange moon. Material's Score, which also weighs chroma, would pick
;; the moon, but it isn't in the library yet.
;;
;; Each cover's five commonest colours come from two quantisers:
;; `quantise/buckets`, which podcast-clj uses, and `quantise/k-means`,
;; which clusters in Oklab. Each colour is as wide as its share of the
;; image, so a strip ends where the five colours' shares run out.

(def palettes
  (for [[title b] artworks]
    {:title   title
     :buckets (image/palette b 5 quantise/buckets)
     :k-means (image/palette b 5 quantise/k-means)}))

^{::clerk/visibility {:code :hide :result :show}}
(labelled (for [{:keys [title buckets k-means]} palettes
                [n p] [["buckets" buckets] ["k-means" k-means]]]
            [(str title ", " n) (shares p)]))

;; ## Tones at a fixed lightness
;;
;; A token takes a colour from a seed at a fixed lightness, caps its
;; chroma, and turns its hue. These tokens are the bars of the podcast app,
;; which turn each seed's hue halfway round:

(def bar-tokens
  {:bar      {:l 0.33 :c 0.1 :turn 180}
   :on-bar   {:l 0.97 :c 0.012 :turn 180}
   :on-bar-2 {:l 0.84 :c 0.035 :turn 180}
   :bar-rule {:l 0.68 :c 0.05 :turn 180}})

(def bar-seeds
  ["#c8382d" "#e8b931" "#2e8b57" "#2b4c9b" "#7b2cbf" "#808080"])

(def bars
  (map #(tone/tones bar-tokens %) bar-seeds))

^{::clerk/visibility {:code :hide :result :show}}
(wide (row (map bar bar-seeds bars)))

;; In CSS, each token derives from one custom property:

(def bar-css
  (update-vals bar-tokens #(tone/relative-css % "--seed")))

^{::clerk/visibility {:code :hide :result :show}}
(code (str/join "\n" (for [[k v] bar-css] (str "--" (name k) ": " v ";"))))

;; Over seeds of every hue and chroma, each pair's contrast never falls
;; below its least. The gamut says whether the tones' mapping into sRGB or
;; a browser's clipping gave it:

(def worst
  (for [[a b] [[:on-bar :bar] [:on-bar-2 :bar] [:bar-rule :bar]]]
    (assoc (tone/worst-contrast (bar-tokens a) (bar-tokens b))
           :pair [a b])))

^{::clerk/visibility {:code :hide :result :show}}
(clerk/table (for [{:keys [pair ratio hue gamut]} worst]
               {:pair  (str/join " on " (map name pair))
                :least (fmt "%.2f" ratio)
                :hue   hue
                :gamut (name gamut)}))

;; A token in OKLCH keeps its lightness but not its contrast. In LCH,
;; whose lightness is L*, the contrast varies far less. This chart follows
;; a vivid token's contrast with white over every hue of the seed:

(def sweep
  (for [h          (range 0 360 4)
        [n token]  {"OKLCH, l 0.5" {:l 0.5 :c 0.15}
                    "LCH, L* 45"   {:space :lch :l 45 :c 50}}
        :let [seed {:space :oklch :coords [0.6 0.2 h]}]]
    {:hue    h
     :tokens n
     :ratio  (contrast/ratio (tone/tone token seed) "white")}))

^{::clerk/visibility {:code :hide :result :show}}
(clerk/vl
 {:width    600
  :height   220
  :data     {:values sweep}
  :mark     "line"
  :encoding {:x     {:field "hue" :type "quantitative" :title "hue of the seed"}
             :y     {:field "ratio" :type "quantitative" :scale {:zero false} :title "contrast with white"}
             :color {:field "tokens" :type "nominal"}}})

;; ## Colours by their contrast
;;
;; Adobe's Leonardo finds a colour's lightness by the contrast it should
;; have with a background, and so does `at-contrast`. These four colours
;; have contrasts of 1.5, 3, 4.5 and 7, on white and on near-black:

(def scales
  (for [background ["white" "#121212"]
        x          ["#24476b" "#e8b931" "#c8382d" "#2e8b57"]]
    {:colour     x
     :background background
     :scale      (target/scale x background [1.5 3 4.5 7])}))

^{::clerk/visibility {:code :hide :result :show}}
(labelled (for [{:keys [colour background scale]} scales]
            [(str colour " on " background)
             (row (for [c scale]
                    (swatch c (fmt "%s %.2f" (cc/hex c) (contrast/ratio c background)))))]))

;; ## Harmonies
;;
;; These are the classical harmonies of a red, turned in OKLCH and in HSL,
;; with each colour labelled by its OKLCH lightness. In OKLCH the
;; lightness stays put, but in HSL it jumps.

(def red "#c8382d")

(def harmonies
  (for [space [:oklch :hsl]]
    {:space            space
     :complement       (harmony/turns red [180] space)
     :analogous        (harmony/analogous red space)
     :split-complement (harmony/split-complement red space)
     :triad            (harmony/triad red space)
     :square           (harmony/square red space)
     :wheel-of-6       (harmony/wheel red 6 space)}))

^{::clerk/visibility {:code :hide :result :show}}
(labelled (for [k [:complement :analogous :split-complement :triad :square :wheel-of-6]
                h harmonies]
            [(str (str/replace (name k) "-" " ") ", " (name (:space h)))
             (row (for [c (get h k)]
                    (swatch c (fmt "%s L %.2f" (cc/hex c) (get-in (cc/to c :oklch) [:coords 0])))))]))

;; ## Choosing among colours
;;
;; The seed of a palette is its first colour with a hue, which skips the
;; white and black that often fill most of a cover:

(def palette
  ["#ffffff" "#111111" "#24476b" "#c8382d"])

(def palette-seed
  (choose/seed palette))

^{::clerk/visibility {:code :hide :result :show}}
(swatches palette (map #(if (= % palette-seed) (str % " seed") %) palette))

;; From a list of colours, `spread` keeps only those that differ by a ΔEOK
;; of at least 0.08 from the ones before. Against a background,
;; `first-with-contrast` finds the first colour with a contrast of 3:1,
;; which WCAG asks of a control:

(def candidates
  ["#2b4c9b" "#2f51a3" "#3557a8" "#c8382d" "#cc3f33" "#e8b931" "#7aa95c"])

(def spread
  (choose/spread candidates 0.08))

(def control
  (choose/first-with-contrast candidates "#2b4c9b" 3))

^{::clerk/visibility {:code :hide :result :show}}
(labelled [["all" (row (map swatch candidates))]
           ["spread by 0.08" (row (map swatch spread))]
           ["3:1 on #2b4c9b" (swatch control)]])

;; ## Contrast
;;
;; Each sample shows WCAG 2.2's contrast ratio and the level it meets:
;; :aaa for 7 or more, :aa for 4.5, and :large for 3, which is enough for
;; large text and for controls.

(def samples
  (for [background ["white" "#767676" "#24476b" "#ffd000" "#2277d3" "#121212"]]
    {:background background
     :black      [(contrast/ratio "black" background) (contrast/level "black" background)]
     :white      [(contrast/ratio "white" background) (contrast/level "white" background)]}))

^{::clerk/visibility {:code :hide :result :show}}
(contrast-grid samples)

;; For text on a colour, `black-or-white` picks whichever of the two has
;; the higher contrast by WCAG 2.2. On mid blues it picks black, though
;; most readers find white easier to read there: a known fault of WCAG 2's
;; measure.

(def mid-tones
  (for [h (range 0 360 30)]
    {:space :oklch :coords [0.62 0.15 h]}))

(def picks
  (map contrast/black-or-white mid-tones))

^{::clerk/visibility {:code :hide :result :show}}
(swatches mid-tones (map #(if (= % contrast/black) "black" "white") picks))

;; ## Colour vision
;;
;; The `vision` namespace shows colours as people with a colour vision
;; deficiency see them, by the model of Machado, Oliveira and Fernandes:
;; protan and deutan, the red-green deficiencies, and the rarer tritan.
;; These are the first five colours of the palette that D3 and Matplotlib
;; use for categories, at full severity:

(def chart
  ["#d62728" "#2ca02c" "#1f77b4" "#ff7f0e" "#9467bd"])

(def seen
  (cons [:normal chart]
        (for [d vision/deficiencies]
          [d (map #(vision/simulate % d) chart)])))

^{::clerk/visibility {:code :hide :result :show}}
(labelled (for [[k xs] seen]
            [(name k) (strip xs)]))

;; A severity below 1 simulates the milder deficiencies, in which the cone
;; still responds, but shifted:

(def deutan-by-severity
  (for [severity [0.25 0.5 0.75 1]]
    [severity (map #(vision/simulate % :deutan severity) chart)]))

^{::clerk/visibility {:code :hide :result :show}}
(labelled (for [[severity xs] deutan-by-severity]
            [(str "deutan at " severity) (strip xs)]))

;; To check that a palette's colours stay apart, `least-difference` returns
;; the least ΔEOK between any two of them with each deficiency. For a
;; protan, the chart's green and orange nearly meet:

(def chart-differences
  (vision/least-difference chart))

^{::clerk/visibility {:code :hide :result :show}}
(labelled (for [[k d] chart-differences]
            [(name k)
             [:div {:style {:display "flex" :gap "0.5rem" :align-items "center"}}
              [:div {:style {:width (str (* 100 d) "rem") :height "1rem" :background "#888" :border-radius "3px"}}]
              (fmt "%.3f" d)]]))

;; ## Mixing
;;
;; Each strip mixes the same two colours in 24 steps, in a different
;; space. The mix in sRGB greys out in the middle, while the one in Oklab,
;; the default of CSS, doesn't.

(def mixes
  (for [space [:srgb :srgb-linear :lab :oklab :lch :oklch :hsl]]
    [space (mix/steps "#2b4c9b" "#e8b931" 24 space)]))

^{::clerk/visibility {:code :hide :result :show}}
(labelled (for [[space xs] mixes] [(name space) (strip xs)]))

;; In OKLCH, the hue method decides which way round the hue turns, here
;; from red to blue:

(def hue-methods
  (for [method [:shorter :longer :increasing :decreasing]]
    [method (mix/steps "oklch(0.6 0.2 30)" "oklch(0.6 0.2 260)" 24 :oklch method)]))

^{::clerk/visibility {:code :hide :result :show}}
(labelled (for [[method xs] hue-methods] [(name method) (strip xs)]))

;; ## Colour difference
;;
;; These are the CSS named colours nearest to some colours, by ΔEOK and by
;; CIEDE2000:

(def named
  (for [[n hex] syntax/named-colours]
    (assoc (cc/colour hex) :name n)))

(def nearest
  (for [x ["#24476b" "#c8382d" "#e8b931" "#7aa95c" "#f4f4f2"]]
    {:colour    x
     :ΔEOK      (difference/nearest x named)
     :CIEDE2000 (difference/nearest x named difference/delta-e-2000)}))

^{::clerk/visibility {:code :hide :result :show}}
(labelled (for [{:keys [colour ΔEOK CIEDE2000]} nearest]
            [colour
             (row [(swatch colour)
                   (swatch ΔEOK (str (:name ΔEOK) ", ΔEOK"))
                   (swatch CIEDE2000 (str (:name CIEDE2000) ", CIEDE2000"))])]))

;; ## Gamut mapping
;;
;; A vivid OKLCH colour often lies outside sRGB, and there are three ways
;; to bring it in:
;;
;; - Browsers clip each channel, which shifts its lightness and hue.
;; - CSS Color 4 lowers its chroma at the same lightness until it fits.
;; - The function `reduce-chroma` lowers it too, but keeps the lightness
;;   and hue exactly.
;;
;; This ring of hues has a lightness of 0.7 and a chroma of 0.3:

(def ring
  (for [h (range 0 360 20)]
    {:space :oklch :coords [0.7 0.3 h]}))

(def brought-in
  {"clipped"        (map gamut/clip ring)
   "mapped by CSS"  (map gamut/to-gamut ring)
   "chroma reduced" (map #(gamut/reduce-chroma % :oklch) ring)})

^{::clerk/visibility {:code :hide :result :show}}
(labelled (for [[n xs] brought-in] [n (strip xs)]))

;; Clipping moves the OKLCH lightness of each away from 0.7:

(def clipped-lightness
  (for [c (brought-in "clipped")]
    (get-in (cc/to c :oklch) [:coords 0])))

^{::clerk/visibility {:code :hide :result :show}}
(code (str/join " " (map (partial fmt "%.2f") clipped-lightness)))

;; ## Colours and spaces
;;
;; A colour is a map of its space, its coordinates and its alpha. Every
;; function also takes a CSS colour.

(def purple
  (cc/to "rebeccapurple" :oklch))

^{::clerk/visibility {:code :hide :result :show}}
purple

;; Here is that colour in CSS, in every space of CSS Color 4:

(def in-every-space
  (for [space [:srgb :srgb-linear :display-p3 :a98-rgb :prophoto-rgb :rec2020
               :xyz-d65 :xyz-d50 :lab :lch :oklab :oklch :hsl :hwb]]
    [space (cc/css (cc/to "rebeccapurple" space))]))

^{::clerk/visibility {:code :hide :result :show}}
(clerk/table (for [[space s] in-every-space] {:space (name space) :css s}))

;; The function `with` replaces components, like a relative colour in CSS.
;; Here it steps the OKLCH lightness of a blue from dark to light:

(def lighter
  (for [l [0.2 0.35 0.5 0.65 0.8 0.95]]
    (cc/with "#24476b" :oklch {:l l})))

^{::clerk/visibility {:code :hide :result :show}}
(swatches lighter (map #(fmt "l %.2f" (get-in % [:coords 0])) lighter))

;; ## Syntax
;;
;; The parser reads every colour syntax of CSS Color 4 and 5, including
;; `color-mix()`. These colours are read and then written back out:

(def read-and-written
  (for [s ["#24476b" "rgb(36 71 107 / 50%)" "hsl(120 30% 50%)" "hwb(200 10% 30%)"
           "lab(50% 40 -20)" "oklch(70% 0.15 145)" "color(display-p3 1 0.5 0)"
           "color-mix(in oklch, white, blue)" "color-mix(in srgb, red 25%, blue)"
           "oklch(0.6 none 30)"]]
    {:css s :serialised (cc/css s) :hex (cc/hex s)}))

^{::clerk/visibility {:code :hide :result :show}}
(clerk/table read-and-written)
