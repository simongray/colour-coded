(ns dk.simongray.colour-coded.theme-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is]]
            [dk.simongray.colour-coded :as cc]
            [dk.simongray.colour-coded.contrast :as contrast]
            [dk.simongray.colour-coded.epaper :as epaper]
            [dk.simongray.colour-coded.test-data :as data]
            [dk.simongray.colour-coded.theme :as theme]))

(def seeds
  "Seeds of many hues and chromas."
  (for [h      (range 0 360 30)
        chroma [0 0.05 0.12 0.25 0.37]]
    {:space :oklch :coords [0.6 chroma h]}))

(deftest epaper-roles-land-on-their-shades
  (doseq [seed                 seeds
          :let                 [t (theme/from-seed seed theme/epaper-roles)]
          [level appearances]  (select-keys t [:standard :more])
          [appearance colours] appearances
          [role colour]        colours]
    (is (= (get-in theme/epaper-roles [level appearance role :shade])
           (epaper/shade colour))
        (str seed " " level " " appearance " " role))))

(deftest epaper-roles-contrast-on-a-panel
  (doseq [seed seeds
          :let [t (theme/from-seed seed theme/epaper-roles)]]
    (is (empty? (remove :ok? (theme/report t)))
        (str seed " on a screen"))
    (is (empty? (remove :ok? (theme/report t epaper/ratio)))
        (str seed " on Carta"))
    (is (empty? (->> (theme/report t #(epaper/ratio %1 %2 :kaleido-3))
                     (filter #(= :standard (:level %)))
                     (remove :ok?)))
        (str seed " on Kaleido 3, which can't reach the level :more"))))

(deftest epaper-roles-keep-a-seeds-hue
  (let [{:keys [background text]} (:light (theme/hex (theme/from-seed "#24476b" theme/epaper-roles)))]
    (is (= "#ffffff" background))
    (is (= "#000000" text)))
  (doseq [h    (range 0 360 30)
          :let [seed   {:space :oklch :coords [0.6 0.12 h] :alpha 1.0}
                accent (get-in (theme/from-seed seed theme/epaper-roles) [:standard :light :accent])]]
    (is (data/same-hue? 6 h (get-in (cc/to accent :oklch) [:coords 2]))
        (str "the accent keeps hue " h ", as " (cc/hex accent)))))

(deftest epaper-roles-write-css
  (let [t (theme/from-seed "#24476b" theme/epaper-roles)]
    (is (str/includes? (theme/css t) "--border: light-dark(#666666, #aaaaaa);"))
    (is (str/includes? (theme/relative-css "--seed" theme/epaper-roles) "--accent: light-dark(oklch(from var(--seed)"))))

(deftest every-pair-contrasts-whatever-the-seed
  (doseq [h      (range 0 360 10)
          chroma [0 0.05 0.12 0.25 0.37]
          :let   [seed {:space :oklch :coords [0.6 chroma h]}]]
    (is (empty? (remove :ok? (theme/report (theme/from-seed seed))))
        (str "a seed of oklch(0.6 " chroma " " h ")"))))

(deftest a-theme
  (let [t (theme/from-seed "#24476b")]
    (is (= #{:light :dark} (set (keys (theme/hex t)))))
    (is (= #{:background :surface :text :text-muted :border :divider :accent :on-accent :link :focus}
           (set (keys (:light (theme/hex t))))))
    (is (= "#ffffff" (get-in (theme/hex t) [:light :background])))
    (is (= 48 (count (theme/report t))) "twelve pairs, at two levels, in two appearances")))

(def light-and-dark-seeds
  "Seeds of many hues, chromas and lightnesses, some of them outside sRGB."
  (for [l      [0.2 0.45 0.7 0.95]
        h      (range 0 360 60)
        chroma [0 0.12 0.37]]
    {:space :oklch :coords [l chroma h]}))

(deftest a-kept-seed-contrasts-whatever-the-seed
  (doseq [seed light-and-dark-seeds]
    (is (empty? (remove :ok? (theme/report (theme/keep-seed (theme/from-seed seed)))))
        (str seed))))

(deftest a-seed-with-the-contrast-stays-the-accent
  (let [t     (theme/keep-seed (theme/from-seed "#3498db"))
        roles #(select-keys % [:accent :on-accent])]
    (is (= {:accent "#3498db" :on-accent "#000000"} (roles (:light (theme/hex t)))))
    (is (= {:accent "#3498db" :on-accent "#000000"} (roles (:dark (theme/hex t)))))
    (is (= "#ffffff" (get-in (theme/hex t :more) [:light :on-accent]))
        "no text has 7:1 on it, so it darkens at the level :more, and takes white text")))

(deftest a-seed-without-the-contrast-moves-as-little-as-it-can
  (let [t      (theme/keep-seed (theme/from-seed "#ffd000"))
        accent (get-in t [:standard :light :accent])
        hue    #(get-in (cc/to % :lch) [:coords 2])]
    (is (< 3 (contrast/ratio accent "white") 3.1) "to just past 3:1 with the page")
    (is (data/same-hue? 1e-6 (hue "#ffd000") (hue accent)))
    (is (= "#ffd000" (get-in (theme/hex t) [:dark :accent])) "and not at all in the dark")))

(deftest keeping-the-seed-leaves-the-other-roles
  (let [t      (theme/from-seed "#24476b")
        others (fn [theme level]
                 (update-vals (get theme level) #(dissoc % :accent :on-accent)))]
    (doseq [level [:standard :more]]
      (is (= (others t level) (others (theme/keep-seed t) level))))
    (is (= (:seed t) (:seed (theme/keep-seed t))))))

(deftest css
  (let [s (theme/css (theme/from-seed "#24476b"))]
    (is (str/includes? s "color-scheme: light dark;"))
    (is (re-find #"--text: light-dark\(#[0-9a-f]{6}, #[0-9a-f]{6}\);" s))
    (is (str/includes? s "@media (prefers-contrast: more)"))
    (is (str/includes? s "--background: Canvas;")))
  (is (str/includes? (theme/css (theme/from-seed "red") {:selector ".app" :prefix "--cc-"}) ".app {\n  color-scheme"))
  (is (str/includes? (theme/relative-css "--seed") "--accent: light-dark(lch(from var(--seed) 40 min(c, 60) h)")))

(deftest from-an-image
  (let [pixels (concat (repeat 60 [255 255 255 255]) (repeat 20 [0x24 0x47 0x6b 255]))]
    (is (= (cc/hex (:seed (theme/from-pixels pixels))) "#24476b")
        "the first colour with a hue, past the white")
    (is (some? (theme/from-pixels (repeat 10 [128 128 128 255]))) "or a grey")))

(deftest design-tokens
  (let [tokens (theme/design-tokens (theme/from-seed "#24476b"))]
    (is (= {"$type" "color" "$value" {"colorSpace" "srgb" "components" [1.0 1.0 1.0] "hex" "#ffffff"}}
           (get-in tokens ["light" "background"])))))
