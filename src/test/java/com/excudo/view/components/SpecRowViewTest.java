package com.excudo.view.components;

import com.excudo.core.model.AnimationBinding;
import com.excudo.core.model.AnimationType;
import com.excudo.core.model.BlipRef;
import com.excudo.core.model.ShapeGeometry;
import com.excudo.core.model.ShapeStyle;
import com.excudo.core.model.SlideShape;
import com.excudo.core.model.TextBody;
import com.excudo.core.model.TextRun;
import com.excudo.core.model.TransitionType;
import com.excudo.core.synthesis.spec.CommandSpec;
import com.excudo.core.synthesis.spec.SpecRow;
import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.TextField;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JavaFX-level tests for {@link SpecRowView}'s inline-edit behavior: a valid
 * field edit commits + marks the row dirty + notifies the host; a malformed
 * edit is rejected inline (error shown, value kept, no host notification, no
 * modal); and {@link SpecFormDialog#buildForm} yields an inline form for
 * every one of the 24 spec types (the user's "full inline forms for all"
 * requirement). Runs only under {@code --gui} (needs a JavaFX toolkit).
 */
public class SpecRowViewTest {

    @BeforeAll
    static void bootJavaFX() {
        System.setProperty("java.awt.headless", "true");
        System.setProperty("testfx.headless", "true");
        System.setProperty("prism.order", "sw");
        System.setProperty("glass.platform", "Monocle");
        System.setProperty("monocle.platform", "Headless");
        try {
            new JFXPanel(); // boots the toolkit
        } catch (Exception e) {
            Assumptions.assumeTrue(false, "JavaFX init failed: " + e.getMessage());
        }
    }

    /** Host stub that records callbacks and can feed a canned JSON result. */
    static final class StubHost implements SpecRowView.Host {
        int changed = 0;
        int activated = -1;
        CommandSpec jsonReturn = null;
        @Override public void onChanged() { changed++; }
        @Override public void onActivated(int index) { activated = index; }
        @Override public Optional<CommandSpec> editAsJson(CommandSpec current) {
            return Optional.ofNullable(jsonReturn);
        }
    }

    @Test
    void validNumericEdit_commitsAndMarksDirty() throws Exception {
        runOnFx(() -> {
            SpecRow row = SpecRow.synthesized(new CommandSpec.MoveSpec(1, 5, 100, 200));
            StubHost host = new StubHost();
            SpecRowView view = new SpecRowView(row, 0, host);
            view.buildContentForTest();

            // Form rows are SPID / X / Y -> the X field is index 1.
            textFields(view).get(1).setText("999");
            view.commit();

            assertFalse(view.hasError(), "no error on valid input");
            assertEquals(999L, ((CommandSpec.MoveSpec) row.spec()).newX());
            assertTrue(row.isDirty(), "row deviates from baseline after edit");
            assertTrue(host.changed >= 1, "host notified of the change");
        });
    }

    @Test
    void invalidNumericEdit_keepsValueShowsErrorNoNotify() throws Exception {
        runOnFx(() -> {
            SpecRow row = SpecRow.synthesized(new CommandSpec.MoveSpec(1, 5, 100, 200));
            StubHost host = new StubHost();
            SpecRowView view = new SpecRowView(row, 0, host);
            view.buildContentForTest();

            textFields(view).get(1).setText("not-a-number");
            view.commit(); // must NOT throw, must NOT pop a dialog

            assertTrue(view.hasError(), "error surfaced inline");
            assertTrue(view.getStyleClass().contains("spec-row-error"), "error style class applied");
            assertEquals(100L, ((CommandSpec.MoveSpec) row.spec()).newX(), "value unchanged on bad input");
            assertFalse(row.isDirty(), "rejected edit doesn't dirty the row");
            assertEquals(0, host.changed, "rejected edit doesn't notify the host");
        });
    }

    @Test
    void editBackToOriginal_clearsDirtyAndError() throws Exception {
        runOnFx(() -> {
            SpecRow row = SpecRow.synthesized(new CommandSpec.MoveSpec(1, 5, 100, 200));
            SpecRowView view = new SpecRowView(row, 0, new StubHost());
            view.buildContentForTest();
            TextField x = textFields(view).get(1);

            x.setText("999");
            view.commit();
            assertTrue(row.isDirty());

            x.setText("100"); // back to baseline -> value-equality clears dirty
            view.commit();
            assertFalse(row.isDirty(), "editing back to baseline un-dirties");
            assertFalse(view.hasError());
        });
    }

    @Test
    void buildFormCoversAllTwentyFourSpecTypes() throws Exception {
        runOnFx(() -> {
            for (CommandSpec spec : oneOfEachSpec()) {
                assertTrue(SpecFormDialog.buildForm(spec).isPresent(),
                    "every spec must have an inline form — missing for "
                        + spec.getClass().getSimpleName());
            }
        });
    }

    /** One representative instance of each of the 24 sealed spec types. */
    private static List<CommandSpec> oneOfEachSpec() {
        ShapeGeometry geom = new ShapeGeometry(0L, 0L, 100L, 100L);
        return List.of(
            new CommandSpec.AddShapeSpec(1, SlideShape.ShapeType.RECTANGLE, geom, "", null, null, null, false),
            new CommandSpec.RemoveShapeSpec(1, 5),
            new CommandSpec.MoveSpec(1, 5, 100L, 200L),
            new CommandSpec.ResizeSpec(1, 5, 200L, 200L),
            new CommandSpec.RotateSpec(1, 5, 45.0),
            new CommandSpec.RenameShapeSpec(1, 5, "n"),
            new CommandSpec.SetTextBoxFlagSpec(1, 5, true),
            new CommandSpec.SetRunFormatSpec(1, 5, 0, 0, TextRun.builder("x").build()),
            new CommandSpec.SetTextSpec(1, 5, TextBody.fromPlainText("hi", false)),
            new CommandSpec.SetShapeStyleSpec(1, 5, ShapeStyle.defaultStyle()),
            new CommandSpec.ReorderSpec(1, 5, CommandSpec.ReorderSpec.Direction.FRONT),
            new CommandSpec.AddAnimationSpec(1, AnimationBinding.builder()
                .target(5).type(AnimationType.FADE).entrance().clickTrigger(1).build()),
            new CommandSpec.RemoveAnimationSpec(1, 10),
            new CommandSpec.SetAnimationTimingSpec(1, 10, "500", "0"),
            new CommandSpec.SetTransitionSpec(1, TransitionType.FADE, "med", null),
            new CommandSpec.ClearTransitionSpec(1),
            new CommandSpec.CreateGroupSpec(1, List.of(5, 6), "g"),
            new CommandSpec.UngroupSpec(1, 5),
            new CommandSpec.AddToGroupSpec(1, 10, 5),
            new CommandSpec.DetachFromGroupSpec(1, 5),
            new CommandSpec.CreateCodeBoxSpec(1, "java", "code", 0L, 0L, null, null, null, null),
            new CommandSpec.CreateDiagramSpec(1, "graph LR; A-->B", null, null, null, null, null),
            new CommandSpec.AddConnectorSpec(1, "line", geom, null, null, null, null, null, null, null, null, "c", null),
            new CommandSpec.AddPictureSpec(1, BlipRef.of("ppt/media/image1.png"), geom, "p", null)
        );
    }

    // ===================================================================
    // FX helpers
    // ===================================================================

    private interface FxBody { void run() throws Exception; }

    private static void runOnFx(FxBody body) throws Exception {
        AtomicReference<Throwable> err = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            try { body.run(); } catch (Throwable t) { err.set(t); } finally { latch.countDown(); }
        });
        assertTrue(latch.await(10, TimeUnit.SECONDS), "FX latch must release");
        if (err.get() != null) {
            if (err.get() instanceof AssertionError ae) throw ae;
            throw new RuntimeException(err.get());
        }
    }

    private static List<TextField> textFields(SpecRowView view) {
        List<TextField> out = new ArrayList<>();
        collectTextFields(view.form().node(), out);
        return out;
    }

    private static void collectTextFields(Node n, List<TextField> out) {
        if (n instanceof TextField tf) { out.add(tf); return; }
        if (n instanceof Parent p) {
            for (Node c : p.getChildrenUnmodifiable()) collectTextFields(c, out);
        }
    }
}
