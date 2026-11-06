# Why colour-coded works the way it does

These are the decisions that you couldn't guess from the API, and the reasons for them.
Each namespace also cites its sources in its docstring, and in the code where it makes
a decision.


## Computing colours

The library computes the same colours that a browser computes. It has every colour
space of CSS Color 4, its syntax, `color-mix()` and its gamut mapping, tested against
Colour.js and more than 6,000 of the web-platform-tests.

It depends on Clojure alone. Only the image namespace calls the platform, to decode an
image: ImageIO on the JVM, and the browser's decoder in ClojureScript.

A colour is a map of `:space`, `:coords` and `:alpha`, the shape that Colour.js uses,
and every function takes CSS strings too.

The parser is in `syntax`, since in ClojureScript a namespace called `css` clashes with
the function `css` of the root namespace. The names spell "colour", "quantise" and
"serialise" in British English throughout, but CSS's own words, e.g. `color-mix()`,
keep CSS's spelling.


## Bad input

A function returns nil for what it can't use rather than guessing or throwing, so a
typo in a colour shows up as nil, not as black. A report counts it as a failure instead,
since finding what's wrong is a report's job. The root namespace's docstring states this
rule and the others that every namespace keeps to.


## Themes

A theme takes its lightness from CIELAB's L\*, which keeps contrast to within a few
percent whatever the hue, since CIELAB adapts to the white of D50 where WCAG's luminance
uses D65's. That's why a colour's contrast holds whichever colour or image you start
from. A report checks every pair anyway.

`tone` lowers a colour's chroma in the token's own space, so that its lightness and hue
stay exact, rather than with CSS's gamut mapping. `tone/worst-contrast` checks both that
and the clipping of browsers.

An image with no opaque pixels has no seed, so it has no theme. A made-up grey would
hide that, so pass a colour of your own to `theme/from-seed` for that case.

APCA is left out, since its licence forbids changing it and using it commercially.


## Colour vision

`vision/simulate` has no default deficiency, since there's no general case to assume.
`vision/least-difference` checks them all.

`vision` clips a simulated colour to sRGB. Machado's matrices can give a saturated colour
a negative cone response, which makes its ΔEOK meaningless. The tritan simulation is the
paper's approximation, which doesn't cover tritanopia.


## E-paper

An e-paper panel shows 16 greys, and nothing standardises how a device turns a colour
into one of them. Pure red comes out as 76 of 255 in PDFium, 54 in ImageMagick and 129
in MuPDF. Engines then floor or round to 8 bits. After that, some devices round to the
nearest of the 16 greys, and others keep the top 4 bits. So a colour can shift between
shades from one device to the next, or dither into a stipple.

A colour has a shade when it lands on the same grey by every conversion and each of
those ways of reducing it. By default `epaper` asks for that, at some cost in chroma,
since e-readers don't document their conversions. You can pass the ones you know. They
come from real PDF and image engines and from open source code. No panel has confirmed
a shade yet.

`epaper/palette` has four colours because no more fit 3 shades apart between shades 3
and 12. The range stops there because a dark colour that turns black still shows, but a
pale one nearly vanishes on white. The hues start from Paul Tol's high-contrast scheme,
which he made for a monochrome printout. His own colours can each land on two shades,
so only the hues carry over. Teal sits in the widest gap left, and does better than green
with a red-green deficiency. The hues, the thresholds of `epaper/report` and the number
of colours are judgements, untested on readers.

The numbers for Kaleido 3 in `epaper/panels` are estimates, since E Ink publishes none
and I found no measurements. Gallery 3 is left out for the same reason.


## Still to come

Material 3's HCT and dynamic schemes, ramps, data palettes and historic colour theory.
