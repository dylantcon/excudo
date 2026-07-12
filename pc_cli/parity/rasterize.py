"""Rasterize ground-truth PDFs to per-slide PNGs at the render dimensions."""

from pathlib import Path

import fitz  # PyMuPDF
from PIL import Image


class RasterizeError(Exception):
    pass


def rasterize_pdf(pdf_path: Path, out_dir: Path, target_w: int, target_h: int) -> list[Path]:
    """Render every page of `pdf_path` to `out_dir/slide-N.png` at exactly
    (target_w, target_h) pixels — the same dimensions the Excudo render used,
    so metric comparison is pixel-aligned.

    PDF pages are in points (1/72 in); we zoom so the page width lands on
    target_w, then correct any 1px rounding drift with a resize.
    """
    out_dir.mkdir(parents=True, exist_ok=True)
    outputs: list[Path] = []
    with fitz.open(pdf_path) as doc:
        if doc.page_count == 0:
            raise RasterizeError(f"{pdf_path} has no pages")
        for i, page in enumerate(doc, start=1):
            zoom = target_w / page.rect.width
            pix = page.get_pixmap(matrix=fitz.Matrix(zoom, zoom), alpha=False)
            img = Image.frombytes("RGB", (pix.width, pix.height), pix.samples)
            if abs(pix.height - target_h) > 3 or abs(pix.width - target_w) > 3:
                raise RasterizeError(
                    f"{pdf_path} page {i}: aspect mismatch — rasterized to "
                    f"{pix.width}x{pix.height}, expected ~{target_w}x{target_h}. "
                    f"Deck slide size and PDF page size disagree."
                )
            if (pix.width, pix.height) != (target_w, target_h):
                img = img.resize((target_w, target_h), Image.LANCZOS)
            out = out_dir / f"slide-{i}.png"
            img.save(out)
            outputs.append(out)
    return outputs
