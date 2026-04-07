package com.excudo.core.mermaid.parser.sequence;

import com.excudo.core.mermaid.ast.sequence.*;
import com.excudo.core.mermaid.ast.sequence.SequenceBlock.BlockType;
import com.excudo.core.mermaid.ast.sequence.SequenceNote.NotePosition;
import org.junit.Test;
import static org.junit.Assert.*;

import java.util.List;

public class SequenceParserTest {

    @Test
    public void testSimpleTwoParticipantMessage() {
        SequenceDiagram d = SequenceParser.parse("sequenceDiagram\n    A->>B: Hello");
        assertEquals(2, d.participants().size());
        assertEquals("A", d.participants().get(0).id());
        assertEquals("B", d.participants().get(1).id());
        assertEquals(1, d.elements().size());
        SequenceMessage msg = (SequenceMessage) d.elements().get(0);
        assertEquals("A", msg.fromId());
        assertEquals("B", msg.toId());
        assertEquals("Hello", msg.label());
        assertEquals(MessageArrowType.SOLID_ARROW, msg.arrowType());
    }

    @Test
    public void testAllSixArrowTypes() {
        String src = "sequenceDiagram\n"
            + "A->>B: solid arrow\n"
            + "A-->>B: dashed arrow\n"
            + "A->B: solid open\n"
            + "A-->B: dashed open\n"
            + "A-xB: cross\n"
            + "A-)B: async\n";
        SequenceDiagram d = SequenceParser.parse(src);
        assertEquals(6, d.elements().size());
        assertEquals(MessageArrowType.SOLID_ARROW, ((SequenceMessage) d.elements().get(0)).arrowType());
        assertEquals(MessageArrowType.DASHED_ARROW, ((SequenceMessage) d.elements().get(1)).arrowType());
        assertEquals(MessageArrowType.SOLID_OPEN, ((SequenceMessage) d.elements().get(2)).arrowType());
        assertEquals(MessageArrowType.DASHED_OPEN, ((SequenceMessage) d.elements().get(3)).arrowType());
        assertEquals(MessageArrowType.CROSS, ((SequenceMessage) d.elements().get(4)).arrowType());
        assertEquals(MessageArrowType.ASYNC, ((SequenceMessage) d.elements().get(5)).arrowType());
    }

    @Test
    public void testParticipantAlias() {
        SequenceDiagram d = SequenceParser.parse("sequenceDiagram\n    participant A as Alice\n    A->>B: Hi");
        assertEquals("Alice", d.participants().get(0).label());
        assertEquals("A", d.participants().get(0).id());
    }

    @Test
    public void testActorDeclaration() {
        SequenceDiagram d = SequenceParser.parse("sequenceDiagram\n    actor B as Bob\n    A->>B: Hi");
        // B is declared as actor
        SequenceDiagram.Participant bob = d.participants().stream()
            .filter(p -> p.id().equals("B")).findFirst().orElseThrow();
        assertTrue(bob.isActor());
        assertEquals("Bob", bob.label());
    }

    @Test
    public void testAltElseBlock() {
        String src = "sequenceDiagram\n"
            + "A->>B: Check\n"
            + "alt is sick\n"
            + "    B->>A: Not good\n"
            + "else is well\n"
            + "    B->>A: Feeling fresh\n"
            + "end\n";
        SequenceDiagram d = SequenceParser.parse(src);
        assertEquals(2, d.elements().size()); // message + block
        SequenceBlock block = (SequenceBlock) d.elements().get(1);
        assertEquals(BlockType.ALT, block.type());
        assertEquals("is sick", block.label());
        assertEquals(1, block.elements().size()); // one message in primary
        assertEquals(1, block.alternates().size()); // one else section
        assertEquals("is well", block.alternates().get(0).label());
        assertEquals(1, block.alternates().get(0).elements().size());
    }

    @Test
    public void testLoopBlock() {
        String src = "sequenceDiagram\n"
            + "loop Every minute\n"
            + "    A->>B: Ping\n"
            + "end\n";
        SequenceDiagram d = SequenceParser.parse(src);
        SequenceBlock block = (SequenceBlock) d.elements().get(0);
        assertEquals(BlockType.LOOP, block.type());
        assertEquals("Every minute", block.label());
        assertEquals(1, block.elements().size());
    }

    @Test
    public void testOptBlock() {
        String src = "sequenceDiagram\n"
            + "opt Optional action\n"
            + "    A->>B: Maybe\n"
            + "end\n";
        SequenceDiagram d = SequenceParser.parse(src);
        SequenceBlock block = (SequenceBlock) d.elements().get(0);
        assertEquals(BlockType.OPT, block.type());
    }

    @Test
    public void testParAndBlock() {
        String src = "sequenceDiagram\n"
            + "par Parallel\n"
            + "    A->>B: Task 1\n"
            + "and\n"
            + "    A->>B: Task 2\n"
            + "end\n";
        SequenceDiagram d = SequenceParser.parse(src);
        SequenceBlock block = (SequenceBlock) d.elements().get(0);
        assertEquals(BlockType.PAR, block.type());
        assertEquals(1, block.alternates().size()); // one "and" section
    }

    @Test
    public void testNoteLeftOf() {
        String src = "sequenceDiagram\n    Note left of A: Thinking";
        SequenceDiagram d = SequenceParser.parse(src);
        SequenceNote note = (SequenceNote) d.elements().get(0);
        assertEquals(NotePosition.LEFT, note.position());
        assertEquals("Thinking", note.text());
        assertEquals(List.of("A"), note.participantIds());
    }

    @Test
    public void testNoteRightOf() {
        String src = "sequenceDiagram\n    Note right of B: Happy";
        SequenceDiagram d = SequenceParser.parse(src);
        SequenceNote note = (SequenceNote) d.elements().get(0);
        assertEquals(NotePosition.RIGHT, note.position());
    }

    @Test
    public void testNoteOverMultipleParticipants() {
        String src = "sequenceDiagram\n    Note over A,B: Friends";
        SequenceDiagram d = SequenceParser.parse(src);
        SequenceNote note = (SequenceNote) d.elements().get(0);
        assertEquals(NotePosition.OVER, note.position());
        assertEquals(List.of("A", "B"), note.participantIds());
        assertEquals("Friends", note.text());
    }

    @Test
    public void testActivateDeactivate() {
        String src = "sequenceDiagram\n    activate A\n    deactivate A";
        SequenceDiagram d = SequenceParser.parse(src);
        assertEquals(2, d.elements().size());
        ActivationChange act1 = (ActivationChange) d.elements().get(0);
        assertTrue(act1.activate());
        assertEquals("A", act1.participantId());
        ActivationChange act2 = (ActivationChange) d.elements().get(1);
        assertFalse(act2.activate());
    }

    @Test
    public void testPlusSuffixActivation() {
        String src = "sequenceDiagram\n    A->>+B: Activate on send";
        SequenceDiagram d = SequenceParser.parse(src);
        SequenceMessage msg = (SequenceMessage) d.elements().get(0);
        assertTrue(msg.activateTarget());
        assertFalse(msg.deactivateSource());
    }

    @Test
    public void testMinusSuffixDeactivation() {
        String src = "sequenceDiagram\n    B-->>-A: Deactivate on reply";
        SequenceDiagram d = SequenceParser.parse(src);
        SequenceMessage msg = (SequenceMessage) d.elements().get(0);
        assertFalse(msg.activateTarget());
        assertTrue(msg.deactivateSource());
    }

    @Test
    public void testCommentsSkipped() {
        String src = "sequenceDiagram\n    %% This is a comment\n    A->>B: Hello";
        SequenceDiagram d = SequenceParser.parse(src);
        assertEquals(1, d.elements().size());
    }

    @Test(expected = SequenceParser.ParseException.class)
    public void testInvalidSyntaxThrows() {
        SequenceParser.parse("sequenceDiagram\n    notacommand blah");
    }

    @Test
    public void testSelfMessage() {
        String src = "sequenceDiagram\n    A->>A: Self call";
        SequenceDiagram d = SequenceParser.parse(src);
        SequenceMessage msg = (SequenceMessage) d.elements().get(0);
        assertEquals("A", msg.fromId());
        assertEquals("A", msg.toId());
    }

    @Test
    public void testMultipleMessagesInSequence() {
        String src = "sequenceDiagram\n    A->>B: First\n    B->>C: Second\n    C-->>A: Third";
        SequenceDiagram d = SequenceParser.parse(src);
        assertEquals(3, d.elements().size());
        assertEquals(3, d.participants().size());
    }
}
