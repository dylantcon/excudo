package com.excudo.core.parsing;

import java.util.*;
import com.excudo.utils.FuzzyMatcher;

/**
 * Schema lookup for all registered commands.
 *
 * <p>Every command in the system is class-registered: each {@code XCommand}
 * declares a {@code public static final CommandSchema SCHEMA} field and a
 * {@code public static Command fromParameters(CommandParameters, CommandContext)}
 * factory. {@code com.excudo.core.commands.CommandClassRegistry} derives the
 * canonical name from the class (PascalCase → kebab-case, trailing
 * {@code "Command"} stripped), stamps it onto the schema, and calls
 * {@link #addSchema(String, CommandSchema)} to populate the lookup map here.
 *
 * <p>This class is a pure schema map + a static-init reflection hop into
 * {@code CommandClassRegistry}. The reflection exists only because
 * {@code core/parsing} (this package) compiles before {@code core/commands}
 * in the build order, so a direct import would be cyclic.
 */
public class CommandRegistry {
    private static final Map<String, CommandSchema> schemas = new HashMap<>();

    static {
        // Trigger CommandClassRegistry's static init so its self-describing
        // Commands populate the schemas map before any external lookup.
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
     * All LLM-enabled command names, sorted alphabetically. Used for
     * "valid types: ..." error messages and the system-prompt command reference.
     */
    public static List<String> getLlmEnabledCommandNames() {
        List<String> names = new ArrayList<>();
        for (CommandSchema schema : schemas.values()) {
            if (schema.isLlmEnabled()) {
                names.add(schema.getName());
            }
        }
        Collections.sort(names);
        return names;
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