package com.excudo.core.orchestration;

import com.excudo.core.model.PPTXDocument;
import com.excudo.core.themes.TextLevelStyle;
import com.excudo.core.themes.ThemeManager;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * Tests SlideMasterOrchestrationManager: clrMap, master styles, background resolution.
 */
public class SlideMasterOrchestrationManagerTest {

    private PPTXOrchestratorImpl orchestrator;
    private SlideMasterOrchestrationManager manager;

    private static final File TEST_FILE = resolveTestFile();

    private static File resolveTestFile() {
        File f = new File("test-pptx-samples/generalist_test_file.pptx");
        if (!f.exists()) f = new File("../../../test-pptx-samples/generalist_test_file.pptx");
        return f;
    }

    @Before
    public void setUp() throws Exception {
        if (!TEST_FILE.exists()) fail("Required fixture not found: " + TEST_FILE.getAbsolutePath());
        orchestrator = new PPTXOrchestratorImpl();
        orchestrator.loadPresentation(TEST_FILE);
    }

    @Test
    public void getClrMapReturnsNonEmpty() throws Exception {
        if (!TEST_FILE.exists()) fail("Required fixture not found: " + TEST_FILE.getAbsolutePath());
        Map<String, String> clrMap = orchestrator.getClrMap();

        assertNotNull(clrMap);
        assertFalse("clrMap should not be empty", clrMap.isEmpty());
        assertTrue("clrMap should have bg1", clrMap.containsKey("bg1"));
        assertTrue("clrMap should have tx1", clrMap.containsKey("tx1"));
    }

    @Test
    public void getClrMapHasStandardMappings() throws Exception {
        if (!TEST_FILE.exists()) fail("Required fixture not found: " + TEST_FILE.getAbsolutePath());
        Map<String, String> clrMap = orchestrator.getClrMap();

        // generalist_test_file.pptx is a dark theme: bg1=dk1, tx1=lt1
        assertEquals("dk1", clrMap.get("bg1"));
        assertEquals("lt1", clrMap.get("tx1"));
    }

    @Test
    public void getClrMapHasAccentIdentityMappings() throws Exception {
        if (!TEST_FILE.exists()) fail("Required fixture not found: " + TEST_FILE.getAbsolutePath());
        Map<String, String> clrMap = orchestrator.getClrMap();

        // Accent colors map to themselves
        assertEquals("accent1", clrMap.get("accent1"));
        assertEquals("accent2", clrMap.get("accent2"));
    }

    @Test
    public void getBackgroundColorHexReturnsColor() throws Exception {
        if (!TEST_FILE.exists()) fail("Required fixture not found: " + TEST_FILE.getAbsolutePath());
        String bgHex = orchestrator.getBackgroundColorHex(1);

        assertNotNull("Background should be resolved", bgHex);
        assertTrue("Background should be hex starting with #",
            bgHex.startsWith("#"));
        // generalist_test_file.pptx has turquoise background
        assertEquals("#103C3F", bgHex);
    }

    @Test
    public void getBackgroundColorHexForAllSlides() throws Exception {
        if (!TEST_FILE.exists()) fail("Required fixture not found: " + TEST_FILE.getAbsolutePath());
        // All slides in the test file should inherit the master background
        for (int i = 1; i <= 5; i++) {
            String bgHex = orchestrator.getBackgroundColorHex(i);
            assertNotNull("Slide " + i + " should have background", bgHex);
        }
    }

    @Test
    public void getMasterInfoContainsExpectedKeys() throws Exception {
        if (!TEST_FILE.exists()) fail("Required fixture not found: " + TEST_FILE.getAbsolutePath());

        // Access through the orchestrator's slide master info
        var context = orchestrator.getContext();
        assertTrue(context.isPresent());

        Map<String, String> clrMap = orchestrator.getClrMap();
        assertNotNull(clrMap);
        assertTrue(clrMap.size() >= 6); // at least bg1, tx1, bg2, tx2, accent1, accent2
    }
}
