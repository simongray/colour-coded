(ns dk.simongray.colour-coded.theme
  "The colours of a site or an app from one colour or one image, light and
  dark, checked for contrast, as CSS or as hex.

      (def t (from-seed \"#24476b\"))  ; or (from-image bytes)
      (css t)               ; custom properties for a web page
      (:dark (hex t))       ; a map of hex colours, e.g. for React Native
      (remove :ok? (report t))

  A theme has ten roles, in words that a beginner knows:

  - :background, the page, and :surface, a panel or a card on it
  - :text, and :text-muted for what matters less, e.g. a date
  - :border, the edge of a control, e.g. a field, and :divider, a line
    between parts that marks nothing
  - :accent, the fill of the main control, e.g. a button, and :on-accent,
    the text on it
  - :link, the colour of a link, and :focus, the ring of the focused
    control

  Each role is a token of tone in LCH, so its lightness is CIELAB's L*,
  like the tones of Material 3. Each pair that must contrast keeps its
  contrast, to within a few percent, whatever the seed's hue, and the
  lightness of each role leaves a margin for that. The accent, the link and
  the focus ring take the seed's hue and chroma, and the rest a hint of its
  hue, like the greys that Radix pairs with an accent.

  There are two levels of contrast, :standard and :more, like those of
  Material 3 and Apple. The function css writes :more for
  prefers-contrast: more.

  A theme is plain data, so you can set a role by hand, e.g. with assoc-in,
  and report checks it again.

  For e-paper, epaper-roles puts each role on a panel's grey:

      (def t (from-seed \"#24476b\" epaper-roles))
      (remove :ok? (report t epaper/ratio))

  https://github.com/material-foundation/material-color-utilities/blob/main/concepts/contrast_for_accessibility.md
  https://www.radix-ui.com/themes/docs/theme/color"
  (:require [clojure.string :as str]
            [dk.simongray.colour-coded :as cc]
            [dk.simongray.colour-coded.choose :as choose]
            [dk.simongray.colour-coded.contrast :as contrast]
            [dk.simongray.colour-coded.image :as image]
            [dk.simongray.colour-coded.quantise :as quantise]
            [dk.simongray.colour-coded.tone :as tone]))

(defn- lch
  "The tokens of the `roles`, a map of each role to its L* and the most
  chroma it takes from the seed, in LCH."
  [roles]
  (update-vals roles (fn [[l c]] {:space :lch :l l :c c})))

(def roles
  "The tokens of each role, by level of contrast and appearance."
  {:standard {:light (lch {:background [100 0]
                           :surface    [97 3]
                           :text       [15 4]
                           :text-muted [45 6]
                           :border     [57 6]
                           :divider    [90 3]
                           :accent     [40 60]
                           :on-accent  [100 0]
                           :link       [40 60]
                           :focus      [40 60]})
              :dark  (lch {:background [8 2]
                           :surface    [14 3]
                           :text       [93 3]
                           :text-muted [70 6]
                           :border     [50 6]
                           :divider    [22 3]
                           :accent     [75 50]
                           :on-accent  [10 4]
                           :link       [75 50]
                           :focus      [75 50]})}
   :more     {:light (lch {:background [100 0]
                           :surface    [97 3]
                           :text       [5 2]
                           :text-muted [33 6]
                           :border     [40 6]
                           :divider    [75 3]
                           :accent     [30 60]
                           :on-accent  [100 0]
                           :link       [30 60]
                           :focus      [30 60]})
              :dark  (lch {:background [5 2]
                           :surface    [12 3]
                           :text       [98 2]
                           :text-muted [82 6]
                           :border     [65 6]
                           :divider    [35 3]
                           :accent     [85 50]
                           :on-accent  [5 2]
                           :link       [85 50]
                           :focus      [85 50]})}})

(defn- on-shades
  "The tokens of the `roles`, a map of each role to its shade on an e-paper
  panel and the most chroma it takes from the seed, in OKLCH."
  [roles]
  (update-vals roles (fn [[shade c]] {:shade shade :c c})))

;; A panel has little contrast to spend. The surface is the same as the
;; background, since the lightest greys are the first to bleach and each
;; shade below white costs the text on it, so a card needs a border
(def epaper-roles
  "The tokens of each role on an e-paper panel, for from-seed. Their colours
  land on one grey by every conversion and keep their contrast as Carta and
  Kaleido 3 show them, except that the level :more asks for a ratio of 7,
  which Kaleido 3 can't reach by epaper/ratio."
  {:standard {:light (on-shades {:background [15 0]
                                 :surface    [15 0]
                                 :text       [0 0]
                                 :text-muted [4 0]
                                 :border     [6 0]
                                 :divider    [12 0]
                                 :accent     [4 0.12]
                                 :on-accent  [15 0]
                                 :link       [4 0.12]
                                 :focus      [4 0.12]})
              :dark  (on-shades {:background [0 0]
                                 :surface    [0 0]
                                 :text       [15 0]
                                 :text-muted [13 0]
                                 :border     [10 0]
                                 :divider    [4 0]
                                 :accent     [13 0.08]
                                 :on-accent  [0 0]
                                 :link       [13 0.08]
                                 :focus      [13 0.08]})}
   :more     {:light (on-shades {:background [15 0]
                                 :surface    [15 0]
                                 :text       [0 0]
                                 :text-muted [3 0]
                                 :border     [5 0]
                                 :divider    [10 0]
                                 :accent     [3 0.1]
                                 :on-accent  [15 0]
                                 :link       [3 0.1]
                                 :focus      [3 0.1]})
              :dark  (on-shades {:background [0 0]
                                 :surface    [0 0]
                                 :text       [15 0]
                                 :text-muted [14 0]
                                 :border     [11 0]
                                 :divider    [6 0]
                                 :accent     [14 0.06]
                                 :on-accent  [0 0]
                                 :link       [14 0.06]
                                 :focus      [14 0.06]})}})

;; WCAG 2.2, success criteria 1.4.3, 1.4.6 and 1.4.11
(def requirements
  "The pairs of roles that must contrast, and the least contrast ratio of
  each, by level of contrast: 4.5 for text and 3 for the parts of
  controls, or 7 and 4.5 at the level :more, which WCAG's AAA asks for."
  (let [pairs (fn [text control]
                [[:text :background text] [:text :surface text]
                 [:text-muted :background text] [:text-muted :surface text]
                 [:link :background text] [:link :surface text]
                 [:on-accent :accent text]
                 [:border :background control] [:border :surface control]
                 [:accent :background control]
                 [:focus :background control] [:focus :surface control]])]
    {:standard (pairs 4.5 3)
     :more     (pairs 7 4.5)}))

(defn from-seed
  "The theme of the colour `seed`, by the tokens of `roles`, by default
  those of roles: a map of the :seed and of the colours of each role, by
  level of contrast and appearance, e.g.
  (get-in theme [:standard :dark :text])."
  ([seed]
   (from-seed seed roles))
  ([seed roles]
   (when-let [seed (cc/colour seed)]
     (into {:seed seed}
           (for [[level appearances] roles]
             [level (update-vals appearances #(tone/tones % seed))])))))

(defn from-pixels
  "The theme of the image whose `pixels` are vectors of red, green, blue and
  alpha from 0 to 255, with the `roles` of from-seed. Its seed is the
  image's commonest colour with a hue, by choose/seed, or else its
  commonest colour. An image without an opaque pixel has no theme."
  ([pixels]
   (from-pixels pixels roles))
  ([pixels roles]
   (let [colours (map :colour (quantise/buckets pixels))]
     (from-seed (or (choose/seed colours) (first colours)) roles))))

(defn from-image
  "The theme of the image in the bytes `b`, e.g. a podcast's artwork, with
  the `roles` of from-seed: from-pixels of the pixels that image/pixels
  decodes. On the JVM it returns the theme, and in ClojureScript a promise
  of it, which is nil where the platform can't decode the image or the image
  is all transparent."
  ([b]
   (from-image b roles))
  ([b roles]
   (image/then (image/pixels b) #(from-pixels % roles))))

(defn colours
  "The colours of the `theme` at the `level` of contrast, by default
  :standard, as a map of each appearance, :light and :dark, to a map of
  each role to its colour."
  ([theme]
   (colours theme :standard))
  ([theme level]
   (get theme level)))

(defn hex
  "The colours of the `theme` at the `level` of contrast, by default
  :standard, as hex colours, e.g. for React Native, which takes no
  oklch(): {:light {:background \"#ffffff\" …} :dark {…}}."
  ([theme]
   (hex theme :standard))
  ([theme level]
   (when-let [appearances (colours theme level)]
     (update-vals appearances #(update-vals % cc/hex)))))

(defn report
  "Each pair of roles of the `theme` that must contrast, at each level and
  in each appearance, by the function `ratio` of two colours, by default
  contrast/ratio, as a map of these:

  - :roles, the pair
  - :level and :appearance
  - :ratio, and :wants, the least that it should be
  - :ok?, whether it is

  A role set to a non-colour has a :ratio of nil, so it fails."
  ([theme]
   (report theme contrast/ratio))
  ([theme ratio]
   (when theme
     (for [[level pairs]        requirements
           [appearance colours] (get theme level)
           [a b wants]          pairs
           :when                (and (colours a) (colours b))
           :let                 [r (ratio (colours a) (colours b))]]
       {:roles      [a b]
        :level      level
        :appearance appearance
        :ratio      r
        :wants      wants
        :ok?        (boolean (and r (>= r wants)))}))))

;; MDN, forced-colors: a page in forced colours keeps to the system colours
;; https://developer.mozilla.org/en-US/docs/Web/CSS/@media/forced-colors
(def system-colours
  "The system colour of each role, for forced colours."
  {:background "Canvas"
   :surface    "Canvas"
   :text       "CanvasText"
   :text-muted "CanvasText"
   :border     "ButtonBorder"
   :divider    "GrayText"
   :accent     "ButtonText"
   :on-accent  "ButtonFace"
   :link       "LinkText"
   :focus      "Highlight"})

(defn- declarations
  "The custom properties `m`, a map of role to value, as lines of CSS, each
  named with the `prefix` and the role, and indented by `indent`."
  [m prefix indent]
  (for [[role v] (sort-by key m)]
    (str indent prefix (name role) ": " v ";")))

(defn- light-dark
  "Each role of the light and dark `colours` as a value of light-dark(), in
  which the function `f` writes each colour."
  [{:keys [light dark]} f]
  (into {} (for [role (keys light)]
             [role (str "light-dark(" (f (light role)) ", " (f (dark role)) ")")])))

(defn- stylesheet
  "The colours or tokens in `levels`, a map of :standard and :more, as CSS, with
  the function `f` writing each one, by the `opts` of css."
  [levels f {:keys [selector prefix] :or {selector ":root" prefix "--"} :as opts}]
  (let [block (fn [lines indent]
                (str indent selector " {\n" (str/join "\n" lines) "\n" indent "}"))]
    (str (block (concat ["  color-scheme: light dark;"
                         (str "  accent-color: var(" prefix "accent);")]
                        (declarations (light-dark (:standard levels) f) prefix "  "))
                "")
         "\n\n@media (prefers-contrast: more) {\n"
         (block (declarations (light-dark (:more levels) f) prefix "    ") "  ")
         "\n}\n\n@media (forced-colors: active) {\n"
         (block (declarations system-colours prefix "    ") "  ")
         "\n}\n")))

(defn css
  "The `theme` as CSS, by the `opts` :selector, by default :root, and
  :prefix, of the custom properties, by default --. It holds these:

  - a custom property for each role, e.g. --background, with light-dark()
    for light and dark
  - the level :more, under prefers-contrast: more
  - system colours, under forced-colors: active
  - color-scheme, which light-dark() needs, and accent-color, which colours
    checkboxes, radio buttons and range sliders"
  ([theme]
   (css theme {}))
  ([theme opts]
   (when theme
     (stylesheet theme cc/hex opts))))

(defn relative-css
  "The `roles` as CSS that derives each one from the custom property `prop`,
  e.g. --seed, with relative colours, so that a page changes its whole theme by
  setting that one property. It takes the `opts` of css and holds the same
  blocks. A browser clips a colour outside the display's gamut, so the contrast
  can differ a little from that of from-seed's colours."
  ([prop]
   (relative-css prop roles))
  ([prop roles]
   (relative-css prop roles {}))
  ([prop roles opts]
   (stylesheet roles #(tone/relative-css % prop) opts)))

;; The Design Tokens Format Module 2025.10, sections 8.1 and 9.2
;; https://www.w3.org/community/reports/design-tokens/CG-FINAL-format-20251028/
(defn design-tokens
  "The colours of the `theme` at the `level` of contrast, by default
  :standard, as design tokens of the DTCG's format, a map with string keys
  that JSON can encode, for tools such as Style Dictionary, which write
  them for Android, iOS and Flutter."
  ([theme]
   (design-tokens theme :standard))
  ([theme level]
   (when-let [appearances (colours theme level)]
     (into {}
           (for [[appearance roles] appearances]
             [(name appearance)
              (into {}
                    (for [[role colour] roles
                          :let [{:keys [coords]} (cc/to colour :srgb)]]
                      [(name role) {"$type"  "color"
                                    "$value" {"colorSpace" "srgb"
                                              "components" (mapv #(/ (Math/round (* 1e6 (min 1.0 (max 0.0 %)))) 1e6)
                                                                 coords)
                                              "hex"        (cc/hex colour)}}]))])))))

(comment
  (def t (from-seed "#24476b"))
  (hex t)
  (println (css t))
  (println (relative-css "--seed"))
  (remove :ok? (report (from-seed "#ffd000")))
  #_.)
