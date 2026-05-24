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
        // remove-animation, update-animation: migrated to class registry
        // (RemoveAnimationCommand, UpdateAnimationCommand).
        // llm, llm-config: migrated to class registry (LLMCommand, LLMConfigCommand)
        // session-create/list/info/close/switch: migrated to class registry
        // (split from the legacy "session" umbrella; each subcommand is now a
        // top-level command keyed on its class derivation).
        // inject-icon, enhanced-content: migrated to class registry
        // (InjectIconCommand, EnhancedContentCommand)
        // undo: migrated to class registry (UndoCommand.SCHEMA / fromParameters)
        // redo, history: migrated to class registry (RedoCommand, HistoryCommand)
        // new-presentation: migrated to class registry (NewPresentationCommand)
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
        // reorder-shape: migrated to class registry (ReorderShapeCommand.SCHEMA / fromParameters)
        // duplicate-shape: migrated to class registry (DuplicateShapeCommand)
        // group-shapes: migrated to class registry (GroupShapesCommand)

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
    
    /**
     * Register the list-spids command with proper schema
     */
    
    /**
     * Register the list-animations command with proper schema
     */
    
    /**
     * Register the list-animation-types command with proper schema
     */
    
    /**
     * Register the dump-timing command with proper schema
     */
    
    /**
     * Register the dump-shape command with proper schema
     */
    
    /**
     * Register the show-shape command with proper schema
     */
    
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

    /**
     * Register the icon command group.
     * Usage: icon &lt;subcommand&gt; [args...]
     */





}