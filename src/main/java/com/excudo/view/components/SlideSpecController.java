package com.excudo.view.components;

import com.excudo.core.commands.mutating.slide.RunSlideScriptCommand;
import com.excudo.core.orchestration.PPTXOrchestrator;
import com.excudo.core.synthesis.ReactiveSynthesizer;
import com.excudo.core.synthesis.ScriptSynthesizer;
import com.excudo.core.synthesis.spec.CommandSpec;
import com.excudo.core.synthesis.spec.CommandSpecJson;
import com.excudo.core.synthesis.spec.SpecRow;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.Label;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Controller for the SlideSpec tab: displays the synthesized
 * {@code CommandScript} for the active slide as a column of
 * inline-expandable, editable rows ({@link SpecRowView}). Consumes
 * {@link ReactiveSynthesizer} so updates are debounced-and-cached rather
 * than raw per-mutation.
 *
 * <p>Editing is in place: expand a row, change a field, and it commits
 * back into a {@link SpecRow} on focus-loss. Any edit (or a structural
 * list-op) <em>stages</em> the script — a working copy that diverges from
 * the synthesized baseline — which lights the badge and enables
 * "Reset to synthesized". The per-row dirty dot marks exactly which rows
 * deviate; Reset drops the whole working copy.
 */
public class SlideSpecController implements SpecRowView.Host {

    // @FXML-injected from panels/SlideSpec.fxml.
    @FXML private javafx.scene.layout.VBox slideSpecPanel;
    @FXML private javafx.scene.layout.FlowPane slideSpecRow1Flow;
    @FXML private javafx.scene.layout.FlowPane slideSpecRow2Flow;
    @FXML private javafx.scene.layout.VBox slideSpecRows;
    @FXML private Label slideSpecTitle;
    @FXML private Label slideSpecWarnings;
    @FXML private Label slideSpecStagedBadge;
    @FXML private Button slideSpecCopyJsonButton;
    @FXML private Button slideSpecApplyToNewButton;
    @FXML private Button slideSpecApplyToExistingButton;
    @FXML private Button slideSpecEditButton;
    @FXML private Button slideSpecDuplicateButton;
    @FXML private Button slideSpecDeleteButton;
    @FXML private Button slideSpecMoveUpButton;
    @FXML private Button slideSpecMoveDownButton;
    @FXML private Button slideSpecResetButton;

    /** Required provider for the active session's CommandInvoker.
     *  Every mutation goes through the invoker — no exceptions, no
     *  silent bypass. If this isn't wired before an apply call runs,
     *  that's an architectural bug and we throw so the caller sees it. */
    private java.util.function.Supplier<com.excudo.core.commands.CommandInvoker> invokerProvider;

    private ReactiveSynthesizer reactive;
    private ScriptSynthesizer.Result lastResult;
    private int activeSlide = -1;
    private PPTXOrchestrator currentOrchestrator;

    // The display model: one SpecRow per command, plus the live views that
    // render them. `staged` is true once any edit or list-op has produced a
    // working copy that diverges from the synthesized baseline; while
    // staged, fresh syntheses update `lastResult` but do NOT clobber the
    // rows (the user's working copy wins). `activeRow` is the selected row
    // index (replaces the old ListView selection model).
    private final List<SpecRow> rows = new ArrayList<>();
    private final List<SpecRowView> rowViews = new ArrayList<>();
    private int activeRow = -1;
    private boolean staged;

    /** JavaFX calls this after @FXML injection finishes. Wires up
     *  button actions once every node is live. */
    @FXML
    private void initialize() {
        if (slideSpecCopyJsonButton != null)
            slideSpecCopyJsonButton.setOnAction(e -> copyJsonToClipboard());
        if (slideSpecApplyToNewButton != null)
            slideSpecApplyToNewButton.setOnAction(e -> applyToNewSlide());
        if (slideSpecApplyToExistingButton != null)
            slideSpecApplyToExistingButton.setOnAction(e -> applyToExistingSlide());
        if (slideSpecEditButton != null)
            slideSpecEditButton.setOnAction(e -> editSelected());
        if (slideSpecDuplicateButton != null)
            slideSpecDuplicateButton.setOnAction(e -> duplicateSelected());
        if (slideSpecDeleteButton != null)
            slideSpecDeleteButton.setOnAction(e -> deleteSelected());
        if (slideSpecMoveUpButton != null)
            slideSpecMoveUpButton.setOnAction(e -> moveSelected(-1));
        if (slideSpecMoveDownButton != null)
            slideSpecMoveDownButton.setOnAction(e -> moveSelected(+1));
        if (slideSpecResetButton != null)
            slideSpecResetButton.setOnAction(e -> resetToSynthesized());

        updateResetGating();
        installDynamicFontScaling();
    }

    // Font scaling layered on the FlowPane layout. FlowPane wraps items
    // to new rows when they don't fit; scaling is the soft adjustment
    // that keeps items on one row longer by shrinking font-size as the
    // panel narrows. Required width = widest button row at BASE_FONT_PT
    // measured via JavaFX Text.getLayoutBounds(). scale = avail / req
    // clamped to [MIN_SCALE, MAX_SCALE]; FlowPane handles any residual
    // overflow below the floor by wrapping. No per-button sizing — the
    // cascaded -fx-font-size drives Button's intrinsic width naturally.
    private static final double MAX_SCALE    = 1.00;
    private static final double MIN_SCALE    = 0.70;
    private static final double BASE_FONT_PT = 10.0;

    // Modena Button padding: 0.333em 0.667em -> 1.333em horizontal.
    // 1em(px) = pt * 4/3, so h-pad(px) = 1.778 * pt. Plus 4px border /
    // focus-ring margin. No hand tuning — derived from Modena defaults.
    private static final double BUTTON_PAD_PT_COEF = 1.778;
    private static final double BUTTON_PAD_FIXED   = 4.0;
    private static final double FLOW_HGAP          = 5.0;
    private static final double PANEL_PADDING_H    = 10.0;

    private static final String   TITLE_TEXT  = "SlideSpec (no slide selected)";
    private static final String[] ROW1_LABELS = { "Copy JSON", "Apply to new slide", "Apply to existing slide" };
    private static final String[] ROW2_LABELS = { "Edit", "Duplicate", "Delete", "Up", "Down", "Reset to synthesized" };

    private Double cachedRequiredWidth;

    // Per-button auto-scale: minimum pt before the button label gives
    // up and ellipsizes. Below this, we stop shrinking to keep text
    // readable — FlowPane's wrap is the next line of defence.
    private static final double BUTTON_MIN_PT = 6.0;

    private void installDynamicFontScaling() {
        if (slideSpecPanel == null) {
            System.err.println("[SlideSpec] font scaling DISABLED: "
                + "slideSpecPanel root injection returned null.");
            return;
        }
        double required = measuredRequiredWidth();
        System.err.println(String.format(java.util.Locale.ROOT,
            "[SlideSpec] font scaling ENABLED: required@%.0fpt=%.0fpx",
            BASE_FONT_PT, required));
        slideSpecPanel.widthProperty().addListener((obs, oldW, newW) -> {
            if (newW == null) return;
            applyFontScale(newW.doubleValue());
        });
        applyFontScale(slideSpecPanel.getWidth());

        // Per-button auto-scale: independent of the panel-wide scale.
        // Buttons with longer labels shrink first; "Up" / "Down" keep
        // their base size until the panel is genuinely crowded.
        bindButtonAutoScale(slideSpecCopyJsonButton);
        bindButtonAutoScale(slideSpecApplyToNewButton);
        bindButtonAutoScale(slideSpecApplyToExistingButton);
        bindButtonAutoScale(slideSpecEditButton);
        bindButtonAutoScale(slideSpecDuplicateButton);
        bindButtonAutoScale(slideSpecDeleteButton);
        bindButtonAutoScale(slideSpecMoveUpButton);
        bindButtonAutoScale(slideSpecMoveDownButton);
        bindButtonAutoScale(slideSpecResetButton);

        // Proportional fill: when the FlowPane has excess horizontal
        // space beyond the sum of button minimum widths, distribute
        // the excess equally across buttons so they expand to occupy
        // the row instead of leaving dead space on the right.
        bindProportionalFill(slideSpecRow1Flow, java.util.List.of(
            slideSpecCopyJsonButton,
            slideSpecApplyToNewButton,
            slideSpecApplyToExistingButton));
        bindProportionalFill(slideSpecRow2Flow, java.util.List.of(
            slideSpecEditButton,
            slideSpecDuplicateButton,
            slideSpecDeleteButton,
            slideSpecMoveUpButton,
            slideSpecMoveDownButton,
            slideSpecResetButton));
    }

    private void bindProportionalFill(javafx.scene.layout.FlowPane flow,
                                      java.util.List<Button> buttons) {
        if (flow == null) {
            System.err.println("[SlideSpec] bindProportionalFill: flow is null "
                + "— fx:id injection failed. Falling back to no fill.");
            return;
        }
        Runnable redistribute = () -> distributeFill(flow, buttons);
        flow.widthProperty().addListener((obs, oldW, newW) -> redistribute.run());
        redistribute.run();
        // Safety net: if the widthProperty doesn't fire on the initial
        // layout pulse (e.g. scene laid out before we wired the listener),
        // runLater catches us on the next FX tick with a populated width.
        javafx.application.Platform.runLater(redistribute);
    }

    // Safety buffer: leave a few px unused so fractional rounding on
    // individual button widths can't push sum past flowW and trigger
    // an unwanted FlowPane wrap.
    private static final double FLOW_SAFETY = 4.0;

    // Per-button cache of the button's own reported intrinsic pref
    // width at BASE_FONT_PT. We ask JavaFX via Button.prefWidth(-1)
    // rather than approximating text+padding — the approximation
    // underestimates by enough to truncate ('Copy JSON' needs 96 but
    // textW+pad comes out 72). JavaFX's own number accounts for the
    // actual font family, Modena padding + insets + border, etc.
    private final java.util.Map<Button, Double> intrinsicCache =
        new java.util.IdentityHashMap<>();

    /** Get the button's intrinsic pref width at BASE_FONT_PT, asking
     *  JavaFX for it on first call and caching. Temporarily clears
     *  our inline style/pref/min so prefWidth(-1) returns the real
     *  computed value; restores before returning. Called only once
     *  per button on the cold path. */
    private double intrinsicAtBase(Button b) {
        Double cached = intrinsicCache.get(b);
        if (cached != null) return cached;
        String savedStyle = b.getStyle();
        double savedPref  = b.getPrefWidth();
        double savedMin   = b.getMinWidth();
        double result;
        try {
            b.setStyle(String.format(java.util.Locale.ROOT,
                "-fx-font-size: %.2fpt;", BASE_FONT_PT));
            b.setPrefWidth(Button.USE_COMPUTED_SIZE);
            b.setMinWidth(Button.USE_COMPUTED_SIZE);
            b.applyCss();
            double pw = b.prefWidth(-1);
            if (pw > 0 && pw < 10000) {
                result = Math.ceil(pw);
            } else {
                // Fallback to approximation if JavaFX didn't give a
                // sensible number (e.g. button not in scene yet).
                String label = b.getText() != null ? b.getText() : "";
                result = Math.ceil(measureTextWidth(label, BASE_FONT_PT, false)
                    + BUTTON_PAD_PT_COEF * BASE_FONT_PT + BUTTON_PAD_FIXED);
            }
        } finally {
            b.setStyle(savedStyle);
            b.setPrefWidth(savedPref);
            b.setMinWidth(savedMin);
        }
        intrinsicCache.put(b, result);
        return result;
    }

    /** Fair-share allocation with wrap awareness. Simulates FlowPane's
     *  greedy left-to-right packing to assign buttons to rows, then
     *  for each row distributes that row's leftover space evenly among
     *  its own buttons. This means a 2-row wrap has each row filled
     *  proportionally instead of packed-left with dead space on the right. */
    private void distributeFill(javafx.scene.layout.FlowPane flow,
                                java.util.List<Button> buttons) {
        double flowW = flow.getWidth();
        if (flowW <= 0 || buttons.isEmpty()) return;
        int sz = buttons.size();
        double[] mins = new double[sz];
        boolean[] valid = new boolean[sz];
        for (int i = 0; i < sz; i++) {
            Button b = buttons.get(i);
            if (b == null) continue;
            mins[i] = intrinsicAtBase(b);
            valid[i] = true;
        }

        // Greedy row packing — mirrors FlowPane's own wrap algorithm so
        // our per-row allocation matches its layout output.
        double capacity = flowW - FLOW_SAFETY;
        java.util.List<java.util.List<Integer>> rows = new java.util.ArrayList<>();
        java.util.List<Integer> current = new java.util.ArrayList<>();
        double currentSum = 0;
        for (int i = 0; i < sz; i++) {
            if (!valid[i]) continue;
            double add = (current.isEmpty() ? 0 : FLOW_HGAP) + mins[i];
            if (currentSum + add > capacity && !current.isEmpty()) {
                rows.add(current);
                current = new java.util.ArrayList<>();
                currentSum = 0;
                add = mins[i];
            }
            current.add(i);
            currentSum += add;
        }
        if (!current.isEmpty()) rows.add(current);

        boolean doLog = distributeDebugLogs < 4;
        if (doLog) {
            System.err.println(String.format(java.util.Locale.ROOT,
                "[SlideSpec] distributeFill flow=%s flowW=%.0f rows=%d",
                flow == slideSpecRow1Flow ? "Row1" : "Row2", flowW, rows.size()));
        }

        for (var row : rows) {
            int rn = row.size();
            double rowMin = 0;
            for (int idx : row) rowMin += mins[idx];
            double gaps = (rn - 1) * FLOW_HGAP;
            double rowAvail = capacity - gaps;
            double excessPer = Math.max(0, (rowAvail - rowMin) / rn);
            for (int idx : row) {
                Button b = buttons.get(idx);
                double w = Math.floor(mins[idx] + excessPer);
                if (doLog) {
                    System.err.println(String.format(java.util.Locale.ROOT,
                        "[SlideSpec]   row=%d '%s' min=%.0f -> pref=%.0f",
                        rows.indexOf(row) + 1, b.getText(), mins[idx], w));
                }
                if (Math.abs(b.getPrefWidth() - w) >= 1.0) {
                    b.setPrefWidth(w);
                    b.setMinWidth(w);
                }
            }
        }
        if (doLog) distributeDebugLogs++;
    }

    private int distributeDebugLogs = 0;

    private void bindButtonAutoScale(Button b) {
        if (b == null) return;
        b.widthProperty().addListener((obs, oldW, newW) -> scaleButtonToFit(b));
        scaleButtonToFit(b);
    }

    /** Listener body. Measure button's intrinsic pref via JavaFX's own
     *  calculation (not our approximation — they disagree), compare to
     *  allocated width, shrink font to fit. Snap pt to 0.25-pt steps
     *  + idempotent setStyle to prevent layout-feedback oscillation. */
    private void scaleButtonToFit(Button b) {
        double allocated = b.getWidth();
        if (allocated <= 0) return;
        String label = b.getText();
        if (label == null || label.isEmpty()) return;
        double reqAtBase = intrinsicAtBase(b);
        double scale = allocated / reqAtBase;
        if (scale > 1.0) scale = 1.0;
        double minScale = BUTTON_MIN_PT / BASE_FONT_PT;
        if (scale < minScale) scale = minScale;
        double pt = Math.floor(BASE_FONT_PT * scale * 4.0) / 4.0;
        String newStyle = String.format(java.util.Locale.ROOT,
            "-fx-font-size: %.2fpt;", pt);
        if (!newStyle.equals(b.getStyle())) b.setStyle(newStyle);
    }

    private static double measureTextWidth(String s, double pt, boolean bold) {
        javafx.scene.text.Font f = javafx.scene.text.Font.font(
            null,
            bold ? javafx.scene.text.FontWeight.BOLD : javafx.scene.text.FontWeight.NORMAL,
            pt);
        javafx.scene.text.Text t = new javafx.scene.text.Text(s);
        t.setFont(f);
        return t.getLayoutBounds().getWidth();
    }

    private static double rowWidth(String[] labels, double pt) {
        double pad = BUTTON_PAD_PT_COEF * pt + BUTTON_PAD_FIXED;
        double total = 0;
        for (String s : labels) total += measureTextWidth(s, pt, false) + pad;
        total += Math.max(0, labels.length - 1) * FLOW_HGAP;
        return total;
    }

    private double measuredRequiredWidth() {
        if (cachedRequiredWidth != null) return cachedRequiredWidth;
        double title = measureTextWidth(TITLE_TEXT, BASE_FONT_PT, true);
        double row1  = rowWidth(ROW1_LABELS, BASE_FONT_PT);
        double row2  = rowWidth(ROW2_LABELS, BASE_FONT_PT);
        cachedRequiredWidth = Math.max(title, Math.max(row1, row2)) + PANEL_PADDING_H;
        return cachedRequiredWidth;
    }

    private void applyFontScale(double availableWidth) {
        if (availableWidth <= 0) return;
        double scale = availableWidth / measuredRequiredWidth();
        if (scale > MAX_SCALE) scale = MAX_SCALE;
        if (scale < MIN_SCALE) scale = MIN_SCALE;
        double pt = BASE_FONT_PT * scale;

        slideSpecPanel.setStyle(String.format(java.util.Locale.ROOT,
            "-fx-padding: 5px; -fx-font-size: %.2fpt;", pt));

        // Title's inline -fx-font-weight: bold blocks cascade of
        // -fx-font-size; restate both in one style so weight doesn't
        // shadow the size.
        if (slideSpecTitle != null) {
            slideSpecTitle.setStyle(String.format(java.util.Locale.ROOT,
                "-fx-font-weight: bold; -fx-font-size: %.2fpt;", pt));
        }
    }

    /** Wire the provider for the active session's CommandInvoker.
     *  Every apply-to-slide runs goes through this — the method is
     *  part of the panel's required setup. */
    public void bindInvokerProvider(
            java.util.function.Supplier<com.excudo.core.commands.CommandInvoker> provider) {
        this.invokerProvider = provider;
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
        // Staged edits were scoped to the previous slide's baseline —
        // carrying them onto a different slide doesn't make sense. Drop
        // the working copy so render() repopulates cleanly. User must
        // apply before switching to preserve.
        staged = false;
        rows.clear();
        activeRow = -1;
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
        // If the user has a staged working copy, don't clobber it with a
        // fresh synthesis — the staged rows are the user's edit. New
        // synthesis still updates `lastResult` so Reset has fresh data.
        if (staged) {
            return;
        }
        if (slideSpecTitle != null) {
            slideSpecTitle.setText("SlideSpec: slide " + slideNumber
                + " (" + (r != null ? r.script().size() : 0) + " commands)");
        }
        rows.clear();
        activeRow = -1;
        if (r != null) {
            for (CommandSpec spec : r.script().topologicalOrder()) {
                rows.add(SpecRow.synthesized(spec));
            }
        }
        rebuildRowViews();
        if (slideSpecWarnings != null) {
            if (r == null || r.warnings().isEmpty()) {
                slideSpecWarnings.setText("");
            } else {
                slideSpecWarnings.setText("Warnings: " + String.join(" | ", r.warnings()));
            }
        }
    }

    /** Recreate the row views from the current {@code rows}, preserving
     *  each row's expanded state (it lives on the SpecRow) and the active
     *  selection. Called after every render / list-op. Also refreshes the
     *  staged badge and Reset gating. */
    private void rebuildRowViews() {
        rowViews.clear();
        if (slideSpecRows == null) return;
        slideSpecRows.getChildren().clear();
        for (int i = 0; i < rows.size(); i++) {
            SpecRowView view = new SpecRowView(rows.get(i), i, this);
            view.setActive(i == activeRow);
            rowViews.add(view);
            slideSpecRows.getChildren().add(view);
        }
        updateBadge();
        updateResetGating();
    }

    private void updateBadge() {
        if (slideSpecStagedBadge != null) {
            slideSpecStagedBadge.setText(staged ? "Staged — edits not yet applied" : "");
        }
    }

    /** Reset is interactable exactly when a staged working copy exists —
     *  i.e. the script has deviated from the synthesized baseline. */
    private void updateResetGating() {
        if (slideSpecResetButton != null) {
            slideSpecResetButton.setDisable(!staged);
        }
    }

    private void setActiveRow(int index) {
        activeRow = index;
        for (int i = 0; i < rowViews.size(); i++) {
            rowViews.get(i).setActive(i == index);
        }
    }

    // ========== SpecRowView.Host ==========

    @Override
    public void onChanged() {
        // A row committed a real edit (or a JSON edit). The script is now a
        // working copy. Do NOT rebuild the rows here — that would pull the
        // controls out from under an in-progress edit; the committing row
        // already refreshed its own header.
        staged = true;
        updateBadge();
        updateResetGating();
    }

    @Override
    public void onActivated(int index) {
        setActiveRow(index);
    }

    @Override
    public Optional<CommandSpec> editAsJson(CommandSpec current) {
        return editSpecAsJson(current);
    }

    // ========== Edit / list-ops ==========

    private void editSelected() {
        if (activeRow < 0 || activeRow >= rowViews.size()) {
            showInfo("Select a spec first (click a row), then Edit to expand it.");
            return;
        }
        rowViews.get(activeRow).setExpanded(true);
    }

    /** Modal raw-JSON editor used as the escape hatch for spec types
     *  without (or beyond) an inline form. Returns the parsed spec, or
     *  empty if the user cancelled / the JSON didn't parse. The caller
     *  (the row view) owns the write-back. */
    Optional<CommandSpec> editSpecAsJson(CommandSpec current) {
        String currentJson = CommandSpecJson.toJson(current);
        javafx.scene.control.Dialog<String> dlg = new javafx.scene.control.Dialog<>();
        dlg.setTitle("Edit spec (JSON)");
        dlg.setHeaderText("Full-fidelity JSON editor.");
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
        if (edited.isEmpty()) return Optional.empty();
        try {
            return Optional.of(CommandSpecJson.fromJson(edited.get()));
        } catch (RuntimeException ex) {
            showError("Failed to parse edited JSON: " + ex.getMessage());
            return Optional.empty();
        }
    }

    private void duplicateSelected() {
        if (activeRow < 0 || activeRow >= rows.size()) { showInfo("Select a spec first."); return; }
        CommandSpec dup = rows.get(activeRow).spec();
        rows.add(activeRow + 1, SpecRow.added(dup));
        staged = true;
        activeRow = activeRow + 1;
        rebuildRowViews();
    }

    private void deleteSelected() {
        if (activeRow < 0 || activeRow >= rows.size()) { showInfo("Select a spec first."); return; }
        rows.remove(activeRow);
        staged = true;
        activeRow = rows.isEmpty() ? -1 : Math.min(activeRow, rows.size() - 1);
        rebuildRowViews();
    }

    private void moveSelected(int delta) {
        if (activeRow < 0 || activeRow >= rows.size()) { showInfo("Select a spec first."); return; }
        int target = activeRow + delta;
        if (target < 0 || target >= rows.size()) return;
        SpecRow moved = rows.remove(activeRow);
        rows.add(target, moved);
        staged = true;
        activeRow = target;
        rebuildRowViews();
    }

    private void resetToSynthesized() {
        staged = false;
        rows.clear();
        activeRow = -1;
        render(activeSlide, lastResult);
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
        rows.clear();
        activeRow = -1;
        staged = false;
        rebuildRowViews();
        if (slideSpecTitle != null) slideSpecTitle.setText("SlideSpec (no slide selected)");
        if (slideSpecWarnings != null) slideSpecWarnings.setText("");
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
            + (staged ? "staged edits" : "synthesized") + ").");
    }

    /** Current working specs — the staged/edited rows if any, else the
     *  synthesized order. Null when no script exists for this slide yet. */
    private List<CommandSpec> currentSpecs() {
        if (rows.isEmpty()) return null;
        return rows.stream().map(SpecRow::spec).toList();
    }

    private void applyToNewSlide() {
        if (currentSpecs() == null || currentOrchestrator == null) {
            showInfo("Nothing to apply.");
            return;
        }
        com.excudo.core.commands.CommandInvoker invoker = requireInvoker();
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
            invoker.executeCommand(cmd);
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
        com.excudo.core.commands.CommandInvoker invoker = requireInvoker();
        try {
            String json = CommandSpecJson.toJsonArray(currentSpecs());
            RunSlideScriptCommand cmd = new RunSlideScriptCommand(
                target, json, currentOrchestrator, null);
            invoker.executeCommand(cmd);
            showInfo("Applied " + cmd.getAppliedSpecCount() + " spec(s) to slide " + target + ".");
        } catch (Exception ex) {
            showError("Apply failed: " + ex.getMessage());
        }
    }

    /** Hard contract: apply-to-slide must go through the invoker so the
     *  mutation lands on the undo stack. If the provider isn't wired
     *  (MainController didn't call bindInvokerProvider) or returns null
     *  (no active session), throw — silent fallback to cmd.execute()
     *  would break undo for the whole action, which is the kind of
     *  correctness gap that hides until someone hits Ctrl+Z. */
    private com.excudo.core.commands.CommandInvoker requireInvoker() {
        if (invokerProvider == null) {
            throw new IllegalStateException(
                "SlideSpecController.invokerProvider not wired — "
                + "MainController must call bindInvokerProvider before apply.");
        }
        com.excudo.core.commands.CommandInvoker inv = invokerProvider.get();
        if (inv == null) {
            throw new IllegalStateException(
                "No active CommandInvoker — can't apply without a live session.");
        }
        return inv;
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

}
