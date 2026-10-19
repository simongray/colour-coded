// Writes test/resources/wpt.json: the computed values that the
// web-platform-tests of CSS Color expect, as pairs of a CSS colour and its
// computed value, or a vector of the values that pass. It runs each test
// file's own scripts with the test functions stubbed, so that its loops and
// template literals list every case. Run it with `npm run wpt`.
//
// The web-platform-tests are BSD-3-Clause, © web-platform-tests
// contributors: https://github.com/web-platform-tests/wpt

import fs from "node:fs";
import vm from "node:vm";

const base = "https://raw.githubusercontent.com/web-platform-tests/wpt/master/css/css-color/parsing/";
const files = ["color-computed-rgb.html", "color-computed-hsl.html",
  "color-computed-hwb.html", "color-computed-lab.html",
  "color-computed-color-function.html", "color-computed-hex-color.html",
  "color-computed-named-color.html", "color-computed-color-mix-function.html",
  "color-computed-none.html"];

const cases = [];
for (const file of files) {
  const html = await (await fetch(base + file)).text();
  const scripts = [...html.matchAll(/<script>([\s\S]*?)<\/script>/g)].map(m => m[1]);
  const record = (spec, expected) => cases.push({ file, spec, expected });
  const context = vm.createContext({
    test_computed_value: (property, spec, expected) => record(spec, expected ?? spec),
    fuzzy_test_computed_color: (spec, expected) => record(spec, expected ?? spec),
    test: () => {}, setup: () => {}, assert_equals: () => {},
    document: { getElementById: () => ({ style: {} }) },
  });
  for (const script of scripts) {
    try { vm.runInContext(script, context); }
    catch (e) { console.warn(`${file}: ${e.message}`); }
  }
}

fs.writeFileSync(new URL("../test/resources/wpt.json", import.meta.url),
  JSON.stringify({ fetched: new Date().toISOString().slice(0, 10), cases }));
console.log(`Wrote ${cases.length} cases of the web-platform-tests`);
