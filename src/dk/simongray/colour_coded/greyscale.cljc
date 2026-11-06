(ns dk.simongray.colour-coded.greyscale
  "The greys that colours become on greyscale devices, which differ by device.

  Nothing standardises how to turn a colour into a grey, and a device that
  shows the same PDF may use any of several weightings. Pure red comes out
  as 76 of 255 in PDFium, 54 in ImageMagick and 129 in MuPDF. A muted colour
  depends much less on the choice than a saturated one, which is why it's
  safer on a device whose conversion you don't know. Each function here
  works in sRGB, and clips a colour outside it, since that's what a device
  gets. A conversion ignores alpha.

  The tests compare these with what MuPDF, PDFium, Poppler, Pillow and
  ImageMagick render, in test/resources/greyscale.json, which
  dev/greyscale.py writes. The weights come from these sources:

  - PDFium, FXRGB2GRAY in core/fxge/dib/fx_dib.h
  - MuPDF, color-fast.c for its fast path, and colorspace.c for ICC
  - Poppler, GfxState.cc
  - KOReader, getColor8 in ffi/blitbuffer.lua, in 14 bits
  - Android, setSaturation in android/graphics/ColorMatrix.java, to 3 digits
  - CSS's grayscale(), https://www.w3.org/TR/filter-effects-1/#grayscaleEquivalent
  - Kindle's rule for text, section 11.3.2 of the Kindle Publishing Guidelines
  - Rockchip's e-ink compositor, rgb888_to_gray_256_neon.s and
    einkcompositorworker.cpp in
    https://github.com/TinkerBoard-Android/rockchip-android-hardware-rk35-einkhwc"
  (:require [dk.simongray.colour-coded :as cc]
            [dk.simongray.colour-coded.gamut :as gamut]
            [dk.simongray.colour-coded.space :as space]))

;; A viewer's PDF engine is often unknown, e.g. that of Onyx's NeoReader, and
;; so is any step of a device beyond it, so epaper asks for a colour that
;; lands on one grey by all of these
(def conversions
  "The ways to turn a colour into a grey, by the weights of its red, green
  and blue, which apply to sRGB's encoded values, or to linear light where
  :linear? says so:

  - :pdf, PDFium, Poppler, MuPDF without ICC, and very nearly the 8-bit NEON
    path of Rockchip's e-ink compositor
  - :rec601, Rec. 601's luma, in Pillow, ImageMagick's Rec601Luma, KOReader,
    and the RGA path of Rockchip's e-ink compositor
  - :rec709, Rec. 709's weights without linearising, in ImageMagick by
    default, Android's ColorMatrix, CSS's grayscale() and Kindle's rule
  - :rockchip, the 4-bit NEON path of Rockchip's e-ink compositor, in 32nds,
    which goes straight to 4 bits by keeping the top ones, as :truncates?
    says
  - :icc, MuPDF, which turns ICC on unless a build or an app disables it,
    and which matches the luminance of XYZ-D50, encoded again with sRGB's
    curve"
  {:pdf      {:weights [0.3 0.59 0.11]}
   :rec601   {:weights [0.299 0.587 0.114]}
   :rec709   {:weights [0.2126 0.7152 0.0722]}
   :rockchip {:weights [0.34375 0.5 0.15625] :truncates? true}
   :icc      {:weights [0.2225045 0.7168786 0.0606169] :linear? true}})

(def default-conversion
  "The conversion to use where only one can apply: :pdf, since the most engines
  share its weights."
  :pdf)

(defn- weigh
  "The value that a `conversion` of the conversions map gives the sRGB
  channels `rgb`."
  [{:keys [weights linear?] :as conversion} rgb]
  (if linear?
    (space/linear->srgb (reduce + (map * weights (map space/srgb->linear rgb))))
    (reduce + (map * weights rgb))))

(defn values
  "A map of each conversion in `ks`, by default all of them, to the value it
  gives the colour `x`, from 0 for black to 1 for white, or nil where `ks` holds
  anything but a key of conversions."
  ([x]
   (values x (keys conversions)))
  ([x ks]
   (when-let [rgb (:coords (gamut/clip x))]
     (when (every? conversions ks)
       (update-vals (select-keys conversions ks) #(weigh % rgb))))))

(defn value
  "The grey that the `conversion`, a key of conversions, by default
  default-conversion, turns the colour `x` into, from 0 for black to 1 for
  white."
  ([x]
   (value x default-conversion))
  ([x conversion]
   (get (values x [conversion]) conversion)))

(defn grey
  "The colour `x` as the grey that the `conversion`, by default
  default-conversion, turns it into, in sRGB, keeping its alpha."
  ([x]
   (grey x default-conversion))
  ([x conversion]
   (when-let [v (value x conversion)]
     {:space :srgb :coords [v v v] :alpha (:alpha (cc/colour x))})))

(comment
  (values "red")
  ;; => {:pdf 0.3, :rec601 0.299, :rec709 0.2126, :rockchip 0.34375, :icc 0.509…}

  (cc/hex (grey "#0072b2" :icc))
  #_.)
