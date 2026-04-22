package com.excudo.core.synthesis;

import com.excudo.core.model.PPTXDocument;
import com.excudo.core.model.ShapeGeometry;
import com.excudo.core.model.ShapeStyle;
import com.excudo.core.model.SlideShape;
import com.excudo.core.orchestration.PPTXOrchestratorImpl;
import com.excudo.core.orchestration.PresentationScaffolder;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

/**
 * Tests the debounce + cache behavior of {@link ReactiveSynthesizer}.
 * The synthesizer itself is pure and already covered elsewhere; these
 * tests pin the wrapper's behavior: subscribers fire, cache hits
 * match revisions, and rapid mutations collapse into fewer synths.
 */
public class ReactiveSynthesizerTest {

    private PPTXOrchestratorImpl orchestrator;
    private ReactiveSynthesizer reactive;

    @Before
    public void setUp() throws Exception {
        PPTXDocument doc = PresentationScaffolder.scaffoldDocument("excudo");
        orchestrator = new PPTXOrchestratorImpl();
        orchestrator.initialize(doc);
        orchestrator.createSlide(1, "Reactive Test", "slideLayout7");
        reactive = new ReactiveSynthesizer(orchestrator);
    }

    @Test
    public void synthesizeNow_producesResultAndCaches() {
        var r1 = reactive.synthesizeNow(1);
        assertNotNull("Synth must produce a non-null result", r1);
        // Cache is hit on second call -- same reference returned when
        // revision hasn't advanced.
        var r2 = reactive.synthesizeNow(1);
        assertSame("Cache must return the same instance on no-revision-bump",
            r1, r2);
    }

    @Test
    public void cached_nullBeforeFirstSynth() {
        assertNull("cached() before synthesizeNow must be null",
            reactive.cached(1));
    }

    @Test
    public void cached_returnsLastResultAfterSynth() {
        var fresh = reactive.synthesizeNow(1);
        assertSame("cached() after synthesizeNow returns the same instance",
            fresh, reactive.cached(1));
    }

    @Test
    public void mutationInvalidatesCacheNextSynth() {
        var before = reactive.synthesizeNow(1);
        assertEquals("Before any user shape: 0 specs", 0, before.script().size());

        orchestrator.addShape(1, SlideShape.ShapeType.RECTANGLE,
            new ShapeGeometry(1_000_000, 1_000_000, 2_000_000, 1_000_000),
            "", "R", ShapeStyle.defaultStyle());
        // Mutation bumps the slide revision. synthesizeNow must NOT
        // return the cached "0 specs" result.
        var after = reactive.synthesizeNow(1);
        assertNotSame("Cache must invalidate on revision bump", before, after);
        assertTrue("Post-mutation synth must include the new shape",
            after.script().size() >= 1);
    }

    @Test
    public void subscriberFiresAfterDebouncedMutation() throws Exception {
        AtomicInteger fires = new AtomicInteger(0);
        reactive.subscribe(r -> fires.incrementAndGet());

        orchestrator.addShape(1, SlideShape.ShapeType.RECTANGLE,
            new ShapeGeometry(1_000_000, 1_000_000, 2_000_000, 1_000_000),
            "", "R", ShapeStyle.defaultStyle());

        // 150ms debounce window + generous safety margin for CI timing.
        Thread.sleep(400);
        assertTrue("Subscriber must fire at least once after debounce",
            fires.get() >= 1);
    }

    @Test
    public void rapidMutationsCollapseToOneSynthPerSlide() throws Exception {
        AtomicInteger fires = new AtomicInteger(0);
        reactive.subscribe(r -> fires.incrementAndGet());

        // Three rapid-fire mutations; debounce should collapse them
        // into ONE synth firing after the quiet window.
        for (int i = 0; i < 3; i++) {
            orchestrator.addShape(1, SlideShape.ShapeType.RECTANGLE,
                new ShapeGeometry(i * 1_000_000L, 0, 500_000, 500_000),
                "", "R", ShapeStyle.defaultStyle());
        }
        Thread.sleep(400);
        assertEquals("Three quick mutations must collapse into one fire", 1, fires.get());
    }
}
