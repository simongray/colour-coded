(ns dk.simongray.colour-coded.syntax-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is]]
            [dk.simongray.colour-coded :as cc]
            [dk.simongray.colour-coded.syntax :as syntax]
            [dk.simongray.colour-coded.space :as space]
            [dk.simongray.colour-coded.test-data :as data]))

;; Syntax that a value of CSS can have but a colour on its own can't, or
;; that needs a document: calc(), var(), relative colours, currentcolor,
;; system colours and escapes
(def beyond-the-parser
  #"calc\(|var\(|from |currentcolor|cqw|\bem\b|canvas|highlight|buttontext|-webkit|accentcolor|linktext|\\")

(defn- same?
  "Whether the colour `ours` is the colour of the CSS `expected`, within
  what its serialisation rounds."
  [ours expected]
  (let [e   (cc/colour expected)
        id  (:space e)
        hue (space/hue-index id)
        tol (case id (:lab :lch) 0.05 (:oklab :oklch) 0.0005 0.005)]
    (when (and e ours)
      (let [a (:coords (cc/to ours id))
            b (:coords e)]
        (and (every? true? (map-indexed (fn [i [x y]]
                                          (if (= i hue)
                                            (data/same-hue? 0.1 x y)
                                            (data/close? tol x y)))
                                        (map vector a b)))
             (data/close? 0.005 (:alpha ours) (:alpha e)))))))

(deftest the-computed-values-of-the-web-platform-tests
  (let [cases (remove #(re-find beyond-the-parser (str/lower-case (get % "spec"))) data/wpt)]
    (is (< 6000 (count cases)))
    (doseq [{:strs [spec expected]} cases]
      (is (some #(same? (cc/colour spec) %) (if (string? expected) [expected] expected))
          (str spec " computes to " expected)))))

(deftest parsing
  (is (= {:space :srgb :coords [1.0 0.0 0.0] :alpha 1.0} (syntax/parse "#f00")))
  (is (= {:space :srgb :coords [0.0 0.0 0.0] :alpha 0.0} (syntax/parse "transparent")))
  (is (= {:space :oklch :coords [0.44 0.085 158.0] :alpha 1.0} (syntax/parse "oklch(44% 0.085 158)")))
  (is (= {:space :oklch :coords [0.44 nil 158.0] :alpha 0.5} (syntax/parse "OKLCH(0.44 none 158deg / 50%)")))
  (is (= {:space :display-p3 :coords [1.0 0.5 0.0] :alpha 1.0} (syntax/parse "color(display-p3 1 50% 0)")))
  (is (data/close? 1e-9 [90.0 180.0 9.0]
                   (map #(get-in (syntax/parse (str "hsl(" % " 50% 50%)")) [:coords 0])
                        ["0.25turn" "3.14159265358979rad" "10grad"]))
      "angles in other units")
  (is (every? nil? (map syntax/parse ["red blue" "rgb(1 2)" "oklch(1 2 3 4)" "#12345" "nonsense(1 2 3)" "" nil 5]))
      "nil for anything else"))

(deftest serialising
  (is (= "rgb(36, 71, 107)" (syntax/serialise (syntax/parse "#24476b"))))
  (is (= "rgba(36, 71, 107, 0.5)" (syntax/serialise (syntax/parse "rgb(36 71 107 / 0.5)"))))
  (is (= "oklch(0.44 0.085 158)" (syntax/serialise (syntax/parse "oklch(44% 0.085 158)"))))
  (is (= "lab(50 none 20 / 0.25)" (syntax/serialise (syntax/parse "lab(50 none 20 / 25%)"))))
  (is (= "color(display-p3 1 0 0)" (syntax/serialise (syntax/parse "color(display-p3 1 0 0)")))))

(deftest numbers
  (is (= ["0" "1" "0.5" "-0.25" "158" "0.123457" "12345.7" "0.0000123457"]
         (map syntax/format-number [0 1.0 0.5 -0.25 158.0 0.1234567 12345.678 0.0000123456789])))
  (is (= "0" (syntax/format-number -0.0000000000001)) "no -0"))
