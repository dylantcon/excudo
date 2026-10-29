package com.excudo.xml.parsers;

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

import java.util.List;

import static org.junit.Assert.*;

/**
 * Write an animation, parse the slide back, and check the binding carries
 * what was written: the paragraph range of a per-bullet animation and the
 * transparency level of a transparency emphasis.
 */
public class AnimationReadBackTest {

    private PPTXOrchestratorImpl orchestrator;
    private int spid;

    @Before
    public void setUp() throws Exception {
        PPTXDocument doc = PresentationScaffolder.scaffoldDocument("excudo");
        orchestrator = new PPTXOrchestratorImpl();
        orchestrator.initialize(doc);
        orchestrator.createSlide(1, "Read Back", "slideLayout7");
        ExecutionResult<Integer> r = orchestrator.addShape(1, SlideShape.ShapeType.RECTANGLE,
            new ShapeGeometry(0, 0, 4_000_000, 2_000_000),
            "First\nSecond\nThird", "Bullets", ShapeStyle.defaultStyle());
        assertTrue(r.isSuccess());
        spid = r.getData().orElseThrow();
    }

    @Test
    public void paragraphRangeIsReadBack() {
        add(AnimationBinding.builder().target(spid).type(AnimationType.FADE).entrance()
            .duration("500").delay("0").animationGroup("on-click").paragraph(1).build());

        AnimationBinding parsed = only();
        assertTrue("Per-bullet animation must parse as paragraph-level",
            parsed.isParagraphLevelAnimation());
        assertEquals(Integer.valueOf(1), parsed.getParagraphStart());
        assertEquals(Integer.valueOf(1), parsed.getParagraphEnd());
    }

    @Test
    public void ooxmlParagraphsCountEmptyParagraphsLikePRg() {
        ExecutionResult<Integer> r = orchestrator.addShape(1, SlideShape.ShapeType.RECTANGLE,
            new ShapeGeometry(0, 2_500_000, 4_000_000, 2_000_000),
            "First\n\nThird", "Gap", ShapeStyle.defaultStyle());
        assertTrue(r.isSuccess());
        int gapSpid = r.getData().orElseThrow();

        var doc = orchestrator.getContext().get().getDocument();
        SlideShape shape = doc.getParsedSlideData(1, (dom, n) -> new SlideXMLParser().parseSlide(dom, n))
            .getShapeRegistry().getShape(gapSpid);
        assertEquals(List.of("First", "", "Third"), shape.getOoxmlParagraphs());
    }

    @Test
    public void shapeLevelAnimationHasNoParagraphRange() {
        add(AnimationBinding.builder().target(spid).type(AnimationType.FADE).entrance()
            .duration("500").delay("0").animationGroup("on-click").build());

        assertFalse(only().isParagraphLevelAnimation());
    }

    @Test
    public void transparencyLevelIsReadBack() {
        add(AnimationBinding.builder().target(spid).type(AnimationType.TRANSPARENCY).emphasis()
            .duration("500").delay("0").animationGroup("on-click").transparency(75).build());

        assertEquals("75", only().getEffectParam(AnimationBinding.PARAM_TRANSPARENCY));
    }

    @Test
    public void multiParagraphRangeIsWrittenOnePerParagraph() {
        // PowerPoint never writes pRg st != end and doesn't play one correctly
        var cmd = com.excudo.core.commands.mutating.slide.AddAnimationCommand.fromBinding(1,
            AnimationBinding.builder().target(spid).type(AnimationType.TRANSPARENCY).emphasis()
                .duration("500").delay("0").animationGroup("on-click").paragraphRange(0, 2)
                .transparency(50).build(),
            orchestrator, null);
        cmd.execute();

        var dom = orchestrator.getContext().get().getDocument().getSlideDocument(1);
        org.w3c.dom.NodeList ranges = dom.getElementsByTagName("p:pRg");
        assertEquals("3 effects x (p:set + p:animEffect)", 6, ranges.getLength());
        for (int i = 0; i < ranges.getLength(); i++) {
            var r = (org.w3c.dom.Element) ranges.item(i);
            assertEquals("No multi-paragraph range may be written", r.getAttribute("st"), r.getAttribute("end"));
        }

        List<AnimationBinding> bindings = all();
        assertEquals(3, bindings.size());
        for (int i = 0; i < 3; i++) {
            assertEquals(Integer.valueOf(i), bindings.get(i).getParagraphStart());
            assertEquals(Integer.valueOf(i), bindings.get(i).getParagraphEnd());
            assertEquals("50", bindings.get(i).getEffectParam(AnimationBinding.PARAM_TRANSPARENCY));
        }
        assertEquals("first keeps the trigger", "on-click", bindings.get(0).getAnimationGroup());
        assertEquals("with-previous", bindings.get(1).getAnimationGroup());
        assertEquals("with-previous", bindings.get(2).getAnimationGroup());

        cmd.undo();
        assertEquals("undo removes every expanded effect", 0, all().size());
    }

    @Test
    public void characterRangeIsReadBackNotShapeLevel() {
        // PowerPoint writes p:txEl/p:charRg when an effect is applied to selected text
        add(AnimationBinding.builder().target(spid).type(AnimationType.FADE).entrance()
            .duration("500").delay("0").animationGroup("on-click").charRange(0, 5).build());

        AnimationBinding parsed = only();
        assertTrue(parsed.isCharacterRangeAnimation());
        assertFalse("a char range is not a whole-shape target", parsed.isShapeLevelAnimation());
        assertFalse(parsed.isParagraphLevelAnimation());
        assertEquals(Integer.valueOf(0), parsed.getCharStart());
        assertEquals(Integer.valueOf(5), parsed.getCharEnd());

        // Native form: like paragraph targets, no grpId and no bldP
        var dom = orchestrator.getContext().get().getDocument().getSlideDocument(1);
        assertEquals(0, dom.getElementsByTagName("p:bldP").getLength());
        org.w3c.dom.NodeList ctns = dom.getElementsByTagName("p:cTn");
        for (int i = 0; i < ctns.getLength(); i++) {
            var c = (org.w3c.dom.Element) ctns.item(i);
            if (c.hasAttribute("presetClass")) assertFalse(c.hasAttribute("grpId"));
        }
    }

    @Test
    public void characterRangeSurvivesReAdd() {
        // The path clear-slide-content undo and remove-animation undo use
        var cmd = com.excudo.core.commands.mutating.slide.AddAnimationCommand.fromBinding(1,
            AnimationBinding.builder().target(spid).type(AnimationType.FADE).entrance()
                .duration("500").delay("0").animationGroup("on-click").charRange(6, 12).build(),
            orchestrator, null);
        cmd.execute();

        AnimationBinding parsed = only();
        assertEquals(Integer.valueOf(6), parsed.getCharStart());
        assertEquals(Integer.valueOf(12), parsed.getCharEnd());
        assertFalse("the threaded range must not linger as an effect param",
            parsed.getEffectParams().containsKey(AnimationBinding.PARAM_CHAR_START));
    }

    private List<AnimationBinding> all() {
        var doc = orchestrator.getContext().get().getDocument();
        return doc.getParsedSlideData(1, (dom, n) -> new SlideXMLParser().parseSlide(dom, n)).getAnimationBindings();
    }

    private void add(AnimationBinding binding) {
        assertTrue(orchestrator.addAnimation(1, binding).isSuccess());
    }

    private AnimationBinding only() {
        var doc = orchestrator.getContext().get().getDocument();
        List<AnimationBinding> bindings = doc.getParsedSlideData(1,
            (dom, n) -> new SlideXMLParser().parseSlide(dom, n)).getAnimationBindings();
        assertEquals(1, bindings.size());
        return bindings.get(0);
    }
}
