(ns dk.simongray.colour-coded.api-test
  "Tests of the rules that every namespace keeps to, which the root namespace's
  docstring states."
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [dk.simongray.colour-coded :as cc]
            [dk.simongray.colour-coded.choose :as choose]
            [dk.simongray.colour-coded.contrast :as contrast]
            [dk.simongray.colour-coded.difference :as difference]
            [dk.simongray.colour-coded.epaper :as epaper]
            [dk.simongray.colour-coded.gamut :as gamut]
            [dk.simongray.colour-coded.greyscale :as greyscale]
            [dk.simongray.colour-coded.harmony :as harmony]
            [dk.simongray.colour-coded.mix :as mix]
            [dk.simongray.colour-coded.quantise :as quantise]
            [dk.simongray.colour-coded.syntax :as syntax]
            [dk.simongray.colour-coded.target :as target]
            [dk.simongray.colour-coded.theme :as theme]
            [dk.simongray.colour-coded.tone :as tone]
            [dk.simongray.colour-coded.vision :as vision]))

(def not-colours
  "Things that aren't colours, of each sort that a caller might pass."
  ["not a colour" "" nil 5 {} [1 2 3]
   {:space :nope :coords [0 0 0]}
   {:space :srgb}
   {:space {} :coords []}
   {:space :srgb :coords []}
   {:space :srgb :coords [1]}
   {:space :srgb :coords ["a" "b" "c"]}
   {:space :srgb :coords [0 0 0] :alpha "opaque"}])

(def functions-of-a-colour
  "Each function that takes a colour, by name, with its other arguments filled
  in."
  {"cc/colour"               cc/colour
   "cc/to"                   #(cc/to % :srgb)
   "cc/components"           cc/components
   "cc/with"                 #(cc/with % :oklch {:l 0.5})
   "cc/css"                  cc/css
   "cc/hex"                  cc/hex
   "gamut/clip"              gamut/clip
   "gamut/to-gamut"          gamut/to-gamut
   "gamut/reduce-chroma"     gamut/reduce-chroma
   "contrast/luminance"      contrast/luminance
   "contrast/ratio a"        #(contrast/ratio % "white")
   "contrast/ratio b"        #(contrast/ratio "white" %)
   "contrast/wcag-level a"   #(contrast/wcag-level % "white")
   "contrast/wcag-level b"   #(contrast/wcag-level "white" %)
   "contrast/black-or-white" contrast/black-or-white
   "difference/delta-e a"    #(difference/delta-e % "white")
   "difference/delta-e b"    #(difference/delta-e "white" %)
   "difference/delta-e-ok2"  #(difference/delta-e-ok2 % "white")
   "difference/delta-e-76"   #(difference/delta-e-76 % "white")
   "difference/delta-e-2000" #(difference/delta-e-2000 % "white")
   "difference/nearest"      #(difference/nearest ["red"] %)
   "choose/chroma"           choose/chroma
   "choose/first-with-contrast background" #(choose/first-with-contrast ["red"] %)
   "choose/most-contrast background"       #(choose/most-contrast ["red"] %)
   "mix/interpolate a"       #(mix/interpolate % "red")
   "mix/interpolate b"       #(mix/interpolate "red" %)
   "mix/mix a"               #(mix/mix % "red")
   "mix/mix b"               #(mix/mix "red" %)
   "mix/steps"               #(mix/steps % "red" 3)
   "harmony/turn"            #(harmony/turn % 30)
   "harmony/turns"           #(harmony/turns % [30])
   "harmony/complement"      harmony/complement
   "harmony/analogous"       harmony/analogous
   "harmony/split-complement" harmony/split-complement
   "harmony/triad"           harmony/triad
   "harmony/square"          harmony/square
   "harmony/wheel"           #(harmony/wheel % 3)
   "tone/unmapped"           #(tone/unmapped {:l 0.5} %)
   "tone/tone"               #(tone/tone {:l 0.5} %)
   "tone/tone on a shade"    #(tone/tone {:shade 5} %)
   "tone/tones"              #(tone/tones {:a {:l 0.5}} %)
   "target/at-contrast x"    #(target/at-contrast % "white")
   "target/at-contrast background" #(target/at-contrast "red" %)
   "target/scale"            #(target/scale % "white")
   "vision/simulate"         #(vision/simulate % :protan)
   "greyscale/values"        greyscale/values
   "greyscale/value"         greyscale/value
   "greyscale/grey"          greyscale/grey
   "epaper/landings"         epaper/landings
   "epaper/shades"           epaper/shades
   "epaper/shade"            epaper/shade
   "epaper/at-shade"         #(epaper/at-shade % 5)
   "epaper/simulate"         epaper/simulate
   "epaper/ratio a"          #(epaper/ratio % "white")
   "epaper/ratio b"          #(epaper/ratio "white" %)
   "theme/from-seed"         theme/from-seed})

(deftest a-function-of-a-colour-returns-nil-for-anything-else
  (doseq [[name f] functions-of-a-colour
          x        not-colours]
    (is (nil? (f x)) (str name " of " (pr-str x))))
  (testing "serialise, which takes only a map"
    (is (= [nil nil nil nil] (map syntax/serialise ["red" nil 5 {:space :nope :coords [0 0 0]}]))))
  (testing "a theme of no colour"
    (let [nothing (theme/from-seed "not a colour")]
      (is (= [nil nil nil nil nil]
             [(theme/colours nothing) (theme/hex nothing) (theme/css nothing)
              (theme/design-tokens nothing) (theme/report nothing)])))))

(deftest a-question-about-a-colour-answers-false-for-anything-else
  (doseq [[name f] {"cc/colour?"           cc/colour?
                    "gamut/in-gamut?"      gamut/in-gamut?
                    "gamut/in-gamut? of an unbounded space" #(gamut/in-gamut? % :oklab)
                    "choose/chromatic?"    choose/chromatic?
                    "epaper/same-grey?"    #(epaper/same-grey? % "red")}
          x        not-colours]
    (is (false? (f x)) (str name " of " (pr-str x)))))

(deftest a-name-that-does-not-exist-returns-nil
  (testing "a space"
    (is (nil? (cc/to "red" :nope)))
    (is (nil? (cc/components "red" :nope)))
    (is (nil? (cc/with "red" :nope {:l 0.5})))
    (is (nil? (gamut/clip "red" :nope)))
    (is (nil? (gamut/to-gamut "red" :nope)))
    (is (nil? (gamut/reduce-chroma "red" :nope)))
    (is (false? (gamut/in-gamut? "red" :nope)))
    (is (nil? (mix/mix "red" "blue" 0.5 :nope)))
    (is (nil? (mix/steps "red" "blue" 3 :nope)))
    (is (nil? (harmony/turn "red" 30 :nope)))
    (is (nil? (harmony/turns "red" [30] :nope))))
  (testing "a space without a hue, where a hue has to turn"
    (is (nil? (harmony/turn "red" 30 :srgb)))
    (is (nil? (harmony/complement "red" :srgb))))
  (testing "a space without a chroma, where a chroma has to lower"
    (is (nil? (gamut/reduce-chroma "red" :srgb)))
    (is (nil? (gamut/reduce-chroma "red" :hsl))))
  (testing "a token that has no lightness or no chroma to take"
    (doseq [token [{} {:shade 16} {:space :nope :l 0.5} {:space :srgb :l 0.5} {:space :hsl :l 50}]]
      (is (nil? (tone/lightness token)) (pr-str token))
      (is (nil? (tone/unmapped token "red")) (pr-str token))
      (is (nil? (tone/tone token "red")) (pr-str token))
      (is (nil? (tone/relative-css token "--seed")) (pr-str token))
      (is (nil? (tone/worst-contrast token {:l 0.9})) (pr-str token))
      (is (nil? (tone/worst-contrast {:l 0.9} token)) (pr-str token))))
  (testing "a component"
    (is (nil? (cc/with "red" :srgb {:l 0.5})))
    (is (nil? (cc/with "red" :oklch {:nope 1}))))
  (testing "a way for a hue to turn"
    (is (nil? (mix/mix "red" "blue" 0.5 :oklch :nope))))
  (testing "a deficiency"
    (is (nil? (vision/matrix :nope 1.0)))
    (is (nil? (vision/simulate "red" :nope))))
  (testing "a conversion, even beside one that exists"
    (is (nil? (greyscale/value "red" :nope)))
    (is (nil? (greyscale/grey "red" :nope)))
    (is (nil? (greyscale/values "red" [:pdf :nope])))
    (is (nil? (epaper/landings "red" [:pdf :nope])))
    (is (nil? (epaper/shades "red" [:pdf :nope])))
    (is (nil? (epaper/shade "#555555" [:pdf :nope])))
    (is (false? (epaper/same-grey? "#555555" "#555555" [:pdf :nope])))
    (is (nil? (epaper/at-shade "red" 5 [:pdf :nope])))
    (is (nil? (epaper/least-gap ["red" "blue"] [:pdf :nope])))
    (is (nil? (epaper/palette epaper/hues [:pdf :nope])))
    (is (nil? (epaper/report ["red"] [:pdf :nope])))
    (is (nil? (epaper/simulate "red" :carta :nope)))
    (is (nil? (epaper/simulate "red" :kaleido-3 :nope)) "though a colour panel uses no conversion")
    (is (nil? (epaper/ratio "red" "white" :carta :nope))))
  (testing "a panel"
    (is (nil? (epaper/simulate "red" :nope)))
    (is (nil? (epaper/ratio "red" "white" :nope))))
  (testing "a level of contrast"
    (let [t (theme/from-seed "#24476b")]
      (is (= [nil nil nil] [(theme/colours t :nope) (theme/hex t :nope) (theme/design-tokens t :nope)])))))

(deftest a-function-of-a-list-skips-what-is-not-a-colour
  (let [clean    ["red" "blue" "#808080" "#ffff00"]
        with-bad (interpose "not a colour" clean)]
    (is (= (difference/least clean) (difference/least with-bad)))
    (is (= "red" (difference/nearest with-bad "red")))
    (is (= "red" (choose/seed ["not a colour" nil "red"])))
    (is (= "red" (choose/first-with-contrast ["not a colour" nil "red"] "white" 3)))
    (is (= "blue" (choose/most-contrast ["not a colour" nil "blue"] "white")))
    (is (= (choose/spread clean) (choose/spread with-bad)))
    (is (= (vision/least-difference clean) (vision/least-difference with-bad)))
    (is (= (epaper/least-gap clean) (epaper/least-gap with-bad)))
    (is (= (choose/scheme clean) (choose/scheme with-bad)))
    (testing "a hue with no colour on its shade"
      (let [good (first epaper/hues)]
        (is (= (epaper/palette [good])
               (epaper/palette [good (assoc good :colour "not a colour") (assoc good :shade 16)])))))
    (testing "nothing left to choose from"
      (is (nil? (choose/seed ["not a colour"])))
      (is (nil? (choose/most-contrast ["not a colour"] "white")))
      (is (nil? (choose/scheme ["not a colour"])))
      (is (= [] (choose/spread ["not a colour"])))
      (is (nil? (difference/least ["not a colour" "red"]))))))

(deftest a-report-fails-what-is-not-a-colour
  (testing "a list of colours"
    (let [[check] (epaper/report ["#555555" "#aaaaaa" "not a colour"])]
      (is (= {:check :on-a-shade :value 2 :wants 3 :ok? false} check))))
  (testing "a role of a theme"
    (let [t      (assoc-in (theme/from-seed "#24476b") [:standard :light :text] "not a colour")
          failed (remove :ok? (theme/report t))]
      (is (= #{[:text :background] [:text :surface]} (set (map :roles failed))))
      (is (every? (comp nil? :ratio) failed)))))

(deftest a-quantiser-finds-no-colours-in-no-opaque-pixels
  (doseq [pixels [[] [[1 2 3 0]]]]
    (is (= [] (quantise/buckets pixels)))
    (is (= [] (quantise/k-means pixels))))
  (is (nil? (theme/from-pixels [[1 2 3 0]])) "so an image like that has no theme"))

(deftest optional-arguments-default-to-the-general-case
  (testing "halfway, as in color-mix()"
    (is (= (mix/mix "red" "blue" 0.5) (mix/mix "red" "blue"))))
  (testing "OKLCH, as harmony turns hues"
    (is (= (gamut/reduce-chroma "oklch(70% 0.4 140)" :oklch)
           (gamut/reduce-chroma "oklch(70% 0.4 140)"))))
  (testing "the contrast that WCAG asks of text"
    (is (= (target/at-contrast "red" "white" 4.5) (target/at-contrast "red" "white")))
    (is (= (choose/first-with-contrast ["#999999" "#222222"] "white" 4.5)
           (choose/first-with-contrast ["#999999" "#222222"] "white"))))
  (testing "a ramp of ratios"
    (is (= (target/scale "red" "white" [1.5 3 4.5 7]) (target/scale "red" "white"))))
  (testing "colours clearly apart"
    (is (= (choose/spread ["red" "#ff0a00" "blue"] 0.1)
           (choose/spread ["red" "#ff0a00" "blue"]))))
  (testing "five colours of an image"
    (let [pixels (for [r (range 0 256 16)] [r 0 0 255])]
      (is (= (quantise/buckets pixels 5) (quantise/buckets pixels)))
      (is (= (quantise/k-means pixels 5) (quantise/k-means pixels)))))
  (testing "the most widely shared conversion"
    (is (= (greyscale/value "red" greyscale/default-conversion) (greyscale/value "red")))
    (is (= (greyscale/grey "red" greyscale/default-conversion) (greyscale/grey "red")))
    (is (= (epaper/simulate "red" :carta greyscale/default-conversion) (epaper/simulate "red"))))
  (testing "a monochrome panel"
    (is (= (epaper/ratio "red" "white" :carta) (epaper/ratio "red" "white")))
    (is (= (epaper/ratio "red" "white" :carta greyscale/default-conversion) (epaper/ratio "red" "white"))))
  (testing "a conversion that shows differently"
    (is (not= (epaper/ratio "red" "white" :carta :icc) (epaper/ratio "red" "white" :carta :rec709)))))

(deftest both-kinds-of-css-take-the-same-options-and-say-the-same-things
  (let [t    (theme/from-seed "#24476b")
        opts {:selector ".card" :prefix "--x-"}]
    (doseq [written [(theme/css t opts) (theme/relative-css "--seed" theme/roles opts)]]
      (is (str/includes? written ".card {"))
      (is (str/includes? written "--x-accent: light-dark("))
      (is (str/includes? written "accent-color: var(--x-accent);"))
      (is (str/includes? written "@media (prefers-contrast: more)"))
      (is (str/includes? written "@media (forced-colors: active)"))
      (is (not (str/includes? written ":root"))))))
