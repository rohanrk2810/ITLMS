#!/usr/bin/env python3
"""
Builds the institute data sheet (a PDF) from the field list the web app uses.

    python deploy/scripts/generate-setup-sheet.py

Reads frontend/src/lib/institute-setup-fields.json (the one list that also makes the ChatGPT prompt, the example file
and the checks in Customize > Quick setup) and writes frontend/public/institute-data-sheet.pdf, which the Quick setup
tab offers as "Download data sheet (PDF)". Run it again whenever the field list changes. Needs PyMuPDF.

The pages are drawn directly (no HTML layout), so a table row is never split and the heading row of a table is
repeated on the next page.
"""
import json
import os

try:
    import pymupdf as fitz
except ImportError:  # older PyMuPDF
    import fitz

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
FIELDS = os.path.join(ROOT, "frontend", "src", "lib", "institute-setup-fields.json")
OUT = os.path.join(ROOT, "frontend", "public", "institute-data-sheet.pdf")

PAGE_W, PAGE_H = 595, 842
MARGIN_X, MARGIN_TOP, MARGIN_BOTTOM = 40, 44, 44
CONTENT_W = PAGE_W - 2 * MARGIN_X
PURPLE = (0x2B / 255, 0x02 / 255, 0x55 / 255)
INK = (0.13, 0.13, 0.13)
MUTED = (0.42, 0.42, 0.42)
LINE = (0.8, 0.8, 0.8)
RED = (0.7, 0.15, 0.12)
WHITE = (1, 1, 1)

REGULAR = fitz.Font("helv")
BOLD = fitz.Font("hebo")
COLUMNS = [("What", 0.19), ("What to give", 0.34), ("Example", 0.25), ("Your answer", 0.22)]


def wrap(text, font, size, width):
    """Splits text into lines that fit the width."""
    lines = []
    for paragraph in str(text).split("\n"):
        current = ""
        for word in paragraph.split():
            trial = word if not current else current + " " + word
            if font.text_length(trial, fontsize=size) <= width:
                current = trial
            else:
                if current:
                    lines.append(current)
                current = word
        lines.append(current)
    return lines or [""]


def example_text(value):
    """A short, readable version of an example value."""
    if isinstance(value, bool):
        return "true" if value else "false"
    if isinstance(value, list):
        parts = [example_text(v) for v in value[:2]]
        return "; ".join(parts) + (" ..." if len(value) > 2 else "")
    if isinstance(value, dict):
        return ", ".join(f"{k}: {example_text(v)}" for k, v in value.items() if v != "")
    return str(value)


class Sheet:
    def __init__(self):
        self.doc = fitz.open()
        self.page = None
        self.y = 0
        self.new_page()

    def new_page(self):
        self.page = self.doc.new_page(width=PAGE_W, height=PAGE_H)
        self.y = MARGIN_TOP

    def room(self, height):
        return self.y + height <= PAGE_H - MARGIN_BOTTOM

    def text(self, x, baseline, value, size, font=REGULAR, color=INK):
        name = "hebo" if font is BOLD else "helv"
        self.page.insert_text((x, baseline), value, fontsize=size, fontname=name, color=color)

    def paragraph(self, text, size, color=INK, font=REGULAR, width=CONTENT_W, x=MARGIN_X, gap=0):
        leading = size * 1.35
        for line in wrap(text, font, size, width):
            if not self.room(leading):
                self.new_page()
            self.text(x, self.y + size, line, size, font, color)
            self.y += leading
        self.y += gap

    def heading(self, title, note):
        # A heading is never left alone at the foot of a page: it needs room for the table header and a row.
        if not self.room(120):
            self.new_page()
        self.y += 10
        self.text(MARGIN_X, self.y + 14, title, 13.5, BOLD, PURPLE)
        self.y += 20
        self.paragraph(note, 8.8, MUTED, gap=4)

    def table_header(self):
        self.page.draw_rect(fitz.Rect(MARGIN_X, self.y, MARGIN_X + CONTENT_W, self.y + 17), color=None, fill=PURPLE)
        x = MARGIN_X
        for label, share in COLUMNS:
            self.text(x + 4, self.y + 12, label, 8.8, BOLD, WHITE)
            x += CONTENT_W * share
        self.y += 17

    def row(self, field):
        pad, size = 5, 8.6
        widths = [CONTENT_W * share - 8 for _, share in COLUMNS]
        label = wrap(field["label"], BOLD, size, widths[0])
        if field.get("required"):
            label.append("(required)")
        path = wrap(field["path"], REGULAR, 7, widths[0])
        limit = f" (up to {field['limit']})" if field.get("limit") and not field.get("list") else ""
        what = wrap(field["what"] + limit, REGULAR, size, widths[1])
        example = wrap(example_text(field["example"]), REGULAR, 8.2, widths[2])
        leading = size * 1.3
        height = max(len(label) * leading + len(path) * 8.4 + 2 * pad, len(what) * leading + 2 * pad,
                     len(example) * 8.2 * 1.3 + 2 * pad, 34)
        if not self.room(height):
            self.new_page()
            self.table_header()
        top = self.y
        # label and its field name
        y = top + pad + size
        for line in label:
            self.text(MARGIN_X + 4, y, line, size, RED_IF_REQ(line), RED if line == "(required)" else INK)
            y += leading
        for line in path:
            self.text(MARGIN_X + 4, y - 1, line, 7, REGULAR, MUTED)
            y += 8.4
        # what to give
        y = top + pad + size
        for line in what:
            self.text(MARGIN_X + CONTENT_W * COLUMNS[0][1] + 4, y, line, size)
            y += leading
        # example
        y = top + pad + 8.2
        for line in example:
            self.text(MARGIN_X + CONTENT_W * (COLUMNS[0][1] + COLUMNS[1][1]) + 4, y, line, 8.2, REGULAR, (0.27, 0.27, 0.27))
            y += 8.2 * 1.3
        # the answer cell: a box to write in
        ax = MARGIN_X + CONTENT_W * (1 - COLUMNS[3][1])
        self.page.draw_rect(fitz.Rect(ax + 3, top + 4, MARGIN_X + CONTENT_W - 3, top + height - 4), color=LINE, width=0.5)
        self.y = top + height
        self.page.draw_line((MARGIN_X, self.y), (MARGIN_X + CONTENT_W, self.y), color=LINE, width=0.6)


def RED_IF_REQ(line):
    return BOLD


def build(catalog):
    sheet = Sheet()
    sheet.text(MARGIN_X, sheet.y + 20, catalog["title"], 22, BOLD, PURPLE)
    sheet.y += 32
    sheet.paragraph(catalog["intro"], 9.6, MUTED, gap=8)

    # the "how to fill this in" box
    rules = list(catalog["rules"]) + [
        "Write your answers in the last column, or give the same facts to ChatGPT with the prompt file "
        "(Customize > Quick setup > Download prompt file). ChatGPT returns a .json file, which you upload on the same screen."
    ]
    box_top = sheet.y
    sheet.y += 8
    sheet.text(MARGIN_X + 8, sheet.y + 10, "How to fill this in", 10, BOLD)
    sheet.y += 17
    for rule in rules:
        lines = wrap(rule, REGULAR, 8.8, CONTENT_W - 28)
        for i, line in enumerate(lines):
            if i == 0:
                sheet.text(MARGIN_X + 10, sheet.y + 8.8, "-", 8.8)
            sheet.text(MARGIN_X + 20, sheet.y + 8.8, line, 8.8)
            sheet.y += 8.8 * 1.35
        sheet.y += 2
    sheet.y += 6
    sheet.page.draw_rect(fitz.Rect(MARGIN_X, box_top, MARGIN_X + CONTENT_W, sheet.y), color=PURPLE, width=0.9)
    sheet.y += 6

    for section in catalog["sections"]:
        sheet.heading(section["title"], section["note"])
        sheet.table_header()
        for field in section["fields"]:
            sheet.row(field)
        sheet.y += 6

    sheet.heading("Pictures", "")
    sheet.paragraph(
        "Pictures are not part of the file. Add the logo and the favicon on the Institute tab, and the front page picture, "
        "the gallery and the photos of trainers and students on the Website tab. A trainer, partner or student whose name "
        "stays the same keeps the photo already added.", 9, MUTED)

    # page numbers
    total = len(sheet.doc)
    for number, page in enumerate(sheet.doc, start=1):
        page.insert_text((PAGE_W - MARGIN_X - 40, PAGE_H - 24), f"Page {number} of {total}", fontsize=8, fontname="helv", color=MUTED)
        page.insert_text((MARGIN_X, PAGE_H - 24), catalog["title"], fontsize=8, fontname="helv", color=MUTED)
    return sheet.doc


def main():
    with open(FIELDS, encoding="utf-8") as fh:
        catalog = json.load(fh)
    doc = build(catalog)
    doc.save(OUT, deflate=True)
    print(f"wrote {OUT} ({len(doc)} pages)")


if __name__ == "__main__":
    main()
