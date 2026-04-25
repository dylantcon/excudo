package com.excudo.core.commands;

/**
 * GoF Command Pattern Interface
 * 
 * This interface defines the standard Command pattern from the Gang of Four
 * Design Patterns book. It provides execute/undo capabilities with proper
 * encapsulation of requests as objects.
 * 
 * Key principles:
 * - Encapsulates a request as an object
 * - Allows parameterization of clients with different requests
 * - Allows queuing of requests, logging, and undo operations
 * - Supports macro commands (composite commands)
 */
public interface Command {
    
    /**
     * Execute the command.
     * This method should perform the actual work of the command.
     * 
     * @throws CommandExecutionException if the command cannot be executed
     */
    void execute();
    
    /**
     * Undo the command.
     * This method should reverse the effects of execute().
     * Should only be called after execute() has been successfully called.
     * 
     * @throws CommandExecutionException if the command cannot be undone
     */
    void undo();
    
    /**
     * Check if this command can be undone.
     * 
     * @return true if undo() can be called safely, false otherwise
     */
    boolean canUndo();
    
    /**
     * Get a description of what this command does.
     * Useful for logging, debugging, and user interfaces.
     * 
     * @return human-readable description of the command
     */
    String getDescription();
    
    /**
     * Check if this command has been executed.
     *
     * @return true if execute() has been called, false otherwise
     */
    boolean isExecuted();

    /**
     * Canonical name of this command, derived from the class name by
     * stripping a trailing {@code Command} suffix and converting the
     * remaining PascalCase to kebab-case. {@code AddShapeCommand} →
     * {@code add-shape}, {@code CreateCodeBoxCommand} → {@code create-code-box}.
     *
     * <p>Implementations whose canonical name doesn't follow this
     * convention can override this method (e.g. anonymous batch wrappers
     * or composites with non-trivial human-readable names). Most won't
     * need to.
     *
     * <p>Centralizing the name here removes the magic-string registration
     * pattern at every {@code CommandSchema.builder("...")} call site
     * and makes a class rename the only edit needed to rename the
     * command across REPL + MCP surfaces.
     */
    default String getCommandName() {
        String simple = getClass().getSimpleName();
        String stripped = simple.endsWith("Command")
            ? simple.substring(0, simple.length() - "Command".length())
            : simple;
        return toKebabCase(stripped);
    }

    /** {@code AddShape} → {@code add-shape}; {@code SetURL} → {@code set-u-r-l}.
     *  Acronym handling is intentionally simple: callers that want different
     *  output override {@link #getCommandName()} directly. */
    private static String toKebabCase(String pascal) {
        if (pascal == null || pascal.isEmpty()) return pascal;
        StringBuilder sb = new StringBuilder(pascal.length() + 4);
        for (int i = 0; i < pascal.length(); i++) {
            char c = pascal.charAt(i);
            if (i > 0 && Character.isUpperCase(c)) sb.append('-');
            sb.append(Character.toLowerCase(c));
        }
        return sb.toString();
    }
}