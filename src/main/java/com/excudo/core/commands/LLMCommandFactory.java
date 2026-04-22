package com.excudo.core.commands;

import com.excudo.core.commands.meta.LLMCommand;
import com.excudo.core.commands.meta.LLMConfigCommand;

import com.excudo.core.orchestration.PPTXOrchestrator;

/**
 * Specialized factory for creating LLM (AI-powered) commands.
 * 
 * This factory handles all LLM command creation logic, following the Single Responsibility Principle
 * by separating LLM command creation from the main CommandFactory. This approach allows for easy
 * extension of LLM capabilities without bloating the main factory.
 * 
 * The factory uses the LLMContext interface to access required components, maintaining clean
 * architectural boundaries and avoiding direct dependencies on console implementations.
 */
public class LLMCommandFactory {
    
    /**
     * Create an LLM command based on the subcommand and request.
     * 
     * @param context the LLM context providing access to required components
     * @param subcommand the LLM subcommand (edit, help, analyze, suggest, script, etc.)
     * @param request the natural language request (for edit and script commands)
     * @return the appropriate LLM command
     * @throws IllegalArgumentException if the subcommand is not recognized
     * @throws IllegalStateException if required components are not available
     */
    public static Command createLLMCommand(LLMContext context, String subcommand, String request) {
        if (context == null) {
            throw new IllegalArgumentException("LLMContext cannot be null");
        }

        // Get handler first -- config only needs the handler
        LLMHandler llmHandler = context.getLLMHandler();

        // Validate handler
        if (llmHandler == null) {
            throw new IllegalStateException("LLM handler not available");
        }

        // Normalize subcommand
        String normalizedSubcommand = subcommand != null ? subcommand.trim().toLowerCase() : "help";
        String normalizedRequest = request != null ? request.trim() : "";

        // Config doesn't need orchestrator or invoker
        if ("config".equals(normalizedSubcommand)) {
            return new LLMConfigCommand(llmHandler, normalizedRequest);
        }

        // All other subcommands need orchestrator and invoker
        PPTXOrchestrator orchestrator = context.getCurrentOrchestrator();
        CommandInvoker commandInvoker = context.getCurrentCommandInvoker();

        if (orchestrator == null) {
            throw new IllegalStateException("PPTXOrchestrator not available");
        }
        if (commandInvoker == null) {
            throw new IllegalStateException("CommandInvoker not available");
        }

        // Create appropriate LLM command based on subcommand
        switch (normalizedSubcommand) {
            case "edit":
                if (normalizedRequest.isEmpty()) {
                    throw new IllegalArgumentException("LLM edit commands require a request description");
                }
                return new LLMCommand(llmHandler, orchestrator, commandInvoker, "edit", normalizedRequest);

            case "help":
                return new LLMCommand(llmHandler, orchestrator, commandInvoker, "help", "");

            case "analyze":
                return new LLMCommand(llmHandler, orchestrator, commandInvoker, "analyze", normalizedRequest);

            case "suggest":
                return new LLMCommand(llmHandler, orchestrator, commandInvoker, "suggest", normalizedRequest);

            // Future extension point for notes script generation
            case "script":
                // TODO: Implement LLMNotesScriptCommand for context-aware script generation
                throw new IllegalArgumentException("LLM script generation not yet implemented");

            // Future extension points for other LLM capabilities
            case "translate":
                throw new IllegalArgumentException("LLM translation not yet implemented");

            case "summarize":
                throw new IllegalArgumentException("LLM summarization not yet implemented");

            default:
                throw new IllegalArgumentException("Unknown LLM subcommand: " + normalizedSubcommand +
                    ". Available: edit, help, analyze, suggest, config");
        }
    }
    
    /**
     * Get help text for available LLM commands.
     * Used by the help system to provide command documentation.
     * 
     * @return formatted help text for LLM commands
     */
    public static String getLLMCommandHelp() {
        return """
            LLM AI-Powered Commands:
              llm edit <request>    - Make AI-powered changes to presentation
              llm help             - Show LLM command help
              llm analyze          - Analyze current presentation structure
              llm suggest          - Get improvement suggestions
            
            Examples:
              llm edit add fade animations to all titles
              llm edit create 3 slides about project timeline
              llm edit improve the visual consistency
              llm analyze
              llm suggest
            """;
    }
}