package com.excudo.core.synthesis.spec;

import com.excudo.core.model.AnimationBinding;
import com.excudo.core.model.AnimationType;
import com.excudo.core.model.ShapeFill;
import com.excudo.core.model.ShapeGeometry;
import com.excudo.core.model.ShapeStyle;
import com.excudo.core.model.SlideShape;
import com.excudo.core.model.TextBody;
import com.excudo.core.model.TransitionType;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

/**
 * Value-equality tests for the v1 {@link CommandSpec} records. Records
 * auto-generate {@code equals}/{@code hashCode} from their components,
 * but the test enforces that invariant by construction: two specs
 * built from equal field inputs must be {@code .equals}, and two built
 * from differing field inputs must NOT be. If ever a record is
 * accidentally turned into a class and loses structural equality,
 * these tests fail fast.
 *
 * <p>Invariants are also validated: specs with negative slide numbers
 * or non-positive SPIDs must throw, so a malformed DAG never
 * propagates silently into the executor.
 */
public class CommandSpecEqualityTest {

    // ========== AddShapeSpec ==========

    @Test
    public void addShapeSpec_equalByFields() {
        CommandSpec.AddShapeSpec a = new CommandSpec.AddShapeSpec(
            1, SlideShape.ShapeType.RECTANGLE,
            new ShapeGeometry(0, 0, 1000, 500),
            "hello", "R1", null, null, false);
        CommandSpec.AddShapeSpec b = new CommandSpec.AddShapeSpec(
            1, SlideShape.ShapeType.RECTANGLE,
            new ShapeGeometry(0, 0, 1000, 500),
            "hello", "R1", null, null, false);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void addShapeSpec_unequalWhenNameDiffers() {
        CommandSpec.AddShapeSpec a = new CommandSpec.AddShapeSpec(
            1, SlideShape.ShapeType.RECTANGLE,
            new ShapeGeometry(0, 0, 1000, 500),
            "hello", "R1", null, null, false);
        CommandSpec.AddShapeSpec b = new CommandSpec.AddShapeSpec(
            1, SlideShape.ShapeType.RECTANGLE,
            new ShapeGeometry(0, 0, 1000, 500),
            "hello", "R2", null, null, false);
        assertNotEquals(a, b);
    }

    @Test(expected = IllegalArgumentException.class)
    public void addShapeSpec_rejectsZeroSlideNumber() {
        new CommandSpec.AddShapeSpec(0, SlideShape.ShapeType.RECTANGLE,
            new ShapeGeometry(0, 0, 100, 100), "", "X", null, null, false);
    }

    @Test(expected = NullPointerException.class)
    public void addShapeSpec_rejectsNullGeometry() {
        new CommandSpec.AddShapeSpec(1, SlideShape.ShapeType.RECTANGLE, null,
            "", "X", null, null, false);
    }

    // ========== Position / size / rotation specs ==========

    @Test
    public void moveSpec_equalByFields() {
        assertEquals(new CommandSpec.MoveSpec(1, 5, 100, 200),
                     new CommandSpec.MoveSpec(1, 5, 100, 200));
    }

    @Test
    public void moveSpec_unequalWhenCoordsDiffer() {
        assertNotEquals(new CommandSpec.MoveSpec(1, 5, 100, 200),
                        new CommandSpec.MoveSpec(1, 5, 101, 200));
    }

    @Test
    public void resizeSpec_equalByFields() {
        assertEquals(new CommandSpec.ResizeSpec(1, 5, 500, 300),
                     new CommandSpec.ResizeSpec(1, 5, 500, 300));
    }

    @Test(expected = IllegalArgumentException.class)
    public void resizeSpec_rejectsNegativeSize() {
        new CommandSpec.ResizeSpec(1, 5, -1, 100);
    }

    @Test
    public void rotateSpec_equalByFields() {
        assertEquals(new CommandSpec.RotateSpec(1, 5, 45.0),
                     new CommandSpec.RotateSpec(1, 5, 45.0));
    }

    @Test
    public void rotateSpec_unequalWhenDegreesDiffer() {
        assertNotEquals(new CommandSpec.RotateSpec(1, 5, 45.0),
                        new CommandSpec.RotateSpec(1, 5, 46.0));
    }

    // ========== Text / style / reorder ==========

    @Test
    public void setTextSpec_equalByFields() {
        TextBody b = TextBody.fromPlainText("hello", true);
        assertEquals(new CommandSpec.SetTextSpec(1, 5, b),
                     new CommandSpec.SetTextSpec(1, 5, b));
    }

    @Test
    public void setShapeStyleSpec_equalByFields() {
        ShapeStyle s = ShapeStyle.withFill(ShapeFill.solid("FF0000"));
        assertEquals(new CommandSpec.SetShapeStyleSpec(1, 5, s),
                     new CommandSpec.SetShapeStyleSpec(1, 5, s));
    }

    @Test
    public void reorderSpec_equalByFields() {
        assertEquals(new CommandSpec.ReorderSpec(1, 5, CommandSpec.ReorderSpec.Direction.FRONT),
                     new CommandSpec.ReorderSpec(1, 5, CommandSpec.ReorderSpec.Direction.FRONT));
    }

    @Test
    public void reorderSpec_unequalAcrossDirections() {
        assertNotEquals(new CommandSpec.ReorderSpec(1, 5, CommandSpec.ReorderSpec.Direction.FRONT),
                        new CommandSpec.ReorderSpec(1, 5, CommandSpec.ReorderSpec.Direction.BACK));
    }

    // ========== Animations ==========

    @Test
    public void addAnimationSpec_equalByBinding() {
        AnimationBinding ab = AnimationBinding.builder()
            .target(3).type(AnimationType.FADE).entrance().clickTrigger(1).build();
        assertEquals(new CommandSpec.AddAnimationSpec(1, ab),
                     new CommandSpec.AddAnimationSpec(1, ab));
    }

    @Test
    public void removeAnimationSpec_equalByFields() {
        assertEquals(new CommandSpec.RemoveAnimationSpec(1, 42),
                     new CommandSpec.RemoveAnimationSpec(1, 42));
    }

    @Test(expected = IllegalArgumentException.class)
    public void removeAnimationSpec_rejectsZeroTimingId() {
        new CommandSpec.RemoveAnimationSpec(1, 0);
    }

    // ========== Transitions ==========

    @Test
    public void setTransitionSpec_equalByFields() {
        assertEquals(new CommandSpec.SetTransitionSpec(1, TransitionType.FADE, "med", null),
                     new CommandSpec.SetTransitionSpec(1, TransitionType.FADE, "med", null));
    }

    @Test
    public void setTransitionSpec_canonicalizesNullSpeedToMed() {
        CommandSpec.SetTransitionSpec s = new CommandSpec.SetTransitionSpec(
            1, TransitionType.FADE, null, null);
        assertEquals("Null speed must canonicalize to 'med'", "med", s.speed());
    }

    @Test
    public void clearTransitionSpec_equalByField() {
        assertEquals(new CommandSpec.ClearTransitionSpec(1),
                     new CommandSpec.ClearTransitionSpec(1));
    }

    @Test
    public void clearTransitionSpec_unequalAcrossSlides() {
        assertNotEquals(new CommandSpec.ClearTransitionSpec(1),
                        new CommandSpec.ClearTransitionSpec(2));
    }

    // ========== Groups ==========

    @Test
    public void createGroupSpec_equalByFields() {
        assertEquals(new CommandSpec.CreateGroupSpec(1, List.of(5, 6), "G"),
                     new CommandSpec.CreateGroupSpec(1, List.of(5, 6), "G"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void createGroupSpec_rejectsEmptyChildren() {
        new CommandSpec.CreateGroupSpec(1, List.of(), "G");
    }

    @Test
    public void ungroupSpec_equalByFields() {
        assertEquals(new CommandSpec.UngroupSpec(1, 10),
                     new CommandSpec.UngroupSpec(1, 10));
    }

    // ========== Cross-type ==========

    @Test
    public void differentSpecTypesAreNotEqual() {
        // Sanity: the sealed hierarchy doesn't accidentally make two
        // different spec types compare equal via shared-field coincidence.
        CommandSpec a = new CommandSpec.RemoveShapeSpec(1, 5);
        CommandSpec b = new CommandSpec.UngroupSpec(1, 5);
        assertNotEquals(a, b);
    }
}
