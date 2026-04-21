package com.excudo.core.parsing;

import java.util.*;
import com.excudo.core.parsing.Parameter.ParameterType;
import com.excudo.utils.FuzzyMatcher;

/**
 * Registry of all command schemas.
 * Single source of truth for command definitions.
 */
public class CommandRegistry {
    private static final Map<String, CommandSchema> schemas = new HashMap<>();
    
    static {
        // Register all command schemas
        registerAnimationCommand();
        registerCreateCommand();
        registerDeleteCommand();
        registerListCommand();
        registerEditContentCommand();
        registerAddShapeCommand();
        
        // Add missing command schemas identified in Phase 1 audit
        registerHelpCommand();
        registerLoadCommand();
        registerSaveCommand();
        registerRenderCommand();
        registerShowCommand();
        registerListLayoutsCommand();
        registerListSpidsCommand();
        registerListAnimationsCommand();
        registerListAnimationTypesCommand();
        registerRemoveAnimationCommand();
        registerUpdateAnimationCommand();
        registerDumpTimingCommand();
        registerDumpShapeCommand();
        registerShowShapeCommand();
        registerLlmCommand();
        registerSessionCommand();
        registerInjectCommand();
        registerEnhanceCommand();
        registerUndoCommand();
        registerRedoCommand();
        registerHistoryCommand();
        registerListNotesCommand();
        registerApplyThemeCommand();
        registerListThemesCommand();
        registerNewCommand();
        registerShowThemeCommand();
        registerCreateThemeCommand();
        registerEditThemeCommand();
        registerDeleteThemeCommand();
        registerRemoveShapeCommand();
        registerEditBulletCommand();
        registerListShapeTypesCommand();
        registerSetBodyPropsCommand();
        registerSetTextCommand();
        registerAddNotesCommand();
        registerAddConnectorCommand();
        registerSetActionCommand();
        registerCopySlideCommand();
        registerMoveSlideCommand();
        registerSetTransitionCommand();
        registerRemoveTransitionCommand();
        registerListTransitionTypesCommand();
        registerMoveShapeCommand();
        registerResizeShapeCommand();
        registerArrangeCommand();
        registerReorderCommand();
        registerDuplicateLayoutCommand();
        registerAddLayoutCommand();
        registerDeleteLayoutCommand();
        registerRenameLayoutCommand();
        registerAddPlaceholderCommand();
        registerRemovePlaceholderCommand();
        registerListArrangeOpsCommand();
        registerSetFontCommand();
        registerSetStyleCommand();
        registerDuplicateCommand();
        registerGroupCommand();
        registerUngroupCommand();
        registerCopyStyleCommand();
        registerIconCommand();
        registerEditMasterStyleCommand();
        registerEditMasterClrMapCommand();
        registerEditMasterBgCommand();
        registerShowMasterCommand();
        registerSetObjectDefaultsCommand();
    }
    
    /**
     * Register the add-animation command with proper schema
     */
    private static void registerAnimationCommand() {
        CommandSchema schema = CommandSchema.builder("add-animation")
            .description("Add animation to a shape")
            .llmEnabled(true)
            .llmAlias("animation-edit")
            .llmDescription("Add animation to a shape.")
            .parameter(Parameter.builder("slide")
                .description("Slide number")
                .type(ParameterType.SLIDE_NUMBER)
                .llmName("slideNumber")
                .required(true)
                .build())
            .parameter(Parameter.builder("spid")
                .description("Shape ID to animate (must be valid existing SPID)")
                .type(ParameterType.SPID)
                .llmName("targetSpid")
                .required(true)
                .build())
            .parameter(Parameter.builder("type")
                .description("Animation type (fade, fly, wipe, appear, split, zoom, motion-custom, etc.)")
                .type(ParameterType.ANIMATION_TYPE)
                .llmName("animationType")
                .required(true)
                .build())
            .parameter(Parameter.builder("direction")
                .description("Animation direction")
                .validValues("in", "out", "emphasis")
                .defaultValue("in")
                .build())
            .parameter(Parameter.builder("trigger")
                .description("Animation trigger")
                .validValues("on-click", "with-previous", "after-previous")
                .llmName("animationGroup")
                .defaultValue("on-click")
                .build())
            .parameter(Parameter.builder("duration")
                .description("Duration in milliseconds (e.g., 500 = 0.5 seconds)")
                .type(ParameterType.INTEGER)
                .llmName("durationMs")
                .defaultValue("500")
                .build())
            .parameter(Parameter.builder("path")
                .description("SVG-like motion path for motion-custom (e.g. 'M 0 0 L 0.25 0.25')")
                .required(false)
                .build())
            .parameter(Parameter.builder("paragraphStart")
                .description("Start paragraph index (0-based) for paragraph-level targeting")
                .type(ParameterType.INTEGER)
                .required(false)
                .build())
            .parameter(Parameter.builder("paragraphEnd")
                .description("End paragraph index (0-based) for paragraph-level targeting")
                .type(ParameterType.INTEGER)
                .required(false)
                .build())
            .parameter(Parameter.builder("opacity")
                .description("Opacity percentage for transparency emphasis (25, 50, 75, 100)")
                .type(ParameterType.INTEGER)
                .llmName("opacity")
                .validValues("25", "50", "75", "100")
                .required(false)
                .build())
            .example("add-animation 1 2 fade in on-click")
            .example("add-animation 1 2 fly-in-left in with-previous 1000")
            .example("add-animation 1 2 transparency emphasis on-click --opacity 50")
            .build();

        schemas.put("add-animation", schema);
    }
    
    /**
     * Register the create command
     */
    private static void registerCreateCommand() {
        CommandSchema schema = CommandSchema.builder("create")
            .description("Create a new slide")
            .llmEnabled(true)
            .llmAlias("slide-creation")
            .llmDescription("Create slide with title and content. ALWAYS pass content as a JSON array of markdown strings, one per placeholder.")
            .parameter(Parameter.builder("position")
                .description("Position to insert slide (1-based)")
                .type(ParameterType.SLIDE_NUMBER)
                .llmName("slideNumber")
                .required(true)
                .build())
            .parameter(Parameter.builder("title")
                .description("Slide title")
                .required(true)
                .build())
            .parameter(Parameter.builder("layout")
                .description("Slide layout ID (e.g. slideLayout1)")
                .llmName("layoutId")
                .defaultValue("slideLayout1")
                .build())
            .parameter(Parameter.builder("content")
                .description("JSON array of strings, one per content placeholder. e.g. [\"- Bullet 1\\n- Bullet 2\"] for 1-content layouts, [\"Left col\",\"Right col\"] for 2-content layouts. Supports markdown.")
                .required(false)
                .build())
            .example("create 2 \"My New Slide\"")
            .example("create 2 \"My Slide\" slideLayout2")
            .build();

        schemas.put("create", schema);
    }
    
    /**
     * Register the delete command
     */
    private static void registerDeleteCommand() {
        CommandSchema schema = CommandSchema.builder("delete")
            .description("Delete a slide")
            .llmEnabled(true)
            .llmAlias("slide-deletion")
            .parameter(Parameter.builder("slide")
                .description("Slide number to delete")
                .type(ParameterType.SLIDE_NUMBER)
                .llmName("slideNumber")
                .required(true)
                .build())
            .example("delete 3")
            .build();

        schemas.put("delete", schema);
    }
    
    /**
     * Register the list command
     */
    private static void registerListCommand() {
        CommandSchema schema = CommandSchema.builder("list")
            .description("List slides in the presentation")
            .parameter(Parameter.builder("verbose")
                .description("Show detailed information")
                .type(ParameterType.BOOLEAN)
                .defaultValue("false")
                .build())
            .example("list")
            .example("list --verbose true")
            .build();
        
        schemas.put("list", schema);
    }
    
    /**
     * Register edit-content command
     */
    private static void registerEditContentCommand() {
        CommandSchema schema = CommandSchema.builder("edit-content")
            .description("Edit text content of a shape. Default mode is REPLACE: the passed string (including empty string) replaces the shape's content. --prepend / --append opt into additive modes.")
            .llmEnabled(true)
            .llmAlias("content-edit")
            .llmDescription("Edit text content of a shape. Default: replace. Flags --prepend and --append opt into additive modes; the absence of both means the passed string replaces the shape's content (empty string clears).")
            .parameter(Parameter.builder("slide")
                .description("Slide number")
                .type(ParameterType.SLIDE_NUMBER)
                .llmName("slideNumber")
                .required(true)
                .build())
            .parameter(Parameter.builder("spid")
                .description("Shape ID (must be valid existing SPID)")
                .type(ParameterType.SPID)
                .llmName("targetSpid")
                .required(true)
                .build())
            .parameter(Parameter.builder("text")
                .description("New text content. Markdown: **bold**, *italic*, - bullets, 1. numbered, indent 2 spaces per level, \\n for line breaks. Empty string clears the shape's content when neither --prepend nor --append is set.")
                .llmName("newText")
                .required(true)
                .variableLength(true)
                .build())
            .parameter(Parameter.builder("prepend")
                .description("Prepend the text to existing content instead of replacing (boolean flag).")
                .llmName("prepend")
                .type(ParameterType.BOOLEAN)
                .required(false)
                .defaultValue("false")
                .build())
            .parameter(Parameter.builder("append")
                .description("Append the text to existing content instead of replacing (boolean flag). Mutually exclusive with --prepend.")
                .llmName("append")
                .type(ParameterType.BOOLEAN)
                .required(false)
                .defaultValue("false")
                .build())
            .example("edit-content 1 2 \"New text content\"")
            .example("edit-content 1 2 \"\"           # clear the shape's text")
            .example("edit-content --slide 1 --spid 2 --text \" more\" --append")
            .build();

        schemas.put("edit-content", schema);
    }
    
    /**
     * Register add-shape command
     */
    private static void registerAddShapeCommand() {
        CommandSchema schema = CommandSchema.builder("add-shape")
            .description("Add a shape to a slide")
            .llmEnabled(true)
            .llmAlias("shape-addition")
            .llmDescription("Add a new shape to a slide.")
            .parameter(Parameter.builder("slide")
                .description("Slide number")
                .type(ParameterType.SLIDE_NUMBER)
                .llmName("slideNumber")
                .required(true)
                .build())
            .parameter(Parameter.builder("shape-type")
                .description("Shape type: TEXT_BOX for plain text with no fill/border, "
                    + "or an autoshape preset like RECTANGLE, ELLIPSE, TRIANGLE, "
                    + "ROUNDED_RECTANGLE, etc. See ShapeType enum for the full set.")
                .llmName("shapeType")
                .defaultValue("RECTANGLE")
                .build())
            .parameter(Parameter.builder("text")
                .description("Text content placed inside the shape. Markdown supported: "
                    + "**bold**, *italic*, - bullets, 1. numbered, \\n for line breaks. "
                    + "Leave empty for a shape with no text.")
                .llmName("text")
                .defaultValue("")
                .build())
            .parameter(Parameter.builder("x")
                .description("X position in EMU")
                .type(ParameterType.DOUBLE)
                .required(true)
                .build())
            .parameter(Parameter.builder("y")
                .description("Y position in EMU")
                .type(ParameterType.DOUBLE)
                .required(true)
                .build())
            .parameter(Parameter.builder("width")
                .description("Width in EMU")
                .type(ParameterType.DOUBLE)
                .required(true)
                .build())
            .parameter(Parameter.builder("height")
                .description("Height in EMU")
                .type(ParameterType.DOUBLE)
                .required(true)
                .build())
            .parameter(Parameter.builder("fill-color")
                .description("Fill color: hex (FF0000) or scheme name (accent1)")
                .llmName("fillColor")
                .required(false)
                .build())
            .parameter(Parameter.builder("line-color")
                .description("Line color: hex (000000) or scheme name (dk1)")
                .llmName("lineColor")
                .required(false)
                .build())
            .example("add-shape 1 RECTANGLE \"Hello\" 100 100 200 100")
            .example("add-shape 1 RECTANGLE \"Styled\" 100 100 200 100 --fill-color FF0000 --line-color 000000")
            .build();

        schemas.put("add-shape", schema);
    }
    
    /**
     * Register the help command with proper schema
     */
    private static void registerHelpCommand() {
        CommandSchema schema = CommandSchema.builder("help")
            .description("Show help information")
            .parameter(Parameter.builder("topic")
                .description("Specific help topic")
                .type(ParameterType.STRING)
                .required(false)
                .build())
            .example("help")
            .example("help animations")
            .build();
        
        schemas.put("help", schema);
        schemas.put("?", schema);
    }
    
    /**
     * Register the load command with proper schema
     */
    private static void registerLoadCommand() {
        CommandSchema schema = CommandSchema.builder("load")
            .description("Load an existing PowerPoint presentation from disk")
            .llmEnabled(true)
            .llmDescription("DESTRUCTIVE: replaces the current in-memory presentation "
                + "with the file at the given path. Any unsaved work is lost. Use when "
                + "asked to edit an existing deck rather than create a new one.")
            .parameter(Parameter.builder("filename")
                .description("PPTX file to load")
                .type(ParameterType.STRING)
                .required(true)
                .build())
            .example("load presentation.pptx")
            .example("open /path/to/file.pptx")
            .build();

        schemas.put("load", schema);
        schemas.put("open", schema);
    }

    /**
     * Register the save command with proper schema
     */
    private static void registerSaveCommand() {
        CommandSchema schema = CommandSchema.builder("save")
            .description("Save the current presentation to a PPTX file")
            .llmEnabled(true)
            .llmDescription("OVERWRITES: writes the current presentation to the given path, "
                + "replacing any existing file. Always pass an explicit filename -- the "
                + "console's Ctrl+S-style save-to-last-path convenience is not reliable "
                + "in an agent context.")
            .parameter(Parameter.builder("filename")
                .description("Output PPTX path. Always pass this explicitly.")
                .type(ParameterType.STRING)
                .required(false)
                .build())
            .example("save")
            .example("save output.pptx")
            .build();
        
        schemas.put("save", schema);
    }

    private static void registerRenderCommand() {
        CommandSchema schema = CommandSchema.builder("render")
            .description("Render a slide to a PNG image file")
            .llmEnabled(true)
            .llmDescription("Render a slide to PNG for visual inspection.")
            .parameter(Parameter.builder("slide")
                .description("Slide number to render (1-based)")
                .type(ParameterType.SLIDE_NUMBER)
                .llmName("slideNumber")
                .required(true)
                .build())
            .parameter(Parameter.builder("output")
                .description("Output PNG file path")
                .type(ParameterType.STRING)
                .required(true)
                .build())
            .parameter(Parameter.builder("width")
                .description("Image width in pixels")
                .type(ParameterType.INTEGER)
                .defaultValue("1280")
                .required(false)
                .build())
            .parameter(Parameter.builder("height")
                .description("Image height in pixels")
                .type(ParameterType.INTEGER)
                .defaultValue("720")
                .required(false)
                .build())
            .example("render 1 slide1.png")
            .example("render 3 /tmp/slide3.png 1920 1080")
            .build();

        schemas.put("render", schema);
    }
    
    /**
     * Register the show command with proper schema
     */
    private static void registerShowCommand() {
        CommandSchema schema = CommandSchema.builder("show")
            .description("Show slide details")
            .parameter(Parameter.builder("slide")
                .description("Slide number")
                .type(ParameterType.SLIDE_NUMBER)
                .required(true)
                .build())
            .example("show 1")
            .build();
        
        schemas.put("show", schema);
    }
    
    /**
     * Register the list-layouts command with optional themeId parameter
     */
    private static void registerListLayoutsCommand() {
        CommandSchema schema = CommandSchema.builder("list-layouts")
            .description("List available slide layouts")
            .parameter(Parameter.builder("themeId")
                .description("Optional theme ID to list layouts from (no session needed)")
                .type(ParameterType.STRING)
                .required(false)
                .build())
            .example("list-layouts")
            .example("list-layouts corporate")
            .build();

        schemas.put("list-layouts", schema);
    }
    
    /**
     * Register the list-spids command with proper schema
     */
    private static void registerListSpidsCommand() {
        CommandSchema schema = CommandSchema.builder("list-spids")
            .description("List shape IDs on a slide")
            .parameter(Parameter.builder("slide")
                .description("Slide number")
                .type(ParameterType.SLIDE_NUMBER)
                .required(true)
                .build())
            .example("list-spids 1")
            .build();
        
        schemas.put("list-spids", schema);
    }
    
    /**
     * Register the list-animations command with proper schema
     */
    private static void registerListAnimationsCommand() {
        CommandSchema schema = CommandSchema.builder("list-animations")
            .description("List animations on a slide")
            .parameter(Parameter.builder("slide")
                .description("Slide number")
                .type(ParameterType.SLIDE_NUMBER)
                .required(true)
                .build())
            .example("list-animations 1")
            .build();
        
        schemas.put("list-animations", schema);
    }
    
    /**
     * Register the list-animation-types command with proper schema
     */
    private static void registerListAnimationTypesCommand() {
        CommandSchema schema = CommandSchema.builder("list-animation-types")
            .description("List available animation types")
            .example("list-animation-types")
            .build();
        
        schemas.put("list-animation-types", schema);
    }
    
    /**
     * Register the dump-timing command with proper schema
     */
    private static void registerDumpTimingCommand() {
        CommandSchema schema = CommandSchema.builder("dump-timing")
            .description("Dump timing tree for a slide range")
            .parameter(Parameter.builder("range")
                .description("Slide range: single number (1), range (1-5), or 'all'")
                .type(ParameterType.STRING)
                .required(false)
                .defaultValue("1")
                .build())
            .parameter(Parameter.builder("slide")
                .description("Legacy: Single slide number")
                .type(ParameterType.SLIDE_NUMBER)
                .required(false)
                .build())
            .example("dump-timing 1")
            .example("dump-timing 1-5")
            .example("dump-timing all")
            .example("dump-timing --range all")
            .build();
        
        schemas.put("dump-timing", schema);
    }
    
    /**
     * Register the dump-shape command with proper schema
     */
    private static void registerDumpShapeCommand() {
        CommandSchema schema = CommandSchema.builder("dump-shape")
            .description("Dump shape structure for a slide range")
            .parameter(Parameter.builder("range")
                .description("Slide range: single number (1), range (1-5), or 'all'")
                .type(ParameterType.STRING)
                .required(false)
                .defaultValue("1")
                .build())
            .parameter(Parameter.builder("slide")
                .description("Legacy: Single slide number")
                .type(ParameterType.SLIDE_NUMBER)
                .required(false)
                .build())
            .parameter(Parameter.builder("spid")
                .description("Legacy: Shape SPID (ignored in bulk mode)")
                .type(ParameterType.SPID)
                .required(false)
                .build())
            .example("dump-shape 1")
            .example("dump-shape 1-5")
            .example("dump-shape all")
            .example("dump-shape --range all")
            .build();
        
        schemas.put("dump-shape", schema);
    }
    
    /**
     * Register the show-shape command with proper schema
     */
    private static void registerShowShapeCommand() {
        CommandSchema schema = CommandSchema.builder("show-shape")
            .description("Show details for a specific shape")
            .parameter(Parameter.builder("slide")
                .description("Slide number")
                .type(ParameterType.SLIDE_NUMBER)
                .required(true)
                .build())
            .parameter(Parameter.builder("spid")
                .description("Shape ID")
                .type(ParameterType.SPID)
                .required(true)
                .build())
            .example("show-shape 1 42")
            .build();
        
        schemas.put("show-shape", schema);
    }
    
    /**
     * Register the llm command with proper schema
     */
    private static void registerLlmCommand() {
        CommandSchema schema = CommandSchema.builder("llm")
            .description("LLM-powered presentation editing")
            .parameter(Parameter.builder("subcommand")
                .description("LLM subcommand (edit, analyze, suggest, config, help)")
                .type(ParameterType.STRING)
                .required(true)
                .build())
            .parameter(Parameter.builder("request")
                .description("Natural language request for the LLM")
                .type(ParameterType.STRING)
                .required(false)
                .variableLength(true)
                .build())
            .example("llm help")
            .example("llm edit add a blue rectangle to slide 1")
            .example("llm analyze")
            .build();

        schemas.put("llm", schema);
    }
    
    /**
     * Register the session command with proper schema
     */
    private static void registerSessionCommand() {
        CommandSchema schema = CommandSchema.builder("session")
            .description("Session management operations")
            .parameter(Parameter.builder("operation")
                .description("Session operation")
                .validValues("create", "list", "info", "close", "switch")
                .required(true)
                .build())
            .parameter(Parameter.builder("args")
                .description("Operation arguments")
                .type(ParameterType.STRING)
                .required(false)
                .build())
            .example("session create presentation.pptx")
            .example("session list")
            .example("session info session-123")
            .build();
        
        schemas.put("session", schema);
    }
    
    /**
     * Register the inject command with proper schema
     */
    private static void registerInjectCommand() {
        CommandSchema schema = CommandSchema.builder("inject")
            .description("Inject enhanced content (icons, images)")
            .parameter(Parameter.builder("slide")
                .description("Slide number")
                .type(ParameterType.SLIDE_NUMBER)
                .required(true)
                .build())
            .parameter(Parameter.builder("keyword")
                .description("Content keyword")
                .type(ParameterType.STRING)
                .required(true)
                .build())
            .example("inject 1 \"business icon\"")
            .build();
        
        schemas.put("inject", schema);
    }
    
    /**
     * Register the enhance command with proper schema
     */
    private static void registerEnhanceCommand() {
        CommandSchema schema = CommandSchema.builder("enhance")
            .description("Enhance slide content with smart features")
            .llmEnabled(true)
            .llmAlias("enhanced-content")
            .llmDescription("Add icon to a slide by keyword.")
            .parameter(Parameter.builder("slide")
                .description("Slide number")
                .type(ParameterType.SLIDE_NUMBER)
                .llmName("slideNumber")
                .required(true)
                .build())
            .parameter(Parameter.builder("keyword")
                .description("Keyword for icon/graphic search")
                .llmName("iconKeyword")
                .required(false)
                .build())
            .example("enhance 1")
            .build();
        
        schemas.put("enhance", schema);
    }
    
    /**
     * Register the undo command with proper schema
     */
    private static void registerUndoCommand() {
        CommandSchema schema = CommandSchema.builder("undo")
            .description("Undo the last command")
            .example("undo")
            .build();
        
        schemas.put("undo", schema);
    }
    
    /**
     * Register the redo command with proper schema
     */
    private static void registerRedoCommand() {
        CommandSchema schema = CommandSchema.builder("redo")
            .description("Redo the last undone command")
            .example("redo")
            .build();
        
        schemas.put("redo", schema);
    }
    
    /**
     * Register the history command with proper schema
     */
    private static void registerHistoryCommand() {
        CommandSchema schema = CommandSchema.builder("history")
            .description("Show command history")
            .example("history")
            .build();
        
        schemas.put("history", schema);
    }
    
    /**
     * Register the list-notes command with proper schema
     */
    private static void registerListNotesCommand() {
        CommandSchema schema = CommandSchema.builder("list-notes")
            .description("List presentation notes")
            .parameter(Parameter.builder("filter")
                .description("Filter notes by criteria")
                .type(ParameterType.STRING)
                .required(false)
                .build())
            .example("list-notes")
            .example("list-notes \"important\"")
            .build();
        
        schemas.put("list-notes", schema);
    }
    
    /**
     * Register the remove-animation command
     */
    private static void registerRemoveAnimationCommand() {
        CommandSchema schema = CommandSchema.builder("remove-animation")
            .description("Remove an animation from a slide by timing node ID")
            .parameter(Parameter.builder("slide")
                .description("Slide number")
                .type(ParameterType.SLIDE_NUMBER)
                .required(true)
                .build())
            .parameter(Parameter.builder("timingNodeId")
                .description("Timing node ID (cTn id) of the animation to remove")
                .type(ParameterType.INTEGER)
                .required(true)
                .build())
            .example("remove-animation 1 15")
            .example("remove-animation --slide 1 --timingNodeId 15")
            .build();

        schemas.put("remove-animation", schema);
    }

    /**
     * Register the update-animation command
     */
    private static void registerUpdateAnimationCommand() {
        CommandSchema schema = CommandSchema.builder("update-animation")
            .description("Update properties of an existing animation")
            .parameter(Parameter.builder("slide")
                .description("Slide number")
                .type(ParameterType.SLIDE_NUMBER)
                .required(true)
                .build())
            .parameter(Parameter.builder("timingNodeId")
                .description("Timing node ID (cTn id) of the animation to update")
                .type(ParameterType.INTEGER)
                .required(true)
                .build())
            .parameter(Parameter.builder("property")
                .description("Property to update")
                .validValues("duration", "delay", "presetSubtype")
                .required(true)
                .build())
            .parameter(Parameter.builder("value")
                .description("New value for the property")
                .required(true)
                .build())
            .example("update-animation 1 15 duration 1000")
            .example("update-animation --slide 1 --timingNodeId 15 --property duration --value 1000")
            .build();

        schemas.put("update-animation", schema);
    }

    /**
     * Get schema for a command
     */
    public static CommandSchema getSchema(String commandName) {
        return schemas.get(commandName);
    }
    
    /**
     * Check if a command is registered
     */
    public static boolean hasCommand(String commandName) {
        return schemas.containsKey(commandName);
    }
    
    /**
     * Get all registered command names
     */
    public static Set<String> getCommandNames() {
        return schemas.keySet();
    }

    /**
     * Get all registered command schemas.
     */
    public static Map<String, CommandSchema> getAllSchemas() {
        return Collections.unmodifiableMap(schemas);
    }

    /**
     * Resolve an LLM action type to its canonical command name.
     * Checks both direct command names and legacy llmAlias mappings.
     */
    public static String resolveActionType(String actionType) {
        // Direct match
        if (schemas.containsKey(actionType)) return actionType;
        // Search by llmAlias
        for (CommandSchema schema : schemas.values()) {
            if (actionType.equals(schema.getLlmAlias())) {
                return schema.getName();
            }
        }
        return null;
    }
    
    private static void registerApplyThemeCommand() {
        CommandSchema schema = CommandSchema.builder("apply-theme")
            .description("Apply a bundled theme to the presentation")
            .llmEnabled(true)
            .llmDescription("Apply a theme to the presentation.")
            .parameter(Parameter.builder("themeId")
                .description("Theme ID")
                .validValues("minimal", "corporate", "academic", "excudo")
                .required(true)
                .build())
            .example("apply-theme minimal")
            .example("apply-theme corporate")
            .build();

        schemas.put("apply-theme", schema);
    }

    private static void registerListThemesCommand() {
        CommandSchema schema = CommandSchema.builder("list-themes")
            .description("List available bundled themes")
            .example("list-themes")
            .build();

        schemas.put("list-themes", schema);
    }

    private static void registerNewCommand() {
        CommandSchema schema = CommandSchema.builder("new")
            .description("Create a new presentation from scratch")
            .llmEnabled(true)
            .llmAlias("new-presentation")
            .llmDescription("DESTRUCTIVE: Reset and create a blank presentation with a theme. Destroys all current slides. Only use when explicitly asked to start over.")
            .parameter(Parameter.builder("themeId")
                .description("Theme ID")
                .validValues("minimal", "corporate", "academic", "excudo")
                .defaultValue("minimal")
                .build())
            .example("new minimal")
            .example("new corporate")
            .build();

        schemas.put("new", schema);
    }

    private static void registerShowThemeCommand() {
        CommandSchema schema = CommandSchema.builder("show-theme")
            .description("Show complete theme details")
            .parameter(Parameter.builder("themeId")
                .description("Theme ID to inspect")
                .type(ParameterType.STRING)
                .required(true)
                .build())
            .example("show-theme minimal")
            .example("show-theme corporate")
            .build();

        schemas.put("show-theme", schema);
    }

    private static void registerCreateThemeCommand() {
        CommandSchema schema = CommandSchema.builder("create-theme")
            .description("Create a new theme by duplicating an existing one")
            .parameter(Parameter.builder("id")
                .description("New theme ID")
                .type(ParameterType.STRING)
                .required(true)
                .build())
            .parameter(Parameter.builder("baseTheme")
                .description("Base theme to duplicate (default: minimal)")
                .type(ParameterType.STRING)
                .required(false)
                .build())
            .parameter(Parameter.builder("displayName")
                .description("Display name for the new theme")
                .type(ParameterType.STRING)
                .required(false)
                .build())
            .example("create-theme my-theme")
            .example("create-theme my-theme corporate \"My Custom Theme\"")
            .build();

        schemas.put("create-theme", schema);
    }

    private static void registerEditThemeCommand() {
        CommandSchema schema = CommandSchema.builder("edit-theme")
            .description("Edit a custom theme property")
            .parameter(Parameter.builder("themeId")
                .description("Theme ID to edit")
                .type(ParameterType.STRING)
                .required(true)
                .build())
            .parameter(Parameter.builder("property")
                .description("Property to edit (color.<name>, majorFont, minorFont, displayName)")
                .type(ParameterType.STRING)
                .required(true)
                .build())
            .parameter(Parameter.builder("value")
                .description("New value")
                .type(ParameterType.STRING)
                .required(true)
                .build())
            .example("edit-theme my-theme color.accent1 FF5733")
            .example("edit-theme my-theme majorFont Georgia")
            .build();

        schemas.put("edit-theme", schema);
    }

    private static void registerDeleteThemeCommand() {
        CommandSchema schema = CommandSchema.builder("delete-theme")
            .description("Delete a custom theme")
            .parameter(Parameter.builder("themeId")
                .description("Theme ID to delete")
                .type(ParameterType.STRING)
                .required(true)
                .build())
            .example("delete-theme my-theme")
            .build();

        schemas.put("delete-theme", schema);
    }

    private static void registerRemoveShapeCommand() {
        CommandSchema schema = CommandSchema.builder("remove-shape")
            .description("Remove a shape from a slide")
            .llmEnabled(true)
            .parameter(Parameter.builder("slide")
                .description("Slide number")
                .type(ParameterType.SLIDE_NUMBER)
                .llmName("slideNumber")
                .required(true)
                .build())
            .parameter(Parameter.builder("spid")
                .description("Shape ID to remove")
                .type(ParameterType.SPID)
                .llmName("targetSpid")
                .required(true)
                .build())
            .example("remove-shape 1 5")
            .build();

        schemas.put("remove-shape", schema);
    }

    private static void registerEditBulletCommand() {
        CommandSchema schema = CommandSchema.builder("edit-bullet")
            .description("Edit bullet points in a shape")
            .llmEnabled(true)
            .llmAlias("bullet-point-edit")
            .llmDescription("Bullet point operations on a text shape.")
            .parameter(Parameter.builder("slide")
                .description("Slide number")
                .type(ParameterType.SLIDE_NUMBER)
                .llmName("slideNumber")
                .required(true)
                .build())
            .parameter(Parameter.builder("spid")
                .description("Shape ID containing bullets")
                .type(ParameterType.SPID)
                .llmName("targetSpid")
                .required(true)
                .build())
            .parameter(Parameter.builder("operation")
                .description("Bullet operation")
                .validValues("add", "edit", "remove", "reorder")
                .llmName("editType")
                .required(true)
                .build())
            .parameter(Parameter.builder("index")
                .description("Bullet index (0-based, -1 to append)")
                .type(ParameterType.INTEGER)
                .llmName("bulletIndex")
                .required(true)
                .build())
            .parameter(Parameter.builder("text")
                .description("New text content (target index for reorder)")
                .llmName("content")
                .required(false)
                .build())
            .example("edit-bullet 1 3 add -1 \"New bullet point\"")
            .example("edit-bullet 1 3 edit 0 \"Updated text\"")
            .build();

        schemas.put("edit-bullet", schema);
    }

    private static void registerListShapeTypesCommand() {
        CommandSchema schema = CommandSchema.builder("list-shape-types")
            .description("List all available shape types by category")
            .example("list-shape-types")
            .build();

        schemas.put("list-shape-types", schema);
    }

    private static void registerSetBodyPropsCommand() {
        CommandSchema schema = CommandSchema.builder("set-body-props")
            .description("Set body properties on a shape")
            .parameter(Parameter.builder("slide")
                .description("Slide number")
                .type(ParameterType.SLIDE_NUMBER)
                .required(true)
                .build())
            .parameter(Parameter.builder("spid")
                .description("Shape ID")
                .type(ParameterType.SPID)
                .required(true)
                .build())
            .parameter(Parameter.builder("anchor")
                .description("Vertical alignment: t, ctr, b")
                .type(ParameterType.STRING)
                .required(false)
                .build())
            .parameter(Parameter.builder("vert")
                .description("Text direction: vert, vert270, wordArtVert")
                .type(ParameterType.STRING)
                .required(false)
                .build())
            .parameter(Parameter.builder("autofit")
                .description("Autofit mode: none, normal, shape")
                .type(ParameterType.STRING)
                .required(false)
                .build())
            .parameter(Parameter.builder("columns")
                .description("Number of text columns")
                .type(ParameterType.INTEGER)
                .required(false)
                .build())
            .parameter(Parameter.builder("wrap")
                .description("Text wrap mode: square, none")
                .type(ParameterType.STRING)
                .required(false)
                .build())
            .parameter(Parameter.builder("textbox")
                .description("Mark shape as textbox (true/false)")
                .type(ParameterType.STRING)
                .required(false)
                .build())
            .example("set-body-props 1 3 --anchor ctr")
            .example("set-body-props 1 3 --autofit shape --textbox true")
            .example("set-body-props 1 3 --vert vert270 --columns 2")
            .build();

        schemas.put("set-body-props", schema);
    }

    private static void registerSetTextCommand() {
        CommandSchema schema = CommandSchema.builder("set-text")
            .description("Set rich text body on a shape from JSON")
            .parameter(Parameter.builder("slide")
                .description("Slide number")
                .type(ParameterType.SLIDE_NUMBER)
                .required(true)
                .build())
            .parameter(Parameter.builder("spid")
                .description("Shape ID")
                .type(ParameterType.SPID)
                .required(true)
                .build())
            .parameter(Parameter.builder("json")
                .description("JSON text body definition")
                .required(true)
                .variableLength(true)
                .build())
            .example("set-text 1 3 '[{\"runs\":[{\"text\":\"Bold\",\"b\":true}]}]'")
            .build();

        schemas.put("set-text", schema);
    }

    private static void registerAddNotesCommand() {
        CommandSchema schema = CommandSchema.builder("add-notes")
            .description("Add speaker notes to a slide")
            .parameter(Parameter.builder("slide")
                .description("Slide number")
                .type(ParameterType.SLIDE_NUMBER)
                .required(true)
                .build())
            .parameter(Parameter.builder("text")
                .description("Notes text content")
                .required(true)
                .variableLength(true)
                .build())
            .example("add-notes 1 \"Speaker notes for slide 1\"")
            .example("add-notes --slide 2 --text \"Key talking points for this slide\"")
            .build();

        schemas.put("add-notes", schema);
    }

    private static void registerAddConnectorCommand() {
        CommandSchema schema = CommandSchema.builder("add-connector")
            .description("Add a connector shape to a slide")
            .parameter(Parameter.builder("slide")
                .description("Slide number")
                .type(ParameterType.SLIDE_NUMBER)
                .required(true)
                .build())
            .parameter(Parameter.builder("type")
                .description("Connector type: line, straight, elbow, curved")
                .type(ParameterType.STRING)
                .required(true)
                .build())
            .parameter(Parameter.builder("x")
                .description("X position in EMUs")
                .type(ParameterType.DOUBLE)
                .required(true)
                .build())
            .parameter(Parameter.builder("y")
                .description("Y position in EMUs")
                .type(ParameterType.DOUBLE)
                .required(true)
                .build())
            .parameter(Parameter.builder("width")
                .description("Width in EMUs")
                .type(ParameterType.DOUBLE)
                .required(true)
                .build())
            .parameter(Parameter.builder("height")
                .description("Height in EMUs")
                .type(ParameterType.DOUBLE)
                .required(true)
                .build())
            .parameter(Parameter.builder("head-end")
                .description("Head end type: none, triangle, arrow")
                .type(ParameterType.STRING)
                .required(false)
                .build())
            .parameter(Parameter.builder("tail-end")
                .description("Tail end type: none, triangle, arrow")
                .type(ParameterType.STRING)
                .required(false)
                .build())
            .parameter(Parameter.builder("line-color")
                .description("Line color: hex (FF0000) or scheme (accent1)")
                .type(ParameterType.STRING)
                .required(false)
                .build())
            .parameter(Parameter.builder("start")
                .description("Start binding: spid:idx")
                .type(ParameterType.STRING)
                .required(false)
                .build())
            .parameter(Parameter.builder("end")
                .description("End binding: spid:idx")
                .type(ParameterType.STRING)
                .required(false)
                .build())
            .parameter(Parameter.builder("path")
                .description("Custom geometry path: M x y L x y C x1 y1 x2 y2 x y")
                .type(ParameterType.STRING)
                .required(false)
                .build())
            .example("add-connector 1 line 100 100 500 0")
            .example("add-connector 1 elbow 100 100 500 300 --tail-end triangle")
            .example("add-connector 1 line 100 100 500 0 --start 3:2 --end 5:0")
            .build();

        schemas.put("add-connector", schema);
    }

    private static void registerSetActionCommand() {
        CommandSchema schema = CommandSchema.builder("set-action")
            .description("Set hyperlink action on a shape")
            .parameter(Parameter.builder("slide")
                .description("Slide number")
                .type(ParameterType.SLIDE_NUMBER)
                .required(true)
                .build())
            .parameter(Parameter.builder("spid")
                .description("Shape ID")
                .type(ParameterType.SPID)
                .required(true)
                .build())
            .parameter(Parameter.builder("action")
                .description("Action type: nextslide, previousslide, firstslide, lastslide, endshow, noaction")
                .type(ParameterType.STRING)
                .required(true)
                .build())
            .parameter(Parameter.builder("sound")
                .description("Audio file path to embed")
                .type(ParameterType.STRING)
                .required(false)
                .build())
            .example("set-action 1 5 nextslide")
            .example("set-action 1 5 noaction --sound applause.wav")
            .build();

        schemas.put("set-action", schema);
    }

    private static void registerCopySlideCommand() {
        CommandSchema schema = CommandSchema.builder("copy")
            .description("Copy a slide to a new position")
            .llmEnabled(true)
            .llmAlias("slide-copy")
            .parameter(Parameter.builder("slide")
                .description("Source slide number")
                .type(ParameterType.SLIDE_NUMBER)
                .llmName("sourceSlide")
                .required(true)
                .build())
            .parameter(Parameter.builder("position")
                .description("Target position for the copy")
                .type(ParameterType.INTEGER)
                .llmName("targetPosition")
                .required(true)
                .build())
            .parameter(Parameter.builder("title")
                .description("New title for the copied slide")
                .llmName("newTitle")
                .required(false)
                .build())
            .example("copy 1 3")
            .example("copy 2 5 \"Updated Title\"")
            .build();

        schemas.put("copy", schema);
    }

    private static void registerMoveSlideCommand() {
        CommandSchema schema = CommandSchema.builder("move")
            .description("Move a slide to a new position")
            .parameter(Parameter.builder("from")
                .description("Current slide position")
                .type(ParameterType.SLIDE_NUMBER)
                .required(true)
                .build())
            .parameter(Parameter.builder("to")
                .description("Target position")
                .type(ParameterType.INTEGER)
                .required(true)
                .build())
            .example("move 3 1")
            .example("move 5 2")
            .build();

        schemas.put("move", schema);
    }

    private static void registerSetTransitionCommand() {
        CommandSchema schema = CommandSchema.builder("set-transition")
            .description("Set a slide transition effect")
            .llmEnabled(true)
            .llmDescription("Set slide transition effect.")
            .parameter(Parameter.builder("slide")
                .description("Slide number")
                .type(ParameterType.SLIDE_NUMBER)
                .llmName("slideNumber")
                .required(true)
                .build())
            .parameter(Parameter.builder("type")
                .description("Transition type (fade, wipe-left, push-down, dissolve, etc.)")
                .required(true)
                .build())
            .parameter(Parameter.builder("speed")
                .description("Transition speed")
                .validValues("slow", "med", "fast")
                .required(false)
                .build())
            .parameter(Parameter.builder("advance")
                .description("Auto-advance time in milliseconds (e.g., 5000 = 5 seconds)")
                .type(ParameterType.INTEGER)
                .llmName("advanceMs")
                .required(false)
                .build())
            .example("set-transition 1 fade")
            .example("set-transition 3 wipe-left --speed slow --advance 5000")
            .build();

        schemas.put("set-transition", schema);
    }

    private static void registerRemoveTransitionCommand() {
        CommandSchema schema = CommandSchema.builder("remove-transition")
            .description("Remove transition from a slide")
            .parameter(Parameter.builder("slide")
                .description("Slide number")
                .type(ParameterType.SLIDE_NUMBER)
                .required(true)
                .build())
            .example("remove-transition 3")
            .build();

        schemas.put("remove-transition", schema);
    }

    private static void registerListTransitionTypesCommand() {
        CommandSchema schema = CommandSchema.builder("list-transition-types")
            .description("List all available transition types")
            .example("list-transition-types")
            .build();

        schemas.put("list-transition-types", schema);
    }

    /**
     * Parse a command line input
     */
    public static ParsedCommand parse(String commandLine) throws CommandParseException {
        String[] parts = CommandLineParser.parseCommand(commandLine);
        if (parts.length == 0) {
            throw new CommandParseException("Empty command");
        }
        
        String commandName = parts[0];
        CommandSchema schema = getSchema(commandName);
        
        if (schema == null) {
            String suggestion = suggestCommand(commandName);
            throw new CommandParseException(
                String.format("Unknown command: %s%s", commandName, suggestion));
        }
        
        String[] args = Arrays.copyOfRange(parts, 1, parts.length);
        
        try {
            return schema.parse(args);
        } catch (CommandParseException e) {
            // Add helpful suggestions
            String suggestion = schema.generateSuggestion(args);
            if (!suggestion.isEmpty()) {
                throw new CommandParseException(e.getMessage() + "\n\n" + suggestion);
            }
            throw e;
        }
    }
    
    private static void registerMoveShapeCommand() {
        CommandSchema schema = CommandSchema.builder("move")
            .description("Move a shape to a new position")
            .llmEnabled(true)
            .llmAlias("shape-move")
            .llmDescription("Move a shape to a position.")
            .parameter(Parameter.builder("slide")
                .description("Slide number")
                .type(ParameterType.SLIDE_NUMBER)
                .llmName("slideNumber")
                .required(true)
                .build())
            .parameter(Parameter.builder("spid")
                .description("Shape ID")
                .type(ParameterType.SPID)
                .required(true)
                .build())
            .parameter(Parameter.builder("x")
                .description("New X position (points, EMU, or inches: 100pt, 1270000emu, 1.5in)")
                .required(true)
                .build())
            .parameter(Parameter.builder("y")
                .description("New Y position (points, EMU, or inches: 100pt, 1270000emu, 1.5in)")
                .required(true)
                .build())
            .example("move 1 5 100pt 200pt")
            .build();

        schemas.put("move", schema);
    }

    private static void registerResizeShapeCommand() {
        CommandSchema schema = CommandSchema.builder("resize")
            .description("Resize a shape")
            .llmEnabled(true)
            .llmAlias("shape-resize")
            .llmDescription("Resize a shape.")
            .parameter(Parameter.builder("slide")
                .description("Slide number")
                .type(ParameterType.SLIDE_NUMBER)
                .llmName("slideNumber")
                .required(true)
                .build())
            .parameter(Parameter.builder("spid")
                .description("Shape ID")
                .type(ParameterType.SPID)
                .required(true)
                .build())
            .parameter(Parameter.builder("width")
                .description("New width (points, EMU, or inches)")
                .required(true)
                .build())
            .parameter(Parameter.builder("height")
                .description("New height (points, EMU, or inches)")
                .required(true)
                .build())
            .example("resize 1 5 400pt 300pt")
            .build();

        schemas.put("resize", schema);
    }

    private static void registerArrangeCommand() {
        CommandSchema schema = CommandSchema.builder("arrange")
            .description("Arrange shapes on a slide (align, distribute, match, center, snap)")
            .llmEnabled(true)
            .llmAlias("shape-arrange")
            .llmDescription("Arrange shapes: align, distribute, match, center, snap.")
            .parameter(Parameter.builder("slide")
                .description("Slide number")
                .type(ParameterType.SLIDE_NUMBER)
                .llmName("slideNumber")
                .required(true)
                .build())
            .parameter(Parameter.builder("operation")
                .description("Arrange operation")
                .validValues("align-left", "align-right", "align-top", "align-bottom",
                    "align-center-h", "align-center-v", "distribute-h", "distribute-v",
                    "match-width", "match-height", "match-size", "center-on-slide", "snap-to-grid")
                .required(true)
                .build())
            .parameter(Parameter.builder("targets")
                .description("Shape selector: SPIDs (2,3,4), all, type:rectangle, text:*, name:Title*")
                .required(true)
                .build())
            .parameter(Parameter.builder("anchor")
                .description("Anchor shape SPID for alignment/match reference")
                .type(ParameterType.SPID)
                .required(false)
                .build())
            .example("arrange 1 align-left 2,3,4,5")
            .example("arrange 1 distribute-h type:rectangle")
            .example("arrange 1 match-width all --anchor 3")
            .build();

        schemas.put("arrange", schema);
    }

    private static void registerReorderCommand() {
        CommandSchema schema = CommandSchema.builder("reorder")
            .description("Change z-order of a shape")
            .llmEnabled(true)
            .llmAlias("shape-reorder")
            .llmDescription("Change z-order of a shape.")
            .parameter(Parameter.builder("slide")
                .description("Slide number")
                .type(ParameterType.SLIDE_NUMBER)
                .llmName("slideNumber")
                .required(true)
                .build())
            .parameter(Parameter.builder("spid")
                .description("Shape ID")
                .type(ParameterType.SPID)
                .required(true)
                .build())
            .parameter(Parameter.builder("direction")
                .description("Direction: front, back, forward, backward")
                .validValues("front", "back", "forward", "backward")
                .required(true)
                .build())
            .example("reorder 1 5 front")
            .build();

        schemas.put("reorder", schema);
    }

    private static void registerDuplicateLayoutCommand() {
        CommandSchema schema = CommandSchema.builder("duplicate-layout")
            .description("Duplicate an existing slide layout with a new name")
            .llmEnabled(true)
            .llmAlias("duplicate-layout")
            .llmDescription("Duplicate a slide layout. Use to create custom layouts before creating slides.")
            .parameter(Parameter.builder("sourceLayoutId")
                .description("Source layout ID (e.g., slideLayout2)")
                .required(true)
                .build())
            .parameter(Parameter.builder("name")
                .description("Display name for the new layout")
                .required(true)
                .build())
            .example("duplicate-layout slideLayout2 \"Custom Title and Image\"")
            .build();

        schemas.put("duplicate-layout", schema);
    }

    private static void registerAddLayoutCommand() {
        CommandSchema schema = CommandSchema.builder("add-layout")
            .description("Create a new layout from scratch")
            .llmEnabled(true)
            .llmAlias("add-layout")
            .llmDescription("Create a layout by duplicating the closest existing layout type.")
            .parameter(Parameter.builder("name")
                .description("Display name for the layout")
                .required(true)
                .build())
            .parameter(Parameter.builder("type")
                .description("Layout type")
                .validValues("BLANK", "TITLE_SLIDE", "TITLE_CONTENT", "TWO_CONTENT",
                    "COMPARISON", "TITLE_ONLY", "SECTION_HEADER")
                .build())
            .parameter(Parameter.builder("placeholders")
                .description("JSON array of placeholder definitions")
                .build())
            .example("add-layout \"Full Bleed\" --type BLANK")
            .build();

        schemas.put("add-layout", schema);
    }

    private static void registerDeleteLayoutCommand() {
        CommandSchema schema = CommandSchema.builder("delete-layout")
            .description("Delete a slide layout (fails if in use)")
            .llmEnabled(true)
            .llmAlias("delete-layout")
            .llmDescription("Delete a layout. Fails if any slides use it.")
            .parameter(Parameter.builder("layoutId")
                .description("Layout ID to delete (e.g., slideLayout11)")
                .required(true)
                .build())
            .example("delete-layout slideLayout11")
            .build();

        schemas.put("delete-layout", schema);
    }

    private static void registerRenameLayoutCommand() {
        CommandSchema schema = CommandSchema.builder("rename-layout")
            .description("Rename a layout's display name")
            .llmEnabled(true)
            .llmAlias("rename-layout")
            .llmDescription("Rename a layout.")
            .parameter(Parameter.builder("layoutId")
                .description("Layout ID to rename")
                .required(true)
                .build())
            .parameter(Parameter.builder("name")
                .description("New display name")
                .required(true)
                .build())
            .example("rename-layout slideLayout2 \"Title and Image\"")
            .build();

        schemas.put("rename-layout", schema);
    }

    private static void registerAddPlaceholderCommand() {
        CommandSchema schema = CommandSchema.builder("add-placeholder")
            .description("Add a placeholder to an existing layout")
            .llmEnabled(true)
            .llmAlias("add-placeholder")
            .llmDescription("Add a placeholder shape to a layout.")
            .parameter(Parameter.builder("layoutId")
                .description("Layout ID (e.g., slideLayout11)")
                .required(true)
                .build())
            .parameter(Parameter.builder("type")
                .description("Placeholder type (obj, pic, title, body, chart, tbl)")
                .validValues("obj", "pic", "title", "body", "ctrTitle", "subTitle", "chart", "tbl", "dt", "ftr", "sldNum")
                .build())
            .parameter(Parameter.builder("idx")
                .description("Placeholder index")
                .type(ParameterType.INTEGER)
                .required(true)
                .build())
            .parameter(Parameter.builder("x")
                .description("X position in EMUs")
                .type(ParameterType.DOUBLE)
                .required(true)
                .build())
            .parameter(Parameter.builder("y")
                .description("Y position in EMUs")
                .type(ParameterType.DOUBLE)
                .required(true)
                .build())
            .parameter(Parameter.builder("cx")
                .description("Width in EMUs")
                .type(ParameterType.DOUBLE)
                .required(true)
                .build())
            .parameter(Parameter.builder("cy")
                .description("Height in EMUs")
                .type(ParameterType.DOUBLE)
                .required(true)
                .build())
            .example("add-placeholder slideLayout11 --type pic --idx 3 --x 838200 --y 1825625 --cx 4838700 --cy 4351338")
            .build();

        schemas.put("add-placeholder", schema);
    }

    private static void registerRemovePlaceholderCommand() {
        CommandSchema schema = CommandSchema.builder("remove-placeholder")
            .description("Remove a placeholder from a layout by idx")
            .llmEnabled(true)
            .llmAlias("remove-placeholder")
            .llmDescription("Remove a placeholder shape from a layout.")
            .parameter(Parameter.builder("layoutId")
                .description("Layout ID (e.g., slideLayout11)")
                .required(true)
                .build())
            .parameter(Parameter.builder("idx")
                .description("Placeholder index to remove")
                .type(ParameterType.INTEGER)
                .required(true)
                .build())
            .example("remove-placeholder slideLayout11 2")
            .build();

        schemas.put("remove-placeholder", schema);
    }

    private static void registerListArrangeOpsCommand() {
        CommandSchema schema = CommandSchema.builder("list-arrange-ops")
            .description("List available arrange operations")
            .build();

        schemas.put("list-arrange-ops", schema);
    }

    private static void registerSetFontCommand() {
        CommandSchema schema = CommandSchema.builder("set-font")
            .description("Set font properties on a shape's text")
            .llmEnabled(true)
            .llmAlias("shape-set-font")
            .llmDescription("Set font properties on a shape.")
            .parameter(Parameter.builder("slide")
                .description("Slide number")
                .type(ParameterType.SLIDE_NUMBER)
                .llmName("slideNumber")
                .required(true)
                .build())
            .parameter(Parameter.builder("spid")
                .description("Shape ID")
                .type(ParameterType.SPID)
                .llmName("targetSpid")
                .required(true)
                .build())
            .parameter(Parameter.builder("family")
                .description("Font family name (e.g. Arial, Calibri)")
                .type(ParameterType.STRING)
                .required(false)
                .build())
            .parameter(Parameter.builder("size")
                .description("Font size in points")
                .type(ParameterType.INTEGER)
                .required(false)
                .build())
            .parameter(Parameter.builder("bold")
                .description("Bold: true or false")
                .type(ParameterType.STRING)
                .required(false)
                .build())
            .parameter(Parameter.builder("italic")
                .description("Italic: true or false")
                .type(ParameterType.STRING)
                .required(false)
                .build())
            .parameter(Parameter.builder("underline")
                .description("Underline: true or false")
                .type(ParameterType.STRING)
                .required(false)
                .build())
            .parameter(Parameter.builder("color")
                .description("Font color as hex (e.g. FF0000) or scheme name (e.g. accent1)")
                .type(ParameterType.STRING)
                .required(false)
                .build())
            .example("set-font 1 2 --family Arial --size 24 --bold true")
            .example("set-font 1 2 --color FF0000 --italic true")
            .build();

        schemas.put("set-font", schema);
    }

    private static void registerSetStyleCommand() {
        CommandSchema schema = CommandSchema.builder("set-style")
            .description("Set fill and line style on an existing shape")
            .llmEnabled(true)
            .llmAlias("shape-set-style")
            .llmDescription("Set fill and line style on a shape.")
            .parameter(Parameter.builder("slide")
                .description("Slide number")
                .type(ParameterType.SLIDE_NUMBER)
                .llmName("slideNumber")
                .required(true)
                .build())
            .parameter(Parameter.builder("spid")
                .description("Shape ID")
                .type(ParameterType.SPID)
                .llmName("targetSpid")
                .required(true)
                .build())
            .parameter(Parameter.builder("fill-color")
                .description("Fill color: hex (FF0000) or scheme name (accent1)")
                .type(ParameterType.STRING)
                .required(false)
                .build())
            .parameter(Parameter.builder("line-color")
                .description("Line/border color: hex (FF0000) or scheme name (accent1)")
                .type(ParameterType.STRING)
                .required(false)
                .build())
            .example("set-style 1 3 --fill-color FF5733")
            .example("set-style 1 3 --fill-color accent2 --line-color dk1")
            .build();

        schemas.put("set-style", schema);
    }

    private static void registerDuplicateCommand() {
        CommandSchema schema = CommandSchema.builder("duplicate")
            .description("Duplicate a shape on the same slide")
            .llmEnabled(true)
            .llmAlias("shape-duplicate")
            .llmDescription("Duplicate a shape on the same slide.")
            .parameter(Parameter.builder("slide")
                .description("Slide number")
                .type(ParameterType.SLIDE_NUMBER)
                .llmName("slideNumber")
                .required(true)
                .build())
            .parameter(Parameter.builder("spid")
                .description("Shape ID to duplicate")
                .type(ParameterType.SPID)
                .llmName("targetSpid")
                .required(true)
                .build())
            .parameter(Parameter.builder("offset-x")
                .description("Horizontal offset for the clone (points, EMU, or inches). Defaults to 0.5in.")
                .type(ParameterType.STRING)
                .required(false)
                .build())
            .parameter(Parameter.builder("offset-y")
                .description("Vertical offset for the clone (points, EMU, or inches). Defaults to 0.5in.")
                .type(ParameterType.STRING)
                .required(false)
                .build())
            .example("duplicate 1 3")
            .example("duplicate 1 3 --offset-x 1in --offset-y 1in")
            .build();

        schemas.put("duplicate", schema);
    }

    /**
     * Suggest similar command names
     */
    private static String suggestCommand(String input) {
        String closest = findClosestCommand(input);
        if (closest != null) {
            return String.format("\nDid you mean: %s?", closest);
        }
        return "\nAvailable commands: " + String.join(", ", getCommandNames());
    }
    
    /**
     * Find closest matching command using edit distance
     */
    private static String findClosestCommand(String input) {
        return FuzzyMatcher.findClosestMatch(input, getCommandNames(), 3);
    }

    /**
     * Register the group command schema.
     * Usage: group &lt;slide&gt; --spids &lt;spid1,spid2,...&gt;
     */
    private static void registerGroupCommand() {
        CommandSchema schema = CommandSchema.builder("group")
            .description("Group multiple shapes into a single group shape. Returns group SPID.")
            .llmEnabled(true)
            .parameter(Parameter.builder("slide")
                .description("Slide number")
                .type(ParameterType.SLIDE_NUMBER)
                .llmName("slideNumber")
                .required(true)
                .build())
            .parameter(Parameter.builder("spids")
                .description("Comma-separated list of SPIDs to group (minimum 2)")
                .type(ParameterType.STRING)
                .required(true)
                .build())
            .example("group 1 --spids 2,3,4")
            .build();

        schemas.put("group", schema);
    }

    /**
     * Register the ungroup command schema.
     * Usage: ungroup &lt;slide&gt; --spid &lt;spid&gt;
     */
    private static void registerUngroupCommand() {
        CommandSchema schema = CommandSchema.builder("ungroup")
            .description("Dissolve a group shape back into individual child shapes")
            .llmEnabled(true)
            .parameter(Parameter.builder("slide")
                .description("Slide number")
                .type(ParameterType.SLIDE_NUMBER)
                .llmName("slideNumber")
                .required(true)
                .build())
            .parameter(Parameter.builder("spid")
                .description("SPID of the group shape to dissolve")
                .type(ParameterType.SPID)
                .llmName("targetSpid")
                .required(true)
                .build())
            .example("ungroup 1 --spid 5")
            .build();

        schemas.put("ungroup", schema);
    }

    /**
     * Register the copy-style command schema.
     * Usage: copy-style &lt;slide&gt; --source &lt;spid&gt; --targets &lt;spid1,spid2,...&gt;
     */
    private static void registerCopyStyleCommand() {
        CommandSchema schema = CommandSchema.builder("copy-style")
            .description("Copy the visual style (fill, line) from one shape to one or more target shapes")
            .parameter(Parameter.builder("slide")
                .description("Slide number")
                .type(ParameterType.SLIDE_NUMBER)
                .required(true)
                .build())
            .parameter(Parameter.builder("source")
                .description("SPID of the shape whose style will be copied")
                .type(ParameterType.SPID)
                .required(true)
                .build())
            .parameter(Parameter.builder("targets")
                .description("Comma-separated list of target SPIDs that will receive the copied style")
                .type(ParameterType.STRING)
                .required(true)
                .build())
            .example("copy-style 1 --source 2 --targets 3,4,5")
            .build();

        schemas.put("copy-style", schema);
    }

    /**
     * Register the icon command group.
     * Usage: icon &lt;subcommand&gt; [args...]
     */
    private static void registerIconCommand() {
        CommandSchema schema = CommandSchema.builder("icon")
            .description("Icon management: search, upload, list, sources")
            .parameter(Parameter.builder("subcommand")
                .description("Operation: search, upload, list, sources, help")
                .validValues("search", "upload", "list", "sources", "help")
                .required(true)
                .build())
            .parameter(Parameter.builder("args")
                .description("Subcommand arguments")
                .type(ParameterType.STRING)
                .required(false)
                .variableLength(true)
                .build())
            .example("icon search database")
            .example("icon upload ~/icons/logo.svg company-logo")
            .example("icon list")
            .example("icon sources")
            .build();

        schemas.put("icon", schema);
    }

    private static void registerEditMasterStyleCommand() {
        CommandSchema schema = CommandSchema.builder("edit-master-style")
            .description("Edit text style levels on the slide master")
            .llmEnabled(true)
            .llmAlias("edit-master-style")
            .llmDescription("Edit slide master text styles (titleStyle, bodyStyle, otherStyle) per level.")
            .parameter(Parameter.builder("target")
                .description("Style target: title, body, or other")
                .validValues("title", "body", "other")
                .required(true)
                .build())
            .parameter(Parameter.builder("level")
                .description("Style level (1-9)")
                .type(ParameterType.INTEGER)
                .required(true)
                .build())
            .parameter(Parameter.builder("fontSize")
                .description("Font size in points (e.g., 36)")
                .type(ParameterType.INTEGER)
                .required(false)
                .build())
            .parameter(Parameter.builder("bold")
                .description("Bold text (true/false)")
                .type(ParameterType.STRING)
                .required(false)
                .build())
            .parameter(Parameter.builder("color")
                .description("Color scheme reference (e.g., tx1, dk1)")
                .type(ParameterType.STRING)
                .required(false)
                .build())
            .parameter(Parameter.builder("bullet")
                .description("Bullet character")
                .type(ParameterType.STRING)
                .required(false)
                .build())
            .parameter(Parameter.builder("bulletFont")
                .description("Bullet font name")
                .type(ParameterType.STRING)
                .required(false)
                .build())
            .parameter(Parameter.builder("margin")
                .description("Left margin in EMUs")
                .type(ParameterType.INTEGER)
                .required(false)
                .build())
            .parameter(Parameter.builder("indent")
                .description("Text indent in EMUs")
                .type(ParameterType.INTEGER)
                .required(false)
                .build())
            .example("edit-master-style title --level 1 --fontSize 36 --bold true --color tx1")
            .example("edit-master-style body --level 1 --fontSize 20 --bullet *")
            .example("edit-master-style other --level 1 --fontSize 14 --color tx1")
            .build();

        schemas.put("edit-master-style", schema);
    }

    private static void registerEditMasterClrMapCommand() {
        CommandSchema schema = CommandSchema.builder("edit-master-clrmap")
            .description("Change the slide master color mapping (dark/light switch)")
            .llmEnabled(true)
            .llmAlias("edit-master-clrmap")
            .llmDescription("Edit the slide master clrMap to switch between dark and light backgrounds.")
            .parameter(Parameter.builder("bg1")
                .description("Theme color for bg1 (e.g., dk1 for dark, lt1 for light)")
                .type(ParameterType.STRING)
                .required(false)
                .build())
            .parameter(Parameter.builder("tx1")
                .description("Theme color for tx1 (e.g., lt1 for dark bg, dk1 for light bg)")
                .type(ParameterType.STRING)
                .required(false)
                .build())
            .parameter(Parameter.builder("bg2")
                .description("Theme color for bg2")
                .type(ParameterType.STRING)
                .required(false)
                .build())
            .parameter(Parameter.builder("tx2")
                .description("Theme color for tx2")
                .type(ParameterType.STRING)
                .required(false)
                .build())
            .example("edit-master-clrmap --bg1 dk1 --tx1 lt1")
            .build();

        schemas.put("edit-master-clrmap", schema);
    }

    private static void registerEditMasterBgCommand() {
        CommandSchema schema = CommandSchema.builder("edit-master-bg")
            .description("Change slide master background")
            .llmEnabled(true)
            .llmAlias("edit-master-bg")
            .llmDescription("Set the slide master background fill index and scheme color.")
            .parameter(Parameter.builder("fill-idx")
                .description("Background fill index (e.g., 1001)")
                .type(ParameterType.INTEGER)
                .required(false)
                .build())
            .parameter(Parameter.builder("color")
                .description("Scheme color reference (e.g., bg1)")
                .type(ParameterType.STRING)
                .required(false)
                .build())
            .example("edit-master-bg --fill-idx 1001 --color bg1")
            .build();

        schemas.put("edit-master-bg", schema);
    }

    private static void registerShowMasterCommand() {
        CommandSchema schema = CommandSchema.builder("show-master")
            .description("Display current slide master state")
            .llmEnabled(true)
            .llmAlias("show-master")
            .llmDescription("Display clrMap, txStyles, background, placeholders, and object defaults.")
            .example("show-master")
            .build();

        schemas.put("show-master", schema);
    }

    private static void registerSetObjectDefaultsCommand() {
        CommandSchema schema = CommandSchema.builder("set-object-defaults")
            .description("Populate theme objectDefaults for consistent shape styling")
            .llmEnabled(true)
            .llmAlias("set-object-defaults")
            .llmDescription("Set a:objectDefaults in theme XML so non-placeholder shapes inherit theme-consistent styling.")
            .parameter(Parameter.builder("font-color")
                .description("Scheme color for default shape text (e.g., tx1)")
                .type(ParameterType.STRING)
                .required(false)
                .build())
            .parameter(Parameter.builder("line-width")
                .description("Default line width in EMUs (e.g., 25400 for 2pt)")
                .type(ParameterType.INTEGER)
                .required(false)
                .build())
            .parameter(Parameter.builder("fill-color")
                .description("Scheme color for default shape fill")
                .type(ParameterType.STRING)
                .required(false)
                .build())
            .example("set-object-defaults --font-color tx1 --line-width 25400")
            .build();

        schemas.put("set-object-defaults", schema);
    }
}