package com.excudo.core.parsing;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Unit tests for CommandLineParser.
 *
 * Verifies tokenization of command strings, quoted string handling,
 * escape sequence processing, whitespace collapsing, and edge cases.
 */
public class CommandLineParserTest {

    // ========== Simple Tokenization ==========

    @Test
    public void parseCommand_simpleTokens_returnsSplitArray() {
        String[] tokens = CommandLineParser.parseCommand("add-shape 1 rect");

        assertArrayEquals(new String[]{"add-shape", "1", "rect"}, tokens);
    }

    @Test
    public void parseCommand_singleToken_returnsSingleElementArray() {
        String[] tokens = CommandLineParser.parseCommand("list");

        assertArrayEquals(new String[]{"list"}, tokens);
    }

    // ========== Quoted Strings ==========

    @Test
    public void parseCommand_doubleQuotedString_preservesSpacesInsideQuotes() {
        String[] tokens = CommandLineParser.parseCommand("create 1 \"My Title\"");

        assertArrayEquals(new String[]{"create", "1", "My Title"}, tokens);
    }

    @Test
    public void parseCommand_singleQuotedString_preservesSpacesInsideQuotes() {
        String[] tokens = CommandLineParser.parseCommand("create 1 'My Title'");

        assertArrayEquals(new String[]{"create", "1", "My Title"}, tokens);
    }

    @Test
    public void parseCommand_mixedQuotedAndUnquotedTokens_parsesCorrectly() {
        String[] tokens = CommandLineParser.parseCommand("edit-content 2 3 \"Hello World\" plain");

        assertArrayEquals(new String[]{"edit-content", "2", "3", "Hello World", "plain"}, tokens);
    }

    // ========== Escape Sequences ==========

    @Test
    public void parseCommand_newlineEscapeInsideDoubleQuotes_expandsToNewline() {
        // edit-content has 4 tokens: command, slide, shape-index, content
        String[] tokens = CommandLineParser.parseCommand("edit-content 1 2 \"Hello\\nWorld\"");

        assertEquals(4, tokens.length);
        String content = tokens[3];
        assertTrue("Token should contain an actual newline character",
                content.contains("\n"));
        assertEquals("Hello\nWorld", content);
    }

    @Test
    public void parseCommand_tabEscapeInsideDoubleQuotes_expandsToTab() {
        // set-text has 4 tokens: command, slide, spid, text
        String[] tokens = CommandLineParser.parseCommand("set-text 1 2 \"col1\\tcol2\"");

        assertEquals(4, tokens.length);
        assertEquals("col1\tcol2", tokens[3]);
    }

    @Test
    public void parseCommand_backslashEscapeInsideDoubleQuotes_expandsToSingleBackslash() {
        // set-text has 4 tokens: command, slide, spid, text
        String[] tokens = CommandLineParser.parseCommand("set-text 1 2 \"path\\\\file\"");

        assertEquals(4, tokens.length);
        assertEquals("path\\file", tokens[3]);
    }

    @Test
    public void parseCommand_noEscapeOutsideQuotes_backslashIsLiteral() {
        // Outside quotes the parser does not process escape sequences
        String[] tokens = CommandLineParser.parseCommand("cmd arg\\nvalue");

        assertEquals(2, tokens.length);
        assertEquals("arg\\nvalue", tokens[1]);
    }

    // ========== Null and Empty Input ==========

    @Test
    public void parseCommand_nullInput_returnsEmptyArray() {
        String[] tokens = CommandLineParser.parseCommand(null);

        assertNotNull(tokens);
        assertEquals(0, tokens.length);
    }

    @Test
    public void parseCommand_emptyString_returnsEmptyArray() {
        String[] tokens = CommandLineParser.parseCommand("");

        assertNotNull(tokens);
        assertEquals(0, tokens.length);
    }

    @Test
    public void parseCommand_whitespaceOnlyString_returnsEmptyArray() {
        String[] tokens = CommandLineParser.parseCommand("   ");

        assertNotNull(tokens);
        assertEquals(0, tokens.length);
    }

    // ========== Whitespace Collapsing ==========

    @Test
    public void parseCommand_multipleSpacesBetweenTokens_collapses() {
        String[] tokens = CommandLineParser.parseCommand("create   1    title");

        assertArrayEquals(new String[]{"create", "1", "title"}, tokens);
    }

    @Test
    public void parseCommand_leadingAndTrailingWhitespace_stripped() {
        String[] tokens = CommandLineParser.parseCommand("  list  ");

        assertArrayEquals(new String[]{"list"}, tokens);
    }

    // ========== Unmatched Quote ==========

    @Test
    public void parseCommand_unmatchedDoubleQuote_consumesToEndOfString() {
        String[] tokens = CommandLineParser.parseCommand("create 1 \"unterminated");

        assertEquals(3, tokens.length);
        assertEquals("create", tokens[0]);
        assertEquals("1", tokens[1]);
        assertEquals("unterminated", tokens[2]);
    }

    @Test
    public void parseCommand_unmatchedSingleQuote_consumesToEndOfString() {
        String[] tokens = CommandLineParser.parseCommand("create 1 'unterminated");

        assertEquals(3, tokens.length);
        assertEquals("unterminated", tokens[2]);
    }

    // ========== Mixed Quote Types ==========

    @Test
    public void parseCommand_singleQuoteInsideDoubleQuoted_treatedAsLiteral() {
        String[] tokens = CommandLineParser.parseCommand("cmd \"it's fine\"");

        assertEquals(2, tokens.length);
        assertEquals("it's fine", tokens[1]);
    }

    @Test
    public void parseCommand_doubleQuoteInsideSingleQuoted_treatedAsLiteral() {
        String[] tokens = CommandLineParser.parseCommand("cmd 'say \"hello\"'");

        assertEquals(2, tokens.length);
        assertEquals("say \"hello\"", tokens[1]);
    }

    // ========== Multi-word Quoted Followed by More Args ==========

    @Test
    public void parseCommand_quotedTitleFollowedByLayout_allTokensExtracted() {
        String[] tokens = CommandLineParser.parseCommand("create 2 \"Big Idea\" slideLayout3");

        assertArrayEquals(new String[]{"create", "2", "Big Idea", "slideLayout3"}, tokens);
    }
}
