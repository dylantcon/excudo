package com.excudo.core.commands;

/**
 * Command for LLM configuration operations (show, set, clear, provider).
 * Delegates to LLMHandler.handleConfigAction() for the actual config logic.
 */
public class LLMConfigCommand implements Command {

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
        throw new CommandExecutionException(getDescription(), "undo", "Config commands cannot be undone");
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
