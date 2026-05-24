package com.excudo.core.commands.meta;

import com.excudo.core.commands.meta.UndoCommand;
import com.excudo.core.commands.Command;
import com.excudo.core.commands.CommandClassRegistry;
import com.excudo.core.commands.CommandContext;
import com.excudo.core.commands.CommandExecutionException;
import com.excudo.core.commands.LLMContext;
import com.excudo.core.commands.LLMHandler;
import com.excudo.core.parsing.CommandParameters;
import com.excudo.core.parsing.CommandSchema;
import com.excudo.core.parsing.Parameter;

/**
 * Command for LLM configuration operations (show, set, clear, provider).
 * Delegates to LLMHandler.handleConfigAction() for the actual config logic.
 *
 * <p>Self-registers via {@link CommandClassRegistry}: canonical name
 * {@code llm-config} derives from the class. Replaces the legacy
 * {@code llm config <action>} subcommand routing -- now a top-level command.
 */
public class LLMConfigCommand implements Command {

    static final Parameter<String> ACTION = Parameter.ofString("action")
        .description("Config action (show, set, clear, provider, ...)")
        .required(false).variableLength(true).build();

    public static final CommandSchema SCHEMA = CommandSchema.builder()
        .description("Configure the LLM provider/credentials")
        .parameter(ACTION)
        .example("llm-config show")
        .example("llm-config set provider openai")
        .build();

    public static final String NAME = CommandClassRegistry.nameOf(LLMConfigCommand.class);

    public static Command fromParameters(CommandParameters p, CommandContext ctx) {
        LLMContext llmCtx = ctx.requireLlmContext();
        LLMHandler handler = llmCtx.getLLMHandler();
        if (handler == null) {
            throw new IllegalStateException("LLM handler not available");
        }
        return new LLMConfigCommand(handler, p.opt(ACTION).orElse(""));
    }

    private final LLMHandler llmHandler;
    private final String configAction;
    private boolean executed = false;

    public LLMConfigCommand(LLMHandler llmHandler, String configAction) {
        this.llmHandler = llmHandler;
        this.configAction = configAction != null ? configAction : "";
    }

    @Override
    public void execute() {
        llmHandler.handleConfigAction(configAction);
        executed = true;
    }

    @Override
    public void undo() {
        throw new CommandExecutionException(getDescription(), UndoCommand.NAME, "Config commands cannot be undone");
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
        return "LLM config" + (configAction.isEmpty() ? "" : " " + configAction);
    }
}
