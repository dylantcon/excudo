package com.excudo.core.commands.mutating.deck;

import com.excudo.core.commands.Command;
import com.excudo.core.commands.CommandContext;
import com.excudo.core.commands.CommandDisplay;
import com.excudo.core.commands.CommandExecutionException;
import com.excudo.core.commands.CommandSessionContext;

import com.excudo.core.parsing.CommandParameters;
import com.excudo.core.parsing.CommandSchema;
import com.excudo.core.parsing.Parameter;
import com.excudo.core.results.ExecutionResult;
import com.excudo.core.results.SlideExecutionResult;
import com.excudo.core.orchestration.PPTXOrchestrator;
import com.excudo.core.orchestration.SessionManager;
import com.excudo.core.model.ParsedSlideData;
import com.excudo.core.model.SlideShape;
import com.excudo.xml.writers.SlideCreator;
import java.io.File;
import com.excudo.core.utils.Logger;
import com.excudo.core.utils.ComponentLogger;

/**
 * GoF Command for creating new slides.
 *
 * This is a pure Command pattern implementation that handles slide creation
 * directly using the orchestrator. Provides undo support by tracking created
 * slides and removing them on rollback.
 *
 * <p>Self-registers via {@link com.excudo.core.commands.CommandClassRegistry}:
 * the canonical name {@code create-slide} derives from the class name.
 * Dual-path construction: console (REPL) uses {@link CommandSessionContext}
 * to fetch the active orchestrator; the LLM/MCP path pulls {@link SlideCreator}
 * from the orchestrator's context directly. {@code fromParameters} branches
 * on whether the {@link CommandContext} carries a session.
 */
public class CreateSlideCommand implements Command {

    static final Parameter<Integer> POSITION = Parameter.ofInt("position")
        .slideNumber().description("Position to insert slide (1-based)")
        .llmName("slideNumber").required().build();
    static final Parameter<String> TITLE = Parameter.ofString("title")
        .description("Slide title").required().build();
    static final Parameter<String> LAYOUT = Parameter.ofString("layout")
        .description("Slide layout ID (e.g. slideLayout1)")
        .llmName("layoutId").def("slideLayout1").build();
    static final Parameter<String> CONTENT = Parameter.ofString("content")
        .description("JSON array of strings, one per content placeholder. e.g. "
            + "[\"- Bullet 1\\n- Bullet 2\"] for 1-content layouts, "
            + "[\"Left col\",\"Right col\"] for 2-content layouts. Supports markdown.")
        .required(false).build();

    public static final CommandSchema SCHEMA = CommandSchema.builder()
        .description("Create a new slide")
        .llmEnabled(true)
        .llmDescription("Create slide with title and content. ALWAYS pass content as a JSON array of markdown strings, one per placeholder.")
        .parameter(POSITION).parameter(TITLE).parameter(LAYOUT).parameter(CONTENT)
        .example("create-slide 2 \"My New Slide\"")
        .example("create-slide 2 \"My Slide\" slideLayout2")
        .build();

    public static Command fromParameters(CommandParameters p, CommandContext ctx) {
        int position = p.get(POSITION);
        String title = p.get(TITLE);
        String layoutId = p.get(LAYOUT);
        String content = p.opt(CONTENT).orElse(null);

        if (ctx.displayAdapter() instanceof CommandSessionContext) {
            // Console path: session-backed orchestrator + display sink.
            return new CreateSlideCommand(
                ctx.requireSession(), ctx.requireDisplay(),
                position, title, layoutId, content);
        }
        // LLM path: orchestrator already wired; pull SlideCreator from its context.
        SlideCreator slideCreator = null;
        if (ctx.orchestrator() != null && ctx.orchestrator().getContext().isPresent()) {
            slideCreator = ctx.orchestrator().getContext().get().getSlideCreator();
        }
        return new CreateSlideCommand(position, title, layoutId, content,
            slideCreator, null, ctx.orchestrator());
    }
    
    private final int position;
    private final String title;
    private final String layoutId;
    private final String content;
    private final SlideCreator slideCreator;
    private final File pptxDirectory;
    private final PPTXOrchestrator orchestrator;
    private boolean executed = false;
    private int createdSlideNumber = -1;
    private static final ComponentLogger logger = Logger.getLogger("SPID");
    
    /**
     * Create a CreateSlideCommand using session context.
     * 
     * @param sessionContext the session context for accessing orchestrator
     * @param display the console display interface
     * @param position the position to insert the slide
     * @param title the slide title
     * @param layoutId the layout ID (optional, null for default)
     */
    public CreateSlideCommand(CommandSessionContext sessionContext, CommandDisplay display,
                             int position, String title, String layoutId) {
        this(sessionContext, display, position, title, layoutId, null);
    }

    public CreateSlideCommand(CommandSessionContext sessionContext, CommandDisplay display,
                             int position, String title, String layoutId, String content) {
        if (sessionContext == null) {
            throw new IllegalArgumentException("CommandSessionContext cannot be null");
        }
        if (display == null) {
            throw new IllegalArgumentException("CommandDisplay cannot be null");
        }

        this.position = position;
        this.title = title;
        this.layoutId = layoutId;
        this.content = content;

        // Get orchestrator from session context
        this.orchestrator = sessionContext.getCurrentOrchestrator();
        if (this.orchestrator == null) {
            throw new IllegalArgumentException("No active orchestrator in session context");
        }

        // These will be accessed from orchestrator when needed
        this.slideCreator = null;
        this.pptxDirectory = null;
    }
    
    /**
     * Create a CreateSlideCommand.
     * 
     * @param position the position to insert the slide
     * @param title the slide title
     * @param layoutId the layout ID (optional, null for default)
     * @param slideCreator the slide creator instance
     * @param pptxDirectory the PPTX directory
     * @param orchestrator the PPTX orchestrator for execution
     */
    public CreateSlideCommand(int position, String title, String layoutId, SlideCreator slideCreator,
                             File pptxDirectory, PPTXOrchestrator orchestrator) {
        this(position, title, layoutId, null, slideCreator, pptxDirectory, orchestrator);
    }

    public CreateSlideCommand(int position, String title, String layoutId, String content,
                             SlideCreator slideCreator, File pptxDirectory, PPTXOrchestrator orchestrator) {
        if (slideCreator == null) {
            throw new IllegalArgumentException("SlideCreator cannot be null");
        }
        if (orchestrator == null) {
            throw new IllegalArgumentException("PPTXOrchestrator cannot be null");
        }

        this.position = position;
        this.title = title;
        this.layoutId = layoutId;
        this.content = content;
        this.slideCreator = slideCreator;
        this.pptxDirectory = pptxDirectory;
        this.orchestrator = orchestrator;
    }
    
    /**
     * Execute the slide creation command.
     * 
     * @throws CommandExecutionException if the operation fails
     */
    @Override
    public void execute() {
        logger.debug("CREATE SLIDE EXECUTE START: " + getDescription() + " (executed=" + executed + ")");
        
        if (executed) {
            throw new CommandExecutionException(getDescription(), "execute", "Command has already been executed");
        }
        
        try {
            // Resolve layout ID (supports fuzzy matching: "title-content" -> "slideLayout2")
            String resolvedLayoutId = layoutId;
            if (layoutId != null && !layoutId.trim().isEmpty() && !layoutId.startsWith("slideLayout")) {
                try {
                    var contextOpt = orchestrator.getContext();
                    if (contextOpt.isPresent() && contextOpt.get().getContextService() != null) {
                        com.excudo.core.model.LayoutManager lm =
                            contextOpt.get().getContextService().getLayoutManager();
                        String resolved = lm.resolveLayoutId(layoutId);
                        if (resolved != null) {
                            logger.debug("Resolved layout ID '{}' -> '{}'", layoutId, resolved);
                            resolvedLayoutId = resolved;
                        } else {
                            logger.warn("Could not resolve layout ID '{}', using as-is", layoutId);
                        }
                    }
                } catch (Exception e) {
                    logger.debug("Layout resolution failed, using original: {}", e.getMessage());
                }
            }

            logger.debug("CREATE SLIDE EXECUTE: About to call orchestrator.createSlide(" + position + ", '" + title + "', '" + resolvedLayoutId + "')");
            // Use orchestrator to create slide with optional layoutId and title
            SlideExecutionResult result;
            if (resolvedLayoutId != null && !resolvedLayoutId.trim().isEmpty()) {
                // Use provided title or empty string for blank layouts
                String slideTitle = (title != null && !title.trim().isEmpty()) ? title : "";
                result = orchestrator.createSlide(position, slideTitle, resolvedLayoutId);
            } else {
                // For default layout, provide a reasonable title
                String slideTitle = (title != null && !title.trim().isEmpty()) ? title : "New Slide";
                result = orchestrator.createSlide(position, slideTitle);
            }
            logger.debug("CREATE SLIDE EXECUTE: orchestrator.createSlide returned, isSuccess=" + result.isSuccess());
            
            if (!result.isSuccess()) {
                logger.error("CREATE SLIDE EXECUTE FAILED: " + result.getMessage());
                throw new CommandExecutionException(
                    getDescription(), 
                    "execute", 
                    result.getMessage()
                );
            }
            
            // Track the created slide for undo purposes
            createdSlideNumber = result.getSlideNumber();

            // Populate content placeholder if content was provided
            if (content != null && !content.trim().isEmpty() && createdSlideNumber > 0) {
                populateContentPlaceholder(createdSlideNumber, content);
            }

            logger.debug("CREATE SLIDE EXECUTE: Setting executed=true, createdSlideNumber=" + createdSlideNumber);
            executed = true;
            logger.debug("CREATE SLIDE EXECUTE SUCCESS: executed=" + executed);

            // Notify state listeners that the slide list changed.
            SessionManager.getInstance().firePresentationStructureChanged();
            
        } catch (CommandExecutionException e) {
            logger.error("CREATE SLIDE EXECUTE COMMAND EXCEPTION: " + e.getMessage());
            throw e;
        } catch (Exception e) {
            logger.debug("CREATE SLIDE EXECUTE GENERAL EXCEPTION: " + e.getMessage());
            throw new CommandExecutionException(
                getDescription(), 
                "execute", 
                "Failed to create slide: " + e.getMessage(),
                e
            );
        }
    }
    
    /**
     * Undo the slide creation command by deleting the created slide.
     * 
     * @throws CommandExecutionException if the undo operation fails
     */
    @Override
    public void undo() {
        if (!executed) {
            throw new CommandExecutionException(getDescription(), "undo", "Command has not been executed");
        }
        
        if (!canUndo()) {
            throw new CommandExecutionException(getDescription(), "undo", "Command cannot be undone");
        }
        
        try {
            // Undo by deleting the created slide
            SlideExecutionResult result = orchestrator.deleteSlide(createdSlideNumber);
            
            if (!result.isSuccess()) {
                throw new CommandExecutionException(
                    getDescription(), 
                    "undo", 
                    "Failed to delete created slide: " + result.getMessage()
                );
            }
            
            executed = false;
            createdSlideNumber = -1;
            
        } catch (CommandExecutionException e) {
            throw e;
        } catch (Exception e) {
            throw new CommandExecutionException(
                getDescription(), 
                "undo", 
                "Failed to undo slide creation: " + e.getMessage(),
                e
            );
        }
    }
    
    /**
     * Check if this command can be undone.
     * Slide creation can be undone by deleting the created slide.
     * 
     * @return true if the command can be undone
     */
    @Override
    public boolean canUndo() {
        return executed && createdSlideNumber > 0;
    }
    
    /**
     * Get the description of this command.
     * 
     * @return description of the slide creation operation
     */
    @Override
    public String getDescription() {
        String titleDesc = (title != null && !title.trim().isEmpty()) ? "with title '" + title + "'" : "without title";
        if (layoutId != null && !layoutId.trim().isEmpty()) {
            return String.format("Create slide at position %d %s using layout '%s'", position, titleDesc, layoutId);
        } else {
            return String.format("Create slide at position %d %s", position, titleDesc);
        }
    }
    
    /**
     * Check if this command has been executed.
     * 
     * @return true if execute() has been called successfully
     */
    @Override
    public boolean isExecuted() {
        return executed;
    }
    
    /**
     * Get the position where the slide was inserted.
     * 
     * @return the insertion position
     */
    public int getPosition() {
        return position;
    }
    
    /**
     * Get the slide title.
     * 
     * @return the slide title
     */
    public String getTitle() {
        return title;
    }
    
    /**
     * Get the layout ID.
     * 
     * @return the layout ID, or null if using default layout
     */
    public String getLayoutId() {
        return layoutId;
    }
    
    /**
     * Get the slide number that was created (available after execution).
     *
     * @return the created slide number, or -1 if not executed
     */
    public int getCreatedSlideNumber() {
        return createdSlideNumber;
    }

    /**
     * Find the content placeholder on the newly created slide and populate it.
     * Looks for non-title placeholders (typically SPID 3+) and writes the content text.
     * If content starts with '[', it is parsed as a JSON array of strings and distributed
     * across multiple content placeholders (e.g. two-column layouts).
     */
    private void populateContentPlaceholder(int slideNumber, String text) {
        try {
            ExecutionResult<ParsedSlideData> dataResult = orchestrator.getSlideData(slideNumber);
            if (!dataResult.isSuccess() || dataResult.getData().isEmpty()) {
                logger.debug("Could not read slide data for content population on slide " + slideNumber);
                return;
            }

            ParsedSlideData slideData = dataResult.getData().get();
            // Collect all non-title placeholders sorted by SPID
            java.util.List<SlideShape> contentPlaceholders = new java.util.ArrayList<>();
            for (SlideShape shape : slideData.getShapeRegistry().getAllShapes()) {
                String name = shape.getName() != null ? shape.getName().toLowerCase() : "";
                boolean isTitle = name.contains("title") || name.contains("subtitle");
                if (shape.getType() == SlideShape.ShapeType.PLACEHOLDER && !isTitle) {
                    contentPlaceholders.add(shape);
                }
            }

            if (contentPlaceholders.isEmpty()) {
                logger.debug("No content placeholder found on slide " + slideNumber + " for content population");
                return;
            }

            contentPlaceholders.sort(java.util.Comparator.comparingInt(SlideShape::getSpid));

            // Check if content is a JSON array of strings
            String trimmed = text.trim();
            if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                java.util.List<String> items = parseSimpleJsonArray(trimmed);
                for (int i = 0; i < items.size() && i < contentPlaceholders.size(); i++) {
                    int spid = contentPlaceholders.get(i).getSpid();
                    ExecutionResult<Void> editResult = orchestrator.editShapeText(slideNumber, spid, items.get(i));
                    if (editResult.isSuccess()) {
                        logger.debug("Populated content placeholder SPID " + spid + " on slide " + slideNumber);
                    } else {
                        logger.debug("Failed to populate content placeholder SPID " + spid + ": " + editResult.getMessage());
                    }
                }
            } else {
                // Single string: populate first content placeholder
                int contentSpid = contentPlaceholders.get(0).getSpid();
                ExecutionResult<Void> editResult = orchestrator.editShapeText(slideNumber, contentSpid, text);
                if (editResult.isSuccess()) {
                    logger.debug("Populated content placeholder SPID " + contentSpid + " on slide " + slideNumber);
                } else {
                    logger.debug("Failed to populate content placeholder: " + editResult.getMessage());
                }
            }
        } catch (Exception e) {
            // Content population is best-effort; don't fail the slide creation
            logger.debug("Content population failed (non-fatal): " + e.getMessage());
        }
    }

    /**
     * Parse a simple JSON array of strings: ["item1", "item2", ...]
     */
    private java.util.List<String> parseSimpleJsonArray(String json) {
        java.util.List<String> items = new java.util.ArrayList<>();
        int i = 1; // skip opening '['
        while (i < json.length()) {
            // Skip whitespace and commas
            while (i < json.length() && (json.charAt(i) == ' ' || json.charAt(i) == ','
                    || json.charAt(i) == '\n' || json.charAt(i) == '\r' || json.charAt(i) == '\t')) i++;
            if (i >= json.length() || json.charAt(i) == ']') break;
            if (json.charAt(i) == '"') {
                i++; // skip opening quote
                StringBuilder sb = new StringBuilder();
                while (i < json.length() && json.charAt(i) != '"') {
                    if (json.charAt(i) == '\\' && i + 1 < json.length()) {
                        char next = json.charAt(i + 1);
                        if (next == '"') { sb.append('"'); i += 2; }
                        else if (next == 'n') { sb.append('\n'); i += 2; }
                        else if (next == '\\') { sb.append('\\'); i += 2; }
                        else { sb.append(json.charAt(i)); i++; }
                    } else {
                        sb.append(json.charAt(i));
                        i++;
                    }
                }
                items.add(sb.toString());
                if (i < json.length()) i++; // skip closing quote
            } else {
                i++; // skip unexpected char
            }
        }
        return items;
    }
}