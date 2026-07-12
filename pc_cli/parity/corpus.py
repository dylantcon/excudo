"""Corpus discovery and fixture validation for the parity harness.

Layout, one directory per category under <project>/parity-corpus/:

    parity-corpus/
      thresholds.json          per-category SSIM graduation floors
      baselines.json           per-slide recorded-best metrics (the ratchet)
      expected-failures.json   xfail-strict category -> reason tag
      <category>/
        gen_deck.py            python-pptx generator (reproducible corpus)
        deck.pptx              the generated deck (committed)
        deck.pdf               PowerPoint's own export of deck.pptx (committed)

Fixture rule (no fake green): a category directory missing deck.pptx or
deck.pdf hard-fails the entire run — it never silently skips.
"""

import subprocess
import sys
from dataclasses import dataclass
from pathlib import Path

from pptx import Presentation

EMU_PER_PIXEL = 9525  # 96 DPI, matches CoordinateMapper.EMUS_PER_PIXEL


class CorpusError(Exception):
    pass


@dataclass(frozen=True)
class DeckInfo:
    slide_count: int
    width_px: int
    height_px: int


@dataclass(frozen=True)
class Category:
    name: str
    directory: Path

    @property
    def gen_script(self) -> Path:
        return self.directory / "gen_deck.py"

    @property
    def pptx(self) -> Path:
        return self.directory / "deck.pptx"

    @property
    def pdf(self) -> Path:
        return self.directory / "deck.pdf"


def corpus_root(project_root: Path) -> Path:
    return project_root / "parity-corpus"


def discover(project_root: Path, name_filter: str | None = None) -> list[Category]:
    """All category directories, sorted by name. A category is any direct
    subdirectory of parity-corpus/ (control .json files live at the root)."""
    root = corpus_root(project_root)
    if not root.is_dir():
        raise CorpusError(
            f"parity corpus not found at {root} — run 'pc.py parity generate' first"
        )
    cats = [
        Category(p.name, p) for p in sorted(root.iterdir())
        if p.is_dir() and not p.name.startswith((".", "_"))
    ]
    if name_filter:
        cats = [c for c in cats if _glob_match(c.name, name_filter)]
        if not cats:
            raise CorpusError(f"no corpus category matches filter '{name_filter}'")
    return cats


def _glob_match(name: str, pattern: str) -> bool:
    import fnmatch
    return fnmatch.fnmatch(name, pattern)


def validate_fixtures(categories: list[Category], require_pdf: bool = True) -> None:
    """Hard-fail on any missing fixture. Never skip."""
    problems = []
    for c in categories:
        if not c.pptx.is_file():
            problems.append(f"{c.name}: missing {c.pptx.name}")
        if require_pdf and not c.pdf.is_file():
            problems.append(f"{c.name}: missing ground truth {c.pdf.name}")
    if problems:
        raise CorpusError(
            "corpus fixtures missing (run 'pc.py parity generate'):\n  "
            + "\n  ".join(problems)
        )


def deck_info(pptx_path: Path) -> DeckInfo:
    """Slide count and pixel render dimensions derived from the deck itself."""
    prs = Presentation(str(pptx_path))
    return DeckInfo(
        slide_count=len(prs.slides),
        width_px=round(prs.slide_width / EMU_PER_PIXEL),
        height_px=round(prs.slide_height / EMU_PER_PIXEL),
    )


def generate_decks(categories: list[Category]) -> None:
    """Run each category's gen_deck.py to (re)produce deck.pptx.

    Generators run with cwd set to their category directory and must write
    ./deck.pptx. Any generator failure aborts the whole generate step.
    """
    for c in categories:
        if not c.gen_script.is_file():
            raise CorpusError(f"{c.name}: missing generator {c.gen_script}")
        print(f"  [{c.name}] generating deck.pptx ...")
        result = subprocess.run(
            [sys.executable, str(c.gen_script)],
            cwd=c.directory, capture_output=True, text=True,
        )
        if result.returncode != 0:
            raise CorpusError(
                f"{c.name}: gen_deck.py failed:\n{result.stdout}\n{result.stderr}"
            )
        if not c.pptx.is_file():
            raise CorpusError(f"{c.name}: gen_deck.py did not produce deck.pptx")
