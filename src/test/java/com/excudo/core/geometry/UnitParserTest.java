package com.excudo.core.geometry;

import org.junit.Test;
import static org.junit.Assert.*;

public class UnitParserTest {

    @Test
    public void testBareNumberDefaultsToPoints() {
        assertEquals(1270000L, UnitParser.parseToEmu("100"));
    }

    @Test
    public void testPointSuffix() {
        assertEquals(1270000L, UnitParser.parseToEmu("100pt"));
    }

    @Test
    public void testEmuSuffix() {
        assertEquals(1270000L, UnitParser.parseToEmu("1270000emu"));
    }

    @Test
    public void testInchSuffix() {
        assertEquals(1371600L, UnitParser.parseToEmu("1.5in"));
    }

    @Test
    public void testDecimalPoints() {
        assertEquals(63500L, UnitParser.parseToEmu("5.0pt"));
    }

    @Test
    public void testZero() {
        assertEquals(0L, UnitParser.parseToEmu("0"));
        assertEquals(0L, UnitParser.parseToEmu("0pt"));
        assertEquals(0L, UnitParser.parseToEmu("0emu"));
        assertEquals(0L, UnitParser.parseToEmu("0in"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNullInput() {
        UnitParser.parseToEmu(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testEmptyInput() {
        UnitParser.parseToEmu("");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidSuffix() {
        UnitParser.parseToEmu("100cm");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNonNumeric() {
        UnitParser.parseToEmu("abc");
    }

    @Test
    public void testLargeEmuValue() {
        assertEquals(12192000L, UnitParser.parseToEmu("12192000emu"));
    }

    @Test
    public void testOneInch() {
        assertEquals(914400L, UnitParser.parseToEmu("1in"));
    }

    @Test
    public void testOnePoint() {
        assertEquals(12700L, UnitParser.parseToEmu("1pt"));
        assertEquals(12700L, UnitParser.parseToEmu("1"));
    }
}
