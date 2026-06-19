package com.excudo.view.components;

import com.excudo.core.synthesis.spec.CommandSpec;
import com.excudo.core.synthesis.spec.SpecRow;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputControl;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * One expandable row in the SlideSpec editor. The collapsed header shows a
 * dirty dot + the spec summary; expanding reveals the inline form (built
 * lazily from {@link SpecFormDialog#buildForm}) whose fields auto-commit
 * back into the backing {@link SpecRow} when focus leaves them (or on
 * collapse / Enter).
 *
 * <p>Persistent node by design — this is NOT a virtualized {@code ListCell},
 * so there is no recycling: each row owns its controls for its whole life,
 * and the expanded/dirty state lives in the {@link SpecRow} model (so a
 * full rebuild of the row list preserves it).
 *
 * <p>Commit is atomic: {@link SpecForm#read()} rebuilds the whole spec, so
 * a single unparseable field fails the commit. On failure the row keeps its
 * previous value, surfaces an inline error, and adds the
 * {@code spec-row-error} style class — never a modal popup.
 */
public final class SpecRowView extends TitledPane {

    /** Callbacks into the owning controller. Implementations must NOT
     *  rebuild the row list synchronously in {@link #onChanged()} — that
     *  would yank the controls out from under an in-progress edit. */
    public interface Host {
        /** A commit or JSON edit changed this row's spec: the script is now
         *  staged; refresh the badge + Reset gating. */
        void onChanged();
        /** This row became the active/selected one (header click, expand,
         *  or a field gained focus). */
        void onActivated(int index);
        /** Open the modal JSON editor for {@code current}; empty if the user
         *  cancelled or the JSON didn't parse. */
        Optional<CommandSpec> editAsJson(CommandSpec current);
    }

    private static final String DOT = "●"; // ●
    private static final String DOT_DIRTY_STYLE = "-fx-text-fill: #dcdcaa;";
    private static final String DOT_CLEAN_STYLE = "-fx-text-fill: #5a5a5a;";

    private final SpecRow row;
    private final int index;
    private final Host host;

    private final Label dotLabel = new Label(DOT);
    private final Label summaryLabel = new Label();
    private final Label errorLabel = new Label();

    private boolean contentBuilt;
    private SpecForm form; // null for spec types with no inline form yet

    public SpecRowView(SpecRow row, int index, Host host) {
        this.row = row;
        this.index = index;
        this.host = host;

        HBox header = new HBox(6, dotLabel, summaryLabel);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setOnMouseClicked(e -> host.onActivated(index));
        setText("");
        setGraphic(header);
        getStyleClass().add("spec-row");

        errorLabel.setStyle("-fx-text-fill: #ff6b6b; -fx-font-size: 10px;");
        errorLabel.setWrapText(true);
        errorLabel.setManaged(false);
        errorLabel.setVisible(false);

        refreshHeader();

        // Start collapsed with no listener attached so this initial set has
        // no side effects, THEN wire the listener, THEN honor the model's
        // expanded state (which builds content for an initially-open row).
        setExpanded(false);
        expandedProperty().addListener((o, was, is) -> {
            row.setExpanded(is);
            if (is) {
                ensureContentBuilt();
                host.onActivated(index);
            } else {
                commit(); // commit-on-collapse
            }
        });
        if (row.isExpanded()) setExpanded(true);
    }

    /** Toggle the active/selected styling (driven by the controller's
     *  {@code activeRow}). */
    public void setActive(boolean active) {
        if (active) {
            if (!getStyleClass().contains("spec-row-active")) getStyleClass().add("spec-row-active");
        } else {
            getStyleClass().remove("spec-row-active");
        }
    }

    private void refreshHeader() {
        summaryLabel.setText("[" + index + "] " + SpecFormDialog.summarize(row.spec()));
        dotLabel.setStyle(row.isDirty() ? DOT_DIRTY_STYLE : DOT_CLEAN_STYLE);
    }

    private void ensureContentBuilt() {
        if (contentBuilt) return;
        contentBuilt = true;

        VBox box = new VBox(6);
        box.setPadding(new Insets(4, 4, 4, 4));

        Optional<SpecForm> built = SpecFormDialog.buildForm(row.spec());
        if (built.isPresent()) {
            form = built.get();
            box.getChildren().add(form.node());
            wireCommit(form.node());
            // Nested specs expose only a common subset inline; offer the
            // full-fidelity JSON hatch alongside.
            if (SpecFormDialog.supportsAdvancedJson(row.spec())) {
                box.getChildren().add(jsonButton("Advanced (JSON)…"));
            }
        } else {
            // No inline form for this spec — JSON is the only editor.
            Label note = new Label("No inline form for this command — use JSON.");
            note.setStyle("-fx-font-size: 10px; -fx-text-fill: #aaaaaa;");
            box.getChildren().addAll(note, jsonButton("Edit as JSON…"));
        }
        box.getChildren().add(errorLabel);
        setContent(box);
    }

    private Button jsonButton(String label) {
        Button b = new Button(label);
        b.setOnAction(e -> editViaJson());
        return b;
    }

    // ====================================================================
    // Commit
    // ====================================================================

    /** Read the form into a new spec and write it back to the row. Atomic:
     *  any parse/validation error keeps the old value and shows the error
     *  inline. No-ops (value unchanged) don't flip staged. Package-visible
     *  so the {@code --gui} test can drive a commit without real focus
     *  events. */
    void commit() {
        if (form == null) return;
        CommandSpec next;
        try {
            next = form.read().get();
        } catch (RuntimeException ex) {
            showError(ex.getMessage());
            return;
        }
        clearError();
        if (next.equals(row.spec())) return; // nothing changed -> don't stage
        row.setSpec(next);
        refreshHeader();
        host.onChanged();
    }

    private void editViaJson() {
        Optional<CommandSpec> edited = host.editAsJson(row.spec());
        if (edited.isEmpty()) return;
        clearError();
        if (edited.get().equals(row.spec())) return;
        row.setSpec(edited.get());
        refreshHeader();
        rebuildContent(); // inline fields now reflect the JSON-edited spec
        host.onChanged();
    }

    /** Rebuild the expanded content from the (possibly JSON-edited) spec so
     *  the inline fields reflect the new value. */
    private void rebuildContent() {
        contentBuilt = false;
        form = null;
        ensureContentBuilt();
    }

    /** Attach commit triggers to every interactive control in the form:
     *  text fields commit on focus-loss + Enter; discrete controls
     *  (checkbox / choice / spinner) commit on value change; gaining focus
     *  on any control activates the row. */
    private void wireCommit(Node formRoot) {
        List<Node> nodes = new ArrayList<>();
        collect(formRoot, nodes);
        for (Node n : nodes) {
            if (n instanceof TextInputControl tic) {
                tic.focusedProperty().addListener((o, was, is) -> { if (is) host.onActivated(index); else commit(); });
                if (tic instanceof TextField tf) tf.setOnAction(e -> commit());
            } else if (n instanceof CheckBox cb) {
                cb.selectedProperty().addListener((o, a, b) -> commit());
                cb.indeterminateProperty().addListener((o, a, b) -> commit()); // tri-state -> inherit
                cb.focusedProperty().addListener((o, was, is) -> { if (is) host.onActivated(index); });
            } else if (n instanceof ChoiceBox<?> chb) {
                chb.valueProperty().addListener((o, a, b) -> commit());
                chb.focusedProperty().addListener((o, was, is) -> { if (is) host.onActivated(index); });
            } else if (n instanceof Spinner<?> sp) {
                sp.valueProperty().addListener((o, a, b) -> commit());
                sp.focusedProperty().addListener((o, was, is) -> { if (is) host.onActivated(index); });
                if (sp.getEditor() != null) {
                    sp.getEditor().focusedProperty().addListener((o, was, is) -> {
                        if (is) host.onActivated(index);
                        else { commitSpinnerEditor(sp); commit(); }
                    });
                    sp.getEditor().setOnAction(e -> { commitSpinnerEditor(sp); commit(); });
                }
            }
        }
    }

    /** Walk the form subtree collecting nodes, but do NOT descend into a
     *  control's own skin children (we want the logical controls, not their
     *  internal parts). */
    private static void collect(Node node, List<Node> out) {
        if (node instanceof TextInputControl || node instanceof CheckBox
                || node instanceof ChoiceBox<?> || node instanceof Spinner<?>) {
            out.add(node);
            return;
        }
        if (node instanceof Parent p) {
            for (Node child : p.getChildrenUnmodifiable()) collect(child, out);
        }
    }

    /** Force an editable spinner to parse its editor text into its value
     *  (JavaFX doesn't do this on focus-loss by default). Best-effort: a
     *  parse failure leaves the value and {@link #commit()} surfaces it. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void commitSpinnerEditor(Spinner<?> sp) {
        if (!sp.isEditable() || sp.getEditor() == null) return;
        SpinnerValueFactory vf = sp.getValueFactory();
        if (vf == null || vf.getConverter() == null) return;
        try {
            vf.setValue(vf.getConverter().fromString(sp.getEditor().getText()));
        } catch (RuntimeException ignore) {
            // leave the value as-is; commit() reads whatever it is
        }
    }

    private void showError(String msg) {
        errorLabel.setText("⚠ " + (msg == null || msg.isBlank() ? "invalid value" : msg));
        errorLabel.setManaged(true);
        errorLabel.setVisible(true);
        if (!getStyleClass().contains("spec-row-error")) getStyleClass().add("spec-row-error");
    }

    private void clearError() {
        errorLabel.setManaged(false);
        errorLabel.setVisible(false);
        getStyleClass().remove("spec-row-error");
    }

    // ====================================================================
    // Test seams (package-visible)
    // ====================================================================

    /** True when an inline error is currently shown. */
    boolean hasError() {
        return errorLabel.isVisible();
    }

    /** The backing model row. */
    SpecRow row() {
        return row;
    }

    /** Build the content now (as if expanded) — lets a headless test commit
     *  without driving a real expand animation. */
    void buildContentForTest() {
        ensureContentBuilt();
    }

    /** The inline form, or null for a JSON-only spec. */
    SpecForm form() {
        return form;
    }
}
