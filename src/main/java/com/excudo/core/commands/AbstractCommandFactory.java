package com.excudo.core.commands;

import com.excudo.core.orchestration.PPTXOrchestrator;
import com.excudo.core.parsing.ParsedCommand;

/**
 * Abstract base class for command subfactories.
 *
 * All command creation flows through a single path: ParsedCommand -> Command.
 * LLM requests are converted to ParsedCommands by LLMRequestBridge before
 * reaching the factory, eliminating the need for separate LLM dispatch.
 *
 * Key responsibilities:
 * - Define interface for command creation from ParsedCommand
 * - Store reference to orchestrator (receiver coordinator)
 */
public abstract class AbstractCommandFactory {

    protected final PPTXOrchestrator orchestrator;

    /**
     * Create an AbstractCommandFactory with the given orchestrator.
     *
     * @param orchestrator the PPTX orchestrator (receiver coordinator)
     */
    protected AbstractCommandFactory(PPTXOrchestrator orchestrator) {
        if (orchestrator == null) {
            throw new IllegalArgumentException("PPTXOrchestrator cannot be null");
        }
        this.orchestrator = orchestrator;
    }

    /**
     * Create a command from a parsed command.
     * This is the single entry point for both console and LLM command creation.
     * LLM requests are bridged to ParsedCommand by LLMRequestBridge before reaching here.
     *
     * @param parsedCommand the parsed command with validated parameters
     * @param displayAdapter the display adapter (null for LLM path)
     * @return the created command, or null if this factory doesn't handle the command
     */
    public abstract Command createFromParsedCommand(ParsedCommand parsedCommand, Object displayAdapter);

    /**
     * Check if this factory handles the given command name.
     *
     * @param commandName the command name to check
     * @return true if this factory handles the command, false otherwise
     */
    public abstract boolean handlesCommand(String commandName);

    /**
     * Get the PPTXOrchestrator for subclasses that need direct access.
     *
     * @return the orchestrator
     */
    protected PPTXOrchestrator getOrchestrator() {
        return orchestrator;
    }
}