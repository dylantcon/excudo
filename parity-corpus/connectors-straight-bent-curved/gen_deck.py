"""Connector routing: straight / elbow (bent) / curved, in all four
diagonal directions each (exercises flipH/flipV handling)."""
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent))

from _gen_common import blank_slide, grid_boxes, new_deck, save
from pptx.dml.color import RGBColor
from pptx.enum.shapes import MSO_CONNECTOR
from pptx.util import Pt

KINDS = [MSO_CONNECTOR.STRAIGHT, MSO_CONNECTOR.ELBOW, MSO_CONNECTOR.CURVE]
# (dx, dy) sign pairs: down-right, down-left, up-right, up-left
DIRECTIONS = [(1, 1), (-1, 1), (1, -1), (-1, -1)]


def main():
    prs = new_deck()
    slide = blank_slide(prs)
    cells = list(grid_boxes(12, cols=4))
    i = 0
    for kind in KINDS:
        for dx, dy in DIRECTIONS:
            left, top, w, h = cells[i]
            x1 = left if dx > 0 else left + w
            x2 = left + w if dx > 0 else left
            y1 = top if dy > 0 else top + h
            y2 = top + h if dy > 0 else top
            conn = slide.shapes.add_connector(kind, x1, y1, x2, y2)
            conn.line.width = Pt(2.5)
            conn.line.color.rgb = RGBColor(0x1F, 0x4E, 0x79)
            i += 1
    save(prs)


if __name__ == "__main__":
    main()
