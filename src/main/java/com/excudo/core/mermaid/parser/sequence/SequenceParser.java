package com.excudo.core.mermaid.parser.sequence;

import com.excudo.core.mermaid.ast.sequence.*;
import com.excudo.core.mermaid.ast.sequence.SequenceBlock.BlockType;
import com.excudo.core.mermaid.ast.sequence.SequenceNote.NotePosition;
import com.excudo.core.mermaid.parser.Lexer;
import com.excudo.core.mermaid.parser.Token;
import com.excudo.core.mermaid.parser.TokenType;

import java.util.ArrayList;
import java.util.List;
import com.excudo.core.utils.Logger;
import com.excudo.core.utils.ComponentLogger;

/**
 * Recursive descent parser for mermaid sequence diagram syntax.
 * Produces a SequenceDiagram AST from source text.
 *
 * Grammar:
 *   sequence     := SEQ_DECL NL (statement NL)* EOF
 *   statement    := participant_decl | message | block | note | activation
 *   participant  := (PARTICIPANT | ACTOR) ID (AS rest_of_line)?
 *   message      := ID arrow_type [+|-] ID COLON rest_of_line
 *   block        := block_kw rest_of_line NL (statement NL)* (alt_section)* END
 *   note         := NOTE position ID (COMMA ID)* COLON rest_of_line
 *   activation   := (ACTIVATE | DEACTIVATE) ID
 */
public class SequenceParser {

    private static final ComponentLogger logger = Logger.getLogger(SequenceParser.class);

    private final List<Token> tokens;
    private int pos;

    private SequenceParser(List<Token> tokens) {
        this.tokens = tokens;
        this.pos = 0;
    }

    public static SequenceDiagram parse(String source) {
        Lexer lexer = new Lexer(SequenceTokens.rules(), source.strip());
        List<Token> tokens = lexer.tokenize();
        SequenceParser parser = new SequenceParser(tokens);
        return parser.parseSequence();
    }

    private SequenceDiagram parseSequence() {
        expect(TokenType.SEQ_DECL);
        skipSeparators();

        SequenceDiagram diagram = new SequenceDiagram();

        while (!check(TokenType.EOF)) {
            parseStatement(diagram, diagram.elements());
            skipSeparators();
        }

        return diagram;
    }

    private void parseStatement(SequenceDiagram diagram, List<SequenceElement> target) {
        if (check(TokenType.EOF) || check(TokenType.END) ||
            check(TokenType.ELSE_KW) || check(TokenType.AND_KW) ||
            check(TokenType.OPTION_KW)) {
            return;
        }

        if (check(TokenType.PARTICIPANT) || check(TokenType.ACTOR_KW)) {
            parseParticipantDecl(diagram);
        } else if (check(TokenType.NOTE)) {
            target.add(parseNote(diagram));
        } else if (check(TokenType.ACTIVATE) || check(TokenType.DEACTIVATE)) {
            target.add(parseActivation(diagram));
        } else if (isBlockOpen()) {
            target.add(parseBlock(diagram));
        } else if (check(TokenType.IDENTIFIER)) {
            target.add(parseMessage(diagram));
        } else {
            // Log and skip unrecognized tokens to next line
            StringBuilder skipped = new StringBuilder();
            while (!check(TokenType.NEWLINE) && !check(TokenType.EOF)) {
                Token t = advance();
                if (skipped.length() > 0) skipped.append(" ");
                skipped.append(t.value());
            }
            if (skipped.length() > 0) {
                logger.debug("Skipping unrecognized input: {}", skipped);
            }
        }
    }

    private void parseParticipantDecl(SequenceDiagram diagram) {
        boolean isActor = check(TokenType.ACTOR_KW);
        advance(); // consume participant/actor

        String id = expect(TokenType.IDENTIFIER).value();
        String label = id;

        if (check(TokenType.AS)) {
            advance(); // consume "as"
            label = readRestOfLine();
        }

        diagram.addParticipant(new SequenceDiagram.Participant(id, label, isActor));
    }

    private SequenceMessage parseMessage(SequenceDiagram diagram) {
        String fromId = expect(TokenType.IDENTIFIER).value();
        diagram.ensureParticipant(fromId);

        MessageArrowType arrowType = parseArrowType();

        // Check for +/- activation suffix before target ID
        boolean activateTarget = false;
        boolean deactivateSource = false;
        if (check(TokenType.PLUS)) {
            advance();
            activateTarget = true;
        } else if (check(TokenType.MINUS)) {
            advance();
            deactivateSource = true;
        }

        String toId = expect(TokenType.IDENTIFIER).value();
        diagram.ensureParticipant(toId);

        String label = "";
        if (check(TokenType.COLON)) {
            advance(); // consume :
            label = readRestOfLine();
        }

        return new SequenceMessage(fromId, toId, label, arrowType, activateTarget, deactivateSource);
    }

    private MessageArrowType parseArrowType() {
        Token t = peek();
        return switch (t.type()) {
            case DASHED_ARROW_MSG -> { advance(); yield MessageArrowType.DASHED_ARROW; }
            case SOLID_ARROW_MSG -> { advance(); yield MessageArrowType.SOLID_ARROW; }
            case DASHED_OPEN_MSG -> { advance(); yield MessageArrowType.DASHED_OPEN; }
            case SOLID_OPEN_MSG -> { advance(); yield MessageArrowType.SOLID_OPEN; }
            case CROSS_MSG -> { advance(); yield MessageArrowType.CROSS; }
            case ASYNC_MSG -> { advance(); yield MessageArrowType.ASYNC; }
            default -> throw new ParseException("Expected arrow type but got " + t + " at position " + pos);
        };
    }

    private SequenceNote parseNote(SequenceDiagram diagram) {
        advance(); // consume "Note"

        NotePosition position;
        if (check(TokenType.LEFT_OF)) {
            advance();
            position = NotePosition.LEFT;
        } else if (check(TokenType.RIGHT_OF)) {
            advance();
            position = NotePosition.RIGHT;
        } else if (check(TokenType.OVER)) {
            advance();
            position = NotePosition.OVER;
        } else {
            throw new ParseException("Expected 'left of', 'right of', or 'over' after Note at position " + pos);
        }

        List<String> participantIds = new ArrayList<>();
        participantIds.add(expect(TokenType.IDENTIFIER).value());
        while (check(TokenType.COMMA)) {
            advance(); // consume ,
            participantIds.add(expect(TokenType.IDENTIFIER).value());
        }

        for (String pid : participantIds) {
            diagram.ensureParticipant(pid);
        }

        String text = "";
        if (check(TokenType.COLON)) {
            advance();
            text = readRestOfLine();
        }

        return new SequenceNote(position, participantIds, text);
    }

    private ActivationChange parseActivation(SequenceDiagram diagram) {
        boolean activate = check(TokenType.ACTIVATE);
        advance(); // consume activate/deactivate

        String id = expect(TokenType.IDENTIFIER).value();
        diagram.ensureParticipant(id);

        return new ActivationChange(id, activate);
    }

    private boolean isBlockOpen() {
        TokenType t = peek().type();
        return t == TokenType.ALT || t == TokenType.OPT || t == TokenType.LOOP ||
               t == TokenType.PAR || t == TokenType.CRITICAL || t == TokenType.BREAK_KW;
    }

    private SequenceBlock parseBlock(SequenceDiagram diagram) {
        BlockType blockType = switch (peek().type()) {
            case ALT -> BlockType.ALT;
            case OPT -> BlockType.OPT;
            case LOOP -> BlockType.LOOP;
            case PAR -> BlockType.PAR;
            case CRITICAL -> BlockType.CRITICAL;
            case BREAK_KW -> BlockType.BREAK;
            default -> throw new ParseException("Expected block keyword at position " + pos);
        };
        advance(); // consume block keyword

        String label = readRestOfLine();
        skipSeparators();

        SequenceBlock block = new SequenceBlock(blockType, label);

        // Parse statements until end/else/and/option
        while (!check(TokenType.END) && !check(TokenType.EOF) &&
               !isAlternateKeyword()) {
            parseStatement(diagram, block.elements());
            skipSeparators();
        }

        // Parse alternate sections
        while (isAlternateKeyword()) {
            TokenType altType = peek().type();
            advance(); // consume else/and/option
            String altLabel = readRestOfLine();
            skipSeparators();

            // Map alternate keyword to a block type for the alternate
            BlockType altBlockType = switch (altType) {
                case ELSE_KW -> BlockType.ALT;
                case AND_KW -> BlockType.PAR;
                case OPTION_KW -> BlockType.CRITICAL;
                default -> blockType;
            };
            SequenceBlock alternate = new SequenceBlock(altBlockType, altLabel);

            while (!check(TokenType.END) && !check(TokenType.EOF) &&
                   !isAlternateKeyword()) {
                parseStatement(diagram, alternate.elements());
                skipSeparators();
            }

            block.addAlternate(alternate);
        }

        if (check(TokenType.END)) advance();

        return block;
    }

    private boolean isAlternateKeyword() {
        TokenType t = peek().type();
        return t == TokenType.ELSE_KW || t == TokenType.AND_KW || t == TokenType.OPTION_KW;
    }

    private String readRestOfLine() {
        StringBuilder sb = new StringBuilder();
        while (!check(TokenType.NEWLINE) && !check(TokenType.EOF)) {
            Token t = advance();
            if (sb.length() > 0) sb.append(" ");
            sb.append(t.value());
        }
        return sb.toString().strip();
    }

    // --- Token stream helpers ---

    private Token peek() {
        return tokens.get(pos);
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
        while (check(TokenType.NEWLINE)) {
            advance();
        }
    }

    public static class ParseException extends RuntimeException {
        public ParseException(String message) {
            super(message);
        }
    }
}
