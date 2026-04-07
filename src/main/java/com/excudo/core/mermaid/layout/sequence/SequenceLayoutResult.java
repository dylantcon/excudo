package com.excudo.core.mermaid.layout.sequence;

import com.excudo.core.mermaid.ast.sequence.MessageArrowType;
import com.excudo.core.mermaid.ast.sequence.SequenceBlock.BlockType;

import java.util.List;

/**
 * Positioned output from the sequence diagram layout algorithm.
 * All coordinates are absolute EMUs.
 */
public record SequenceLayoutResult(
    List<PositionedParticipant> participants,
    List<PositionedLifeline> lifelines,
    List<PositionedMessage> messages,
    List<PositionedBlock> blocks,
    List<PositionedNote> notes,
    List<PositionedActivation> activations,
    long totalWidth,
    long totalHeight
) {
    public record PositionedParticipant(String id, String label, boolean isActor,
                                        long x, long y, long width, long height) {}

    public record PositionedLifeline(String participantId,
                                      long x, long topY, long bottomY) {}

    public record PositionedMessage(String fromId, String toId, String label,
                                     MessageArrowType arrowType, boolean isSelf,
                                     long fromX, long toX, long y) {}

    public record PositionedBlock(BlockType type, String label,
                                   long x, long y, long width, long height,
                                   List<Long> dividerYs) {}

    public record PositionedNote(String text, long x, long y, long width, long height) {}

    public record PositionedActivation(String participantId,
                                        long x, long topY, long bottomY) {}
}
