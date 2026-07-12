"""Ground-truth PDF export via PowerPoint COM automation (Windows only).

Each corpus deck.pptx is opened in a fresh, windowless PowerPoint instance
and saved as deck.pdf next to it. The PDFs are committed to the repo, so
comparison runs (`pc.py parity run`) never need PowerPoint — only
regeneration (`pc.py parity generate`) does.

Export goes to a temp directory first and is then moved into the corpus,
because PowerPoint writing directly into a OneDrive-synced folder can hit
transient file locks.
"""

import shutil
import subprocess
import tempfile
import time
from pathlib import Path

from .corpus import Category, CorpusError

PP_SAVE_AS_PDF = 32  # PpSaveAsFileType.ppSaveAsPDF


class GroundTruthError(Exception):
    pass


def _require_powerpoint():
    try:
        import win32com.client  # noqa: F401
    except ImportError:
        raise GroundTruthError(
            "pywin32 is not installed — run: python -m pip install pywin32"
        )
    try:
        import winreg
        winreg.QueryValue(winreg.HKEY_CLASSES_ROOT, r"PowerPoint.Application\CurVer")
    except OSError:
        raise GroundTruthError(
            "PowerPoint is not COM-registered on this machine. Ground truth "
            "can only be regenerated where Microsoft PowerPoint is installed; "
            "comparison runs (pc.py parity run) use the committed PDFs and "
            "do not need PowerPoint."
        )


def export_pdfs(categories: list[Category]) -> None:
    """Export deck.pdf for every category. Any failure is fatal — a missing
    ground truth must never be papered over."""
    _require_powerpoint()
    import pythoncom
    import win32com.client

    pythoncom.CoInitialize()
    app = None
    try:
        app = win32com.client.DispatchEx("PowerPoint.Application")
        app.DisplayAlerts = 1  # ppAlertsNone (0 is not a valid PpAlertLevel)
        for c in categories:
            if not c.pptx.is_file():
                raise GroundTruthError(f"{c.name}: {c.pptx} does not exist")
            print(f"  [{c.name}] exporting deck.pdf via PowerPoint ...")
            _export_one(app, c.pptx, c.pdf)
    finally:
        if app is not None:
            try:
                app.Quit()
            except Exception:
                # A wedged instance would silently poison later runs; kill it.
                subprocess.run(
                    ["taskkill", "/IM", "POWERPNT.EXE", "/F"],
                    capture_output=True,
                )
        pythoncom.CoUninitialize()


def _export_one(app, pptx: Path, pdf: Path, retries: int = 1) -> None:
    last_error = None
    for attempt in range(retries + 1):
        try:
            with tempfile.TemporaryDirectory(prefix="excudo-parity-") as tmp:
                tmp_pdf = Path(tmp) / "out.pdf"
                pres = app.Presentations.Open(
                    str(pptx), ReadOnly=True, Untitled=False, WithWindow=False
                )
                try:
                    pres.SaveAs(str(tmp_pdf), PP_SAVE_AS_PDF)
                finally:
                    pres.Close()
                if not tmp_pdf.is_file() or tmp_pdf.stat().st_size == 0:
                    raise GroundTruthError(f"PowerPoint produced no PDF for {pptx}")
                pdf.unlink(missing_ok=True)
                shutil.move(str(tmp_pdf), str(pdf))
            return
        except GroundTruthError:
            raise
        except Exception as e:  # COM errors are pywintypes.com_error
            last_error = e
            time.sleep(1.0)
    raise GroundTruthError(f"PDF export failed for {pptx}: {last_error}")
