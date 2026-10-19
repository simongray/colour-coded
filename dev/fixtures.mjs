// Writes test/resources/colorjs.json: the values that Colour.js, the
// reference implementation of CSS Color 4, gives for a fixed set of
// colours, which the tests compare with colour-coded's on the JVM and in
// Node. Run it with `npm run fixtures` after a change of Colour.js's
// version, and commit the result.
//
// Colour.js is MIT, © Lea Verou and Chris Lilley: https://colorjs.io

import fs from "node:fs";
import Color from "colorjs.io";

const version = JSON.parse(
  fs.readFileSync(new URL("../node_modules/colorjs.io/package.json", import.meta.url))).version;

// colour-coded's id of each space, and Colour.js's
const spaces = {
  "srgb": "srgb", "srgb-linear": "srgb-linear", "display-p3": "p3",
  "display-p3-linear": "p3-linear", "a98-rgb": "a98rgb",
  "a98-rgb-linear": "a98rgb-linear", "prophoto-rgb": "prophoto",
  "prophoto-rgb-linear": "prophoto-linear", "rec2020": "rec2020",
  "rec2020-linear": "rec2020-linear", "xyz-d65": "xyz-d65",
  "xyz-d50": "xyz-d50", "lab": "lab", "lch": "lch", "oklab": "oklab",
  "oklch": "oklch", "hsl": "hsl", "hwb": "hwb",
};

// mulberry32, so that the colours are the same on every run
function random(seed) {
  return () => {
    seed |= 0; seed = seed + 0x6D2B79F5 | 0;
    let t = Math.imul(seed ^ seed >>> 15, 1 | seed);
    t = t + Math.imul(t ^ t >>> 7, 61 | t) ^ t;
    return ((t ^ t >>> 14) >>> 0) / 4294967296;
  };
}

const rand = random(2026);
const coords = c => c.coords.map(x => (x === null || Number.isNaN(x)) ? null : x);

// colours inside sRGB, and colours of OKLCH, many of them outside it
const srgb = Array.from({ length: 100 }, () =>
  new Color("srgb", [rand(), rand(), rand()]));
const oklch = Array.from({ length: 100 }, () =>
  new Color("oklch", [0.05 + 0.9 * rand(), 0.37 * rand(), 360 * rand()]));
const colours = [...srgb, ...oklch];

const conversions = colours.map(c => ({
  from: c.space.id === "oklch" ? "oklch" : "srgb",
  coords: coords(c),
  to: Object.fromEntries(Object.entries(spaces).map(([ours, theirs]) =>
    [ours, coords(c.to(theirs))])),
}));

const gamut = oklch.map(c => ({
  coords: coords(c),
  "in-gamut": c.inGamut("srgb"),
  mapped: coords(c.toGamut({ space: "srgb", method: "css" })),
  clipped: coords(c.toGamut({ space: "srgb", method: "clip" })),
}));

const pairs = Array.from({ length: 100 }, (_, i) => [colours[i], colours[199 - i]]);
const differences = pairs.map(([a, b]) => ({
  a: { space: a.space.id, coords: coords(a) },
  b: { space: b.space.id, coords: coords(b) },
  "delta-e": a.deltaE(b, "OK"),
  "delta-e-76": a.deltaE(b, "76"),
  "delta-e-2000": a.deltaE(b, "2000"),
  contrast: a.to("srgb").toGamut({ method: "clip" })
    .contrast(b.to("srgb").toGamut({ method: "clip" }), "WCAG21"),
}));

const mixes = pairs.slice(0, 30).flatMap(([a, b]) =>
  ["oklab", "oklch", "srgb", "lab"].map(space => ({
    a: { space: a.space.id, coords: coords(a) },
    b: { space: b.space.id, coords: coords(b) },
    space,
    mixed: coords(Color.mix(a, b, 0.3, { space, outputSpace: space })),
  })));

const fixtures = { version, conversions, gamut, differences, mixes };
fs.writeFileSync(new URL("../test/resources/colorjs.json", import.meta.url),
  JSON.stringify(fixtures));
console.log(`Wrote the fixtures of Colour.js ${version}`);
