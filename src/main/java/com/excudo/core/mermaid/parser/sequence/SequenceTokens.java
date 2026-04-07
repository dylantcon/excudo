package com.excudo.core.mermaid.parser.sequence;

import com.excudo.core.mermaid.parser.Lexer.LexRule;
import com.excudo.core.mermaid.parser.TokenType;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Lexer rules for mermaid sequence diagram syntax.
 * Rule order matters: longer/more-specific patterns must precede shorter ones.
 */
public class SequenceTokens {

    private SequenceTokens() {}

    public static List<LexRule> rules() {
        List<LexRule> rules = new ArrayList<>();

        // Comments (consume, don't emit)
        rules.add(new LexRule(Pattern.compile("%%[^\n]*"), null));

        // Whitespace (consume inline, don't emit)
        rules.add(new LexRule(Pattern.compile("[ \t]+"), null));

        // Newlines (statement separator)
        rules.add(new LexRule(Pattern.compile("\r?\n"), TokenType.NEWLINE));

        // Diagram declaration
        rules.add(new LexRule(Pattern.compile("sequenceDiagram"), TokenType.SEQ_DECL));

        // Multi-word tokens (must precede single-word keywords)
        rules.add(new LexRule(Pattern.compile("left\\s+of"), TokenType.LEFT_OF));
        rules.add(new LexRule(Pattern.compile("right\\s+of"), TokenType.RIGHT_OF));

        // Keywords (must precede IDENTIFIER)
        rules.add(new LexRule(Pattern.compile("participant(?=\\s)"), TokenType.PARTICIPANT));
        rules.add(new LexRule(Pattern.compile("actor(?=\\s)"), TokenType.ACTOR_KW));
        rules.add(new LexRule(Pattern.compile("as(?=\\s)"), TokenType.AS));
        rules.add(new LexRule(Pattern.compile("alt(?=\\s|$)"), TokenType.ALT));
        rules.add(new LexRule(Pattern.compile("else(?=\\s|$)"), TokenType.ELSE_KW));
        rules.add(new LexRule(Pattern.compile("opt(?=\\s|$)"), TokenType.OPT));
        rules.add(new LexRule(Pattern.compile("loop(?=\\s|$)"), TokenType.LOOP));
        rules.add(new LexRule(Pattern.compile("par(?=\\s|$)"), TokenType.PAR));
        rules.add(new LexRule(Pattern.compile("and(?=\\s|$)"), TokenType.AND_KW));
        rules.add(new LexRule(Pattern.compile("critical(?=\\s|$)"), TokenType.CRITICAL));
        rules.add(new LexRule(Pattern.compile("option(?=\\s|$)"), TokenType.OPTION_KW));
        rules.add(new LexRule(Pattern.compile("break(?=\\s|$)"), TokenType.BREAK_KW));
        rules.add(new LexRule(Pattern.compile("end(?=\\s|$)"), TokenType.END));
        rules.add(new LexRule(Pattern.compile("Note(?=\\s)"), TokenType.NOTE));
        rules.add(new LexRule(Pattern.compile("over(?=\\s)"), TokenType.OVER));
        rules.add(new LexRule(Pattern.compile("activate(?=\\s)"), TokenType.ACTIVATE));
        rules.add(new LexRule(Pattern.compile("deactivate(?=\\s)"), TokenType.DEACTIVATE));

        // Arrow types (longest first to avoid prefix matching)
        rules.add(new LexRule(Pattern.compile("-->>"), TokenType.DASHED_ARROW_MSG));
        rules.add(new LexRule(Pattern.compile("->>"), TokenType.SOLID_ARROW_MSG));
        rules.add(new LexRule(Pattern.compile("-->"), TokenType.DASHED_OPEN_MSG));
        rules.add(new LexRule(Pattern.compile("-x"), TokenType.CROSS_MSG));
        rules.add(new LexRule(Pattern.compile("-\\)"), TokenType.ASYNC_MSG));
        rules.add(new LexRule(Pattern.compile("->"), TokenType.SOLID_OPEN_MSG));

        // Punctuation
        rules.add(new LexRule(Pattern.compile(":"), TokenType.COLON));
        rules.add(new LexRule(Pattern.compile(","), TokenType.COMMA));
        rules.add(new LexRule(Pattern.compile("\\+"), TokenType.PLUS));
        rules.add(new LexRule(Pattern.compile("-"), TokenType.MINUS));

        // Identifiers (alphanumeric + underscore, no hyphens -- hyphens are arrow prefixes in sequence syntax)
        rules.add(new LexRule(Pattern.compile("[a-zA-Z_][a-zA-Z0-9_]*"), TokenType.IDENTIFIER));

        // Numeric identifiers
        rules.add(new LexRule(Pattern.compile("[0-9]+"), TokenType.IDENTIFIER));

        return rules;
    }
}
