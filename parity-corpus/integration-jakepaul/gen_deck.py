"""Integration deck: a real-world sample copied from test-pptx-samples.
Not synthetic — measures whole-deck parity on authentic content."""
import shutil
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
SOURCE = HERE.parent.parent / "test-pptx-samples" / "jake-paul-presentation.pptx"


def main():
    if not SOURCE.is_file():
        print(f"gen_deck failed: source deck missing: {SOURCE}", file=sys.stderr)
        sys.exit(1)
    shutil.copyfile(SOURCE, HERE / "deck.pptx")
    print(f"copied {SOURCE.name}")


if __name__ == "__main__":
    main()
