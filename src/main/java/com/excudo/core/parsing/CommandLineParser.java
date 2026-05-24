package com.excudo.core.parsing;

import java.util.ArrayList;
import java.util.List;

/**
 * Utility class for parsing command line input into tokens, handling quoted strings properly.
 *
 * This is a core parsing utility that doesn't depend on console-specific classes,
 * avoiding compilation order issues between core.parsing and console.utils packages.
 */
public class CommandLineParser {

    /**
     * Parse a command string into tokens, handling quoted strings.
     * Supports both single and double quotes.
     *
     * Examples:
     * - "create 1 'New Slide Title'" → ["create", "1", "New Slide Title"]
     * - "edit-content 1 2 \"Hello World\"" → ["edit-content", "1", "2", "Hello World"]
     * - "add-shape 1 text 100 200 300 400" → ["add-shape", "1", "text", "100", "200", "300", "400"]
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
        // Track whether the current token was explicitly introduced by a
        // quote so an empty quoted string (e.g. `""`) is preserved as an
        // intentional empty token. Without this, `edit-content 1 4 ""`
        // tokenizes to three tokens and the schema's variable-length
        // fallback silently substitutes "" -- but any caller that relied
        // on "received '' means user typed ''" would disagree.
        boolean currentTokenQuoted = false;

        for (int i = 0; i < command.length(); i++) {
            char c = command.charAt(i);

            if (!inQuotes && (c == '"' || c == '\'')) {
                // Starting a quoted string -- mark this token as intentional
                // so an immediate close-quote still emits an empty token.
                inQuotes = true;
                quoteChar = c;
                currentTokenQuoted = true;
            } else if (inQuotes && c == quoteChar) {
                // Ending the quoted string -- unescape sequences in the completed token
                String unescaped = unescapeString(currentToken.toString());
                currentToken = new StringBuilder(unescaped);
                inQuotes = false;
                quoteChar = '\0';
            } else if (!inQuotes && Character.isWhitespace(c)) {
                // Whitespace outside quotes - end current token
                if (currentToken.length() > 0 || currentTokenQuoted) {
                    tokens.add(currentToken.toString());
                    currentToken = new StringBuilder();
                    currentTokenQuoted = false;
                }
            } else {
                // Regular character or whitespace inside quotes
                currentToken.append(c);
            }
        }

        // Add the last token if any -- including intentionally-empty quoted ones.
        if (currentToken.length() > 0 || currentTokenQuoted) {
            tokens.add(currentToken.toString());
        }

        return tokens.toArray(new String[0]);
    }

    /**
     * Unescape common escape sequences in a string extracted from quotes.
     * Converts literal backslash-n to newline, backslash-t to tab, etc.
     */
    private static String unescapeString(String input) {
        StringBuilder result = new StringBuilder(input.length());
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c == '\\' && i + 1 < input.length()) {
                char next = input.charAt(i + 1);
                switch (next) {
                    case 'n': result.append('\n'); i++; break;
                    case 't': result.append('\t'); i++; break;
                    case '\\': result.append('\\'); i++; break;
                    default: result.append(c); break;
                }
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }
}