package com.excudo.view.components;

import com.excudo.core.commands.RunSlideScriptCommand;
import com.excudo.core.orchestration.PPTXOrchestrator;
import com.excudo.core.orchestration.SessionManager;
import com.excudo.core.synthesis.ReactiveSynthesizer;
import com.excudo.core.synthesis.ScriptSynthesizer;
import com.excudo.core.synthesis.spec.CommandSpec;
import com.excudo.core.synthesis.spec.CommandSpecJson;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Controller for the SlideSpec tab: displays the synthesized
 * {@code CommandScript} for the active slide as an indexed, live-refreshed
 * listing. Consumes {@link ReactiveSynthesizer} so updates are
 * debounced-and-cached rather than raw per-mutation.
 *
 * <p>v1 (6.10a): read-only list + Copy JSON + Apply-to-{new,existing}
 * buttons. Form editing (6.10b-c) and apply-UX polish (6.10d) layer
 * on top.
 */
public class SlideSpecController {

    private ListView<String> listView;
    private Label titleLabel;
    private Label warningsLabel;
    private Button copyJsonButton;
    private Button applyToNewButton;
    private Button applyToExistingButton;

    private final ObservableList<String> rowLabels = FXCollections.observableArrayList();
    private ReactiveSynthesizer reactive;
    private ScriptSynthesizer.Result lastResult;
    private int activeSlide = -1;
    private PPTXOrchestrator currentOrchestrator;

    // Staged script — the user's edited copy. Null when the synthesized
    // script is being displayed verbatim. Any edit or list-op switches
    // the view to the staged script and sets the "Staged" badge; Reset
    // returns to the synthesized view.
    private List<CommandSpec> stagedSpecs;
    private javafx.scene.control.Label stagedBadge;
    private javafx.scene.control.Button editButton;
    private javafx.scene.control.Button duplicateButton;
    private javafx.scene.control.Button deleteButton;
    private javafx.scene.control.Button moveUpButton;
    private javafx.scene.control.Button moveDownButton;
    private javafx.scene.control.Button resetButton;

    // ========== Setters (wired from MainController after FXML load) ==========

    public void setListView(ListView<String> listView) {
        this.listView = listView;
        if (listView != null) {
            listView.setItems(rowLabels);
            listView.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2) editSelected();
            });
        }
    }

    public void setStagedBadge(javafx.scene.control.Label badge) { this.stagedBadge = badge; }

    public void setEditButton(javafx.scene.control.Button btn) {
        this.editButton = btn;
        if (btn != null) btn.setOnAction(e -> editSelected());
    }
    public void setDuplicateButton(javafx.scene.control.Button btn) {
        this.duplicateButton = btn;
        if (btn != null) btn.setOnAction(e -> duplicateSelected());
    }
    public void setDeleteButton(javafx.scene.control.Button btn) {
        this.deleteButton = btn;
        if (btn != null) btn.setOnAction(e -> deleteSelected());
    }
    public void setMoveUpButton(javafx.scene.control.Button btn) {
        this.moveUpButton = btn;
        if (btn != null) btn.setOnAction(e -> moveSelected(-1));
    }
    public void setMoveDownButton(javafx.scene.control.Button btn) {
        this.moveDownButton = btn;
        if (btn != null) btn.setOnAction(e -> moveSelected(+1));
    }
    public void setResetButton(javafx.scene.control.Button btn) {
        this.resetButton = btn;
        if (btn != null) btn.setOnAction(e -> resetToSynthesized());
    }

    public void setTitleLabel(Label label) { this.titleLabel = label; }
    public void setWarningsLabel(Label label) { this.warningsLabel = label; }

    public void setCopyJsonButton(Button button) {
        this.copyJsonButton = button;
        if (button != null) button.setOnAction(e -> copyJsonToClipboard());
    }

    public void setApplyToNewButton(Button button) {
        this.applyToNewButton = button;
        if (button != null) button.setOnAction(e -> applyToNewSlide());
    }

    public void setApplyToExistingButton(Button button) {
        this.applyToExistingButton = button;
        if (button != null) button.setOnAction(e -> applyToExistingSlide());
    }

    /** Attach this controller to the given orchestrator. Pulls the
     *  reactive synthesizer for that orchestrator and subscribes to
     *  per-slide synthesis events + the session's active-slide stream. */
    public void bindToOrchestrator(PPTXOrchestrator orch) {
        if (currentOrchestrator == orch) return;
        currentOrchestrator = orch;
        if (reactive != null) {
            reactive.unsubscribe(this::onReactiveResult);
        }
        if (orch == null) {
            reactive = null;
            clearView();
            return;
        }
        reactive = new ReactiveSynthesizer(orch);
        reactive.subscribe(this::onReactiveResult);
    }

    /** Switch the active slide (typically on tree / canvas selection).
     *  Triggers an immediate synthesis for the new slide so the tab
     *  doesn't sit blank until the next mutation. */
    public void setActiveSlide(int slideNumber) {
        if (slideNumber == activeSlide) return;
        activeSlide = slideNumber;
        if (reactive == null || slideNumber <= 0) {
            clearView();
            return;
        }
        ScriptSynthesizer.Result r = reactive.synthesizeNow(slideNumber);
        render(slideNumber, r);
    }

    // ========== Reactive path ==========

    private void onReactiveResult(ReactiveSynthesizer.ReactiveResult payload) {
        if (payload == null) return;
        if (payload.slideNumber() != activeSlide) return; // ignore other slides
        Platform.runLater(() -> render(payload.slideNumber(), payload.result()));
    }

    private void render(int slideNumber, ScriptSynthesizer.Result r) {
        this.lastResult = r;
        // If the user has a staged edit active, don't clobber it with
        // a fresh synthesis — the staged list is the user's working
        // copy. New synthesis results still update `lastResult` so the
        // "Reset to synthesized" button has fresh data to revert to.
        if (stagedSpecs != null) {
            return;
        }
        if (titleLabel != null) {
            titleLabel.setText("SlideSpec: slide " + slideNumber
                + " (" + (r != null ? r.script().size() : 0) + " commands)");
        }
        rowLabels.clear();
        if (r == null) {
            if (warningsLabel != null) warningsLabel.setText("");
            return;
        }
        List<CommandSpec> order = r.script().topologicalOrder();
        for (int i = 0; i < order.size(); i++) {
            rowLabels.add("[" + i + "] " + summarize(order.get(i)));
        }
        if (warningsLabel != null) {
            if (r.warnings().isEmpty()) {
                warningsLabel.setText("");
            } else {
                warningsLabel.setText("Warnings: " + String.join(" | ", r.warnings()));
            }
        }
        if (stagedBadge != null) stagedBadge.setText("");
    }

    /** Refresh the list view from the staged script. Called after every
     *  edit / list-op so the UI reflects current staging. */
    private void renderStaged() {
        rowLabels.clear();
        if (stagedSpecs == null) return;
        for (int i = 0; i < stagedSpecs.size(); i++) {
            rowLabels.add("[" + i + "] " + summarize(stagedSpecs.get(i)) + "  (edited)");
        }
        if (stagedBadge != null) stagedBadge.setText("Staged — edits not yet applied");
    }

    /** Ensure stagedSpecs is non-null; initialize from the last synthesized
     *  result if the user has just started editing. */
    private boolean ensureStaged() {
        if (stagedSpecs != null) return true;
        if (lastResult == null) {
            showInfo("No script available to edit yet.");
            return false;
        }
        stagedSpecs = new ArrayList<>(lastResult.script().topologicalOrder());
        return true;
    }

    private int selectedIndex() {
        return listView != null ? listView.getSelectionModel().getSelectedIndex() : -1;
    }

    private void editSelected() {
        int idx = selectedIndex();
        if (idx < 0) { showInfo("Select a spec first."); return; }
        if (!ensureStaged()) return;
        CommandSpec current = stagedSpecs.get(idx);
        String currentJson = CommandSpecJson.toJson(current);

        javafx.scene.control.Dialog<String> dlg = new javafx.scene.control.Dialog<>();
        dlg.setTitle("Edit spec [" + idx + "]");
        dlg.setHeaderText("Edit the JSON representation of this spec.");
        javafx.scene.control.TextArea area = new javafx.scene.control.TextArea(pretty(currentJson));
        area.setPrefRowCount(18);
        area.setPrefColumnCount(60);
        area.setStyle("-fx-font-family: Monospaced;");
        dlg.getDialogPane().setContent(area);
        javafx.scene.control.ButtonType save = new javafx.scene.control.ButtonType(
            "Save", javafx.scene.control.ButtonBar.ButtonData.OK_DONE);
        dlg.getDialogPane().getButtonTypes().addAll(
            save, javafx.scene.control.ButtonType.CANCEL);
        dlg.setResultConverter(bt -> bt == save ? area.getText() : null);

        Optional<String> edited = dlg.showAndWait();
        if (edited.isEmpty()) return;
        try {
            CommandSpec parsed = CommandSpecJson.fromJson(edited.get());
            stagedSpecs.set(idx, parsed);
            renderStaged();
            listView.getSelectionModel().select(idx);
        } catch (RuntimeException ex) {
            showError("Failed to parse edited JSON: " + ex.getMessage());
        }
    }

    private void duplicateSelected() {
        int idx = selectedIndex();
        if (idx < 0) { showInfo("Select a spec first."); return; }
        if (!ensureStaged()) return;
        stagedSpecs.add(idx + 1, stagedSpecs.get(idx));
        renderStaged();
        listView.getSelectionModel().select(idx + 1);
    }

    private void deleteSelected() {
        int idx = selectedIndex();
        if (idx < 0) { showInfo("Select a spec first."); return; }
        if (!ensureStaged()) return;
        stagedSpecs.remove(idx);
        renderStaged();
        if (!stagedSpecs.isEmpty()) {
            listView.getSelectionModel().select(Math.min(idx, stagedSpecs.size() - 1));
        }
    }

    private void moveSelected(int delta) {
        int idx = selectedIndex();
        if (idx < 0) { showInfo("Select a spec first."); return; }
        if (!ensureStaged()) return;
        int target = idx + delta;
        if (target < 0 || target >= stagedSpecs.size()) return;
        CommandSpec spec = stagedSpecs.remove(idx);
        stagedSpecs.add(target, spec);
        renderStaged();
        listView.getSelectionModel().select(target);
    }

    private void resetToSynthesized() {
        stagedSpecs = null;
        if (lastResult != null) render(activeSlide, lastResult);
        else clearView();
    }

    /** Best-effort JSON pretty-printer so the edit dialog is scannable.
     *  Not a full pretty-printer; good enough for small specs. */
    private static String pretty(String json) {
        return json
            .replace(",\"", ",\n  \"")
            .replace("{\"", "{\n  \"")
            .replace("\"}", "\"\n}")
            .replace("\"}", "\"\n}");
    }

    private void clearView() {
        lastResult = null;
        rowLabels.clear();
        if (titleLabel != null) titleLabel.setText("SlideSpec (no slide selected)");
        if (warningsLabel != null) warningsLabel.setText("");
    }

    // ========== Actions ==========

    private void copyJsonToClipboard() {
        List<CommandSpec> specs = currentSpecs();
        if (specs == null) {
            showInfo("Nothing to copy — no script synthesized yet for this slide.");
            return;
        }
        String json = CommandSpecJson.toJsonArray(specs);
        ClipboardContent cc = new ClipboardContent();
        cc.putString(json);
        Clipboard.getSystemClipboard().setContent(cc);
        showInfo("Script JSON copied to clipboard (" + json.length() + " chars, "
            + (stagedSpecs != null ? "staged edits" : "synthesized") + ").");
    }

    /** Staged edits take precedence over the synthesized script when
     *  the user has entered edit mode. */
    private List<CommandSpec> currentSpecs() {
        if (stagedSpecs != null) return stagedSpecs;
        if (lastResult == null) return null;
        return lastResult.script().topologicalOrder();
    }

    private void applyToNewSlide() {
        if (currentSpecs() == null || currentOrchestrator == null) {
            showInfo("Nothing to apply.");
            return;
        }
        // Create a new slide after the active one, same layout, then
        // run the script on it. Default label "Untitled" — user can
        // rename later via the canvas or properties pane.
        try {
            int targetPos = activeSlide > 0 ? activeSlide + 1 : 1;
            String layoutId = resolveLayoutIdForSlide(activeSlide);
            com.excudo.core.results.SlideExecutionResult created =
                currentOrchestrator.createSlide(targetPos, "Untitled", layoutId);
            if (!created.isSuccess()) {
                showError("Failed to create target slide: " + created.getMessage());
                return;
            }
            int newSlide = targetPos;
            String json = CommandSpecJson.toJsonArray(currentSpecs());
            RunSlideScriptCommand cmd = new RunSlideScriptCommand(
                newSlide, json, currentOrchestrator, null);
            cmd.execute();
            showInfo("Applied " + cmd.getAppliedSpecCount() + " spec(s) to new slide " + newSlide + ".");
        } catch (Exception ex) {
            showError("Apply failed: " + ex.getMessage());
        }
    }

    private void applyToExistingSlide() {
        if (currentSpecs() == null || currentOrchestrator == null) {
            showInfo("Nothing to apply.");
            return;
        }
        List<Integer> slides = currentOrchestrator.getContext().isPresent()
            ? currentOrchestrator.getContext().get().getDocument().getSlideNumbers()
            : new ArrayList<>();
        if (slides.isEmpty()) {
            showError("No slides available.");
            return;
        }
        ChoiceDialog<Integer> dialog = new ChoiceDialog<>(slides.get(0), slides);
        dialog.setTitle("Apply script");
        dialog.setHeaderText("Run this script on which slide?");
        Optional<Integer> choice = dialog.showAndWait();
        if (choice.isEmpty()) return;
        int target = choice.get();
        if (target == activeSlide) {
            Alert warn = new Alert(Alert.AlertType.CONFIRMATION);
            warn.setTitle("Source slide selected");
            warn.setHeaderText("This will run the script AGAIN on the source.");
            warn.setContentText("Shapes will likely be duplicated. Clear the slide first for "
                + "a clean replay. Continue anyway?");
            Optional<javafx.scene.control.ButtonType> r = warn.showAndWait();
            if (r.isEmpty() || r.get() != javafx.scene.control.ButtonType.OK) return;
        }
        try {
            String json = CommandSpecJson.toJsonArray(currentSpecs());
            RunSlideScriptCommand cmd = new RunSlideScriptCommand(
                target, json, currentOrchestrator, null);
            cmd.execute();
            showInfo("Applied " + cmd.getAppliedSpecCount() + " spec(s) to slide " + target + ".");
        } catch (Exception ex) {
            showError("Apply failed: " + ex.getMessage());
        }
    }

    // ========== Helpers ==========

    private String resolveLayoutIdForSlide(int slideNumber) {
        try {
            var doc = currentOrchestrator.getContext().get().getDocument();
            var state = doc.getParsedState();
            if (state != null) {
                String id = state.getSlideToLayoutId().get(slideNumber);
                if (id != null) return id;
            }
        } catch (Exception ignored) {}
        return "slideLayout1";
    }

    /** One-line summary of a spec for the indexed listing. Mirrors the
     *  MCP tool's format so GUI and agent see the same shape. */
    private static String summarize(CommandSpec s) {
        return switch (s) {
            case CommandSpec.AddShapeSpec a ->
                String.format("AddShape %s name=%s @(%d,%d %dx%d)",
                    a.shapeType(), a.name(),
                    a.geometry().getX(), a.geometry().getY(),
                    a.geometry().getWidth(), a.geometry().getHeight());
            case CommandSpec.RemoveShapeSpec r -> "RemoveShape spid=" + r.spid();
            case CommandSpec.MoveSpec m ->
                String.format("Move spid=%d (%d,%d)", m.spid(), m.newX(), m.newY());
            case CommandSpec.ResizeSpec r ->
                String.format("Resize spid=%d %dx%d", r.spid(), r.newWidth(), r.newHeight());
            case CommandSpec.RotateSpec r ->
                String.format("Rotate spid=%d %.2f°", r.spid(), r.newRotationDegrees());
            case CommandSpec.RenameShapeSpec r ->
                "Rename spid=" + r.spid() + " → " + r.newName();
            case CommandSpec.SetTextSpec st ->
                "SetText spid=" + st.spid() + " ("
                    + st.textBody().getParagraphs().size() + "p)";
            case CommandSpec.SetShapeStyleSpec ss -> "SetStyle spid=" + ss.spid();
            case CommandSpec.SetTextBoxFlagSpec tb ->
                "SetTxBoxFlag spid=" + tb.spid() + "=" + tb.flag();
            case CommandSpec.SetRunFormatSpec rf ->
                String.format("SetRunFormat spid=%d p=%d r=%d", rf.spid(), rf.paragraphIdx(), rf.runIdx());
            case CommandSpec.ReorderSpec r -> "Reorder spid=" + r.spid() + " " + r.direction();
            case CommandSpec.AddAnimationSpec a ->
                String.format("AddAnim target=%d %s", a.binding().getTargetSpid(), a.binding().getAnimationType());
            case CommandSpec.RemoveAnimationSpec r -> "RemoveAnim cTn=" + r.timingNodeId();
            case CommandSpec.SetAnimationTimingSpec t -> "SetAnimTiming cTn=" + t.timingNodeId();
            case CommandSpec.SetTransitionSpec tt ->
                "SetTransition " + tt.transitionType() + " " + tt.speed();
            case CommandSpec.ClearTransitionSpec c -> "ClearTransition";
            case CommandSpec.CreateGroupSpec g -> "CreateGroup children=" + g.childSpids();
            case CommandSpec.UngroupSpec u -> "Ungroup spid=" + u.groupSpid();
            case CommandSpec.AddToGroupSpec a ->
                "AddToGroup group=" + a.groupSpid() + " child=" + a.childSpid();
            case CommandSpec.DetachFromGroupSpec d -> "DetachFromGroup child=" + d.childSpid();
        };
    }

    private void showInfo(String msg) {
        Alert a = new Alert(Alert.AlertType.INFORMATION, msg);
        a.setHeaderText(null);
        a.showAndWait();
    }

    private void showError(String msg) {
        Alert a = new Alert(Alert.AlertType.ERROR, msg);
        a.setHeaderText(null);
        a.showAndWait();
    }

    // Silence unused field sink so the listView / labels / buttons
    // referenced only via setters remain intentionally wired.
    @SuppressWarnings("unused")
    private Object refSink() { return new Object[] { listView, titleLabel, warningsLabel,
        copyJsonButton, applyToNewButton, applyToExistingButton, reactive }; }
}
