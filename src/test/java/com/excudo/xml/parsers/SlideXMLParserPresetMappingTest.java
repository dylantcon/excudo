package com.excudo.xml.parsers;

import com.excudo.core.model.AnimationType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for SlideXMLParser preset ID to animation type mapping.
 * Tests the critical animation detection functionality including
 * presetClass-aware disambiguation (entrance vs emphasis ID spaces).
 */
class SlideXMLParserPresetMappingTest {

    @Test
    @DisplayName("Should map FADE animation correctly from presetID 10")
    void testFadeAnimationMapping() {
        AnimationType result = SlideXMLParser.determineAnimationTypeFromPreset("10");
        assertEquals(AnimationType.FADE, result,
                    "PresetID 10 should map to FADE animation");
    }

    @Test
    @DisplayName("Should map APPEAR animation correctly from presetID 1")
    void testAppearAnimationMapping() {
        AnimationType result = SlideXMLParser.determineAnimationTypeFromPreset("1");
        assertEquals(AnimationType.APPEAR, result,
                    "PresetID 1 should map to APPEAR animation");
    }

    @Test
    @DisplayName("Should map ZOOM animation correctly from presetID 53")
    void testZoomAnimationMapping() {
        AnimationType result = SlideXMLParser.determineAnimationTypeFromPreset("53");
        assertEquals(AnimationType.ZOOM, result,
                    "PresetID 53 should map to ZOOM animation");
    }

    @Test
    @DisplayName("Should map FLY animations correctly from presetID 2 with subtype resolution")
    void testFlyAnimationMapping() {
        // No subtype (default 0) = PowerPoint default direction = from bottom
        AnimationType defaultResult = SlideXMLParser.determineAnimationTypeFromPreset("2");
        assertEquals(AnimationType.FLY_IN_BOTTOM, defaultResult,
                    "PresetID 2 with no subtype should default to FLY_IN_BOTTOM (PowerPoint default)");

        // Subtype-based direction resolution
        assertEquals(AnimationType.FLY_IN_BOTTOM, SlideXMLParser.determineAnimationTypeFromPreset("2", "entr", "4"),
                    "Subtype 4 = from bottom");
        assertEquals(AnimationType.FLY_IN_LEFT, SlideXMLParser.determineAnimationTypeFromPreset("2", "entr", "8"),
                    "Subtype 8 = from left");
        assertEquals(AnimationType.FLY_IN_RIGHT, SlideXMLParser.determineAnimationTypeFromPreset("2", "entr", "2"),
                    "Subtype 2 = from right");
        assertEquals(AnimationType.FLY_IN_TOP, SlideXMLParser.determineAnimationTypeFromPreset("2", "entr", "1"),
                    "Subtype 1 = from top");
    }

    @Test
    @DisplayName("Should handle unknown presetID with safe fallback")
    void testUnknownPresetIdFallback() {
        AnimationType result = SlideXMLParser.determineAnimationTypeFromPreset("999");
        assertEquals(AnimationType.FADE, result,
                    "Unknown presetID should fallback to FADE for safety");
    }

    @Test
    @DisplayName("Should handle null presetID safely")
    void testNullPresetIdHandling() {
        AnimationType result = SlideXMLParser.determineAnimationTypeFromPreset(null);
        assertEquals(AnimationType.FADE, result,
                    "Null presetID should fallback to FADE safely");
    }

    @Test
    @DisplayName("Should handle empty presetID safely")
    void testEmptyPresetIdHandling() {
        AnimationType result = SlideXMLParser.determineAnimationTypeFromPreset("");
        assertEquals(AnimationType.FADE, result,
                    "Empty presetID should fallback to FADE safely");

        AnimationType whitespaceResult = SlideXMLParser.determineAnimationTypeFromPreset("   ");
        assertEquals(AnimationType.FADE, whitespaceResult,
                    "Whitespace-only presetID should fallback to FADE safely");
    }

    @Test
    @DisplayName("Should handle numeric presetID edge cases")
    void testNumericPresetIdEdgeCases() {
        AnimationType zeroResult = SlideXMLParser.determineAnimationTypeFromPreset("0");
        assertEquals(AnimationType.FADE, zeroResult,
                    "PresetID 0 should fallback to FADE");

        AnimationType negativeResult = SlideXMLParser.determineAnimationTypeFromPreset("-1");
        assertEquals(AnimationType.FADE, negativeResult,
                    "Negative presetID should fallback to FADE");

        AnimationType nonNumericResult = SlideXMLParser.determineAnimationTypeFromPreset("abc");
        assertEquals(AnimationType.FADE, nonNumericResult,
                    "Non-numeric presetID should fallback to FADE");
    }

    @Test
    @DisplayName("Should disambiguate presetID by presetClass context")
    void testPresetClassDisambiguation() {
        // PresetID 8 means DIAMOND_IN in entrance context, SPIN in emphasis context
        assertEquals(AnimationType.DIAMOND_IN, SlideXMLParser.determineAnimationTypeFromPreset("8", "entr"),
                    "Entrance presetID 8 = DIAMOND_IN");
        assertEquals(AnimationType.SPIN, SlideXMLParser.determineAnimationTypeFromPreset("8", "emph"),
                    "Emphasis presetID 8 = SPIN");

        // PresetID 6 means GROW_SHRINK in emphasis context, falls through in entrance
        assertEquals(AnimationType.GROW_SHRINK, SlideXMLParser.determineAnimationTypeFromPreset("6", "emph"),
                    "Emphasis presetID 6 = GROW_SHRINK");

        // Exit should use same ID space as entrance
        assertEquals(AnimationType.DIAMOND_IN, SlideXMLParser.determineAnimationTypeFromPreset("8", "exit"),
                    "Exit presetID 8 = DIAMOND_IN (same as entrance)");
    }

    @Test
    @DisplayName("Should correctly map oracle-corrected entrance presetIDs")
    void testOracleCorrectedEntranceIds() {
        assertEquals(AnimationType.APPEAR, SlideXMLParser.determineAnimationTypeFromPreset("1", "entr"),
                    "PresetID 1 = APPEAR");
        assertEquals(AnimationType.FLY_IN_BOTTOM, SlideXMLParser.determineAnimationTypeFromPreset("2", "entr"),
                    "PresetID 2 without subtype = FLY_IN_BOTTOM (PowerPoint default)");
        assertEquals(AnimationType.BLINDS_HORIZONTAL, SlideXMLParser.determineAnimationTypeFromPreset("3", "entr"),
                    "PresetID 3 = BLINDS_HORIZONTAL");
        assertEquals(AnimationType.BOX_IN, SlideXMLParser.determineAnimationTypeFromPreset("4", "entr"),
                    "PresetID 4 = BOX_IN (corrected from 5)");
        assertEquals(AnimationType.DIAMOND_IN, SlideXMLParser.determineAnimationTypeFromPreset("8", "entr"),
                    "PresetID 8 = DIAMOND_IN (corrected from 6)");
        assertEquals(AnimationType.FADE, SlideXMLParser.determineAnimationTypeFromPreset("10", "entr"),
                    "PresetID 10 = FADE");
        assertEquals(AnimationType.RANDOM_BARS_HORIZONTAL, SlideXMLParser.determineAnimationTypeFromPreset("14", "entr"),
                    "PresetID 14 = RANDOM_BARS_HORIZONTAL (corrected from 8)");
        assertEquals(AnimationType.SPLIT_HORIZONTAL, SlideXMLParser.determineAnimationTypeFromPreset("16", "entr"),
                    "PresetID 16 = SPLIT_HORIZONTAL");
        assertEquals(AnimationType.WHEEL_4, SlideXMLParser.determineAnimationTypeFromPreset("21", "entr"),
                    "PresetID 21 = WHEEL_4 (corrected from 4)");
        assertEquals(AnimationType.WIPE_LEFT, SlideXMLParser.determineAnimationTypeFromPreset("22", "entr"),
                    "PresetID 22 = WIPE_LEFT");
        assertEquals(AnimationType.GROW_TURN, SlideXMLParser.determineAnimationTypeFromPreset("31", "entr"),
                    "PresetID 31 = GROW_TURN (corrected from 13)");
        assertEquals(AnimationType.SWIVEL, SlideXMLParser.determineAnimationTypeFromPreset("45", "entr"),
                    "PresetID 45 = SWIVEL (corrected from 15)");
        assertEquals(AnimationType.ZOOM, SlideXMLParser.determineAnimationTypeFromPreset("53", "entr"),
                    "PresetID 53 = ZOOM");
    }

    @Test
    @DisplayName("Should correctly map oracle-corrected emphasis presetIDs")
    void testOracleCorrectedEmphasisIds() {
        assertEquals(AnimationType.GROW_SHRINK, SlideXMLParser.determineAnimationTypeFromPreset("6", "emph"),
                    "Emphasis presetID 6 = GROW_SHRINK");
        assertEquals(AnimationType.SPIN, SlideXMLParser.determineAnimationTypeFromPreset("8", "emph"),
                    "Emphasis presetID 8 = SPIN");
        assertEquals(AnimationType.TRANSPARENCY, SlideXMLParser.determineAnimationTypeFromPreset("9", "emph"),
                    "Emphasis presetID 9 = TRANSPARENCY");
        assertEquals(AnimationType.DARKEN, SlideXMLParser.determineAnimationTypeFromPreset("24", "emph"),
                    "Emphasis presetID 24 = DARKEN");
        assertEquals(AnimationType.DESATURATE, SlideXMLParser.determineAnimationTypeFromPreset("25", "emph"),
                    "Emphasis presetID 25 = DESATURATE");
        assertEquals(AnimationType.PULSE, SlideXMLParser.determineAnimationTypeFromPreset("26", "emph"),
                    "Emphasis presetID 26 = PULSE");
        assertEquals(AnimationType.COLOR_PULSE, SlideXMLParser.determineAnimationTypeFromPreset("27", "emph"),
                    "Emphasis presetID 27 = COLOR_PULSE");
        assertEquals(AnimationType.LIGHTEN, SlideXMLParser.determineAnimationTypeFromPreset("30", "emph"),
                    "Emphasis presetID 30 = LIGHTEN");
        assertEquals(AnimationType.TEETER, SlideXMLParser.determineAnimationTypeFromPreset("32", "emph"),
                    "Emphasis presetID 32 = TEETER");
    }

    @Test
    @DisplayName("Should maintain consistency across multiple calls")
    void testMappingConsistency() {
        String testPresetId = "53";
        AnimationType firstResult = SlideXMLParser.determineAnimationTypeFromPreset(testPresetId);
        AnimationType secondResult = SlideXMLParser.determineAnimationTypeFromPreset(testPresetId);
        AnimationType thirdResult = SlideXMLParser.determineAnimationTypeFromPreset(testPresetId);

        assertEquals(firstResult, secondResult, "Multiple calls should return same result");
        assertEquals(secondResult, thirdResult, "Mapping should be consistent");
        assertEquals(AnimationType.ZOOM, firstResult, "Zoom mapping should be consistent");
    }

    @Test
    @DisplayName("Single-arg overload defaults to entrance context")
    void testSingleArgDefaultsToEntrance() {
        // Single-arg should behave identically to two-arg with "entr"
        assertEquals(
            SlideXMLParser.determineAnimationTypeFromPreset("4", "entr"),
            SlideXMLParser.determineAnimationTypeFromPreset("4"),
            "Single-arg should default to entrance context");
    }
}