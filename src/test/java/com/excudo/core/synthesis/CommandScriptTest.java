package com.excudo.core.synthesis;

import com.excudo.core.model.ShapeGeometry;
import com.excudo.core.model.SlideShape;
import com.excudo.core.synthesis.spec.CommandSpec;
import org.junit.Test;

import java.util.List;
import java.util.Set;

import static org.junit.Assert.*;

/**
 * State assertions for the DAG container: topological sort respects
 * edges, sub-scripts prune dependencies correctly, cycle detection
 * fires at build time (not execute time), and equality is
 * order-independent.
 */
public class CommandScriptTest {

    // ========== Topological order ==========

    @Test
    public void singleNode_orderIsSelf() {
        CommandSpec a = add(1);
        CommandScript s = CommandScript.builder().add(a).build();
        assertEquals(List.of(a), s.topologicalOrder());
    }

    @Test
    public void independentNodes_preserveInsertionOrder() {
        CommandSpec a = add(1);
        CommandSpec b = add(2);
        CommandSpec c = add(3);
        CommandScript s = CommandScript.builder().add(a).add(b).add(c).build();
        assertEquals("Independent specs must respect insertion order",
            List.of(a, b, c), s.topologicalOrder());
    }

    @Test
    public void dependsOn_forcesCorrectOrdering() {
        CommandSpec add1 = add(1);
        CommandSpec setText = new CommandSpec.SetTextSpec(1, 42,
            com.excudo.core.model.TextBody.fromPlainText("hi", true));
        CommandScript s = CommandScript.builder()
            .add(setText)         // Inserted first so default order would run it first
            .add(add1)
            .dependsOn(setText, add1) // But setText requires add1 to finish first
            .build();
        List<CommandSpec> order = s.topologicalOrder();
        assertEquals("add1 must come before setText regardless of insertion order",
            List.of(add1, setText), order);
    }

    @Test
    public void diamondDependency_hasSingleBottomNode() {
        // A depends on nothing; B and C both depend on A; D depends on both.
        // Valid topo order: A, (B|C), (C|B), D
        CommandSpec a = add(1);
        CommandSpec b = new CommandSpec.MoveSpec(1, 5, 100, 100);
        CommandSpec c = new CommandSpec.MoveSpec(1, 6, 200, 200);
        CommandSpec d = new CommandSpec.ReorderSpec(1, 5, CommandSpec.ReorderSpec.Direction.FRONT);
        CommandScript s = CommandScript.builder()
            .add(a).add(b).add(c).add(d)
            .dependsOn(b, a)
            .dependsOn(c, a)
            .dependsOn(d, b)
            .dependsOn(d, c)
            .build();
        List<CommandSpec> order = s.topologicalOrder();
        assertEquals(4, order.size());
        assertEquals("A must be first", a, order.get(0));
        assertEquals("D must be last", d, order.get(3));
        assertTrue("B and C are the middle two",
            Set.of(b, c).equals(Set.of(order.get(1), order.get(2))));
    }

    @Test
    public void cycle_detectedAtBuildTime() {
        CommandSpec a = add(1);
        CommandSpec b = add(2);
        try {
            CommandScript.builder().add(a).add(b)
                .dependsOn(a, b)
                .dependsOn(b, a)
                .build();
            fail("Cycle must be rejected at build time");
        } catch (IllegalStateException expected) {
            assertTrue("Error message should mention cycle: " + expected.getMessage(),
                expected.getMessage().toLowerCase().contains("cycle"));
        }
    }

    // ========== Sub-script ==========

    @Test
    public void subScriptBySlide_prunesUnrelatedEdges() {
        CommandSpec s1a = add(1);
        CommandSpec s1b = new CommandSpec.MoveSpec(1, 5, 100, 100);
        CommandSpec s2a = add(2);
        CommandScript full = CommandScript.builder()
            .add(s1a).add(s1b).add(s2a)
            .dependsOn(s1b, s1a)  // s1b depends on s1a (same slide, kept)
            .build();
        CommandScript slide1Only = full.subScript(1);
        assertEquals("Only slide-1 nodes remain",
            Set.of(s1a, s1b), slide1Only.nodes());
        assertEquals("Edge between surviving slide-1 nodes must be preserved",
            Set.of(s1a), slide1Only.dependenciesOf(s1b));
    }

    @Test
    public void subScriptBySlide_dropsEdgesToPrunedNodes() {
        CommandSpec s1a = add(1);
        CommandSpec s2a = add(2);
        CommandScript full = CommandScript.builder()
            .add(s1a).add(s2a)
            .dependsOn(s2a, s1a)  // cross-slide edge
            .build();
        CommandScript slide2Only = full.subScript(2);
        assertEquals("Only slide-2 nodes remain", Set.of(s2a), slide2Only.nodes());
        assertTrue("Edge to pruned slide-1 node must be dropped",
            slide2Only.dependenciesOf(s2a).isEmpty());
    }

    // ========== Diff ==========

    @Test
    public void minus_returnsNodesOnlyInThis() {
        CommandSpec a = add(1);
        CommandSpec b = add(2);
        CommandSpec c = add(3);
        CommandScript left  = CommandScript.builder().add(a).add(b).add(c).build();
        CommandScript right = CommandScript.builder().add(a).add(c).build();
        assertEquals("Only b is in left but not right", Set.of(b), left.minus(right));
    }

    // ========== Equality ==========

    @Test
    public void equality_isOrderIndependent() {
        CommandSpec a = add(1);
        CommandSpec b = add(2);
        CommandScript s1 = CommandScript.builder().add(a).add(b).build();
        CommandScript s2 = CommandScript.builder().add(b).add(a).build();
        assertEquals("Scripts with same nodes must be equal regardless of insertion order",
            s1, s2);
        assertEquals(s1.hashCode(), s2.hashCode());
    }

    @Test
    public void equality_distinguishesByDependencies() {
        CommandSpec a = add(1);
        CommandSpec b = add(2);
        CommandScript plain = CommandScript.builder().add(a).add(b).build();
        CommandScript linked = CommandScript.builder().add(a).add(b).dependsOn(b, a).build();
        assertNotEquals("Two scripts with same nodes but different edges must NOT be equal",
            plain, linked);
    }

    // ========== Terminal nodes ==========

    @Test
    public void terminalNodes_excludesNodesWithDependents() {
        CommandSpec a = add(1);
        CommandSpec b = add(2);
        CommandSpec c = add(3);
        CommandScript s = CommandScript.builder()
            .add(a).add(b).add(c)
            .dependsOn(b, a)   // a has dependent b
            .build();
        assertEquals("Only b and c are terminal (a has dependents)",
            Set.of(b, c), s.terminalNodes());
    }

    // ========== Slide numbers ==========

    @Test
    public void slideNumbers_collectsUniqueSlides() {
        CommandSpec s1 = add(1);
        CommandSpec s2 = add(2);
        CommandSpec s2b = new CommandSpec.MoveSpec(2, 5, 0, 0);
        CommandScript s = CommandScript.builder().add(s1).add(s2).add(s2b).build();
        assertEquals(Set.of(1, 2), s.slideNumbers());
    }

    // ========== Empty / ofFlat ==========

    @Test
    public void empty_script() {
        CommandScript s = CommandScript.empty();
        assertTrue(s.isEmpty());
        assertEquals(0, s.size());
        assertTrue(s.topologicalOrder().isEmpty());
    }

    @Test
    public void ofFlat_preservesOrderAndHasNoEdges() {
        CommandSpec a = add(1);
        CommandSpec b = add(2);
        CommandScript s = CommandScript.ofFlat(List.of(a, b));
        assertEquals(List.of(a, b), s.topologicalOrder());
        assertTrue(s.dependenciesOf(a).isEmpty());
        assertTrue(s.dependenciesOf(b).isEmpty());
    }

    // ========== Helpers ==========

    private static CommandSpec add(int slide) {
        return new CommandSpec.AddShapeSpec(slide, SlideShape.ShapeType.RECTANGLE,
            new ShapeGeometry(0, 0, 1000, 500), "", "R" + slide, null, null, false);
    }
}
