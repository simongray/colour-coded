colour-coded
============

[![Clojars Project](https://img.shields.io/clojars/v/dk.simongray/colour-coded.svg)](https://clojars.org/dk.simongray/colour-coded)

Give colour-coded one colour, or one image, e.g. a podcast's artwork, and it
gives you the colours of a whole site or app: light and dark, ready to use as
CSS or as hex colours for a native app. It works in Clojure and ClojureScript.

- Text stays readable whatever colour you start from, and a report checks every
  pair.
- The CSS follows the reader's light or dark mode and contrast settings.
- Palettes and themes hold up on e-readers, in grey and in colour.
- Smaller tools are there when you need them: contrast, mixing, harmonies, the
  colours of an image, and how colours look with a colour vision deficiency.


Getting started
---------------

You need Clojure 1.11+ and Java 11+. Add colour-coded to the `:deps` in your
`deps.edn`:

```clojure
dk.simongray/colour-coded {:mvn/version "0.2.0"}
```

Start from one colour:

```clojure
(require '[dk.simongray.colour-coded.theme :as theme])

(def t (theme/from-seed "#24476b"))

;; each pair of colours that must contrast, if it doesn't
(remove :ok? (theme/report t))
;; => ()

;; as hex colours, e.g. for React Native
(get-in (theme/hex t) [:dark :accent])
;; => "#9cbce7"

;; as custom properties for a web page
(println (theme/css t))
;; :root {
;;   color-scheme: light dark;
;;   accent-color: var(--accent);
;;   --accent: light-dark(#416187, #9cbce7);
;;   --background: light-dark(#ffffff, #16181a);
;;   …
```

A theme has ten roles, in words that a beginner knows: the background, a surface
on it, text, muted text, the border of a control, a divider, the accent of the
main control, the text on the accent, links and the focus ring.
`theme/relative-css` writes CSS that derives them all from one custom property, so
that a page changes its theme by setting it. `theme/design-tokens` writes them as
design tokens, e.g. for Style Dictionary.

To start from an image instead, pass the bytes of its file. In ClojureScript,
this returns a promise.

```clojure
(def artwork
  (java.nio.file.Files/readAllBytes
   (java.nio.file.Path/of "artwork.jpg" (make-array String 0))))

(def t (theme/from-image artwork))
```


On e-paper
----------

For colours that work on any e-reader, take as many as you need from the front
of `epaper/palette`:

```clojure
(require '[dk.simongray.colour-coded :as cc]
         '[dk.simongray.colour-coded.epaper :as epaper])

(map (comp cc/hex :colour) (epaper/palette))
;; => ("#003b78" "#ffc330" "#b3485b" "#5cadad")
```

[How to choose colours for e-paper](doc/e-paper.md) covers whole themes, colours
of your own, and what to watch out for on a panel.


Smaller tools
-------------

Every function takes a CSS colour, and returns nil for anything else:

```clojure
(require '[dk.simongray.colour-coded.contrast :as contrast]
         '[dk.simongray.colour-coded.mix :as mix]
         '[dk.simongray.colour-coded.target :as target]
         '[dk.simongray.colour-coded.vision :as vision])

;; the contrast of two colours, by WCAG 2.2
(contrast/ratio "#24476b" "white")
;; => 9.595…

;; a colour at the lightness that gives it the contrast text needs
(cc/hex (target/at-contrast "#c8382d" "white"))
;; => "#d44437"

;; halfway between two colours
(cc/hex (mix/mix "red" "blue"))
;; => "#8c53a2"

;; how far apart colours stay with each colour vision deficiency
(vision/least-difference ["#e00000" "#008000" "#4080ff"])
;; => {:normal 0.343…, :protan 0.115…, :deutan 0.071…, :tritan 0.132…}

(cc/hex "not a colour")
;; => nil
```


Documentation
-------------

- [How to choose colours for e-paper](doc/e-paper.md): a palette, a theme and
  colours of your own, on any e-reader or on a device you know.
- [API reference](https://cljdoc.org/d/dk.simongray/colour-coded): every namespace
  and function.
- [Why colour-coded works the way it does](doc/decisions.md): the decisions behind
  the library, and what's still to come.
- [Development](doc/development.md): the tests, their fixtures, and how to try
  unreleased changes.


A demo
------

To see every part of the library applied to examples, drawn as colours, open the
notebook in `notebooks/demo.clj` with [Clerk](https://clerk.vision) from a checkout
of this repository. This opens it at http://localhost:7777:

```bash
clojure -X:demo
```

`clojure -X:demo:static` writes it as a static page in `target/demo` instead.


Licence
-------

colour-coded is MIT. Its conversions and gamut mapping follow the sample
code of CSS Color 4, under the W3C Software and Document License. The
matrices of `vision` are those that Machado, Oliveira and Fernandes publish
with their paper. The tests' fixtures come from Colour.js (MIT), the
web-platform-tests (BSD-3-Clause), colour-science (BSD-3-Clause), and
Sharma, Wu and Dalal's test data for CIEDE2000, which they publish for
research with a citation.
