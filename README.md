colour-coded
============

Give colour-coded one colour, or one image, e.g. a podcast's artwork, and
it gives you the colours of a whole site or app: light and dark, at two
levels of contrast, with every pair of colours that must contrast checked
against WCAG 2.2. You get them as CSS for a web page, or as hex colours
and design tokens for a native app, from the same Clojure code on the JVM
or in ClojureScript.

Underneath, it computes the same colours that a browser computes. It has
every colour space of CSS Color 4, its syntax, `color-mix()` and its gamut
mapping, tested against Colour.js and more than 6,000 of the
web-platform-tests. No other Clojure library has both OKLCH and the colour
syntax of CSS.

- **Accessible by construction.** Each colour takes its lightness from a
  scale on which lightness predicts contrast, so the contrast holds
  whatever the hue of the colour or image you start from. A report checks
  every pair anyway.
- **CSS that adapts.** The CSS uses `light-dark()`, respects
  `prefers-contrast: more` and forced colours, and can derive every colour
  from one custom property at run time.
- **Ready for native apps.** React Native reads no `oklch()`, so you get
  hex colours too, and design tokens in the DTCG's format for Style
  Dictionary.
- **Ready for e-paper.** Colours can land on the same grey on any
  monochrome panel and keep their hue on a colour one.
- **Colour theory as plain functions,** each citing its source: contrast,
  colour difference, harmonies, colours at a fixed lightness, colours found
  by the contrast they should have, the colours of an image, and colours
  as people with a colour vision deficiency see them.

The library depends on Clojure alone. Only the image namespace calls the
platform, to decode an image: ImageIO on the JVM, and the browser's
decoder in ClojureScript. A [demo](#a-demo) shows every part of it
applied to examples.


Getting started
---------------

It requires Clojure 1.11+ and Java 11+. Add it from
[Clojars](https://clojars.org/dk.simongray/colour-coded) to the `:deps` in
your `deps.edn`:

```clojure
dk.simongray/colour-coded {:mvn/version "0.2.0"}
```

For unreleased changes, depend on the latest commit on `master` instead.
In shadow-cljs, a Git dependency only works through `deps.edn`, so set
`:deps true` in your `shadow-cljs.edn` too.


From an image
-------------

```clojure
(require '[dk.simongray.colour-coded :as cc]
         '[dk.simongray.colour-coded.image :as image]
         '[dk.simongray.colour-coded.theme :as theme])

;; a podcast's artwork; in ClojureScript, each call below returns a
;; promise instead
(def artwork
  (java.nio.file.Files/readAllBytes
   (java.nio.file.Path/of "artwork.jpg" (make-array String 0))))

;; the colour to derive a UI's colours from: the commonest with a hue
(cc/hex (image/seed artwork))
;; => e.g. "#24476b"

;; the colours of a whole site, from the artwork
(def t (theme/from-image artwork))
```

`image/palette` gives the commonest colours of the image with their
shares, and `image/scheme` a small scheme of a background, text and an
accent for a player.


From a colour
-------------

```clojure
(def t (theme/from-seed "#24476b"))

;; as custom properties for a web page
(println (theme/css t))
;; :root {
;;   color-scheme: light dark;
;;   accent-color: var(--accent);
;;   --accent: light-dark(#416187, #9cbce7);
;;   --background: light-dark(#ffffff, #16181a);
;;   …

;; as hex colours, e.g. for React Native
(:dark (theme/hex t))
;; => {:background "#16181a", :text "#e8ebf1", :accent "#9cbce7", …}

;; each pair of colours that must contrast, and whether it does
(remove :ok? (theme/report t))
;; => ()
```

A theme has ten roles, in words that a beginner knows: the background, a
surface on it, text, muted text, the border of a control, a divider, the
accent of the main control, the text on the accent, links and the focus
ring. `theme/relative-css` writes CSS that derives them all from one
custom property, e.g. `--seed`, so that a page changes its theme by
setting it. `theme/design-tokens` writes them as design tokens.


On e-paper
----------

For a good palette on any e-reader, call `epaper/palette`. Take as many
colours as you need from the front. They stay apart on a monochrome panel,
on a colour panel and with a colour vision deficiency.

```clojure
(require '[dk.simongray.colour-coded.epaper :as epaper])

;; for lines and fills
(map (comp cc/hex :colour) (epaper/palette))
;; => ("#003b78" "#ffc330" "#b3485b" "#5cadad")

;; the same hues as pale backgrounds for black text
(map (comp cc/hex :highlight) (epaper/palette))
;; => ("#bddaff" "#f7daa1" "#ffccd1" "#9ceeee")

;; the colours of a whole site or app
(def paper (theme/from-seed "#24476b" theme/epaper-roles))

;; each pair that must contrast, as a panel shows it
(remove :ok? (theme/report paper epaper/ratio))
;; => ()
```

An e-paper panel shows 16 greys, and nothing standardises how a device
turns a colour into one of them: pure red comes out as 76 of 255 in
PDFium, 54 in ImageMagick and 129 in MuPDF. A colour with a *shade* lands on
the same one of the 16 whichever way a device converts it, so it looks the same
on any monochrome panel. A colour panel still shows its hue. To give colours of
your own a shade:

```clojure
;; a blue that can land on either of two shades
(epaper/shades "#0072b2")
;; => #{5 6}

;; the same hue on one of them
(cc/hex (epaper/at-shade "#0072b2" 5))
;; => "#156396"

;; a palette of your own, and whether it stays apart
(def brand (epaper/palette [{:name :brand :colour "#24476b" :shade 3}
                            {:name :accent :colour "#e8b931" :shade 12}]))

(remove :ok? (epaper/report (map :colour brand)))
;; => ()

;; more chroma, if you know your device's conversion
(cc/hex (epaper/at-shade "#0072b2" 5 [:pdf]))
;; => "#0072b2"
```

Hues on one shade have the same grey, so they tell things apart on a colour
panel at no cost on a monochrome one. To paint a grey drawing without
changing it on a monochrome panel, `epaper/same-grey?` checks each colour
against the grey it replaces. The tokens of `tone` take a shade too, e.g.
`{:shade 5 :c 0.1}`. To see roughly how Carta or Kaleido 3 shows a colour, use
`epaper/simulate`.

Whichever colours you use:

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


Colours
-------

A colour is a map of its space, its coordinates and its alpha, and every
function takes a CSS colour as well:

```clojure
(require '[dk.simongray.colour-coded.contrast :as contrast]
         '[dk.simongray.colour-coded.gamut :as gamut])

(cc/to "#24476b" :oklch)
;; => {:space :oklch, :coords [0.3896… 0.0738… 250.84…], :alpha 1.0}

;; the relative colour oklch(from #24476b 0.7 c h)
(cc/css (cc/with "#24476b" :oklch {:l 0.7}))
;; => "oklch(0.7 0.0738503 250.846)"

(cc/css "color-mix(in oklch, #24476b 40%, gold)")
;; => "oklch(0.68793 0.138852 157.537)"

(contrast/ratio "#24476b" "white")
;; => 9.595…

;; a colour outside sRGB, mapped into it by CSS Color 4's gamut mapping
(cc/hex (gamut/to-gamut "oklch(70% 0.3 140)"))
;; => "#15c000"
```

The functions return nil for anything that isn't a colour, e.g.
`(cc/hex "not a colour")`, and for a space or a panel that doesn't exist. A
function of a list of colours skips what isn't one, but a report fails it.

To check that a palette's colours stay apart for people with a colour
vision deficiency:

```clojure
(require '[dk.simongray.colour-coded.vision :as vision])

;; red and green as someone with deuteranopia sees them
(map #(cc/hex (vision/simulate % :deutan)) ["#e00000" "#008000"])
;; => ("#8f7e00" "#776a18")

;; the least ΔEOK between any two of the colours, with each deficiency
(vision/least-difference ["#e00000" "#008000" "#4080ff"])
;; => {:normal 0.343…, :protan 0.115…, :deutan 0.071…, :tritan 0.132…}
```

These are the namespaces:

- `colour-coded`, colours as maps, and `space`, `syntax`, `gamut`,
  `contrast`, `difference` (ΔEOK, CIE76 and CIEDE2000) and `mix`
- `choose`, a seed from a list of colours, the first with enough contrast,
  those set apart, and a small scheme for a UI
- `harmony`, complements, triads and the rest, turned in OKLCH
- `tone`, colours at a fixed lightness from one seed, and the worst
  contrast of a pair over every hue
- `target`, a colour at the lightness that gives it a contrast
- `quantise` and `image`, the colours of an image, and `image` its
  thumbnails too
- `vision`, colours as people with a protan, deutan or tritan deficiency
  see them, by Machado, Oliveira and Fernandes's model
- `greyscale`, the greys that devices turn colours into
- `epaper`, colours that land on one of a panel's 16 greys, and colours as
  a panel shows them
- `theme`, the colours of a site or an app

Each namespace cites its sources in its docstring, and in the code where
it makes a decision.


Status
------

These choices were made in building it, for review:

- A colour is a map of :space, :coords and :alpha, the shape that
  Colour.js uses, and every function takes CSS strings too.
- A function returns nil for what it can't use rather than guessing or
  throwing, so a typo in a colour shows up as nil, not as black.
- An image with no opaque pixels has no seed, so it has no theme. Pass a colour
  of your own to `theme/from-seed` for that case.
- `vision/simulate` has no default deficiency, since there's no general case
  to assume. `vision/least-difference` checks them all.
- The names spell "colour", "quantise" and "serialise" in British English
  throughout, but CSS's own words, e.g. color-mix(), keep CSS's spelling.
- The parser is in `syntax`, since in ClojureScript a namespace called
  `css` clashes with the function `css` of the root namespace.
- `tone` lowers a colour's chroma in the token's own space, so that its
  lightness and hue stay exact, rather than with CSS's gamut mapping.
  `worst-contrast` checks both that and the clipping of browsers.
- The theme takes its lightness from CIELAB's L\*, which keeps contrast to
  within a few percent whatever the hue, since CIELAB adapts to the white
  of D50 where WCAG's luminance uses D65's.
- APCA is left out: its licence forbids changing it and using it
  commercially.
- `vision` clips a simulated colour to sRGB. Machado's matrices can give
  a saturated colour a negative cone response, which makes its ΔEOK
  meaningless. The tritan simulation is the paper's approximation, which
  doesn't cover tritanopia.
- By default `epaper` asks a colour to land on one grey by every conversion,
  at some cost in chroma, since e-readers don't document theirs. You can pass
  the conversions you know. They come from real PDF and image engines and from
  open source code. No panel has confirmed a shade yet.
- `epaper/palette` has four colours because no more fit 3 shades apart between
  shades 3 and 12. Its hues start from Paul Tol's high-contrast scheme, which he
  made for a monochrome printout. The hues, the thresholds of `epaper/report`
  and the number of colours are judgements, untested on readers.
- The numbers for Kaleido 3 in `epaper/panels` are estimates, since E Ink
  publishes none and I found no measurements. Gallery 3 is left out for the
  same reason.

Material 3's HCT and dynamic schemes, ramps, data palettes and historic
colour theory are still to come.


A demo
------

To see every part of the library applied to examples, drawn as colours,
open the notebook in `notebooks/demo.clj` with
[Clerk](https://clerk.vision). This opens it at http://localhost:7777,
and it follows changes to the code:

```bash
clojure -X:demo
```

To write it as a static page in `target/demo` instead:

```bash
clojure -X:demo:static
```


Development
-----------

Install the npm packages once, for shadow-cljs and Colour.js:

```bash
npm install
```

To run the tests on the JVM:

```bash
clojure -X:test
```

To run them in Node:

```bash
clojure -M:cljs compile test
```

The root namespace's docstring states the rules that every namespace keeps to,
and `api_test.cljc` tests them. Add a new function to its tables.

The tests compare the library with Colour.js, whose values are in
`test/resources/colorjs.json`, and with the computed values of the
web-platform-tests of CSS Color, in `test/resources/wpt.json`. To write
those again, e.g. for a new version of Colour.js:

```bash
npm run fixtures
```

```bash
npm run wpt
```

The tests of `vision` compare it with the paper's matrices and with
colour-science, whose values are in `test/resources/machado2009.json`. To
write those again, with Python:

```bash
python3 -m venv .venv
.venv/bin/pip install colour-science
.venv/bin/python dev/machado.py
```

The tests of `greyscale` compare it with the greys that MuPDF, PDFium,
Poppler, Pillow and ImageMagick render, which are in
`test/resources/greyscale.json`. To write those again, with Poppler and
ImageMagick installed:

```bash
python3 -m venv .venv
.venv/bin/pip install pymupdf pypdfium2 pillow
.venv/bin/python dev/greyscale.py
```

ClojureScript inlines the fixtures when it compiles, so delete `.shadow-cljs`
before you run the tests in Node after writing one again.


Licence
-------

colour-coded is MIT. Its conversions and gamut mapping follow the sample
code of CSS Color 4, under the W3C Software and Document License. The
matrices of `vision` are those that Machado, Oliveira and Fernandes publish
with their paper. The tests' fixtures come from Colour.js (MIT), the
web-platform-tests (BSD-3-Clause), colour-science (BSD-3-Clause), and
Sharma, Wu and Dalal's test data for CIEDE2000, which they publish for
research with a citation.
