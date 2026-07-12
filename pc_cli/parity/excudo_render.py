"""Drive Excudo's headless renderer: one console-mode JVM per deck.

The interactive console reads commands line-by-line from stdin, so a deck
renders with a piped script:

    load <deck.pptx>
    render-slide 1 <out>/slide-1.png W H
    ...
    exit

Renders use the AWT backend (Graphics2DRenderSurface), Excudo's default
headless/export path — the same pixels `render_slide` MCP calls produce.
Success is judged by the output files: every expected PNG must exist and be
non-empty, otherwise the run fails with the JVM's output attached.
"""

import subprocess
from pathlib import Path

MAIN_CLASS = "com.excudo.console.InteractiveConsole"
JVM_TIMEOUT_S = 300


class RenderError(Exception):
    pass


def render_deck(env, pptx: Path, out_dir: Path, slide_count: int,
                width: int, height: int) -> list[Path]:
    """Render all slides of `pptx` to out_dir/slide-N.png. Returns the paths."""
    out_dir.mkdir(parents=True, exist_ok=True)
    expected = [out_dir / f"slide-{n}.png" for n in range(1, slide_count + 1)]
    for p in expected:
        p.unlink(missing_ok=True)  # never let a stale render pass for a fresh one

    script_lines = [f"load {pptx}"]
    script_lines += [
        f"render-slide {n} {out_dir / f'slide-{n}.png'} {width} {height}"
        for n in range(1, slide_count + 1)
    ]
    script_lines += ["exit", ""]

    cmd = [
        env.get_java_executable(),
        "-cp", env.get_classpath(),
        "-Dglass.platform=Monocle", "-Dmonocle.platform=Headless",
        "-Dprism.order=sw", "-Djava.awt.headless=true",
        MAIN_CLASS,
    ]
    try:
        result = subprocess.run(
            cmd,
            input="\n".join(script_lines),
            capture_output=True, text=True, encoding="utf-8", errors="replace",
            cwd=env.project_root,  # classpath contains the relative 'build' dir
            timeout=JVM_TIMEOUT_S,
        )
    except subprocess.TimeoutExpired:
        raise RenderError(f"renderer JVM timed out after {JVM_TIMEOUT_S}s on {pptx}")

    missing = [p for p in expected if not p.is_file() or p.stat().st_size == 0]
    if missing:
        tail = "\n".join((result.stdout or "").splitlines()[-25:])
        err_tail = "\n".join((result.stderr or "").splitlines()[-10:])
        raise RenderError(
            f"{len(missing)}/{slide_count} renders missing for {pptx.name} "
            f"(first: {missing[0].name}); JVM exit={result.returncode}\n"
            f"--- stdout tail ---\n{tail}\n--- stderr tail ---\n{err_tail}"
        )
    return expected
