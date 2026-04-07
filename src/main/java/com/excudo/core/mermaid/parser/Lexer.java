package com.excudo.core.mermaid.parser;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Regex-table tokenizer with state support.
 * Takes an ordered list of (pattern, token type) rules and produces a token stream.
 */
public class Lexer {

    /**
     * A lexer rule: a regex pattern and the token type it produces.
     * If tokenType is null, the match is consumed but not emitted (e.g., whitespace).
     */
    public record LexRule(Pattern pattern, TokenType tokenType) {}

    private final List<LexRule> rules;
    private final String source;
    private int pos;
    private int line;
    private int col;

    public Lexer(List<LexRule> rules, String source) {
        this.rules = rules;
        this.source = source;
        this.pos = 0;
        this.line = 1;
        this.col = 1;
    }

    public List<Token> tokenize() {
        List<Token> tokens = new ArrayList<>();
        while (pos < source.length()) {
            Token token = nextToken();
            if (token != null) {
                tokens.add(token);
            }
        }
        tokens.add(new Token(TokenType.EOF, "", line, col));
        return tokens;
    }

    private Token nextToken() {
        if (pos >= source.length()) return null;

        for (LexRule rule : rules) {
            Matcher m = rule.pattern().matcher(source);
            m.region(pos, source.length());
            if (m.lookingAt()) {
                String matched = m.group();
                int startLine = line;
                int startCol = col;
                advance(matched);
                if (rule.tokenType() == null) {
                    return null; // skip (whitespace, comments)
                }
                return new Token(rule.tokenType(), matched, startLine, startCol);
            }
        }

        throw new LexerException("Unexpected character '" + source.charAt(pos) +
            "' at line " + line + ", col " + col + " (pos " + pos + "): ..." +
            source.substring(pos, Math.min(pos + 20, source.length())) + "...");
    }

    private void advance(String text) {
        for (char c : text.toCharArray()) {
            if (c == '\n') {
                line++;
                col = 1;
            } else {
                col++;
            }
            pos++;
        }
    }

    public static class LexerException extends RuntimeException {
        public LexerException(String message) {
            super(message);
        }
    }
}
