(ns dk.simongray.colour-coded.theme-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is]]
            [dk.simongray.colour-coded :as cc]
            [dk.simongray.colour-coded.theme :as theme]))

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
