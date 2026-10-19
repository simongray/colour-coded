"""Write test/resources/machado2009.json: the matrices of Machado, Oliveira
and Fernandes (2009), read from Table 1 of their page, and what
colour-science computes with them for a fixed set of colours.

colour-science is an independent implementation in Python. Between the
paper's steps of severity, it extrapolates from the step above, e.g. at 0.15
from 0.2 and 0.3, where the paper interpolates between 0.1 and 0.2, so the
simulations here stay on the steps.

    python3 -m venv .venv
    .venv/bin/pip install colour-science
    .venv/bin/python dev/machado.py
"""

import html
import json
import re
import urllib.request
import warnings

import numpy as np
from colour import cctf_decoding
from colour.blindness import CVD_MATRICES_MACHADO2010, matrix_cvd_Machado2009

URL = "https://www.inf.ufrgs.br/~oliveira/pubs_files/CVD_Simulation/CVD_Simulation.html"
NAMES = {"protan": "Protanomaly", "deutan": "Deuteranomaly", "tritan": "Tritanomaly"}
COLOURS = ["#ff0000", "#00ff00", "#0000ff", "#ffff00", "#00ffff", "#ff00ff",
           "#ffffff", "#808080", "#000000", "#24476b", "#a90000", "#f0c040",
           "#2e8b57", "#d2691e"]
SEVERITIES = [0.3, 0.5, 1.0]


def paper_matrices():
    """Table 1 of the paper's page: for each severity from 0 to 1 in steps
    of 0.1, a row of nine numbers for each deficiency, row by row."""
    with urllib.request.urlopen(URL) as response:
        page = response.read().decode("utf-8")
    text = html.unescape(re.sub(r"<[^>]+>", " ", page))
    table = text[text.find("Table 1"):]
    numbers = re.findall(r"-?\d+\.\d+", table[table.find("Tritanomaly"):])
    matrices = {d: [] for d in NAMES}
    for row in range(11):
        cells = [float(x) for x in numbers[row * 28 + 1:row * 28 + 28]]
        for i, d in enumerate(NAMES):
            m = cells[i * 9:i * 9 + 9]
            matrices[d].append([m[0:3], m[3:6], m[6:9]])
    return matrices


def check(matrices):
    """Fail unless colour-science's matrices are the paper's."""
    for d, ms in matrices.items():
        for i, m in enumerate(ms):
            theirs = CVD_MATRICES_MACHADO2010[NAMES[d]][i / 10]
            assert np.array_equal(theirs, np.array(m)), (d, i / 10)


def simulation(colour, deficiency, severity):
    """What colour-science computes for the hex `colour`, in linear sRGB."""
    srgb = np.array([int(colour[i:i + 2], 16) / 255 for i in (1, 3, 5)])
    m = matrix_cvd_Machado2009(NAMES[deficiency], severity)
    return {"colour": colour,
            "deficiency": deficiency,
            "severity": severity,
            "srgb-linear": list(m @ cctf_decoding(srgb, function="sRGB"))}


if __name__ == "__main__":
    # colour-science warns that the paper models tritanomaly as an
    # approximation; the namespace's docstring says so
    warnings.filterwarnings("ignore")
    matrices = paper_matrices()
    check(matrices)
    simulations = [simulation(c, d, s)
                   for c in COLOURS for d in NAMES for s in SEVERITIES]
    with open("test/resources/machado2009.json", "w") as f:
        json.dump({"source": URL,
                   "matrices": matrices,
                   "simulations": simulations},
                  f, indent=1)
