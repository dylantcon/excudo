package com.excudo.core.commands.meta;

import com.excudo.core.commands.meta.UndoCommand;
import com.excudo.core.commands.CommandInvoker;
import com.excudo.core.commands.Command;
import com.excudo.core.commands.CommandClassRegistry;
import com.excudo.core.commands.CommandContext;
import com.excudo.core.commands.CommandExecutionException;
import com.excudo.core.commands.CompositeCommand;
import com.excudo.core.commands.LLMContext;
import com.excudo.core.commands.LLMHandler;

import com.excudo.core.orchestration.PPTXOrchestrator;
import com.excudo.core.orchestration.BatchExecutionResult;
import com.excudo.core.parsing.CommandParameters;
import com.excudo.core.parsing.CommandSchema;
import com.excudo.core.parsing.Parameter;
import com.excudo.core.utils.Logger;
import com.excudo.core.utils.ComponentLogger;

/**
 * GoF Command for handling LLM (AI-powered) operations.
 *
 * This is a "command creation command" that uses AI to generate and execute
 * multiple presentation operations as a single atomic unit. The LLM analyzes
 * the current presentation state and creates appropriate commands via the
 * existing CompositeCommand infrastructure.
 *
 * Key features:
 * - Delegates to LLMHandler for API interaction
 * - Uses existing LLMIntegrationService for command creation
 * - Leverages CompositeCommand for atomic undo/redo behavior
 * - Provides agentic productivity features through natural language
 *
 * <p>Self-registers via {@link CommandClassRegistry}: canonical name
 * {@code llm} derives from the class. {@code fromParameters} pulls the
 * orchestrator and {@link CommandInvoker} from the {@link LLMContext}
 * exposed by the REPL adapter.
 */
public class LLMCommand implements Command {

    private static final ComponentLogger logger = Logger.llm();

    static final Parameter<String> SUBCOMMAND = Parameter.ofString("subcommand")
        .description("LLM subcommand (edit, analyze, suggest, help)").required().build();
    static final Parameter<String> REQUEST = Parameter.ofString("request")
        .description("Natural language request for the LLM")
        .required(false).variableLength(true).build();

    public static final CommandSchema SCHEMA = CommandSchema.builder()
        .description("LLM-powered presentation editing")
        .parameter(SUBCOMMAND)
        .parameter(REQUEST)
        .example("llm help")
        .example("llm edit add a blue rectangle to slide 1")
        .example("llm analyze")
        .build();

    public static final String NAME = CommandClassRegistry.nameOf(LLMCommand.class);

    public static Command fromParameters(CommandParameters p, CommandContext ctx) {
        LLMContext llmCtx = ctx.requireLlmContext();
        LLMHandler handler = llmCtx.getLLMHandler();
        if (handler == null) {
            throw new IllegalStateException("LLM handler not available");
        }
        return new LLMCommand(handler, llmCtx.getCurrentOrchestrator(),
            llmCtx.getCurrentCommandInvoker(),
            p.get(SUBCOMMAND).trim().toLowerCase(),
            p.opt(REQUEST).orElse("").trim());
    }

    private final LLMHandler llmHandler;
    private final PPTXOrchestrator orchestrator;
    private final CommandInvoker commandInvoker;
    private final String subcommand;
    private final String request;
    private boolean executed = false;
    
    /**
     * Create an LLMCommand.
     * 
     * @param llmHandler the LLM console handler for processing requests
     * @param orchestrator the PPTX orchestrator for presentation operations
     * @param commandInvoker the command invoker for executing generated commands
     * @param subcommand the LLM subcommand (edit, help, analyze, etc.)
     * @param request the natural language request (for edit commands)
     */
    public LLMCommand(LLMHandler llmHandler, PPTXOrchestrator orchestrator,
                     CommandInvoker commandInvoker, String subcommand, String request) {
        if (llmHandler == null) {
            throw new IllegalArgumentException("LLMHandler cannot be null");
        }
        if (orchestrator == null) {
            throw new IllegalArgumentException("PPTXOrchestrator cannot be null");
        }
        if (commandInvoker == null) {
            throw new IllegalArgumentException("CommandInvoker cannot be null");
        }
        if (subcommand == null || subcommand.trim().isEmpty()) {
            throw new IllegalArgumentException("Subcommand cannot be null or empty");
        }
        
        this.llmHandler = llmHandler;
        this.orchestrator = orchestrator;
        this.commandInvoker = commandInvoker;
        this.subcommand = subcommand.trim().toLowerCase();
        this.request = request != null ? request.trim() : "";
    }
    
    @Override
    public void execute() {
        if (executed) {
            throw new CommandExecutionException(getDescription(), "execute", "Command has already been executed");
        }
        
        try {
            switch (subcommand) {
                case "edit":
                    executeEdit();
                    break;
                case "help":
                    executeHelp();
                    break;
                case "analyze":
                    executeAnalyze();
                    break;
                case "suggest":
                    executeSuggest();
                    break;
                default:
                    throw new IllegalArgumentException("Unknown LLM subcommand: " + subcommand);
            }
            
            executed = true;
            
        } catch (CommandExecutionException e) {
            throw e; // Don't rewrap
        } catch (Exception e) {
            throw new CommandExecutionException(getDescription(), "execute",
                e.getMessage(), e);
        }
    }
    
    /**
     * Execute an LLM edit request using the agentic pipeline.
     */
    private void executeEdit() throws Exception {
        if (request.isEmpty()) {
            throw new IllegalArgumentException("Edit requests require a description");
        }

        System.out.println(com.excudo.console.utils.ConsoleColors.dim("Thinking..."));

        // Local models use single-shot path (propose -> execute as CompositeCommand)
        // Cloud models use agentic multi-turn tool-use path
        if (llmHandler.isAgenticAvailable() && !llmHandler.isLocalModel()) {
            System.out.println("Using agentic mode (selective context retrieval)...");
            String summary = llmHandler.processEditRequestAgentic(request, commandInvoker);
            System.out.println();
            System.out.println(summary);
            return;
        }

        // Single-shot path: generate proposal, execute as atomic CompositeCommand
        LLMHandler.LLMEditProposal proposal = llmHandler.processEditRequest(request);

        // Always show the raw model output so the user can see what the LLM returned
        System.out.println(com.excudo.console.utils.ConsoleColors.dim(
            "--- model response ---"));
        System.out.println(com.excudo.console.utils.ConsoleColors.dim(
            proposal.getJsonCommand()));
        System.out.println(com.excudo.console.utils.ConsoleColors.dim(
            "--- end response ---"));

        BatchExecutionResult result = llmHandler.executeApprovedProposal(proposal, commandInvoker);

        if (!result.isSuccess()) {
            // Surface the actual error from the results map
            String errorDetail = null;
            if (result.getResults() != null && result.getResults().containsKey("error")) {
                errorDetail = String.valueOf(result.getResults().get("error"));
            }
            if (errorDetail == null || errorDetail.isEmpty()) {
                errorDetail = String.format("%d of %d operations failed",
                    result.getFailedOperations(), result.getTotalOperations());
            }
            logger.error("{}", errorDetail);
            return; // Don't throw -- error is already displayed
        }

        System.out.println("[OK] Completed: " + result.getSuccessfulOperations()
            + "/" + result.getTotalOperations() + " operations");
    }
    
    /**
     * Execute LLM help command.
     */
    private void executeHelp() {
        // Delegate to LLMHandler's help logic
        // For now, we'll simulate it since the original uses Scanner
        System.out.println("LLM AI-Powered Editing Commands:");
        System.out.println("  llm edit <request>  - Make AI-powered changes to presentation");
        System.out.println("  llm analyze         - Analyze current presentation structure");
        System.out.println("  llm suggest         - Get improvement suggestions");
        System.out.println("  llm help           - Show this help");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  llm edit add fade animations to all titles");
        System.out.println("  llm edit create 3 slides about project timeline");
        System.out.println("  llm edit improve the visual consistency");
    }
    
    /**
     * Execute LLM analyze command.
     */
    private void executeAnalyze() throws Exception {
        // Generate and display presentation analysis
        String context = llmHandler.generateContext(orchestrator, true);
        System.out.println("=== PRESENTATION ANALYSIS ===");
        System.out.println(context);
    }
    
    /**
     * Execute LLM suggest command.
     */
    private void executeSuggest() {
        // For now, provide basic suggestions
        // In the future, this could call the API for AI-generated suggestions
        System.out.println("AI Suggestions for improving your presentation:");
        System.out.println("• Add consistent animations to slide transitions");
        System.out.println("• Ensure all slides follow the same layout pattern");
        System.out.println("• Consider adding icons to enhance visual appeal");
        System.out.println("• Use 'llm edit' with specific requests for AI-powered changes");
    }
    
    @Override
    public void undo() {
        throw new CommandExecutionException(getDescription(), UndoCommand.NAME, 
            "LLMCommand cannot be undone directly. The generated CompositeCommands handle their own undo.");
    }
    
    @Override
    public boolean canUndo() {
        return false; // The generated CompositeCommand handles undo, not this meta-command
    }
    
    @Override
    public boolean isExecuted() {
        return executed;
    }
    
    @Override
    public String getDescription() {
        return String.format("LLM %s%s", subcommand, 
            request.isEmpty() ? "" : " (" + request + ")");
    }
}