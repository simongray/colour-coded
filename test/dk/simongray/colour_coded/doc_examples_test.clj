(ns dk.simongray.colour-coded.doc-examples-test
  "A test of the examples in the README and the pages of doc: each result
  that a code block documents with ;; => is the result that the code gives.

  It runs on the JVM only, where the documents are files to read. Each
  document's code runs in order in a namespace of its own. A block without a
  documented result isn't run, and neither is the code after a block's last
  result, since either may need something that a test lacks, e.g. the artwork
  of the README's image example."
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.test :refer [deftest is]]))

(def documents
  "The README and the pages of doc."
  (cons "README.md"
        (for [file  (file-seq (io/file "doc"))
              :when (str/ends-with? (str file) ".md")]
          (str file))))

(defn- blocks
  "The Clojure code blocks of the Markdown `text` that document a result."
  [text]
  (->> (re-seq #"(?s)```clojure\n(.*?)```" text)
       (map second)
       (filter #(re-find #"(?m)^\s*;; =>" %))))

(defn- examples
  "The code `block` as pairs of the code before each documented result and
  the text of that result."
  [block]
  (map rest (re-seq #"(?s)(.*?)\n[ \t]*;; => ([^\n]*)" block)))

(defn- forms
  "The forms that the `code` holds."
  [code]
  (let [reader (java.io.PushbackReader. (java.io.StringReader. code))]
    (doall (take-while #(not= ::end %) (repeatedly #(read {:eof ::end} reader))))))

(defn- expected
  "The `text` of a documented result as data. A number that's cut short with
  an ellipsis, e.g. 9.595…, becomes a map of the ::digits that it shows."
  [text]
  (edn/read-string {:readers {'cut (fn [digits] {::digits digits})}}
                   (str/replace text #"(-?\d+\.\d+)…" "#cut \"$1\"")))

(defn- matches?
  "Whether the value `actual` is the `wanted` one, where a number cut short
  matches any number that begins with its digits."
  [wanted actual]
  (cond
    (::digits wanted)    (str/starts-with? (str actual) (::digits wanted))
    (map? wanted)        (and (map? actual)
                              (= (set (keys wanted)) (set (keys actual)))
                              (every? (fn [[k v]] (matches? v (get actual k))) wanted))
    (sequential? wanted) (and (sequential? actual)
                              (= (count wanted) (count actual))
                              (every? true? (map matches? wanted actual)))
    :else                (= wanted actual)))

(defn- results
  "Each documented result of the Markdown `text`, as a map of its :code, the
  value that it :wants and the :actual one."
  [text]
  (binding [*ns*  (create-ns (gensym "doc-examples-"))
            *out* (java.io.StringWriter.)]
    (refer-clojure)
    (doall
     (for [block         (blocks text)
           [code result] (examples block)]
       {:code   (str/trim code)
        :wants  (expected result)
        :actual (last (map eval (forms code)))}))))

(deftest documented-results-are-what-the-code-gives
  (is (seq (results (slurp "README.md"))) "the README documents some")
  (doseq [path                         documents
          {:keys [code wants actual]} (results (slurp path))]
    (is (matches? wants actual) (str path ": " code))))
