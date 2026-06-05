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
import javafx.scene.control.ListView;
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
 * Boots the JavaFX runtime and exercises {@link SlideSpecController}'s
 * own wiring: FXML-equivalent initialization, orchestrator binding, the
 * reactive synthesis populating {@code lastResult}, and the Apply
 * button's onAction wiring.
 *
 * <p>This is the narrow JavaFX-level safety net under the boundary tests
 * in {@code SlideSpecApplyFlowTest}. The mutation/JSON path is tested
 * cheaply at that boundary; this test makes sure JavaFX doesn't break
 * the wiring that <em>routes</em> a user button click to that path.
 *
 * <p>Why we don't fire the button directly: {@code applyToNewSlide}
 * ends with {@code Alert.showAndWait()}, which would block the FX
 * thread in Monocle headless mode. The button's onAction-not-null
 * assertion plus the boundary tests cover the click together.
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
     * non-trivial slide, set the active slide, verify the reactive
     * synthesis produced a result the apply button can act on, and that
     * the Apply button's onAction is wired (not null). All on the FX
     * thread because @FXML and reactive subscribers expect it.
     */
    @Test
    void controllerWiresApplyButtonAfterBindAndSetActiveSlide() throws Exception {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicReference<SlideSpecController> ctrlRef = new AtomicReference<>();
        AtomicReference<Button> applyButtonRef = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);

        Platform.runLater(() -> {
            try {
                // Source slide with one rectangle => non-trivial synthesis.
                PPTXOrchestratorImpl orch = newScaffolded();
                orch.createSlide(1, "Source", "slideLayout7");
                orch.addShape(1, SlideShape.ShapeType.RECTANGLE,
                    new ShapeGeometry(1_000_000, 1_000_000, 3_000_000, 2_000_000),
                    "", "R1", ShapeStyle.defaultStyle());

                SlideSpecController ctrl = new SlideSpecController();

                // Stand in for the FXML loader: every @FXML node the
                // controller touches must be a live JavaFX node so
                // initialize() can wire onAction handlers and the
                // reactive render() can populate the list view.
                Button applyToNew = new Button("Apply to new slide");
                Button applyToExisting = new Button("Apply to existing");
                Button copyJson = new Button("Copy JSON");
                Button edit = new Button("Edit");
                Button duplicate = new Button("Duplicate");
                Button delete = new Button("Delete");
                Button moveUp = new Button("Up");
                Button moveDown = new Button("Down");
                Button reset = new Button("Reset");

                injectField(ctrl, "slideSpecPanel", new VBox());
                injectField(ctrl, "slideSpecRow1Flow", new FlowPane());
                injectField(ctrl, "slideSpecRow2Flow", new FlowPane());
                injectField(ctrl, "slideSpecListView", new ListView<String>());
                injectField(ctrl, "slideSpecTitle", new Label());
                injectField(ctrl, "slideSpecWarnings", new Label());
                injectField(ctrl, "slideSpecStagedBadge", new Label());
                injectField(ctrl, "slideSpecCopyJsonButton", copyJson);
                injectField(ctrl, "slideSpecApplyToNewButton", applyToNew);
                injectField(ctrl, "slideSpecApplyToExistingButton", applyToExisting);
                injectField(ctrl, "slideSpecEditButton", edit);
                injectField(ctrl, "slideSpecDuplicateButton", duplicate);
                injectField(ctrl, "slideSpecDeleteButton", delete);
                injectField(ctrl, "slideSpecMoveUpButton", moveUp);
                injectField(ctrl, "slideSpecMoveDownButton", moveDown);
                injectField(ctrl, "slideSpecResetButton", reset);

                callPrivateNoArg(ctrl, "initialize");

                ctrl.bindInvokerProvider(com.excudo.core.commands.CommandInvoker::new);
                ctrl.bindToOrchestrator(orch);
                ctrl.setActiveSlide(1);

                ctrlRef.set(ctrl);
                applyButtonRef.set(applyToNew);
            } catch (Throwable t) {
                failure.set(t);
            } finally {
                done.countDown();
            }
        });

        assertTrue(done.await(10, TimeUnit.SECONDS), "FX setup latch must release");
        if (failure.get() != null) {
            fail("FX setup threw: " + failure.get(), failure.get());
        }

        SlideSpecController ctrl = ctrlRef.get();
        assertNotNull(ctrl, "Controller must be set up");

        // 1. lastResult populated by the reactive path => initialize() +
        //    bindToOrchestrator() + setActiveSlide() really ran.
        Object lastResult = getField(ctrl, "lastResult");
        assertNotNull(lastResult, "After setActiveSlide, lastResult must be "
            + "populated by reactive synthesis. If null, the reactive subscribe / "
            + "synthesizeNow path is broken in the controller wiring.");
        assertTrue(lastResult instanceof ScriptSynthesizer.Result);

        // 2. Apply button's onAction is wired by initialize(). If null,
        //    a user click would be silently swallowed -- exactly the
        //    kind of regression this smoke test exists to catch.
        Button apply = applyButtonRef.get();
        assertNotNull(apply.getOnAction(),
            "slideSpecApplyToNewButton must have an onAction set by initialize()");
    }

    // ===================================================================
    // Reflection helpers
    // ===================================================================

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
