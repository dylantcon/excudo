package com.excudo.xml.writers;

import com.excudo.core.model.AnimationBinding;
import com.excudo.core.model.AnimationType;
import com.excudo.core.model.PPTXDocument;
import com.excudo.core.model.PackageIntegrityValidator;
import com.excudo.core.model.ShapeGeometry;
import com.excudo.core.model.ShapeStyle;
import com.excudo.core.model.SlideShape;
import com.excudo.core.orchestration.PPTXOrchestratorImpl;
import com.excudo.core.orchestration.PresentationScaffolder;
import com.excudo.core.results.ExecutionResult;
import org.junit.Before;
import org.junit.Test;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.*;

/**
 * Two effects on one shape in the same click (an entrance plus a with-previous
 * motion path or emphasis) used to share the click's group id, so the build
 * list held the same (spid, grpId) twice and the deck couldn't be saved.
 */
public class AnimationGroupIdTest {

    private PPTXOrchestratorImpl orchestrator;
    private PPTXDocument doc;
    private int spid;

    @Before
    public void setUp() throws Exception {
        doc = PresentationScaffolder.scaffoldDocument("excudo");
        orchestrator = new PPTXOrchestratorImpl();
        orchestrator.initialize(doc);
        orchestrator.createSlide(1, "Groups", "slideLayout7");
        doc = orchestrator.getContext().get().getDocument();
        ExecutionResult<Integer> r = orchestrator.addShape(1, SlideShape.ShapeType.RECTANGLE,
            new ShapeGeometry(0, 0, 2_000_000, 1_000_000), "Label", "Box", ShapeStyle.defaultStyle());
        assertTrue(r.isSuccess());
        spid = r.getData().orElseThrow();
        add(AnimationBinding.builder().target(spid).type(AnimationType.FADE).entrance()
            .duration("500").delay("0").animationGroup("on-click").build());
    }

    @Test
    public void entrancePlusWithPreviousMotionPathGetDistinctGroups() {
        add(AnimationBinding.builder().target(spid).type(AnimationType.MOTION_LINEAR)
            .linearMotion(0, 0, 0.25, 0).duration("1000").delay("0").withPrevious().build());
        assertDistinctAndMatching(2);
    }

    @Test
    public void entrancePlusWithPreviousEmphasisGetDistinctGroups() {
        add(AnimationBinding.builder().target(spid).type(AnimationType.PULSE).emphasis()
            .duration("500").delay("0").withPrevious().build());
        add(AnimationBinding.builder().target(spid).type(AnimationType.PULSE).emphasis()
            .duration("500").delay("0").withPrevious().build());
        assertDistinctAndMatching(3);
    }

    private void assertDistinctAndMatching(int expected) {
        assertEquals(List.of(), PackageIntegrityValidator.validate(doc));

        List<String> builds = new ArrayList<>();
        NodeList bld = doc.getSlideDocument(1).getElementsByTagName("p:bldP");
        for (int i = 0; i < bld.getLength(); i++) {
            Element e = (Element) bld.item(i);
            if (e.getAttribute("spid").equals(String.valueOf(spid))) builds.add(e.getAttribute("grpId"));
        }
        assertEquals("one build entry per effect", expected, builds.size());
        assertEquals("no repeated group ids: " + builds, expected, new HashSet<>(builds).size());

        Set<String> effectGroups = new HashSet<>();
        NodeList ctns = doc.getSlideDocument(1).getElementsByTagName("p:cTn");
        for (int i = 0; i < ctns.getLength(); i++) {
            Element c = (Element) ctns.item(i);
            if (c.hasAttribute("presetClass")) effectGroups.add(c.getAttribute("grpId"));
        }
        assertEquals("each effect's grpId has its build entry", new HashSet<>(builds), effectGroups);
    }

    private void add(AnimationBinding binding) {
        ExecutionResult<String> r = orchestrator.addAnimation(1, binding);
        assertTrue(r.getMessage(), r.isSuccess());
    }
}
