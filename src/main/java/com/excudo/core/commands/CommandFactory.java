package com.excudo.core.commands;

import com.excudo.core.orchestration.PPTXOrchestrator;
import com.excudo.core.parsing.CommandParameters;
import com.excudo.core.llm.LLMRequestBridge;
import com.excudo.xml.writers.SlideCreator;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Thin command-name → Command dispatcher, post-sweep.
 *
 * <p>Every command in the system is now class-registered (each
 * {@code XCommand} declares its own {@code SCHEMA} + {@code fromParameters}
 * and registers in {@link CommandClassRegistry}). This factory's only job is
 * to bind ambient state (orchestrator, group-id manager, display adapter)
 * into a {@link CommandContext} and hand the parameters off to the class
 * registry. The legacy sub-factory cascade (UtilityCommandFactory,
 * SystemCommandFactory, PresentationCommandFactory, SlideCommandFactory) is
 * gone; ShapeCommandFactory and UtilityCommandFactory survive only as
 * static-helper holders ({@code parseShapeStyle}, render-function registry).
 *
 * <p>Three things still live here:
 * <ul>
 *   <li>{@link #sessionGroupIdManager} -- the session-scoped animation
 *       group-id allocator that AddAnimationCommand pulls from
 *       {@link CommandContext#groupIdManager()}.</li>
 *   <li>{@link #createComposite} -- wraps a list of commands as a single
 *       undo unit; used by the LLM batch path and test harnesses.</li>
 *   <li>{@link #createFromLLMRequest} -- bridges an LLM {@code LLMRequest}
 *       (multi-action payload) through {@link LLMRequestBridge} to a list
 *       of constructed commands.</li>
 * </ul>
 */
public class CommandFactory {

    private final PPTXOrchestrator orchestrator;
    private final com.excudo.xml.writers.animations.GroupIdManager sessionGroupIdManager;

    public CommandFactory(PPTXOrchestrator orchestrator) {
        if (orchestrator == null) {
            throw new IllegalArgumentException("PPTXOrchestrator cannot be null");
        }
        this.orchestrator = orchestrator;
        this.sessionGroupIdManager = new com.excudo.xml.writers.animations.SequentialGroupIdManager();
    }

    /**
     * Construct a Command from validated CommandParameters.
     *
     * <p>Builds a {@link CommandContext} from the ambient state (orchestrator,
     * displayAdapter, group-id manager) and delegates to the class registry.
     * Throws if no class is registered under the parameters' command name.
     */
    public Command createCommand(CommandParameters parameters, Object displayAdapter) {
        Command cmd = CommandClassRegistry.createFromParameters(
            parameters, new CommandContext(orchestrator, displayAdapter, sessionGroupIdManager));
        if (cmd == null) {
            throw new IllegalArgumentException(
                "Unknown command: " + parameters.getCommandName());
        }
        return cmd;
    }

    /**
     * Convert an LLM multi-action request into a list of constructed Commands.
     * {@link LLMRequestBridge} canonicalizes llmName parameter keys and
     * resolves action types; each action is then dispatched through
     * {@link #createCommand}.
     */
    public List<Command> createFromLLMRequest(
            com.excudo.core.commands.RequestSchema.LLMRequest request,
            SlideCreator slideCreator, File pptxDirectory) {
        if (request == null || request.getActions() == null) {
            throw new IllegalArgumentException("LLM request and actions cannot be null");
        }
        List<CommandParameters> bridged = LLMRequestBridge.bridgeAll(request);
        List<Command> commands = new ArrayList<>();
        for (CommandParameters parameters : bridged) {
            commands.add(createCommand(parameters, null));
        }
        return commands;
    }

    /** Wrap a list of commands as a single undoable {@link CompositeCommand}. */
    public CompositeCommand createComposite(List<Command> commands, String description) {
        return new CompositeCommand(commands, description);
    }

    /** Build a {@link CompositeCommand} directly from an LLM request. */
    public CompositeCommand createCompositeFromLLMRequest(
            com.excudo.core.commands.RequestSchema.LLMRequest request,
            SlideCreator slideCreator, File pptxDirectory) {
        List<Command> commands = createFromLLMRequest(request, slideCreator, pptxDirectory);
        String description = String.format("LLM Request: %d operations", commands.size());
        if (request.getMetadata() != null && request.getMetadata().getReasoning() != null) {
            description += " - " + request.getMetadata().getReasoning();
        }
        return createComposite(commands, description);
    }
}
