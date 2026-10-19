(ns dk.simongray.colour-coded.vision
  "Colours as people with a colour vision deficiency see them, by the model
  of Machado, Oliveira and Fernandes.

  The model shifts how one kind of cone responds to light: the L cones for
  a protan deficiency, the M cones for a deutan one, and the S cones for a
  tritan one. Its severity runs from 0, normal colour vision, to 1. At 1,
  the protan and deutan simulations are those of dichromats, who lack the
  cone. The paper treats its tritan simulation as an approximation, and
  doesn't simulate tritanopia, since the causes of tritan deficiencies
  aren't well known.

  Red-green deficiencies, protan and deutan, affect about 8% of men and
  0.4% of women of European descent, while tritan ones are much rarer (the
  paper, after Sharpe et al.).

  The paper derives each matrix from the spectra of a display's primaries,
  so the matrices apply to linear light, and the namespace applies them in
  linear sRGB.

  Machado, Oliveira and Fernandes, A physiologically-based model for
  simulation of color vision deficiency, IEEE Transactions on
  Visualization and Computer Graphics 15(6), 2009, pp. 1291–1298
  https://www.inf.ufrgs.br/~oliveira/pubs_files/CVD_Simulation/CVD_Simulation.html"
  (:require [dk.simongray.colour-coded :as cc]
            [dk.simongray.colour-coded.difference :as difference]
            [dk.simongray.colour-coded.space :as space]))

(def deficiencies
  "The deficiencies that the model simulates."
  [:protan :deutan :tritan])

;; From Table 1 of the authors' page, where the columns are protanomaly,
;; deuteranomaly and tritanomaly
(def matrices
  "The paper's matrices for each deficiency, at the severities from 0 to 1
  in steps of 0.1. Each maps a colour's red, green and blue in linear
  light."
  {:protan
   [[[ 1.000000  0.000000 -0.000000]  ; 0.0
     [ 0.000000  1.000000  0.000000]
     [-0.000000 -0.000000  1.000000]]
    [[ 0.856167  0.182038 -0.038205]  ; 0.1
     [ 0.029342  0.955115  0.015544]
     [-0.002880 -0.001563  1.004443]]
    [[ 0.734766  0.334872 -0.069637]  ; 0.2
     [ 0.051840  0.919198  0.028963]
     [-0.004928 -0.004209  1.009137]]
    [[ 0.630323  0.465641 -0.095964]  ; 0.3
     [ 0.069181  0.890046  0.040773]
     [-0.006308 -0.007724  1.014032]]
    [[ 0.539009  0.579343 -0.118352]  ; 0.4
     [ 0.082546  0.866121  0.051332]
     [-0.007136 -0.011959  1.019095]]
    [[ 0.458064  0.679578 -0.137642]  ; 0.5
     [ 0.092785  0.846313  0.060902]
     [-0.007494 -0.016807  1.024301]]
    [[ 0.385450  0.769005 -0.154455]  ; 0.6
     [ 0.100526  0.829802  0.069673]
     [-0.007442 -0.022190  1.029632]]
    [[ 0.319627  0.849633 -0.169261]  ; 0.7
     [ 0.106241  0.815969  0.077790]
     [-0.007025 -0.028051  1.035076]]
    [[ 0.259411  0.923008 -0.182420]  ; 0.8
     [ 0.110296  0.804340  0.085364]
     [-0.006276 -0.034346  1.040622]]
    [[ 0.203876  0.990338 -0.194214]  ; 0.9
     [ 0.112975  0.794542  0.092483]
     [-0.005222 -0.041043  1.046265]]
    [[ 0.152286  1.052583 -0.204868]  ; 1.0
     [ 0.114503  0.786281  0.099216]
     [-0.003882 -0.048116  1.051998]]]

   :deutan
   [[[ 1.000000  0.000000 -0.000000]  ; 0.0
     [ 0.000000  1.000000  0.000000]
     [-0.000000 -0.000000  1.000000]]
    [[ 0.866435  0.177704 -0.044139]  ; 0.1
     [ 0.049567  0.939063  0.011370]
     [-0.003453  0.007233  0.996220]]
    [[ 0.760729  0.319078 -0.079807]  ; 0.2
     [ 0.090568  0.889315  0.020117]
     [-0.006027  0.013325  0.992702]]
    [[ 0.675425  0.433850 -0.109275]  ; 0.3
     [ 0.125303  0.847755  0.026942]
     [-0.007950  0.018572  0.989378]]
    [[ 0.605511  0.528560 -0.134071]  ; 0.4
     [ 0.155318  0.812366  0.032316]
     [-0.009376  0.023176  0.986200]]
    [[ 0.547494  0.607765 -0.155259]  ; 0.5
     [ 0.181692  0.781742  0.036566]
     [-0.010410  0.027275  0.983136]]
    [[ 0.498864  0.674741 -0.173604]  ; 0.6
     [ 0.205199  0.754872  0.039929]
     [-0.011131  0.030969  0.980162]]
    [[ 0.457771  0.731899 -0.189670]  ; 0.7
     [ 0.226409  0.731012  0.042579]
     [-0.011595  0.034333  0.977261]]
    [[ 0.422823  0.781057 -0.203881]  ; 0.8
     [ 0.245752  0.709602  0.044646]
     [-0.011843  0.037423  0.974421]]
    [[ 0.392952  0.823610 -0.216562]  ; 0.9
     [ 0.263559  0.690210  0.046232]
     [-0.011910  0.040281  0.971630]]
    [[ 0.367322  0.860646 -0.227968]  ; 1.0
     [ 0.280085  0.672501  0.047413]
     [-0.011820  0.042940  0.968881]]]

   :tritan
   [[[ 1.000000  0.000000 -0.000000]  ; 0.0
     [ 0.000000  1.000000  0.000000]
     [-0.000000 -0.000000  1.000000]]
    [[ 0.926670  0.092514 -0.019184]  ; 0.1
     [ 0.021191  0.964503  0.014306]
     [ 0.008437  0.054813  0.936750]]
    [[ 0.895720  0.133330 -0.029050]  ; 0.2
     [ 0.029997  0.945400  0.024603]
     [ 0.013027  0.104707  0.882266]]
    [[ 0.905871  0.127791 -0.033662]  ; 0.3
     [ 0.026856  0.941251  0.031893]
     [ 0.013410  0.148296  0.838294]]
    [[ 0.948035  0.089490 -0.037526]  ; 0.4
     [ 0.014364  0.946792  0.038844]
     [ 0.010853  0.193991  0.795156]]
    [[ 1.017277  0.027029 -0.044306]  ; 0.5
     [-0.006113  0.958479  0.047634]
     [ 0.006379  0.248708  0.744913]]
    [[ 1.104996 -0.046633 -0.058363]  ; 0.6
     [-0.032137  0.971635  0.060503]
     [ 0.001336  0.317922  0.680742]]
    [[ 1.193214 -0.109812 -0.083402]  ; 0.7
     [-0.058496  0.979410  0.079086]
     [-0.002346  0.403492  0.598854]]
    [[ 1.257728 -0.139648 -0.118081]  ; 0.8
     [-0.078003  0.975409  0.102594]
     [-0.003316  0.501214  0.502102]]
    [[ 1.278864 -0.125333 -0.153531]  ; 0.9
     [-0.084748  0.957674  0.127074]
     [-0.000989  0.601151  0.399838]]
    [[ 1.255528 -0.076749 -0.178779]  ; 1.0
     [-0.078411  0.930809  0.147602]
     [ 0.004733  0.691367  0.303900]]]})

;; the authors' page calls this interpolation a fast and accurate
;; approximation of the model's own matrix
(defn matrix
  "The matrix that simulates the `deficiency` at the `severity`, from 0 to
  1. Between the paper's steps, it interpolates the two nearest matrices,
  e.g. at 0.873 those of 0.8 and 0.9, weighing the latter by 0.73."
  [deficiency severity]
  (let [ms (matrices deficiency)
        s  (* 10 (min 1.0 (max 0.0 severity)))
        i  (int s)
        t  (- s i)]
    (if (zero? t)
      (ms i)
      (mapv (fn [r1 r2] (mapv #(+ %1 (* t (- %2 %1))) r1 r2))
            (ms i)
            (ms (inc i))))))

(defn simulate
  "The colour `x` as someone with the `deficiency` sees it, at the
  `severity` from 0 to 1, by default 1, in linear sRGB.

  The matrices can take a saturated colour outside sRGB, even to one that
  no light gives: for a deutan, pure blue gets a negative response of the
  L cones. The function clips the result to sRGB, where ΔEOK can measure
  it."
  ([x deficiency]
   (simulate x deficiency 1.0))
  ([x deficiency severity]
   (let [m (matrix deficiency severity)]
     (some-> (cc/to x :srgb-linear)
             (update :coords #(->> (mapv (fn [v] (or v 0.0)) %)
                                   (space/multiply m)
                                   (mapv (fn [v] (min 1.0 (max 0.0 v))))))))))

(defn least-difference
  "The least ΔEOK between any two colours of `xs`, with normal colour
  vision and with each deficiency at the `severity`, by default 1.

  It returns a map of :normal and each deficiency to the least ΔEOK, which
  is nil for fewer than two colours. A difference of 0.02 is about the
  least that shows, so colours that people must tell apart need more than
  that with each deficiency."
  ([xs]
   (least-difference xs 1.0))
  ([xs severity]
   (into {:normal (difference/least xs)}
         (map (fn [d] [d (difference/least (map #(simulate % d severity) xs))]))
         deficiencies)))
