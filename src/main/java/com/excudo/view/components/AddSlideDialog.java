package com.excudo.view.components;

import com.excudo.core.model.LayoutInfo;
import com.excudo.core.orchestration.PPTXOrchestrator;
import javafx.geometry.Insets;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.util.StringConverter;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Slide-creation dialog that replaces the bare {@code TextInputDialog}
 * the Presentation Explorer was using. Asks for title, layout, and
 * insertion position. The old flow silently defaulted every new slide
 * to layout 1 regardless of the user's intent — this dialog surfaces
 * the full layout list so agents and users stop diverging on layout
 * selection.
 *
 * <p><b>Follow-up (not yet shipped):</b> per-placeholder content inputs
 * that pre-fill body / subtitle placeholders. Holding on those until
 * the placeholder-addressability story is cleaner — today,
 * {@link com.excudo.core.model.SlideShape} collapses the placeholder
 * metadata (phType, idx) into a single ShapeType enum value, so
 * matching user-provided text to the right placeholder on a freshly-
 * created slide requires DOM inspection. Cleaner to wait for Tier
 * 4.11 work (which surfaces placeholder fields as first-class state).
 */
public final class AddSlideDialog {

    private AddSlideDialog() {}

    public enum Position { BEFORE_CURRENT, AFTER_CURRENT, AT_END }

    /** What the user chose in the dialog. Immutable result payload. */
    public record Result(String title, String layoutId, Position position) {}

    /**
     * Open the dialog and return the user's selection, or
     * {@link Optional#empty()} if they cancelled or no layouts are
     * available.
     *
     * @param orchestrator live orchestrator — used to enumerate layouts
     *     for the current presentation
     * @param hasCurrentSelection true when the explorer has a selected
     *     slide; controls the default Position value (AFTER_CURRENT
     *     when a slide is selected, AT_END otherwise)
     */
    public static Optional<Result> show(PPTXOrchestrator orchestrator, boolean hasCurrentSelection) {
        List<LayoutInfo> layouts = listLayouts(orchestrator);
        if (layouts.isEmpty()) {
            return Optional.empty();
        }

        Dialog<Result> dialog = new Dialog<>();
        dialog.setTitle("Add Slide");
        dialog.setHeaderText("Create a new slide");

        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(8);
        grid.setPadding(new Insets(12));

        TextField titleField = new TextField("New Slide");
        titleField.setPrefColumnCount(22);

        ChoiceBox<LayoutInfo> layoutBox = new ChoiceBox<>();
        layoutBox.getItems().addAll(layouts);
        layoutBox.setValue(layouts.get(0));
        layoutBox.setConverter(new StringConverter<>() {
            @Override public String toString(LayoutInfo l) {
                if (l == null) return "";
                String name = l.getName() == null || l.getName().isBlank()
                    ? "Untitled layout" : l.getName();
                return l.getLayoutId() + "  —  " + name;
            }
            @Override public LayoutInfo fromString(String s) { return null; }
        });

        ChoiceBox<Position> positionBox = new ChoiceBox<>();
        positionBox.getItems().addAll(Position.values());
        positionBox.setValue(hasCurrentSelection ? Position.AFTER_CURRENT : Position.AT_END);
        positionBox.setConverter(new StringConverter<>() {
            @Override public String toString(Position p) {
                return switch (p) {
                    case BEFORE_CURRENT -> "Before current slide";
                    case AFTER_CURRENT  -> "After current slide";
                    case AT_END         -> "At end of presentation";
                };
            }
            @Override public Position fromString(String s) { return null; }
        });

        addRow(grid, 0, "Title",    titleField);
        addRow(grid, 1, "Layout",   layoutBox);
        addRow(grid, 2, "Position", positionBox);

        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dialog.setResultConverter(bt -> {
            if (bt != ButtonType.OK) return null;
            String title = titleField.getText() == null ? "" : titleField.getText().trim();
            if (title.isEmpty()) title = "New Slide";
            return new Result(title, layoutBox.getValue().getLayoutId(), positionBox.getValue());
        });

        return dialog.showAndWait();
    }

    private static List<LayoutInfo> listLayouts(PPTXOrchestrator orchestrator) {
        var ctxService = orchestrator.getContextService();
        if (ctxService == null) return List.of();
        try {
            List<LayoutInfo> layouts = ctxService.getAvailableLayoutsDetailed();
            return layouts == null ? List.of() : new ArrayList<>(layouts);
        } catch (Exception e) {
            return List.of();
        }
    }

    private static void addRow(GridPane grid, int row, String label, javafx.scene.Node control) {
        grid.add(new Label(label), 0, row);
        grid.add(control, 1, row);
    }
}
