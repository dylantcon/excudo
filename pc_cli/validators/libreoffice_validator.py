"""
Layer 2: LibreOffice Headless PPTX Validator

Opens PPTX files in LibreOffice headless mode and converts to PDF.
If the conversion succeeds without errors, the file is structurally valid.
Catches major corruption (missing parts, broken XML, invalid relationships)
but does NOT catch all PowerPoint-specific semantic issues.
"""

import os
import shutil
import subprocess
import tempfile
import time
from dataclasses import dataclass, field
from pathlib import Path
from typing import Optional


@dataclass
class ValidationResult:
    """Result of a single file validation."""
    file_path: str
    passed: bool
    exit_code: int = 0
    errors: list = field(default_factory=list)
    warnings: list = field(default_factory=list)
    duration_ms: int = 0

    @property
    def status_label(self) -> str:
        if self.passed:
            return "PASS" if not self.warnings else "PASS (warnings)"
        return "FAIL"


class LibreOfficeValidator:
    """Validates PPTX files using LibreOffice headless PDF conversion."""

    # Error patterns in LibreOffice stderr that indicate genuine failures
    ERROR_PATTERNS = [
        "Error",
        "exception",
        "corrupt",
        "could not be repaired",
        "General input/output error",
        "Format error discovered",
    ]

    # Warning patterns (non-fatal)
    WARNING_PATTERNS = [
        "warn",
        "deprecated",
    ]

    # Lines to ignore in stderr (LibreOffice noise)
    IGNORE_PATTERNS = [
        "javaldx",
        "Could not find a Java Runtime",
        "Warning: no font found",
        "FontConfig",
    ]

    def __init__(self, soffice_path: Optional[str] = None, timeout: int = 30):
        self.soffice_path = soffice_path or self._find_soffice()
        self.timeout = timeout

    @staticmethod
    def _find_soffice() -> str:
        """Locate the LibreOffice binary."""
        candidates = ["soffice", "libreoffice"]
        for candidate in candidates:
            path = shutil.which(candidate)
            if path:
                return path
        raise FileNotFoundError(
            "LibreOffice not found. Install with: sudo apt install libreoffice-impress"
        )

    @staticmethod
    def is_available() -> bool:
        """Check if LibreOffice is installed and accessible."""
        return shutil.which("soffice") is not None or shutil.which("libreoffice") is not None

    def validate_file(self, file_path: str) -> ValidationResult:
        """Validate a single PPTX file by converting to PDF via LibreOffice headless."""
        file_path = os.path.abspath(file_path)
        if not os.path.isfile(file_path):
            return ValidationResult(
                file_path=file_path,
                passed=False,
                errors=[f"File not found: {file_path}"],
            )

        if not file_path.lower().endswith((".pptx", ".ppt")):
            return ValidationResult(
                file_path=file_path,
                passed=False,
                errors=["Not a PowerPoint file (expected .pptx or .ppt)"],
            )

        start_time = time.monotonic()

        with tempfile.TemporaryDirectory(prefix="pc_validate_") as tmp_dir:
            # Use a unique user profile to avoid lock conflicts with running LO instances
            user_profile = os.path.join(tmp_dir, "profile")
            os.makedirs(user_profile)

            cmd = [
                self.soffice_path,
                "--headless",
                "--norestore",
                "--nofirststartwizard",
                f"-env:UserInstallation=file://{user_profile}",
                "--convert-to", "pdf",
                "--outdir", tmp_dir,
                file_path,
            ]

            try:
                proc = subprocess.run(
                    cmd,
                    capture_output=True,
                    text=True,
                    timeout=self.timeout,
                    env={**os.environ, "HOME": tmp_dir},
                )
            except subprocess.TimeoutExpired:
                duration = int((time.monotonic() - start_time) * 1000)
                return ValidationResult(
                    file_path=file_path,
                    passed=False,
                    exit_code=-1,
                    errors=[f"LibreOffice timed out after {self.timeout}s"],
                    duration_ms=duration,
                )
            except FileNotFoundError:
                return ValidationResult(
                    file_path=file_path,
                    passed=False,
                    errors=["LibreOffice binary not found at: " + self.soffice_path],
                )

            duration = int((time.monotonic() - start_time) * 1000)
            errors = []
            warnings = []

            # Analyze stderr
            if proc.stderr:
                for line in proc.stderr.strip().splitlines():
                    line_stripped = line.strip()
                    if not line_stripped:
                        continue

                    # Skip known noise
                    if any(pat.lower() in line_stripped.lower() for pat in self.IGNORE_PATTERNS):
                        continue

                    # Check for errors
                    if any(pat.lower() in line_stripped.lower() for pat in self.ERROR_PATTERNS):
                        errors.append(line_stripped)
                    elif any(pat.lower() in line_stripped.lower() for pat in self.WARNING_PATTERNS):
                        warnings.append(line_stripped)

            # Check if PDF was actually produced
            base_name = Path(file_path).stem + ".pdf"
            pdf_path = os.path.join(tmp_dir, base_name)
            if not os.path.isfile(pdf_path):
                if proc.returncode != 0:
                    errors.append(f"Conversion failed with exit code {proc.returncode}")
                else:
                    errors.append("PDF output not produced (possible silent failure)")

            passed = proc.returncode == 0 and len(errors) == 0

            return ValidationResult(
                file_path=file_path,
                passed=passed,
                exit_code=proc.returncode,
                errors=errors,
                warnings=warnings,
                duration_ms=duration,
            )

    def validate_directory(self, dir_path: str, recursive: bool = False) -> list:
        """Validate all PPTX files in a directory."""
        dir_path = os.path.abspath(dir_path)
        if not os.path.isdir(dir_path):
            return [ValidationResult(
                file_path=dir_path,
                passed=False,
                errors=[f"Not a directory: {dir_path}"],
            )]

        results = []
        pattern = "**/*.pptx" if recursive else "*.pptx"
        pptx_files = sorted(Path(dir_path).glob(pattern))

        if not pptx_files:
            print(f"No .pptx files found in {dir_path}")
            return results

        for pptx_file in pptx_files:
            print(f"  Validating: {pptx_file.name} ...", end=" ", flush=True)
            result = self.validate_file(str(pptx_file))
            print(f"{result.status_label} ({result.duration_ms}ms)")
            results.append(result)

        return results


def run_validation(file_or_dir: str, recursive: bool = False) -> bool:
    """Entry point for CLI validation. Returns True if all files pass."""
    if not LibreOfficeValidator.is_available():
        print("LibreOffice not found. Install with:")
        print("  sudo apt install libreoffice-impress")
        return False

    validator = LibreOfficeValidator()

    if os.path.isfile(file_or_dir):
        print(f"Validating: {file_or_dir}")
        result = validator.validate_file(file_or_dir)
        _print_result(result)
        return result.passed
    elif os.path.isdir(file_or_dir):
        print(f"Validating directory: {file_or_dir}")
        results = validator.validate_directory(file_or_dir, recursive=recursive)
        if not results:
            return True

        passed = sum(1 for r in results if r.passed)
        failed = len(results) - passed
        print(f"\nResults: {passed} passed, {failed} failed, {len(results)} total")

        for r in results:
            if not r.passed:
                _print_result(r)

        return failed == 0
    else:
        print(f"Path not found: {file_or_dir}")
        return False


def _print_result(result: ValidationResult):
    """Print a single validation result."""
    status = result.status_label
    print(f"  [{status}] {result.file_path} ({result.duration_ms}ms)")
    for err in result.errors:
        print(f"    ERROR: {err}")
    for warn in result.warnings:
        print(f"    WARN:  {warn}")
