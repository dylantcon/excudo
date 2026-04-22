package com.excudo.core.commands;

import com.excudo.core.commands.mutating.theme.CreateThemeCommand;
import com.excudo.core.commands.mutating.theme.DeleteThemeCommand;
import com.excudo.core.commands.mutating.theme.EditThemeCommand;
import com.excudo.core.commands.readonly.ShowThemeCommand;

import com.excudo.core.themes.BundledThemes;
import com.excudo.core.themes.ThemeDefinition;
import com.excudo.core.themes.ThemeJsonSerializer;
import com.excudo.core.themes.ThemeLoader;
import org.junit.Before;
import org.junit.After;
import org.junit.Test;
import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Tests for theme CRUD commands: show-theme, create-theme, edit-theme, delete-theme.
 */
public class ThemeCRUDCommandsTest {

    private File tempDir;
    private TestDisplay display;

    @Before
    public void setUp() throws IOException {
        tempDir = Files.createTempDirectory("theme-crud-test").toFile();
        new File(tempDir, "themes/builtin").mkdirs();
        // Write minimal theme JSON for testing
        String minimalJson = ThemeJsonSerializer.serialize(BundledThemes.get("minimal"));
        Files.writeString(new File(tempDir, "themes/builtin/minimal.json").toPath(), minimalJson);
        String corpJson = ThemeJsonSerializer.serialize(BundledThemes.get("corporate"));
        Files.writeString(new File(tempDir, "themes/builtin/corporate.json").toPath(), corpJson);
        ThemeLoader.initialize(tempDir);
        display = new TestDisplay();
    }

    @After
    public void tearDown() {
        ThemeLoader.initialize(); // Restore real project root
        deleteRecursive(tempDir);
    }

    @Test
    public void showTheme_displaysAllFields() {
        ShowThemeCommand cmd = new ShowThemeCommand("minimal", display);
        cmd.execute();
        assertTrue(cmd.isExecuted());
        String output = display.getAllMessages();
        assertTrue(output.contains("Minimal"));
        assertTrue(output.contains("Inter"));
        assertTrue(output.contains("dk1"));
        assertTrue(output.contains("slideLayout1"));
    }

    @Test
    public void showTheme_unknownTheme_showsError() {
        ShowThemeCommand cmd = new ShowThemeCommand("nonexistent", display);
        cmd.execute();
        assertTrue(display.hasError());
    }

    @Test
    public void createTheme_savesToDisk() {
        CreateThemeCommand cmd = new CreateThemeCommand("test-new", "minimal", "Test New Theme", display);
        cmd.execute();
        assertTrue(cmd.isExecuted());
        assertTrue(ThemeLoader.exists("test-new"));
        assertTrue(ThemeLoader.isCustomTheme("test-new"));

        ThemeDefinition created = ThemeLoader.get("test-new");
        assertEquals("Test New Theme", created.getDisplayName());
        assertEquals("Inter", created.getMajorFont()); // inherited from minimal
        assertEquals(10, created.getLayouts().size());
    }

    @Test
    public void createTheme_duplicateId_showsError() {
        // Create first
        new CreateThemeCommand("dup-test", "minimal", "First", display).execute();
        display.clear();
        // Try duplicate
        new CreateThemeCommand("dup-test", "minimal", "Second", display).execute();
        assertTrue(display.hasError());
    }

    @Test
    public void editTheme_modifiesColor() throws IOException {
        // Create custom theme first
        new CreateThemeCommand("edit-test", "minimal", "Edit Test", display).execute();
        display.clear();

        EditThemeCommand cmd = new EditThemeCommand("edit-test", "color.accent1", "FF5733", display);
        cmd.execute();
        assertTrue(cmd.isExecuted());
        assertFalse(display.hasError());

        ThemeDefinition edited = ThemeLoader.get("edit-test");
        assertEquals("FF5733", edited.getColor("accent1"));
    }

    @Test
    public void editTheme_modifiesFont() throws IOException {
        new CreateThemeCommand("font-test", "minimal", "Font Test", display).execute();
        display.clear();

        new EditThemeCommand("font-test", "majorFont", "Georgia", display).execute();
        assertFalse(display.hasError());

        assertEquals("Georgia", ThemeLoader.get("font-test").getMajorFont());
    }

    @Test
    public void editTheme_refusesBuiltin() {
        EditThemeCommand cmd = new EditThemeCommand("minimal", "majorFont", "Arial", display);
        cmd.execute();
        assertTrue(display.hasError());
        assertTrue(display.getLastError().contains("builtin"));
    }

    @Test
    public void deleteTheme_removesCustom() {
        new CreateThemeCommand("del-test", "minimal", "Del Test", display).execute();
        assertTrue(ThemeLoader.exists("del-test"));
        display.clear();

        new DeleteThemeCommand("del-test", display).execute();
        assertFalse(ThemeLoader.exists("del-test"));
        assertFalse(display.hasError());
    }

    @Test
    public void deleteTheme_refusesBuiltin() {
        new DeleteThemeCommand("minimal", display).execute();
        assertTrue(display.hasError());
        assertTrue(display.getLastError().contains("builtin"));
        assertTrue(ThemeLoader.exists("minimal"));
    }

    // Test display helper
    private static class TestDisplay implements CommandDisplay {
        private final List<String> messages = new ArrayList<>();
        private final List<String> errors = new ArrayList<>();
        private final List<String> successes = new ArrayList<>();

        @Override public void displayMessage(String message) { messages.add(message); }
        @Override public void displayError(String message) { errors.add(message); }
        @Override public void displaySuccess(String message) { successes.add(message); }

        boolean hasError() { return !errors.isEmpty(); }
        String getLastError() { return errors.isEmpty() ? "" : errors.get(errors.size() - 1); }
        String getAllMessages() {
            StringBuilder sb = new StringBuilder();
            messages.forEach(m -> sb.append(m).append("\n"));
            successes.forEach(m -> sb.append(m).append("\n"));
            return sb.toString();
        }
        void clear() { messages.clear(); errors.clear(); successes.clear(); }
    }

    private static void deleteRecursive(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) deleteRecursive(child);
            }
        }
        file.delete();
    }
}
