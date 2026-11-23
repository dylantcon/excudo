package com.excudo.xml.writers;

import com.excudo.core.commands.CommandFactory;
import com.excudo.core.commands.CommandInvoker;
import com.excudo.core.llm.ToolDispatcher;
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
import java.util.List;

import static org.junit.Assert.*;

/**
 * A slide's first animation set to With or After Previous starts when the
 * slide opens, as in PowerPoint. It used to be refused ("Cannot use
 * with-previous/after-previous as first animation on a slide").
 */
public class AnimationAutoStartTest {

    private PPTXOrchestratorImpl orchestrator;
    private PPTXDocument doc;
    private int a, b;

    @Before
    public void setUp() throws Exception {
        orchestrator = new PPTXOrchestratorImpl();
        orchestrator.initialize(PresentationScaffolder.scaffoldDocument("excudo"));
        orchestrator.createSlide(1, "Auto", "slideLayout7");
        doc = orchestrator.getContext().get().getDocument();
        a = rect(0);
        b = rect(1_000_000);
    }

    @Test
    public void afterPreviousFirstStartsWhenTheSlideOpens() {
        add(fade(a).afterPrevious().build());

        List<Element> groups = clickGroups();
        assertEquals(1, groups.size());
        Element onBegin = onBegin(groups.get(0));
        assertNotNull("the first group begins with the slide", onBegin);
        Element tn = (Element) onBegin.getElementsByTagNameNS("*", "tn").item(0);
        assertEquals("it points at the main sequence", mainSeqId(), tn.getAttribute("val"));
        assertEquals("afterEffect", effects().get(0).getAttribute("nodeType"));
        assertEquals(List.of(), PackageIntegrityValidator.validate(doc));
    }

    @Test
    public void laterEffectsJoinOrFollowIt() {
        add(fade(a).withPrevious().build());
        add(fade(b).withPrevious().build());   // joins the opening group
        add(fade(b).exit().animationGroup("on-click").build()); // then a click

        List<Element> groups = clickGroups();
        assertEquals(2, groups.size());
        assertNotNull(onBegin(groups.get(0)));
        assertNull("only the first group starts with the slide", onBegin(groups.get(1)));
        assertEquals("withEffect", effects().get(0).getAttribute("nodeType"));
        assertEquals(List.of(), PackageIntegrityValidator.validate(doc));
    }

    @Test
    public void theListingSaysItStartsWhenTheSlideOpens() {
        add(fade(a).afterPrevious().build());
        ToolDispatcher dispatcher = new ToolDispatcher(orchestrator, new CommandFactory(orchestrator), new CommandInvoker());
        String out = dispatcher.dispatch("get_slide_animations", "{\"slideNumber\":1}");
        assertTrue(out, out.contains("after-previous (starts when the slide opens)"));
    }

    @Test
    public void aFailedFirstAnimationLeavesNoEmptyTimeline() {
        ExecutionResult<String> r = orchestrator.addAnimation(1,
            fade(a).withPrevious().effectParam(AnimationBinding.PARAM_WITH_ANIMATION_ID, "99999").build());
        assertFalse(r.isSuccess());
        assertEquals(0, doc.getSlideDocument(1).getElementsByTagNameNS("*", "timing").getLength());
    }

    // ========== Helpers ==========

    private int rect(long x) {
        ExecutionResult<Integer> r = orchestrator.addShape(1, SlideShape.ShapeType.RECTANGLE,
            new ShapeGeometry(x, 0, 900_000, 500_000), "Box", "Box", ShapeStyle.defaultStyle());
        assertTrue(r.isSuccess());
        return r.getData().orElseThrow();
    }

    private AnimationBinding.Builder fade(int spid) {
        return AnimationBinding.builder().target(spid).type(AnimationType.FADE).entrance()
            .duration("500").delay("0");
    }

    private void add(AnimationBinding binding) {
        ExecutionResult<String> r = orchestrator.addAnimation(1, binding);
        assertTrue(r.getMessage(), r.isSuccess());
    }

    private Element mainSeq() {
        NodeList ctns = doc.getSlideDocument(1).getElementsByTagNameNS("*", "cTn");
        for (int i = 0; i < ctns.getLength(); i++) {
            Element c = (Element) ctns.item(i);
            if ("mainSeq".equals(c.getAttribute("nodeType"))) return c;
        }
        fail("no main sequence");
        return null;
    }

    private String mainSeqId() { return mainSeq().getAttribute("id"); }

    /** The main sequence's top-level groups (their cTn). */
    private List<Element> clickGroups() {
        List<Element> out = new ArrayList<>();
        Element childTnLst = child(mainSeq(), "childTnLst");
        for (org.w3c.dom.Node n = childTnLst.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element par && "par".equals(par.getLocalName())) out.add(child(par, "cTn"));
        }
        return out;
    }

    private Element onBegin(Element groupCTn) {
        Element stCondLst = child(groupCTn, "stCondLst");
        for (org.w3c.dom.Node n = stCondLst.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element c && "onBegin".equals(c.getAttribute("evt"))) return c;
        }
        return null;
    }

    private List<Element> effects() {
        List<Element> out = new ArrayList<>();
        NodeList ctns = doc.getSlideDocument(1).getElementsByTagNameNS("*", "cTn");
        for (int i = 0; i < ctns.getLength(); i++) {
            Element c = (Element) ctns.item(i);
            if (c.hasAttribute("presetClass")) out.add(c);
        }
        return out;
    }

    private static Element child(Element parent, String localName) {
        for (org.w3c.dom.Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element e && localName.equals(e.getLocalName())) return e;
        }
        return null;
    }
}
