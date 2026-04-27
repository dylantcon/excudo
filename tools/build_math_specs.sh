#!/usr/bin/env bash
# Regenerate math-related OOXML specification extracts under docs/ms-specs/.
#
# Source PDFs (MS-OE376.pdf, MS-OI29500.pdf) must already exist in
# docs/ms-specs/ -- they're gitignored for distribution reasons. The
# OpenXML SDK math pages are scraped from learn.microsoft.com.
#
# Outputs (most are .gitignored; the openxml-sdk-math/ tree is tracked):
#   docs/ms-specs/MS-OE376-math.txt        (math chapter from MS-OE376)
#   docs/ms-specs/MS-OI29500-math.txt      (math chapter from MS-OI29500)
#   docs/ms-specs/OT-MATH-table.txt        (OpenType MATH table spec)
#   docs/ms-specs/openxml-sdk-math/*.txt   (145 OMML SDK class pages)
#
# Tools required: pdftotext (poppler-utils), curl, html2text, awk, sed.

set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SPECS_DIR="$PROJECT_ROOT/docs/ms-specs"
SDK_MATH_DIR="$SPECS_DIR/openxml-sdk-math"

for tool in pdftotext curl html2text awk sed; do
  command -v "$tool" >/dev/null 2>&1 || { echo "ERROR: required tool '$tool' not found on PATH" >&2; exit 1; }
done

mkdir -p "$SDK_MATH_DIR"

# 1. Extract Math chapter from MS-OI29500 (ISO/IEC 29500-1, §22 "Math")
if [[ -f "$SPECS_DIR/MS-OI29500.pdf" ]]; then
  echo "Extracting MS-OI29500-math.txt from MS-OI29500.pdf..."
  pdftotext -layout "$SPECS_DIR/MS-OI29500.pdf" - 2>/dev/null \
    | awk '/^2\.1\.1642[[:space:]]+Part 1 Section 22\.1, Math$/{flag=1} /^2\.1\.1749[[:space:]]+Part 2 Section 6\.2\.2\.2/{flag=0} flag' \
    > "$SPECS_DIR/MS-OI29500-math.txt"
  echo "  -> $(wc -l < "$SPECS_DIR/MS-OI29500-math.txt") lines"
else
  echo "SKIP MS-OI29500 math extract: source PDF not present"
fi

# 2. Extract Math chapter from MS-OE376 (ECMA-376 Part 4 §7 "Math")
if [[ -f "$SPECS_DIR/MS-OE376.pdf" ]]; then
  echo "Extracting MS-OE376-math.txt from MS-OE376.pdf..."
  pdftotext -layout "$SPECS_DIR/MS-OE376.pdf" - 2>/dev/null \
    | awk '/^2\.1\.1797[[:space:]]+Part 4 Section 7\.1, Math$/{flag=1} /^2\.1\.1898[[:space:]]+Part 4 Section 7\.4/{flag=0} flag' \
    > "$SPECS_DIR/MS-OE376-math.txt"
  echo "  -> $(wc -l < "$SPECS_DIR/MS-OE376-math.txt") lines"
else
  echo "SKIP MS-OE376 math extract: source PDF not present"
fi

# 3. OpenType MATH table specification
echo "Fetching OpenType MATH table spec..."
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
curl -sL --user-agent "Mozilla/5.0" \
  "https://learn.microsoft.com/en-us/typography/opentype/spec/math" \
  -o "$TMP/ot-math.html"
html2text "$TMP/ot-math.html" \
  | awk '/^# MATH - The Mathematical Typesetting Table/,/^## Additional Information/' \
  | sed '/^$/N;/^\n$/D' \
  > "$SPECS_DIR/OT-MATH-table.txt"
echo "  -> $(wc -l < "$SPECS_DIR/OT-MATH-table.txt") lines"

# 4. OpenXML SDK math element pages (~145 classes under
#    DocumentFormat.OpenXml.Math). Discover the class list from the
#    namespace overview, then fetch each class's reference page.
echo "Discovering OMML SDK class list..."
curl -sL --user-agent "Mozilla/5.0" \
  "https://learn.microsoft.com/en-us/dotnet/api/documentformat.openxml.math" \
  -o "$TMP/math-ns.html"
grep -oE "documentformat\.openxml\.math\.[a-z]+" "$TMP/math-ns.html" \
  | sort -u > "$TMP/class-list.txt"
echo "  -> $(wc -l < "$TMP/class-list.txt") classes discovered"

echo "Fetching + converting SDK pages (this takes a minute)..."
mkdir -p "$TMP/sdk-html"
while read -r url; do
  name=$(echo "$url" | sed 's/.*\.//')
  curl -sL --user-agent "Mozilla/5.0" \
    "https://learn.microsoft.com/en-us/dotnet/api/$url" \
    -o "$TMP/sdk-html/$name.html"
done < "$TMP/class-list.txt"

# Convert + strip MS Learn boilerplate (everything before the first "# Class").
for f in "$TMP/sdk-html"/*.html; do
  base="${f##*/}"
  base="${base%.html}"
  # Capitalize for filename consistency with existing openxml-sdk/ pages.
  fname="$(echo "$base" | sed 's/^./\U&/')"
  html2text "$f" 2>/dev/null \
    | awk '/^# /{flag=1} flag' \
    > "$SDK_MATH_DIR/$fname.txt"
done
echo "  -> $(ls "$SDK_MATH_DIR" | wc -l) pages written to $SDK_MATH_DIR"

echo "Done."
