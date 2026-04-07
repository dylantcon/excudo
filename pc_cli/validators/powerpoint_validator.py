"""
Layer 3: Wine + PowerPoint Automated PPTX Validator

The authoritative check: opens a PPTX file in Microsoft PowerPoint
running under Wine and detects the "repair" dialog via window title inspection.

Prerequisites:
  1. Wine installed:  sudo apt install wine
  2. Microsoft Office installed under Wine (32-bit wineprefix)
  3. Xvfb for headless display:  sudo apt install xvfb
  4. xdotool for window inspection:  sudo apt install xdotool

Limitations:
  - Slow (~3-5 seconds per file)
  - Requires Wine + Office license/installation
  - Not suitable for CI (use Layer 1+2 for CI, Layer 3 for local dev)
  - Best used for validating new operation types before encoding rules into Layer 1
"""

import os
import shutil
import signal
import subprocess
import tempfile
import time
from dataclasses import dataclass, field
from typing import Optional


# Window title patterns that indicate PowerPoint detected corruption
REPAIR_INDICATORS = [
    "repair",
    "problem",
    "cannot open",
    "error",
    "corrupt",
    "unreadable content",
    "some content could not be read",
]


@dataclass
class PowerPointValidationResult:
    """Result of PowerPoint validation."""
    file_path: str
    passed: bool
    errors: list = field(default_factory=list)
    warnings: list = field(default_factory=list)
    window_title: str = ""
    duration_ms: int = 0

    @property
    def status_label(self) -> str:
        if self.passed:
            return "PASS"
        return "FAIL"


class PowerPointValidator:
    """Validates PPTX files by opening them in PowerPoint under Wine."""

    DEFAULT_POWERPNT_PATHS = [
        r"C:\Program Files\Microsoft Office\Office16\POWERPNT.EXE",
        r"C:\Program Files\Microsoft Office\Office15\POWERPNT.EXE",
        r"C:\Program Files\Microsoft Office\Office14\POWERPNT.EXE",
        r"C:\Program Files (x86)\Microsoft Office\Office16\POWERPNT.EXE",
        r"C:\Program Files (x86)\Microsoft Office\Office15\POWERPNT.EXE",
        r"C:\Program Files (x86)\Microsoft Office\Office14\POWERPNT.EXE",
    ]

    def __init__(
        self,
        powerpnt_path: Optional[str] = None,
        wineprefix: Optional[str] = None,
        startup_delay: float = 5.0,
        timeout: int = 15,
    ):
        self.powerpnt_path = powerpnt_path or self._find_powerpnt(wineprefix)
        self.wineprefix = wineprefix or os.path.expanduser("~/.wine")
        self.startup_delay = startup_delay
        self.timeout = timeout

    @staticmethod
    def is_available() -> bool:
        """Check if Wine and required tools are installed."""
        if not shutil.which("wine"):
            return False
        if not shutil.which("xdotool"):
            return False
        return True

    @classmethod
    def check_prerequisites(cls) -> list:
        """Return list of missing prerequisites."""
        missing = []
        if not shutil.which("wine"):
            missing.append("wine (sudo apt install wine)")
        if not shutil.which("xdotool"):
            missing.append("xdotool (sudo apt install xdotool)")
        if not shutil.which("Xvfb") and not shutil.which("xvfb-run"):
            missing.append("xvfb (sudo apt install xvfb)")
        return missing

    def _find_powerpnt(self, wineprefix: Optional[str] = None) -> str:
        """Locate PowerPoint executable within the Wine prefix."""
        prefix = wineprefix or os.path.expanduser("~/.wine")
        drive_c = os.path.join(prefix, "drive_c")

        for win_path in self.DEFAULT_POWERPNT_PATHS:
            # Convert Windows path to Unix path within wineprefix
            unix_path = win_path.replace("C:\\", "").replace("\\", "/")
            full_path = os.path.join(drive_c, unix_path)
            if os.path.isfile(full_path):
                return win_path

        raise FileNotFoundError(
            "PowerPoint not found in Wine prefix. Install Microsoft Office under Wine first.\n"
            f"  Searched in: {drive_c}\n"
            "  Expected one of:\n"
            + "\n".join(f"    {p}" for p in self.DEFAULT_POWERPNT_PATHS)
        )

    def _ensure_display(self) -> tuple:
        """Ensure an X display is available. Returns (display, xvfb_proc_or_None)."""
        display = os.environ.get("DISPLAY")
        if display:
            return display, None

        # Start Xvfb on a free display
        for display_num in range(99, 110):
            display = f":{display_num}"
            xvfb_proc = subprocess.Popen(
                ["Xvfb", display, "-screen", "0", "1024x768x24"],
                stdout=subprocess.DEVNULL,
                stderr=subprocess.DEVNULL,
            )
            time.sleep(0.5)
            if xvfb_proc.poll() is None:
                return display, xvfb_proc

        raise RuntimeError("Could not start Xvfb on any display :99-:109")

    def validate_file(self, file_path: str) -> PowerPointValidationResult:
        """Validate a PPTX file by opening in PowerPoint under Wine."""
        file_path = os.path.abspath(file_path)
        if not os.path.isfile(file_path):
            return PowerPointValidationResult(
                file_path=file_path,
                passed=False,
                errors=[f"File not found: {file_path}"],
            )

        start_time = time.monotonic()
        xvfb_proc = None
        ppt_proc = None

        try:
            display, xvfb_proc = self._ensure_display()

            env = os.environ.copy()
            env["DISPLAY"] = display
            env["WINEPREFIX"] = self.wineprefix

            # Convert Unix path to Wine-accessible path
            wine_path = subprocess.run(
                ["winepath", "-w", file_path],
                capture_output=True, text=True, env=env,
            )
            if wine_path.returncode != 0:
                win_file = file_path  # Fallback: let Wine handle it
            else:
                win_file = wine_path.stdout.strip()

            # Launch PowerPoint with the file
            ppt_proc = subprocess.Popen(
                ["wine", self.powerpnt_path, "/O", win_file],
                stdout=subprocess.DEVNULL,
                stderr=subprocess.DEVNULL,
                env=env,
            )

            # Wait for PowerPoint to load
            time.sleep(self.startup_delay)

            # Check if process crashed immediately
            if ppt_proc.poll() is not None:
                duration = int((time.monotonic() - start_time) * 1000)
                return PowerPointValidationResult(
                    file_path=file_path,
                    passed=False,
                    errors=["PowerPoint process exited immediately (crash or file error)"],
                    duration_ms=duration,
                )

            # Inspect window titles for repair dialog
            window_titles = self._get_window_titles(display, env)
            repair_detected = False
            detected_title = ""

            for title in window_titles:
                title_lower = title.lower()
                for indicator in REPAIR_INDICATORS:
                    if indicator in title_lower:
                        repair_detected = True
                        detected_title = title
                        break
                if repair_detected:
                    break

            duration = int((time.monotonic() - start_time) * 1000)

            if repair_detected:
                return PowerPointValidationResult(
                    file_path=file_path,
                    passed=False,
                    errors=[f"PowerPoint repair dialog detected: '{detected_title}'"],
                    window_title=detected_title,
                    duration_ms=duration,
                )

            return PowerPointValidationResult(
                file_path=file_path,
                passed=True,
                window_title="; ".join(window_titles) if window_titles else "(no windows detected)",
                duration_ms=duration,
            )

        except FileNotFoundError as e:
            duration = int((time.monotonic() - start_time) * 1000)
            return PowerPointValidationResult(
                file_path=file_path,
                passed=False,
                errors=[str(e)],
                duration_ms=duration,
            )
        finally:
            # Clean up PowerPoint process
            if ppt_proc and ppt_proc.poll() is None:
                try:
                    ppt_proc.terminate()
                    ppt_proc.wait(timeout=5)
                except subprocess.TimeoutExpired:
                    ppt_proc.kill()

            # Clean up Xvfb if we started it
            if xvfb_proc:
                xvfb_proc.terminate()
                try:
                    xvfb_proc.wait(timeout=3)
                except subprocess.TimeoutExpired:
                    xvfb_proc.kill()

    @staticmethod
    def _get_window_titles(display: str, env: dict) -> list:
        """Get all window titles on the given display using xdotool."""
        try:
            result = subprocess.run(
                ["xdotool", "search", "--name", ""],
                capture_output=True, text=True, env=env, timeout=5,
            )
            if result.returncode != 0 or not result.stdout.strip():
                return []

            window_ids = result.stdout.strip().splitlines()
            titles = []
            for wid in window_ids:
                name_result = subprocess.run(
                    ["xdotool", "getwindowname", wid],
                    capture_output=True, text=True, env=env, timeout=3,
                )
                if name_result.returncode == 0 and name_result.stdout.strip():
                    titles.append(name_result.stdout.strip())
            return titles
        except (subprocess.TimeoutExpired, FileNotFoundError):
            return []


def run_powerpoint_validation(file_path: str) -> bool:
    """Entry point for CLI PowerPoint validation. Returns True if file passes."""
    missing = PowerPointValidator.check_prerequisites()
    if missing:
        print("Missing prerequisites for PowerPoint validation:")
        for dep in missing:
            print(f"  - {dep}")
        return False

    try:
        validator = PowerPointValidator()
    except FileNotFoundError as e:
        print(str(e))
        return False

    print(f"Validating with PowerPoint (via Wine): {file_path}")
    result = validator.validate_file(file_path)

    status = result.status_label
    print(f"  [{status}] {result.file_path} ({result.duration_ms}ms)")
    if result.window_title:
        print(f"    Window: {result.window_title}")
    for err in result.errors:
        print(f"    ERROR: {err}")
    for warn in result.warnings:
        print(f"    WARN:  {warn}")

    return result.passed
