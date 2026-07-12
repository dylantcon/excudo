"""Per-slide image comparison metrics for the parity harness.

Three complementary metrics per slide, following the aiden0z/pptx-renderer
methodology:

- SSIM (structural similarity) on grayscale: the primary gate. Sensitive to
  geometry, text placement, and shading structure.
- RGB histogram correlation: catches global color errors (e.g. a missing
  lumMod) that SSIM under-weights because structure is unchanged.
- Foreground IoU: how well the "ink" footprints overlap — catches missing
  or misplaced shapes even when they are small relative to the canvas.

All functions take uint8 HxWx3 arrays. Renders are deterministic, so
comparisons use exact thresholds (no noise tolerance).
"""

from dataclasses import dataclass
from pathlib import Path

import numpy as np
from PIL import Image
from skimage.metrics import structural_similarity

# Maximum per-axis pixel difference tolerated between our render and the
# rasterized ground truth before resizing (EMU->px rounding can differ by 1).
MAX_DIMENSION_SLACK = 2

# A pixel is "foreground" when any channel differs from the estimated
# background color by more than this (0-255 scale).
FOREGROUND_TOLERANCE = 30


class MetricError(Exception):
    """Raised when two images cannot be meaningfully compared."""


@dataclass(frozen=True)
class SlideMetrics:
    ssim: float
    histogram: float
    iou: float


def load_rgb(path: Path) -> np.ndarray:
    """Load an image file as a uint8 HxWx3 RGB array."""
    with Image.open(path) as im:
        return np.asarray(im.convert("RGB"))


def align_pair(ours: np.ndarray, truth: np.ndarray) -> tuple[np.ndarray, np.ndarray]:
    """Bring both images to identical dimensions.

    Dimensions may differ by a pixel or two from EMU->px rounding; anything
    larger means the harness rendered at the wrong size and comparing would
    be meaningless, so that is a hard error rather than a silent resize.
    """
    if ours.shape == truth.shape:
        return ours, truth
    dh = abs(ours.shape[0] - truth.shape[0])
    dw = abs(ours.shape[1] - truth.shape[1])
    if dh > MAX_DIMENSION_SLACK or dw > MAX_DIMENSION_SLACK:
        raise MetricError(
            f"image dimensions differ beyond rounding slack: "
            f"ours {ours.shape[1]}x{ours.shape[0]} vs truth {truth.shape[1]}x{truth.shape[0]}"
        )
    target = (ours.shape[1], ours.shape[0])  # PIL wants (w, h)
    resized = Image.fromarray(truth).resize(target, Image.LANCZOS)
    return ours, np.asarray(resized)


def ssim_gray(a: np.ndarray, b: np.ndarray) -> float:
    """Grayscale structural similarity in [-1, 1] (1 = identical)."""
    ag = np.asarray(Image.fromarray(a).convert("L"))
    bg = np.asarray(Image.fromarray(b).convert("L"))
    return float(structural_similarity(ag, bg, data_range=255))


def histogram_correlation(a: np.ndarray, b: np.ndarray, bins: int = 16) -> float:
    """Pearson correlation of concatenated per-channel FOREGROUND histograms
    in [-1, 1] (like OpenCV's HISTCMP_CORREL on a 3x`bins` histogram).

    Only foreground pixels are counted: the (usually dominant) background
    would otherwise swamp the correlation and hide recolored shapes —
    background agreement is SSIM/IoU's job, this metric is about ink color.
    """
    ma, mb = foreground_mask(a), foreground_mask(b)
    if not ma.any() and not mb.any():
        return 1.0  # two blank slides agree
    if not ma.any() or not mb.any():
        return 0.0

    def hist(img: np.ndarray, mask: np.ndarray) -> np.ndarray:
        fg = img[mask]
        chans = [
            np.histogram(fg[:, c], bins=bins, range=(0, 256))[0]
            for c in range(3)
        ]
        h = np.concatenate(chans).astype(np.float64)
        return h / h.sum()

    ha, hb = hist(a, ma), hist(b, mb)
    da, db = ha - ha.mean(), hb - hb.mean()
    denom = np.sqrt((da * da).sum() * (db * db).sum())
    if denom == 0:
        # Both perfectly flat (or single-color) histograms: identical iff equal.
        return 1.0 if np.array_equal(ha, hb) else 0.0
    return float((da * db).sum() / denom)


def _estimate_background(img: np.ndarray) -> np.ndarray:
    """Most common color along the 1px border — the slide background for
    every corpus deck (shapes are laid out away from the edges)."""
    border = np.concatenate([
        img[0, :, :], img[-1, :, :], img[:, 0, :], img[:, -1, :]
    ])
    colors, counts = np.unique(border, axis=0, return_counts=True)
    return colors[counts.argmax()]


def foreground_mask(img: np.ndarray, tolerance: int = FOREGROUND_TOLERANCE) -> np.ndarray:
    """Boolean mask of pixels that differ from the background color."""
    bg = _estimate_background(img).astype(np.int16)
    diff = np.abs(img.astype(np.int16) - bg).max(axis=-1)
    return diff > tolerance


def foreground_iou(a: np.ndarray, b: np.ndarray) -> float:
    """Intersection-over-union of the two foreground masks in [0, 1].

    Both empty -> 1.0 (two blank slides agree); one empty -> 0.0.
    """
    ma, mb = foreground_mask(a), foreground_mask(b)
    union = np.logical_or(ma, mb).sum()
    if union == 0:
        return 1.0
    inter = np.logical_and(ma, mb).sum()
    return float(inter / union)


def diff_heatmap(a: np.ndarray, b: np.ndarray) -> Image.Image:
    """Ground truth dimmed to grayscale with per-pixel differences in red —
    the side-by-side dashboard's 'where to look' panel."""
    delta = np.abs(a.astype(np.int16) - b.astype(np.int16)).max(axis=-1)
    base = np.asarray(Image.fromarray(b).convert("L"), dtype=np.float64) * 0.35 + 140
    out = np.stack([base, base, base], axis=-1)
    strength = np.clip(delta * 3, 0, 255)
    out[..., 0] = np.clip(out[..., 0] + strength, 0, 255)
    out[..., 1] = np.clip(out[..., 1] - strength * 0.5, 0, 255)
    out[..., 2] = np.clip(out[..., 2] - strength * 0.5, 0, 255)
    return Image.fromarray(out.astype(np.uint8))


def compare(ours_path: Path, truth_path: Path) -> SlideMetrics:
    """Compute all three metrics for one slide pair."""
    ours, truth = align_pair(load_rgb(ours_path), load_rgb(truth_path))
    return SlideMetrics(
        ssim=ssim_gray(ours, truth),
        histogram=histogram_correlation(ours, truth),
        iou=foreground_iou(ours, truth),
    )
