#!/bin/bash
# Send a PPTX file to the Windows PC via Taildrop for PowerPoint testing.
# Usage: tools/send-to-ppt.sh <file.pptx>
#
# Prerequisites:
#   - Tailscale running on both machines
#   - Windows Tailscale configured to accept files
#   - sudo tailscale set --operator=$USER (to avoid needing sudo each time)

set -e

PPTX_FILE="${1:?Usage: $0 <file.pptx>}"
TARGET_HOST="${PPT_TARGET:-dylans-battlestation}"

if [ ! -f "$PPTX_FILE" ]; then
    echo "ERROR: File not found: $PPTX_FILE"
    exit 1
fi

echo "Sending $(basename "$PPTX_FILE") to $TARGET_HOST via Taildrop..."
tailscale file cp "$PPTX_FILE" "$TARGET_HOST:"
echo "SENT: $(basename "$PPTX_FILE") -> $TARGET_HOST"
echo "Check Taildrop notifications on Windows to accept the file."
