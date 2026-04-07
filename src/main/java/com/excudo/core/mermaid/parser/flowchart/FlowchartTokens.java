package com.excudo.core.mermaid.parser.flowchart;

import com.excudo.core.mermaid.parser.Lexer.LexRule;
import com.excudo.core.mermaid.parser.TokenType;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Lexer rules for mermaid flowchart syntax.
 * Derived from flow.jison token definitions.
 *
 * Rule order matters: longer/more-specific patterns must precede shorter ones
 * to ensure correct greedy matching.
 */
public class FlowchartTokens {

    private FlowchartTokens() {}

    public static List<LexRule> rules() {
        List<LexRule> rules = new ArrayList<>();

        // Comments (consume, don't emit)
        rules.add(new LexRule(Pattern.compile("%%[^\n]*"), null));

        // Whitespace (consume inline whitespace, don't emit)
        rules.add(new LexRule(Pattern.compile("[ \t]+"), null));

        // Newlines and semicolons (statement separators)
        rules.add(new LexRule(Pattern.compile("\r?\n"), TokenType.NEWLINE));
        rules.add(new LexRule(Pattern.compile(";"), TokenType.SEMICOLON));

        // Keywords (must precede IDENTIFIER)
        rules.add(new LexRule(Pattern.compile("(?:graph|flowchart)(?=\\s)"), TokenType.GRAPH_DECL));
        rules.add(new LexRule(Pattern.compile("subgraph(?=\\s|$)"), TokenType.SUBGRAPH));
        rules.add(new LexRule(Pattern.compile("end(?=\\s|;|$)"), TokenType.END));
        rules.add(new LexRule(Pattern.compile("classDef(?=\\s)"), TokenType.CLASS_DEF));
        rules.add(new LexRule(Pattern.compile("class(?=\\s)"), TokenType.CLASS_ASSIGN));
        rules.add(new LexRule(Pattern.compile("style(?=\\s)"), TokenType.STYLE));

        // Direction keywords
        rules.add(new LexRule(Pattern.compile("(?:TB|TD|BT|LR|RL)(?=\\s|;|$)"), TokenType.DIRECTION));

        // Edge patterns (longer first)
        // Bidirectional
        rules.add(new LexRule(Pattern.compile("<-->"), TokenType.BIARROW));
        rules.add(new LexRule(Pattern.compile("<-.->"), TokenType.BIARROW));
        rules.add(new LexRule(Pattern.compile("<==>"), TokenType.BIARROW));

        // Dotted
        rules.add(new LexRule(Pattern.compile("-\\.->"), TokenType.DOTTED_ARROW));
        rules.add(new LexRule(Pattern.compile("-\\.-"), TokenType.DOTTED_LINK));

        // Thick
        rules.add(new LexRule(Pattern.compile("==>"), TokenType.THICK_ARROW));
        rules.add(new LexRule(Pattern.compile("==="), TokenType.THICK_LINK));

        // Cross and circle ends
        rules.add(new LexRule(Pattern.compile("--x"), TokenType.CROSS_END));
        rules.add(new LexRule(Pattern.compile("--o"), TokenType.CIRCLE_END_LINK));

        // Standard arrows and links (must follow longer patterns)
        rules.add(new LexRule(Pattern.compile("-->"), TokenType.ARROW));
        rules.add(new LexRule(Pattern.compile("---"), TokenType.OPEN_LINK));

        // Pipe for edge labels
        rules.add(new LexRule(Pattern.compile("\\|"), TokenType.PIPE));

        // Double-char delimiters (must precede single-char)
        rules.add(new LexRule(Pattern.compile("\\(\\["), TokenType.OPEN_PAREN_BRACKET));
        rules.add(new LexRule(Pattern.compile("\\]\\)"), TokenType.CLOSE_BRACKET_PAREN));
        rules.add(new LexRule(Pattern.compile("\\[\\("), TokenType.OPEN_BRACKET_PAREN));
        rules.add(new LexRule(Pattern.compile("\\)\\]"), TokenType.CLOSE_PAREN_BRACKET));
        rules.add(new LexRule(Pattern.compile("\\[\\["), TokenType.OPEN_DOUBLE_BRACKET));
        rules.add(new LexRule(Pattern.compile("\\]\\]"), TokenType.CLOSE_DOUBLE_BRACKET));
        rules.add(new LexRule(Pattern.compile("\\(\\("), TokenType.OPEN_DOUBLE_PAREN));
        rules.add(new LexRule(Pattern.compile("\\)\\)"), TokenType.CLOSE_DOUBLE_PAREN));
        rules.add(new LexRule(Pattern.compile("\\{\\{"), TokenType.OPEN_DOUBLE_BRACE));
        rules.add(new LexRule(Pattern.compile("\\}\\}"), TokenType.CLOSE_DOUBLE_BRACE));

        // Slash/backslash shape delimiters
        rules.add(new LexRule(Pattern.compile("\\[/"), TokenType.OPEN_SLASH));
        rules.add(new LexRule(Pattern.compile("/\\]"), TokenType.CLOSE_SLASH));
        rules.add(new LexRule(Pattern.compile("\\[\\\\"), TokenType.OPEN_BACKSLASH));
        rules.add(new LexRule(Pattern.compile("\\\\\\]"), TokenType.CLOSE_BACKSLASH));

        // Single-char delimiters
        rules.add(new LexRule(Pattern.compile("\\["), TokenType.OPEN_BRACKET));
        rules.add(new LexRule(Pattern.compile("\\]"), TokenType.CLOSE_BRACKET));
        rules.add(new LexRule(Pattern.compile("\\("), TokenType.OPEN_PAREN));
        rules.add(new LexRule(Pattern.compile("\\)"), TokenType.CLOSE_PAREN));
        rules.add(new LexRule(Pattern.compile("\\{"), TokenType.OPEN_BRACE));
        rules.add(new LexRule(Pattern.compile("\\}"), TokenType.CLOSE_BRACE));
        rules.add(new LexRule(Pattern.compile(">"), TokenType.GT));

        // Quoted strings
        rules.add(new LexRule(Pattern.compile("\"[^\"]*\""), TokenType.QUOTED_STRING));
        rules.add(new LexRule(Pattern.compile("'[^']*'"), TokenType.QUOTED_STRING));

        // Style class shorthand :::
        rules.add(new LexRule(Pattern.compile(":::"), TokenType.CLASS_ASSIGN));

        // Identifiers (alphanumeric + underscore + hyphens, must start with letter/underscore)
        // Hyphen-continuation requires alphanumeric after the hyphen, so "A--" won't
        // greedily consume into edge syntax (edge rules are matched first).
        rules.add(new LexRule(Pattern.compile("[a-zA-Z_][a-zA-Z0-9_]*(?:-[a-zA-Z0-9_]+)*"), TokenType.IDENTIFIER));

        // Numeric identifiers (standalone numbers as node IDs)
        rules.add(new LexRule(Pattern.compile("[0-9]+"), TokenType.IDENTIFIER));

        return rules;
    }
}
