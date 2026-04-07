package com.excudo.core.mermaid.layout.sequence;

import com.excudo.core.mermaid.ast.sequence.*;
import com.excudo.core.mermaid.ast.sequence.SequenceBlock.BlockType;
import com.excudo.core.mermaid.layout.LayoutConfig;
import com.excudo.core.mermaid.layout.sequence.SequenceLayoutResult.*;

import java.util.*;

/**
 * Swim-lane layout algorithm for sequence diagrams.
 * X axis = participants (fixed columns), Y axis = time (incremental rows).
 */
public class SequenceDiagramLayout {

    // Layout constants (EMUs)
    private static final long PARTICIPANT_WIDTH = 1097280;    // 1.2"
    private static final long PARTICIPANT_HEIGHT = 457200;    // 0.5"
    private static final long PARTICIPANT_GAP = 731520;       // 0.8"
    private static final long MESSAGE_ROW_HEIGHT = 457200;    // 0.5"
    private static final long BLOCK_HEADER_HEIGHT = 274320;   // 0.3"
    private static final long ACTIVATION_BAR_WIDTH = 137160;  // 0.15"
    private static final long BLOCK_PADDING = 91440;          // 0.1"
    private static final long NOTE_WIDTH = 914400;            // 1.0"
    private static final long NOTE_HEIGHT = 365760;           // 0.4"
    private static final long SELF_MSG_OFFSET = 457200;       // 0.5"
    private static final long LIFELINE_WIDTH = 12700;         // 1pt

    public SequenceLayoutResult layout(SequenceDiagram diagram, LayoutConfig config) {
        List<SequenceDiagram.Participant> participants = diagram.participants();
        int n = participants.size();
        if (n == 0) {
            return new SequenceLayoutResult(
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), 0, 0);
        }

        // Compute participant widths and gaps, scaling if needed
        long pWidth = PARTICIPANT_WIDTH;
        long pGap = PARTICIPANT_GAP;
        long totalNeeded = (long) n * pWidth + (long) (n - 1) * pGap;
        if (totalNeeded > config.boundingWidth()) {
            double scale = (double) config.boundingWidth() / totalNeeded;
            pWidth = (long) (pWidth * scale);
            pGap = (long) (pGap * scale);
            totalNeeded = (long) n * pWidth + (long) (n - 1) * pGap;
        }

        // Center participants horizontally
        long startX = config.boundingX() + (config.boundingWidth() - totalNeeded) / 2;

        // Map participant id -> center X
        Map<String, Long> centerX = new LinkedHashMap<>();
        Map<String, Integer> participantIndex = new HashMap<>();
        List<PositionedParticipant> posParticipants = new ArrayList<>();
        long topY = config.boundingY();

        for (int i = 0; i < n; i++) {
            SequenceDiagram.Participant p = participants.get(i);
            long px = startX + i * (pWidth + pGap);
            long cx = px + pWidth / 2;
            centerX.put(p.id(), cx);
            participantIndex.put(p.id(), i);
            posParticipants.add(new PositionedParticipant(
                p.id(), p.label(), p.isActor(), px, topY, pWidth, PARTICIPANT_HEIGHT));
        }

        // Walk elements and layout
        long currentY = topY + PARTICIPANT_HEIGHT;
        List<PositionedMessage> posMessages = new ArrayList<>();
        List<PositionedBlock> posBlocks = new ArrayList<>();
        List<PositionedNote> posNotes = new ArrayList<>();
        List<PositionedActivation> posActivations = new ArrayList<>();

        // Activation tracking: participant -> stack of activation start Y
        Map<String, Deque<Long>> activationStacks = new HashMap<>();

        // Block stack for nested blocks
        Deque<BlockState> blockStack = new ArrayDeque<>();

        currentY = layoutElements(diagram.elements(), currentY, centerX, participantIndex,
            posMessages, posBlocks, posNotes, posActivations, activationStacks, blockStack, config);

        // Close any remaining activations
        for (Map.Entry<String, Deque<Long>> entry : activationStacks.entrySet()) {
            String pid = entry.getKey();
            long cx = centerX.get(pid);
            while (!entry.getValue().isEmpty()) {
                long activY = entry.getValue().pop();
                posActivations.add(new PositionedActivation(pid,
                    cx - ACTIVATION_BAR_WIDTH / 2, activY, currentY));
            }
        }

        // Lifelines: from bottom of participant box to bottom of diagram
        List<PositionedLifeline> posLifelines = new ArrayList<>();
        long lifelineBottom = currentY + MESSAGE_ROW_HEIGHT / 2;
        for (SequenceDiagram.Participant p : participants) {
            long cx = centerX.get(p.id());
            posLifelines.add(new PositionedLifeline(p.id(), cx, topY + PARTICIPANT_HEIGHT, lifelineBottom));
        }

        long totalWidth = totalNeeded;
        long totalHeight = lifelineBottom - topY;

        return new SequenceLayoutResult(posParticipants, posLifelines, posMessages,
            posBlocks, posNotes, posActivations, totalWidth, totalHeight);
    }

    private long layoutElements(List<SequenceElement> elements, long currentY,
                                 Map<String, Long> centerX, Map<String, Integer> participantIndex,
                                 List<PositionedMessage> posMessages,
                                 List<PositionedBlock> posBlocks,
                                 List<PositionedNote> posNotes,
                                 List<PositionedActivation> posActivations,
                                 Map<String, Deque<Long>> activationStacks,
                                 Deque<BlockState> blockStack,
                                 LayoutConfig config) {

        for (SequenceElement elem : elements) {
            if (elem instanceof SequenceMessage msg) {
                currentY += MESSAGE_ROW_HEIGHT;
                long fromX = centerX.getOrDefault(msg.fromId(), 0L);
                long toX = centerX.getOrDefault(msg.toId(), 0L);
                boolean isSelf = msg.fromId().equals(msg.toId());
                if (isSelf) {
                    toX = fromX + SELF_MSG_OFFSET;
                }
                posMessages.add(new PositionedMessage(
                    msg.fromId(), msg.toId(), msg.label(), msg.arrowType(), isSelf,
                    fromX, toX, currentY));

                // Handle +/- activation suffixes
                if (msg.activateTarget()) {
                    activationStacks.computeIfAbsent(msg.toId(), k -> new ArrayDeque<>()).push(currentY);
                }
                if (msg.deactivateSource()) {
                    Deque<Long> stack = activationStacks.get(msg.fromId());
                    if (stack != null && !stack.isEmpty()) {
                        long activY = stack.pop();
                        long cx = centerX.get(msg.fromId());
                        posActivations.add(new PositionedActivation(msg.fromId(),
                            cx - ACTIVATION_BAR_WIDTH / 2, activY, currentY));
                    }
                }

            } else if (elem instanceof SequenceNote note) {
                currentY += NOTE_HEIGHT;
                long noteX;
                if (note.position() == SequenceNote.NotePosition.OVER) {
                    if (note.participantIds().size() >= 2) {
                        long leftX = centerX.getOrDefault(note.participantIds().get(0), 0L);
                        long rightX = centerX.getOrDefault(
                            note.participantIds().get(note.participantIds().size() - 1), 0L);
                        noteX = (leftX + rightX) / 2 - NOTE_WIDTH / 2;
                    } else {
                        noteX = centerX.getOrDefault(note.participantIds().get(0), 0L) - NOTE_WIDTH / 2;
                    }
                } else if (note.position() == SequenceNote.NotePosition.LEFT) {
                    noteX = centerX.getOrDefault(note.participantIds().get(0), 0L) - NOTE_WIDTH - BLOCK_PADDING;
                } else {
                    noteX = centerX.getOrDefault(note.participantIds().get(0), 0L) + BLOCK_PADDING;
                }
                posNotes.add(new PositionedNote(note.text(), noteX, currentY - NOTE_HEIGHT, NOTE_WIDTH, NOTE_HEIGHT));

            } else if (elem instanceof ActivationChange act) {
                if (act.activate()) {
                    activationStacks.computeIfAbsent(act.participantId(), k -> new ArrayDeque<>()).push(currentY);
                } else {
                    Deque<Long> stack = activationStacks.get(act.participantId());
                    if (stack != null && !stack.isEmpty()) {
                        long activY = stack.pop();
                        long cx = centerX.get(act.participantId());
                        posActivations.add(new PositionedActivation(act.participantId(),
                            cx - ACTIVATION_BAR_WIDTH / 2, activY, currentY));
                    }
                }

            } else if (elem instanceof SequenceBlock block) {
                currentY += BLOCK_HEADER_HEIGHT;
                long blockStartY = currentY - BLOCK_HEADER_HEIGHT;

                // Track involved participants for width calculation
                Set<Integer> involvedIndices = new HashSet<>();
                collectParticipantIndices(block, participantIndex, involvedIndices);

                // Layout block children
                currentY = layoutElements(block.elements(), currentY, centerX, participantIndex,
                    posMessages, posBlocks, posNotes, posActivations, activationStacks, blockStack, config);

                // Layout alternates with divider tracking
                List<Long> dividerYs = new ArrayList<>();
                for (SequenceBlock alt : block.alternates()) {
                    dividerYs.add(currentY);
                    currentY += BLOCK_HEADER_HEIGHT;
                    currentY = layoutElements(alt.elements(), currentY, centerX, participantIndex,
                        posMessages, posBlocks, posNotes, posActivations, activationStacks, blockStack, config);
                }

                // Compute block rectangle from involved participants
                long blockX, blockWidth;
                if (!involvedIndices.isEmpty()) {
                    int minIdx = Collections.min(involvedIndices);
                    int maxIdx = Collections.max(involvedIndices);
                    List<String> pIds = new ArrayList<>(centerX.keySet());
                    long leftCx = centerX.get(pIds.get(minIdx));
                    long rightCx = centerX.get(pIds.get(maxIdx));
                    blockX = leftCx - PARTICIPANT_WIDTH / 2 - BLOCK_PADDING;
                    blockWidth = (rightCx + PARTICIPANT_WIDTH / 2 + BLOCK_PADDING) - blockX;
                } else {
                    blockX = config.boundingX() + BLOCK_PADDING;
                    blockWidth = config.boundingWidth() - 2 * BLOCK_PADDING;
                }

                posBlocks.add(new PositionedBlock(block.type(), block.label(),
                    blockX, blockStartY, blockWidth, currentY - blockStartY, dividerYs));
            }
        }

        return currentY;
    }

    private void collectParticipantIndices(SequenceBlock block,
                                            Map<String, Integer> participantIndex,
                                            Set<Integer> indices) {
        for (SequenceElement elem : block.elements()) {
            if (elem instanceof SequenceMessage msg) {
                Integer fi = participantIndex.get(msg.fromId());
                Integer ti = participantIndex.get(msg.toId());
                if (fi != null) indices.add(fi);
                if (ti != null) indices.add(ti);
            } else if (elem instanceof SequenceBlock nested) {
                collectParticipantIndices(nested, participantIndex, indices);
            }
        }
        for (SequenceBlock alt : block.alternates()) {
            collectParticipantIndices(alt, participantIndex, indices);
        }
    }

    private static class BlockState {
        final BlockType type;
        final String label;
        final long startY;
        BlockState(BlockType type, String label, long startY) {
            this.type = type;
            this.label = label;
            this.startY = startY;
        }
    }
}
