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
    private Path bridgeScript;

    @Before
    public void setUp() throws IOException {
        tempDir = Files.createTempDirectory("claude-config-test");
        configPath = tempDir.resolve("claude_desktop_config.json");
        // The writer stores the absolute path of whatever Path it's given;
        // it does not require the file to exist on disk.
        bridgeScript = tempDir.resolve("tools/mcp-server/excudo_bridge.py");
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
            ClaudeDesktopConfigWriter.register(configPath, bridgeScript);

        assertTrue(result.written());
        assertTrue(Files.exists(configPath));

        JsonObject root = read(configPath);
        JsonObject servers = root.getAsJsonObject("mcpServers");
        JsonObject excudo = servers.getAsJsonObject("excudo");
        // Claude Desktop only accepts stdio transport. We hand it our own
        // python bridge script that always presents a healthy handshake
        // and proxies to the live HTTP server when Excudo is up.
        assertIsPythonCommand(excudo.get("command").getAsString());
        assertTrue("args should be a JSON array", excudo.get("args").isJsonArray());
        var args = excudo.getAsJsonArray("args");
        assertEquals(1, args.size());
        assertEquals(bridgeScript.toAbsolutePath().toString(), args.get(0).getAsString());
    }

    @Test
    public void registerWritesAbsolutePathEvenForRelativeBridgeArg() throws Exception {
        Path relative = Path.of("tools/mcp-server/excudo_bridge.py");

        ClaudeDesktopConfigWriter.register(configPath, relative);

        JsonObject excudo = read(configPath)
            .getAsJsonObject("mcpServers").getAsJsonObject("excudo");
        String written = excudo.getAsJsonArray("args").get(0).getAsString();
        assertTrue("config must contain an absolute path so Claude Desktop "
                + "can launch the bridge from any cwd, got: " + written,
            Path.of(written).isAbsolute());
    }

    @Test
    public void registerCreatesParentDirectoriesIfMissing() throws Exception {
        Path nested = tempDir.resolve("nested/deeper/claude_desktop_config.json");
        assertFalse(Files.exists(nested.getParent()));

        ClaudeDesktopConfigWriter.Result result =
            ClaudeDesktopConfigWriter.register(nested, bridgeScript);

        assertTrue(result.written());
        assertTrue(Files.exists(nested));
    }

    // ========== register: existing file, preservation ==========

    @Test
    public void registerPreservesExistingServers() throws Exception {
        writeJson(configPath, "{\"mcpServers\":{\"other-tool\":{\"command\":\"node\",\"args\":[\"x.js\"]}}}");

        ClaudeDesktopConfigWriter.register(configPath, bridgeScript);

        JsonObject servers = read(configPath).getAsJsonObject("mcpServers");
        assertTrue("previous server must still be present", servers.has("other-tool"));
        assertTrue("new excudo entry must be present", servers.has("excudo"));
    }

    @Test
    public void registerPreservesTopLevelNonServerFields() throws Exception {
        writeJson(configPath, "{\"globalShortcut\":\"Cmd+Shift+Space\",\"mcpServers\":{}}");

        ClaudeDesktopConfigWriter.register(configPath, bridgeScript);

        JsonObject root = read(configPath);
        assertEquals("Cmd+Shift+Space", root.get("globalShortcut").getAsString());
        assertTrue(root.getAsJsonObject("mcpServers").has("excudo"));
    }

    @Test
    public void registerOverwritesPreviousExcudoEntry() throws Exception {
        // Previous Excudo installs wrote either the rejected "url" shape or
        // the older "npx mcp-remote URL" stdio shape. The new writer must
        // fully replace them, not merge, so Claude Desktop sees only our
        // current python-bridge entry with no stale args or keys.
        writeJson(configPath,
            "{\"mcpServers\":{\"excudo\":{\"command\":\"npx\","
            + "\"args\":[\"-y\",\"mcp-remote\",\"http://stale:1000/mcp/old\"],"
            + "\"url\":\"http://also-stale\"}}}");

        ClaudeDesktopConfigWriter.register(configPath, bridgeScript);

        JsonObject excudo = read(configPath)
            .getAsJsonObject("mcpServers").getAsJsonObject("excudo");
        assertFalse("stale 'url' key must be gone", excudo.has("url"));
        assertIsPythonCommand(excudo.get("command").getAsString());
        assertEquals("args must be replaced, not merged",
            1, excudo.getAsJsonArray("args").size());
        assertEquals(bridgeScript.toAbsolutePath().toString(),
            excudo.getAsJsonArray("args").get(0).getAsString());
    }

    @Test
    public void registerOnExistingFileCreatesBackup() throws Exception {
        writeJson(configPath, "{\"mcpServers\":{\"other\":{\"url\":\"http://x/1\"}}}");

        ClaudeDesktopConfigWriter.register(configPath, bridgeScript);

        Path backup = configPath.resolveSibling(configPath.getFileName() + ".bak");
        assertTrue(".bak must exist after register on existing file", Files.exists(backup));
        JsonObject backupRoot = JsonParser.parseString(
            Files.readString(backup, StandardCharsets.UTF_8)).getAsJsonObject();
        assertFalse("backup should NOT have the new entry",
            backupRoot.getAsJsonObject("mcpServers").has("excudo"));
    }

    @Test
    public void registerOnFreshFileDoesNotCreateBackup() throws Exception {
        ClaudeDesktopConfigWriter.register(configPath, bridgeScript);
        Path backup = configPath.resolveSibling(configPath.getFileName() + ".bak");
        assertFalse("no previous content, so no .bak expected", Files.exists(backup));
    }

    // ========== register: bad inputs ==========

    @Test
    public void registerFailsGracefullyOnNonObjectJson() throws Exception {
        writeJson(configPath, "[\"not\", \"an\", \"object\"]");

        ClaudeDesktopConfigWriter.Result result =
            ClaudeDesktopConfigWriter.register(configPath, bridgeScript);

        assertFalse(result.written());
        assertTrue(result.message().toLowerCase().contains("json"));
    }

    @Test
    public void registerHandlesEmptyFile() throws Exception {
        writeJson(configPath, "");

        ClaudeDesktopConfigWriter.Result result =
            ClaudeDesktopConfigWriter.register(configPath, bridgeScript);

        assertTrue(result.written());
        JsonObject excudo = read(configPath)
            .getAsJsonObject("mcpServers").getAsJsonObject("excudo");
        assertIsPythonCommand(excudo.get("command").getAsString());
        assertEquals(bridgeScript.toAbsolutePath().toString(),
            excudo.getAsJsonArray("args").get(0).getAsString());
    }

    // ========== deregister ==========

    @Test
    public void deregisterRemovesExcudoEntry() throws Exception {
        // Windows paths use backslashes; raw interpolation into a JSON
        // string literal produces invalid escape sequences ("\U", "\A",
        // etc.) and the writer can't parse the file. Escape for JSON.
        String bridgePath = bridgeScript.toAbsolutePath().toString().replace("\\", "\\\\");
        writeJson(configPath,
            "{\"mcpServers\":{\"excudo\":{\"command\":\"python3\",\"args\":[\""
            + bridgePath + "\"]}}}");

        ClaudeDesktopConfigWriter.Result result =
            ClaudeDesktopConfigWriter.deregister(configPath);

        assertTrue(result.written());
        assertFalse(read(configPath).getAsJsonObject("mcpServers").has("excudo"));
    }

    @Test
    public void deregisterPreservesOtherServers() throws Exception {
        writeJson(configPath,
            "{\"mcpServers\":{\"excudo\":{\"command\":\"python3\",\"args\":[\"x\"]},"
            + "\"other\":{\"command\":\"node\",\"args\":[\"y\"]}}}");

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
        writeJson(configPath, "{\"mcpServers\":{\"other\":{\"command\":\"node\",\"args\":[\"y\"]}}}");

        ClaudeDesktopConfigWriter.Result result =
            ClaudeDesktopConfigWriter.deregister(configPath);

        assertTrue("file was found", result.configFound());
        assertFalse("nothing to write", result.written());
        assertTrue(result.message().toLowerCase().contains("not present")
            || result.message().toLowerCase().contains("nothing to deregister"));
    }

    // ========== resolvePythonCommand ==========

    @Test
    public void resolvePythonPrefersLauncherInterpreterWhenItExists() throws IOException {
        // EXCUDO_PYTHON pointing at a real file wins outright -- this is the
        // interpreter pc.py used, guaranteed to exist and carry the stdlib.
        Path launcher = tempDir.resolve("launcher-python.exe");
        Files.writeString(launcher, "");
        assertEquals(launcher.toString(),
            ClaudeDesktopConfigWriter.resolvePythonCommand(
                launcher.toString(), "/nonexistent", "Windows 11"));
    }

    @Test
    public void resolvePythonSearchesPathWhenLauncherUnset() throws IOException {
        // No launcher hint: a python on PATH is found and returned absolute.
        Path binDir = Files.createDirectory(tempDir.resolve("bin"));
        Path py = binDir.resolve("python3");
        Files.writeString(py, "");
        assertEquals(py.toAbsolutePath().toString(),
            ClaudeDesktopConfigWriter.resolvePythonCommand(
                null, binDir.toString(), "Linux"));
    }

    @Test
    public void resolvePythonFallsBackWhenNothingFound() {
        // Blank launcher + a PATH with no python -> OS-appropriate fallback.
        assertEquals("python", ClaudeDesktopConfigWriter.resolvePythonCommand(
            "", tempDir.resolve("empty").toString(), "Windows 11"));
        assertEquals("python3", ClaudeDesktopConfigWriter.resolvePythonCommand(
            null, null, "Linux"));
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

    /** The bridge command is now an environment-resolved python interpreter
     *  (absolute path, or a "python"/"python3"/"py" fallback), not a fixed
     *  literal -- assert it references a python rather than a specific value. */
    private static void assertIsPythonCommand(String command) {
        assertNotNull(command);
        assertFalse("bridge command must not be blank", command.isBlank());
        assertTrue("bridge command must reference a python interpreter: " + command,
            command.toLowerCase().contains("py"));
    }
}
