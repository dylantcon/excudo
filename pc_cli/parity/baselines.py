"""Threshold floors, per-slide ratchet, and strict expected-failures.

Three checked-in control files under parity-corpus/ keep the harness honest:

- thresholds.json     {"default": {"ssim": 0.95}, "<category>": {"ssim": ...}}
                      A category PASSES only when every slide's SSIM meets
                      its floor.
- baselines.json      {"<category>/slide-N": {"ssim":, "histogram":, "iou":}}
                      Recorded-best metrics. Any slide dropping below its
                      baseline fails the run — improvements only ratchet UP
                      (--update-baselines), and lowering requires
                      --force-regress, which is never used in CI.
- expected-failures.json  {"<category>": "reason-tag"}
                      xfail-strict: a listed category must actually fail its
                      floor. If it passes, the run ERRORS: the entry is
                      stale and the category must graduate. This is what
                      prevents "expected failure" from decaying into
                      "ignored forever".

Renders and rasterization are deterministic, so floor comparisons use a
bare float-serialization epsilon. The RATCHET epsilons are per-metric:
ssim (the structural, gating metric) stays at serialization tolerance,
while histogram and iou carry a small calibrated noise floor. Those two
are threshold metrics -- iou binarizes a foreground mask, histogram
buckets 16 bins -- so ANY legitimate renderer change reshuffles a few
anti-aliased edge pixels across their cutoffs even as the render gets
closer to truth. Measured during A5: truth-verified corrections (round
default joins per the PDF `1 j` operators, stroke-over-fill pass order)
flipped 1-2 mask pixels per 1280x720 slide, moving iou by up to 4.2e-6
while ssim and histogram improved. The 1e-5 floor (~3 foreground pixels)
absorbs exactly that class of noise; real regressions move these metrics
by 1e-3 or more.
"""

import json
import math
from dataclasses import dataclass, field
from enum import Enum
from pathlib import Path

from .metrics import SlideMetrics

EPSILON = 1e-6
RATCHET_EPSILON = {"ssim": 1e-6, "histogram": 1e-5, "iou": 1e-5}

THRESHOLDS_FILE = "thresholds.json"
BASELINES_FILE = "baselines.json"
XFAIL_FILE = "expected-failures.json"


class BaselineError(Exception):
    pass


class Status(Enum):
    PASS = "pass"
    FAIL = "fail"
    XFAIL = "xfail"          # listed in expected-failures and indeed failing
    STALE_XFAIL = "stale-xfail"  # listed but passing -> error: graduate it


@dataclass
class SlideResult:
    category: str
    slide: int          # 1-based
    metrics: SlideMetrics
    ours_png: Path
    truth_png: Path
    heatmap_png: Path | None = None
    floor_ok: bool = True
    ratchet_ok: bool = True
    notes: list[str] = field(default_factory=list)

    @property
    def key(self) -> str:
        return f"{self.category}/slide-{self.slide}"


@dataclass
class CategoryResult:
    name: str
    slides: list[SlideResult]
    floor: float
    status: Status
    reason: str = ""


def _control_path(corpus_dir: Path, name: str) -> Path:
    return corpus_dir / name


def load_json(corpus_dir: Path, name: str, default):
    path = _control_path(corpus_dir, name)
    if not path.is_file():
        return default
    with open(path, encoding="utf-8") as f:
        return json.load(f)


def save_json(corpus_dir: Path, name: str, data) -> None:
    path = _control_path(corpus_dir, name)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(data, f, indent=2, sort_keys=True)
        f.write("\n")


def category_floor(thresholds: dict, category: str) -> float:
    entry = thresholds.get(category) or thresholds.get("default") or {}
    floor = entry.get("ssim")
    if floor is None:
        raise BaselineError(
            f"thresholds.json has no 'ssim' floor for '{category}' and no default"
        )
    return float(floor)


def evaluate_category(name: str, slides: list[SlideResult], thresholds: dict,
                      baselines: dict, xfails: dict) -> CategoryResult:
    floor = category_floor(thresholds, name)
    ratchet_violations = []
    floor_failures = []

    for s in slides:
        # reset so evaluation is idempotent (results may be re-scored after
        # a baseline update)
        s.ratchet_ok = True
        s.notes = []
        s.floor_ok = s.metrics.ssim + EPSILON >= floor
        if not s.floor_ok:
            floor_failures.append(s)
        recorded = baselines.get(s.key)
        if recorded:
            current = {"ssim": s.metrics.ssim, "histogram": s.metrics.histogram,
                       "iou": s.metrics.iou}
            for metric, best in recorded.items():
                eps = RATCHET_EPSILON.get(metric, EPSILON)
                if current.get(metric, 0.0) + eps < best:
                    s.ratchet_ok = False
                    s.notes.append(
                        f"{metric} regressed: {current.get(metric):.6f} < baseline {best:.6f}"
                    )
            if not s.ratchet_ok:
                ratchet_violations.append(s)

    if name in xfails:
        if ratchet_violations:
            return CategoryResult(name, slides, floor, Status.FAIL,
                                  f"{len(ratchet_violations)} slide(s) regressed below baseline")
        if not floor_failures:
            return CategoryResult(
                name, slides, floor, Status.STALE_XFAIL,
                f"expected to fail ('{xfails[name]}') but every slide meets the "
                f"{floor:.2f} floor — remove the stale entry and graduate it",
            )
        return CategoryResult(name, slides, floor, Status.XFAIL, xfails[name])

    if floor_failures or ratchet_violations:
        bits = []
        if floor_failures:
            worst = min(floor_failures, key=lambda s: s.metrics.ssim)
            bits.append(f"{len(floor_failures)} slide(s) below SSIM floor {floor:.2f} "
                        f"(worst: slide {worst.slide} @ {worst.metrics.ssim:.4f})")
        if ratchet_violations:
            bits.append(f"{len(ratchet_violations)} slide(s) regressed below baseline")
        return CategoryResult(name, slides, floor, Status.FAIL, "; ".join(bits))

    return CategoryResult(name, slides, floor, Status.PASS)


def update_baselines(corpus_dir: Path, results: list[CategoryResult],
                     force: bool = False) -> tuple[int, int]:
    """Raise recorded baselines to current metrics. Returns (raised, lowered).

    Without `force`, values only go UP; with `force` (--force-regress) the
    current value replaces the record even when lower.
    """
    baselines = load_json(corpus_dir, BASELINES_FILE, {})
    raised = lowered = 0
    for cat in results:
        for s in cat.slides:
            # Floor, never round: round-half-up recorded baselines UP TO
            # 5e-7 ABOVE the value the renderer actually achieved, which
            # no later run (including a byte-identical one, after any
            # epsilon tighter than the rounding error) could meet.
            current = {"ssim": _floor6(s.metrics.ssim),
                       "histogram": _floor6(s.metrics.histogram),
                       "iou": _floor6(s.metrics.iou)}
            recorded = baselines.get(s.key, {})
            merged = {}
            for metric, value in current.items():
                best = recorded.get(metric)
                if best is None or value > best + EPSILON:
                    merged[metric] = value
                    if best is not None:
                        raised += 1
                elif value + EPSILON < best and force:
                    merged[metric] = value
                    lowered += 1
                else:
                    merged[metric] = best
            baselines[s.key] = merged
    save_json(corpus_dir, BASELINES_FILE, baselines)
    return raised, lowered


def _floor6(value: float) -> float:
    """Truncate to 6 decimals -- a recorded baseline must never exceed
    the metric value that produced it."""
    return math.floor(value * 1e6) / 1e6
