package com.excudo.core.commands;

import com.excudo.core.commands.mutating.slide.RemoveTransitionCommand;
import com.excudo.core.commands.mutating.layout.AddLayoutCommand;
import com.excudo.core.commands.mutating.layout.AddPlaceholderCommand;
import com.excudo.core.commands.mutating.layout.DeleteLayoutCommand;
import com.excudo.core.commands.mutating.layout.DuplicateLayoutCommand;
import com.excudo.core.commands.mutating.layout.RemovePlaceholderCommand;
import com.excudo.core.commands.mutating.layout.RenameLayoutCommand;
import com.excudo.core.commands.mutating.master.EditMasterBgCommand;
import com.excudo.core.commands.mutating.master.EditMasterClrMapCommand;
import com.excudo.core.commands.mutating.master.EditMasterStyleCommand;
import com.excudo.core.commands.mutating.notes.AddNotesCommand;
import com.excudo.core.commands.mutating.slide.AddConnectorCommand;
import com.excudo.core.commands.mutating.slide.AddShapeCommand;
import com.excudo.core.commands.mutating.slide.ArrangeCommand;
import com.excudo.core.commands.mutating.slide.BulletPointEditCommand;
import com.excudo.core.commands.mutating.slide.ContentEditCommand;
import com.excudo.core.commands.mutating.slide.CopyStyleCommand;
import com.excudo.core.commands.mutating.slide.DuplicateShapeCommand;
import com.excudo.core.commands.mutating.slide.EnhancedContentCommand;
import com.excudo.core.commands.mutating.slide.GroupShapesCommand;
import com.excudo.core.commands.mutating.slide.InjectIconCommand;
import com.excudo.core.commands.mutating.slide.MoveShapeCommand;
import com.excudo.core.commands.mutating.slide.RemoveShapeCommand;
import com.excudo.core.commands.mutating.slide.ReorderShapeCommand;
import com.excudo.core.commands.mutating.slide.ResizeShapeCommand;
import com.excudo.core.commands.mutating.slide.SetActionCommand;
import com.excudo.core.commands.mutating.slide.SetBodyPropsCommand;
import com.excudo.core.commands.mutating.slide.SetFontCommand;
import com.excudo.core.commands.mutating.slide.SetStyleCommand;
import com.excudo.core.commands.mutating.slide.SetTextCommand;
import com.excudo.core.commands.mutating.slide.SetTransitionCommand;
import com.excudo.core.commands.mutating.slide.UngroupCommand;
import com.excudo.core.commands.mutating.theme.SetObjectDefaultsCommand;
import com.excudo.core.commands.readonly.ShowMasterCommand;

import com.excudo.core.orchestration.PPTXOrchestrator;
import com.excudo.core.model.SlideShape;
import com.excudo.core.model.ShapeGeometry;
import com.excudo.core.model.ShapeStyle;
import com.excudo.core.model.ShapeFill;
import com.excudo.core.model.ShapeLine;
import com.excudo.core.model.TextColor;
import com.excudo.core.model.BodyProperties;
import com.excudo.core.model.AutofitType;
import com.excudo.core.model.TransitionType;
import com.excudo.core.geometry.UnitParser;
import com.excudo.core.parsing.CommandParameters;
import java.util.HashSet;
import java.util.Set;
import java.util.Map;
import java.util.HashMap;

/**
 * Factory for creating shape and content-related commands.
 * Handles edit-content, add-shape, move, resize, arrange, reorder,
 * transitions, connectors, notes, and more.
 * LLM requests are bridged to CommandParameters by LLMRequestBridge before reaching here.
 */
public class ShapeCommandFactory extends AbstractCommandFactory {
    
    private static final Set<String> HANDLED_COMMANDS = new HashSet<>();

    static {
        // add-shape, remove-shape, set-body-props, add-notes, set-action,
        // edit-content, edit-bullet, set-text, add-connector: migrated to
        // class registry (own SCHEMA / fromParameters).
        // inject-icon, enhanced-content: migrated to class registry
        // set-transition, remove-transition: migrated to class registry
        // move: migrated to class registry (MoveShapeCommand)
        // resize: migrated to class registry (ResizeShapeCommand)
        HANDLED_COMMANDS.add("arrange");
        // reorder: migrated to class registry (ReorderShapeCommand)
        HANDLED_COMMANDS.add("add-layout");
        HANDLED_COMMANDS.add("add-placeholder");
        HANDLED_COMMANDS.add("set-font");
        HANDLED_COMMANDS.add("set-style");
        // duplicate: migrated to class registry (DuplicateShapeCommand)
        // group: migrated to class registry (GroupShapesCommand)
        HANDLED_COMMANDS.add("copy-style");
        HANDLED_COMMANDS.add("edit-master-style");
        HANDLED_COMMANDS.add("edit-master-clrmap");
        HANDLED_COMMANDS.add("set-object-defaults");
    }
    
    public ShapeCommandFactory(PPTXOrchestrator orchestrator) {
        super(orchestrator);
    }
    
    @Override
    public boolean handlesCommand(String commandName) {
        return HANDLED_COMMANDS.contains(commandName);
    }
    
    @Override
    public Command createFromParameters(CommandParameters parameters, Object displayAdapter) {
        String commandName = parameters.getCommandName();
        
        switch (commandName) {
            // "edit-content" migrated to class registry (ContentEditCommand)


            // AddShapeCommand.NAME intentionally absent: routed via the
            // class-keyed registry in CommandRegistry / CommandFactory
            // (AddShapeCommand.SCHEMA + AddShapeCommand.fromParameters).

            // RemoveShapeCommand.NAME migrated to class registry (RemoveShapeCommand)

            // "edit-bullet" migrated to class registry (BulletPointEditCommand)

            // SetBodyPropsCommand.NAME migrated to class registry (SetBodyPropsCommand)

            // SetTextCommand.NAME migrated to class registry (SetTextCommand)

            // AddNotesCommand.NAME migrated to class registry (AddNotesCommand)

            // AddConnectorCommand.NAME migrated to class registry (AddConnectorCommand)

            // SetActionCommand.NAME migrated to class registry (SetActionCommand)

            // SetTransitionCommand.NAME / RemoveTransitionCommand.NAME migrated to class registry

            // "move" migrated to class registry (MoveShapeCommand.SCHEMA / fromParameters)

            // "resize" migrated to class registry (ResizeShapeCommand.SCHEMA / fromParameters)

            // "reorder" migrated to class registry (ReorderShapeCommand.SCHEMA / fromParameters)

            // "duplicate" migrated to class registry (DuplicateShapeCommand.SCHEMA / fromParameters)

            // "inject" -> InjectIconCommand.NAME (InjectIconCommand) and
            // "enhance" -> EnhancedContentCommand.NAME (EnhancedContentCommand)
            // migrated to class registry.

            // "group" migrated to class registry (GroupShapesCommand.SCHEMA / fromParameters)

            default:
                throw new IllegalArgumentException("Unknown shape command: " + commandName);
        }
    }
    
    // ========== PUBLIC COMMAND CREATION METHODS ==========
    
    /**
     * Create a content edit command.
     * 
     * Phase 3 Enhancement: Includes pre-validation of shape existence and geometry.
     * 
     * @param slideNumber the slide number containing the shape
     * @param spid the SPID of the shape to edit
     * @param newText the new text content
     * @return ContentEditCommand
     * @throws IllegalArgumentException if validation fails
     */
    public ContentEditCommand createContentEdit(int slideNumber, int spid, String newText) {
        return createContentEdit(slideNumber, spid, newText, ContentEditCommand.Mode.REPLACE, null);
    }

    /**
     * Overload for callers who want to choose replace/prepend/append and
     * receive feedback through a display adapter (typically the console
     * engine). The display adapter is optional -- passing null suppresses
     * feedback (headless / programmatic use).
     */
    public ContentEditCommand createContentEdit(int slideNumber, int spid, String newText,
            ContentEditCommand.Mode mode, Object displayAdapter) {
        validateContentEditParameters(slideNumber, spid, newText);
        return new ContentEditCommand(slideNumber, spid, newText, mode, orchestrator, displayAdapter);
    }
    
    /**
     * Create an enhanced content command.
     * 
     * @param slideNumber the slide number to enhance
     * @param iconKeyword the keyword for content search
     * @param templateStyle the template style to apply
     * @param geometry the geometry parameters
     * @return EnhancedContentCommand
     */
    public EnhancedContentCommand createEnhancedContent(int slideNumber, String iconKeyword, 
                                                       String templateStyle, Map<String, Object> geometry) {
        return new EnhancedContentCommand(slideNumber, iconKeyword, templateStyle, geometry, orchestrator);
    }
    
    /**
     * Create an add shape command.
     * 
     * @param slideNumber the slide number to add the shape to
     * @param shapeType the type of shape to create
     * @param geometry the shape geometry (position and size)
     * @param text the text content (can be null for non-text shapes)
     * @param shapeName the name for the shape
     * @return AddShapeCommand
     */
    public AddShapeCommand createAddShape(int slideNumber, SlideShape.ShapeType shapeType, 
                                         ShapeGeometry geometry, String text, String shapeName) {
        return new AddShapeCommand(slideNumber, shapeType, geometry, text, shapeName, orchestrator);
    }
    
    /**
     * Create an icon injection command.
     * 
     * @param slideNumber the slide number to inject icon into
     * @param iconQuery the query/keyword for icon search
     * @param placementOptions optional placement options (position, size, etc.)
     * @return InjectIconCommand
     */
    /**
     * Create a remove shape command.
     */
    public RemoveShapeCommand createRemoveShape(int slideNumber, int spid) {
        return new RemoveShapeCommand(slideNumber, spid, orchestrator);
    }

    /**
     * Create a bullet point edit command.
     */
    public BulletPointEditCommand createBulletPointEdit(int slideNumber, int spid, String operation,
                                                        int bulletIndex, String newText, String bulletStyle) {
        return new BulletPointEditCommand(slideNumber, spid, operation, bulletIndex, newText, bulletStyle, orchestrator);
    }

    public InjectIconCommand createIconInjection(int slideNumber, String iconQuery,
                                                Map<String, Object> placementOptions) {
        return new InjectIconCommand(slideNumber, iconQuery, placementOptions, orchestrator);
    }
    
    // ========== STYLE HELPERS ==========

    /**
     * Parse optional fill-color and line-color strings into a ShapeStyle.
     * Accepts hex colors (e.g. "FF0000", "#FF0000") or scheme names (e.g. "accent1").
     * Returns null if no style parameters given (triggers default theme style).
     */
    /**
     * Normalize an alignment input ("left" / "l" / "center" / "ctr" /
     * "right" / "r" / "justify" / "just") to the canonical OOXML token
     * ("l" / "ctr" / "r" / "just"). Returns null if the input is null
     * or blank, signaling "use default alignment." Throws on
     * unrecognized values rather than silently dropping them so the
     * agent gets immediate feedback.
     */
    public static String normalizeAlignment(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String v = raw.trim().toLowerCase();
        switch (v) {
            case "l": case "left":    return "l";
            case "ctr": case "center": case "centre": return "ctr";
            case "r": case "right":   return "r";
            case "just": case "justify": return "just";
            default:
                throw new IllegalArgumentException(
                    "Unrecognised alignment: '" + raw
                    + "'. Use one of: l/left, ctr/center, r/right, just/justify.");
        }
    }

    public static ShapeStyle parseShapeStyle(String fillColor, String lineColor) {
        return parseShapeStyle(fillColor, lineColor, null, null);
    }

    /**
     * Build a ShapeStyle from explicit color + opacity params. Alpha values
     * are percentages 0-100; null leaves the channel fully opaque (no
     * a:alpha emitted).
     */
    public static ShapeStyle parseShapeStyle(String fillColor, String lineColor,
                                             Integer fillAlphaPercent, Integer lineAlphaPercent) {
        ShapeFill fill = null;
        ShapeLine line = null;

        if (fillColor != null && !fillColor.isEmpty()) {
            fill = isSchemeColor(fillColor)
                ? ShapeFill.scheme(fillColor)
                : ShapeFill.solid(fillColor);
            if (fillAlphaPercent != null) fill = fill.withAlphaPercent(fillAlphaPercent);
        }

        if (lineColor != null && !lineColor.isEmpty()) {
            TextColor lc = isSchemeColor(lineColor)
                ? TextColor.scheme(lineColor)
                : TextColor.hex(lineColor);
            line = ShapeLine.solid(12700, lc); // 1pt default width
            if (lineAlphaPercent != null) line = line.withAlphaPercent(lineAlphaPercent);
        }

        if (fill == null && line == null) return null;
        return ShapeStyle.withFillAndLine(fill, line);
    }

    private static boolean isSchemeColor(String val) {
        String lower = val.toLowerCase();
        return lower.startsWith("accent") || lower.startsWith("dk") || lower.startsWith("lt")
            || "hlink".equals(lower) || "folhlink".equals(lower);
    }

    // ========== VALIDATION METHODS ==========
    
    /**
     * Validate parameters for content edit operations.
     * Phase 3 Enhancement: Pre-validates shape existence and provides detailed error messages.
     * 
     * @param slideNumber the slide number
     * @param spid the shape SPID
     * @param newText the new text content
     * @throws IllegalArgumentException if validation fails
     */
    private void validateContentEditParameters(int slideNumber, int spid, String newText) {
        // Basic parameter validation
        if (slideNumber <= 0) {
            throw new IllegalArgumentException(String.format(
                "Invalid slide number: %d. Slide numbers must be positive.", slideNumber));
        }
        
        if (spid <= 0) {
            throw new IllegalArgumentException(String.format(
                "Invalid SPID: %d. SPIDs must be positive integers.", spid));
        }
        
        if (newText == null) {
            throw new IllegalArgumentException("New text content cannot be null.");
        }
        
    }
}