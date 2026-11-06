(ns dk.simongray.colour-coded.epaper-test
  (:require [clojure.test :refer [deftest is testing]]
            [dk.simongray.colour-coded :as cc]
            [dk.simongray.colour-coded.choose :as choose]
            [dk.simongray.colour-coded.difference :as difference]
            [dk.simongray.colour-coded.epaper :as epaper]
            [dk.simongray.colour-coded.gamut :as gamut]
            [dk.simongray.colour-coded.test-data :as data]
            [dk.simongray.colour-coded.vision :as vision]))

(deftest what-a-shade-is
  (is (every? epaper/shade? (range 16)))
  (is (epaper/shade? 5.0) "the same on the JVM and in Node")
  (doseq [n [16 -1 5.5 nil "5" ##Inf]]
    (is (false? (epaper/shade? n)) n)))

(deftest the-grey-of-a-shade
  (is (= "#000000" (cc/hex (epaper/shade->grey 0))))
  (is (= "#555555" (cc/hex (epaper/shade->grey 5))))
  (is (= "#ffffff" (cc/hex (epaper/shade->grey 15))))
  (doseq [n (range 16)]
    (is (= n (epaper/shade (epaper/shade->grey n))) (str "shade " n)))
  (is (nil? (epaper/shade->grey 16))))

(defn- flat
  "The grey of the byte `b`, which needn't be a whole number."
  [b]
  (let [v (/ b 255.0)]
    {:space :srgb :coords [v v v] :alpha 1.0}))

(deftest greys-between-shades
  (testing "shade 5 starts at 80, by its top bits, and ends at 93.5, by rounding"
    (is (= #{5} (epaper/shades (flat 80.5) [:pdf])))
    (is (= #{5} (epaper/shades (flat 93.4) [:pdf])))
    (is (= #{4 5} (epaper/shades (flat 79.5) [:pdf])))
    (is (= #{5 6} (epaper/shades (flat 93.6) [:pdf]))))
  (testing "a grey can only land on neighbouring shades"
    (doseq [b (range 0 255.01 0.25)
            :let [ss (epaper/shades (flat b) [:pdf])]]
      (is (<= (- (apply max ss) (apply min ss)) 1) b))))

(deftest shades-of-greys
  (is (= #{5} (epaper/shades "#555555")))
  (is (= #{4 5} (epaper/shades "#505050")) "80 can floor to 79, whose top bits are 4")
  (is (= #{0 1} (epaper/shades "#101010")) "and 16 to 15")
  (is (= #{4 5} (epaper/shades "#4f4f4f")) "79 rounds to 5, but its top bits are 4")
  (is (= #{15} (epaper/shades "#ffffff"))))

(deftest flooring-to-8-bits-counts
  (testing "greys just over the next shade's edge, which floor back below it"
    (is (= #{9 10} (epaper/shades "#88aabb")) "161.7 by the PDF engines' weights floors to 161")
    (is (= #{8 9} (epaper/shades "#8791a0")))))

(deftest no-conversions
  (is (nil? (epaper/shade "red" [])))
  (is (nil? (epaper/shades "red" [])))
  (is (nil? (epaper/at-shade "red" 5 [])))
  (is (false? (epaper/same-grey? "red" "blue" [])))
  (is (= [] (epaper/palette epaper/hues []))))

(deftest no-such-panel
  (doseq [panel [:nope nil {:saturation 0.0} {:contrast 15.0} {:contrast 0 :saturation 0}]]
    (is (nil? (epaper/simulate "red" panel)) panel)
    (is (nil? (epaper/ratio "red" "white" panel)) panel))
  (is (some? (epaper/simulate "red" {:contrast 15 :saturation 0})) "whole numbers are fine"))

(deftest no-such-shade
  (doseq [n [16 -1 5.5 nil "5" ##Inf]]
    (is (nil? (epaper/at-shade "red" n)) n))
  (testing "a whole number that's a double means the same on the JVM and in Node"
    (is (= (cc/hex (epaper/at-shade "red" 5)) (cc/hex (epaper/at-shade "red" 5.0))))))

(deftest colours-that-shift-have-no-shade
  (is (= #{5 6} (epaper/shades "#0072b2")))
  (is (nil? (epaper/shade "#0072b2")))
  (is (= #{5 6 7 8} (epaper/shades "#ff3300")))
  (is (nil? (epaper/shade "#00ff00"))))

(deftest rockchip-only-truncates
  (testing "#953a63 by 11/16/5 is 95.7, which rounds to 6 but which that path never rounds"
    (is (= 5 (epaper/shade "#953a63")))))

(defn- from-byte
  "The shades that a grey's byte `g` lands on by rounding and by its top bits."
  [g]
  (hash-set (quot g 16) (long (Math/round (/ g 17.0)))))

(deftest a-shade-holds-in-real-engines
  (let [{:strs [colours engines]} data/greyscale]
    (doseq [[engine {:strs [greys]}] engines
            [colour grey]            (map vector colours greys)
            :let                     [n (epaper/shade colour)]
            :when                    n]
      (is (= #{n} (from-byte grey)) (str colour " on shade " n " in " engine)))))

(def even-colours
  "The colours whose channels each lie on one of 16 even values, which cover
  sRGB well enough to test against."
  (for [r (range 16) g (range 16) b (range 16)]
    {:space :srgb :coords [(/ r 15.0) (/ g 15.0) (/ b 15.0)] :alpha 1.0}))

(defn- channels
  "The red, green and blue of the colour `x` as bytes."
  [x]
  (map #(long (Math/round (* 255.0 %))) (:coords x)))

;; these restate each engine's integers, which reproduce the fixture's greys
(deftest a-shade-holds-by-the-engines-integers
  (doseq [x (filter epaper/shade even-colours)
          :let [n       (epaper/shade x)
                [r g b] (channels x)]]
    (is (= #{n} (from-byte (quot (+ (* 30 r) (* 59 g) (* 11 b)) 100)))
        (str (cc/hex x) " in PDFium"))
    (is (= #{n} (from-byte (bit-shift-right (+ (* 19595 r) (* 38470 g) (* 7471 b) 0x8000) 16)))
        (str (cc/hex x) " in Pillow"))
    (is (= n (bit-shift-right (+ (* 11 r) (* 16 g) (* 5 b)) 9))
        (str (cc/hex x) " in Rockchip's 4-bit path"))))

;; colours picked by eye for the figures of a book on bouldering, as they stood
;; when this was written, as a check on colours that people chose
(deftest colours-chosen-by-eye-land-on-their-shades
  (doseq [[n xs] {3  ["#6b211b"]
                  4  ["#58328a"]
                  5  ["#346841" "#953a63" "#325a94" "#6a5b00"
                      "#9c3d30" "#266666" "#654496" "#944600"]
                  6  ["#3b69ae"]
                  9  ["#e98421"]
                  11 ["#d7be2e"]
                  12 ["#a2dea2"]}
          x      xs]
    (is (= n (epaper/shade x)) x)))

(deftest keeping-a-drawings-greys
  (testing "colours for greys off the even values, from that book's figures"
    (doseq [[x grey] {"#f9cd80" "#d4d4d4"
                      "#edad4b" "#b4b4b4"
                      "#d7732b" "#8d8d8d"}]
      (is (epaper/same-grey? x grey) (str x " for " grey))))
  (testing "colours that share the shades of a grey, but not by the same rule"
    (is (= (epaper/shades "#f7d37b") (epaper/shades "#d4d4d4")))
    (is (not (epaper/same-grey? "#f7d37b" "#d4d4d4"))))
  (testing "colours one step away, which can floor or round past their grey"
    (is (not (epaper/same-grey? "#edac4b" "#b4b4b4")))
    (is (not (epaper/same-grey? "#d8732b" "#8d8d8d"))))
  (is (not (epaper/same-grey? "#325a94" "#666666")))
  (is (epaper/same-grey? "#325a94" "#555555"))
  (is (epaper/same-grey? "#d4d4d4" "#d4d4d4")))

(deftest at-shade-lands-there
  (doseq [n [2 5 8 12 13]
          h (range 0 360 45)
          :let [x (epaper/at-shade {:space :oklch :coords [0.5 0.4 h] :alpha 1.0} n)]]
    (is (= n (epaper/shade x)) (str "hue " h " on shade " n))
    (is (= x (cc/colour (cc/hex x))) "survives 8 bits exactly")))

(deftest at-shade-keeps-the-hue
  (let [hue #(get-in (cc/to % :oklch) [:coords 2])]
    (doseq [h [30 150 258 300]]
      (let [x (epaper/at-shade {:space :oklch :coords [0.5 0.4 h] :alpha 1.0} 5)]
        (is (data/same-hue? 3 h (hue x)) (str "hue " h))
        (is (> (choose/chroma x) 0.05) (str "hue " h))))))

(deftest at-shade-takes-only-the-colours-chroma
  (let [x (epaper/at-shade "#325a94" 5)]
    (is (= "#325a94" (cc/hex x)) "already there")
    (is (<= (choose/chroma (epaper/at-shade "oklch(50% 0.03 258)" 5)) 0.035)
        "within what 8 bits can do")))

(deftest at-shade-of-a-grey
  (is (= "#555555" (cc/hex (epaper/at-shade "grey" 5))))
  (is (= 0 (epaper/shade (epaper/at-shade "red" 0))))
  (is (= "#000000" (cc/hex (epaper/at-shade "black" 0)))))

(deftest at-shade-finds-the-chroma-there-is
  (testing "more than one lightness per chroma: #5b1d72 lands here at 0.144"
    (is (>= (choose/chroma (epaper/at-shade "oklch(50% 0.4 315)" 3)) 0.14)))
  (testing "about as much as a search of every lightness finds"
    (doseq [n [3 9]
            h [0 120 258 315]]
      (let [found (choose/chroma (epaper/at-shade {:space :oklch :coords [0.5 0.4 h]} n))
            most  (first (for [c (range 0.3 0 -0.01)
                               l (range 0.1 1 0.01)
                               :let [x {:space :oklch :coords [l c h] :alpha 1.0}]
                               :when (and (gamut/in-gamut? x) (= n (epaper/shade x)))]
                           c))]
        (is (<= (or most 0) (+ found 0.02)) (str "hue " h " on shade " n))))))

(deftest a-known-device-allows-more-chroma
  (let [x       "oklch(50% 0.4 12)"
        generic (epaper/at-shade x 5)
        known   (epaper/at-shade x 5 [:pdf])]
    (is (> (choose/chroma known) (choose/chroma generic)))
    (is (= 5 (epaper/shade known [:pdf])))
    (is (= 5 (epaper/shade generic)))
    (is (nil? (epaper/shade known)) "which only holds on that device")))

(deftest a-shade-by-some-of-the-conversions
  (is (= #{5} (epaper/shades "#0072b2" [:pdf])))
  (is (= 5 (epaper/shade "#0072b2" [:pdf])))
  (is (= [:pdf] (keys (epaper/landings "#0072b2" [:pdf]))))
  (is (epaper/same-grey? "#0072b2" "#555555" [:pdf]))
  (is (not (epaper/same-grey? "#0072b2" "#555555")))
  (is (= 2 (epaper/least-gap ["#0072b2" "#777777"] [:pdf])))
  (is (= 1 (epaper/least-gap ["#0072b2" "#777777"]))))

(deftest at-shade-keeps-alpha
  (is (= 0.5 (:alpha (epaper/at-shade "rgb(0 80 200 / 50%)" 5)))))

(deftest hues-on-a-shade-tell-apart
  (let [xs (map #(epaper/at-shade {:space :oklch :coords [0.5 0.12 %] :alpha 1.0} 5)
                [30 150 258 300])]
    (is (= 0 (epaper/least-gap xs)) "the same grey")
    (is (= #{5} (reduce into #{} (map epaper/shades xs))))
    (is (> (difference/least xs) 0.05) "but different colours")
    (is (every? #(> % 0.02) (vals (vision/least-difference xs))))))

(deftest gaps
  (is (= 1 (epaper/least-gap ["#325a94" "#f2c55f" "#a3bcd9"])) "shades 5, 12 and 11")
  (is (= 7 (epaper/least-gap ["#325a94" "#f2c55f"])))
  (is (= 0 (epaper/least-gap ["#0072b2" "#006699"])) "they can land together")
  (is (nil? (epaper/least-gap ["#000000"])))
  (is (nil? (epaper/least-gap []))))

(deftest simulating-a-monochrome-panel
  (testing "white stays white, and black has the panel's contrast"
    (is (data/close? 1e-9 [1 1 1] (:coords (epaper/simulate "white" :carta))))
    (is (data/close? 1e-9 (repeat 3 (/ 1 15.0)) (:coords (epaper/simulate "black" :carta)))))
  (testing "a colour becomes the grey of its shade"
    (let [[r g b] (:coords (epaper/simulate "#325a94" :carta))]
      (is (= r g b))
      (is (data/close? 1e-9 (:coords (epaper/simulate "#555555" :carta))
                       (:coords (epaper/simulate "#325a94" :carta))))))
  (testing "the conversion decides for a colour without a shade"
    (is (not= (:coords (epaper/simulate "#ff0000" :carta :pdf))
              (:coords (epaper/simulate "#ff0000" :carta :icc)))))
  (testing "a conversion that only truncates does so here too"
    (is (= (:coords (epaper/simulate "#555555"))
           (:coords (epaper/simulate "#953a63" :carta :rockchip)))))
  (is (= 0.5 (:alpha (epaper/simulate "rgb(255 0 0 / 50%)" :carta)))))

(deftest simulating-a-colour-panel
  (testing "the hue stays but the chroma falls"
    (let [x "#325a94"
          y (epaper/simulate x :kaleido-3)]
      (is (data/same-hue? 15
                          (get-in (cc/to x :oklch) [:coords 2])
                          (get-in (cc/to y :oklch) [:coords 2])))
      (is (< (choose/chroma y) (* 0.6 (choose/chroma x))))))
  (testing "a grey stays a grey"
    (let [[r g b] (:coords (epaper/simulate "#555555" :kaleido-3))]
      (is (data/close? 1e-9 r g))
      (is (data/close? 1e-9 g b))))
  (testing "a panel can be a plain map"
    (is (data/close? 1e-9 [1 1 1] (:coords (epaper/simulate "white" {:contrast 5.0 :saturation 1.0}))))
    (is (data/close? 1e-9 (repeat 3 0.2) (:coords (epaper/simulate "black" {:contrast 5.0 :saturation 1.0}))))))

(deftest ratios
  (is (data/close? 1e-3 9.0 (epaper/ratio "black" "white" :carta)))
  (is (< (epaper/ratio "black" "white" :kaleido-3) 7))
  (is (> (epaper/ratio "#555555" "white" :carta) 4.5))
  (is (< (epaper/ratio "#555555" "white" :kaleido-3) 4.5)))

(deftest the-palette
  (let [p (epaper/palette)]
    (is (= [:blue :yellow :red :teal] (map :name p)) "in the order to use them")
    (testing "each colour lands on its shade, and each highlight on shade 13"
      (doseq [{:keys [name shade colour highlight]} p]
        (is (= shade (epaper/shade colour)) name)
        (is (= 13 (epaper/shade highlight)) name)))
    (testing "any number from the front stay apart"
      (doseq [n (range 1 (inc (count p)))]
        (is (empty? (remove :ok? (epaper/report (map :colour (take n p)))))
            (str "the first " n))))
    (testing "black text reads on a highlight on either panel"
      (doseq [{:keys [name highlight]} p
              panel [:carta :kaleido-3]]
        (is (>= (epaper/ratio "black" highlight panel) 4.5) (str name " on " panel))))
    (testing "the highlights keep a hue on a colour panel"
      (doseq [{:keys [name highlight]} p]
        (is (> (choose/chroma (epaper/simulate highlight :kaleido-3)) 0.02) name)))
    (testing "the first holds on white as text, and the third as a line"
      (doseq [panel [:carta :kaleido-3]]
        (is (>= (epaper/ratio (:colour (p 0)) "white" panel) 4.5) panel)
        (is (>= (epaper/ratio (:colour (p 2)) "white" panel) 3) panel)))
    (testing "they keep their hues"
      (doseq [[{:keys [name colour]} hue] (map vector p [255 84 12 195])]
        (is (data/same-hue? 3 hue (get-in (cc/to colour :oklch) [:coords 2])) name)))
    (testing "real engines put them through, so a change here needs dev/greyscale.py"
      (let [measured (set (get data/greyscale "colours"))]
        (doseq [{:keys [name colour highlight]} p
                x [colour highlight]]
          (is (contains? measured (cc/hex x)) name))))))

(deftest a-palette-of-your-own
  (let [[brand] (epaper/palette [{:name :brand :colour "#24476b" :shade 4}])]
    (is (= :brand (:name brand)))
    (is (= 4 (:shade brand) (epaper/shade (:colour brand))))
    (is (= 13 (epaper/shade (:highlight brand)))))
  (testing "for a device you know"
    (let [generic (first (epaper/palette))
          known   (first (epaper/palette epaper/hues [:pdf]))]
      (is (= 3 (epaper/shade (:colour known) [:pdf])))
      (is (>= (choose/chroma (:colour known)) (choose/chroma (:colour generic)))))))

(deftest reports
  (let [failing #(set (map :check (remove :ok? (epaper/report %))))]
    (testing "Paul Tol's high-contrast scheme stays apart in colour, but shifts in grey"
      (is (= #{:on-a-shade :gap} (failing ["#004488" "#DDAA33" "#BB5566"]))))
    (testing "hues on one shade don't keep apart in grey"
      (is (contains? (failing ["#325a94" "#953a63"]) :gap)))
    (testing "colours can land on the same grey"
      (is (= 0 (:value (first (filter #(= :gap (:check %)) (epaper/report ["#0072b2" "#006699"])))))))
    (testing "one colour has no pair to check"
      (is (= [:on-a-shade] (map :check (epaper/report ["#325a94"])))))
    (testing "a colour can have a shade on a device you know, and no other"
      (is (= 0 (:value (first (epaper/report ["#0072b2"])))))
      (is (= 1 (:value (first (epaper/report ["#0072b2"] [:pdf])))))))
  (is (= {:check :gap :value 3 :wants 3 :ok? true}
         (second (epaper/report (map :colour (epaper/palette)))))))
