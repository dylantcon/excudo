package com.excudo.core.mermaid.layout.sequence;

import com.excudo.core.mermaid.ast.sequence.*;
import com.excudo.core.mermaid.layout.LayoutConfig;
import com.excudo.core.mermaid.layout.sequence.SequenceLayoutResult.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class SequenceDiagramLayoutTest {

    private SequenceDiagram twoParticipantDiagram() {
        SequenceDiagram d = new SequenceDiagram();
        d.addParticipant(new SequenceDiagram.Participant("A", "Alice", false));
        d.addParticipant(new SequenceDiagram.Participant("B", "Bob", false));
        return d;
    }

    private LayoutConfig defaultConfig() {
        return LayoutConfig.slideContentArea();
    }

    @Test
    public void testParticipantsSpacedLeftToRight() {
        SequenceDiagram d = twoParticipantDiagram();
        SequenceLayoutResult result = new SequenceDiagramLayout().layout(d, defaultConfig());
        assertEquals(2, result.participants().size());
        assertTrue("First participant should be left of second",
            result.participants().get(0).x() < result.participants().get(1).x());
    }

    @Test
    public void testMessagesAtIncrementingY() {
        SequenceDiagram d = twoParticipantDiagram();
        d.addElement(new SequenceMessage("A", "B", "First", MessageArrowType.SOLID_ARROW, false, false));
        d.addElement(new SequenceMessage("B", "A", "Second", MessageArrowType.DASHED_ARROW, false, false));
        SequenceLayoutResult result = new SequenceDiagramLayout().layout(d, defaultConfig());
        assertEquals(2, result.messages().size());
        assertTrue("Second message should be below first",
            result.messages().get(1).y() > result.messages().get(0).y());
    }

    @Test
    public void testLifelinesExtendToDiagramBottom() {
        SequenceDiagram d = twoParticipantDiagram();
        d.addElement(new SequenceMessage("A", "B", "Hi", MessageArrowType.SOLID_ARROW, false, false));
        SequenceLayoutResult result = new SequenceDiagramLayout().layout(d, defaultConfig());
        assertEquals(2, result.lifelines().size());
        for (PositionedLifeline ll : result.lifelines()) {
            assertTrue("Lifeline should extend below last message",
                ll.bottomY() > result.messages().get(0).y());
        }
    }

    @Test
    public void testBlockSpansCorrectParticipants() {
        SequenceDiagram d = twoParticipantDiagram();
        SequenceBlock block = new SequenceBlock(SequenceBlock.BlockType.ALT, "test");
        block.addElement(new SequenceMessage("A", "B", "Msg", MessageArrowType.SOLID_ARROW, false, false));
        d.addElement(block);
        SequenceLayoutResult result = new SequenceDiagramLayout().layout(d, defaultConfig());
        assertEquals(1, result.blocks().size());
        PositionedBlock pb = result.blocks().get(0);
        assertTrue("Block width should span both participants", pb.width() > 0);
        assertTrue("Block height should be positive", pb.height() > 0);
    }

    @Test
    public void testActivationBarsPositioned() {
        SequenceDiagram d = twoParticipantDiagram();
        d.addElement(new ActivationChange("A", true));
        d.addElement(new SequenceMessage("A", "B", "Hi", MessageArrowType.SOLID_ARROW, false, false));
        d.addElement(new ActivationChange("A", false));
        SequenceLayoutResult result = new SequenceDiagramLayout().layout(d, defaultConfig());
        assertEquals(1, result.activations().size());
        PositionedActivation act = result.activations().get(0);
        assertEquals("A", act.participantId());
        assertTrue("Activation bar should have height", act.bottomY() > act.topY());
    }

    @Test
    public void testNotePositionedBesideLifeline() {
        SequenceDiagram d = twoParticipantDiagram();
        d.addElement(new SequenceNote(SequenceNote.NotePosition.RIGHT, java.util.List.of("A"), "Test note"));
        SequenceLayoutResult result = new SequenceDiagramLayout().layout(d, defaultConfig());
        assertEquals(1, result.notes().size());
        assertTrue("Note should have positive dimensions",
            result.notes().get(0).width() > 0 && result.notes().get(0).height() > 0);
    }

    @Test
    public void testAutoScalingForManyParticipants() {
        SequenceDiagram d = new SequenceDiagram();
        for (int i = 0; i < 10; i++) {
            d.addParticipant(new SequenceDiagram.Participant("P" + i, "Participant " + i, false));
        }
        LayoutConfig config = new LayoutConfig(0, 0, 7772400, 4525963);
        SequenceLayoutResult result = new SequenceDiagramLayout().layout(d, config);
        assertEquals(10, result.participants().size());
        // Last participant should fit within bounds
        PositionedParticipant last = result.participants().get(9);
        assertTrue("All participants should fit within bounding width",
            last.x() + last.width() <= config.boundingWidth() + config.boundingX());
    }

    @Test
    public void testSelfMessageOffset() {
        SequenceDiagram d = twoParticipantDiagram();
        d.addElement(new SequenceMessage("A", "A", "Self", MessageArrowType.SOLID_ARROW, false, false));
        SequenceLayoutResult result = new SequenceDiagramLayout().layout(d, defaultConfig());
        assertEquals(1, result.messages().size());
        PositionedMessage msg = result.messages().get(0);
        assertTrue("Self message should be flagged", msg.isSelf());
        assertTrue("Self message toX should be offset from fromX", msg.toX() > msg.fromX());
    }
}
