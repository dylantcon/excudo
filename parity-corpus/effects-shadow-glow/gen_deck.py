"""Shape effects: outer shadow (with real blur), inner shadow, glow,
reflection, soft edge — one effect per shape."""
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent))

from _gen_common import append_effects, blank_slide, grid_boxes, new_deck, save
from pptx.enum.shapes import MSO_SHAPE

EFFECTS = [
    # blurred outer shadow, down-right
    ('<a:effectLst><a:outerShdw blurRad="190500" dist="152400" dir="2700000" '
     'algn="tl" rotWithShape="0"><a:srgbClr val="000000">'
     '<a:alpha val="45000"/></a:srgbClr></a:outerShdw></a:effectLst>'),
    # tight sharp-ish shadow (small blur) for contrast
    ('<a:effectLst><a:outerShdw blurRad="25400" dist="101600" dir="5400000" '
     'algn="tl" rotWithShape="0"><a:srgbClr val="1F4E79">'
     '<a:alpha val="60000"/></a:srgbClr></a:outerShdw></a:effectLst>'),
    # inner shadow
    ('<a:effectLst><a:innerShdw blurRad="114300" dist="76200" dir="13500000">'
     '<a:srgbClr val="000000"><a:alpha val="55000"/></a:srgbClr>'
     '</a:innerShdw></a:effectLst>'),
    # glow
    ('<a:effectLst><a:glow rad="139700"><a:schemeClr val="accent1">'
     '<a:alpha val="60000"/></a:schemeClr></a:glow></a:effectLst>'),
    # reflection
    ('<a:effectLst><a:reflection blurRad="6350" stA="50000" endA="300" '
     'endPos="55000" dir="5400000" sy="-100000" algn="bl" '
     'rotWithShape="0"/></a:effectLst>'),
    # soft edge
    ('<a:effectLst><a:softEdge rad="127000"/></a:effectLst>'),
]

SHAPES = [MSO_SHAPE.RECTANGLE, MSO_SHAPE.ROUNDED_RECTANGLE, MSO_SHAPE.OVAL,
          MSO_SHAPE.RECTANGLE, MSO_SHAPE.RECTANGLE, MSO_SHAPE.OVAL]


def main():
    prs = new_deck()
    slide = blank_slide(prs)
    cells = list(grid_boxes(6, cols=3))
    for effect, preset, box in zip(EFFECTS, SHAPES, cells):
        # inset so shadows/reflections have room inside the cell
        left, top, w, h = box
        shape = slide.shapes.add_shape(
            preset, left + w // 6, top + h // 6, w * 2 // 3, h * 2 // 3)
        append_effects(shape, effect)
    save(prs)


if __name__ == "__main__":
    main()
