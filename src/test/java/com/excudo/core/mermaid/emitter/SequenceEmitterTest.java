package com.excudo.core.mermaid.emitter;

import com.excudo.core.mermaid.ast.sequence.*;
import com.excudo.core.mermaid.layout.LayoutConfig;
import com.excudo.core.mermaid.layout.sequence.SequenceDiagramLayout;
import com.excudo.core.mermaid.layout.sequence.SequenceLayoutResult;
import org.junit.Test;
import static org.junit.Assert.*;

public class SequenceEmitterTest {

    private DiagramOutput emitSimpleDiagram() {
        SequenceDiagram d = new SequenceDiagram();
        d.addParticipant(new SequenceDiagram.Participant("A", "Alice", false));
        d.addParticipant(new SequenceDiagram.Participant("B", "Bob", false));
        d.addElement(new SequenceMessage("A", "B", "Hello", MessageArrowType.SOLID_ARROW, false, false));
        d.addElement(new SequenceMessage("B", "A", "Hi back", MessageArrowType.DASHED_ARROW, false, false));
        SequenceLayoutResult layout = new SequenceDiagramLayout().layout(d, LayoutConfig.slideContentArea());
        return SequenceEmitter.emit(layout);
    }

    @Test
    public void testCorrectShapeCount() {
        DiagramOutput output = emitSimpleDiagram();
        // 2 lifelines + 2 participants = 4 shapes minimum
        long lifelineCount = output.shapes().stream()
            .filter(s -> s.nodeId().startsWith("lifeline_")).count();
        long participantCount = output.shapes().stream()
            .filter(s -> s.nodeId().startsWith("participant_")).count();
        assertEquals(2, lifelineCount);
        assertEquals(2, participantCount);
    }

    @Test
    public void testCorrectConnectorCount() {
        DiagramOutput output = emitSimpleDiagram();
        // 2 messages = 2 connectors
        assertEquals(2, output.connectors().size());
    }

    @Test
    public void testMessageConnectorDirection() {
        DiagramOutput output = emitSimpleDiagram();
        // First message A->B (left to right): startIdx=3(right), endIdx=1(left)
        ConnectorSpec first = output.connectors().get(0);
        assertEquals(3, first.startIdx());
        assertEquals(1, first.endIdx());
        // Second message B->A (right to left): startIdx=1(left), endIdx=3(right)
        ConnectorSpec second = output.connectors().get(1);
        assertEquals(1, second.startIdx());
        assertEquals(3, second.endIdx());
    }

    @Test
    public void testBlockShapesHaveRoundRect() {
        SequenceDiagram d = new SequenceDiagram();
        d.addParticipant(new SequenceDiagram.Participant("A", "A", false));
        d.addParticipant(new SequenceDiagram.Participant("B", "B", false));
        SequenceBlock block = new SequenceBlock(SequenceBlock.BlockType.LOOP, "Every sec");
        block.addElement(new SequenceMessage("A", "B", "Ping", MessageArrowType.SOLID_ARROW, false, false));
        d.addElement(block);
        SequenceLayoutResult layout = new SequenceDiagramLayout().layout(d, LayoutConfig.slideContentArea());
        DiagramOutput output = SequenceEmitter.emit(layout);
        boolean hasRoundRect = output.shapes().stream()
            .filter(s -> s.nodeId().startsWith("block_"))
            .anyMatch(s -> s.ooxmlPreset().equals("roundRect"));
        assertTrue("Block shapes should use roundRect preset", hasRoundRect);
    }

    @Test
    public void testNoteShapesHaveYellowFill() {
        SequenceDiagram d = new SequenceDiagram();
        d.addParticipant(new SequenceDiagram.Participant("A", "A", false));
        d.addElement(new SequenceNote(SequenceNote.NotePosition.RIGHT, java.util.List.of("A"), "A note"));
        SequenceLayoutResult layout = new SequenceDiagramLayout().layout(d, LayoutConfig.slideContentArea());
        DiagramOutput output = SequenceEmitter.emit(layout);
        boolean hasYellowNote = output.shapes().stream()
            .filter(s -> s.nodeId().startsWith("note_"))
            .anyMatch(s -> "FFFFCC".equals(s.fillColor()));
        assertTrue("Note shapes should have yellow fill", hasYellowNote);
    }

    @Test
    public void testLabelShapesForMessages() {
        DiagramOutput output = emitSimpleDiagram();
        assertTrue("Should have label shapes for messages with text",
            output.labelShapes().size() >= 2);
    }
}
