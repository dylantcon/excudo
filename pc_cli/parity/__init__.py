"""Renderer-vs-PowerPoint parity harness (pc.py parity).

Compares Excudo's headless slide renders against ground-truth rasters of
PDFs exported by real PowerPoint, per slide, using SSIM / histogram
correlation / foreground IoU. See pc_cli/commands/parity.py for the CLI.
"""
