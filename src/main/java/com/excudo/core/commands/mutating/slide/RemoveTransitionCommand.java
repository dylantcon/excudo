package com.excudo.core.commands.mutating.slide;

import com.excudo.core.commands.Command;
import com.excudo.core.commands.CommandClassRegistry;
import com.excudo.core.commands.CommandContext;

import com.excudo.core.model.TransitionType;
import com.excudo.core.orchestration.PPTXOrchestrator;
import com.excudo.core.parsing.CommandParameters;
import com.excudo.core.parsing.CommandSchema;
import com.excudo.core.parsing.Parameter;

/**
 * Remove a slide transition by writing an explicit empty {@code <p:transition/>}
 * override -- the same legacy REPL semantic as the old {@code remove-transition}
 * dispatch, which routed through {@link SetTransitionCommand} with
 * {@link TransitionType#NONE}. This class is a thin composition wrapper that
 * exists to own its own derived command name ({@code remove-transition}) and
 * SCHEMA; the actual mutate/undo logic lives in the wrapped
 * {@link SetTransitionCommand}.
 *
 * <p><b>Not to be confused with</b> {@link ClearTransitionCommand}, which is a
 * distinct operation: it removes the {@code <p:transition>} element entirely so
 * layout/master inheritance can resume. {@code remove-transition} writes an
 * explicit empty override that overrides inheritance with "no transition."
 *
 * <p>Self-registers via {@link com.excudo.core.commands.CommandClassRegistry}:
 * the canonical name {@code remove-transition} derives from the class name.
 */
public class RemoveTransitionCommand implements Command {

    static final Parameter<Integer> SLIDE = Parameter.ofInt("slide")
        .slideNumber().description("Slide number").required().build();

    public static final CommandSchema SCHEMA = CommandSchema.builder()
        .description("Remove transition from a slide")
        .parameter(SLIDE)
        .example("remove-transition 3")
        .build();

    public static final String NAME = CommandClassRegistry.nameOf(RemoveTransitionCommand.class);

    public static Command fromParameters(CommandParameters p, CommandContext ctx) {
        return new RemoveTransitionCommand(p.get(SLIDE), ctx.orchestrator());
    }

    private final int slideNumber;
    private final SetTransitionCommand delegate;

    public RemoveTransitionCommand(int slideNumber, PPTXOrchestrator orchestrator) {
        this.slideNumber = slideNumber;
        this.delegate = new SetTransitionCommand(slideNumber, TransitionType.NONE,
            null, null, orchestrator);
    }

    @Override public void execute() { delegate.execute(); }
    @Override public void undo() { delegate.undo(); }
    @Override public boolean canUndo() { return delegate.canUndo(); }
    @Override public boolean isExecuted() { return delegate.isExecuted(); }

    @Override
    public String getDescription() {
        return "Remove transition from slide " + slideNumber;
    }
}
