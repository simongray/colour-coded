# How to choose colours for e-paper

An e-paper panel shows 16 greys, its *shades*. A colour with a shade lands on the
same one whichever way a device converts it, so it looks the same on any monochrome
panel, and a colour panel still shows its hue.
[The decisions behind this](decisions.md#e-paper) explain why that takes some care.


## If you have no colours yet

Call `epaper/palette` and take as many colours as you need from the front. They stay
apart on a monochrome panel, on a colour panel and with a colour vision deficiency.

```clojure
(require '[dk.simongray.colour-coded :as cc]
         '[dk.simongray.colour-coded.epaper :as epaper])

;; for lines and fills
(map (comp cc/hex :colour) (epaper/palette))
;; => ("#003b78" "#ffc330" "#b3485b" "#5cadad")

;; the same hues as pale backgrounds for black text
(map (comp cc/hex :highlight) (epaper/palette))
;; => ("#bddaff" "#f7daa1" "#ffccd1" "#9ceeee")
```


## For a whole site or app

Pass `theme/epaper-roles` to `theme/from-seed`. To check the contrast as a panel shows
it, pass `epaper/ratio` to `theme/report`.

```clojure
(require '[dk.simongray.colour-coded.theme :as theme])

(def paper (theme/from-seed "#24476b" theme/epaper-roles))

(remove :ok? (theme/report paper epaper/ratio))
;; => ()
```

The result works with `theme/css`, `theme/hex` and `theme/design-tokens` like any
other theme.


## If you have colours of your own

Move each one onto a shade with `epaper/at-shade`, which keeps its hue and as much of
its chroma as it can. Then check that they stay apart with `epaper/report`.

```clojure
;; a blue that can land on either of two shades
(epaper/shades "#0072b2")
;; => #{5 6}

;; the same hue on one of them
(cc/hex (epaper/at-shade "#0072b2" 5))
;; => "#156396"

;; or a whole palette at once
(def brand (epaper/palette [{:name :brand :colour "#24476b" :shade 3}
                            {:name :accent :colour "#e8b931" :shade 12}]))

(remove :ok? (epaper/report (map :colour brand)))
;; => ()
```

Hues on one shade have the same grey, so they tell things apart on a colour panel at
no cost on a monochrome one. To tell colours apart by their grey alone, keep them
several shades apart.


## If you know your device's conversion

Pass the conversion to allow more chroma. `greyscale/conversions` lists them. The
colour may then shift a shade on other devices.

```clojure
(cc/hex (epaper/at-shade "#0072b2" 5 [:pdf]))
;; => "#0072b2"
```


## If you're repainting a grey drawing

To change a drawing's greys to colours without changing it on a monochrome panel, check
each colour against the grey it replaces with `epaper/same-grey?`.

```clojure
(epaper/same-grey? "#555555" (epaper/at-shade "#0072b2" 5))
;; => true
```


## If you derive colours from one seed

The tokens of `tone` take a shade in place of a lightness.

```clojure
(require '[dk.simongray.colour-coded.tone :as tone])

(cc/hex (tone/tone {:shade 5 :c 0.1} "#a90000"))
;; => "#8a4239"
```


## To see roughly how a panel shows a colour

Use `epaper/simulate`, by default for Carta. It's an approximation, only good for
comparing colours. A panel can also be a map of your own measurements.

```clojure
(cc/hex (epaper/simulate "#325a94" :kaleido-3))
;; => "#727a94"

(cc/hex (epaper/simulate "#325a94" {:contrast 12.0 :saturation 0.4}))
;; => "#6b728a"
```


## Whichever colours you use

- Keep fine detail black. [Kaleido 3](https://www.eink.com/brand/detail/Kaleido3)
  has half the resolution in colour, so a hue needs a fill, or a line of about
  1 pt at reading size.
- Don't rely on colour alone. [Okabe and Ito](https://jfly.uni-koeln.de/color/)
  advise showing a difference by shape or line style too, which also survives
  a black and white copy.
- Give pale colours a black outline or a large area. They wash out on a colour
  panel, and a
  [Boox's Light Color Filter](https://ebookreader.dk/images/upload/Onyx%20BOOX%20Go%2010%203%20User%20Manual.pdf)
  can turn them white.
- Expect fewer greys in fast refresh modes. Rockchip's e-ink code makes shades
  0 to 9 black and the rest white in A2 and DU.
- Judge hues by eye, since a shade says nothing about how a hue looks, e.g.
  yellow on a dark shade reads as olive or brown.
