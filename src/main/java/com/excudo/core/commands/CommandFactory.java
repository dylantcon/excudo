package com.excudo.core.commands;

import com.excudo.core.commands.meta.LoadCommand;
import com.excudo.core.commands.meta.RedoCommand;
import com.excudo.core.commands.meta.SaveCommand;
import com.excudo.core.commands.meta.UndoCommand;
import com.excudo.core.commands.mutating.deck.CopySlideCommand;
import com.excudo.core.commands.mutating.deck.CreateSlideCommand;
import com.excudo.core.commands.mutating.deck.DeleteSlideCommand;
import com.excudo.core.commands.mutating.slide.AddAnimationCommand;
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
import com.excudo.core.parsing.CommandParameters;
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
    public Command createFromParameters(CommandParameters parameters, Object displayAdapter) {
        return createCommand(parameters, displayAdapter);
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
     * @return AddAnimationCommand
     */
    public AddAnimationCommand createAnimationEdit(int slideNumber, int spid, String animationType,
                                                   String direction, String trigger, String animationGroup) {
        return new AddAnimationCommand(slideNumber, spid, animationType, direction, trigger, animationGroup, orchestrator, sessionGroupIdManager);
    }

    /**
     * Create an animation edit command with effect-specific parameters.
     */
    public AddAnimationCommand createAnimationEdit(int slideNumber, int spid, String animationType,
                                                   String direction, String trigger, String animationGroup,
                                                   Map<String, String> effectParams) {
        return new AddAnimationCommand(slideNumber, spid, animationType, direction, trigger, animationGroup,
                                       orchestrator, sessionGroupIdManager, effectParams);
    }

    /**
     * Create an animation edit command with paragraph-level targeting.
     */
    public AddAnimationCommand createAnimationEdit(int slideNumber, int spid, String animationType,
                                                   String direction, String trigger, String animationGroup,
                                                   Integer paragraphStart, Integer paragraphEnd) {
        return new AddAnimationCommand(slideNumber, spid, animationType, direction, trigger, animationGroup,
                                       orchestrator, sessionGroupIdManager, Collections.emptyMap(),
                                       paragraphStart, paragraphEnd);
    }

    /**
     * Create an animation edit command with both effect parameters and
     * paragraph-level targeting. Used when a caller passes e.g. delayMs
     * AND a paragraphStart/paragraphEnd range -- the older two-arg
     * overloads would silently drop one of them.
     */
    public AddAnimationCommand createAnimationEdit(int slideNumber, int spid, String animationType,
                                                   String direction, String trigger, String animationGroup,
                                                   Map<String, String> effectParams,
                                                   Integer paragraphStart, Integer paragraphEnd) {
        return new AddAnimationCommand(slideNumber, spid, animationType, direction, trigger, animationGroup,
                                       orchestrator, sessionGroupIdManager,
                                       effectParams != null ? effectParams : Collections.emptyMap(),
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
     * @return AddAnimationCommand
     */
    public AddAnimationCommand createAddAnimation(int slideNumber, int spid, String animationType, 
                                                 String direction, String trigger) {
        return new AddAnimationCommand(slideNumber, spid, animationType, direction, trigger, orchestrator);
    }
    
    // ========== LLM REQUEST CONVERSION ==========

    /**
     * Create Commands from an LLM request using the unified bridge.
     * All action requests are converted to CommandParameters by LLMRequestBridge,
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

        List<CommandParameters> parameterss = LLMRequestBridge.bridgeAll(request);

        List<Command> commands = new ArrayList<>();
        for (CommandParameters parameters : parameterss) {
            commands.add(createCommand(parameters, null));
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
     * Create a Command from CommandParameters (bridge method for console integration).
     * 
     * This method bridges the gap between CommandRegistry parsing and Command creation,
     * enabling the console layer to use the sophisticated Command pattern infrastructure.
     * 
     * @param parameters the parsed command with validated parameters
     * @param displayAdapter the console display adapter (handles casting to specific interfaces)
     * @return appropriate Command object
     * @throws IllegalArgumentException if command name is not recognized
     */
    public Command createCommand(com.excudo.core.parsing.CommandParameters parameters,
                                Object displayAdapter) {
        String commandName = parameters.getCommandName();

        // Class-keyed registry takes priority -- Commands that own their
        // SCHEMA + fromParameters dispatch directly without going through
        // a sub-factory switch. Falls through when the command isn't yet
        // migrated.
        Command classRouted = CommandClassRegistry.createFromParameters(
            parameters, new CommandContext(orchestrator, displayAdapter, sessionGroupIdManager));
        if (classRouted != null) {
            return classRouted;
        }

        // Check if utility factory handles this command
        if (utilityFactory.handlesCommand(commandName)) {
            return utilityFactory.createFromParameters(parameters, displayAdapter);
        }
        
        // Check if system factory handles this command
        if (systemFactory.handlesCommand(commandName)) {
            return systemFactory.createFromParameters(parameters, displayAdapter);
        }
        
        // Check if presentation factory handles this command
        if (presentationFactory.handlesCommand(commandName)) {
            return presentationFactory.createFromParameters(parameters, displayAdapter);
        }
        
        // Check if slide factory handles this command
        if (slideFactory.handlesCommand(commandName)) {
            return slideFactory.createFromParameters(parameters, displayAdapter);
        }
        
        // Check if shape factory handles this command
        if (shapeFactory.handlesCommand(commandName)) {
            return shapeFactory.createFromParameters(parameters, displayAdapter);
        }
        
        switch (commandName) {

            // AddAnimationCommand.NAME/AddAnimationCommand.NAME, RemoveAnimationCommand.NAME, UpdateAnimationCommand.NAME
            // migrated to class registry (AddAnimationCommand, RemoveAnimationCommand,
            // UpdateAnimationCommand).

            // "llm" / "llm-config" migrated to class registry (LLMCommand, LLMConfigCommand)

            // Icon management commands
            case "icon":
                if (displayAdapter instanceof IconContext iconCtx) {
                    com.excudo.core.smartcontent.IconRepository iconRepo = iconCtx.getIconRepository();
                    if (iconRepo == null) {
                        throw new IllegalStateException("Icon repository not initialized. Load a presentation first.");
                    }
                    String iconSub = parameters.getString("subcommand");
                    String iconArgs = parameters.getString("args");
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