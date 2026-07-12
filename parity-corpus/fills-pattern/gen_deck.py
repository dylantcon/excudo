"""Pattern fills: every MSO_PATTERN preset python-pptx can author."""
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent))

from _gen_common import blank_slide, chunked, grid_boxes, new_deck, save
from pptx.dml.color import RGBColor
from pptx.enum.shapes import MSO_SHAPE

try:
    from pptx.enum.dml import MSO_PATTERN
except ImportError:
    from pptx.enum.dml import MSO_PATTERN_TYPE as MSO_PATTERN

PER_SLIDE = 24


def main():
    patterns = [m for name, m in sorted(MSO_PATTERN.__members__.items())
                if m.value >= 1]
    prs = new_deck()
    for chunk in chunked(patterns, PER_SLIDE):
        slide = blank_slide(prs)
        for pattern, box in zip(chunk, grid_boxes(PER_SLIDE)):
            shape = slide.shapes.add_shape(MSO_SHAPE.RECTANGLE, *box)
            shape.fill.patterned()
            shape.fill.pattern = pattern
            shape.fill.fore_color.rgb = RGBColor(0x1F, 0x4E, 0x79)
            shape.fill.back_color.rgb = RGBColor(0xFF, 0xF2, 0xCC)
            shape.line.fill.background()
    save(prs)
    print(f"fills-pattern: {len(patterns)} patterns")


if __name__ == "__main__":
    main()
