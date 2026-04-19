package com.excudo.core.llm;

import com.google.gson.JsonNull;
import com.google.gson.JsonParser;
import org.junit.Test;

import java.util.Set;

import static org.junit.Assert.*;

/**
 * Tests {@link ToolDispatcher#parseRequestedCommands} -- the resilient
 * parser for the {@code commands} argument of {@code get_command_schemas}.
 *
 * Live MCP clients serialise array arguments inconsistently; some pass a
 * real JSON array, some flatten to a stringified array, some to a
 * comma-separated string. The parser accepts all of these so the agent
 * doesn't have to make one call per command name just because its client
 * happened to flatten the argument.
 */
public class ToolDispatcherParseRequestedCommandsTest {

    @Test
    public void realJsonArrayIsPreserved() {
        Set<String> out = ToolDispatcher.parseRequestedCommands(
            JsonParser.parseString("[\"save\",\"load\"]"));
        assertEquals(Set.of("save", "load"), out);
    }

    @Test
    public void stringifiedJsonArrayIsUnwrapped() {
        // The whole array was flattened into a single string before send.
        Set<String> out = ToolDispatcher.parseRequestedCommands(
            JsonParser.parseString("\"[\\\"save\\\",\\\"load\\\"]\""));
        assertEquals(Set.of("save", "load"), out);
    }

    @Test
    public void stringifiedJsonArrayWithSpacesIsUnwrapped() {
        Set<String> out = ToolDispatcher.parseRequestedCommands(
            JsonParser.parseString("\"[ \\\"save\\\" , \\\"load\\\" ]\""));
        assertEquals(Set.of("save", "load"), out);
    }

    @Test
    public void commaSeparatedStringSplits() {
        Set<String> out = ToolDispatcher.parseRequestedCommands(
            JsonParser.parseString("\"save,load,new\""));
        assertEquals(Set.of("save", "load", "new"), out);
    }

    @Test
    public void commaSeparatedWithWhitespaceTrims() {
        Set<String> out = ToolDispatcher.parseRequestedCommands(
            JsonParser.parseString("\"save, load , new\""));
        assertEquals(Set.of("save", "load", "new"), out);
    }

    @Test
    public void bareStringBecomesSingleEntry() {
        Set<String> out = ToolDispatcher.parseRequestedCommands(
            JsonParser.parseString("\"save\""));
        assertEquals(Set.of("save"), out);
    }

    @Test
    public void emptyStringReturnsNull() {
        Set<String> out = ToolDispatcher.parseRequestedCommands(
            JsonParser.parseString("\"\""));
        assertNull(out);
    }

    @Test
    public void emptyArrayReturnsNull() {
        Set<String> out = ToolDispatcher.parseRequestedCommands(
            JsonParser.parseString("[]"));
        assertNull(out);
    }

    @Test
    public void jsonNullReturnsNull() {
        assertNull(ToolDispatcher.parseRequestedCommands(JsonNull.INSTANCE));
    }

    @Test
    public void javaNullReturnsNull() {
        assertNull(ToolDispatcher.parseRequestedCommands(null));
    }

    @Test
    public void bracketStringThatFailsToParseFallsThroughToLiteral() {
        // Something that looks like an array but isn't valid JSON -- treat
        // the whole thing as a single command name rather than crash.
        Set<String> out = ToolDispatcher.parseRequestedCommands(
            JsonParser.parseString("\"[not valid]\""));
        assertEquals("single-entry fallback",
            Set.of("[not valid]"), out);
    }

    @Test
    public void orderIsPreservedForArrayInput() {
        Set<String> out = ToolDispatcher.parseRequestedCommands(
            JsonParser.parseString("[\"zebra\",\"apple\",\"mango\"]"));
        assertArrayEquals(new String[]{"zebra", "apple", "mango"}, out.toArray());
    }

    @Test
    public void duplicatesAreDeduplicated() {
        Set<String> out = ToolDispatcher.parseRequestedCommands(
            JsonParser.parseString("[\"save\",\"save\",\"load\"]"));
        assertEquals(Set.of("save", "load"), out);
    }
}
