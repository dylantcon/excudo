"""Picture fills on shapes (a:blipFill in spPr): stretch and tile, on a
rectangle and on an ellipse (the ellipse also tests clipping the image to
the shape path). The source image is generated deterministically."""
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent))

from _gen_common import blank_slide, grid_boxes, new_deck, picture_fill, save
from PIL import Image
from pptx.enum.shapes import MSO_SHAPE


def make_test_image(path: Path) -> None:
    """A 200x150 image with distinct quadrants + diagonal so stretch vs
    tile vs crop are visually unmistakable."""
    img = Image.new("RGB", (200, 150))
    px = img.load()
    for y in range(150):
        for x in range(200):
            if abs((x * 150 // 200) - y) < 4:
                px[x, y] = (0, 0, 0)
            elif x < 100 and y < 75:
                px[x, y] = (198, 30, 30)
            elif x >= 100 and y < 75:
                px[x, y] = (255, 192, 0)
            elif x < 100:
                px[x, y] = (46, 116, 181)
            else:
                px[x, y] = (84, 130, 53)
    img.save(path)


def main():
    img_path = HERE / "fill-source.png"
    make_test_image(img_path)

    prs = new_deck()
    slide = blank_slide(prs)
    cells = list(grid_boxes(4, cols=2))
    specs = [
        (MSO_SHAPE.RECTANGLE, "stretch"),
        (MSO_SHAPE.RECTANGLE, "tile"),
        (MSO_SHAPE.OVAL, "stretch"),
        (MSO_SHAPE.ROUNDED_RECTANGLE, "tile"),
    ]
    for (preset, mode), box in zip(specs, cells):
        shape = slide.shapes.add_shape(preset, *box)
        picture_fill(shape, img_path, mode)
        shape.line.fill.background()
    save(prs)


if __name__ == "__main__":
    main()
