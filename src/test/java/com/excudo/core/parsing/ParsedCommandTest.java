package com.excudo.core.parsing;

import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.*;

/**
 * Tests ParsedCommand construction, builder, and parameter access.
 */
public class ParsedCommandTest {

    @Test
    public void basicConstruction() {
        ParsedCommand cmd = new ParsedCommand("add-shape",
            Map.of("type", "rect", "slide", "1"));

        assertEquals("add-shape", cmd.getCommandName());
        assertEquals("rect", cmd.getString("type"));
        assertEquals("1", cmd.getString("slide"));
    }

    @Test
    public void builderCreatesCommand() {
        ParsedCommand cmd = ParsedCommand.builder("edit-text")
            .addParam("slide", 1)
            .addParam("spid", 3)
            .addParam("text", "Hello World")
            .build();

        assertEquals("edit-text", cmd.getCommandName());
        assertEquals("1", cmd.getString("slide"));
        assertEquals("3", cmd.getString("spid"));
        assertEquals("Hello World", cmd.getString("text"));
    }

    @Test
    public void getStringWithDefault() {
        ParsedCommand cmd = ParsedCommand.builder("test").build();

        assertNull(cmd.getString("missing"));
        assertEquals("fallback", cmd.getString("missing", "fallback"));
    }

    @Test
    public void getIntegerParsesNumbers() {
        ParsedCommand cmd = ParsedCommand.builder("move-shape")
            .addParam("x", 914400)
            .addParam("y", 457200)
            .build();

        assertEquals(Integer.valueOf(914400), cmd.getInteger("x"));
        assertEquals(Integer.valueOf(457200), cmd.getInteger("y"));
        assertNull(cmd.getInteger("missing"));
    }

    @Test
    public void getBooleanParsesValues() {
        ParsedCommand cmd = ParsedCommand.builder("config")
            .addParam("enabled", true)
            .addParam("verbose", false)
            .build();

        assertTrue(cmd.getBoolean("enabled"));
        assertFalse(cmd.getBoolean("verbose"));
    }

    @Test
    public void addParamWithDifferentTypes() {
        ParsedCommand cmd = ParsedCommand.builder("multi-type")
            .addParam("string", "text")
            .addParam("int", 42)
            .addParam("double", 3.14)
            .addParam("bool", true)
            .addParam("long", 9876543210L)
            .build();

        assertEquals("text", cmd.getString("string"));
        assertEquals("42", cmd.getString("int"));
        assertEquals("3.14", cmd.getString("double"));
        assertEquals("true", cmd.getString("bool"));
        assertEquals("9876543210", cmd.getString("long"));
    }

    @Test
    public void commandNamePreserved() {
        ParsedCommand cmd = ParsedCommand.builder("add-animation").build();
        assertEquals("add-animation", cmd.getCommandName());
    }

    @Test
    public void emptyParameters() {
        ParsedCommand cmd = ParsedCommand.builder("help").build();
        assertNull(cmd.getString("anything"));
        assertNull(cmd.getInteger("anything"));
    }

    @Test
    public void mapConstructorWorks() {
        Map<String, String> params = new java.util.LinkedHashMap<>();
        params.put("slide", "2");
        params.put("spid", "5");
        ParsedCommand cmd = new ParsedCommand("show-shape", params);

        assertEquals("show-shape", cmd.getCommandName());
        assertEquals("2", cmd.getString("slide"));
        assertEquals("5", cmd.getString("spid"));
    }
}
