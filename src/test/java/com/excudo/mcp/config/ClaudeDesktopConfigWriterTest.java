package com.excudo.mcp.config;

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
 * Tests {@link ClaudeDesktopConfigWriter}'s register / deregister flow
 * against a throwaway temp directory. Covers: creating a fresh config
 * file, updating an existing one (preserving unrelated entries and
 * creating a .bak), deregistering leaves other servers alone, and the
 * not-found branch returns the right result shape.
 *
 * Path detection is stubbed via explicit configPath arguments so these
 * tests don't need to care about the host OS.
 */
public class ClaudeDesktopConfigWriterTest {

    private Path tempDir;
    private Path configPath;
    private static final String URL = "http://127.0.0.1:44321/mcp/abc123def456";

    @Before
    public void setUp() throws IOException {
        tempDir = Files.createTempDirectory("claude-config-test");
        configPath = tempDir.resolve("claude_desktop_config.json");
    }

    @After
    public void tearDown() throws IOException {
        if (tempDir == null || !Files.exists(tempDir)) return;
        Files.walk(tempDir)
            .sorted(Comparator.reverseOrder())
            .forEach(p -> { try { Files.delete(p); } catch (IOException ignored) {} });
    }

    // ========== register: fresh file ==========

    @Test
    public void registerOnMissingFileCreatesConfigWithExcudoEntry() throws Exception {
        assertFalse(Files.exists(configPath));

        ClaudeDesktopConfigWriter.Result result =
            ClaudeDesktopConfigWriter.register(configPath, URL);

        assertTrue(result.written());
        assertTrue(Files.exists(configPath));

        JsonObject root = read(configPath);
        JsonObject servers = root.getAsJsonObject("mcpServers");
        JsonObject excudo = servers.getAsJsonObject("excudo");
        assertEquals(URL, excudo.get("url").getAsString());
    }

    @Test
    public void registerCreatesParentDirectoriesIfMissing() throws Exception {
        Path nested = tempDir.resolve("nested/deeper/claude_desktop_config.json");
        assertFalse(Files.exists(nested.getParent()));

        ClaudeDesktopConfigWriter.Result result =
            ClaudeDesktopConfigWriter.register(nested, URL);

        assertTrue(result.written());
        assertTrue(Files.exists(nested));
    }

    // ========== register: existing file, preservation ==========

    @Test
    public void registerPreservesExistingServers() throws Exception {
        writeJson(configPath, "{\"mcpServers\":{\"other-tool\":{\"command\":\"node\",\"args\":[\"x.js\"]}}}");

        ClaudeDesktopConfigWriter.register(configPath, URL);

        JsonObject servers = read(configPath).getAsJsonObject("mcpServers");
        assertTrue("previous server must still be present", servers.has("other-tool"));
        assertTrue("new excudo entry must be present", servers.has("excudo"));
    }

    @Test
    public void registerPreservesTopLevelNonServerFields() throws Exception {
        writeJson(configPath, "{\"globalShortcut\":\"Cmd+Shift+Space\",\"mcpServers\":{}}");

        ClaudeDesktopConfigWriter.register(configPath, URL);

        JsonObject root = read(configPath);
        assertEquals("Cmd+Shift+Space", root.get("globalShortcut").getAsString());
        assertTrue(root.getAsJsonObject("mcpServers").has("excudo"));
    }

    @Test
    public void registerOverwritesPreviousExcudoEntry() throws Exception {
        writeJson(configPath,
            "{\"mcpServers\":{\"excudo\":{\"url\":\"http://stale:1000/mcp/old\"}}}");

        ClaudeDesktopConfigWriter.register(configPath, URL);

        JsonObject excudo = read(configPath)
            .getAsJsonObject("mcpServers").getAsJsonObject("excudo");
        assertEquals(URL, excudo.get("url").getAsString());
    }

    @Test
    public void registerOnExistingFileCreatesBackup() throws Exception {
        writeJson(configPath, "{\"mcpServers\":{\"other\":{\"url\":\"http://x/1\"}}}");

        ClaudeDesktopConfigWriter.register(configPath, URL);

        Path backup = configPath.resolveSibling(configPath.getFileName() + ".bak");
        assertTrue(".bak must exist after register on existing file", Files.exists(backup));
        JsonObject backupRoot = JsonParser.parseString(
            Files.readString(backup, StandardCharsets.UTF_8)).getAsJsonObject();
        assertFalse("backup should NOT have the new entry",
            backupRoot.getAsJsonObject("mcpServers").has("excudo"));
    }

    @Test
    public void registerOnFreshFileDoesNotCreateBackup() throws Exception {
        ClaudeDesktopConfigWriter.register(configPath, URL);
        Path backup = configPath.resolveSibling(configPath.getFileName() + ".bak");
        assertFalse("no previous content, so no .bak expected", Files.exists(backup));
    }

    // ========== register: bad inputs ==========

    @Test
    public void registerFailsGracefullyOnNonObjectJson() throws Exception {
        writeJson(configPath, "[\"not\", \"an\", \"object\"]");

        ClaudeDesktopConfigWriter.Result result =
            ClaudeDesktopConfigWriter.register(configPath, URL);

        assertFalse(result.written());
        assertTrue(result.message().toLowerCase().contains("json"));
    }

    @Test
    public void registerHandlesEmptyFile() throws Exception {
        writeJson(configPath, "");

        ClaudeDesktopConfigWriter.Result result =
            ClaudeDesktopConfigWriter.register(configPath, URL);

        assertTrue(result.written());
        JsonObject excudo = read(configPath)
            .getAsJsonObject("mcpServers").getAsJsonObject("excudo");
        assertEquals(URL, excudo.get("url").getAsString());
    }

    // ========== deregister ==========

    @Test
    public void deregisterRemovesExcudoEntry() throws Exception {
        writeJson(configPath, "{\"mcpServers\":{\"excudo\":{\"url\":\"" + URL + "\"}}}");

        ClaudeDesktopConfigWriter.Result result =
            ClaudeDesktopConfigWriter.deregister(configPath);

        assertTrue(result.written());
        assertFalse(read(configPath).getAsJsonObject("mcpServers").has("excudo"));
    }

    @Test
    public void deregisterPreservesOtherServers() throws Exception {
        writeJson(configPath,
            "{\"mcpServers\":{\"excudo\":{\"url\":\"" + URL + "\"},\"other\":{\"url\":\"http://x/1\"}}}");

        ClaudeDesktopConfigWriter.deregister(configPath);

        JsonObject servers = read(configPath).getAsJsonObject("mcpServers");
        assertFalse(servers.has("excudo"));
        assertTrue(servers.has("other"));
    }

    @Test
    public void deregisterReturnsNotFoundWhenFileMissing() {
        Path missing = tempDir.resolve("nope.json");
        assertFalse(Files.exists(missing));

        ClaudeDesktopConfigWriter.Result result =
            ClaudeDesktopConfigWriter.deregister(missing);

        assertFalse(result.configFound());
        assertFalse(result.written());
    }

    @Test
    public void deregisterIsNoOpWhenExcudoNotPresent() throws Exception {
        writeJson(configPath, "{\"mcpServers\":{\"other\":{\"url\":\"http://x/1\"}}}");

        ClaudeDesktopConfigWriter.Result result =
            ClaudeDesktopConfigWriter.deregister(configPath);

        assertTrue("file was found", result.configFound());
        assertFalse("nothing to write", result.written());
        assertTrue(result.message().toLowerCase().contains("not present")
            || result.message().toLowerCase().contains("nothing to deregister"));
    }

    // ========== Path detection ==========

    @Test
    public void detectConfigPathReturnsPlausibleLocation() {
        Path detected = ClaudeDesktopConfigWriter.detectConfigPath();
        assertNotNull(detected);
        assertTrue("path should end in claude_desktop_config.json",
            detected.getFileName().toString().equals("claude_desktop_config.json"));
        // The parent directory name should be "Claude" on all three platforms
        assertEquals("Claude", detected.getParent().getFileName().toString());
    }

    // ========== Helpers ==========

    private static void writeJson(Path path, String content) throws IOException {
        Files.writeString(path, content, StandardCharsets.UTF_8);
    }

    private static JsonObject read(Path path) throws IOException {
        return JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8))
            .getAsJsonObject();
    }
}
