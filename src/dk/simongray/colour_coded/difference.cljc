(ns dk.simongray.colour-coded.difference
  "How different two colours look, by the measures of colour difference
  that CSS Color 4 gives, in section 20.

  - delta-e, the distance in Oklab, ΔEOK, of which 0.02 is about the least
    difference that shows
  - delta-e-ok2, ΔEOK with a and b doubled, which Ottosson found to fit
    the data of perceived differences better
  - delta-e-76, the distance in CIELAB, CIE76, of which 2.3 is about the
    least that shows (Sharma and Trussell 1997)
  - delta-e-2000, CIEDE2000, the standard of the CIE, of which 1 to 2 is
    about the least that shows

  https://www.w3.org/TR/css-color-4/#color-difference"
  (:require [dk.simongray.colour-coded :as cc]))

(defn- distance
  "The Euclidean distance of the colours `a` and `b` in the `space`, with
  the second and third coordinates scaled by `k`."
  [a b space k]
  (let [[l1 x1 y1] (:coords (cc/to a space))
        [l2 x2 y2] (:coords (cc/to b space))
        d          #(- (or %1 0) (or %2 0))]
    (Math/sqrt (+ (Math/pow (d l1 l2) 2)
                  (Math/pow (* k (d x1 x2)) 2)
                  (Math/pow (* k (d y1 y2)) 2)))))

(defn delta-e
  "ΔEOK, the distance of the colours `a` and `b` in Oklab, of which 0.02 is
  about the least that shows."
  [a b]
  (distance a b :oklab 1))

;; CSS Color 4, Editor's Draft, section 20.4
(defn delta-e-ok2
  "ΔEOK2, the distance of the colours `a` and `b` in Oklab with a and b
  doubled, of which 0.02 is about the least that shows."
  [a b]
  (distance a b :oklab 2))

(defn delta-e-76
  "CIE76, the distance of the colours `a` and `b` in CIELAB."
  [a b]
  (distance a b :lab 1))

(defn- degrees
  "The angle `x` in radians, in degrees."
  [x]
  (/ (* x 180) Math/PI))

(defn- radians
  "The angle `x` in degrees, in radians."
  [x]
  (/ (* x Math/PI) 180))

;; Sharma, Wu and Dalal, The CIEDE2000 color-difference formula:
;; Implementation notes, supplementary test data, and mathematical
;; observations, Color Research & Application 30(1), 2005, equations 1 to
;; 22, with kL, kC and kH of 1
;; https://hajim.rochester.edu/ece/sites/gsharma/ciede2000/
(defn delta-e-2000
  "CIEDE2000, the difference of the colours `a` and `b` in CIELAB, of which
  1 to 2 is about the least that shows."
  [a b]
  (let [[l1 a1 b1] (map #(or % 0) (:coords (cc/to a :lab)))
        [l2 a2 b2] (map #(or % 0) (:coords (cc/to b :lab)))
        pow7       #(Math/pow % 7)
        c-bar      (/ (+ (Math/hypot a1 b1) (Math/hypot a2 b2)) 2)
        g          (* 0.5 (- 1 (Math/sqrt (/ (pow7 c-bar) (+ (pow7 c-bar) (pow7 25))))))
        a1'        (* (+ 1 g) a1)
        a2'        (* (+ 1 g) a2)
        c1'        (Math/hypot a1' b1)
        c2'        (Math/hypot a2' b2)
        hue        #(if (and (zero? %1) (zero? %2)) 0 (mod (degrees (Math/atan2 %2 %1)) 360))
        h1'        (hue a1' b1)
        h2'        (hue a2' b2)
        chromatic? (not (zero? (* c1' c2')))
        dh'        (cond
                     (not chromatic?)              0
                     (<= (abs (- h2' h1')) 180)    (- h2' h1')
                     (> (- h2' h1') 180)           (- h2' h1' 360)
                     :else                         (+ (- h2' h1') 360))
        dl'        (- l2 l1)
        dc'        (- c2' c1')
        dhh'       (* 2 (Math/sqrt (* c1' c2')) (Math/sin (radians (/ dh' 2))))
        l-bar'     (/ (+ l1 l2) 2)
        c-bar'     (/ (+ c1' c2') 2)
        h-bar'     (cond
                     (not chromatic?)              (+ h1' h2')
                     (<= (abs (- h1' h2')) 180)    (/ (+ h1' h2') 2)
                     (< (+ h1' h2') 360)           (/ (+ h1' h2' 360) 2)
                     :else                         (/ (- (+ h1' h2') 360) 2))
        t          (+ 1
                      (* -0.17 (Math/cos (radians (- h-bar' 30))))
                      (* 0.24 (Math/cos (radians (* 2 h-bar'))))
                      (* 0.32 (Math/cos (radians (+ (* 3 h-bar') 6))))
                      (* -0.20 (Math/cos (radians (- (* 4 h-bar') 63)))))
        d-theta    (* 30 (Math/exp (- (Math/pow (/ (- h-bar' 275) 25) 2))))
        rc         (* 2 (Math/sqrt (/ (pow7 c-bar') (+ (pow7 c-bar') (pow7 25)))))
        sl         (+ 1 (/ (* 0.015 (Math/pow (- l-bar' 50) 2))
                           (Math/sqrt (+ 20 (Math/pow (- l-bar' 50) 2)))))
        sc         (+ 1 (* 0.045 c-bar'))
        sh         (+ 1 (* 0.015 c-bar' t))
        rt         (- (* (Math/sin (radians (* 2 d-theta))) rc))]
    (Math/sqrt (+ (Math/pow (/ dl' sl) 2)
                  (Math/pow (/ dc' sc) 2)
                  (Math/pow (/ dhh' sh) 2)
                  (* rt (/ dc' sc) (/ dhh' sh))))))

(defn nearest
  "The colour of `xs` nearest to the colour `x` by ΔEOK, or by the measure
  `f`, a function of two colours, e.g. delta-e-2000."
  ([x xs]
   (nearest x xs delta-e))
  ([x xs f]
   (when (seq xs)
     (apply min-key #(f x %) xs))))

(defn least
  "The least difference between any two colours of `xs`, by ΔEOK or by the
  measure `f`, or nil for fewer than two colours."
  ([xs]
   (least xs delta-e))
  ([xs f]
   (let [xs (vec xs)]
     (some->> (for [i (range (count xs))
                    j (range (inc i) (count xs))]
                (f (xs i) (xs j)))
              (seq)
              (apply min)))))
