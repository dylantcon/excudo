package com.excudo.test.utils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Committed sample decks under {@code test-pptx-samples/}, extracted on demand.
 *
 * <p>Tests used to read pre-extracted {@code *_extracted} folders that nothing
 * created (so they always failed), or unzip into the repo root. This extracts
 * a deck once per JVM into a temp directory that is deleted on exit. Treat
 * the result as read-only; tests that mutate files should copy it first
 * ({@link #copyOf}).
 */
public final class FixtureDecks {

    private static final Map<String, Path> EXTRACTED = new ConcurrentHashMap<>();

    private FixtureDecks() {}

    /** The committed deck file, e.g. {@code deck("generalist_test_file")}. */
    public static File deck(String name) {
        File f = new File("test-pptx-samples/" + name + ".pptx");
        if (!f.exists()) f = new File("../../../test-pptx-samples/" + name + ".pptx");
        if (!f.exists()) {
            throw new AssertionError("Required fixture deck not found: test-pptx-samples/" + name + ".pptx");
        }
        return f;
    }

    /** The deck's extracted package directory (shared, read-only). */
    public static Path extracted(String name) {
        return EXTRACTED.computeIfAbsent(name, FixtureDecks::extract);
    }

    /** A private, writable copy of the deck's extracted package. */
    public static Path copyOf(String name) throws IOException {
        Path source = extracted(name);
        Path target = Files.createTempDirectory("fixture-" + name + "-copy-");
        deleteOnExit(target);
        try (Stream<Path> walk = Files.walk(source)) {
            for (Path p : (Iterable<Path>) walk::iterator) {
                Path dest = target.resolve(source.relativize(p).toString());
                if (Files.isDirectory(p)) Files.createDirectories(dest);
                else Files.copy(p, dest, StandardCopyOption.REPLACE_EXISTING);
            }
        }
        return target;
    }

    private static Path extract(String name) {
        try {
            Path root = Files.createTempDirectory("fixture-" + name + "-");
            deleteOnExit(root);
            try (ZipFile zip = new ZipFile(deck(name))) {
                Enumeration<? extends ZipEntry> entries = zip.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    Path dest = root.resolve(entry.getName()).normalize();
                    if (!dest.startsWith(root)) throw new IOException("Zip entry escapes target: " + entry.getName());
                    if (entry.isDirectory()) {
                        Files.createDirectories(dest);
                        continue;
                    }
                    Files.createDirectories(dest.getParent());
                    try (InputStream in = zip.getInputStream(entry)) {
                        Files.copy(in, dest, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
            return root;
        } catch (IOException e) {
            throw new AssertionError("Failed to extract fixture deck " + name + ": " + e.getMessage(), e);
        }
    }

    /** Recursively delete {@code root} at JVM exit (File.deleteOnExit can't remove non-empty dirs). */
    public static void deleteOnExit(Path root) {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> deleteTree(root)));
    }

    public static void deleteTree(Path root) {
        if (root == null || !Files.exists(root)) return;
        try (Stream<Path> walk = Files.walk(root)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        } catch (IOException ignored) {
            // best effort
        }
    }
}
