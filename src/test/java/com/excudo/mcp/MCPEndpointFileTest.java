package com.excudo.mcp;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import static org.junit.Assert.*;

/**
 * Tests {@link MCPEndpointFile}'s write/delete behaviour against a
 * throwaway temp directory. Verifies the JSON shape the stdio bridge
 * relies on -- if any field name or type drifts here, the Python shim
 * stops finding the live server.
 */
public class MCPEndpointFileTest {

    private Path tempDir;
    private Path endpointPath;

    @Before
    public void setUp() throws IOException {
        tempDir = Files.createTempDirectory("mcp-endpoint-test");
        endpointPath = tempDir.resolve("nested/dir/mcp-endpoint.json");
    }

    @After
    public void tearDown() throws IOException {
        if (tempDir == null || !Files.exists(tempDir)) return;
        Files.walk(tempDir)
            .sorted(Comparator.reverseOrder())
            .forEach(p -> { try { Files.delete(p); } catch (IOException ignored) {} });
    }

    @Test
    public void writeCreatesParentDirsAndAllExpectedFields() throws IOException {
        boolean ok = MCPEndpointFile.write(endpointPath,
            "http://127.0.0.1:54321/mcp/abc123def456", "abc123def456");

        assertTrue(ok);
        assertTrue(Files.exists(endpointPath));

        JsonObject root = readJson(endpointPath);
        assertEquals("http://127.0.0.1:54321/mcp/abc123def456",
            root.get("url").getAsString());
        assertEquals("abc123def456", root.get("token").getAsString());
        assertTrue("pid must be the current JVM's PID",
            root.has("pid") && root.get("pid").getAsLong() > 0);
        assertEquals("pid must match this JVM",
            ProcessHandle.current().pid(), root.get("pid").getAsLong());
    }

    @Test
    public void writeOverwritesStaleContents() throws IOException {
        Files.createDirectories(endpointPath.getParent());
        Files.writeString(endpointPath,
            "{\"url\":\"http://stale\",\"token\":\"old\",\"pid\":1}",
            StandardCharsets.UTF_8);

        MCPEndpointFile.write(endpointPath,
            "http://127.0.0.1:9999/mcp/fresh", "fresh");

        JsonObject root = readJson(endpointPath);
        assertEquals("http://127.0.0.1:9999/mcp/fresh", root.get("url").getAsString());
        assertEquals("fresh", root.get("token").getAsString());
    }

    @Test
    public void deleteRemovesExistingFile() throws IOException {
        MCPEndpointFile.write(endpointPath, "http://x", "tok");
        assertTrue(Files.exists(endpointPath));

        assertTrue(MCPEndpointFile.delete(endpointPath));
        assertFalse(Files.exists(endpointPath));
    }

    @Test
    public void deleteOnMissingFileReturnsFalseNotThrow() {
        Path missing = tempDir.resolve("does-not-exist.json");
        assertFalse(Files.exists(missing));
        assertFalse(MCPEndpointFile.delete(missing));
    }

    @Test
    public void defaultPathIsUnderHomeDotExcudo() {
        Path p = MCPEndpointFile.defaultPath();
        assertEquals("mcp-endpoint.json", p.getFileName().toString());
        assertEquals(".excudo", p.getParent().getFileName().toString());
        assertEquals(Path.of(System.getProperty("user.home")),
            p.getParent().getParent());
    }

    private static JsonObject readJson(Path path) throws IOException {
        return JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8))
            .getAsJsonObject();
    }
}
