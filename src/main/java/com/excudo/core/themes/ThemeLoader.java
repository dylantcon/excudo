package com.excudo.core.themes;

import com.excudo.core.themes.LayoutDefinition.LayoutType;
import com.excudo.core.utils.ComponentLogger;
import com.excudo.core.utils.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

/**
 * Loads theme definitions from JSON files on disk.
 *
 * Theme storage layout:
 *   themes/builtin/  -- 3 built-in themes tracked in git
 *   themes/custom/   -- user-defined themes (gitignored)
 *
 * JSON format maps directly to ThemeDefinition builder fields.
 * Themes are identified by filename (without .json extension).
 */
public final class ThemeLoader {

    private static final ComponentLogger logger = Logger.console();

    private static File projectRoot;
    private static final Map<String, ThemeDefinition> loadedThemes = new LinkedHashMap<>();
    private static boolean initialized = false;

    private ThemeLoader() {}

    /**
     * Initialize the loader by discovering the project root and loading all themes.
     * The project root is found by walking up from the current working directory
     * until we find a themes/ directory.
     */
    public static synchronized void initialize() {
        projectRoot = findProjectRoot();
        reload();
    }

    /**
     * Initialize with an explicit project root (for testing).
     */
    public static synchronized void initialize(File root) {
        projectRoot = root;
        reload();
    }

    /**
     * Reload all themes from disk.
     */
    public static synchronized void reload() {
        loadedThemes.clear();

        if (projectRoot == null) {
            logger.warn("ThemeLoader: project root not set, using hardcoded fallback");
            loadedThemes.putAll(BundledThemes.getHardcodedThemes());
            initialized = true;
            return;
        }

        File builtinDir = new File(projectRoot, "themes/builtin");
        File customDir = new File(projectRoot, "themes/custom");

        // Load builtin themes first (lower priority)
        if (builtinDir.isDirectory()) {
            loadThemesFromDirectory(builtinDir, true);
        } else {
            logger.warn("ThemeLoader: builtin themes directory not found at " + builtinDir.getAbsolutePath()
                    + " -- falling back to hardcoded themes");
            loadedThemes.putAll(BundledThemes.getHardcodedThemes());
        }

        // Load custom themes (higher priority, can override builtin)
        if (customDir.isDirectory()) {
            loadThemesFromDirectory(customDir, false);
        }

        initialized = true;
        logger.info("ThemeLoader: loaded " + loadedThemes.size() + " themes"
                + " (" + countBuiltin() + " builtin, " + (loadedThemes.size() - countBuiltin()) + " custom)");
    }

    public static ThemeDefinition get(String id) {
        ensureInitialized();
        ThemeDefinition theme = loadedThemes.get(id.toLowerCase());
        if (theme == null) {
            throw new IllegalArgumentException("Unknown theme: '" + id + "'. Available: " + getAvailableIds());
        }
        return theme;
    }

    public static List<ThemeDefinition> getAll() {
        ensureInitialized();
        return new ArrayList<>(loadedThemes.values());
    }

    public static List<String> getAvailableIds() {
        ensureInitialized();
        return new ArrayList<>(loadedThemes.keySet());
    }

    public static boolean exists(String id) {
        ensureInitialized();
        return loadedThemes.containsKey(id.toLowerCase());
    }

    /**
     * Save a theme definition to the custom themes directory.
     */
    public static void saveCustomTheme(ThemeDefinition theme) throws IOException {
        if (projectRoot == null) {
            throw new IOException("Project root not set -- cannot save custom themes");
        }
        File customDir = new File(projectRoot, "themes/custom");
        if (!customDir.exists()) {
            customDir.mkdirs();
        }
        File themeFile = new File(customDir, theme.getId() + ".json");
        String json = ThemeJsonSerializer.serialize(theme);
        Files.writeString(themeFile.toPath(), json);
        loadedThemes.put(theme.getId(), theme);
        logger.info("Saved custom theme: " + theme.getId() + " -> " + themeFile.getAbsolutePath());
    }

    /**
     * Delete a custom theme. Refuses to delete builtin themes.
     */
    public static boolean deleteCustomTheme(String id) {
        if (projectRoot == null) return false;
        File customFile = new File(projectRoot, "themes/custom/" + id.toLowerCase() + ".json");
        if (customFile.exists() && customFile.delete()) {
            // Only remove from cache if it was a custom theme (not shadowing builtin)
            File builtinFile = new File(projectRoot, "themes/builtin/" + id.toLowerCase() + ".json");
            if (builtinFile.exists()) {
                // Reload to restore builtin version
                reload();
            } else {
                loadedThemes.remove(id.toLowerCase());
            }
            logger.info("Deleted custom theme: " + id);
            return true;
        }
        return false;
    }

    public static boolean isCustomTheme(String id) {
        if (projectRoot == null) return false;
        return new File(projectRoot, "themes/custom/" + id.toLowerCase() + ".json").exists();
    }

    // ========== Private Helpers ==========

    private static void ensureInitialized() {
        if (!initialized) {
            initialize();
        }
    }

    private static void loadThemesFromDirectory(File dir, boolean builtin) {
        File[] jsonFiles = dir.listFiles((d, name) -> name.endsWith(".json"));
        if (jsonFiles == null) return;

        Arrays.sort(jsonFiles, Comparator.comparing(File::getName));

        for (File jsonFile : jsonFiles) {
            try {
                String content = Files.readString(jsonFile.toPath());
                ThemeDefinition theme = ThemeJsonSerializer.deserialize(content);
                loadedThemes.put(theme.getId(), theme);
            } catch (Exception e) {
                logger.warn("ThemeLoader: failed to load " + jsonFile.getName() + ": " + e.getMessage());
            }
        }
    }

    private static long countBuiltin() {
        if (projectRoot == null) return loadedThemes.size();
        File builtinDir = new File(projectRoot, "themes/builtin");
        File[] files = builtinDir.listFiles((d, name) -> name.endsWith(".json"));
        return files != null ? files.length : 0;
    }

    private static File findProjectRoot() {
        // Try CWD first
        File cwd = new File(System.getProperty("user.dir"));
        if (new File(cwd, "themes").isDirectory()) return cwd;

        // Walk up from CWD
        File parent = cwd.getParentFile();
        while (parent != null) {
            if (new File(parent, "themes").isDirectory()) return parent;
            parent = parent.getParentFile();
        }

        // Fallback: try relative to this class's location via CLAUDE.md presence
        File classDir = cwd;
        if (new File(classDir, "CLAUDE.md").exists()) return classDir;

        return null;
    }
}
