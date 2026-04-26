package com.excudo.core.commands;

import com.excudo.core.commands.mutating.theme.ApplyThemeCommand;
import com.excudo.core.commands.mutating.theme.CreateThemeCommand;
import com.excudo.core.commands.mutating.theme.DeleteThemeCommand;
import com.excudo.core.commands.mutating.theme.EditThemeCommand;
import com.excudo.core.commands.readonly.DumpShapeCommand;
import com.excudo.core.commands.readonly.DumpTimingCommand;
import com.excudo.core.commands.readonly.ListAnimationTypesCommand;
import com.excudo.core.commands.readonly.ListAnimationsCommand;
import com.excudo.core.commands.readonly.ListArrangeOpsCommand;
import com.excudo.core.commands.readonly.ListLayoutsCommand;
import com.excudo.core.commands.readonly.ListNotesCommand;
import com.excudo.core.commands.readonly.ListShapeTypesCommand;
import com.excudo.core.commands.readonly.ListSlidesCommand;
import com.excudo.core.commands.readonly.ListSpidsCommand;
import com.excudo.core.commands.readonly.ListThemesCommand;
import com.excudo.core.commands.readonly.ListTransitionTypesCommand;
import com.excudo.core.commands.readonly.RenderSlideCommand;
import com.excudo.core.commands.readonly.ShowShapeCommand;
import com.excudo.core.commands.readonly.ShowSlideCommand;
import com.excudo.core.commands.readonly.ShowThemeCommand;

import com.excudo.core.orchestration.PPTXOrchestrator;
import com.excudo.core.parsing.CommandParameters;
import java.util.Set;
import java.util.HashSet;

/**
 * Factory for creating utility/query commands.
 * 
 * This factory handles read-only inspection and query commands that work with
 * UtilityOrchestrationManager as the receiver. These commands are console-only
 * as the LLM doesn't need query operations.
 * 
 * Handled commands:
 * - list: List all slides
 * - show: Show slide details
 * - list-layouts: List available layouts
 * - list-spids: List SPIDs on a slide
 * - list-animations: List animations on a slide
 * - list-animation-types: List available animation types
 * - list-notes: List notes for a slide
 * - dump-shape: Dump shape data
 * - dump-timing: Dump timing tree
 * - show-shape: Show shape details
 */
public class UtilityCommandFactory extends AbstractCommandFactory {

    private static RenderSlideCommand.SlideRenderFunction slideRenderFunction;
    private static ContactSheetRenderFunction contactSheetRenderFunction;

    /**
     * Register the slide render function from the view layer.
     * Must be called before any render commands are executed.
     */
    public static void setSlideRenderFunction(RenderSlideCommand.SlideRenderFunction fn) {
        slideRenderFunction = fn;
    }

    public static RenderSlideCommand.SlideRenderFunction getSlideRenderFunction() {
        return slideRenderFunction;
    }

    /**
     * View-supplied function that renders multiple slides into a single
     * contact-sheet PNG file. Registered from the view layer at boot so
     * {@code core/*} never imports view/rendering types.
     */
    @FunctionalInterface
    public interface ContactSheetRenderFunction {
        /**
         * @param doc          source document
         * @param slideNumbers 1-indexed slide numbers in grid order
         * @param outputFile   destination PNG file
         * @param thumbWidth   pixel width per thumbnail
         * @param thumbHeight  pixel height per thumbnail
         * @param columns      grid columns; rows derived from slideNumbers.length/columns
         * @param gutter       transparent padding between thumbnails
         * @param theme        resolved theme definition, or null to use the first available
         * @param clrMap       master color map
         * @param bgHexForSlide per-slide background hex resolver (may return null)
         * @param masterStyles master text-level styles by placeholder type
         * @return {@code int[]}{sheet width, sheet height} on success
         */
        int[] render(com.excudo.core.model.PPTXDocument doc, int[] slideNumbers,
                     java.io.File outputFile,
                     int thumbWidth, int thumbHeight, int columns, int gutter,
                     com.excudo.core.themes.ThemeDefinition theme,
                     java.util.Map<String, String> clrMap,
                     java.util.function.IntFunction<String> bgHexForSlide,
                     java.util.Map<String, com.excudo.core.themes.TextLevelStyle[]> masterStyles)
                throws Exception;
    }

    public static void setContactSheetRenderFunction(ContactSheetRenderFunction fn) {
        contactSheetRenderFunction = fn;
    }

    public static ContactSheetRenderFunction getContactSheetRenderFunction() {
        return contactSheetRenderFunction;
    }

    private static final Set<String> HANDLED_COMMANDS = new HashSet<>();
    
    static {
        // Query commands
        HANDLED_COMMANDS.add("list");
        HANDLED_COMMANDS.add("show");
        HANDLED_COMMANDS.add("list-layouts");
        HANDLED_COMMANDS.add("list-spids");
        HANDLED_COMMANDS.add("list-animations");
        HANDLED_COMMANDS.add("list-animation-types");
        HANDLED_COMMANDS.add("list-notes");
        
        // Dump/debug commands
        HANDLED_COMMANDS.add("dump-shape");
        HANDLED_COMMANDS.add("dump-timing");
        HANDLED_COMMANDS.add("show-shape");

        // Shape type listing
        HANDLED_COMMANDS.add("list-shape-types");

        // Transition type listing
        HANDLED_COMMANDS.add("list-transition-types");

        // Arrange operations listing
        HANDLED_COMMANDS.add("list-arrange-ops");

        // Render command
        HANDLED_COMMANDS.add("render");

        // Theme commands
        HANDLED_COMMANDS.add("list-themes");
        HANDLED_COMMANDS.add("apply-theme");
        HANDLED_COMMANDS.add("show-theme");
        HANDLED_COMMANDS.add("create-theme");
        HANDLED_COMMANDS.add("edit-theme");
        HANDLED_COMMANDS.add("delete-theme");
    }
    
    /**
     * Create a UtilityCommandFactory.
     * 
     * @param orchestrator the PPTX orchestrator
     */
    public UtilityCommandFactory(PPTXOrchestrator orchestrator) {
        super(orchestrator);
    }
    
    @Override
    public boolean handlesCommand(String commandName) {
        return HANDLED_COMMANDS.contains(commandName);
    }
    
    @Override
    public Command createFromParameters(CommandParameters parameters, Object displayAdapter) {
        String commandName = parameters.getCommandName();
        CommandDisplay display = (CommandDisplay) displayAdapter;
        
        switch (commandName) {
            case "list":
                return createListSlides(parameters, display);
                
            case "show":
                return createShowSlide(parameters, display);
                
            case "list-layouts":
                String layoutThemeId = parameters.getString("themeId");
                return new ListLayoutsCommand(orchestrator, display, layoutThemeId);
                
            case "list-spids":
                return createListSpids(parameters, display);
                
            case "list-animations":
                return createListAnimations(parameters, display);
                
            case "list-animation-types":
                return createListAnimationTypesInternal(display);

            case "list-shape-types":
                return new ListShapeTypesCommand(display);

            case "list-transition-types":
                return new ListTransitionTypesCommand(display);
                
            case "list-notes":
                return createListNotes(parameters, displayAdapter);
                
            case "dump-shape":
                return createDumpShape(parameters, display);
                
            case "dump-timing":
                return createDumpTiming(parameters, display);
                
            case "show-shape":
                return createShowShape(parameters, displayAdapter);

            case "list-arrange-ops":
                return new ListArrangeOpsCommand(display);

            case "list-themes":
                return new ListThemesCommand((CommandDisplay) displayAdapter);

            case "apply-theme":
                String themeId = parameters.getString("themeId");
                return new ApplyThemeCommand(themeId, orchestrator, (CommandDisplay) displayAdapter);

            case "render":
                int renderSlide = parameters.getInteger("slide") != null ? parameters.getInteger("slide") : 1;
                String renderOutput = parameters.getString("output");
                int renderWidth = parameters.getInteger("width") != null ? parameters.getInteger("width") : 1280;
                int renderHeight = parameters.getInteger("height") != null ? parameters.getInteger("height") : 720;
                if (slideRenderFunction == null) {
                    throw new IllegalStateException("Render function not registered. Call setSlideRenderFunction() first.");
                }
                return new RenderSlideCommand(orchestrator, renderSlide, renderOutput,
                    renderWidth, renderHeight, slideRenderFunction);

            case "show-theme":
                return new ShowThemeCommand(parameters.getString("themeId"), display);

            case "create-theme":
                return new CreateThemeCommand(
                    parameters.getString("id"),
                    parameters.getString("baseTheme"),
                    parameters.getString("displayName"),
                    display);

            case "edit-theme":
                return new EditThemeCommand(
                    parameters.getString("themeId"),
                    parameters.getString("property"),
                    parameters.getString("value"),
                    display);

            case "delete-theme":
                return new DeleteThemeCommand(parameters.getString("themeId"), display);

            default:
                return null;
        }
    }
    
    // ========== LIST COMMANDS ==========
    
    /**
     * Create a list slides command.
     */
    private Command createListSlides(CommandParameters parameters, CommandDisplay display) {
        Boolean verbose = parameters.getBoolean("verbose");
        return new ListSlidesCommand(orchestrator, display, 
                                    verbose != null ? verbose : false);
    }
    
    /**
     * Create a show slide command.
     */
    private Command createShowSlide(CommandParameters parameters, CommandDisplay display) {
        Integer slideNumber = parameters.getInteger("slide");
        return new ShowSlideCommand(orchestrator, display, 
                                   slideNumber != null ? slideNumber : 1);
    }
    
    /**
     * Create a list SPIDs command.
     */
    private Command createListSpids(CommandParameters parameters, CommandDisplay display) {
        Integer slideNumber = parameters.getInteger("slide");
        return new ListSpidsCommand(orchestrator, display, 
                                   slideNumber != null ? slideNumber : 1);
    }
    
    /**
     * Create a list animations command.
     */
    private Command createListAnimations(CommandParameters parameters, CommandDisplay display) {
        Integer slideNumber = parameters.getInteger("slide");
        return new ListAnimationsCommand(orchestrator, display, 
                                        slideNumber != null ? slideNumber : 1);
    }
    
    /**
     * Create a list animation types command (internal).
     */
    private Command createListAnimationTypesInternal(CommandDisplay display) {
        return new ListAnimationTypesCommand(display);
    }
    
    /**
     * Create a list notes command.
     */
    private Command createListNotes(CommandParameters parameters, Object displayAdapter) {
        Integer slideNumber = parameters.getInteger("slide");
        return new ListNotesCommand(
            (CommandSessionContext) displayAdapter,
            (CommandDisplay) displayAdapter,
            slideNumber);
    }
    
    // ========== DUMP/DEBUG COMMANDS ==========
    
    /**
     * Create a dump shape command.
     */
    private Command createDumpShape(CommandParameters parameters, CommandDisplay display) {
        // Support original slide range format: "1", "1-5", "all"
        String slideRange = parameters.getString("range");
        if (slideRange == null || slideRange.trim().isEmpty()) {
            // Fallback: try legacy slide parameter for backward compatibility
            Integer slideNumber = parameters.getInteger("slide");
            slideRange = slideNumber != null ? String.valueOf(slideNumber) : "1";
        }
        // Original behavior always wrote to file - preserve this
        return new DumpShapeCommand(display, orchestrator, slideRange, true);
    }
    
    /**
     * Create a dump timing command.
     */
    private Command createDumpTiming(CommandParameters parameters, CommandDisplay display) {
        // Support original slide range format: "1", "1-5", "all"
        String timingRange = parameters.getString("range");
        if (timingRange == null || timingRange.trim().isEmpty()) {
            // Fallback: try legacy slide parameter for backward compatibility
            Integer slideNumber = parameters.getInteger("slide");
            timingRange = slideNumber != null ? String.valueOf(slideNumber) : "1";
        }
        // Original behavior always wrote to file - preserve this
        return new DumpTimingCommand(display, orchestrator, timingRange, true);
    }
    
    /**
     * Create a show shape command.
     */
    private Command createShowShape(CommandParameters parameters, Object displayAdapter) {
        Integer slideNumber = parameters.getInteger("slide");
        String spid = parameters.getString("spid");
        return new ShowShapeCommand(
            (CommandSessionContext) displayAdapter,
            (CommandDisplay) displayAdapter,
            slideNumber != null ? slideNumber : 1,
            spid != null ? spid : "0");
    }
    
    // ========== PUBLIC FACTORY METHODS (for direct use) ==========
    
    /**
     * Create a list slides command directly.
     * 
     * @param display the console display interface
     * @param verbose whether to show detailed information
     * @return ListSlidesCommand
     */
    public ListSlidesCommand createListSlides(CommandDisplay display, boolean verbose) {
        return new ListSlidesCommand(orchestrator, display, verbose);
    }
    
    /**
     * Create a show slide command directly.
     * 
     * @param display the console display interface
     * @param slideNumber the slide number to show (1-based)
     * @return ShowSlideCommand
     */
    public ShowSlideCommand createShowSlide(CommandDisplay display, int slideNumber) {
        return new ShowSlideCommand(orchestrator, display, slideNumber);
    }
    
    /**
     * Create a list SPIDs command directly.
     * 
     * @param display the console display interface
     * @param slideNumber the slide number (1-based)
     * @return ListSpidsCommand
     */
    public ListSpidsCommand createListSpids(CommandDisplay display, int slideNumber) {
        return new ListSpidsCommand(orchestrator, display, slideNumber);
    }
    
    /**
     * Create a list animations command directly.
     * 
     * @param display the console display interface
     * @param slideNumber the slide number (1-based)
     * @return ListAnimationsCommand
     */
    public ListAnimationsCommand createListAnimations(CommandDisplay display, int slideNumber) {
        return new ListAnimationsCommand(orchestrator, display, slideNumber);
    }
    
    /**
     * Create a list layouts command directly.
     * 
     * @param display the console display interface
     * @return ListLayoutsCommand
     */
    public ListLayoutsCommand createListLayouts(CommandDisplay display) {
        return new ListLayoutsCommand(orchestrator, display);
    }
    
    /**
     * Create a list animation types command directly.
     * 
     * @param display the console display interface
     * @return ListAnimationTypesCommand
     */
    public ListAnimationTypesCommand createListAnimationTypes(CommandDisplay display) {
        return new ListAnimationTypesCommand(display);
    }
    
    /**
     * Create a dump shape command directly.
     * 
     * @param display the console display interface
     * @param slideRange slide specification: "1", "1-5", "all"
     * @param writeToFile whether to write to timestamped log files
     * @return DumpShapeCommand
     */
    public DumpShapeCommand createDumpShape(CommandDisplay display, String slideRange, boolean writeToFile) {
        return new DumpShapeCommand(display, orchestrator, slideRange, writeToFile);
    }
    
    /**
     * Create a dump timing command directly.
     * 
     * @param display the console display interface
     * @param slideRange slide specification: "1", "1-5", "all"
     * @param writeToFile whether to write to timestamped log files
     * @return DumpTimingCommand
     */
    public DumpTimingCommand createDumpTiming(CommandDisplay display, String slideRange, boolean writeToFile) {
        return new DumpTimingCommand(display, orchestrator, slideRange, writeToFile);
    }
}