package com.excudo.core.mermaid.parser;

/**
 * Token types shared across diagram parsers with flowchart-specific extensions.
 */
public enum TokenType {
    // Diagram declaration
    GRAPH_DECL,        // "graph", "flowchart"
    DIRECTION,         // TB, TD, BT, LR, RL

    // Structure
    SUBGRAPH,          // "subgraph"
    END,               // "end"
    SEMICOLON,         // ";"
    NEWLINE,           // line break

    // Node shapes
    OPEN_BRACKET,      // [
    CLOSE_BRACKET,     // ]
    OPEN_PAREN,        // (
    CLOSE_PAREN,       // )
    OPEN_BRACE,        // {
    CLOSE_BRACE,       // }
    OPEN_DOUBLE_BRACKET,  // [[
    CLOSE_DOUBLE_BRACKET, // ]]
    OPEN_DOUBLE_PAREN, // ((
    CLOSE_DOUBLE_PAREN,// ))
    OPEN_PAREN_BRACKET,// ([
    CLOSE_BRACKET_PAREN,// ])
    OPEN_BRACKET_PAREN,// [(
    CLOSE_PAREN_BRACKET,// )]
    OPEN_DOUBLE_BRACE, // {{
    CLOSE_DOUBLE_BRACE,// }}
    GT,                // > (for asymmetric shape)
    OPEN_SLASH,        // [/
    CLOSE_SLASH,       // /]
    OPEN_BACKSLASH,    // [\
    CLOSE_BACKSLASH,   // \]
    OPEN_SLASH_CLOSE_BACKSLASH,  // [/ ... \]
    OPEN_BACKSLASH_CLOSE_SLASH,  // [\ ... /]

    // Edges
    ARROW,             // -->
    OPEN_LINK,         // ---
    DOTTED_ARROW,      // -.->
    DOTTED_LINK,       // -.-
    THICK_ARROW,       // ==>
    THICK_LINK,        // ===
    CROSS_END,         // --x
    CIRCLE_END_LINK,   // --o
    BIARROW,           // <-->
    ARROW_WITH_TEXT,   // -- text --> or -->|text|

    // Content
    IDENTIFIER,        // node IDs
    QUOTED_STRING,     // "text" or 'text'
    PIPE,              // | for edge labels
    TEXT,              // unquoted text content

    // Style
    CLASS_DEF,         // classDef
    CLASS_ASSIGN,      // class or :::
    STYLE,             // style

    // Sequence diagram tokens
    SEQ_DECL,            // "sequenceDiagram"
    PARTICIPANT,         // "participant"
    ACTOR_KW,            // "actor"
    AS,                  // "as"
    SOLID_ARROW_MSG,     // ->>
    DASHED_ARROW_MSG,    // -->>
    SOLID_OPEN_MSG,      // ->
    DASHED_OPEN_MSG,     // -->  (reused in sequence context)
    CROSS_MSG,           // -x
    ASYNC_MSG,           // -)
    COLON,               // :
    ALT,                 // "alt"
    ELSE_KW,             // "else"
    OPT,                 // "opt"
    LOOP,                // "loop"
    PAR,                 // "par"
    AND_KW,              // "and"
    CRITICAL,            // "critical"
    OPTION_KW,           // "option"
    BREAK_KW,            // "break"
    NOTE,                // "Note"
    LEFT_OF,             // "left of"
    RIGHT_OF,            // "right of"
    OVER,                // "over"
    ACTIVATE,            // "activate"
    DEACTIVATE,          // "deactivate"
    COMMA,               // ,
    PLUS,                // +
    MINUS,               // -

    // Misc
    WHITESPACE,
    COMMENT,           // %% comment
    EOF
}
