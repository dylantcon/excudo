package com.excudo.core.orchestration;

import com.excudo.core.commands.mutating.theme.ApplyThemeCommand;
import com.excudo.core.model.LayoutInfo;
import com.excudo.core.model.PPTXDocument;
import com.excudo.core.model.SlideShape;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.Assert.*;

/**
 * Theme / master / layout edits propagate to the slides that inherit from
 * them: derived state refreshes and listeners hear which slides changed.
 */
public class StylePropagationTest {

    private PPTXOrchestratorImpl orchestrator;
    private PPTXDocument doc;
    private final List<Set<Integer>> restyled = new ArrayList<>();
    private final OrchestrationStateListener recorder = new OrchestrationStateListener() {
        @Override
        public void onSlidesRestyled(Set<Integer> slideNumbers) {
            restyled.add(slideNumbers);
        }
    };

    @Before
    public void setUp() throws Exception {
        doc = PresentationScaffolder.scaffoldDocument("minimal");
        orchestrator = new PPTXOrchestratorImpl();
        orchestrator.initialize(doc);
        orchestrator.createSlide(1, "Title Slide", "slideLayout1");
        orchestrator.createSlide(2, "Content Slide", "slideLayout2");
        SessionManager.getInstance().addStateListener(recorder);
    }

    @After
    public void tearDown() {
        SessionManager.getInstance().removeStateListener(recorder);
    }

    private Set<Integer> allRestyled() {
        Set<Integer> all = new TreeSet<>();
        restyled.forEach(all::addAll);
        return all;
    }

    @Test
    public void layoutEditRestylesOnlySlidesUsingIt() {
        String part = "ppt/slideLayouts/slideLayout2.xml";
        doc.putXmlPart(part, doc.getXmlPart(part));
        assertEquals(Set.of(2), allRestyled());
    }

    @Test
    public void masterEditRestylesEverySlide() {
        String part = "ppt/slideMasters/slideMaster1.xml";
        doc.markDirty(part);
        assertEquals(Set.of(1, 2), allRestyled());
    }

    @Test
    public void slideEditDoesNotFireRestyle() {
        doc.markSlideDirty(1);
        assertTrue(restyled.isEmpty());
    }

    @Test
    public void darkThemeFlagFollowsMasterColorMap() {
        assertFalse(orchestrator.isDarkTheme());
        Document master = doc.getXmlPart("ppt/slideMasters/slideMaster1.xml");
        Element clrMap = (Element) master.getElementsByTagNameNS("*", "clrMap").item(0);
        clrMap.setAttribute("bg1", "dk1");
        doc.markDirty("ppt/slideMasters/slideMaster1.xml");
        assertTrue("dark-theme cache must refresh after a master edit", orchestrator.isDarkTheme());
    }

    @Test
    public void layoutListReflectsRenamedLayout() throws Exception {
        String part = "ppt/slideLayouts/slideLayout2.xml";
        Document layout = doc.getXmlPart(part);
        Element cSld = (Element) layout.getElementsByTagNameNS("*", "cSld").item(0);
        cSld.setAttribute("name", "Renamed Layout");
        doc.markDirty(part);

        LayoutInfo info = orchestrator.getContext().orElseThrow().getLayoutManager().getLayoutInfo("slideLayout2");
        assertEquals("Renamed Layout", info.getName());
    }

    @Test
    public void applyThemeRestylesAllSlides() {
        new ApplyThemeCommand("excudo", orchestrator, null).execute();
        assertEquals(Set.of(1, 2), allRestyled());
    }

    @Test
    public void slideTitleFollowsTitlePlaceholderText() throws Exception {
        assertEquals("Title Slide", orchestrator.getSlideMetadata(1).orElseThrow().getTitle());

        int titleSpid = -1;
        for (SlideShape shape : orchestrator.getContext().orElseThrow().getContextService()
                .getSlideContext(1).getSlideData().getShapeRegistry().getAllShapes()) {
            if ("Title Slide".equals(shape.getTextContent())) titleSpid = shape.getSpid();
        }
        assertTrue("found the title shape", titleSpid > 0);
        assertTrue(orchestrator.editShapeText(1, titleSpid, "Renamed Title").isSuccess());

        assertEquals("Renamed Title", orchestrator.getSlideMetadata(1).orElseThrow().getTitle());
    }
}
