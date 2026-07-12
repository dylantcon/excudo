"""Arrowheads: every a:headEnd/a:tailEnd type at three sizes."""
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent))

from _gen_common import blank_slide, grid_boxes, new_deck, save, set_line_ends
from pptx.dml.color import RGBColor
from pptx.enum.shapes import MSO_CONNECTOR
from pptx.util import Pt

TYPES = ["triangle", "stealth", "diamond", "oval", "arrow"]
SIZES = ["sm", "med", "lg"]


def main():
    prs = new_deck()
    slide = blank_slide(prs)
    cells = list(grid_boxes(15, cols=3))
    i = 0
    for kind in TYPES:
        for size in SIZES:
            left, top, w, h = cells[i]
            conn = slide.shapes.add_connector(
                MSO_CONNECTOR.STRAIGHT, left, top + h // 2, left + w, top + h // 2)
            conn.line.width = Pt(2.25)
            conn.line.color.rgb = RGBColor(0x1F, 0x4E, 0x79)
            set_line_ends(conn, head=kind, tail=kind, size=size)
            i += 1
    save(prs)


if __name__ == "__main__":
    main()
