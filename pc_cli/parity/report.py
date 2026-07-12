"""Static HTML dashboard: ours | ground truth | diff heatmap, per slide."""

import datetime
import html
from pathlib import Path

from .baselines import CategoryResult, Status

_STATUS_COLORS = {
    Status.PASS: "#1a7f37",
    Status.FAIL: "#cf222e",
    Status.XFAIL: "#9a6700",
    Status.STALE_XFAIL: "#cf222e",
}

_CSS = """
body { font-family: Segoe UI, system-ui, sans-serif; margin: 1.5rem; background: #fafafa; color: #1f2328; }
h1 { font-size: 1.4rem; } h2 { font-size: 1.1rem; margin-top: 2rem; }
table { border-collapse: collapse; background: #fff; }
th, td { border: 1px solid #d0d7de; padding: 4px 10px; font-size: 0.85rem; text-align: left; }
th { background: #f6f8fa; }
img.slide { max-width: 320px; border: 1px solid #d0d7de; display: block; }
.badge { color: #fff; padding: 2px 8px; border-radius: 10px; font-size: 0.75rem; font-weight: 600; }
.metric-bad { color: #cf222e; font-weight: 700; }
.metric-ok { color: #1a7f37; }
.note { color: #cf222e; font-size: 0.78rem; }
.reason { color: #57606a; font-size: 0.85rem; }
"""


def _badge(status: Status) -> str:
    return (f'<span class="badge" style="background:{_STATUS_COLORS[status]}">'
            f'{status.value.upper()}</span>')


def _rel(target: Path, base_dir: Path) -> str:
    return target.relative_to(base_dir).as_posix()


def write_report(out_dir: Path, results: list[CategoryResult]) -> Path:
    """Write index.html into out_dir; image paths are relative to it."""
    rows = []
    for cat in results:
        min_ssim = min((s.metrics.ssim for s in cat.slides), default=0.0)
        rows.append(
            f"<tr><td><a href='#{html.escape(cat.name)}'>{html.escape(cat.name)}</a></td>"
            f"<td>{_badge(cat.status)}</td><td>{cat.floor:.2f}</td>"
            f"<td>{min_ssim:.4f}</td><td class='reason'>{html.escape(cat.reason)}</td></tr>"
        )

    sections = []
    for cat in results:
        slide_rows = []
        for s in cat.slides:
            ssim_cls = "metric-ok" if s.floor_ok else "metric-bad"
            notes = "<br>".join(f'<span class="note">{html.escape(n)}</span>' for n in s.notes)
            heat = (f'<img class="slide" src="{_rel(s.heatmap_png, out_dir)}">'
                    if s.heatmap_png else "")
            slide_rows.append(
                f"<tr><td>{s.slide}</td>"
                f'<td><img class="slide" src="{_rel(s.ours_png, out_dir)}"></td>'
                f'<td><img class="slide" src="{_rel(s.truth_png, out_dir)}"></td>'
                f"<td>{heat}</td>"
                f'<td><span class="{ssim_cls}">SSIM {s.metrics.ssim:.4f}</span><br>'
                f"hist {s.metrics.histogram:.4f}<br>IoU {s.metrics.iou:.4f}"
                f"{('<br>' + notes) if notes else ''}</td></tr>"
            )
        sections.append(
            f'<h2 id="{html.escape(cat.name)}">{html.escape(cat.name)} {_badge(cat.status)}'
            f' <span class="reason">floor {cat.floor:.2f}'
            f"{(' — ' + html.escape(cat.reason)) if cat.reason else ''}</span></h2>"
            f"<table><tr><th>#</th><th>Excudo render</th><th>PowerPoint ground truth</th>"
            f"<th>diff</th><th>metrics</th></tr>{''.join(slide_rows)}</table>"
        )

    stamp = datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    doc = (
        f"<!doctype html><meta charset='utf-8'><title>Excudo parity report</title>"
        f"<style>{_CSS}</style>"
        f"<h1>Renderer parity report</h1>"
        f"<p class='reason'>{stamp} — SSIM floor gates each category; "
        f"baselines only ratchet up.</p>"
        f"<table><tr><th>category</th><th>status</th><th>floor</th>"
        f"<th>min SSIM</th><th>reason</th></tr>{''.join(rows)}</table>"
        f"{''.join(sections)}"
    )
    out_dir.mkdir(parents=True, exist_ok=True)
    out = out_dir / "index.html"
    out.write_text(doc, encoding="utf-8")
    return out
