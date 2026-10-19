(ns dk.simongray.colour-coded.test-data
  "The fixtures of the tests, from test/resources, and comparisons of
  numbers within a tolerance.

  - colorjs.json, what Colour.js gives for a fixed set of colours, which
    dev/fixtures.mjs writes
  - wpt.json, the computed values that the web-platform-tests of CSS Color
    expect, which dev/wpt.mjs writes
  - ciede2000.txt, the 34 pairs of Sharma, Wu and Dalal (2005), as Sharma
    publishes them for research, with their CIEDE2000:
    https://hajim.rochester.edu/ece/sites/gsharma/ciede2000/"
  #?(:clj  (:require [clojure.data.json :as json]
                     [clojure.java.io :as io]
                     [clojure.string :as str])
     :cljs (:require [clojure.string :as str]))
  #?(:cljs (:require-macros [dk.simongray.colour-coded.test-data :refer [resource]])))

#?(:clj
   (defmacro resource
     "The text of the resource at `path`, inlined at compile time in
     ClojureScript, and read at run time on the JVM, whose class files
     can't hold a string as long."
     [path]
     (if (:ns &env)
       (slurp (io/resource path))
       `(slurp (io/resource ~path)))))

(defn- json
  "The JSON `s` as Clojure data, with strings as the keys of maps."
  [s]
  #?(:clj  (json/read-str s)
     :cljs (js->clj (js/JSON.parse s))))

(def colorjs
  "What Colour.js gives for a fixed set of colours."
  (json (resource "colorjs.json")))

(def wpt
  "The computed values that the web-platform-tests of CSS Color expect, as
  maps of the \"spec\" and the \"expected\" value, or a vector of the values
  that pass."
  (get (json (resource "wpt.json")) "cases"))

(def ciede2000
  "Sharma's pairs of CIELAB colours, each a vector of the L, a and b of
  both, and their CIEDE2000."
  (for [line  (str/split-lines (resource "ciede2000.txt"))
        :when (not (str/blank? line))]
    (mapv parse-double (str/split (str/trim line) #"\s+"))))

(defn fixture-colour
  "The colour of a fixture of Colour.js `m`, a map of its space, by
  Colour.js's id, and its coordinates."
  [{:strs [space coords]}]
  {:space ({"p3" :display-p3} space (keyword space)) :coords coords})

(defn close?
  "Whether the numbers or vectors of numbers `x` and `y` differ by no more
  than `tolerance` each, or by that share of `y` when it's over 1, with
  nil, a missing component, only equal to nil."
  [tolerance x y]
  (cond
    (and (nil? x) (nil? y))                       true
    (or (nil? x) (nil? y))                        false
    (and (number? x) (number? y))                 (<= (abs (- x y)) (* tolerance (max 1 (abs y))))
    (and (sequential? x) (sequential? y))         (and (= (count x) (count y))
                                                       (every? true? (map #(close? tolerance %1 %2) x y)))
    :else                                         false))

(defn same-hue?
  "Whether the hues `x` and `y`, in degrees, are within `tolerance` of each
  other round the circle, or both missing."
  [tolerance x y]
  (or (and (nil? x) (nil? y))
      (and (some? x) (some? y)
           (<= (abs (- (mod (+ (- x y) 180) 360) 180)) tolerance))))
