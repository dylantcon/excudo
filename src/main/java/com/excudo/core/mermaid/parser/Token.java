package com.excudo.core.mermaid.parser;

/**
 * A lexer token with type, value, and source position.
 */
public record Token(TokenType type, String value, int line, int col) {

    @Override
    public String toString() {
        return type + "('" + value + "' @" + line + ":" + col + ")";
    }
}
