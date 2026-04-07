package com.excudo.core.mermaid.emitter;

import com.excudo.core.mermaid.ast.sequence.MessageArrowType;
import com.excudo.core.mermaid.layout.sequence.SequenceLayoutResult;
import com.excudo.core.mermaid.layout.sequence.SequenceLayoutResult.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Emits OOXML shape and connector specifications from a positioned sequence diagram.
 * Maps sequence elements to the same ShapeSpec/ConnectorSpec records used by flowcharts.
 */
public class SequenceEmitter {

    private SequenceEmitter() {}

    private static final long LABEL_WIDTH = 914400;    // 1"
    private static final long LABEL_HEIGHT = 274320;   // 0.3"

    public static DiagramOutput emit(SequenceLayoutResult layout) {
        List<ShapeSpec> shapes = new ArrayList<>();
        List<ConnectorSpec> connectors = new ArrayList<>();
        List<ShapeSpec> labelShapes = new ArrayList<>();

        // 1. Block backgrounds (rendered behind everything)
        int blockIdx = 0;
        for (PositionedBlock block : layout.blocks()) {
            shapes.add(new ShapeSpec(
                "block_" + blockIdx,
                "roundRect",
                block.type().name() + (block.label().isEmpty() ? "" : " [" + block.label() + "]"),
                block.x(), block.y(), block.width(), block.height(),
                null, "808080"
            ));
            // Divider lines as thin shapes
            int divIdx = 0;
            for (Long divY : block.dividerYs()) {
                shapes.add(new ShapeSpec(
                    "block_" + blockIdx + "_div_" + divIdx,
                    "rect",
                    "",
                    block.x(), divY, block.width(), 12700,
                    null, "808080"
                ));
                divIdx++;
            }
            blockIdx++;
        }

        // 2. Lifelines (dashed vertical lines as thin rects)
        for (PositionedLifeline ll : layout.lifelines()) {
            shapes.add(new ShapeSpec(
                "lifeline_" + ll.participantId(),
                "rect",
                "",
                ll.x() - 6350, ll.topY(), 12700, ll.bottomY() - ll.topY(),
                null, "808080"
            ));
        }

        // 3. Activation bars
        int actIdx = 0;
        for (PositionedActivation act : layout.activations()) {
            shapes.add(new ShapeSpec(
                "activation_" + actIdx,
                "rect",
                "",
                act.x(), act.topY(), 137160, act.bottomY() - act.topY(),
                "B3D9FF", "4472C4"
            ));
            actIdx++;
        }

        // 4. Participant boxes
        for (PositionedParticipant p : layout.participants()) {
            shapes.add(new ShapeSpec(
                "participant_" + p.id(),
                p.isActor() ? "ellipse" : "rect",
                p.label(),
                p.x(), p.y(), p.width(), p.height(),
                "4472C4", null
            ));
        }

        // 5. Notes
        int noteIdx = 0;
        for (PositionedNote note : layout.notes()) {
            shapes.add(new ShapeSpec(
                "note_" + noteIdx,
                "rect",
                note.text(),
                note.x(), note.y(), note.width(), note.height(),
                "FFFFCC", "C0C0C0"
            ));
            noteIdx++;
        }

        // 6. Messages (connectors for non-self, shapes for self-messages)
        int msgIdx = 0;
        for (PositionedMessage msg : layout.messages()) {
            if (msg.isSelf()) {
                // Self-message: offset label shape (no U-bend connector in v1)
                labelShapes.add(new ShapeSpec(
                    "msg_self_" + msgIdx,
                    "rect",
                    msg.label(),
                    msg.fromX() + 45720, msg.y() - LABEL_HEIGHT / 2,
                    LABEL_WIDTH, LABEL_HEIGHT,
                    null, null
                ));
            } else {
                // Determine connector direction and arrow style
                boolean leftToRight = msg.fromX() < msg.toX();
                int startIdx = leftToRight ? 3 : 1;  // right : left
                int endIdx = leftToRight ? 1 : 3;    // left : right

                ConnectorStyle style = mapArrowType(msg.arrowType());

                long minX = Math.min(msg.fromX(), msg.toX());
                long maxX = Math.max(msg.fromX(), msg.toX());
                long connW = Math.max(maxX - minX, 1);

                connectors.add(new ConnectorSpec(
                    "participant_" + msg.fromId(),
                    "participant_" + msg.toId(),
                    "straight",
                    style.headEnd,
                    style.tailEnd,
                    style.lineStyle,
                    null,
                    startIdx, endIdx,
                    minX, msg.y(), connW, 1,
                    msg.label()
                ));

                // Message label as a text box at midpoint
                if (msg.label() != null && !msg.label().isEmpty()) {
                    long midX = (msg.fromX() + msg.toX()) / 2 - LABEL_WIDTH / 2;
                    labelShapes.add(new ShapeSpec(
                        "msg_label_" + msgIdx,
                        "rect",
                        msg.label(),
                        midX, msg.y() - LABEL_HEIGHT,
                        LABEL_WIDTH, LABEL_HEIGHT,
                        null, null
                    ));
                }
            }
            msgIdx++;
        }

        return new DiagramOutput(shapes, connectors, labelShapes);
    }

    private static ConnectorStyle mapArrowType(MessageArrowType type) {
        return switch (type) {
            case SOLID_ARROW -> new ConnectorStyle("none", "triangle", "solid");
            case DASHED_ARROW -> new ConnectorStyle("none", "triangle", "dash");
            case SOLID_OPEN -> new ConnectorStyle("none", "none", "solid");
            case DASHED_OPEN -> new ConnectorStyle("none", "none", "dash");
            case CROSS -> new ConnectorStyle("none", "diamond", "solid");
            case ASYNC -> new ConnectorStyle("none", "oval", "solid");
        };
    }

    private record ConnectorStyle(String headEnd, String tailEnd, String lineStyle) {}
}
