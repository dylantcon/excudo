package com.excudo.xml.writers;

import com.excudo.core.commands.mutating.slide.AddAnimationCommand;
import com.excudo.core.model.AnimationBinding;
import com.excudo.core.model.AnimationType;
import com.excudo.core.model.PPTXDocument;
import com.excudo.core.model.ShapeGeometry;
import com.excudo.core.model.ShapeStyle;
import com.excudo.core.model.SlideShape;
import com.excudo.core.orchestration.PPTXOrchestratorImpl;
import com.excudo.core.orchestration.PresentationScaffolder;
import com.excudo.core.results.ExecutionResult;
import org.junit.Before;
import org.junit.Test;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Positional insertion (withAnimationId / afterAnimationId / beforeAnimationId)
 * and click-lead promotion on removal, checked on the written click structure.
 */
public class AnimationPlacementTest {

    private PPTXOrchestratorImpl orchestrator;
    private int a, b, c, text;

    @Before
    public void setUp() throws Exception {
        PPTXDocument doc = PresentationScaffolder.scaffoldDocument("excudo");
        orchestrator = new PPTXOrchestratorImpl();
        orchestrator.initialize(doc);
        orchestrator.createSlide(1, "Placement", "slideLayout7");
        a = rect(0);
        b = rect(1_000_000);
        c = rect(2_000_000);
        ExecutionResult<Integer> r = orchestrator.addShape(1, SlideShape.ShapeType.RECTANGLE,
            new ShapeGeometry(0, 3_000_000, 4_000_000, 2_000_000),
            "Heading\nFirst\nSecond", "Bullets", ShapeStyle.defaultStyle());
        assertTrue(r.isSuccess());
        text = r.getData().orElseThrow();

        // Three clicks: fade a, fade b, fade c
        for (int spid : new int[] {a, b, c}) add(fade(spid).animationGroup("on-click").build());
        assertEquals(List.of("a", "b", "c"), clicks());
    }

    @Test
    public void withAnimationJoinsThatClickRightAfterIt() {
        add(fade(c).withPrevious().effectParam(AnimationBinding.PARAM_WITH_ANIMATION_ID, idOf(b)).build());

        assertEquals("still three clicks", List.of("a", "b c", "c"), clicks());
        Element joined = presetCTns().get(2); // document order: a, b, joined c, c
        assertEquals("withEffect", joined.getAttribute("nodeType"));
    }

    @Test
    public void afterAnimationOpensANewClickAfterThatClick() {
        add(fade(c).animationGroup("on-click").effectParam(AnimationBinding.PARAM_AFTER_ANIMATION_ID, idOf(a)).build());
        assertEquals(List.of("a", "c", "b", "c"), clicks());
    }

    @Test
    public void beforeAnimationOpensANewClickBeforeThatClick() {
        add(fade(c).animationGroup("on-click").effectParam(AnimationBinding.PARAM_BEFORE_ANIMATION_ID, idOf(a)).build());
        assertEquals(List.of("c", "a", "b", "c"), clicks());
    }

    @Test
    public void unknownAnimationIdFails() {
        ExecutionResult<String> r = orchestrator.addAnimation(1,
            fade(c).withPrevious().effectParam(AnimationBinding.PARAM_WITH_ANIMATION_ID, "99999").build());
        assertFalse(r.isSuccess());
        assertTrue(r.getMessage(), r.getMessage().contains("No animation with id 99999"));
    }

    @Test
    public void bulletRangePlacedMidSequenceStaysInThatClick() {
        // The slide-4 fix: heading + first bullet fade in together with click 2
        AddAnimationCommand cmd = AddAnimationCommand.fromBinding(1,
            fade(text).withPrevious().paragraphRange(0, 1)
                .effectParam(AnimationBinding.PARAM_WITH_ANIMATION_ID, idOf(b)).build(),
            orchestrator, null);
        cmd.execute();

        assertEquals(List.of("a", "b T0 T1", "c"), clicks());
        cmd.undo();
        assertEquals(List.of("a", "b", "c"), clicks());
    }

    @Test
    public void removingAClickLeadPromotesTheNextEffect() {
        add(fade(c).withPrevious().effectParam(AnimationBinding.PARAM_WITH_ANIMATION_ID, idOf(b)).build());
        int lead = Integer.parseInt(idOf(b));
        assertTrue(orchestrator.removeAnimation(1, lead).isSuccess());

        assertEquals(List.of("a", "c", "c"), clicks());
        Element promoted = presetCTns().get(1);
        assertEquals("the remaining effect now leads its click", "clickEffect", promoted.getAttribute("nodeType"));
    }

    // ========== Helpers ==========

    private int rect(long x) {
        ExecutionResult<Integer> r = orchestrator.addShape(1, SlideShape.ShapeType.RECTANGLE,
            new ShapeGeometry(x, 0, 900_000, 500_000), "", "Box", ShapeStyle.defaultStyle());
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

    private org.w3c.dom.Document dom() {
        return orchestrator.getContext().get().getDocument().getSlideDocument(1);
    }

    private List<Element> presetCTns() {
        List<Element> out = new ArrayList<>();
        NodeList all = dom().getElementsByTagName("p:cTn");
        for (int i = 0; i < all.getLength(); i++) {
            Element e = (Element) all.item(i);
            if (e.hasAttribute("presetClass")) out.add(e);
        }
        return out;
    }

    /** Id of the first effect targeting the given box (a/b/c). */
    private String idOf(int spid) {
        for (Element e : presetCTns()) {
            Element tgt = (Element) e.getElementsByTagName("p:spTgt").item(0);
            if (tgt.getAttribute("spid").equals(String.valueOf(spid))) return e.getAttribute("id");
        }
        fail("no animation on " + spid);
        return null;
    }

    /** Each click as its effects in order: a / b / c for the boxes, T<n> for text paragraph n. */
    private List<String> clicks() {
        List<String> out = new ArrayList<>();
        NodeList ctns = dom().getElementsByTagName("p:cTn");
        Element mainSeq = null;
        for (int i = 0; i < ctns.getLength(); i++) {
            Element e = (Element) ctns.item(i);
            if ("mainSeq".equals(e.getAttribute("nodeType"))) mainSeq = e;
        }
        assertNotNull(mainSeq);
        Element list = (Element) mainSeq.getElementsByTagName("p:childTnLst").item(0);
        for (Node n = list.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (!(n instanceof Element click)) continue;
            List<String> effects = new ArrayList<>();
            NodeList inner = click.getElementsByTagName("p:cTn");
            for (int i = 0; i < inner.getLength(); i++) {
                Element e = (Element) inner.item(i);
                if (!e.hasAttribute("presetClass")) continue;
                Element tgt = (Element) e.getElementsByTagName("p:spTgt").item(0);
                int spid = Integer.parseInt(tgt.getAttribute("spid"));
                NodeList pRg = tgt.getElementsByTagName("p:pRg");
                if (spid == text) effects.add("T" + ((Element) pRg.item(0)).getAttribute("st"));
                else effects.add(spid == a ? "a" : spid == b ? "b" : "c");
            }
            out.add(String.join(" ", effects));
        }
        return out;
    }
}
