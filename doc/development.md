# Development

```bash
npm install                    # once, for shadow-cljs and Colour.js
clojure -M:test:nrepl          # an nREPL
clojure -X:test                # the tests on the JVM
clojure -M:cljs compile test   # the tests in Node
clojure -T:build jar           # the jar and its pom, in target/
```

[CI](../.github/workflows/test.yml) also runs the Node tests with advanced
optimizations, the JVM tests on Java 11, and clj-kondo. `build.clj` describes how to
release.

The root namespace's docstring states the rules that every namespace keeps to, and
`api_test.cljc` tests them. Add a new function to its tables.


## Trying unreleased changes

Depend on the latest commit on `master` instead of a version. In shadow-cljs, a Git
dependency only works through `deps.edn`, so set `:deps true` in your
`shadow-cljs.edn` too.


## Writing the fixtures again

The tests compare the library with values from other software, kept in
`test/resources`. ClojureScript inlines them when it compiles, so delete `.shadow-cljs`
before you run the tests in Node after writing one again.

`colorjs.json` holds the values of Colour.js, e.g. for a new version of it:

```bash
npm run fixtures
```

`wpt.json` holds the computed values of the web-platform-tests of CSS Color:

```bash
npm run wpt
```

`machado2009.json` holds the paper's matrices and the values of colour-science, for
`vision`:

```bash
python3 -m venv .venv
.venv/bin/pip install colour-science
.venv/bin/python dev/machado.py
```

`greyscale.json` holds the greys that MuPDF, PDFium, Poppler, Pillow and ImageMagick
render, for `greyscale`. It needs Poppler and ImageMagick installed:

```bash
python3 -m venv .venv
.venv/bin/pip install pymupdf pypdfium2 pillow
.venv/bin/python dev/greyscale.py
```
