#!/usr/bin/env bash
# ============================================================================
#  Excudo Smoke Test Demo
#  Builds a 6-slide technical presentation from scratch in one shot.
#  Demonstrates: themes, layouts, shapes, styling, animations, transitions,
#                connectors, text editing, font control, alignment, and notes.
# ============================================================================

set -euo pipefail
cd "$(dirname "$0")/.."

OUTPUT="demo-output.pptx"
STARTED=$(date +%s%N)

echo "=== Excudo Smoke Test Demo ==="
echo "Building a 6-slide presentation from scratch..."
echo ""

echo -e "\
new excudo
\
create 1 \"Excudo\"
edit-content 1 3 \"AI-Powered OOXML Presentation Engine\"
set-font 1 2 --size 44 --bold true
set-font 1 3 --size 22 --italic true
set-transition 1 fade --speed slow
add-animation 1 2 fade in on-click 800
add-animation 1 3 fade in after-previous 600
add-notes 1 \"Title slide. Excudo: open-source PPTX manipulation without Microsoft lock-in.\"
\
create 2 \"System Architecture\" slideLayout2
add-shape 2 ROUNDED_RECTANGLE \"Console REPL\" 600000 1800000 2600000 850000 --fill-color 1565C0
add-shape 2 ROUNDED_RECTANGLE \"Command Engine\" 3800000 1800000 2600000 850000 --fill-color 00838F
add-shape 2 ROUNDED_RECTANGLE \"OOXML Writer\" 600000 3300000 2600000 850000 --fill-color 2E7D32
add-shape 2 ROUNDED_RECTANGLE \"LLM Bridge\" 3800000 3300000 2600000 850000 --fill-color 6A1B9A
add-shape 2 ROUNDED_RECTANGLE \"SPIDManager\" 2200000 4800000 2600000 850000 --fill-color BF360C
add-connector 2 line 3200000 2200000 600000 0 --tail-end triangle --line-color FFFFFF
add-connector 2 line 1900000 2650000 0 650000 --tail-end triangle --line-color FFFFFF
add-connector 2 line 5100000 2650000 0 650000 --tail-end triangle --line-color FFFFFF
add-connector 2 line 1900000 4150000 1600000 650000 --tail-end triangle --line-color FFFFFF
add-connector 2 line 5100000 4150000 -1600000 650000 --tail-end triangle --line-color FFFFFF
set-transition 2 wipe-right
add-animation 2 4 fly-in-left in on-click 500
add-animation 2 5 fly-in-right in on-click 500
add-animation 2 6 fly-in-left in after-previous 400
add-animation 2 7 fly-in-right in after-previous 400
add-animation 2 8 zoom in after-previous 600
add-notes 2 \"Architecture: Console drives Commands which write OOXML. LLM bridge enables natural language. SPIDManager ensures PowerPoint-compatible shape IDs.\"
\
create 3 \"Key Capabilities\" slideLayout2
add-shape 3 ROUNDED_RECTANGLE \"40 Animation Types\" 500000 1800000 3200000 700000 --fill-color 1565C0
add-shape 3 ROUNDED_RECTANGLE \"145+ Shape Presets\" 500000 2700000 3200000 700000 --fill-color 00695C
add-shape 3 ROUNDED_RECTANGLE \"4 Bundled Themes\" 500000 3600000 3200000 700000 --fill-color AD1457
add-shape 3 ROUNDED_RECTANGLE \"ECMA-376 Compliant\" 500000 4500000 3200000 700000 --fill-color E65100
add-shape 3 STAR_5_POINTS \"\" 4300000 1900000 500000 500000 --fill-color FFD600
add-shape 3 STAR_5_POINTS \"\" 5000000 1900000 500000 500000 --fill-color FFD600
add-shape 3 STAR_5_POINTS \"\" 5700000 1900000 500000 500000 --fill-color FFD600
add-shape 3 DIAMOND \"\" 4300000 2800000 500000 500000 --fill-color 00BFA5
add-shape 3 HEART \"\" 4800000 3700000 600000 600000 --fill-color E91E63
add-shape 3 LIGHTNING_BOLT \"\" 5500000 4500000 600000 700000 --fill-color FF6D00
set-transition 3 push-left
add-animation 3 4 fly-in-left in on-click 400
add-animation 3 5 fly-in-left in after-previous 300
add-animation 3 6 fly-in-left in after-previous 300
add-animation 3 7 fly-in-left in after-previous 300
add-animation 3 8 zoom in after-previous 400
add-animation 3 9 zoom in after-previous 300
add-animation 3 10 zoom in after-previous 300
add-animation 3 11 zoom in after-previous 400
add-animation 3 12 pulse emphasis after-previous 600
add-animation 3 13 spin emphasis after-previous 800
add-notes 3 \"Feature overview. Each bullet flies in sequentially, decorative shapes zoom to add visual flair.\"
\
create 4 \"Animation Showcase\" slideLayout2
add-shape 4 ROUNDED_RECTANGLE \"Fade\" 500000 1800000 1800000 600000 --fill-color 1E88E5
add-shape 4 ROUNDED_RECTANGLE \"Fly\" 2600000 1800000 1800000 600000 --fill-color 43A047
add-shape 4 ROUNDED_RECTANGLE \"Zoom\" 4700000 1800000 1800000 600000 --fill-color E53935
add-shape 4 ROUNDED_RECTANGLE \"Wipe\" 500000 2800000 1800000 600000 --fill-color 8E24AA
add-shape 4 ROUNDED_RECTANGLE \"Swivel\" 2600000 2800000 1800000 600000 --fill-color F4511E
add-shape 4 ROUNDED_RECTANGLE \"Spin\" 4700000 2800000 1800000 600000 --fill-color 00897B
add-shape 4 ROUNDED_RECTANGLE \"Grow\" 500000 3800000 1800000 600000 --fill-color FDD835
add-shape 4 ROUNDED_RECTANGLE \"Pulse\" 2600000 3800000 1800000 600000 --fill-color 5C6BC0
add-shape 4 ROUNDED_RECTANGLE \"Diamond\" 4700000 3800000 1800000 600000 --fill-color 6D4C41
add-animation 4 4 fade in on-click 600
add-animation 4 5 fly-in-bottom in on-click 600
add-animation 4 6 zoom in on-click 600
add-animation 4 7 wipe-right in on-click 600
add-animation 4 8 swivel in on-click 800
add-animation 4 9 spin emphasis on-click 800
add-animation 4 10 grow-turn in on-click 800
add-animation 4 11 pulse emphasis on-click 600
add-animation 4 12 diamond-in in on-click 600
set-transition 4 diamond
add-notes 4 \"Each shape demonstrates a different animation type. Click through to see fade, fly, zoom, wipe, swivel, spin, grow-turn, pulse, and diamond entrance effects.\"
\
create 5 \"Why This Matters\" slideLayout2
add-shape 5 ROUNDED_RECTANGLE \"Microsoft wants OOXML to depend on proprietary capabilities\" 700000 1800000 5600000 800000 --fill-color B71C1C
add-shape 5 ROUNDED_RECTANGLE \"Excudo proves OOXML can be fully manipulated by open tooling\" 700000 2900000 5600000 800000 --fill-color 1B5E20
add-shape 5 ROUNDED_RECTANGLE \"No vendor lock-in. No Office 365 subscription. No compromise.\" 700000 4000000 5600000 800000 --fill-color 0D47A1
set-font 5 4 --size 18 --bold true
set-font 5 5 --size 18 --bold true
set-font 5 6 --size 18 --bold true
add-animation 5 4 fly-in-left in on-click 500
add-animation 5 5 fly-in-right in on-click 500
add-animation 5 6 fade in on-click 800
set-transition 5 fade
add-notes 5 \"The mission: oppose Embrace-Extend-Extinguish. Bill Gates said Office docs should lock users in. We say no.\"
\
create 6 \"Thank You\" slideLayout1
edit-content 6 3 \"github.com/excudo\"
set-font 6 2 --size 44 --bold true
set-font 6 3 --size 20
add-animation 6 2 fade in on-click 1000
add-animation 6 3 fade in after-previous 600
set-transition 6 dissolve
add-notes 6 \"Closing slide. Point audience to the repo.\"
\
save ${OUTPUT}
quit
" | python3 pc.py run console --headless 2>&1

ENDED=$(date +%s%N)
ELAPSED_MS=$(( (ENDED - STARTED) / 1000000 ))

echo ""
echo "=== Results ==="

if [ -f "$OUTPUT" ]; then
    SIZE=$(stat -c%s "$OUTPUT" 2>/dev/null || stat -f%z "$OUTPUT" 2>/dev/null)
    echo "Output:   $OUTPUT ($(( SIZE / 1024 )) KB)"
    echo "Time:     ${ELAPSED_MS}ms"
    echo ""

    # Verify structure
    SLIDE_COUNT=$(unzip -l "$OUTPUT" 2>/dev/null | grep "ppt/slides/slide[0-9]" | grep -v "_rels" | wc -l)
    ANIM_COUNT=$(unzip -p "$OUTPUT" "ppt/slides/*.xml" 2>/dev/null | grep -c "p:par" || true)
    SHAPE_COUNT=$(unzip -p "$OUTPUT" "ppt/slides/*.xml" 2>/dev/null | grep -c "<p:sp " || true)
    TRANSITION_COUNT=$(unzip -p "$OUTPUT" "ppt/slides/*.xml" 2>/dev/null | grep -c "p:transition" || true)
    CONNECTOR_COUNT=$(unzip -p "$OUTPUT" "ppt/slides/*.xml" 2>/dev/null | grep -c "<p:cxnSp" || true)
    NOTES_COUNT=$(unzip -l "$OUTPUT" 2>/dev/null | grep "ppt/notesSlides/notesSlide[0-9]" | grep -v "_rels" | wc -l)
    HAS_THEME=$(unzip -l "$OUTPUT" 2>/dev/null | grep -c "ppt/theme/theme1.xml" || true)

    echo "--- Structural Verification ---"
    echo "  Slides:      $SLIDE_COUNT (expected: 6)"
    echo "  Shapes:       $SHAPE_COUNT (expected: ~40+)"
    echo "  Animations:  $ANIM_COUNT (expected: ~25+)"
    echo "  Transitions: $TRANSITION_COUNT (expected: 6)"
    echo "  Connectors:  $CONNECTOR_COUNT (expected: 5)"
    echo "  Notes:       $NOTES_COUNT (expected: 6)"
    echo "  Theme:       $([ "$HAS_THEME" -ge 1 ] && echo "yes" || echo "MISSING")"
    echo ""

    PASS=true
    [ "$SLIDE_COUNT" -ne 6 ] && PASS=false && echo "FAIL: Expected 6 slides, got $SLIDE_COUNT"
    [ "$SHAPE_COUNT" -lt 30 ] && PASS=false && echo "FAIL: Expected 30+ shapes, got $SHAPE_COUNT"
    [ "$ANIM_COUNT" -lt 20 ] && PASS=false && echo "FAIL: Expected 20+ animation nodes, got $ANIM_COUNT"
    [ "$TRANSITION_COUNT" -lt 5 ] && PASS=false && echo "FAIL: Expected 5+ transitions, got $TRANSITION_COUNT"
    [ "$CONNECTOR_COUNT" -lt 4 ] && PASS=false && echo "FAIL: Expected 4+ connectors, got $CONNECTOR_COUNT"
    [ "$NOTES_COUNT" -lt 5 ] && PASS=false && echo "FAIL: Expected 5+ notes slides, got $NOTES_COUNT"
    [ "$HAS_THEME" -lt 1 ] && PASS=false && echo "FAIL: Theme missing from output"

    if $PASS; then
        echo "=== SMOKE TEST PASSED ==="
        echo ""
        echo "6 slides, ~${SHAPE_COUNT} shapes, ~${ANIM_COUNT} animation nodes,"
        echo "${CONNECTOR_COUNT} connectors, ${TRANSITION_COUNT} transitions, ${NOTES_COUNT} speaker notes,"
        echo "dark theme -- built from scratch in ${ELAPSED_MS}ms."
    else
        echo "=== SMOKE TEST FAILED ==="
        exit 1
    fi
else
    echo "FAIL: Output file not created"
    exit 1
fi
