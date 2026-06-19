package com.excudo.view.components;

import com.excudo.core.model.AnimationBinding;
import com.excudo.core.model.AnimationType;
import com.excudo.core.model.BlipRef;
import com.excudo.core.model.ShapeFill;
import com.excudo.core.model.ShapeGeometry;
import com.excudo.core.model.ShapeStyle;
import com.excudo.core.model.SlideShape;
import com.excudo.core.model.TextBody;
import com.excudo.core.model.TextColor;
import com.excudo.core.model.TextParagraph;
import com.excudo.core.model.TextRun;
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
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.IntFunction;

/**
 * Typed forms for {@link CommandSpec} editing. The form-building logic is
 * factored into {@link #buildForm(CommandSpec)} -> {@link SpecForm} so the
 * exact same controls + reader feed two surfaces:
 * <ul>
 *   <li>the inline expandable row ({@code SpecRowView}), and</li>
 *   <li>the modal dialog ({@link #editSpec(CommandSpec)}), which is just
 *       {@link #showFormDialog} wrapping a {@code SpecForm}.</li>
 * </ul>
 *
 * <p>Non-trivial specs (AddShape with full style, SetText TextBody,
 * AddAnimation with timing + effect params) currently have no typed form
 * and fall through to the JSON editor. {@link #editSpec(CommandSpec)}
 * returns:
 * <ul>
 *   <li>{@code Optional.of(newSpec)} — user confirmed a typed edit</li>
 *   <li>{@code Optional.empty()} — user cancelled OR this spec type has no
 *       typed form yet (caller should fall back to the JSON editor)</li>
 * </ul>
 *
 * <p>Callers distinguish "cancelled" from "unsupported" by peeking at
 * {@link #hasTypedForm(CommandSpec)} before calling.
 */
public final class SpecFormDialog {

    private SpecFormDialog() {}

    /** True if this spec has a bespoke form; false means the caller should
     *  use the JSON editor instead.
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
            // AddConnector carries endpoint bindings (startSpid/endSpid
            // pairs, headEnd/tailEnd, customPath) — a JSON editor is the
            // right surface until someone needs a richer typed form.
            case CommandSpec.AddConnectorSpec c -> false;
            // AddPicture carries a BlipRef sub-model — JSON editor for now.
            case CommandSpec.AddPictureSpec p -> false;
        };
    }

    /** Build the editable form (controls + reader) for a spec, or empty
     *  for spec types that still route to the JSON editor.
     *
     *  <p>This is the single source of form-building truth shared by the
     *  inline row and the modal dialog. Exhaustive on the sealed hierarchy
     *  for the same reason as {@link #hasTypedForm}: a new spec breaks
     *  compilation here until someone wires it. */
    public static Optional<SpecForm> buildForm(CommandSpec spec) {
        return switch (spec) {
            case CommandSpec.MoveSpec m -> Optional.of(buildMoveForm(m));
            case CommandSpec.ResizeSpec r -> Optional.of(buildResizeForm(r));
            case CommandSpec.RotateSpec r -> Optional.of(buildRotateForm(r));
            case CommandSpec.RenameShapeSpec r -> Optional.of(buildRenameForm(r));
            case CommandSpec.SetTextBoxFlagSpec t -> Optional.of(buildTxBoxFlagForm(t));
            case CommandSpec.ReorderSpec r -> Optional.of(buildReorderForm(r));
            case CommandSpec.RemoveShapeSpec r -> Optional.of(buildSpidForm(r.spid(),
                s -> new CommandSpec.RemoveShapeSpec(r.slideNumber(), s)));
            case CommandSpec.RemoveAnimationSpec r -> Optional.of(buildRemoveAnimForm(r));
            case CommandSpec.SetAnimationTimingSpec t -> Optional.of(buildAnimTimingForm(t));
            case CommandSpec.SetTransitionSpec t -> Optional.of(buildTransitionForm(t));
            case CommandSpec.ClearTransitionSpec c -> Optional.of(buildClearTransitionForm(c));
            case CommandSpec.UngroupSpec u -> Optional.of(buildSpidForm(u.groupSpid(),
                s -> new CommandSpec.UngroupSpec(u.slideNumber(), s)));
            case CommandSpec.AddToGroupSpec a -> Optional.of(buildAddToGroupForm(a));
            case CommandSpec.DetachFromGroupSpec d -> Optional.of(buildSpidForm(d.childSpid(),
                s -> new CommandSpec.DetachFromGroupSpec(d.slideNumber(), s)));
            case CommandSpec.AddShapeSpec a -> Optional.of(buildAddShapeForm(a));
            case CommandSpec.SetRunFormatSpec s -> Optional.of(buildSetRunFormatForm(s));
            case CommandSpec.CreateGroupSpec g -> Optional.of(buildCreateGroupForm(g));
            case CommandSpec.CreateCodeBoxSpec c -> Optional.of(buildCreateCodeBoxForm(c));
            case CommandSpec.CreateDiagramSpec d -> Optional.of(buildCreateDiagramForm(d));
            case CommandSpec.AddConnectorSpec c -> Optional.of(buildAddConnectorForm(c));
            case CommandSpec.AddPictureSpec p -> Optional.of(buildAddPictureForm(p));
            // Nested sub-models — common fields inline + an Advanced-JSON
            // hatch (see supportsAdvancedJson) for full fidelity.
            case CommandSpec.SetTextSpec s -> Optional.of(buildSetTextForm(s));
            case CommandSpec.SetShapeStyleSpec s -> Optional.of(buildSetShapeStyleForm(s));
            case CommandSpec.AddAnimationSpec a -> Optional.of(buildAddAnimationForm(a));
        };
    }

    /** True for specs whose inline form exposes only a common subset of a
     *  rich nested sub-model (TextBody / ShapeStyle / AnimationBinding); the
     *  row offers an "Advanced (JSON)…" hatch alongside the inline fields so
     *  full fidelity is always reachable. */
    public static boolean supportsAdvancedJson(CommandSpec spec) {
        return switch (spec) {
            case CommandSpec.SetTextSpec s -> true;
            case CommandSpec.SetShapeStyleSpec s -> true;
            case CommandSpec.AddAnimationSpec a -> true;
            default -> false;
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
            case CommandSpec.AddConnectorSpec c -> Optional.empty();
            case CommandSpec.AddPictureSpec p -> Optional.empty();
        };
    }

    /** Wrap a {@link SpecForm} in an OK/Cancel modal dialog. The reader
     *  fires only on OK; a parse/validation throw there propagates as the
     *  dialog failing to produce a result (same as before the refactor). */
    private static Optional<CommandSpec> showFormDialog(String title, String header, SpecForm form) {
        Dialog<CommandSpec> dlg = baseDialog(title, header);
        dlg.getDialogPane().setContent(form.node());
        dlg.setResultConverter(bt -> bt != ButtonType.OK ? null : form.read().get());
        return dlg.showAndWait();
    }

    // ====================================================================
    // Simple specs
    // ====================================================================

    private static SpecForm buildMoveForm(CommandSpec.MoveSpec m) {
        GridPane grid = grid();
        TextField spidField = textField(String.valueOf(m.spid()));
        TextField xField = textField(String.valueOf(m.newX()));
        TextField yField = textField(String.valueOf(m.newY()));
        addRow(grid, 0, "SPID", spidField);
        addRow(grid, 1, "X (EMU)", xField);
        addRow(grid, 2, "Y (EMU)", yField);
        return new SpecForm(grid, () ->
            new CommandSpec.MoveSpec(m.slideNumber(), parseInt(spidField), parseLong(xField), parseLong(yField)));
    }

    private static Optional<CommandSpec> editMove(CommandSpec.MoveSpec m) {
        return showFormDialog("Move", "Shape position (EMU, 914400 = 1 inch)", buildMoveForm(m));
    }

    private static SpecForm buildResizeForm(CommandSpec.ResizeSpec r) {
        GridPane grid = grid();
        TextField spidField = textField(String.valueOf(r.spid()));
        TextField wField = textField(String.valueOf(r.newWidth()));
        TextField hField = textField(String.valueOf(r.newHeight()));
        addRow(grid, 0, "SPID", spidField);
        addRow(grid, 1, "Width (EMU)", wField);
        addRow(grid, 2, "Height (EMU)", hField);
        return new SpecForm(grid, () ->
            new CommandSpec.ResizeSpec(r.slideNumber(), parseInt(spidField), parseLong(wField), parseLong(hField)));
    }

    private static Optional<CommandSpec> editResize(CommandSpec.ResizeSpec r) {
        return showFormDialog("Resize", "Shape size (EMU, 914400 = 1 inch)", buildResizeForm(r));
    }

    private static SpecForm buildRotateForm(CommandSpec.RotateSpec r) {
        GridPane grid = grid();
        TextField spidField = textField(String.valueOf(r.spid()));
        Spinner<Double> rotSpinner = new Spinner<>();
        rotSpinner.setValueFactory(new SpinnerValueFactory.DoubleSpinnerValueFactory(
            -360.0, 360.0, r.newRotationDegrees(), 1.0));
        rotSpinner.setEditable(true);
        addRow(grid, 0, "SPID", spidField);
        addRow(grid, 1, "Rotation (°)", rotSpinner);
        return new SpecForm(grid, () ->
            new CommandSpec.RotateSpec(r.slideNumber(), parseInt(spidField), rotSpinner.getValue()));
    }

    private static Optional<CommandSpec> editRotate(CommandSpec.RotateSpec r) {
        return showFormDialog("Rotate", "Shape rotation (degrees)", buildRotateForm(r));
    }

    private static SpecForm buildRenameForm(CommandSpec.RenameShapeSpec r) {
        GridPane grid = grid();
        TextField spidField = textField(String.valueOf(r.spid()));
        TextField nameField = textField(r.newName());
        addRow(grid, 0, "SPID", spidField);
        addRow(grid, 1, "New name", nameField);
        return new SpecForm(grid, () ->
            new CommandSpec.RenameShapeSpec(r.slideNumber(), parseInt(spidField), nameField.getText()));
    }

    private static Optional<CommandSpec> editRename(CommandSpec.RenameShapeSpec r) {
        return showFormDialog("Rename shape", "Update cNvPr/@name", buildRenameForm(r));
    }

    private static SpecForm buildTxBoxFlagForm(CommandSpec.SetTextBoxFlagSpec t) {
        GridPane grid = grid();
        TextField spidField = textField(String.valueOf(t.spid()));
        CheckBox flagBox = new CheckBox("Text box (txBox=\"1\")");
        flagBox.setSelected(t.flag());
        addRow(grid, 0, "SPID", spidField);
        addRow(grid, 1, "", flagBox);
        return new SpecForm(grid, () ->
            new CommandSpec.SetTextBoxFlagSpec(t.slideNumber(), parseInt(spidField), flagBox.isSelected()));
    }

    private static Optional<CommandSpec> editTxBoxFlag(CommandSpec.SetTextBoxFlagSpec t) {
        return showFormDialog("Toggle txBox flag", "cNvSpPr/@txBox marker", buildTxBoxFlagForm(t));
    }

    private static SpecForm buildReorderForm(CommandSpec.ReorderSpec r) {
        GridPane grid = grid();
        TextField spidField = textField(String.valueOf(r.spid()));
        ChoiceBox<CommandSpec.ReorderSpec.Direction> dirBox = new ChoiceBox<>();
        dirBox.getItems().addAll(CommandSpec.ReorderSpec.Direction.values());
        dirBox.setValue(r.direction());
        addRow(grid, 0, "SPID", spidField);
        addRow(grid, 1, "Direction", dirBox);
        return new SpecForm(grid, () ->
            new CommandSpec.ReorderSpec(r.slideNumber(), parseInt(spidField), dirBox.getValue()));
    }

    private static Optional<CommandSpec> editReorder(CommandSpec.ReorderSpec r) {
        return showFormDialog("Reorder z-order", "Pick direction", buildReorderForm(r));
    }

    private static SpecForm buildRemoveAnimForm(CommandSpec.RemoveAnimationSpec r) {
        GridPane grid = grid();
        TextField idField = textField(String.valueOf(r.timingNodeId()));
        addRow(grid, 0, "cTn id", idField);
        return new SpecForm(grid, () ->
            new CommandSpec.RemoveAnimationSpec(r.slideNumber(), parseInt(idField)));
    }

    private static Optional<CommandSpec> editRemoveAnim(CommandSpec.RemoveAnimationSpec r) {
        return showFormDialog("Remove animation", "Timing-tree cTn id", buildRemoveAnimForm(r));
    }

    private static SpecForm buildAnimTimingForm(CommandSpec.SetAnimationTimingSpec t) {
        GridPane grid = grid();
        TextField idField = textField(String.valueOf(t.timingNodeId()));
        TextField durField = textField(t.newDuration() == null ? "" : t.newDuration());
        TextField delayField = textField(t.newDelay() == null ? "" : t.newDelay());
        addRow(grid, 0, "cTn id", idField);
        addRow(grid, 1, "Duration (ms / 'indefinite')", durField);
        addRow(grid, 2, "Delay (ms / 'indefinite')", delayField);
        return new SpecForm(grid, () ->
            new CommandSpec.SetAnimationTimingSpec(
                t.slideNumber(),
                parseInt(idField),
                blankToNull(durField.getText()),
                blankToNull(delayField.getText())));
    }

    private static Optional<CommandSpec> editAnimTiming(CommandSpec.SetAnimationTimingSpec t) {
        return showFormDialog("Animation timing", "Null fields are left unchanged", buildAnimTimingForm(t));
    }

    private static SpecForm buildTransitionForm(CommandSpec.SetTransitionSpec t) {
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
        return new SpecForm(grid, () ->
            new CommandSpec.SetTransitionSpec(
                t.slideNumber(),
                typeBox.getValue(),
                speedBox.getValue(),
                blankToNull(advField.getText()) == null ? null : Integer.valueOf(advField.getText().trim())));
    }

    private static Optional<CommandSpec> editTransition(CommandSpec.SetTransitionSpec t) {
        return showFormDialog("Slide transition", "Inheritance handled on read side", buildTransitionForm(t));
    }

    private static SpecForm buildAddToGroupForm(CommandSpec.AddToGroupSpec a) {
        GridPane grid = grid();
        TextField groupField = textField(String.valueOf(a.groupSpid()));
        TextField childField = textField(String.valueOf(a.childSpid()));
        addRow(grid, 0, "Group SPID", groupField);
        addRow(grid, 1, "Child SPID", childField);
        return new SpecForm(grid, () ->
            new CommandSpec.AddToGroupSpec(a.slideNumber(), parseInt(groupField), parseInt(childField)));
    }

    private static Optional<CommandSpec> editAddToGroup(CommandSpec.AddToGroupSpec a) {
        return showFormDialog("Add to group", "Both SPIDs must differ", buildAddToGroupForm(a));
    }

    /** A one-field SPID form, shared by RemoveShape / Ungroup / Detach. The
     *  {@code rebuild} turns the parsed SPID back into the concrete spec. */
    private static SpecForm buildSpidForm(int spid, IntFunction<CommandSpec> rebuild) {
        GridPane grid = grid();
        TextField spidField = textField(String.valueOf(spid));
        addRow(grid, 0, "SPID", spidField);
        return new SpecForm(grid, () -> rebuild.apply(parseInt(spidField)));
    }

    /** ClearTransition has no editable parameters; its form is a notice so
     *  the inline row still expands to something meaningful. */
    private static SpecForm buildClearTransitionForm(CommandSpec.ClearTransitionSpec c) {
        GridPane grid = grid();
        addRow(grid, 0, "", new Label("Clears the slide-level transition — no parameters."));
        return new SpecForm(grid, () -> c);
    }

    private static Optional<CommandSpec> confirmSpidAction(String title, int spid, IntFunction<CommandSpec> rebuild) {
        return showFormDialog(title, "Target SPID", buildSpidForm(spid, rebuild));
    }

    // ====================================================================
    // AddShape — mid-complexity form
    // ====================================================================

    /** Geometry + shape-type + name + inline text + txBox flag + align
     *  — the common subset agents touch. Full {@code ShapeStyle} edits
     *  remain in the JSON editor for now; the original {@code style} and
     *  {@code sourceSpidHint} are carried through unchanged. */
    private static SpecForm buildAddShapeForm(CommandSpec.AddShapeSpec a) {
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
        return new SpecForm(grid, () -> {
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
    }

    private static Optional<CommandSpec> editAddShape(CommandSpec.AddShapeSpec a) {
        return showFormDialog("Add shape", "Style edits fall through to JSON", buildAddShapeForm(a));
    }

    // ====================================================================
    // Flattenable rich specs (scalar / enum / string fields)
    // ====================================================================

    /** One text run's formatting. The common fields are exposed; the rest
     *  (strikethrough, highlight, language, caps, baseline, spacing, kerning,
     *  math) are carried through from the original run so the edit is not
     *  lossy. */
    private static SpecForm buildSetRunFormatForm(CommandSpec.SetRunFormatSpec rf) {
        TextRun r = rf.newRun();
        GridPane grid = grid();
        TextField spidField = textField(String.valueOf(rf.spid()));
        TextField paraField = textField(String.valueOf(rf.paragraphIdx()));
        TextField runField = textField(String.valueOf(rf.runIdx()));
        TextField textField = textField(r.getText() == null ? "" : r.getText());
        CheckBox boldBox = triState(r.getBold(), "Bold");
        CheckBox italicBox = triState(r.getItalic(), "Italic");
        TextField underlineField = textField(r.getUnderline() == null ? "" : r.getUnderline());
        TextField sizeField = textField(hundredthsToPt(r.getFontSize()));
        TextField fontField = textField(r.getFontFamily() == null ? "" : r.getFontFamily());
        TextField colorField = textField(colorToField(r.getColor()));
        addRow(grid, 0, "SPID", spidField);
        addRow(grid, 1, "Paragraph idx", paraField);
        addRow(grid, 2, "Run idx", runField);
        addRow(grid, 3, "Text", textField);
        addRow(grid, 4, "", boldBox);
        addRow(grid, 5, "", italicBox);
        addRow(grid, 6, "Underline (e.g. sng)", underlineField);
        addRow(grid, 7, "Font size (pt)", sizeField);
        addRow(grid, 8, "Font family", fontField);
        addRow(grid, 9, "Color (hex or scheme:NAME)", colorField);
        return new SpecForm(grid, () -> {
            TextRun.Builder b = TextRun.builder(textField.getText());
            Boolean bold = readTriState(boldBox); if (bold != null) b.bold(bold);
            Boolean italic = readTriState(italicBox); if (italic != null) b.italic(italic);
            String und = blankToNull(underlineField.getText()); if (und != null) b.underline(und);
            Integer fs = ptToHundredths(sizeField.getText()); if (fs != null) b.fontSize(fs);
            String fam = blankToNull(fontField.getText()); if (fam != null) b.fontFamily(fam);
            TextColor col = fieldToColor(colorField.getText()); if (col != null) b.color(col);
            carryUnexposedRunFields(b, r);
            return new CommandSpec.SetRunFormatSpec(rf.slideNumber(), parseInt(spidField),
                parseInt(paraField), parseInt(runField), b.build());
        });
    }

    private static SpecForm buildCreateGroupForm(CommandSpec.CreateGroupSpec g) {
        GridPane grid = grid();
        TextField nameField = textField(g.groupName() == null ? "" : g.groupName());
        TextField childField = textField(joinInts(g.childSpids()));
        addRow(grid, 0, "Group name", nameField);
        addRow(grid, 1, "Child SPIDs (comma-sep)", childField);
        return new SpecForm(grid, () ->
            new CommandSpec.CreateGroupSpec(g.slideNumber(), parseIntList(childField.getText()), nameField.getText()));
    }

    private static SpecForm buildCreateCodeBoxForm(CommandSpec.CreateCodeBoxSpec c) {
        GridPane grid = grid();
        TextField langField = textField(c.language() == null ? "" : c.language());
        TextArea codeArea = new TextArea(c.code() == null ? "" : c.code());
        codeArea.setPrefRowCount(5);
        TextField xField = textField(String.valueOf(c.x()));
        TextField yField = textField(String.valueOf(c.y()));
        TextField wField = textField(c.width() == null ? "" : String.valueOf(c.width()));
        TextField hField = textField(c.height() == null ? "" : String.valueOf(c.height()));
        TextField lineColorField = textField(c.lineNumberColor() == null ? "" : c.lineNumberColor());
        addRow(grid, 0, "Language", langField);
        addRow(grid, 1, "Code", codeArea);
        addRow(grid, 2, "X (EMU)", xField);
        addRow(grid, 3, "Y (EMU)", yField);
        addRow(grid, 4, "Width (EMU, blank=auto)", wField);
        addRow(grid, 5, "Height (EMU, blank=auto)", hField);
        addRow(grid, 6, "Line# color (hex, blank=default)", lineColorField);
        return new SpecForm(grid, () ->
            new CommandSpec.CreateCodeBoxSpec(c.slideNumber(), langField.getText(), codeArea.getText(),
                parseLong(xField), parseLong(yField),
                blankToNullLong(wField), blankToNullLong(hField),
                blankToNull(lineColorField.getText()), c.sourceSpidHint()));
    }

    private static SpecForm buildCreateDiagramForm(CommandSpec.CreateDiagramSpec d) {
        GridPane grid = grid();
        TextArea srcArea = new TextArea(d.mermaidSource() == null ? "" : d.mermaidSource());
        srcArea.setPrefRowCount(5);
        TextField xField = textField(d.x() == null ? "" : String.valueOf(d.x()));
        TextField yField = textField(d.y() == null ? "" : String.valueOf(d.y()));
        TextField wField = textField(d.width() == null ? "" : String.valueOf(d.width()));
        TextField hField = textField(d.height() == null ? "" : String.valueOf(d.height()));
        addRow(grid, 0, "Mermaid source", srcArea);
        addRow(grid, 1, "X (EMU, blank=default)", xField);
        addRow(grid, 2, "Y (EMU, blank=default)", yField);
        addRow(grid, 3, "Width (EMU, blank=default)", wField);
        addRow(grid, 4, "Height (EMU, blank=default)", hField);
        return new SpecForm(grid, () ->
            new CommandSpec.CreateDiagramSpec(d.slideNumber(), srcArea.getText(),
                blankToNullLong(xField), blankToNullLong(yField),
                blankToNullLong(wField), blankToNullLong(hField), d.sourceSpidHint()));
    }

    private static SpecForm buildAddConnectorForm(CommandSpec.AddConnectorSpec c) {
        ShapeGeometry g = c.geometry();
        GridPane grid = grid();
        TextField typeField = textField(c.connectorType() == null ? "" : c.connectorType());
        TextField nameField = textField(c.name() == null ? "" : c.name());
        TextField xField = textField(String.valueOf(g.getX()));
        TextField yField = textField(String.valueOf(g.getY()));
        TextField wField = textField(String.valueOf(g.getWidth()));
        TextField hField = textField(String.valueOf(g.getHeight()));
        TextField rotField = textField(String.valueOf(g.getRotationDegrees()));
        TextField headField = textField(c.headEnd() == null ? "" : c.headEnd());
        TextField tailField = textField(c.tailEnd() == null ? "" : c.tailEnd());
        TextField lineColorField = textField(c.lineColor() == null ? "" : c.lineColor());
        TextField startSpidField = textField(c.startSpid() == null ? "" : String.valueOf(c.startSpid()));
        TextField startIdxField = textField(c.startIdx() == null ? "" : String.valueOf(c.startIdx()));
        TextField endSpidField = textField(c.endSpid() == null ? "" : String.valueOf(c.endSpid()));
        TextField endIdxField = textField(c.endIdx() == null ? "" : String.valueOf(c.endIdx()));
        TextField pathField = textField(c.customPath() == null ? "" : c.customPath());
        addRow(grid, 0, "Type (line/straight/elbow/curved)", typeField);
        addRow(grid, 1, "Name", nameField);
        addRow(grid, 2, "X (EMU)", xField);
        addRow(grid, 3, "Y (EMU)", yField);
        addRow(grid, 4, "Width (EMU)", wField);
        addRow(grid, 5, "Height (EMU)", hField);
        addRow(grid, 6, "Rotation (°)", rotField);
        addRow(grid, 7, "Head end", headField);
        addRow(grid, 8, "Tail end", tailField);
        addRow(grid, 9, "Line color (hex)", lineColorField);
        addRow(grid, 10, "Start SPID", startSpidField);
        addRow(grid, 11, "Start idx", startIdxField);
        addRow(grid, 12, "End SPID", endSpidField);
        addRow(grid, 13, "End idx", endIdxField);
        addRow(grid, 14, "Custom path", pathField);
        return new SpecForm(grid, () -> {
            int rot60k = (int) Math.round(parseDouble(rotField) * 60000.0);
            ShapeGeometry geom = new ShapeGeometry(parseLong(xField), parseLong(yField),
                parseLong(wField), parseLong(hField), rot60k);
            return new CommandSpec.AddConnectorSpec(c.slideNumber(),
                blankToNull(typeField.getText()), geom,
                blankToNull(headField.getText()), blankToNull(tailField.getText()),
                blankToNull(lineColorField.getText()),
                blankToNullInteger(startSpidField), blankToNullInteger(startIdxField),
                blankToNullInteger(endSpidField), blankToNullInteger(endIdxField),
                blankToNull(pathField.getText()), blankToNull(nameField.getText()),
                c.sourceSpidHint());
        });
    }

    private static SpecForm buildAddPictureForm(CommandSpec.AddPictureSpec p) {
        BlipRef blip = p.blipRef();
        ShapeGeometry g = p.geometry();
        GridPane grid = grid();
        TextField mediaField = textField(blip.mediaPartName());
        TextField mimeField = textField(blip.mimeType() == null ? "" : blip.mimeType());
        TextField srcRectField = textField(blip.srcRect() == null ? "" : blip.srcRect());
        TextField nameField = textField(p.name() == null ? "" : p.name());
        TextField xField = textField(String.valueOf(g.getX()));
        TextField yField = textField(String.valueOf(g.getY()));
        TextField wField = textField(String.valueOf(g.getWidth()));
        TextField hField = textField(String.valueOf(g.getHeight()));
        TextField rotField = textField(String.valueOf(g.getRotationDegrees()));
        addRow(grid, 0, "Media part", mediaField);
        addRow(grid, 1, "MIME type (blank=infer)", mimeField);
        addRow(grid, 2, "srcRect (l,t,r,b ×1000)", srcRectField);
        addRow(grid, 3, "Name", nameField);
        addRow(grid, 4, "X (EMU)", xField);
        addRow(grid, 5, "Y (EMU)", yField);
        addRow(grid, 6, "Width (EMU)", wField);
        addRow(grid, 7, "Height (EMU)", hField);
        addRow(grid, 8, "Rotation (°)", rotField);
        return new SpecForm(grid, () -> {
            int rot60k = (int) Math.round(parseDouble(rotField) * 60000.0);
            ShapeGeometry geom = new ShapeGeometry(parseLong(xField), parseLong(yField),
                parseLong(wField), parseLong(hField), rot60k);
            BlipRef ref = new BlipRef(mediaField.getText(),
                blankToNull(mimeField.getText()), blankToNull(srcRectField.getText()));
            return new CommandSpec.AddPictureSpec(p.slideNumber(), ref, geom,
                blankToNull(nameField.getText()), p.sourceSpidHint());
        });
    }

    // ====================================================================
    // Nested specs — common fields inline + Advanced-JSON hatch
    // ====================================================================

    /** Text body as plain text. Editing the text rebuilds via markdown-aware
     *  {@link TextBody#fromPlainText}; leaving it untouched keeps the original
     *  body verbatim (so per-run formatting survives unless you edit here).
     *  Advanced-JSON edits the full paragraph/run tree. */
    private static SpecForm buildSetTextForm(CommandSpec.SetTextSpec st) {
        GridPane grid = grid();
        TextField spidField = textField(String.valueOf(st.spid()));
        String originalPlain = extractPlainText(st.textBody());
        TextArea textArea = new TextArea(originalPlain);
        textArea.setPrefRowCount(5);
        textArea.setWrapText(true);
        textArea.setTooltip(new javafx.scene.control.Tooltip(
            "Markdown: **bold**, *italic*, '- ' bullets. Per-run fonts/colors are "
            + "preserved only if you don't edit the text here — use Advanced (JSON) "
            + "for full formatting control."));
        addRow(grid, 0, "SPID", spidField);
        addRow(grid, 1, "Text", textArea);
        return new SpecForm(grid, () -> {
            String current = textArea.getText();
            TextBody body = current.equals(originalPlain)
                ? st.textBody() // untouched -> keep full-fidelity original
                : TextBody.fromPlainText(current, st.textBody().isPlaceholder());
            return new CommandSpec.SetTextSpec(st.slideNumber(), parseInt(spidField), body);
        });
    }

    /** Shape style: the FILL round-trips faithfully inline (type/color/alpha);
     *  the line + theme-style ref are carried verbatim and edited via
     *  Advanced-JSON, because {@code ShapeLine} can't be rebuilt with an
     *  inherited (null) width through its public factories. */
    private static SpecForm buildSetShapeStyleForm(CommandSpec.SetShapeStyleSpec ss) {
        ShapeStyle style = ss.style();
        ShapeFill fill = style.getFill();
        GridPane grid = grid();
        TextField spidField = textField(String.valueOf(ss.spid()));
        ChoiceBox<String> fillTypeBox = new ChoiceBox<>();
        fillTypeBox.getItems().addAll("(inherit)", "SOLID", "NO_FILL", "GRADIENT");
        fillTypeBox.setValue(fill == null ? "(inherit)" : fill.getType().name());
        TextField colorField = textField(fill == null ? "" : colorToField(fill.getColor()));
        TextField alphaField = textField(
            fill == null || fill.getAlphaPercent() == null ? "" : String.valueOf(fill.getAlphaPercent()));
        addRow(grid, 0, "SPID", spidField);
        addRow(grid, 1, "Fill type", fillTypeBox);
        addRow(grid, 2, "Fill color (hex or scheme:NAME)", colorField);
        addRow(grid, 3, "Fill opacity % (blank=opaque)", alphaField);
        addRow(grid, 4, "", new Label("Line / theme: edit via Advanced (JSON)."));
        return new SpecForm(grid, () -> {
            ShapeFill newFill = readFill(fillTypeBox.getValue(), colorField, alphaField, fill);
            ShapeStyle newStyle = ShapeStyle.of(newFill, style.getLine(), style.getThemeStyle());
            return new CommandSpec.SetShapeStyleSpec(ss.slideNumber(), parseInt(spidField), newStyle);
        });
    }

    private static ShapeFill readFill(String type, TextField colorField, TextField alphaField, ShapeFill original) {
        switch (type) {
            case "(inherit)":
                return null;
            case "NO_FILL":
                return ShapeFill.noFill();
            case "GRADIENT":
                // Gradient is a model stub (no stops yet). Nothing faithful to
                // build, so preserve whatever was there; full edit via JSON.
                return original;
            case "SOLID":
            default:
                TextColor col = fieldToColor(colorField.getText());
                ShapeFill base;
                if (col != null) base = ShapeFill.solid(col);
                else if (original != null && original.getColor() != null) base = ShapeFill.solid(original.getColor());
                else base = ShapeFill.solid("000000");
                return base.withAlphaPercent(blankToNullInteger(alphaField));
        }
    }

    /** Animation: the common effect + timing fields inline; everything else
     *  (motion path, effect params, paragraph targeting, easing) is carried
     *  through {@link AnimationBinding#builder(AnimationBinding)} and edited
     *  via Advanced-JSON. {@code AnimationBinding} has identity equality, so
     *  an untouched form returns the ORIGINAL spec to avoid false dirtying. */
    private static SpecForm buildAddAnimationForm(CommandSpec.AddAnimationSpec a) {
        AnimationBinding o = a.binding();
        GridPane grid = grid();
        TextField targetField = textField(String.valueOf(o.getTargetSpid()));
        ChoiceBox<AnimationType> typeBox = new ChoiceBox<>();
        typeBox.getItems().addAll(AnimationType.values());
        typeBox.setValue(o.getAnimationType());
        ChoiceBox<String> transBox = new ChoiceBox<>();
        transBox.getItems().addAll("in", "out", "emphasis");
        String origTrans = o.getTransition() == null ? "in" : o.getTransition();
        transBox.setValue(origTrans);
        TextField durField = textField(o.getDuration() == null ? "" : o.getDuration());
        TextField delayField = textField(o.getDelay() == null ? "" : o.getDelay());
        TextField clickField = textField(String.valueOf(o.getClickTrigger()));
        addRow(grid, 0, "Target SPID", targetField);
        addRow(grid, 1, "Effect", typeBox);
        addRow(grid, 2, "Transition", transBox);
        addRow(grid, 3, "Duration (ms)", durField);
        addRow(grid, 4, "Delay (ms)", delayField);
        addRow(grid, 5, "Click trigger (1+=click, 0=with, -1=after)", clickField);
        return new SpecForm(grid, () -> {
            int target = parseInt(targetField);
            AnimationType type = typeBox.getValue();
            String trans = transBox.getValue();
            int click = parseInt(clickField);
            boolean unchanged = target == o.getTargetSpid()
                && type == o.getAnimationType()
                && java.util.Objects.equals(trans, origTrans)
                && durField.getText().equals(o.getDuration() == null ? "" : o.getDuration())
                && delayField.getText().equals(o.getDelay() == null ? "" : o.getDelay())
                && click == o.getClickTrigger();
            if (unchanged) return a; // keep original instance (identity equality)
            AnimationBinding.Builder b = AnimationBinding.builder(o).target(target).clickTrigger(click);
            if (type != null) b.type(type);
            applyTransition(b, trans);
            String dur = blankToNull(durField.getText()); if (dur != null) b.duration(dur);
            String delay = blankToNull(delayField.getText()); if (delay != null) b.delay(delay);
            return new CommandSpec.AddAnimationSpec(a.slideNumber(), b.build());
        });
    }

    private static void applyTransition(AnimationBinding.Builder b, String trans) {
        if (trans == null) return;
        switch (trans) {
            case "out" -> b.exit();
            case "emphasis" -> b.emphasis();
            default -> b.entrance();
        }
    }

    /** Flatten a {@link TextBody} to plain text: runs concatenated within a
     *  paragraph, paragraphs joined by newlines. */
    private static String extractPlainText(TextBody body) {
        StringBuilder sb = new StringBuilder();
        List<TextParagraph> paras = body.getParagraphs();
        for (int i = 0; i < paras.size(); i++) {
            if (i > 0) sb.append("\n");
            for (TextRun run : paras.get(i).getRuns()) {
                if (run.getText() != null) sb.append(run.getText());
            }
        }
        return sb.toString();
    }

    // ====================================================================
    // Summaries
    // ====================================================================

    /** One-line summary of a spec for the indexed listing / row header.
     *  Mirrors the MCP tool's format so GUI and agent see the same shape.
     *  Lives here, next to the typed-form knowledge, so the inline row and
     *  the controller render identical text. */
    public static String summarize(CommandSpec s) {
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
                "Rename spid=" + r.spid() + " to " + r.newName();
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
            case CommandSpec.CreateCodeBoxSpec c -> "CreateCodeBox lang=" + c.language();
            case CommandSpec.CreateDiagramSpec d ->
                "CreateDiagram mermaidSource=(" + d.mermaidSource().length() + " chars)";
            case CommandSpec.AddConnectorSpec c ->
                "AddConnector type=" + c.connectorType()
                + (c.startSpid() != null || c.endSpid() != null
                    ? " " + (c.startSpid() != null ? c.startSpid() : "free")
                        + "->" + (c.endSpid() != null ? c.endSpid() : "free")
                    : "");
            case CommandSpec.AddPictureSpec p ->
                "AddPicture media=" + p.blipRef().mediaPartName();
        };
    }

    // ====================================================================
    // Helpers
    // ====================================================================

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
        // Column 0 (labels) hugs its content and never shrinks, so parameter
        // names stay fully visible instead of truncating to "…" when the
        // ScrollPane (fitToWidth) squeezes the form into the narrow panel.
        // Column 1 (controls) absorbs whatever width is left.
        ColumnConstraints labelCol = new ColumnConstraints();
        labelCol.setHgrow(Priority.NEVER);
        labelCol.setMinWidth(Region.USE_PREF_SIZE);
        ColumnConstraints controlCol = new ColumnConstraints();
        controlCol.setHgrow(Priority.ALWAYS);
        controlCol.setFillWidth(true);
        grid.getColumnConstraints().addAll(labelCol, controlCol);
        return grid;
    }

    private static TextField textField(String initial) {
        TextField tf = new TextField(initial);
        tf.setPrefColumnCount(18);
        return tf;
    }

    private static void addRow(GridPane grid, int row, String label, javafx.scene.Node control) {
        Label l = new Label(label);
        // Belt-and-suspenders with the label ColumnConstraints: pin the label
        // to its preferred width so it always renders the full text rather
        // than collapsing to an ellipsis when the panel is narrow.
        l.setMinWidth(Region.USE_PREF_SIZE);
        grid.add(l, 0, row);
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

    /** Parse a Long, or null when the field is blank (the "auto/default"
     *  sentinel for nullable dimensions). Throws on non-numeric input so
     *  the commit fails loudly. */
    private static Long blankToNullLong(TextField tf) {
        String s = tf.getText() == null ? "" : tf.getText().trim();
        return s.isEmpty() ? null : Long.valueOf(s);
    }

    /** Parse an Integer, or null when blank (nullable endpoint SPIDs/indices). */
    private static Integer blankToNullInteger(TextField tf) {
        String s = tf.getText() == null ? "" : tf.getText().trim();
        return s.isEmpty() ? null : Integer.valueOf(s);
    }

    /** Parse a comma-separated SPID list. Blank tokens are skipped; an
     *  empty result lets the record's own validation reject it. */
    private static List<Integer> parseIntList(String csv) {
        List<Integer> out = new ArrayList<>();
        if (csv == null) return out;
        for (String tok : csv.split(",")) {
            String t = tok.trim();
            if (!t.isEmpty()) out.add(Integer.valueOf(t));
        }
        return out;
    }

    private static String joinInts(List<Integer> ids) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(ids.get(i));
        }
        return sb.toString();
    }

    /** A three-state checkbox: checked / unchecked / indeterminate, mapping
     *  to {@code Boolean} {@code TRUE / FALSE / null} so "inherit from theme"
     *  (null) round-trips instead of collapsing to false. */
    private static CheckBox triState(Boolean initial, String label) {
        CheckBox cb = new CheckBox(label + " (— = inherit)");
        cb.setAllowIndeterminate(true);
        if (initial == null) cb.setIndeterminate(true);
        else cb.setSelected(initial);
        return cb;
    }

    private static Boolean readTriState(CheckBox cb) {
        return cb.isIndeterminate() ? null : cb.isSelected();
    }

    /** Render a {@link TextColor} for a text field: {@code ""} for null,
     *  {@code "scheme:NAME"} for a scheme color, else the bare hex. */
    private static String colorToField(TextColor c) {
        if (c == null) return "";
        return c.isScheme() ? "scheme:" + c.getSchemeVal() : c.getHexVal();
    }

    /** Inverse of {@link #colorToField}: blank -> null (inherit),
     *  {@code "scheme:NAME"} -> scheme color, else a hex color. */
    private static TextColor fieldToColor(String s) {
        String t = s == null ? "" : s.trim();
        if (t.isEmpty()) return null;
        if (t.startsWith("scheme:")) return TextColor.scheme(t.substring("scheme:".length()).trim());
        return TextColor.hex(t);
    }

    /** Font size is stored in hundredths of a point; show it as points. */
    private static String hundredthsToPt(Integer hundredths) {
        if (hundredths == null) return "";
        return String.valueOf(hundredths / 100.0);
    }

    private static Integer ptToHundredths(String s) {
        String t = s == null ? "" : s.trim();
        if (t.isEmpty()) return null;
        return (int) Math.round(Double.parseDouble(t) * 100.0);
    }

    /** Carry the run-formatting fields the form does NOT expose so an inline
     *  edit of the common fields doesn't silently drop the rest. */
    private static void carryUnexposedRunFields(TextRun.Builder b, TextRun o) {
        if (o.getStrikethrough() != null) b.strikethrough(o.getStrikethrough());
        if (o.getHighlight() != null) b.highlight(o.getHighlight());
        if (o.getLanguage() != null) b.language(o.getLanguage());
        if (o.getCapitalization() != null) b.capitalization(o.getCapitalization());
        if (o.getBaseline() != null) b.baseline(o.getBaseline());
        if (o.getCharacterSpacing() != null) b.characterSpacing(o.getCharacterSpacing());
        if (o.getKerningThreshold() != null) b.kerningThreshold(o.getKerningThreshold());
        if (o.getMathBody() != null) b.mathBody(o.getMathBody());
    }
}
