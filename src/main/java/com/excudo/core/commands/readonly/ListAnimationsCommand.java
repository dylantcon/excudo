package com.excudo.core.commands.readonly;

import com.excudo.core.commands.meta.UndoCommand;
import com.excudo.core.commands.Command;
import com.excudo.core.parsing.Parameter;
import com.excudo.core.parsing.CommandSchema;
import com.excudo.core.parsing.CommandParameters;
import com.excudo.core.commands.CommandContext;
import com.excudo.core.commands.CommandClassRegistry;
import com.excudo.core.commands.CommandDisplay;
import com.excudo.core.commands.CommandExecutionException;

import com.excudo.core.orchestration.PPTXOrchestrator;
import com.excudo.core.orchestration.SlideMetadata;
import com.excudo.core.model.AnimationBinding;
import com.excudo.core.model.ShapeRegistry;
import com.excudo.core.model.SlideShape;
import com.excudo.core.inspection.SlideInspector;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * GoF Command for listing animations on a specific slide.
 *
 * Displays a structured Animation Pane view matching PowerPoint's native
 * animation panel: click groups, sequential numbering, shape name resolution,
 * trigger labels, and effect properties.
 *
 * This is a read-only query command. Does not support undo since it
 * performs no mutations.
 */
public class ListAnimationsCommand implements Command {

    static final Parameter<Integer> SLIDE = Parameter.ofInt("slide")
        .slideNumber().description("Slide number").required().build();

    public static final CommandSchema SCHEMA = CommandSchema.builder()
        .description("List animations on a slide")
        .parameter(SLIDE)
        .example("list-animations 1")
        .build();

    public static final String NAME = CommandClassRegistry.nameOf(ListAnimationsCommand.class);

    public static Command fromParameters(CommandParameters p, CommandContext ctx) {
        return new ListAnimationsCommand(ctx.orchestrator(), ctx.requireDisplay(), p.get(SLIDE));
    }


    private final PPTXOrchestrator orchestrator;
    private final CommandDisplay display;
    private final int slideNumber;
    private boolean executed = false;


    public ListAnimationsCommand(PPTXOrchestrator orchestrator, CommandDisplay display, int slideNumber) {
        if (orchestrator == null) {
            throw new IllegalArgumentException("PPTXOrchestrator cannot be null");
        }
        if (display == null) {
            throw new IllegalArgumentException("CommandDisplay cannot be null");
        }
        if (slideNumber < 1) {
            throw new IllegalArgumentException("Slide number must be positive");
        }
        this.orchestrator = orchestrator;
        this.display = display;
        this.slideNumber = slideNumber;
    }

    @Override
    public void execute() {
        if (executed) {
            throw new CommandExecutionException(getDescription(), "execute", "Command has already been executed");
        }

        try {
            Optional<com.excudo.core.orchestration.OrchestrationContext> context = orchestrator.getContext();
            if (context.isEmpty()) {
                display.displayError("No presentation loaded. Use 'load <filename>' to open a presentation.");
                executed = true;
                return;
            }

            int slideCount = orchestrator.getPresentationMetadata().getSlideCount();
            if (slideNumber > slideCount) {
                display.displayError(String.format("Slide %d does not exist. Presentation has %d slides.",
                    slideNumber, slideCount));
                executed = true;
                return;
            }

            Optional<SlideMetadata> slideMetadata = orchestrator.getSlideMetadata(slideNumber);
            if (slideMetadata.isEmpty()) {
                display.displayError(String.format("Unable to read metadata for slide %d", slideNumber));
                executed = true;
                return;
            }

            SlideMetadata slide = slideMetadata.get();

            display.displayMessage(String.format("Animation Pane - Slide %d:", slideNumber));

            if (slide.getAnimationCount() == 0) {
                display.displayMessage("  No animations found on this slide.");
                executed = true;
                return;
            }

            try {
                SlideInspector.SlideInspectionResult result = SlideInspector.parseSlideData(
                    orchestrator, slideNumber);

                if (!result.isSuccess()) {
                    display.displayError(String.format("  %d animations detected but unable to parse details.", slide.getAnimationCount()));
                    display.displayError("  Error: " + result.getErrorMessage());
                    executed = true;
                    return;
                }

                List<AnimationBinding> animations = result.getSlideData().getAnimationBindings();
                ShapeRegistry shapeRegistry = result.getSlideData().getShapeRegistry();

                if (animations.isEmpty()) {
                    display.displayError(String.format("  %d animations detected but unable to parse details.", slide.getAnimationCount()));
                    executed = true;
                    return;
                }

                int sequenceNumber = 0;
                int currentClick = Integer.MIN_VALUE;
                int clickGroupCount = 0;
                Set<Integer> targetedSpids = new HashSet<>();

                for (AnimationBinding animation : animations) {
                    int clickTrigger = animation.getClickTrigger();
                    targetedSpids.add(animation.getTargetSpid());

                    if (clickTrigger != currentClick) {
                        currentClick = clickTrigger;
                        display.displayMessage("");
                        if (clickTrigger == 0) {
                            display.displayMessage("  With Slide (auto):");
                        } else if (clickTrigger == -1) {
                            display.displayMessage("  After Previous:");
                        } else {
                            display.displayMessage(String.format("  Click %d:", clickTrigger));
                            clickGroupCount++;
                        }
                    }

                    sequenceNumber++;
                    String line = formatAnimationLine(sequenceNumber, animation, shapeRegistry);
                    display.displayMessage(line);
                }

                display.displayMessage("");
                display.displayMessage(String.format("  Summary: %d animations | %d click groups | %d shapes targeted",
                    animations.size(), clickGroupCount, targetedSpids.size()));

            } catch (Exception e) {
                display.displayError(String.format("  %d animations detected but unable to parse details.", slide.getAnimationCount()));
                display.displayError("  Error: " + e.getMessage());
            }

            executed = true;

        } catch (Exception e) {
            throw new CommandExecutionException(getDescription(), "execute",
                "Failed to list animations for slide " + slideNumber + ": " + e.getMessage(), e);
        }
    }

    private String formatAnimationLine(int sequenceNumber, AnimationBinding anim, ShapeRegistry registry) {
        StringBuilder line = new StringBuilder();

        // "    1. [in]  fade                 "Title 1" (SPID 2)       500ms  id:15"
        line.append(String.format("    %d. ", sequenceNumber));
        line.append(formatCategory(anim));
        line.append(" ");

        String effectName = anim.getAnimationType().getUserFriendlyName();
        line.append(String.format("%-20s ", effectName));

        String shapeLabel = resolveShapeName(registry, anim.getTargetSpid());
        line.append(String.format("%-35s ", shapeLabel));

        line.append(formatDuration(anim));

        String trigger = formatTrigger(anim);
        if (!trigger.isEmpty()) {
            line.append("  ").append(trigger);
        }

        String extras = formatExtras(anim);
        if (!extras.isEmpty()) {
            line.append("  ").append(extras);
        }

        // Always show timing node ID so users can reference it in remove/update commands
        if (anim.getTimingNodeId() >= 0) {
            line.append("  id:").append(anim.getTimingNodeId());
        }

        return line.toString();
    }

    private String resolveShapeName(ShapeRegistry registry, int spid) {
        SlideShape shape = registry.getShape(spid);
        if (shape != null && shape.getName() != null && !shape.getName().isEmpty()) {
            return "\"" + shape.getName() + "\" (SPID " + spid + ")";
        }
        return "SPID " + spid;
    }

    private String formatCategory(AnimationBinding anim) {
        if (anim.isEntranceAnimation()) {
            return "[in]";
        } else if (anim.isExitAnimation()) {
            return "[out]";
        } else {
            return "[emphasis]";
        }
    }

    private String formatTrigger(AnimationBinding anim) {
        if (anim.isWithPrevious()) {
            return "[with-previous]";
        } else if (anim.isAfterPrevious()) {
            return "[after-previous]";
        }
        return "";
    }

    private String formatDuration(AnimationBinding anim) {
        String dur = anim.getDuration();
        try {
            int ms = Integer.parseInt(dur);
            return ms + "ms";
        } catch (NumberFormatException e) {
            return dur;
        }
    }

    private String formatExtras(AnimationBinding anim) {
        List<String> extras = new ArrayList<>();

        // Delay
        try {
            int delay = Integer.parseInt(anim.getDelay());
            if (delay > 0) {
                extras.add("delay:" + delay + "ms");
            }
        } catch (NumberFormatException e) {
            if (!"0".equals(anim.getDelay()) && !"indefinite".equals(anim.getDelay())) {
                extras.add("delay:" + anim.getDelay());
            }
        }

        // Paragraph targeting
        if (anim.isParagraphLevelAnimation()) {
            Integer start = anim.getParagraphStart();
            Integer end = anim.getParagraphEnd();
            if (start.equals(end)) {
                extras.add("para:" + start);
            } else {
                extras.add("para:" + start + "-" + end);
            }
        }

        // Repeat
        if (anim.repeats()) {
            if (anim.getRepeatCount() == -1) {
                extras.add("repeat:infinite");
            } else {
                extras.add("repeat:" + anim.getRepeatCount());
            }
        }

        // Auto-reverse
        if (anim.isAutoReverse()) {
            extras.add("auto-reverse");
        }

        // Easing
        if (anim.hasCustomEasing()) {
            extras.add("easing:" + anim.getAcceleration() + "/" + anim.getDeceleration());
        }

        // Motion path
        if (anim.hasMotionPath()) {
            extras.add("motion-path");
        }

        return String.join("  ", extras);
    }

    @Override
    public void undo() {
        throw new CommandExecutionException(getDescription(), UndoCommand.NAME,
            "ListAnimationsCommand is read-only and does not support undo");
    }

    @Override
    public boolean canUndo() {
        return false;
    }

    @Override
    public boolean isExecuted() {
        return executed;
    }

    @Override
    public String getDescription() {
        return "List animations for slide " + slideNumber;
    }
}
