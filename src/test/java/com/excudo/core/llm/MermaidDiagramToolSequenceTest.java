package com.excudo.core.llm;

import com.excudo.core.mermaid.ast.sequence.SequenceDiagram;
import com.excudo.core.mermaid.emitter.DiagramOutput;
import com.excudo.core.mermaid.emitter.SequenceEmitter;
import com.excudo.core.mermaid.layout.LayoutConfig;
import com.excudo.core.mermaid.layout.sequence.SequenceDiagramLayout;
import com.excudo.core.mermaid.layout.sequence.SequenceLayoutResult;
import com.excudo.core.mermaid.parser.DiagramDetector;
import com.excudo.core.mermaid.parser.DiagramType;
import com.excudo.core.mermaid.parser.sequence.SequenceParser;
import org.junit.Test;
import static org.junit.Assert.*;

public class MermaidDiagramToolSequenceTest {

    @Test
    public void testFullPipelineProducesShapesAndConnectors() {
        String mermaid = "sequenceDiagram\n"
            + "    participant A as Alice\n"
            + "    participant B as Bob\n"
            + "    A->>B: Hello Bob\n"
            + "    B-->>A: Hi Alice\n"
            + "    Note over A,B: Friends\n";

        SequenceDiagram seq = SequenceParser.parse(mermaid);
        SequenceLayoutResult layout = new SequenceDiagramLayout().layout(seq, LayoutConfig.slideContentArea());
        DiagramOutput output = SequenceEmitter.emit(layout);

        assertTrue("Should have shapes (participants, lifelines, notes)",
            output.shapes().size() >= 5);
        assertEquals("Should have 2 connectors for 2 messages",
            2, output.connectors().size());
    }

    @Test
    public void testSequenceDiagramDetectedCorrectly() {
        assertEquals(DiagramType.SEQUENCE,
            DiagramDetector.detect("sequenceDiagram\n    A->>B: Hello"));
        assertEquals(DiagramType.FLOWCHART,
            DiagramDetector.detect("graph TD\n    A-->B"));
    }

    @Test
    public void testErrorOnInvalidSequenceSyntax() {
        try {
            SequenceParser.parse("sequenceDiagram\n    >>> invalid <<<");
            fail("Should have thrown an exception");
        } catch (SequenceParser.ParseException | com.excudo.core.mermaid.parser.Lexer.LexerException e) {
            // Either exception type is acceptable -- lexer rejects unknown chars,
            // parser rejects structurally invalid input
        }
    }
}
