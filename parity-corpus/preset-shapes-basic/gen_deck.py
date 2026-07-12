"""Preset-shape corpus deck: gridded MSO_SHAPE members for one category.

The category is inferred from this script's directory name, so the same
file is copied verbatim into each preset-shapes-* directory.
"""
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent))

from _gen_common import (blank_slide, chunked, die, grid_boxes, new_deck,
                         preset_shape_partition, save)

PER_SLIDE = 24


def main():
    category = HERE.name
    members = preset_shape_partition().get(category)
    if not members:
        die(f"no preset shapes partition for category '{category}'")
    prs = new_deck()
    for chunk in chunked(members, PER_SLIDE):
        slide = blank_slide(prs)
        for member, box in zip(chunk, grid_boxes(PER_SLIDE)):
            slide.shapes.add_shape(member, *box)
    save(prs)
    print(f"{category}: {len(members)} preset shapes")


if __name__ == "__main__":
    main()
