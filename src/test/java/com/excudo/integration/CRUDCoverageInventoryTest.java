package com.excudo.integration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;

import java.io.File;

/**
 * Inventory scanner for CRUD test directories.
 *
 * Reports file counts per category as a progress dashboard.
 * Never fails -- uses Assumptions to skip gracefully if directories are missing.
 * Run with test output visible to see the inventory report.
 */
public class CRUDCoverageInventoryTest {

    private static final String SAMPLES_ROOT = "test-pptx-samples";
    private static final String[] CATEGORIES = {
        "animations-crud", "slide-crud", "slidenotes-crud",
        "shapes-crud", "textel-crud", "theme-crud"
    };

    @Test
    public void reportCRUDCoverageInventory() {
        System.out.println("\n=== CRUD Coverage Inventory ===\n");

        int totalNative = 0;
        int totalRaw = 0;
        int totalRepaired = 0;
        int totalPaired = 0;

        for (String category : CATEGORIES) {
            File baseDir = new File(SAMPLES_ROOT, category);
            Assumptions.assumeTrue(baseDir.isDirectory(),
                    "CRUD directory missing: " + category);

            File nativeDir = new File(baseDir, "native");
            File rawDir = new File(baseDir, "raw");
            File repairedDir = new File(baseDir, "repaired");

            int nativeCount = countPptxFiles(nativeDir);
            int rawCount = countPptxFiles(rawDir);
            int repairedCount = countPptxFiles(repairedDir);
            int pairedCount = countPairedFiles(rawDir, repairedDir);

            totalNative += nativeCount;
            totalRaw += rawCount;
            totalRepaired += repairedCount;
            totalPaired += pairedCount;

            System.out.printf("[%-16s] native=%-3d raw=%-3d repaired=%-3d paired=%-3d%n",
                    category, nativeCount, rawCount, repairedCount, pairedCount);
        }

        System.out.println();
        System.out.printf("[%-16s] native=%-3d raw=%-3d repaired=%-3d paired=%-3d%n",
                "TOTAL", totalNative, totalRaw, totalRepaired, totalPaired);
        System.out.println("\n=== End Inventory ===\n");
    }

    private int countPptxFiles(File dir) {
        if (!dir.isDirectory()) return 0;
        File[] files = dir.listFiles((d, name) -> name.endsWith(".pptx"));
        return files != null ? files.length : 0;
    }

    /**
     * Counts raw files that have a matching repaired file (same base name with _repaired suffix).
     */
    private int countPairedFiles(File rawDir, File repairedDir) {
        if (!rawDir.isDirectory() || !repairedDir.isDirectory()) return 0;
        File[] rawFiles = rawDir.listFiles((d, name) -> name.endsWith(".pptx"));
        if (rawFiles == null) return 0;

        int paired = 0;
        for (File raw : rawFiles) {
            String baseName = raw.getName().replace(".pptx", "");
            File repaired = new File(repairedDir, baseName + "_repaired.pptx");
            if (repaired.exists()) {
                paired++;
            }
        }
        return paired;
    }
}
