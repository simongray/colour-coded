"""Write test/resources/composite.json: what ColorAide computes when it
composites each of a fixed set of colours over each of them, in a few spaces.

ColorAide is an independent implementation in Python of Compositing and
Blending Level 1. Colour.js, which writes the other fixtures of the core,
doesn't composite, and a fixture that the library wrote itself would test
nothing, so this can't be Clojure. Nothing in src or CI runs it.

ColorAide only composites in XYZ and in spaces of red, green and blue, so
the tests cover Oklab and Lab without it.

    python3 -m venv .venv
    .venv/bin/pip install coloraide
    .venv/bin/python dev/composite.py
"""

import json

import coloraide
from coloraide import Color

COLOURS = ["#24476b",
           "white",
           # the text of the W3C's ACT rule afw4f7
           "rgb(0 0 0 / 0.3)",
           "hsl(210 50% 40% / 0.6)",
           "oklch(0.7 0.15 150 / 0.7)",
           "lab(40 20 -30 / 0.9)",
           # outside sRGB
           "color(display-p3 0 1 0 / 0.5)",
           # an alpha of 0, without and with components
           "transparent",
           "rgb(255 0 0 / 0)",
           # a missing component, a missing hue and a missing alpha
           "rgb(none 30 60 / 0.4)",
           "oklch(0.6 0.1 none / 0.8)",
           "rgb(10 200 30 / none)"]
SPACES = ["srgb", "srgb-linear", "display-p3", "xyz-d65"]


def check():
    """Fail unless ColorAide composites in sRGB when no space is named, which
    its documentation doesn't say."""
    for a in COLOURS:
        for b in COLOURS:
            unnamed = Color.layer([a, b])
            named = Color.layer([a, b], space="srgb")
            assert unnamed.space() == "srgb", (a, b)
            assert unnamed.coords() == named.coords(), (a, b)
            assert unnamed.alpha() == named.alpha(), (a, b)


def composite(a, b, space):
    """What ColorAide computes for the colour `a` over `b` in the `space`.
    Its list of layers starts with the one on top."""
    over = Color.layer([a, b], space=space)
    return {"a": a,
            "b": b,
            "space": space,
            "coords": over.coords(),
            "alpha": over.alpha()}


if __name__ == "__main__":
    check()
    composites = [composite(a, b, s)
                  for s in SPACES for a in COLOURS for b in COLOURS]
    with open("test/resources/composite.json", "w") as f:
        json.dump({"source": "ColorAide " + coloraide.__version__,
                   "composites": composites},
                  f, indent=1, allow_nan=False)
