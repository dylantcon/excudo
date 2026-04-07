package com.excudo.core.commands;

import com.excudo.core.orchestration.PPTXOrchestrator;
import com.excudo.core.parsing.ParsedCommand;
import com.excudo.xml.writers.SlideCreator;
import java.io.File;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

/**
 * Factory for creating slide-related commands.
 * Handles create, delete, copy, and move slide operations.
 * LLM requests are bridged to ParsedCommand by LLMRequestBridge before reaching here.
 */
public class SlideCommandFactory extends AbstractCommandFactory {
    
    private static final Set<String> HANDLED_COMMANDS = new HashSet<>();

    static {
        HANDLED_COMMANDS.add("create");
        HANDLED_COMMANDS.add("delete");
        HANDLED_COMMANDS.add("copy");
        HANDLED_COMMANDS.add("move");
    }
    
    /**
     * Create a SlideCommandFactory.
     * 
     * @param orchestrator the PPTX orchestrator
     */
    public SlideCommandFactory(PPTXOrchestrator orchestrator) {
        super(orchestrator);
    }
    
    @Override
    public boolean handlesCommand(String commandName) {
        return HANDLED_COMMANDS.contains(commandName);
    }
    
    @Override
    public Command createFromParsedCommand(ParsedCommand parsedCommand, Object displayAdapter) {
        String commandName = parsedCommand.getCommandName();
        
        switch (commandName) {
            case "create":
                return createSlideCreation(parsedCommand, displayAdapter);
                
            case "delete":
                return createSlideDeletion(parsedCommand);

            case "copy":
                return createSlideCopyFromConsole(parsedCommand);

            case "move":
                return createSlideMoveFromConsole(parsedCommand);

            default:
                return null;
        }
    }
    
    // ========== CONSOLE + LLM COMMAND CREATION ==========
    
    /**
     * Create a slide creation command from console or LLM input.
     * Handles both console (with displayAdapter) and LLM (null displayAdapter) paths.
     */
    private Command createSlideCreation(ParsedCommand parsedCommand, Object displayAdapter) {
        Integer createPosition = parsedCommand.getInteger("position");
        String createTitle = parsedCommand.getString("title");
        String layoutId = parsedCommand.getString("layout");
        String content = parsedCommand.getString("content");

        if (displayAdapter instanceof CommandSessionContext) {
            // Console path: uses session context for slideCreator/pptxDir
            return new CreateSlideCommand(
                (CommandSessionContext) displayAdapter,
                (CommandDisplay) displayAdapter,
                createPosition != null ? createPosition : 1,
                createTitle != null ? createTitle : "New Slide",
                layoutId,
                content);
        } else {
            // LLM path: get slideCreator/pptxDir from orchestrator context
            SlideCreator slideCreator = null;
            File pptxDirectory = null;
            if (orchestrator != null && orchestrator.getContext().isPresent()) {
                slideCreator = orchestrator.getContext().get().getSlideCreator();
                pptxDirectory = null; // No longer available; commands use PPTXDocument via orchestrator
            }
            return new CreateSlideCommand(
                createPosition != null ? createPosition : 1,
                createTitle,
                layoutId,
                content,
                slideCreator,
                pptxDirectory,
                orchestrator);
        }
    }
    
    /**
     * Create a slide deletion command from console input.
     */
    private Command createSlideDeletion(ParsedCommand parsedCommand) {
        Integer slideNumber = parsedCommand.getInteger("slide");
        return createSlideDeletion(slideNumber != null ? slideNumber : 1, false, "Console delete command");
    }
    
    private Command createSlideCopyFromConsole(ParsedCommand parsedCommand) {
        Integer sourceSlide = parsedCommand.getInteger("slide");
        Integer targetPosition = parsedCommand.getInteger("position");
        String newTitle = parsedCommand.getString("title");
        return createSlideCopy(
            sourceSlide != null ? sourceSlide : 1,
            targetPosition != null ? targetPosition : 2,
            newTitle, null, true);
    }

    private Command createSlideMoveFromConsole(ParsedCommand parsedCommand) {
        Integer from = parsedCommand.getInteger("from");
        Integer to = parsedCommand.getInteger("to");
        return new MoveSlideCommand(
            from != null ? from : 1,
            to != null ? to : 1,
            orchestrator);
    }

    // ========== PUBLIC FACTORY METHODS (for direct use and delegation) ==========
    
    /**
     * Create a slide creation command.
     * 
     * @param position the position to insert the slide
     * @param title the slide title
     * @param slideCreator the slide creator instance
     * @param pptxDirectory the PPTX directory
     * @return CreateSlideCommand
     */
    public CreateSlideCommand createSlideCreation(int position, String title, 
                                                 SlideCreator slideCreator, File pptxDirectory) {
        return new CreateSlideCommand(position, title, null, slideCreator, pptxDirectory, orchestrator);
    }
    
    /**
     * Create a slide creation command with specific layout.
     * 
     * @param position the position to insert the slide
     * @param title the slide title
     * @param layoutId the layout ID (e.g., "slideLayout4")
     * @param slideCreator the slide creator instance
     * @param pptxDirectory the PPTX directory
     * @return CreateSlideCommand
     */
    public CreateSlideCommand createSlideCreation(int position, String title, String layoutId,
                                                 SlideCreator slideCreator, File pptxDirectory) {
        return new CreateSlideCommand(position, title, layoutId, slideCreator, pptxDirectory, orchestrator);
    }
    
    /**
     * Create a slide deletion command.
     * 
     * @param slideNumber the slide number to delete
     * @param safetyCheck whether to perform safety checks
     * @param reason reason for deletion
     * @return DeleteSlideCommand
     */
    public DeleteSlideCommand createSlideDeletion(int slideNumber, boolean safetyCheck, String reason) {
        return new DeleteSlideCommand(slideNumber, safetyCheck, reason, orchestrator);
    }
    
    /**
     * Create a slide copy command.
     * 
     * @param sourceSlide the source slide number
     * @param targetPosition the target position
     * @param newTitle optional new title
     * @param modifications optional modifications
     * @param preserveAnimations whether to preserve animations
     * @return CopySlideCommand
     */
    public CopySlideCommand createSlideCopy(int sourceSlide, int targetPosition, String newTitle,
                                            Map<String, Object> modifications, boolean preserveAnimations) {
        return new CopySlideCommand(sourceSlide, targetPosition, newTitle, 
                                   modifications, preserveAnimations, orchestrator);
    }
    
}