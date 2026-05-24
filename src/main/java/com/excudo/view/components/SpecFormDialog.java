package com.excudo.view.components;

import com.excudo.core.model.ShapeGeometry;
import com.excudo.core.model.SlideShape;
import com.excudo.core.model.TransitionType;
import com.excudo.core.synthesis.spec.CommandSpec;
import javafx.geometry.Insets;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;

import java.util.List;
import java.util.Optional;

/**
 * Typed form dialogs for {@link CommandSpec} editing. Replaces the raw
 * JSON editor for common specs so GUI authoring doesn't require
 * hand-writing or pretty-printing JSON. Non-trivial specs (AddShape with
 * full style, SetText TextBody, AddAnimation with timing + effect params)
 * still fall through to the JSON path — the contract here is:
 *
 * <p>{@link #editSpec(CommandSpec)} returns:
 * <ul>
 *   <li>{@code Optional.of(newSpec)} — user confirmed a typed edit</li>
 *   <li>{@code Optional.empty()} — user cancelled OR this spec type has
 *       no typed form yet (caller should fall back to the JSON editor)</li>
 * </ul>
 *
 * <p>Callers distinguish "cancelled" from "unsupported" by peeking at
 * {@link #hasTypedForm(CommandSpec)} before calling.
 */
public final class SpecFormDialog {

    private SpecFormDialog() {}

    /** True if this spec has a bespoke form dialog; false means the
     *  caller should use the JSON editor instead.
     *
     *  <p>Exhaustive switch over the sealed {@link CommandSpec}
     *  hierarchy — if someone adds a new spec to the permits list, this
     *  method stops compiling until the author explicitly decides
     *  {@code true} (ship a form) or {@code false} (leave to JSON). */
    public static boolean hasTypedForm(CommandSpec spec) {
        return switch (spec) {
            case CommandSpec.MoveSpec m -> true;
            case CommandSpec.ResizeSpec r -> true;
            case CommandSpec.RotateSpec r -> true;
            case CommandSpec.RenameShapeSpec r -> true;
            case CommandSpec.SetTextBoxFlagSpec t -> true;
            case CommandSpec.ReorderSpec r -> true;
            case CommandSpec.RemoveShapeSpec r -> true;
            case CommandSpec.RemoveAnimationSpec r -> true;
            case CommandSpec.SetAnimationTimingSpec t -> true;
            case CommandSpec.SetTransitionSpec t -> true;
            case CommandSpec.ClearTransitionSpec c -> true;
            case CommandSpec.UngroupSpec u -> true;
            case CommandSpec.AddToGroupSpec a -> true;
            case CommandSpec.DetachFromGroupSpec d -> true;
            case CommandSpec.AddShapeSpec a -> true;
            // JSON-fallback set — sub-models too rich for a typed form today.
            case CommandSpec.SetTextSpec s -> false;
            case CommandSpec.SetShapeStyleSpec s -> false;
            case CommandSpec.SetRunFormatSpec s -> false;
            case CommandSpec.AddAnimationSpec a -> false;
            case CommandSpec.CreateGroupSpec g -> false;
            case CommandSpec.CreateCodeBoxSpec c -> false;
            case CommandSpec.CreateDiagramSpec d -> false;
        };
    }

    /** Open the appropriate typed-form dialog and return the edited spec,
     *  or empty if the user cancelled / this spec falls through to JSON.
     *  Exhaustive on the sealed hierarchy for the same reason as
     *  {@link #hasTypedForm}. */
    public static Optional<CommandSpec> editSpec(CommandSpec spec) {
        return switch (spec) {
            case CommandSpec.MoveSpec m -> editMove(m);
            case CommandSpec.ResizeSpec r -> editResize(r);
            case CommandSpec.RotateSpec r -> editRotate(r);
            case CommandSpec.RenameShapeSpec r -> editRename(r);
            case CommandSpec.SetTextBoxFlagSpec t -> editTxBoxFlag(t);
            case CommandSpec.ReorderSpec r -> editReorder(r);
            case CommandSpec.RemoveShapeSpec r -> confirmSpidAction("Remove shape", r.spid(),
                newSpid -> new CommandSpec.RemoveShapeSpec(r.slideNumber(), newSpid));
            case CommandSpec.RemoveAnimationSpec r -> editRemoveAnim(r);
            case CommandSpec.SetAnimationTimingSpec t -> editAnimTiming(t);
            case CommandSpec.SetTransitionSpec t -> editTransition(t);
            case CommandSpec.ClearTransitionSpec c -> Optional.of(c);
            case CommandSpec.UngroupSpec u -> confirmSpidAction("Ungroup", u.groupSpid(),
                newSpid -> new CommandSpec.UngroupSpec(u.slideNumber(), newSpid));
            case CommandSpec.AddToGroupSpec a -> editAddToGroup(a);
            case CommandSpec.DetachFromGroupSpec d -> confirmSpidAction("Detach from group", d.childSpid(),
                newSpid -> new CommandSpec.DetachFromGroupSpec(d.slideNumber(), newSpid));
            case CommandSpec.AddShapeSpec a -> editAddShape(a);
            // JSON-fallback — the caller checks hasTypedForm first and
            // routes these to the JSON editor rather than calling editSpec.
            case CommandSpec.SetTextSpec s -> Optional.empty();
            case CommandSpec.SetShapeStyleSpec s -> Optional.empty();
            case CommandSpec.SetRunFormatSpec s -> Optional.empty();
            case CommandSpec.AddAnimationSpec a -> Optional.empty();
            case CommandSpec.CreateGroupSpec g -> Optional.empty();
            case CommandSpec.CreateCodeBoxSpec c -> Optional.empty();
            case CommandSpec.CreateDiagramSpec d -> Optional.empty();
        };
    }

    // ====================================================================
    // Simple specs
    // ====================================================================

    private static Optional<CommandSpec> editMove(CommandSpec.MoveSpec m) {
        Dialog<CommandSpec> dlg = baseDialog("Move", "Shape position (EMU, 914400 = 1 inch)");
        GridPane grid = grid();
        TextField spidField = textField(String.valueOf(m.spid()));
        TextField xField = textField(String.valueOf(m.newX()));
        TextField yField = textField(String.valueOf(m.newY()));
        addRow(grid, 0, "SPID", spidField);
        addRow(grid, 1, "X (EMU)", xField);
        addRow(grid, 2, "Y (EMU)", yField);
        dlg.getDialogPane().setContent(grid);
        dlg.setResultConverter(bt -> bt != ButtonType.OK ? null :
            new CommandSpec.MoveSpec(m.slideNumber(), parseInt(spidField), parseLong(xField), parseLong(yField)));
        return dlg.showAndWait();
    }

    private static Optional<CommandSpec> editResize(CommandSpec.ResizeSpec r) {
        Dialog<CommandSpec> dlg = baseDialog("Resize", "Shape size (EMU, 914400 = 1 inch)");
        GridPane grid = grid();
        TextField spidField = textField(String.valueOf(r.spid()));
        TextField wField = textField(String.valueOf(r.newWidth()));
        TextField hField = textField(String.valueOf(r.newHeight()));
        addRow(grid, 0, "SPID", spidField);
        addRow(grid, 1, "Width (EMU)", wField);
        addRow(grid, 2, "Height (EMU)", hField);
        dlg.getDialogPane().setContent(grid);
        dlg.setResultConverter(bt -> bt != ButtonType.OK ? null :
            new CommandSpec.ResizeSpec(r.slideNumber(), parseInt(spidField), parseLong(wField), parseLong(hField)));
        return dlg.showAndWait();
    }

    private static Optional<CommandSpec> editRotate(CommandSpec.RotateSpec r) {
        Dialog<CommandSpec> dlg = baseDialog("Rotate", "Shape rotation (degrees)");
        GridPane grid = grid();
        TextField spidField = textField(String.valueOf(r.spid()));
        Spinner<Double> rotSpinner = new Spinner<>();
        rotSpinner.setValueFactory(new SpinnerValueFactory.DoubleSpinnerValueFactory(
            -360.0, 360.0, r.newRotationDegrees(), 1.0));
        rotSpinner.setEditable(true);
        addRow(grid, 0, "SPID", spidField);
        addRow(grid, 1, "Rotation (°)", rotSpinner);
        dlg.getDialogPane().setContent(grid);
        dlg.setResultConverter(bt -> bt != ButtonType.OK ? null :
            new CommandSpec.RotateSpec(r.slideNumber(), parseInt(spidField), rotSpinner.getValue()));
        return dlg.showAndWait();
    }

    private static Optional<CommandSpec> editRename(CommandSpec.RenameShapeSpec r) {
        Dialog<CommandSpec> dlg = baseDialog("Rename shape", "Update cNvPr/@name");
        GridPane grid = grid();
        TextField spidField = textField(String.valueOf(r.spid()));
        TextField nameField = textField(r.newName());
        addRow(grid, 0, "SPID", spidField);
        addRow(grid, 1, "New name", nameField);
        dlg.getDialogPane().setContent(grid);
        dlg.setResultConverter(bt -> bt != ButtonType.OK ? null :
            new CommandSpec.RenameShapeSpec(r.slideNumber(), parseInt(spidField), nameField.getText()));
        return dlg.showAndWait();
    }

    private static Optional<CommandSpec> editTxBoxFlag(CommandSpec.SetTextBoxFlagSpec t) {
        Dialog<CommandSpec> dlg = baseDialog("Toggle txBox flag", "cNvSpPr/@txBox marker");
        GridPane grid = grid();
        TextField spidField = textField(String.valueOf(t.spid()));
        CheckBox flagBox = new CheckBox("Text box (txBox=\"1\")");
        flagBox.setSelected(t.flag());
        addRow(grid, 0, "SPID", spidField);
        addRow(grid, 1, "", flagBox);
        dlg.getDialogPane().setContent(grid);
        dlg.setResultConverter(bt -> bt != ButtonType.OK ? null :
            new CommandSpec.SetTextBoxFlagSpec(t.slideNumber(), parseInt(spidField), flagBox.isSelected()));
        return dlg.showAndWait();
    }

    private static Optional<CommandSpec> editReorder(CommandSpec.ReorderSpec r) {
        Dialog<CommandSpec> dlg = baseDialog("Reorder z-order", "Pick direction");
        GridPane grid = grid();
        TextField spidField = textField(String.valueOf(r.spid()));
        ChoiceBox<CommandSpec.ReorderSpec.Direction> dirBox = new ChoiceBox<>();
        dirBox.getItems().addAll(CommandSpec.ReorderSpec.Direction.values());
        dirBox.setValue(r.direction());
        addRow(grid, 0, "SPID", spidField);
        addRow(grid, 1, "Direction", dirBox);
        dlg.getDialogPane().setContent(grid);
        dlg.setResultConverter(bt -> bt != ButtonType.OK ? null :
            new CommandSpec.ReorderSpec(r.slideNumber(), parseInt(spidField), dirBox.getValue()));
        return dlg.showAndWait();
    }

    private static Optional<CommandSpec> editRemoveAnim(CommandSpec.RemoveAnimationSpec r) {
        Dialog<CommandSpec> dlg = baseDialog("Remove animation", "Timing-tree cTn id");
        GridPane grid = grid();
        TextField idField = textField(String.valueOf(r.timingNodeId()));
        addRow(grid, 0, "cTn id", idField);
        dlg.getDialogPane().setContent(grid);
        dlg.setResultConverter(bt -> bt != ButtonType.OK ? null :
            new CommandSpec.RemoveAnimationSpec(r.slideNumber(), parseInt(idField)));
        return dlg.showAndWait();
    }

    private static Optional<CommandSpec> editAnimTiming(CommandSpec.SetAnimationTimingSpec t) {
        Dialog<CommandSpec> dlg = baseDialog("Animation timing", "Null fields are left unchanged");
        GridPane grid = grid();
        TextField idField = textField(String.valueOf(t.timingNodeId()));
        TextField durField = textField(t.newDuration() == null ? "" : t.newDuration());
        TextField delayField = textField(t.newDelay() == null ? "" : t.newDelay());
        addRow(grid, 0, "cTn id", idField);
        addRow(grid, 1, "Duration (ms / 'indefinite')", durField);
        addRow(grid, 2, "Delay (ms / 'indefinite')", delayField);
        dlg.getDialogPane().setContent(grid);
        dlg.setResultConverter(bt -> bt != ButtonType.OK ? null :
            new CommandSpec.SetAnimationTimingSpec(
                t.slideNumber(),
                parseInt(idField),
                blankToNull(durField.getText()),
                blankToNull(delayField.getText())));
        return dlg.showAndWait();
    }

    private static Optional<CommandSpec> editTransition(CommandSpec.SetTransitionSpec t) {
        Dialog<CommandSpec> dlg = baseDialog("Slide transition", "Inheritance handled on read side");
        GridPane grid = grid();
        ChoiceBox<TransitionType> typeBox = new ChoiceBox<>();
        typeBox.getItems().addAll(TransitionType.values());
        typeBox.setValue(t.transitionType());
        ChoiceBox<String> speedBox = new ChoiceBox<>();
        speedBox.getItems().addAll("slow", "med", "fast");
        speedBox.setValue(t.speed() == null ? "med" : t.speed());
        TextField advField = textField(t.autoAdvanceMs() == null ? "" : String.valueOf(t.autoAdvanceMs()));
        addRow(grid, 0, "Type", typeBox);
        addRow(grid, 1, "Speed", speedBox);
        addRow(grid, 2, "Auto-advance (ms)", advField);
        dlg.getDialogPane().setContent(grid);
        dlg.setResultConverter(bt -> bt != ButtonType.OK ? null :
            new CommandSpec.SetTransitionSpec(
                t.slideNumber(),
                typeBox.getValue(),
                speedBox.getValue(),
                blankToNull(advField.getText()) == null ? null : Integer.valueOf(advField.getText().trim())));
        return dlg.showAndWait();
    }

    private static Optional<CommandSpec> editAddToGroup(CommandSpec.AddToGroupSpec a) {
        Dialog<CommandSpec> dlg = baseDialog("Add to group", "Both SPIDs must differ");
        GridPane grid = grid();
        TextField groupField = textField(String.valueOf(a.groupSpid()));
        TextField childField = textField(String.valueOf(a.childSpid()));
        addRow(grid, 0, "Group SPID", groupField);
        addRow(grid, 1, "Child SPID", childField);
        dlg.getDialogPane().setContent(grid);
        dlg.setResultConverter(bt -> bt != ButtonType.OK ? null :
            new CommandSpec.AddToGroupSpec(a.slideNumber(), parseInt(groupField), parseInt(childField)));
        return dlg.showAndWait();
    }

    // ====================================================================
    // AddShape — mid-complexity form
    // ====================================================================

    /** Geometry + shape-type + name + inline text + txBox flag + align
     *  — the common subset agents touch. Full {@code ShapeStyle} edits
     *  remain in the JSON editor for now. */
    private static Optional<CommandSpec> editAddShape(CommandSpec.AddShapeSpec a) {
        Dialog<CommandSpec> dlg = baseDialog("Add shape", "Style edits fall through to JSON");
        GridPane grid = grid();
        ChoiceBox<SlideShape.ShapeType> typeBox = new ChoiceBox<>();
        typeBox.getItems().addAll(SlideShape.ShapeType.values());
        typeBox.setValue(a.shapeType());
        TextField nameField = textField(a.name() == null ? "" : a.name());
        TextField xField = textField(String.valueOf(a.geometry().getX()));
        TextField yField = textField(String.valueOf(a.geometry().getY()));
        TextField wField = textField(String.valueOf(a.geometry().getWidth()));
        TextField hField = textField(String.valueOf(a.geometry().getHeight()));
        TextField rotField = textField(String.valueOf(a.geometry().getRotationDegrees()));
        TextArea textArea = new TextArea(a.text() == null ? "" : a.text());
        textArea.setPrefRowCount(3);
        textArea.setWrapText(true);
        ChoiceBox<String> alignBox = new ChoiceBox<>();
        alignBox.getItems().addAll("", "l", "ctr", "r", "just");
        alignBox.setValue(a.alignment() == null ? "" : a.alignment());
        CheckBox txBoxCheck = new CheckBox("Text box (txBox=\"1\")");
        txBoxCheck.setSelected(a.isTextBox());
        addRow(grid, 0, "Type", typeBox);
        addRow(grid, 1, "Name", nameField);
        addRow(grid, 2, "X (EMU)", xField);
        addRow(grid, 3, "Y (EMU)", yField);
        addRow(grid, 4, "Width (EMU)", wField);
        addRow(grid, 5, "Height (EMU)", hField);
        addRow(grid, 6, "Rotation (°)", rotField);
        addRow(grid, 7, "Inline text", textArea);
        addRow(grid, 8, "Alignment", alignBox);
        addRow(grid, 9, "", txBoxCheck);
        dlg.getDialogPane().setContent(grid);
        dlg.setResultConverter(bt -> {
            if (bt != ButtonType.OK) return null;
            int rot60k = (int) Math.round(parseDouble(rotField) * 60000.0);
            ShapeGeometry geom = new ShapeGeometry(
                parseLong(xField), parseLong(yField),
                parseLong(wField), parseLong(hField),
                rot60k);
            return new CommandSpec.AddShapeSpec(
                a.slideNumber(), typeBox.getValue(), geom,
                textArea.getText(),
                blankToNull(nameField.getText()),
                a.style(),
                blankToNull(alignBox.getValue()),
                txBoxCheck.isSelected(),
                a.sourceSpidHint());
        });
        return dlg.showAndWait();
    }

    // ====================================================================
    // Helpers
    // ====================================================================

    private interface SpidRebuilder<T extends CommandSpec> {
        T build(int spid);
    }

    private static <T extends CommandSpec> Optional<CommandSpec> confirmSpidAction(
            String title, int spid, SpidRebuilder<T> builder) {
        Dialog<CommandSpec> dlg = baseDialog(title, "Target SPID");
        GridPane grid = grid();
        TextField spidField = textField(String.valueOf(spid));
        addRow(grid, 0, "SPID", spidField);
        dlg.getDialogPane().setContent(grid);
        dlg.setResultConverter(bt -> bt != ButtonType.OK ? null : builder.build(parseInt(spidField)));
        return dlg.showAndWait();
    }

    private static Dialog<CommandSpec> baseDialog(String title, String header) {
        Dialog<CommandSpec> dlg = new Dialog<>();
        dlg.setTitle(title);
        dlg.setHeaderText(header);
        dlg.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        return dlg;
    }

    private static GridPane grid() {
        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(6);
        grid.setPadding(new Insets(12, 12, 12, 12));
        return grid;
    }

    private static TextField textField(String initial) {
        TextField tf = new TextField(initial);
        tf.setPrefColumnCount(18);
        return tf;
    }

    private static void addRow(GridPane grid, int row, String label, javafx.scene.Node control) {
        grid.add(new Label(label), 0, row);
        grid.add(control, 1, row);
    }

    private static int parseInt(TextField tf) {
        return Integer.parseInt(tf.getText().trim());
    }

    private static long parseLong(TextField tf) {
        return Long.parseLong(tf.getText().trim());
    }

    private static double parseDouble(TextField tf) {
        return Double.parseDouble(tf.getText().trim());
    }

    private static String blankToNull(String s) {
        if (s == null) return null;
        String trimmed = s.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
