package com.excudo.core.model;

import com.excudo.core.model.PPTXDocumentParser.ParsedPresentationState;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

/**
 * Verifies the slide -> layout -> master -> theme resolution chain added to
 * {@link PPTXDocumentParser}. On the single-master corpus this asserts the
 * chain resolves to the conventional slideMaster1 / theme1 -- i.e. the new
 * derived resolution is behavior-identical to the old hardcoded paths, the
 * prerequisite for swapping the render path onto it without regressing the
 * common case.
 */
public class MasterThemeResolutionTest {

    private static ParsedPresentationState state;
    private static int slideCount;

    @BeforeClass
    public static void parse() throws Exception {
        File f = new File("test-pptx-samples/generalist_test_file.pptx");
        assumeTrue("test deck not present: " + f, f.exists());
        PPTXDocument doc = PPTXDocument.loadFromZip(f);
        state = PPTXDocumentParser.parse(doc);
        slideCount = doc.getSlideNumbers().size();
    }

    @Test
    public void testEverySlideResolvesToAMaster() {
        for (int n = 1; n <= slideCount; n++) {
            String masterId = state.getMasterIdForSlide(n);
            assertNotNull("slide " + n + " resolved no master", masterId);
            assertTrue("slide " + n + " master id should look like slideMasterN, got " + masterId,
                masterId.matches("slideMaster\\d+"));
        }
    }

    @Test
    public void testSingleMasterDeckResolvesToMaster1() {
        // The generalist deck has exactly one master; every slide must
        // resolve to it. This is the behavior-preservation check: the
        // derived chain must match the old hardcoded "slideMaster1".
        for (int n = 1; n <= slideCount; n++) {
            assertEquals("slide " + n, "slideMaster1", state.getMasterIdForSlide(n));
            assertEquals("ppt/slideMasters/slideMaster1.xml", state.getMasterPartForSlide(n));
        }
    }

    @Test
    public void testThemeResolvesThroughMaster() {
        for (int n = 1; n <= slideCount; n++) {
            String themePart = state.getThemePartForSlide(n);
            assertNotNull(themePart);
            assertTrue("theme part should be under ppt/theme/, got " + themePart,
                themePart.startsWith("ppt/theme/theme") && themePart.endsWith(".xml"));
        }
    }

    @Test
    public void testLayoutToMasterMapPopulated() {
        // Every layout the deck actually uses must have resolved a master.
        assertFalse("no layout->master mappings resolved", state.getLayoutToMasterId().isEmpty());
        for (String masterId : state.getLayoutToMasterId().values()) {
            assertTrue(masterId.matches("slideMaster\\d+"));
        }
    }

    @Test
    public void testUnresolvableSlideFallsBackToMaster1() {
        // A slide number with no layout mapping falls back rather than NPEs --
        // matches the legacy hardcoded behavior for malformed/edge decks.
        assertEquals("slideMaster1", state.getMasterIdForSlide(99999));
        assertEquals("ppt/theme/theme1.xml", state.getThemePartForSlide(99999));
    }
}
