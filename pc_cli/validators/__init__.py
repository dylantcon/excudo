"""
PPTX File Integrity Validators

Multi-layer validation system for ensuring generated PPTX files conform
to PowerPoint's integrity rules.

Layers:
  1. Unit test semantic validation (Java-side, WriterTestFixtures assertions)
  2. LibreOffice headless -- structural integrity check via PDF conversion
  3. Wine+PowerPoint -- authoritative validation detecting repair dialog
"""

from pc_cli.validators.libreoffice_validator import LibreOfficeValidator, ValidationResult
