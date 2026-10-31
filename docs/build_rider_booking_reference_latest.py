"""Generate the current Word reference from API_REFERENCE.md, the single content source.

Older reference generators are historical. Use this script for releases so
regeneration cannot restore obsolete payment, security or notification contracts.
Requires python-docx; no production connections or credentials are used.
"""
from pathlib import Path
import re
from docx import Document
from docx.shared import Inches, Pt, RGBColor
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.enum.table import WD_TABLE_ALIGNMENT, WD_CELL_VERTICAL_ALIGNMENT
from docx.oxml import OxmlElement
from docx.oxml.ns import qn

ROOT = Path(__file__).resolve().parent
SOURCE = ROOT / "API_REFERENCE.md"
OUT = ROOT / "RIDER_BOOKING_API_AND_DATABASE_REFERENCE_UPDATED.docx"


def inline(paragraph, text):
    for index, part in enumerate(re.split(r"`([^`]+)`", text)):
        run = paragraph.add_run(part)
        if index % 2:
            run.font.name, run.font.size = "Courier New", Pt(9)


def table(doc, lines):
    rows = [[c.strip() for c in s.strip().strip("|").split("|")] for s in lines]
    rows = [rows[0]] + rows[2:]
    count = len(rows[0])
    widths = [0.58, 3.20, 2.72] if count == 3 else [2.5, 4.0]
    result = doc.add_table(rows=0, cols=count)
    result.alignment, result.autofit = WD_TABLE_ALIGNMENT.CENTER, False
    for col, width in zip(result.columns, widths):
        col.width = Inches(width)
    borders = OxmlElement("w:tblBorders")
    for edge in ("top", "left", "bottom", "right", "insideH", "insideV"):
        node = OxmlElement("w:" + edge)
        for key, value in {"val":"single", "sz":"4", "color":"D9D9D9"}.items():
            node.set(qn("w:" + key), value)
        borders.append(node)
    result._tbl.tblPr.append(borders)
    for index, values in enumerate(rows):
        row = result.add_row()
        row._tr.get_or_add_trPr().append(OxmlElement("w:cantSplit"))
        if index == 0:
            row._tr.get_or_add_trPr().append(OxmlElement("w:tblHeader"))
        for column, (cell, value) in enumerate(zip(row.cells, values)):
            cell.width = Inches(widths[column])
            cell.vertical_alignment = WD_CELL_VERTICAL_ALIGNMENT.CENTER
            props = cell._tc.get_or_add_tcPr()
            margins = OxmlElement("w:tcMar")
            for side in ("top", "bottom", "start", "end"):
                margin = OxmlElement("w:" + side)
                margin.set(qn("w:w"), "90")
                margin.set(qn("w:type"), "dxa")
                margins.append(margin)
            props.append(margins)
            if index == 0:
                shade = OxmlElement("w:shd")
                shade.set(qn("w:fill"), "E8EEF5")
                props.append(shade)
            p = cell.paragraphs[0]
            p.paragraph_format.space_after = p.paragraph_format.space_before = Pt(2)
            p.paragraph_format.line_spacing = 1.05
            if count == 3 and column == 0:
                p.alignment = WD_ALIGN_PARAGRAPH.CENTER
            inline(p, value)
            for run in p.runs:
                run.font.size, run.font.bold = Pt(9), index == 0
                run.font.color.rgb = RGBColor(0, 0, 0)
    doc.add_paragraph().paragraph_format.space_after = Pt(2)


def make_document():
    doc = Document()
    section = doc.sections[0]
    section.page_width, section.page_height = Inches(8.5), Inches(11)
    section.top_margin = section.bottom_margin = Inches(0.8)
    section.left_margin = section.right_margin = Inches(1)
    for name, size in (("Normal",11), ("Title",22), ("Heading 1",15), ("Heading 2",12)):
        style = doc.styles[name]
        style.font.name, style.font.size = "Calibri", Pt(size)
        style.font.color.rgb = RGBColor(0, 0, 0)
    # The bundled Word template's Title carries a blue bottom rule; remove it.
    for border in list(doc.styles.element.xpath(".//w:pBdr")):
        border.getparent().remove(border)
    normal = doc.styles["Normal"].paragraph_format
    normal.line_spacing, normal.space_after = 1.12, Pt(6)
    for name in ("Heading 1", "Heading 2"):
        doc.styles[name].paragraph_format.space_before = Pt(13)
        doc.styles[name].paragraph_format.space_after = Pt(6)
    header = section.header.paragraphs[0]
    header.text = "Rider Booking Application   Technical Reference"
    header.runs[0].font.size = Pt(8)
    footer = section.footer.paragraphs[0]
    footer.alignment = WD_ALIGN_PARAGRAPH.RIGHT
    footer.add_run("Updated 31 October 2026   |   Page ").font.size = Pt(8)
    field = OxmlElement("w:fldSimple")
    field.set(qn("w:instr"), "PAGE")
    footer._p.append(field)
    lines, i = SOURCE.read_text(encoding="utf-8").splitlines(), 0
    while i < len(lines):
        line = lines[i]
        if not line.strip():
            i += 1
            continue
        if line.startswith("```"):
            code = []
            i += 1
            while i < len(lines) and not lines[i].startswith("```"):
                code.append(lines[i])
                i += 1
            p = doc.add_paragraph()
            p.paragraph_format.left_indent = Inches(0.12)
            p.paragraph_format.line_spacing = 1.05
            p.paragraph_format.keep_together = True
            run = p.add_run("\n".join(code))
            run.font.name, run.font.size = "Courier New", Pt(8.5)
        elif line.startswith("|"):
            block = []
            while i < len(lines) and lines[i].startswith("|"):
                block.append(lines[i])
                i += 1
            table(doc, block)
            continue
        elif line.startswith("# "):
            doc.add_paragraph(line[2:], "Title")
        elif line.startswith("## "):
            doc.add_heading(line[3:], 1)
        elif line.startswith("### "):
            doc.add_heading(line[4:], 2)
        else:
            p = doc.add_paragraph()
            if re.match(r"^\d+\. ", line):
                p.paragraph_format.left_indent = Inches(0.18)
                p.paragraph_format.first_line_indent = Inches(-0.18)
            inline(p, line)
        i += 1
    doc.core_properties.title = "Rider Booking API and Database Reference"
    doc.core_properties.subject = "production-changes backend contract"
    doc.core_properties.author = "Rider Booking Engineering"
    doc.save(OUT)
    print(OUT)


if __name__ == "__main__":
    make_document()
