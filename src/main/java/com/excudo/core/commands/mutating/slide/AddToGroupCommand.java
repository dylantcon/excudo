package com.excudo.core.commands.mutating.slide;

import com.excudo.core.commands.meta.UndoCommand;
import com.excudo.core.commands.Command;
import com.excudo.core.commands.CommandClassRegistry;
import com.excudo.core.commands.CommandContext;
import com.excudo.core.commands.CommandExecutionException;

import com.excudo.core.orchestration.PPTXOrchestrator;
import com.excudo.core.parsing.CommandParameters;
import com.excudo.core.parsing.CommandSchema;
import com.excudo.core.parsing.Parameter;
import com.excudo.core.results.ExecutionResult;

/**
 * Move an existing top-level shape into an existing group, preserving
 * its visual slide-space position via the group's inverse coordinate
 * transform. Structural DOM move plus coordinate rewrite.
 *
 * <p>v1 limitations: rotation on the group isn't factored in; nested
 * groups aren't supported. Throws cleanly rather than producing a
 * visually-wrong transform when either is detected upstream.
 *
 * <p>Self-registers via {@link CommandClassRegistry}: the canonical name
 * {@code add-to-group} derives from the class name.
 */
public class AddToGroupCommand implements Command {

    static final Parameter<Integer> SLIDE = Parameter.ofInt("slide")
        .slideNumber().description("Slide number").llmName("slideNumber").required().build();
    static final Parameter<Integer> GROUP = Parameter.ofInt("group")
        .spid().description("SPID of the existing group to move into").llmName("groupSpid").required().build();
    static final Parameter<Integer> CHILD = Parameter.ofInt("child")
        .spid().description("SPID of the top-level shape to move into the group")
        .llmName("childSpid").required().build();

    public static final CommandSchema SCHEMA = CommandSchema.builder()
        .description("Move a top-level shape into an existing group, preserving its visual position")
        .llmEnabled(true)
        .llmDescription("Move a top-level shape into an existing group. The shape's "
            + "visual slide-space position is preserved via the group's inverse coordinate "
            + "transform. v1 does not support rotated groups or nested groups.")
        .parameter(SLIDE)
        .parameter(GROUP)
        .parameter(CHILD)
        .example("add-to-group 1 --group 5 --child 7")
        .build();

    public static final String NAME = CommandClassRegistry.nameOf(AddToGroupCommand.class);

    public static Command fromParameters(CommandParameters p, CommandContext ctx) {
        return new AddToGroupCommand(p.get(SLIDE), p.get(GROUP), p.get(CHILD), ctx.orchestrator());
    }

    private final int slideNumber;
    private final int groupSpid;
    private final int childSpid;
    private final PPTXOrchestrator orchestrator;
    private boolean executed = false;

    public AddToGroupCommand(int slideNumber, int groupSpid, int childSpid,
                             PPTXOrchestrator orchestrator) {
        if (orchestrator == null) {
            throw new IllegalArgumentException("PPTXOrchestrator cannot be null");
        }
        if (slideNumber <= 0 || groupSpid <= 0 || childSpid <= 0) {
            throw new IllegalArgumentException("slide / group / child must be positive");
        }
        if (groupSpid == childSpid) {
            throw new IllegalArgumentException("group and child SPIDs must differ");
        }
        this.slideNumber = slideNumber;
        this.groupSpid = groupSpid;
        this.childSpid = childSpid;
        this.orchestrator = orchestrator;
    }

    @Override
    public void execute() {
        if (executed) {
            throw new CommandExecutionException(getDescription(), "execute",
                "Command has already been executed");
        }
        ExecutionResult<Void> r = orchestrator.addToGroup(slideNumber, groupSpid, childSpid);
        if (!r.isSuccess()) {
            throw new CommandExecutionException(getDescription(), "execute", r.getMessage());
        }
        executed = true;
    }

    @Override
    public void undo() {
        if (!executed) {
            throw new CommandExecutionException(getDescription(), UndoCommand.NAME,
                "Command has not been executed");
        }
        ExecutionResult<Void> r = orchestrator.detachFromGroup(slideNumber, childSpid);
        if (!r.isSuccess()) {
            throw new CommandExecutionException(getDescription(), UndoCommand.NAME, r.getMessage());
        }
        executed = false;
    }

    @Override public boolean canUndo() { return executed; }
    @Override public boolean isExecuted() { return executed; }

    @Override
    public String getDescription() {
        return "AddToGroup(slide=" + slideNumber + ", group=" + groupSpid + ", child=" + childSpid + ")";
    }
}
