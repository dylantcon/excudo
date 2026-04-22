package com.excudo.core.commands;

import com.excudo.core.commands.meta.LoadCommand;
import com.excudo.core.commands.meta.RedoCommand;
import com.excudo.core.commands.meta.SaveCommand;
import com.excudo.core.commands.meta.UndoCommand;
import com.excudo.core.commands.mutating.deck.CopySlideCommand;
import com.excudo.core.commands.mutating.deck.CreateSlideCommand;
import com.excudo.core.commands.mutating.deck.DeleteSlideCommand;
import com.excudo.core.commands.mutating.slide.AnimationEditCommand;
import com.excudo.core.commands.mutating.slide.BulletPointEditCommand;
import com.excudo.core.commands.mutating.slide.RemoveAnimationCommand;
import com.excudo.core.commands.mutating.slide.UpdateAnimationCommand;
import com.excudo.core.commands.readonly.DumpShapeCommand;
import com.excudo.core.commands.readonly.DumpTimingCommand;
import com.excudo.core.commands.readonly.HelpCommand;
import com.excudo.core.commands.readonly.IconCommand;
import com.excudo.core.commands.readonly.ListAnimationTypesCommand;
import com.excudo.core.commands.readonly.ListAnimationsCommand;
import com.excudo.core.commands.readonly.ListLayoutsCommand;
import com.excudo.core.commands.readonly.ListSlidesCommand;
import com.excudo.core.commands.readonly.ListSpidsCommand;
import com.excudo.core.commands.readonly.ShowSlideCommand;

import com.excudo.core.orchestration.PPTXOrchestrator;
import com.excudo.core.model.AnimationBinding;
import com.excudo.core.model.AnimationType;
import com.excudo.xml.writers.SlideCreator;
import com.excudo.core.parsing.ParsedCommand;
import com.excudo.core.llm.LLMRequestBridge;
import java.io.File;
import java.util.Collections;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;

/**
 * Factory for creating GoF Commands from various sources.
 * 
 * This factory centralizes the creation of Command objects, providing
 * a clean interface for converting operations, LLM requests, and other
 * sources into proper GoF Commands.
 */
public class CommandFactory extends AbstractCommandFactory {
    
    private final PPTXOrchestrator orchestrator;
    private final com.excudo.xml.writers.animations.GroupIdManager sessionGroupIdManager;
    
    // Subfactories for delegating command creation
    private final UtilityCommandFactory utilityFactory;
    private final SystemCommandFactory systemFactory;
    private final PresentationCommandFactory presentationFactory;
    private final SlideCommandFactory slideFactory;
    private final ShapeCommandFactory shapeFactory;
    
    /**
     * Create a CommandFactory.
     * 
     * @param orchestrator the PPTX orchestrator for command execution
     */
    public CommandFactory(PPTXOrchestrator orchestrator) {
        super(orchestrator);
        this.orchestrator = orchestrator;
        this.sessionGroupIdManager = new com.excudo.xml.writers.animations.SequentialGroupIdManager();
        
        // Initialize subfactories
        this.utilityFactory = new UtilityCommandFactory(orchestrator);
        this.systemFactory = new SystemCommandFactory(orchestrator);
        this.presentationFactory = new PresentationCommandFactory(orchestrator);
        this.slideFactory = new SlideCommandFactory(orchestrator);
        this.shapeFactory = new ShapeCommandFactory(orchestrator);
    }
    
    // ========== ABSTRACT METHOD IMPLEMENTATIONS ==========

    @Override
    public boolean handlesCommand(String commandName) {
        // CommandFactory handles commands not handled by subfactories
        return !utilityFactory.handlesCommand(commandName) &&
               !systemFactory.handlesCommand(commandName) &&
               !presentationFactory.handlesCommand(commandName) &&
               !slideFactory.handlesCommand(commandName) &&
               !shapeFactory.handlesCommand(commandName);
    }

    @Override
    public Command createFromParsedCommand(ParsedCommand parsedCommand, Object displayAdapter) {
        return createCommand(parsedCommand, displayAdapter);
    }
    
    // ========== SLIDE OPERATIONS ==========
    
    /**
     * Create a slide creation command.
     * Delegates to SlideCommandFactory.
     * 
     * @param position the position to insert the slide
     * @param title the slide title
     * @param slideCreator the slide creator instance
     * @param pptxDirectory the PPTX directory
     * @return CreateSlideCommand
     */
    public CreateSlideCommand createSlideCreation(int position, String title, 
                                                 SlideCreator slideCreator, File pptxDirectory) {
        return slideFactory.createSlideCreation(position, title, slideCreator, pptxDirectory);
    }
    
    /**
     * Create a slide creation command with specific layout.
     * Delegates to SlideCommandFactory.
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
        return slideFactory.createSlideCreation(position, title, layoutId, slideCreator, pptxDirectory);
    }
    
    /**
     * Create a slide deletion command.
     * Delegates to SlideCommandFactory.
     * 
     * @param slideNumber the slide number to delete
     * @param safetyCheck whether to perform safety checks
     * @param reason reason for deletion
     * @return DeleteSlideCommand
     */
    public DeleteSlideCommand createSlideDeletion(int slideNumber, boolean safetyCheck, String reason) {
        return slideFactory.createSlideDeletion(slideNumber, safetyCheck, reason);
    }
    
    /**
     * Create a slide copy command.
     * Delegates to SlideCommandFactory.
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
        return slideFactory.createSlideCopy(sourceSlide, targetPosition, newTitle, modifications, preserveAnimations);
    }
    
    // ========== CONTENT AND ANIMATION OPERATIONS ==========
    
    
    /**
     * Create an animation edit command.
     * 
     * @param slideNumber the slide number containing the shape
     * @param spid the SPID of the shape to animate
     * @param animationType the type of animation
     * @param direction the animation direction
     * @param trigger the animation trigger
     * @return AnimationEditCommand
     */
    public AnimationEditCommand createAnimationEdit(int slideNumber, int spid, String animationType,
                                                   String direction, String trigger, String animationGroup) {
        return new AnimationEditCommand(slideNumber, spid, animationType, direction, trigger, animationGroup, orchestrator, sessionGroupIdManager);
    }

    /**
     * Create an animation edit command with effect-specific parameters.
     */
    public AnimationEditCommand createAnimationEdit(int slideNumber, int spid, String animationType,
                                                   String direction, String trigger, String animationGroup,
                                                   Map<String, String> effectParams) {
        return new AnimationEditCommand(slideNumber, spid, animationType, direction, trigger, animationGroup,
                                       orchestrator, sessionGroupIdManager, effectParams);
    }

    /**
     * Create an animation edit command with paragraph-level targeting.
     */
    public AnimationEditCommand createAnimationEdit(int slideNumber, int spid, String animationType,
                                                   String direction, String trigger, String animationGroup,
                                                   Integer paragraphStart, Integer paragraphEnd) {
        return new AnimationEditCommand(slideNumber, spid, animationType, direction, trigger, animationGroup,
                                       orchestrator, sessionGroupIdManager, Collections.emptyMap(),
                                       paragraphStart, paragraphEnd);
    }
    
    /**
     * Create a bullet point edit command.
     * 
     * @param slideNumber the slide number containing the shape
     * @param spid the SPID of the text shape to edit
     * @param operation the operation type (add, edit, remove, reorder)
     * @param bulletIndex the bullet point index (0-based, -1 for append)
     * @param newText the new text content
     * @param bulletStyle the bullet style
     * @return BulletPointEditCommand
     */
    public BulletPointEditCommand createBulletPointEdit(int slideNumber, int spid, String operation,
                                                       int bulletIndex, String newText, String bulletStyle) {
        return new BulletPointEditCommand(slideNumber, spid, operation, bulletIndex, 
                                         newText, bulletStyle, orchestrator);
    }
    
    
    
    /**
     * Create an add animation command.
     * 
     * @param slideNumber the slide number containing the shape
     * @param spid the SPID of the shape to animate
     * @param animationType the type of animation (fade, fly, wipe, etc.)
     * @param direction the animation direction (in, out, emphasis)
     * @param trigger the animation trigger (click, with_previous, after_previous)
     * @return AnimationEditCommand
     */
    public AnimationEditCommand createAddAnimation(int slideNumber, int spid, String animationType, 
                                                 String direction, String trigger) {
        return new AnimationEditCommand(slideNumber, spid, animationType, direction, trigger, orchestrator);
    }
    
    // ========== LLM REQUEST CONVERSION ==========

    /**
     * Create Commands from an LLM request using the unified bridge.
     * All action requests are converted to ParsedCommands by LLMRequestBridge,
     * then routed through the same createCommand() path as console commands.
     *
     * @param request the LLM request to convert
     * @param slideCreator the slide creator instance (unused - context from orchestrator)
     * @param pptxDirectory the PPTX directory (unused - context from orchestrator)
     * @return list of Commands
     */
    public List<Command> createFromLLMRequest(RequestSchema.LLMRequest request,
                                              SlideCreator slideCreator, File pptxDirectory) {
        if (request == null || request.getActions() == null) {
            throw new IllegalArgumentException("LLM request and actions cannot be null");
        }

        LLMRequestBridge bridge = new LLMRequestBridge();
        List<ParsedCommand> parsedCommands = bridge.bridgeAll(request);

        List<Command> commands = new ArrayList<>();
        for (ParsedCommand parsedCommand : parsedCommands) {
            commands.add(createCommand(parsedCommand, null));
        }
        return commands;
    }
    
    /**
     * Create a CompositeCommand from multiple Commands.
     * Useful for creating transaction-like behavior.
     * 
     * @param commands the commands to group
     * @param description description of the composite operation
     * @return CompositeCommand
     */
    public CompositeCommand createComposite(List<Command> commands, String description) {
        return new CompositeCommand(commands, description);
    }
    
    /**
     * Create a CompositeCommand from an LLM request.
     * All operations in the request will be executed as a single transaction.
     * 
     * @param request the LLM request
     * @param slideCreator the slide creator instance
     * @param pptxDirectory the PPTX directory
     * @return CompositeCommand containing all operations
     */
    public CompositeCommand createCompositeFromLLMRequest(RequestSchema.LLMRequest request,
                                                          SlideCreator slideCreator, File pptxDirectory) {
        List<Command> commands = createFromLLMRequest(request, slideCreator, pptxDirectory);
        
        String description = String.format("LLM Request: %d operations", commands.size());
        if (request.getMetadata() != null && request.getMetadata().getReasoning() != null) {
            description += " - " + request.getMetadata().getReasoning();
        }
        
        return createComposite(commands, description);
    }
    
    // ========== QUERY COMMAND CREATION METHODS ==========
    
    /**
     * Create a list slides command.
     * Delegates to UtilityCommandFactory.
     * 
     * @param display the console display interface
     * @param verbose whether to show detailed information
     * @return ListSlidesCommand
     */
    public ListSlidesCommand createListSlides(CommandDisplay display, boolean verbose) {
        return utilityFactory.createListSlides(display, verbose);
    }
    
    /**
     * Create a show slide command.
     * Delegates to UtilityCommandFactory.
     * 
     * @param display the console display interface
     * @param slideNumber the slide number to show (1-based)
     * @return ShowSlideCommand
     */
    public ShowSlideCommand createShowSlide(CommandDisplay display, int slideNumber) {
        return utilityFactory.createShowSlide(display, slideNumber);
    }
    
    /**
     * Create a help command for general help.
     * Delegates to SystemCommandFactory.
     * 
     * @param display the console display interface
     * @return HelpCommand
     */
    public HelpCommand createHelp(CommandDisplay display) {
        return systemFactory.createHelp(display);
    }
    
    /**
     * Create a help command for specific topic.
     * Delegates to SystemCommandFactory.
     * 
     * @param display the console display interface
     * @param topic the help topic
     * @return HelpCommand
     */
    public HelpCommand createHelp(CommandDisplay display, String topic) {
        return systemFactory.createHelp(display, topic);
    }
    
    /**
     * Create a list SPIDs command.
     * Delegates to UtilityCommandFactory.
     * 
     * @param display the console display interface
     * @param slideNumber the slide number (1-based)
     * @return ListSpidsCommand
     */
    public ListSpidsCommand createListSpids(CommandDisplay display, int slideNumber) {
        return utilityFactory.createListSpids(display, slideNumber);
    }
    
    /**
     * Create a list animations command.
     * Delegates to UtilityCommandFactory.
     * 
     * @param display the console display interface
     * @param slideNumber the slide number (1-based)
     * @return ListAnimationsCommand
     */
    public ListAnimationsCommand createListAnimations(CommandDisplay display, int slideNumber) {
        return utilityFactory.createListAnimations(display, slideNumber);
    }
    
    /**
     * Create a list layouts command.
     * Delegates to UtilityCommandFactory.
     * 
     * @param display the console display interface
     * @return ListLayoutsCommand
     */
    public ListLayoutsCommand createListLayouts(CommandDisplay display) {
        return utilityFactory.createListLayouts(display);
    }
    
    /**
     * Create a list animation types command (sessionless).
     * Delegates to UtilityCommandFactory.
     * 
     * @param display the console display interface
     * @return ListAnimationTypesCommand
     */
    public ListAnimationTypesCommand createListAnimationTypes(CommandDisplay display) {
        return utilityFactory.createListAnimationTypes(display);
    }
    
    /**
     * Create a dump shape command with slide range support (original functionality).
     * Delegates to UtilityCommandFactory.
     * 
     * @param display the console display interface
     * @param slideRange slide specification: "1", "1-5", "all"
     * @param writeToFile whether to write to timestamped log files
     * @return DumpShapeCommand
     */
    public DumpShapeCommand createDumpShape(CommandDisplay display, String slideRange, boolean writeToFile) {
        return utilityFactory.createDumpShape(display, slideRange, writeToFile);
    }

    /**
     * Create a dump shape command (legacy compatibility).
     * 
     * @param display the console display interface
     * @param slideNumber the slide number
     * @param spid the shape SPID
     * @return DumpShapeCommand
     */
    public DumpShapeCommand createDumpShape(CommandDisplay display, int slideNumber, int spid) {
        return new DumpShapeCommand(display, orchestrator, slideNumber, spid);
    }
    
    /**
     * Create a dump timing command with slide range support (original functionality).
     * Delegates to UtilityCommandFactory.
     * 
     * @param display the console display interface
     * @param slideRange slide specification: "1", "1-5", "all"
     * @param writeToFile whether to write to timestamped log files
     * @return DumpTimingCommand
     */
    public DumpTimingCommand createDumpTiming(CommandDisplay display, String slideRange, boolean writeToFile) {
        return utilityFactory.createDumpTiming(display, slideRange, writeToFile);
    }

    /**
     * Create a dump timing command (legacy compatibility).
     * 
     * @param display the console display interface
     * @param slideNumber the slide number
     * @return DumpTimingCommand
     */
    public DumpTimingCommand createDumpTiming(CommandDisplay display, int slideNumber) {
        return new DumpTimingCommand(display, orchestrator, slideNumber);
    }
    
    // ========== SYSTEM COMMAND CREATION METHODS ==========
    
    /**
     * Create an undo command.
     * Delegates to SystemCommandFactory.
     * 
     * @param sessionContext the CommandSessionContext for undo operations
     * @param display the console display interface
     * @return UndoCommand
     */
    public UndoCommand createUndo(CommandSessionContext sessionContext, CommandDisplay display) {
        return systemFactory.createUndo(sessionContext, display);
    }
    
    /**
     * Create a redo command.
     * Delegates to SystemCommandFactory.
     * 
     * @param sessionContext the CommandSessionContext for redo operations
     * @param display the console display interface
     * @return RedoCommand
     */
    public RedoCommand createRedo(CommandSessionContext sessionContext, CommandDisplay display) {
        return systemFactory.createRedo(sessionContext, display);
    }
    
    /**
     * Create a Command from ParsedCommand (bridge method for console integration).
     * 
     * This method bridges the gap between CommandRegistry parsing and Command creation,
     * enabling the console layer to use the sophisticated Command pattern infrastructure.
     * 
     * @param parsedCommand the parsed command with validated parameters
     * @param displayAdapter the console display adapter (handles casting to specific interfaces)
     * @return appropriate Command object
     * @throws IllegalArgumentException if command name is not recognized
     */
    public Command createCommand(com.excudo.core.parsing.ParsedCommand parsedCommand, 
                                Object displayAdapter) {
        String commandName = parsedCommand.getCommandName();
        
        // Check if utility factory handles this command
        if (utilityFactory.handlesCommand(commandName)) {
            return utilityFactory.createFromParsedCommand(parsedCommand, displayAdapter);
        }
        
        // Check if system factory handles this command
        if (systemFactory.handlesCommand(commandName)) {
            return systemFactory.createFromParsedCommand(parsedCommand, displayAdapter);
        }
        
        // Check if presentation factory handles this command
        if (presentationFactory.handlesCommand(commandName)) {
            return presentationFactory.createFromParsedCommand(parsedCommand, displayAdapter);
        }
        
        // Check if slide factory handles this command
        if (slideFactory.handlesCommand(commandName)) {
            return slideFactory.createFromParsedCommand(parsedCommand, displayAdapter);
        }
        
        // Check if shape factory handles this command
        if (shapeFactory.handlesCommand(commandName)) {
            return shapeFactory.createFromParsedCommand(parsedCommand, displayAdapter);
        }
        
        switch (commandName) {
                                    
            case "add-animation":
                Integer animSlideNum = parsedCommand.getInteger("slide");
                String animSpidStr = parsedCommand.getString("spid");
                String animType = parsedCommand.getString("type");
                String direction = parsedCommand.getString("direction");
                String trigger = parsedCommand.getString("trigger");
                Integer paragraphStart = parsedCommand.getInteger("paragraphStart");
                Integer paragraphEnd = parsedCommand.getInteger("paragraphEnd");
                String motionPath = parsedCommand.getString("path");
                Integer opacity = parsedCommand.getInteger("opacity");
                int animSpid = animSpidStr != null ? Integer.parseInt(animSpidStr) : 0;
                // Use proper animation grouping logic instead of hardcoded "default-group"
                String cleanTrigger = normalizeAnimationTrigger(trigger != null ? trigger : "on-click");
                String animationGroup = determineAnimationGroup(cleanTrigger);

                // Build effectParams map
                Map<String, String> animEffectParams = new HashMap<>();
                if (motionPath != null && !motionPath.isBlank()) {
                    animEffectParams.put(AnimationBinding.PARAM_MOTION_PATH, motionPath);
                }
                if (opacity != null) {
                    animEffectParams.put(AnimationBinding.PARAM_OPACITY, String.valueOf(opacity));
                }

                if (paragraphStart != null && paragraphEnd != null) {
                    return createAnimationEdit(animSlideNum != null ? animSlideNum : 1, animSpid,
                                             animType, direction != null ? direction : "in",
                                             cleanTrigger, animationGroup,
                                             paragraphStart, paragraphEnd);
                }
                if (!animEffectParams.isEmpty()) {
                    return createAnimationEdit(animSlideNum != null ? animSlideNum : 1, animSpid,
                                             animType, direction != null ? direction : "in",
                                             cleanTrigger, animationGroup, animEffectParams);
                }
                return createAnimationEdit(animSlideNum != null ? animSlideNum : 1, animSpid,
                                         animType, direction != null ? direction : "in",
                                         cleanTrigger, animationGroup);

            case "remove-animation":
                Integer removeSlide = parsedCommand.getInteger("slide");
                Integer removeNodeId = parsedCommand.getInteger("timingNodeId");
                return new RemoveAnimationCommand(
                    removeSlide != null ? removeSlide : 1,
                    removeNodeId != null ? removeNodeId : 0,
                    orchestrator);

            case "update-animation":
                Integer updateSlide = parsedCommand.getInteger("slide");
                Integer updateNodeId = parsedCommand.getInteger("timingNodeId");
                String updateProperty = parsedCommand.getString("property");
                String updateValue = parsedCommand.getString("value");
                return new UpdateAnimationCommand(
                    updateSlide != null ? updateSlide : 1,
                    updateNodeId != null ? updateNodeId : 0,
                    updateProperty,
                    updateValue,
                    orchestrator);

            // LLM AI-powered commands - delegate to specialized factory
            case "llm":
                String llmSubcommand = parsedCommand.getString("subcommand");
                String llmRequest = parsedCommand.getString("request");

                if (displayAdapter instanceof LLMContext) {
                    return LLMCommandFactory.createLLMCommand(
                        (LLMContext) displayAdapter,
                        llmSubcommand,
                        llmRequest);
                } else {
                    throw new IllegalStateException("LLM commands require LLMContext support");
                }

            // Icon management commands
            case "icon":
                if (displayAdapter instanceof IconContext iconCtx) {
                    com.excudo.core.smartcontent.IconRepository iconRepo = iconCtx.getIconRepository();
                    if (iconRepo == null) {
                        throw new IllegalStateException("Icon repository not initialized. Load a presentation first.");
                    }
                    String iconSub = parsedCommand.getString("subcommand");
                    String iconArgs = parsedCommand.getString("args");
                    String[] iconArgArray = iconArgs != null && !iconArgs.isEmpty()
                        ? iconArgs.split("\\s+") : new String[0];
                    return new IconCommand(iconRepo, iconSub, iconArgArray,
                        new IconCommand.DisplayAdapter() {
                            @Override public void displayMessage(String msg) { iconCtx.displayMessage(msg); }
                            @Override public void displayError(String msg) { iconCtx.displayError(msg); }
                            @Override public String promptUser(String prompt) { return iconCtx.promptUser(prompt); }
                        });
                } else {
                    throw new IllegalStateException("Icon commands require IconContext support");
                }

            default:
                throw new IllegalArgumentException("Unknown command: " + commandName);
        }
    }
    
    // LoadPresentationCommand and SavePresentationCommand removed - 
    // use LoadCommand and SaveCommand with proper session management instead
    
    // ========== ANIMATION GROUPING UTILITIES ==========
    
    /**
     * Normalize animation trigger for consistent processing.
     * Handles various trigger formats and converts them to standard forms.
     */
    private String normalizeAnimationTrigger(String trigger) {
        if (trigger == null) {
            return AnimationParameterRequirement.TRIGGER_ON_CLICK;
        }

        String cleanTrigger = trigger.toLowerCase().trim().replaceAll("[^a-z_0-9]", "");

        // Handle numbered clicks
        if (cleanTrigger.matches("click\\d+")) {
            return AnimationParameterRequirement.TRIGGER_ON_CLICK;
        }

        // Normalize trigger names
        switch (cleanTrigger) {
            case "click":
            case "onclick":
            case "on_click":
                return AnimationParameterRequirement.TRIGGER_ON_CLICK;
            case "withprevious":
            case "with_previous":
                return AnimationParameterRequirement.TRIGGER_WITH_PREVIOUS;
            case "afterprevious":
            case "after_previous":
                return AnimationParameterRequirement.TRIGGER_AFTER_PREVIOUS;
            default:
                return cleanTrigger;
        }
    }

    /**
     * Determine animation group based on trigger type.
     * For console commands, use simple trigger-based grouping.
     */
    private String determineAnimationGroup(String normalizedTrigger) {
        switch (normalizedTrigger) {
            case AnimationParameterRequirement.TRIGGER_ON_CLICK:
                return AnimationParameterRequirement.TRIGGER_ON_CLICK;
            case AnimationParameterRequirement.TRIGGER_WITH_PREVIOUS:
                return AnimationParameterRequirement.TRIGGER_WITH_PREVIOUS;
            case AnimationParameterRequirement.TRIGGER_AFTER_PREVIOUS:
                return AnimationParameterRequirement.TRIGGER_AFTER_PREVIOUS;
            default:
                return AnimationParameterRequirement.TRIGGER_ON_CLICK;
        }
    }
}