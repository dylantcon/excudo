package com.excudo.core.mermaid.parser.flowchart;

import com.excudo.core.mermaid.ast.*;
import com.excudo.core.mermaid.parser.*;

import java.util.List;
import com.excudo.core.utils.Logger;
import com.excudo.core.utils.ComponentLogger;

/**
 * Recursive descent parser for mermaid flowchart syntax.
 * Produces a DiagramGraph from a token stream.
 *
 * Grammar (simplified):
 *   flowchart   := GRAPH_DECL DIRECTION sep (statement sep)* EOF
 *   statement   := subgraph | edge_chain | node_decl | class_def | style_decl
 *   edge_chain  := node_ref (edge_op node_ref)+
 *   node_ref    := IDENTIFIER shape_def?
 *   shape_def   := '[' text ']' | '(' text ')' | '{' text '}' | ...
 *   edge_op     := '-->' | '---' | '-.->' | '==>' | '--x' | '--o' | '<-->'
 *   subgraph    := SUBGRAPH id sep (statement sep)* END
 */
public class FlowchartParser {

    private static final ComponentLogger logger = Logger.getLogger(FlowchartParser.class);

    private final List<Token> tokens;
    private int pos;
    private DiagramGraph graph;

    private FlowchartParser(List<Token> tokens) {
        this.tokens = tokens;
        this.pos = 0;
    }

    /**
     * Parse mermaid flowchart source into a DiagramGraph.
     */
    public static DiagramGraph parse(String source) {
        Lexer lexer = new Lexer(FlowchartTokens.rules(), source.strip());
        List<Token> tokens = lexer.tokenize();
        FlowchartParser parser = new FlowchartParser(tokens);
        return parser.parseFlowchart();
    }

    private DiagramGraph parseFlowchart() {
        // Expect: GRAPH_DECL DIRECTION
        expect(TokenType.GRAPH_DECL);
        Token dirToken = expect(TokenType.DIRECTION);
        Direction direction = Direction.fromCode(dirToken.value());
        graph = new DiagramGraph(direction);

        skipSeparators();

        // Parse statements until EOF
        while (!check(TokenType.EOF)) {
            parseStatement(null);
            skipSeparators();
        }

        return graph;
    }

    private void parseStatement(Subgraph currentSubgraph) {
        if (check(TokenType.EOF) || check(TokenType.END)) return;

        if (check(TokenType.SUBGRAPH)) {
            parseSubgraph();
        } else if (check(TokenType.CLASS_DEF)) {
            parseClassDef();
        } else if (check(TokenType.STYLE)) {
            parseStyleDecl();
        } else if (check(TokenType.CLASS_ASSIGN) && peek().type() == TokenType.CLASS_ASSIGN) {
            // "class X,Y className" syntax
            parseClassAssign();
        } else if (check(TokenType.IDENTIFIER)) {
            parseEdgeChainOrNode(currentSubgraph);
        }
        // else: skip unknown tokens (defensive)
    }

    /**
     * Parse a node reference (possibly with shape definition), then check for edge chains.
     * Mermaid allows: A --> B --> C (chained edges)
     */
    private void parseEdgeChainOrNode(Subgraph currentSubgraph) {
        String nodeId = parseNodeRef(currentSubgraph);

        // Check for edge chain
        while (isEdgeToken()) {
            EdgeArrowType edgeType = parseEdgeType();
            boolean bidirectional = false;
            if (peek(-1).type() == TokenType.BIARROW) {
                bidirectional = true;
            }

            // Check for edge label: -->|text| or edge followed by pipe
            String edgeLabel = null;
            if (check(TokenType.PIPE)) {
                advance(); // consume |
                edgeLabel = parseTextUntil(TokenType.PIPE);
                expect(TokenType.PIPE);
            }

            // Parse target node
            String targetId = parseNodeRef(currentSubgraph);

            DiagramEdge edge = new DiagramEdge(nodeId, targetId, edgeType, edgeLabel);
            edge.setBidirectional(bidirectional);
            graph.addEdge(edge);

            nodeId = targetId; // for chaining: A --> B --> C
        }
    }

    /**
     * Parse a node ID and optional shape definition.
     * Returns the node ID.
     */
    private String parseNodeRef(Subgraph currentSubgraph) {
        Token idToken = expect(TokenType.IDENTIFIER);
        String nodeId = idToken.value();

        DiagramNode node = graph.getOrCreateNode(nodeId);

        // Check for shape definition
        if (isShapeOpen()) {
            parseShapeDef(node);
        }

        // Check for style class shorthand :::className
        if (check(TokenType.CLASS_ASSIGN) && peek().value().equals(":::")) {
            advance(); // consume :::
            if (check(TokenType.IDENTIFIER)) {
                node.addStyleClass(advance().value());
            }
        }

        if (currentSubgraph != null && !currentSubgraph.childNodeIds().contains(nodeId)) {
            currentSubgraph.addChildNodeId(nodeId);
        }

        return nodeId;
    }

    private void parseShapeDef(DiagramNode node) {
        Token open = peek();

        switch (open.type()) {
            case OPEN_DOUBLE_BRACKET -> {
                advance(); // [[
                node.setLabel(parseTextUntil(TokenType.CLOSE_DOUBLE_BRACKET));
                expect(TokenType.CLOSE_DOUBLE_BRACKET);
                node.setShapeHint(NodeShapeHint.SUBROUTINE);
            }
            case OPEN_DOUBLE_PAREN -> {
                advance(); // ((
                node.setLabel(parseTextUntil(TokenType.CLOSE_DOUBLE_PAREN));
                expect(TokenType.CLOSE_DOUBLE_PAREN);
                node.setShapeHint(NodeShapeHint.CIRCLE);
            }
            case OPEN_DOUBLE_BRACE -> {
                advance(); // {{
                node.setLabel(parseTextUntil(TokenType.CLOSE_DOUBLE_BRACE));
                expect(TokenType.CLOSE_DOUBLE_BRACE);
                node.setShapeHint(NodeShapeHint.HEXAGON);
            }
            case OPEN_PAREN_BRACKET -> {
                advance(); // ([
                node.setLabel(parseTextUntil(TokenType.CLOSE_BRACKET_PAREN));
                expect(TokenType.CLOSE_BRACKET_PAREN);
                node.setShapeHint(NodeShapeHint.STADIUM);
            }
            case OPEN_BRACKET_PAREN -> {
                advance(); // [(
                node.setLabel(parseTextUntil(TokenType.CLOSE_PAREN_BRACKET));
                expect(TokenType.CLOSE_PAREN_BRACKET);
                node.setShapeHint(NodeShapeHint.CYLINDER);
            }
            case OPEN_SLASH -> {
                advance(); // [/
                String text = parseTextUntil(TokenType.CLOSE_SLASH, TokenType.CLOSE_BACKSLASH);
                if (check(TokenType.CLOSE_BACKSLASH)) {
                    expect(TokenType.CLOSE_BACKSLASH); // [/ ... \]  = trapezoid
                    node.setShapeHint(NodeShapeHint.TRAPEZOID);
                } else {
                    expect(TokenType.CLOSE_SLASH); // [/ ... /]  = parallelogram
                    node.setShapeHint(NodeShapeHint.PARALLELOGRAM);
                }
                node.setLabel(text);
            }
            case OPEN_BACKSLASH -> {
                advance(); // [\
                String text = parseTextUntil(TokenType.CLOSE_BACKSLASH, TokenType.CLOSE_SLASH);
                if (check(TokenType.CLOSE_SLASH)) {
                    expect(TokenType.CLOSE_SLASH); // [\ ... /]  = trapezoid alt
                    node.setShapeHint(NodeShapeHint.TRAPEZOID_ALT);
                } else {
                    expect(TokenType.CLOSE_BACKSLASH); // [\ ... \]  = parallelogram alt
                    node.setShapeHint(NodeShapeHint.PARALLELOGRAM_ALT);
                }
                node.setLabel(text);
            }
            case OPEN_BRACKET -> {
                advance(); // [
                node.setLabel(parseTextUntil(TokenType.CLOSE_BRACKET));
                expect(TokenType.CLOSE_BRACKET);
                node.setShapeHint(NodeShapeHint.RECT);
            }
            case OPEN_PAREN -> {
                advance(); // (
                node.setLabel(parseTextUntil(TokenType.CLOSE_PAREN));
                expect(TokenType.CLOSE_PAREN);
                node.setShapeHint(NodeShapeHint.ROUND_RECT);
            }
            case OPEN_BRACE -> {
                advance(); // {
                node.setLabel(parseTextUntil(TokenType.CLOSE_BRACE));
                expect(TokenType.CLOSE_BRACE);
                node.setShapeHint(NodeShapeHint.DIAMOND);
            }
            case GT -> {
                advance(); // >
                node.setLabel(parseTextUntil(TokenType.CLOSE_BRACKET));
                expect(TokenType.CLOSE_BRACKET);
                node.setShapeHint(NodeShapeHint.ASYMMETRIC);
            }
            default -> {} // no shape def, just an ID reference
        }
    }

    private void parseSubgraph() {
        expect(TokenType.SUBGRAPH);
        String subId;
        String subLabel;

        // subgraph id [label]
        Token idToken = expect(TokenType.IDENTIFIER);
        subId = idToken.value();
        subLabel = subId;

        // Optional label in brackets
        if (check(TokenType.OPEN_BRACKET)) {
            advance();
            subLabel = parseTextUntil(TokenType.CLOSE_BRACKET);
            expect(TokenType.CLOSE_BRACKET);
        }

        Subgraph sub = new Subgraph(subId, subLabel);
        graph.addSubgraph(sub);
        skipSeparators();

        while (!check(TokenType.END) && !check(TokenType.EOF)) {
            parseStatement(sub);
            skipSeparators();
        }

        if (check(TokenType.END)) advance();
    }

    private void parseClassDef() {
        advance(); // classDef
        logger.debug("classDef parsed but not yet applied to shapes");
        while (!check(TokenType.NEWLINE) && !check(TokenType.SEMICOLON) && !check(TokenType.EOF)) {
            advance();
        }
    }

    private void parseStyleDecl() {
        advance(); // style
        logger.debug("style directive parsed but not yet applied to shapes");
        while (!check(TokenType.NEWLINE) && !check(TokenType.SEMICOLON) && !check(TokenType.EOF)) {
            advance();
        }
    }

    private void parseClassAssign() {
        advance(); // class
        while (!check(TokenType.NEWLINE) && !check(TokenType.SEMICOLON) && !check(TokenType.EOF)) {
            advance();
        }
    }

    // --- Edge type parsing ---

    private boolean isEdgeToken() {
        if (pos >= tokens.size()) return false;
        TokenType t = peek().type();
        return t == TokenType.ARROW || t == TokenType.OPEN_LINK ||
               t == TokenType.DOTTED_ARROW || t == TokenType.DOTTED_LINK ||
               t == TokenType.THICK_ARROW || t == TokenType.THICK_LINK ||
               t == TokenType.CROSS_END || t == TokenType.CIRCLE_END_LINK ||
               t == TokenType.BIARROW;
    }

    private EdgeArrowType parseEdgeType() {
        Token t = advance();
        return switch (t.type()) {
            case ARROW -> EdgeArrowType.ARROW;
            case OPEN_LINK -> EdgeArrowType.OPEN;
            case DOTTED_ARROW -> EdgeArrowType.DOTTED_ARROW;
            case DOTTED_LINK -> EdgeArrowType.DOTTED;
            case THICK_ARROW -> EdgeArrowType.THICK_ARROW;
            case THICK_LINK -> EdgeArrowType.THICK;
            case CROSS_END -> EdgeArrowType.CROSS;
            case CIRCLE_END_LINK -> EdgeArrowType.CIRCLE_END;
            case BIARROW -> EdgeArrowType.ARROW;
            default -> throw new ParseException("Expected edge token, got: " + t);
        };
    }

    // --- Shape detection ---

    private boolean isShapeOpen() {
        if (pos >= tokens.size()) return false;
        TokenType t = peek().type();
        return t == TokenType.OPEN_BRACKET || t == TokenType.OPEN_PAREN ||
               t == TokenType.OPEN_BRACE || t == TokenType.GT ||
               t == TokenType.OPEN_DOUBLE_BRACKET || t == TokenType.OPEN_DOUBLE_PAREN ||
               t == TokenType.OPEN_DOUBLE_BRACE || t == TokenType.OPEN_PAREN_BRACKET ||
               t == TokenType.OPEN_BRACKET_PAREN || t == TokenType.OPEN_SLASH ||
               t == TokenType.OPEN_BACKSLASH;
    }

    // --- Text accumulation ---

    private String parseTextUntil(TokenType... stopTypes) {
        StringBuilder sb = new StringBuilder();
        while (!check(TokenType.EOF)) {
            for (TokenType stop : stopTypes) {
                if (check(stop)) return sb.toString().strip();
            }
            // Also stop at newlines/semicolons
            if (check(TokenType.NEWLINE) || check(TokenType.SEMICOLON)) {
                return sb.toString().strip();
            }

            Token t = advance();
            // Strip quotes from quoted strings
            if (t.type() == TokenType.QUOTED_STRING) {
                sb.append(t.value(), 1, t.value().length() - 1);
            } else {
                if (sb.length() > 0) sb.append(" ");
                sb.append(t.value());
            }
        }
        return sb.toString().strip();
    }

    // --- Token stream helpers ---

    private Token peek() {
        return tokens.get(pos);
    }

    private Token peek(int offset) {
        int idx = pos + offset;
        if (idx < 0 || idx >= tokens.size()) return tokens.get(tokens.size() - 1);
        return tokens.get(idx);
    }

    private boolean check(TokenType type) {
        return pos < tokens.size() && tokens.get(pos).type() == type;
    }

    private Token advance() {
        Token t = tokens.get(pos);
        pos++;
        return t;
    }

    private Token expect(TokenType type) {
        if (!check(type)) {
            Token actual = pos < tokens.size() ? tokens.get(pos) : null;
            throw new ParseException("Expected " + type + " but got " +
                (actual != null ? actual : "EOF") + " at position " + pos);
        }
        return advance();
    }

    private void skipSeparators() {
        while (check(TokenType.NEWLINE) || check(TokenType.SEMICOLON)) {
            advance();
        }
    }

    public static class ParseException extends RuntimeException {
        public ParseException(String message) {
            super(message);
        }
    }
}
