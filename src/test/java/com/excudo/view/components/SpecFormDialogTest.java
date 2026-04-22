package com.excudo.view.components;

import com.excudo.core.model.ShapeGeometry;
import com.excudo.core.model.SlideShape;
import com.excudo.core.model.TransitionType;
import com.excudo.core.synthesis.spec.CommandSpec;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Guards the {@link SpecFormDialog#hasTypedForm(CommandSpec)} predicate
 * against drift from the dispatch table in {@link SpecFormDialog#editSpec}.
 * If a typed form is ever added but forgotten in {@code hasTypedForm},
 * the controller silently falls back to JSON — yellow flag against the
 * project's "don't fail silently" rule.
 */
public class SpecFormDialogTest {

    @Test
    void hasTypedFormMatchesDispatchForAllSpecs() {
        // One representative instance per spec type; order follows the
        // sealed-interface permits list.
        ShapeGeometry geom = new ShapeGeometry(0L, 0L, 100L, 100L);
        List<CommandSpec> all = List.of(
            new CommandSpec.AddShapeSpec(1, SlideShape.ShapeType.RECTANGLE, geom, "", null, null, null, false),
            new CommandSpec.RemoveShapeSpec(1, 5),
            new CommandSpec.MoveSpec(1, 5, 100L, 100L),
            new CommandSpec.ResizeSpec(1, 5, 200L, 200L),
            new CommandSpec.RotateSpec(1, 5, 45.0),
            new CommandSpec.RenameShapeSpec(1, 5, "Renamed"),
            new CommandSpec.SetTextBoxFlagSpec(1, 5, true),
            new CommandSpec.ReorderSpec(1, 5, CommandSpec.ReorderSpec.Direction.FRONT),
            new CommandSpec.RemoveAnimationSpec(1, 10),
            new CommandSpec.SetAnimationTimingSpec(1, 10, "500", "0"),
            new CommandSpec.SetTransitionSpec(1, TransitionType.FADE, "med", null),
            new CommandSpec.ClearTransitionSpec(1),
            new CommandSpec.UngroupSpec(1, 5),
            new CommandSpec.AddToGroupSpec(1, 10, 5),
            new CommandSpec.DetachFromGroupSpec(1, 5)
        );

        for (CommandSpec s : all) {
            // Sanity: every spec is marked typed-form-capable. If you add
            // a new spec and deliberately DON'T ship a typed form, add it
            // to the explicit-false list below so the test documents
            // which specs intentionally fall through to JSON.
            assertTrue(SpecFormDialog.hasTypedForm(s),
                "Missing typed form for " + s.getClass().getSimpleName()
                    + " — either add to SpecFormDialog.editSpec or to the explicit-fallback list");
        }
    }

    @Test
    void jsonFallbackSpecsReturnFalse() {
        // These spec types deliberately fall through to the JSON editor
        // in the controller — rich sub-models (TextBody, AnimationBinding,
        // ShapeStyle, TextRun, List<Integer>) don't yet have bespoke forms.
        ShapeGeometry geom = new ShapeGeometry(0L, 0L, 100L, 100L);
        assertFalse(SpecFormDialog.hasTypedForm(
            new CommandSpec.SetTextSpec(1, 5, com.excudo.core.model.TextBody.fromPlainText("hi", false))));
        assertFalse(SpecFormDialog.hasTypedForm(
            new CommandSpec.CreateGroupSpec(1, List.of(5, 6), "grp")));
    }
}
