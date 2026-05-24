package com.excudo.core.parsing;

import java.util.*;
import com.excudo.core.parsing.Parameter.ParameterType;
import com.excudo.utils.FuzzyMatcher;

/**
 * Registry of all command schemas.
 * Single source of truth for command definitions.
 *
 * <p>Two parallel registration paths exist during the magic-string-removal
 * migration:
 * <ol>
 *   <li>The legacy {@code register…Command()} static methods below — each
 *       hardcodes the canonical name as a string in
 *       {@code CommandSchema.builder("name")} and writes directly into
 *       {@link #schemas}.</li>
 *   <li>The new class-keyed path in
 *       {@code com.excudo.core.commands.CommandClassRegistry}. A Command
 *       opts in by declaring a {@code public static final CommandSchema SCHEMA}
 *       field and a
 *       {@code public static Command fromParameters(CommandParameters, CommandContext)}
 *       factory. Name derives from the class via {@link Object#getClass()}
 *       (PascalCase → kebab-case, trailing {@code "Command"} stripped). The
 *       class registry calls {@link #addSchema(String, CommandSchema)} so
 *       schema lookups (validators, system-prompt generators, dispatcher)
 *       continue to find the schema uniformly.</li>
 * </ol>
 *
 * <p>The {@code Class.forName} call in the static block triggers
 * {@code CommandClassRegistry}'s static init so its registrations land in
 * {@link #schemas} before any external lookup. The reflection hop exists
 * because {@code core/parsing} (this package) compiles before
 * {@code core/commands} where the class registry lives.
 *
 * <p>Once every command is migrated to (2), the {@code register…Command()}
 * methods can be deleted and the schema map populated entirely via the
 * class registry.
 */
public class CommandRegistry {
    private static final Map<String, CommandSchema> schemas = new HashMap<>();

    static {
        // Legacy schema-only registrations.
        // add-animation: migrated to class registry (AddAnimationCommand)
        // create-slide, delete-slide: migrated to class registry
        // (CreateSlideCommand, DeleteSlideCommand)
        // list-slides: migrated to class registry (ListSlidesCommand)
        // content-edit: migrated to class registry (ContentEditCommand)
        
        // Add missing command schemas identified in Phase 1 audit
        // load, save: migrated to class registry (LoadCommand, SaveCommand)
        // render-slide, show-slide: migrated to class registry
        // (RenderSlideCommand, ShowSlideCommand)
        registerListLayoutsCommand();
        registerListSpidsCommand();
        registerListAnimationsCommand();
        // remove-animation, update-animation: migrated to class registry
        // (RemoveAnimationCommand, UpdateAnimationCommand).
        registerDumpTimingCommand();
        registerDumpShapeCommand();
        registerShowShapeCommand();
        // llm, llm-config: migrated to class registry (LLMCommand, LLMConfigCommand)
        // session-create/list/info/close/switch: migrated to class registry
        // (split from the legacy "session" umbrella; each subcommand is now a
        // top-level command keyed on its class derivation).
        // inject-icon, enhanced-content: migrated to class registry
        // (InjectIconCommand, EnhancedContentCommand)
        // undo: migrated to class registry (UndoCommand.SCHEMA / fromParameters)
        // redo, history: migrated to class registry (RedoCommand, HistoryCommand)
        registerListNotesCommand();
        // new-presentation: migrated to class registry (NewPresentationCommand)
        registerCreateThemeCommand();
        registerEditThemeCommand();
        // remove-shape: migrated to class registry (RemoveShapeCommand)
        // bullet-point-edit: migrated to class registry (BulletPointEditCommand)
        // set-body-props: migrated to class registry (SetBodyPropsCommand)
        // set-text: migrated to class registry (SetTextCommand)
        // add-notes: migrated to class registry (AddNotesCommand)
        // add-connector: migrated to class registry (AddConnectorCommand)
        // set-action: migrated to class registry (SetActionCommand)
        // copy-slide: migrated to class registry (CopySlideCommand)
        // move-slide: migrated to class registry (MoveSlideCommand.SCHEMA / fromParameters)
        // set-transition, remove-transition: migrated to class registry
        // (SetTransitionCommand, RemoveTransitionCommand)
        // move-shape: migrated to class registry (MoveShapeCommand.SCHEMA / fromParameters)
        // resize-shape: migrated to class registry (ResizeShapeCommand)
        registerArrangeCommand();
        // reorder-shape: migrated to class registry (ReorderShapeCommand.SCHEMA / fromParameters)
        registerAddLayoutCommand();
        registerAddPlaceholderCommand();
        registerSetFontCommand();
        registerSetStyleCommand();
        // duplicate-shape: migrated to class registry (DuplicateShapeCommand)
        // group-shapes: migrated to class registry (GroupShapesCommand)
        registerCopyStyleCommand();
        registerIconCommand();
        registerEditMasterStyleCommand();
        registerEditMasterClrMapCommand();
        registerSetObjectDefaultsCommand();

        // Trigger the class-keyed registry so its self-describing Commands
        // populate the schemas map. Reflection because core/parsing
        // compiles before core/commands.
        try {
            Class.forName("com.excudo.core.commands.CommandClassRegistry");
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(
                "CommandClassRegistry class not found -- build is broken", e);
        }
    }

    /**
     * Side-channel for {@code com.excudo.core.commands.CommandClassRegistry}
     * to populate the schemas map for class-registered Commands. Mirrors
     * what the legacy {@code register…Command()} methods do — namely,
     * {@code schemas.put(name, schema)} — so all schema consumers see the
     * same uniform map regardless of registration path.
     */
    public static void addSchema(String name, CommandSchema schema) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(schema, "schema");
        schemas.put(name, schema);
    }

    /**
     * Register the create command
     */
    /**
     * Register the list command
     */
    /**
     * Register edit-content command
     */
    /**
     * Register the help command with proper schema
     */
    
    /**
     * Register the load command with proper schema
     */

    /**
     * Register the show command with proper schema
     */
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
    /**
     * Register the inject command with proper schema
     */
    /**
     * Register the undo command with proper schema
     */
    /**
     * Register the redo command with proper schema
     */
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









    /**
     * Parse a command line input
     */
    public static CommandParameters parse(String commandLine) throws CommandParseException {
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
    

    private static void registerArrangeCommand() {
        CommandSchema schema = CommandSchema.builder("arrange")
            .description("Arrange shapes on a slide (align, distribute, match, center, snap)")
            .llmEnabled(true)
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


    private static void registerAddLayoutCommand() {
        CommandSchema schema = CommandSchema.builder("add-layout")
            .description("Create a new layout from scratch")
            .llmEnabled(true)
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



    private static void registerAddPlaceholderCommand() {
        CommandSchema schema = CommandSchema.builder("add-placeholder")
            .description("Add a placeholder to an existing layout")
            .llmEnabled(true)
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



    private static void registerSetFontCommand() {
        CommandSchema schema = CommandSchema.builder("set-font")
            .description("Set font properties on a shape's text")
            .llmEnabled(true)
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
                .llmName("fillColor")
                .required(false)
                .build())
            .parameter(Parameter.builder("line-color")
                .description("Line/border color: hex (FF0000) or scheme name (accent1)")
                .type(ParameterType.STRING)
                .llmName("lineColor")
                .required(false)
                .build())
            .parameter(Parameter.builder("fill-alpha")
                .description("Fill opacity, 0-100 (percent). 100 = fully opaque (default if "
                    + "fill-color is set), 0 = fully transparent. Use to author muted fills "
                    + "without picking a paler hex.")
                .type(ParameterType.INTEGER)
                .llmName("fillAlpha")
                .required(false)
                .build())
            .parameter(Parameter.builder("line-alpha")
                .description("Line opacity, 0-100 (percent). Same semantics as fill-alpha.")
                .type(ParameterType.INTEGER)
                .llmName("lineAlpha")
                .required(false)
                .build())
            .example("set-style 1 3 --fill-color FF5733")
            .example("set-style 1 3 --fill-color accent2 --line-color dk1")
            .example("set-style 1 3 --fill-color 5B9BD5 --fill-alpha 40  (muted fill)")
            .build();

        schemas.put("set-style", schema);
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

    // group-shapes: migrated to class registry (GroupShapesCommand.SCHEMA / fromParameters)

    /**
     * Register the ungroup command schema.
     * Usage: ungroup &lt;slide&gt; --spid &lt;spid&gt;
     */

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



    private static void registerSetObjectDefaultsCommand() {
        CommandSchema schema = CommandSchema.builder("set-object-defaults")
            .description("Populate theme objectDefaults for consistent shape styling")
            .llmEnabled(true)
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