package com.excudo.view.components;

import com.excudo.core.model.PPTXDocument;
import com.excudo.core.model.ShapeGeometry;
import com.excudo.core.model.ShapeStyle;
import com.excudo.core.model.SlideShape;
import com.excudo.core.orchestration.PPTXOrchestratorImpl;
import com.excudo.core.orchestration.PresentationScaffolder;
import com.excudo.core.synthesis.ScriptSynthesizer;
import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Boots the JavaFX runtime and exercises {@link SlideSpecController}'s own
 * wiring on the new disclosure-row container: orchestrator binding, the
 * reactive synthesis populating {@code lastResult} + the rows VBox, the
 * Apply button's onAction, and the staged/Reset-gating state machine.
 *
 * <p>Why we don't fire the apply/copy buttons directly: those handlers end
 * in {@code Alert.showAndWait()}, which would block the FX thread in Monocle
 * headless mode. We assert wiring + state instead; the mutation path itself
 * is covered at the boundary by {@code SlideSpecApplyFlowTest}.
 */
public class SlideSpecControllerSmokeTest {

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
            org.junit.jupiter.api.Assumptions.assumeTrue(false,
                "JavaFX init failed in this environment: " + e.getMessage());
        }
    }

    /**
     * Full controller wiring round-trip: bind to an orchestrator with a
     * non-trivial slide, set the active slide, verify the reactive synthesis
     * produced a result the apply button can act on, and that the Apply
     * button's onAction is wired (not null).
     */
    @Test
    void controllerWiresApplyButtonAfterBindAndSetActiveSlide() throws Exception {
        AtomicReference<SlideSpecController> ctrlRef = new AtomicReference<>();
        runOnFx(() -> {
            PPTXOrchestratorImpl orch = scaffoldWithRectangle();
            SlideSpecController ctrl = wireController(orch);
            ctrl.setActiveSlide(1);
            ctrlRef.set(ctrl);
        });

        SlideSpecController ctrl = ctrlRef.get();
        assertNotNull(ctrl, "Controller must be set up");

        // 1. lastResult populated by the reactive path => initialize() +
        //    bindToOrchestrator() + setActiveSlide() really ran.
        Object lastResult = getField(ctrl, "lastResult");
        assertNotNull(lastResult, "After setActiveSlide, lastResult must be "
            + "populated by reactive synthesis. If null, the reactive subscribe / "
            + "synthesizeNow path is broken in the controller wiring.");
        assertTrue(lastResult instanceof ScriptSynthesizer.Result);

        // 2. Apply button's onAction is wired by initialize().
        Button apply = (Button) getField(ctrl, "slideSpecApplyToNewButton");
        assertNotNull(apply.getOnAction(),
            "slideSpecApplyToNewButton must have an onAction set by initialize()");
    }

    /**
     * The staged/Reset state machine: Reset starts disabled after a clean
     * synthesis, a structural edit (Duplicate) stages the script and enables
     * Reset (and adds a row), and Reset returns to the synthesized rows with
     * the button disabled again.
     */
    @Test
    void resetGatingAndStagingTrackEdits() throws Exception {
        AtomicReference<SlideSpecController> ctrlRef = new AtomicReference<>();
        runOnFx(() -> {
            SlideSpecController ctrl = wireController(scaffoldWithRectangle());
            ctrl.setActiveSlide(1);
            ctrlRef.set(ctrl);
        });
        SlideSpecController ctrl = ctrlRef.get();
        Button reset = (Button) getField(ctrl, "slideSpecResetButton");
        VBox rowsBox = (VBox) getField(ctrl, "slideSpecRows");
        int[] synthesizedCount = new int[1];

        runOnFx(() -> {
            assertTrue(reset.isDisable(), "Reset must be disabled on a clean (un-staged) script");
            assertFalse(rowsBox.getChildren().isEmpty(), "rows VBox populated by synthesis");
            synthesizedCount[0] = rowsBox.getChildren().size();
        });

        // Select row 0, then Duplicate -> stages the script.
        runOnFx(() -> {
            ctrl.onActivated(0);
            callPrivateNoArg(ctrl, "duplicateSelected");
        });
        runOnFx(() -> {
            assertFalse(reset.isDisable(), "Reset must enable once the script is staged");
            assertEquals(synthesizedCount[0] + 1, rowsBox.getChildren().size(),
                "Duplicate must add a row view");
        });

        // Reset -> back to synthesized rows, button disabled.
        runOnFx(() -> callPrivateNoArg(ctrl, "resetToSynthesized"));
        runOnFx(() -> {
            assertTrue(reset.isDisable(), "Reset must disable again after resetToSynthesized");
            assertEquals(synthesizedCount[0], rowsBox.getChildren().size(),
                "Reset must restore the synthesized row count");
        });
    }

    // ===================================================================
    // Setup helpers
    // ===================================================================

    /** A scaffolded orchestrator with one slide carrying a single rectangle
     *  (non-trivial synthesis). Must be called on the FX thread. */
    private static PPTXOrchestratorImpl scaffoldWithRectangle() {
        PPTXOrchestratorImpl orch = newScaffolded();
        orch.createSlide(1, "Source", "slideLayout7");
        orch.addShape(1, SlideShape.ShapeType.RECTANGLE,
            new ShapeGeometry(1_000_000, 1_000_000, 3_000_000, 2_000_000),
            "", "R1", ShapeStyle.defaultStyle());
        return orch;
    }

    /** Stand in for the FXML loader: inject a live node for every @FXML field
     *  the controller touches, then run initialize() + bind the orchestrator.
     *  Buttons are retrievable afterward via {@link #getField}. */
    private static SlideSpecController wireController(PPTXOrchestratorImpl orch) throws Exception {
        SlideSpecController ctrl = new SlideSpecController();
        injectField(ctrl, "slideSpecPanel", new VBox());
        injectField(ctrl, "slideSpecRow1Flow", new FlowPane());
        injectField(ctrl, "slideSpecRow2Flow", new FlowPane());
        injectField(ctrl, "slideSpecScroll", new ScrollPane());
        injectField(ctrl, "slideSpecRows", new VBox());
        injectField(ctrl, "slideSpecTitle", new Label());
        injectField(ctrl, "slideSpecWarnings", new Label());
        injectField(ctrl, "slideSpecStagedBadge", new Label());
        injectField(ctrl, "slideSpecCopyJsonButton", new Button("Copy JSON"));
        injectField(ctrl, "slideSpecApplyToNewButton", new Button("Apply to new slide"));
        injectField(ctrl, "slideSpecApplyToExistingButton", new Button("Apply to existing"));
        injectField(ctrl, "slideSpecEditButton", new Button("Edit"));
        injectField(ctrl, "slideSpecDuplicateButton", new Button("Duplicate"));
        injectField(ctrl, "slideSpecDeleteButton", new Button("Delete"));
        injectField(ctrl, "slideSpecMoveUpButton", new Button("Up"));
        injectField(ctrl, "slideSpecMoveDownButton", new Button("Down"));
        injectField(ctrl, "slideSpecResetButton", new Button("Reset"));
        callPrivateNoArg(ctrl, "initialize");
        ctrl.bindInvokerProvider(com.excudo.core.commands.CommandInvoker::new);
        ctrl.bindToOrchestrator(orch);
        return ctrl;
    }

    // ===================================================================
    // FX + reflection helpers
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

    private static void injectField(Object target, String name, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }

    private static Object getField(Object target, String name) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.get(target);
    }

    private static void callPrivateNoArg(Object target, String name) throws Exception {
        Method m = target.getClass().getDeclaredMethod(name);
        m.setAccessible(true);
        m.invoke(target);
    }

    private static PPTXOrchestratorImpl newScaffolded() {
        try {
            PPTXDocument doc = PresentationScaffolder.scaffoldDocument("excudo");
            PPTXOrchestratorImpl orch = new PPTXOrchestratorImpl();
            orch.initialize(doc);
            return orch;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
