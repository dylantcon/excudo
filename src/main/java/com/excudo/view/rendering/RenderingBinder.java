package com.excudo.view.rendering;

import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Button;

import java.util.function.Consumer;

/**
 * Decorator that attaches a widget to a render-trigger pipeline. The
 * call-site {@code binder.decorate(widget, stateWrite)} is atomic: it
 * registers the widget's state-write AND the re-render trigger in one
 * go. Adding a new toggle without calling this is a visible no-op —
 * the widget does nothing at all — so the "set state, forget to
 * re-render" silent drift becomes impossible.
 *
 * <p>This is the Decorator pattern applied to JavaFX widgets: the
 * Binder wraps a widget's event lifecycle with the side-effect of
 * triggering a canvas re-render. The Binder returns the original
 * widget so layout wiring stays unchanged — only behavior is
 * augmented.
 *
 * <p>Observer is the implementation detail (we use JavaFX's built-in
 * property listeners), but the exposed abstraction is Decorator:
 * callers say "decorate this widget to also re-render" and get a
 * widget that does both things as a single atomic behavior.
 */
public final class RenderingBinder {

    private final Runnable renderTrigger;

    /**
     * @param renderTrigger the hook that kicks a canvas re-render
     *     (typically {@code livePreviewManager::forceRender} or a
     *     controller-level rerender method). Called on the JavaFX
     *     Application Thread after every decorated widget's state
     *     write completes.
     */
    public RenderingBinder(Runnable renderTrigger) {
        if (renderTrigger == null) {
            throw new IllegalArgumentException("renderTrigger must not be null");
        }
        this.renderTrigger = renderTrigger;
    }

    /**
     * Decorate a {@link CheckMenuItem} so toggling its selection
     * drives {@code stateWrite} and then triggers a canvas re-render.
     * Typical call:
     * <pre>{@code
     *   binder.decorate(showGridItem, editor::setShowGrid);
     * }</pre>
     *
     * @param item the menu item whose selected state drives the write
     * @param stateWrite receives the new boolean selection, writes it
     *     to the backing state (e.g. RenderingContext flag)
     * @return {@code item}, for chaining
     */
    public CheckMenuItem decorate(CheckMenuItem item, Consumer<Boolean> stateWrite) {
        if (item == null) return null;
        item.selectedProperty().addListener((obs, oldValue, newValue) -> {
            stateWrite.accept(newValue);
            renderTrigger.run();
        });
        return item;
    }

    /**
     * Decorate a plain {@link MenuItem} (no checkbox state) whose
     * action triggers a state change + re-render. Typical for zoom
     * menu items.
     *
     * @param item the menu item whose action fires
     * @param action the state write / side-effect to run; the binder
     *     runs the re-render AFTER this completes so state-dependent
     *     render logic sees the new state
     * @return {@code item}, for chaining
     */
    public MenuItem decorate(MenuItem item, Runnable action) {
        if (item == null) return null;
        item.setOnAction(e -> {
            action.run();
            renderTrigger.run();
        });
        return item;
    }

    /**
     * Decorate a {@link Button} whose action triggers a state change
     * + re-render. Same shape as the MenuItem overload.
     */
    public Button decorate(Button button, Runnable action) {
        if (button == null) return null;
        button.setOnAction(e -> {
            action.run();
            renderTrigger.run();
        });
        return button;
    }
}
