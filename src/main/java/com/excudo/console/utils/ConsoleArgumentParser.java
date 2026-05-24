package com.excudo.console.utils;

import com.excudo.core.commands.mutating.slide.AddShapeCommand;

import java.util.ArrayList;
import java.util.List;

/**
 * Utility class for parsing console command arguments.
 * Centralizes quote-aware argument parsing logic to eliminate DRY violations
 * in AbstractConsoleEngine.
 */
public class ConsoleArgumentParser {
    
    /**
     * Container for parsed command arguments
     */
    public static class ParsedArgs {
        private final String[] args;
        
        public ParsedArgs(String[] args) { 
            this.args = args != null ? args : new String[0]; 
        }
        
        public String[] getArgs() {
            return args;
        }
        
        public int getArgCount() {
            return args.length;
        }
        
        public String getArg(int index) {
            return index >= 0 && index < args.length ? args[index] : null;
        }
        
        public boolean hasMinimumArgs(int minimum) {
            return args.length >= minimum;
        }
    }
    
    /**
     * Parse command arguments handling quoted strings properly with maximum argument limit.
     * This method stops parsing after reaching maxArgs-1 tokens to preserve the remainder.
     * 
     * @param input the input string to parse
     * @param maxArgs maximum number of arguments to parse (remaining content goes to last arg)
     * @return ParsedArgs containing the parsed tokens
     */
    public static ParsedArgs parseQuotedArgs(String input, int maxArgs) {
        if (input == null || input.trim().isEmpty()) {
            return new ParsedArgs(new String[0]);
        }
        
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        boolean escapeNext = false;
        
        for (int i = 0; i < input.length() && result.size() < maxArgs - 1; i++) {
            char c = input.charAt(i);
            
            if (escapeNext) {
                current.append(c);
                escapeNext = false;
            } else if (c == '\\') {
                escapeNext = true;
            } else if (c == '"') {
                inQuotes = !inQuotes;
            } else if (!inQuotes && Character.isWhitespace(c)) {
                if (current.length() > 0) {
                    result.add(current.toString());
                    current = new StringBuilder();
                }
            } else {
                current.append(c);
            }
        }
        
        if (current.length() > 0) {
            result.add(current.toString());
        }
        
        return new ParsedArgs(result.toArray(new String[0]));
    }
    
    /**
     * Parse a command string into tokens, handling quoted strings.
     * Supports both single and double quotes.
     * 
     * Examples:
     * - "create 1 'New Slide Title'" → ["create", "1", "New Slide Title"]
     * - "edit-content 1 2 \"Hello World\"" → ["edit-content", "1", "2", "Hello World"]
     * - "add-shape 1 text 100 200 300 400" → [AddShapeCommand.NAME, "1", "text", "100", "200", "300", "400"]
     * 
     * @param command the command string to parse
     * @return array of parsed tokens
     */
    public static String[] parseCommand(String command) {
        if (command == null || command.trim().isEmpty()) {
            return new String[0];
        }
        
        List<String> tokens = new ArrayList<>();
        StringBuilder currentToken = new StringBuilder();
        boolean inQuotes = false;
        char quoteChar = '\0';
        
        for (int i = 0; i < command.length(); i++) {
            char c = command.charAt(i);
            
            if (!inQuotes && (c == '"' || c == '\'')) {
                // Starting a quoted string
                inQuotes = true;
                quoteChar = c;
            } else if (inQuotes && c == quoteChar) {
                // Ending the quoted string
                inQuotes = false;
                quoteChar = '\0';
            } else if (!inQuotes && Character.isWhitespace(c)) {
                // Whitespace outside quotes - end current token
                if (currentToken.length() > 0) {
                    tokens.add(currentToken.toString());
                    currentToken = new StringBuilder();
                }
            } else {
                // Regular character or whitespace inside quotes
                currentToken.append(c);
            }
        }
        
        // Add final token if exists
        if (currentToken.length() > 0) {
            tokens.add(currentToken.toString());
        }
        
        return tokens.toArray(new String[0]);
    }
    
    /**
     * Build arguments string from parsed tokens starting at a given index.
     * This is used for commands that need to preserve spaces in arguments.
     * 
     * @param parts the parsed command tokens
     * @param startIndex the index to start building from
     * @return arguments string with spaces preserved
     */
    public static String buildArgsString(String[] parts, int startIndex) {
        if (parts == null || startIndex >= parts.length) {
            return "";
        }
        
        StringBuilder argsBuilder = new StringBuilder();
        for (int i = startIndex; i < parts.length; i++) {
            if (i > startIndex) {
                argsBuilder.append(" ");
            }
            argsBuilder.append(parts[i]);
        }
        return argsBuilder.toString();
    }
    
    /**
     * Split arguments with a maximum number of parts.
     * Similar to String.split() but limits the number of resulting parts.
     * 
     * @param args the arguments string
     * @param maxParts maximum number of parts
     * @return array of split arguments
     */
    public static String[] splitArgs(String args, int maxParts) {
        if (args == null || args.trim().isEmpty()) {
            return new String[0];
        }
        
        return args.split("\\s+", maxParts);
    }
    
    /**
     * Parse arguments for commands that require specific minimum argument counts.
     * Provides consistent error handling for insufficient arguments.
     * 
     * @param args the arguments string
     * @param minArgs minimum required arguments
     * @param usage usage message to display on error
     * @return ParsedArgs if successful, null if insufficient arguments
     */
    public static ParsedArgs parseWithMinimum(String args, int minArgs, String usage) {
        String[] parts = parseCommand(args);
        if (parts.length < minArgs) {
            return null; // Caller should handle this by displaying usage
        }
        return new ParsedArgs(parts);
    }
    
    /**
     * Convenience method to parse subcommands (first argument is subcommand, rest are args)
     * 
     * @param input full command input
     * @return ParsedArgs where first arg is subcommand, rest are arguments
     */
    public static ParsedArgs parseSubcommand(String input) {
        return parseQuotedArgs(input, Integer.MAX_VALUE);
    }
}