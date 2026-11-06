"""Write test/resources/greyscale.json: the grey that real PDF and image
engines give a fixed set of colours, which the tests of greyscale compare with
its conversions.

It writes a one-page PDF of flat swatches, renders the page to greyscale with
each engine, and reads the middle of each swatch. The same swatches go through
the image converters as a PNG.

    python3 -m venv .venv
    .venv/bin/pip install pymupdf pypdfium2 pillow
    .venv/bin/python dev/greyscale.py

It also needs pdftoppm, from Poppler, and magick, from ImageMagick, on the path.
"""
import json
import os
import subprocess
import tempfile

import pymupdf
import pypdfium2
from PIL import Image

SIZE = 8  # points, and pixels at 72 dpi

NAMED = ["#ff0000", "#00ff00", "#0000ff", "#ffff00", "#00ffff", "#ff00ff",
         "#000000", "#555555", "#808080", "#cccccc", "#ffffff",
         # Okabe and Ito, and Paul Tol's high-contrast scheme
         "#e69f00", "#56b4e9", "#009e73", "#f0e442", "#0072b2", "#d55e00",
         "#cc79a7", "#004488", "#ddaa33", "#bb5566",
         # KOReader's highlights
         "#ff3300", "#ff8800", "#ffff33", "#00aa66", "#88ff77", "#00ffee",
         "#0066ff", "#ee00ff",
         # muted colours for a greyscale panel
         "#355b92", "#3357b2", "#f5c762", "#994211", "#6c2309",
         # epaper/palette, its colours and then its highlights
         "#003b78", "#ffc330", "#b3485b", "#5cadad",
         "#bddaff", "#f7daa1", "#ffccd1", "#9ceeee"]

# the web's 216 safe colours, to cover the cube of sRGB evenly
STEPS = ["00", "33", "66", "99", "cc", "ff"]
CUBE = [f"#{r}{g}{b}" for r in STEPS for g in STEPS for b in STEPS]

COLOURS = list(dict.fromkeys(NAMED + CUBE))
COLUMNS = 16
ROWS = (len(COLOURS) + COLUMNS - 1) // COLUMNS


def rgb(colour):
    """The red, green and blue of the hex `colour`, from 0 to 255."""
    return tuple(int(colour[i:i + 2], 16) for i in (1, 3, 5))


def place(i):
    """The column and row of the swatch at the index `i`."""
    return i % COLUMNS, i // COLUMNS


def pdf():
    """A one-page PDF, as bytes, of a DeviceRGB swatch for each colour."""
    height = ROWS * SIZE
    fills = []
    for i, colour in enumerate(COLOURS):
        r, g, b = rgb(colour)
        x, y = place(i)
        fills.append(f"{r / 255:.6f} {g / 255:.6f} {b / 255:.6f} rg "
                     f"{x * SIZE} {height - (y + 1) * SIZE} {SIZE} {SIZE} re f")
    stream = "\n".join(fills)
    objects = ["<< /Type /Catalog /Pages 2 0 R >>",
               "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
               f"<< /Type /Page /Parent 2 0 R /Contents 4 0 R /Resources << >> "
               f"/MediaBox [0 0 {COLUMNS * SIZE} {height}] >>",
               f"<< /Length {len(stream)} >>\nstream\n{stream}\nendstream"]
    out = bytearray(b"%PDF-1.7\n")
    offsets = []
    for n, body in enumerate(objects, 1):
        offsets.append(len(out))
        out += f"{n} 0 obj\n{body}\nendobj\n".encode()
    xref = len(out)
    out += f"xref\n0 {len(objects) + 1}\n0000000000 65535 f \n".encode()
    out += b"".join(f"{o:010d} 00000 n \n".encode() for o in offsets)
    out += (f"trailer\n<< /Size {len(objects) + 1} /Root 1 0 R >>\n"
            f"startxref\n{xref}\n%%EOF\n").encode()
    return bytes(out)


def png(path):
    """Write a PNG of a swatch for each colour to `path`."""
    image = Image.new("RGB", (COLUMNS * SIZE, ROWS * SIZE), "white")
    for i, colour in enumerate(COLOURS):
        x, y = place(i)
        image.paste(rgb(colour), (x * SIZE, y * SIZE, (x + 1) * SIZE, (y + 1) * SIZE))
    image.save(path)


def swatches(image):
    """The grey at the middle of each swatch of the greyscale `image`."""
    return [image.getpixel((x * SIZE + SIZE // 2, y * SIZE + SIZE // 2))
            for x, y in map(place, range(len(COLOURS)))]


def mupdf(path, icc):
    """The greys of the PDF at `path` in MuPDF, with or without its ICC workflow."""
    pymupdf.TOOLS.set_icc(icc)
    pix = pymupdf.open(path)[0].get_pixmap(colorspace=pymupdf.csGRAY, dpi=72, alpha=False)
    return swatches(Image.frombytes("L", (pix.width, pix.height), pix.samples))


def pdfium(path):
    """The greys of the PDF at `path` in PDFium."""
    page = pypdfium2.PdfDocument(path)[0]
    return swatches(page.render(scale=1, grayscale=True).to_pil().convert("L"))


def poppler(path, directory):
    """The greys of the PDF at `path` in Poppler's pdftoppm."""
    base = os.path.join(directory, "poppler")
    subprocess.run(["pdftoppm", "-gray", "-r", "72", "-png", "-singlefile", path, base],
                   check=True)
    return swatches(Image.open(base + ".png").convert("L"))


def magick(path, directory, *args):
    """The greys of the PNG at `path` in ImageMagick, by the `args`."""
    out = os.path.join(directory, "magick.png")
    subprocess.run(["magick", path, *args, "-depth", "8", out], check=True)
    return swatches(Image.open(out).convert("L"))


def version(*command):
    """The first line that `command` prints about its version."""
    done = subprocess.run(command, capture_output=True, text=True, check=True)
    return (done.stdout or done.stderr).splitlines()[0]


def main():
    with tempfile.TemporaryDirectory() as directory:
        pdf_path = os.path.join(directory, "swatches.pdf")
        png_path = os.path.join(directory, "swatches.png")
        with open(pdf_path, "wb") as f:
            f.write(pdf())
        png(png_path)
        engines = {
            "mupdf-icc":    {"conversion": "icc",
                             "greys": mupdf(pdf_path, True)},
            "mupdf":        {"conversion": "pdf",
                             "greys": mupdf(pdf_path, False)},
            "pdfium":       {"conversion": "pdf",
                             "greys": pdfium(pdf_path)},
            "poppler":      {"conversion": "pdf",
                             "greys": poppler(pdf_path, directory)},
            "pillow":       {"conversion": "rec601",
                             "greys": swatches(Image.open(png_path).convert("L"))},
            "magick":       {"conversion": "rec709",
                             "greys": magick(png_path, directory, "-colorspace", "Gray")},
            "magick-rec601": {"conversion": "rec601",
                              "greys": magick(png_path, directory, "-grayscale", "Rec601Luma")},
        }
    versions = {"pymupdf": pymupdf.__version__,
                "pypdfium2": pypdfium2.PYPDFIUM_INFO.version,
                "poppler": version("pdftoppm", "-v"),
                "imagemagick": version("magick", "-version")}
    target = os.path.join(os.path.dirname(__file__), "..", "test", "resources", "greyscale.json")
    with open(target, "w") as f:
        json.dump({"versions": versions, "colours": COLOURS, "engines": engines}, f, indent=1)
        f.write("\n")
    print(f"{len(COLOURS)} colours through {len(engines)} engines, to {os.path.normpath(target)}")


if __name__ == "__main__":
    main()
