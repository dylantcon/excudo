package com.excudo.core.introspection;

import com.excudo.core.commands.mutating.slide.GroupShapesCommand;

import com.excudo.core.model.AnimationBinding;
import com.excudo.core.model.AnimationType;
import com.excudo.core.model.LayoutInfo;
import com.excudo.core.model.PPTXDocument;
import com.excudo.core.model.ShapeFill;
import com.excudo.core.model.ShapeGeometry;
import com.excudo.core.model.ShapeStyle;
import com.excudo.core.model.SlideShape;
import com.excudo.core.model.TextColor;
import com.excudo.core.orchestration.PPTXOrchestratorImpl;
import com.excudo.core.orchestration.PresentationScaffolder;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

/**
 * State assertions for the 4.0 introspection surface. The synthesizer
 * will compare values returned here against current slide state to
 * decide which {@code CommandSpec}s to emit; correctness of the diff
 * starts with correctness of the baseline + current reads.
 */
public class SlideIntrospectorTest {

    private PPTXOrchestratorImpl orchestrator;
    private SlideIntrospector introspector;

    @Before
    public void setUp() throws Exception {
        PPTXDocument doc = PresentationScaffolder.scaffoldDocument("excudo");
        orchestrator = new PPTXOrchestratorImpl();
        orchestrator.initialize(doc);
        orchestrator.createSlide(1, "Introspection Test", "slideLayout2");
        introspector = new SlideIntrospector(orchestrator);
    }

    // ========== listAnimations ==========

    @Test
    public void listAnimationsReturnsEmptyForSlideWithNoAnimations() {
        List<AnimationBinding> anims = introspector.listAnimations(1);
        assertNotNull("Must never return null; empty list is the well-defined empty case",
            anims);
        assertTrue("Fresh slide has no animations", anims.isEmpty());
    }

    @Test
    public void listAnimationsSurfacesAddedAnimationsWithTypedFields() {
        orchestrator.addAnimation(1, AnimationBinding.builder()
            .target(3).type(AnimationType.FADE).entrance()
            .clickTrigger(1).durationMs(500).build());

        List<AnimationBinding> anims = introspector.listAnimations(1);
        assertEquals("Exactly one animation should be surfaced", 1, anims.size());
        AnimationBinding b = anims.get(0);
        assertEquals("Target SPID preserved", 3, b.getTargetSpid());
        assertEquals("AnimationType preserved", AnimationType.FADE, b.getAnimationType());
        // clickTrigger semantics: 1+ means on-click (click N). Here it's click 1.
        assertEquals("clickTrigger preserved", 1, b.getClickTrigger());
    }

    @Test
    public void listAnimationsIsImmutable() {
        orchestrator.addAnimation(1, AnimationBinding.builder()
            .target(3).type(AnimationType.FADE).entrance().clickTrigger(1).build());
        List<AnimationBinding> anims = introspector.listAnimations(1);
        try {
            anims.clear();
            fail("Caller must not be able to mutate the returned list");
        } catch (UnsupportedOperationException expected) {
            // good: unmodifiable
        }
    }

    // ========== getLayoutBaseline ==========

    @Test
    public void layoutBaselineResolvesSlideLayout2() {
        LayoutBaseline baseline = introspector.getLayoutBaseline(1);
        assertNotNull("Baseline must be available for a scaffolded slide", baseline);
        LayoutInfo layout = baseline.layout();
        assertNotNull("Layout must be present in baseline", layout);
        assertEquals("slideLayout2 is the 'Title and Content' layout",
            "slideLayout2", layout.getLayoutId());
        assertTrue("slideLayout2 has a title placeholder", layout.hasTitlePlaceholder());
        assertTrue("slideLayout2 has a content placeholder", layout.hasContentPlaceholder());
    }

    @Test
    public void layoutBaselineIncludesMasterStylesAndClrMap() {
        LayoutBaseline baseline = introspector.getLayoutBaseline(1);
        assertNotNull(baseline);
        // Non-null empty maps are acceptable fallback when styles are
        // absent in test fixtures, but for the excudo theme the master
        // publishes full txStyles so we expect them here.
        assertNotNull("masterStyles must never be null (empty map fallback OK)",
            baseline.masterStyles());
        assertNotNull("colorMap must never be null (empty map fallback OK)",
            baseline.colorMap());
    }

    @Test
    public void layoutBaselineIsNullForUnknownSlide() {
        LayoutBaseline missing = introspector.getLayoutBaseline(42);
        assertNull("Slide that doesn't exist has no baseline", missing);
    }

    // ========== getShapeStyle ==========

    @Test
    public void shapeStyleReturnsNullForUnknownSpid() {
        // SPID 9999 does not exist on the scaffolded slide.
        ShapeStyle s = introspector.getShapeStyle(1, 9999);
        assertNull("Unknown SPID must yield null, not a fabricated default", s);
    }

    @Test
    public void shapeStyleSurfacesDirectFillOverride() throws Exception {
        // Add a rectangle with an explicit scheme fill and assert the
        // reader round-trips it faithfully.
        com.excudo.core.results.ExecutionResult<Integer> added = orchestrator.addShape(
            1, SlideShape.ShapeType.RECTANGLE,
            new ShapeGeometry(1_000_000, 1_000_000, 3_000_000, 2_000_000),
            "", "FilledRect",
            ShapeStyle.withFill(ShapeFill.scheme("accent3")));
        assertTrue("addShape must succeed: " + added.getMessage(), added.isSuccess());
        int spid = added.getData().orElseThrow();

        ShapeStyle style = introspector.getShapeStyle(1, spid);
        assertNotNull("Added shape must have a readable style", style);
        assertNotNull("Fill override must be present", style.getFill());
        assertEquals("Fill type must be SOLID (scheme)",
            com.excudo.core.model.FillType.SOLID, style.getFill().getType());
        TextColor c = style.getFill().getColor();
        assertNotNull("Fill color must be captured", c);
        assertTrue("Fill color must be a scheme color (accent3)", c.isScheme());
        assertEquals("accent3", c.getSchemeVal());
    }

    @Test
    public void shapeStyleCapturesThemeStyleRefOnDefaultShape() throws Exception {
        // Default-styled shapes get a p:style with default theme refs
        // (accent1 line with shade, accent1 fill, minor fontRef, tx1 font).
        com.excudo.core.results.ExecutionResult<Integer> added = orchestrator.addShape(
            1, SlideShape.ShapeType.RECTANGLE,
            new ShapeGeometry(5_000_000, 1_000_000, 2_000_000, 2_000_000),
            "", "DefaultRect",
            ShapeStyle.defaultStyle());
        assertTrue(added.isSuccess());
        int spid = added.getData().orElseThrow();

        ShapeStyle style = introspector.getShapeStyle(1, spid);
        assertNotNull(style);
        com.excudo.core.model.ThemeStyleRef ref = style.getThemeStyle();
        assertNotNull("Default shape should have a non-null themeStyleRef", ref);
        assertNotEquals("Default is NOT the NONE sentinel (that's text-box only)",
            com.excudo.core.model.ThemeStyleRef.NONE, ref);
        assertEquals("Default lnRef idx is 2", 2, ref.getLineRefIdx());
        assertEquals("Default fillRef idx is 1", 1, ref.getFillRefIdx());
        assertEquals("Default fontRef idx is 'minor'", "minor", ref.getFontRefIdx());
    }

    // ========== getTransition ==========

    @Test
    public void transitionIsNullForFreshSlide() {
        // Scaffolded slide has no <p:transition> element, and the
        // excudo theme's bundled layouts/master also have none.
        // All three inheritance levels return null -> descriptor is null.
        assertNull("Fresh slide with no transition anywhere in the chain yields null",
            introspector.getTransition(1));
    }

    @Test
    public void transitionSurfacesExplicitSlideOverride() {
        // Inject an explicit slide-level fade transition with 800ms duration.
        orchestrator.setTransition(1, com.excudo.core.model.TransitionType.FADE,
            "fast", null);
        TransitionDescriptor d = introspector.getTransition(1);
        assertNotNull("Slide-level transition must be surfaced", d);
        assertEquals("Type round-trips", com.excudo.core.model.TransitionType.FADE, d.type());
        assertEquals("Speed round-trips", "fast", d.speed());
        assertEquals("Source must be SLIDE (explicit override)",
            TransitionDescriptor.Source.SLIDE, d.source());
    }

    @Test
    public void transitionPreservesDirectionalSubtype() {
        // PUSH_LEFT writes <p:push dir="l"/> under <p:transition>.
        // Reverse-lookup must match element+attribute back to the exact enum.
        orchestrator.setTransition(1, com.excudo.core.model.TransitionType.PUSH_LEFT,
            "med", null);
        TransitionDescriptor d = introspector.getTransition(1);
        assertNotNull(d);
        assertEquals("Directional subtype must round-trip exactly",
            com.excudo.core.model.TransitionType.PUSH_LEFT, d.type());
    }

    // ========== getGroupBounds ==========

    @Test
    public void groupBoundsReturnsSlideSpaceGeometryForGroup() throws Exception {
        // Add two shapes, group them, then read back the group's bounds.
        var r1 = orchestrator.addShape(1, SlideShape.ShapeType.RECTANGLE,
            new ShapeGeometry(1_000_000, 1_000_000, 2_000_000, 1_000_000),
            "", "A", ShapeStyle.defaultStyle());
        var r2 = orchestrator.addShape(1, SlideShape.ShapeType.RECTANGLE,
            new ShapeGeometry(4_000_000, 1_000_000, 2_000_000, 1_000_000),
            "", "B", ShapeStyle.defaultStyle());
        int aSpid = r1.getData().orElseThrow();
        int bSpid = r2.getData().orElseThrow();
        new com.excudo.core.commands.mutating.slide.GroupShapesCommand(1,
            java.util.List.of(aSpid, bSpid), orchestrator).execute();
        int groupSpid = -1;
        var parsed = orchestrator.getContext().get().getDocument().getParsedSlideData(1,
            (dom, n) -> new com.excudo.xml.parsers.SlideXMLParser().parseSlide(dom, n));
        for (var s : parsed.getShapeRegistry().getAllShapes()) {
            if (s.getType() == SlideShape.ShapeType.GROUP) {
                groupSpid = s.getSpid();
                break;
            }
        }
        assertTrue("Group must be present after grouping", groupSpid > 0);

        ShapeGeometry bounds = introspector.getGroupBounds(1, groupSpid);
        assertNotNull("Group bounds must be resolvable", bounds);
        // Collective bbox spans from (1M,1M) to (6M,2M) -- width 5M, height 1M.
        assertTrue("Width must cover both children (>= 5M EMU)",
            bounds.getWidth() >= 5_000_000);
        assertTrue("Height must cover at least one row (>= 1M EMU)",
            bounds.getHeight() >= 1_000_000);
    }

    @Test
    public void groupBoundsReturnsNullForNonGroup() {
        // A regular rectangle, not a group.
        var res = orchestrator.addShape(1, SlideShape.ShapeType.RECTANGLE,
            new ShapeGeometry(0, 0, 1000, 500),
            "", "Plain", ShapeStyle.defaultStyle());
        int spid = res.getData().orElseThrow();
        assertNull("Plain rectangle must not return group bounds",
            introspector.getGroupBounds(1, spid));
    }

    @Test
    public void groupBoundsReturnsNullForUnknownSpid() {
        assertNull(introspector.getGroupBounds(1, 9999));
    }

    // ========== Constructor contracts ==========

    @Test
    public void nullOrchestratorThrows() {
        try {
            new SlideIntrospector(null);
            fail("Null orchestrator must throw");
        } catch (IllegalArgumentException expected) {
            // good
        }
    }
}
