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

(def fallback
  "The grey that from-pixels takes as the seed of an image without an
  opaque pixel."
  {:space :srgb :coords [0.5 0.5 0.5] :alpha 1.0})

(defn from-pixels
  "The theme of the image whose `pixels` are vectors of red, green, blue and
  alpha from 0 to 255, with the `roles` of from-seed. Its seed is the
  image's commonest colour with a hue, by choose/seed, or else its
  commonest colour."
  ([pixels]
   (from-pixels pixels roles))
  ([pixels roles]
   (let [palette (map :colour (quantise/buckets pixels 5))]
     (from-seed (or (choose/seed palette) (first palette) fallback) roles))))

(defn from-image
  "The theme of the image in the bytes `b`, e.g. a podcast's artwork, with
  the `roles` of from-seed: from-pixels of the pixels that image/pixels
  decodes. On the JVM it returns the theme, and in ClojureScript a promise
  of it, which is nil where the platform can't decode the image."
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
   (update-vals (colours theme level) #(update-vals % cc/hex))))

(defn report
  "Each pair of roles of the `theme` that must contrast, at each level and
  in each appearance, as a map of these:

  - :roles, the pair
  - :level and :appearance
  - :ratio, and :wants, the least that it should be
  - :ok?, whether it is"
  [theme]
  (for [[level pairs]          requirements
        [appearance colours]   (get theme level)
        [a b wants]            pairs
        :when                  (and (colours a) (colours b))
        :let                   [r (contrast/ratio (colours a) (colours b))]]
    {:roles      [a b]
     :level      level
     :appearance appearance
     :ratio      r
     :wants      wants
     :ok?        (>= r wants)}))

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
  ([theme {:keys [selector prefix] :or {selector ":root" prefix "--"} :as opts}]
   (let [block (fn [lines indent]
                 (str indent selector " {\n" (str/join "\n" lines) "\n" indent "}"))]
     (str (block (concat ["  color-scheme: light dark;"
                          (str "  accent-color: var(" prefix "accent);")]
                         (declarations (light-dark (:standard theme) cc/hex) prefix "  "))
                 "")
          "\n\n@media (prefers-contrast: more) {\n"
          (block (declarations (light-dark (:more theme) cc/hex) prefix "    ") "  ")
          "\n}\n\n@media (forced-colors: active) {\n"
          (block (declarations system-colours prefix "    ") "  ")
          "\n}\n"))))

(defn relative-css
  "The `roles` as CSS that derives each from the custom property `prop`,
  e.g. --seed, with relative colours, so that a page changes its whole
  theme by setting that one property. A browser clips a colour outside the
  display's gamut, so the contrast can differ a little from that of
  from-seed's colours."
  ([prop]
   (relative-css prop roles))
  ([prop roles]
   (let [declare #(declarations (light-dark (get roles %) (fn [token] (tone/relative-css token prop)))
                                "--" %2)]
     (str ":root {\n  color-scheme: light dark;\n  accent-color: var(--accent);\n"
          (str/join "\n" (declare :standard "  "))
          "\n}\n\n@media (prefers-contrast: more) {\n  :root {\n"
          (str/join "\n" (declare :more "    "))
          "\n  }\n}\n"))))

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
   (into {}
         (for [[appearance roles] (colours theme level)]
           [(name appearance)
            (into {}
                  (for [[role colour] roles
                        :let [{:keys [coords]} (cc/to colour :srgb)]]
                    [(name role) {"$type"  "color"
                                  "$value" {"colorSpace" "srgb"
                                            "components" (mapv #(/ (Math/round (* 1e6 (min 1.0 (max 0.0 %)))) 1e6)
                                                               coords)
                                            "hex"        (cc/hex colour)}}]))]))))

(comment
  (def t (from-seed "#24476b"))
  (hex t)
  (println (css t))
  (println (relative-css "--seed"))
  (remove :ok? (report (from-seed "#ffd000")))
  #_.)
