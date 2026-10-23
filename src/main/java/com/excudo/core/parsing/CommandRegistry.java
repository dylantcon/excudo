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
    /** Alias (lowercase) → canonical command name. */
    private static final Map<String, String> aliases = new HashMap<>();

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
     * Register a short/legacy alias for a canonical command name. Aliases are
     * resolved case-insensitively by {@link #resolveName(String)}, and so by
     * {@link #getSchema(String)} and {@link #parse(String)}. Parsed parameters
     * always carry the canonical name.
     */
    public static void addAlias(String alias, String canonicalName) {
        Objects.requireNonNull(alias, "alias");
        Objects.requireNonNull(canonicalName, "canonicalName");
        String key = alias.toLowerCase(Locale.ROOT);
        if (schemas.containsKey(key)) {
            throw new IllegalStateException(
                "Alias '" + alias + "' collides with the canonical command '" + key + "'");
        }
        String existing = aliases.putIfAbsent(key, canonicalName);
        if (existing != null && !existing.equals(canonicalName)) {
            throw new IllegalStateException("Alias '" + alias + "' already maps to '"
                + existing + "', cannot remap to '" + canonicalName + "'");
        }
    }

    /**
     * Resolve a typed command name (canonical or alias, any case) to its
     * canonical name, or {@code null} if it names no command.
     */
    public static String resolveName(String commandName) {
        if (commandName == null) return null;
        if (schemas.containsKey(commandName)) return commandName;
        String lower = commandName.toLowerCase(Locale.ROOT);
        if (schemas.containsKey(lower)) return lower;
        return aliases.get(lower);
    }

    /** Aliases registered for a canonical command name, sorted. */
    public static List<String> getAliases(String canonicalName) {
        List<String> result = new ArrayList<>();
        for (Map.Entry<String, String> e : aliases.entrySet()) {
            if (e.getValue().equals(canonicalName)) result.add(e.getKey());
        }
        Collections.sort(result);
        return result;
    }

    /** All aliases, alias → canonical name. */
    public static Map<String, String> getAllAliases() {
        return Collections.unmodifiableMap(aliases);
    }

    /**
     * Get schema for a command (canonical name or alias, any case).
     */
    public static CommandSchema getSchema(String commandName) {
        String resolved = resolveName(commandName);
        return resolved == null ? null : schemas.get(resolved);
    }
    
    /**
     * Check if a command is registered (canonical name or alias, any case).
     */
    public static boolean hasCommand(String commandName) {
        return resolveName(commandName) != null;
    }
    
    /**
     * Get all registered canonical command names
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
        int consumed = 1;

        // Two-word spellings of hyphenated commands ("session create",
        // "llm config set", "list slides") resolve to the joined name. Tried
        // first so "llm config" is not read as the llm command with "config".
        CommandSchema schema = null;
        if (parts.length > 1) {
            schema = getSchema(parts[0] + "-" + parts[1]);
            if (schema != null) consumed = 2;
        }
        if (schema == null) {
            schema = getSchema(commandName);
        }
        
        if (schema == null) {
            String suggestion = suggestCommand(commandName);
            throw new CommandParseException(
                String.format("Unknown command: %s%s", commandName, suggestion));
        }
        
        String[] args = Arrays.copyOfRange(parts, consumed, parts.length);
        
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
        return "\nType 'help' to list available commands.";
    }
    
    /**
     * Find closest matching command using edit distance
     */
    private static String findClosestCommand(String input) {
        Set<String> candidates = new HashSet<>(getCommandNames());
        // One- and two-letter aliases sit within edit distance of nearly
        // everything; leave them out of suggestions.
        for (String alias : aliases.keySet()) {
            if (alias.length() >= 3) candidates.add(alias);
        }
        String match = FuzzyMatcher.findClosestMatch(input, candidates, 3);
        if (match == null) return null;
        String canonical = aliases.get(match);
        return canonical != null ? canonical : match;
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