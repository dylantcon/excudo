package com.excudo.console.utils;

import com.excudo.core.commands.mutating.slide.AddShapeCommand;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Tests ConsoleArgumentParser for quoted and unquoted argument parsing.
 */
public class ConsoleArgumentParserTest {

    @Test
    public void parseSimpleArgs() {
        ConsoleArgumentParser.ParsedArgs args = ConsoleArgumentParser.parseQuotedArgs(
            "add-shape 1 rect", 10);
        assertNotNull(args);
        assertTrue(args.getArgCount() >= 3);
        assertEquals(AddShapeCommand.NAME, args.getArg(0));
        assertEquals("1", args.getArg(1));
        assertEquals("rect", args.getArg(2));
    }

    @Test
    public void parseQuotedString() {
        ConsoleArgumentParser.ParsedArgs args = ConsoleArgumentParser.parseQuotedArgs(
            "edit-text 1 2 \"Hello World\"", 10);
        assertNotNull(args);
        boolean foundQuoted = false;
        for (String token : args.getArgs()) {
            if (token.contains("Hello World")) foundQuoted = true;
        }
        assertTrue("Should parse quoted string as single token", foundQuoted);
    }

    @Test
    public void parseEmptyInput() {
        ConsoleArgumentParser.ParsedArgs args = ConsoleArgumentParser.parseQuotedArgs("", 10);
        assertNotNull(args);
        assertEquals(0, args.getArgCount());
    }

    @Test
    public void parseSingleCommand() {
        ConsoleArgumentParser.ParsedArgs args = ConsoleArgumentParser.parseQuotedArgs("help", 10);
        assertEquals(1, args.getArgCount());
        assertEquals("help", args.getArg(0));
    }

    @Test
    public void parseWithExtraSpaces() {
        ConsoleArgumentParser.ParsedArgs args = ConsoleArgumentParser.parseQuotedArgs(
            "  add-shape   1   rect  ", 10);
        assertEquals(AddShapeCommand.NAME, args.getArg(0));
    }

    @Test
    public void hasMinimumArgsCheck() {
        ConsoleArgumentParser.ParsedArgs args = ConsoleArgumentParser.parseQuotedArgs(
            "cmd a b", 10);
        assertTrue(args.hasMinimumArgs(2));
        assertTrue(args.hasMinimumArgs(3));
        assertFalse(args.hasMinimumArgs(4));
    }

    @Test
    public void parseCommandSplitsTokens() {
        String[] tokens = ConsoleArgumentParser.parseCommand("add-shape 1 rect");
        assertNotNull(tokens);
        assertEquals(3, tokens.length);
        assertEquals(AddShapeCommand.NAME, tokens[0]);
    }

    @Test
    public void buildArgsStringJoinsFromIndex() {
        String[] parts = {"cmd", "hello", "world", "test"};
        String result = ConsoleArgumentParser.buildArgsString(parts, 1);
        assertNotNull(result);
        assertTrue(result.contains("hello"));
    }
}
