package com.excudo.core.commands.mutating.slide;

import com.excudo.core.commands.Command;
import com.excudo.core.commands.CommandContext;
import com.excudo.core.commands.CommandDisplay;
import com.excudo.core.commands.CommandExecutionException;

import com.excudo.core.orchestration.PPTXOrchestrator;
import com.excudo.core.parsing.CommandParameters;
import com.excudo.core.parsing.CommandSchema;
import com.excudo.core.parsing.Parameter;
import com.excudo.core.results.ExecutionResult;
import com.excudo.core.model.SlideShape;
import com.excudo.core.model.ShapeGeometry;
import com.excudo.core.model.ParsedSlideData;
import com.excudo.core.model.TextBody;
import com.excudo.core.model.TextParagraph;
import com.excudo.core.model.TextRun;
import java.util.logging.Logger;

/**
 * GoF Command for editing text content of shapes.
 *
 * This command allows editing the text content of a specific shape (SPID)
 * on a slide with undo capability. Includes geometry validation and layout
 * context awareness for debugging placeholder inheritance issues.
 *
 * <p>Self-registers via {@link com.excudo.core.commands.CommandClassRegistry}:
 * the canonical name {@code content-edit} derives from the class name
 * ({@code ContentEditCommand}).
 */
public class ContentEditCommand implements Command {

    static final Parameter<Integer> SLIDE = Parameter.ofInt("slide")
        .slideNumber().description("Slide number").llmName("slideNumber").required().build();
    static final Parameter<Integer> SPID = Parameter.ofInt("spid")
        .spid().description("Shape ID (must be valid existing SPID)").llmName("targetSpid").required().build();
    static final Parameter<String> TEXT = Parameter.ofString("text")
        .description("New text content. Markdown: **bold**, *italic*, - bullets, 1. numbered, "
            + "indent 2 spaces per level, \\n for line breaks. Empty string clears the shape's "
            + "content when neither --prepend nor --append is set.")
        .llmName("newText").required().variableLength(true).build();
    static final Parameter<Boolean> PREPEND = Parameter.ofBool("prepend")
        .description("Prepend the text to existing content instead of replacing (boolean flag).")
        .defaultValue("false").build();
    static final Parameter<Boolean> APPEND = Parameter.ofBool("append")
        .description("Append the text to existing content instead of replacing (boolean flag). "
            + "Mutually exclusive with --prepend.")
        .defaultValue("false").build();

    public static final CommandSchema SCHEMA = CommandSchema.builder()
        .description("Edit text content of a shape. Default mode is REPLACE: the passed string "
            + "(including empty string) replaces the shape's content. --prepend / --append opt "
            + "into additive modes.")
        .llmEnabled(true)
        .llmDescription("Edit text content of a shape. Default: replace. Flags --prepend and "
            + "--append opt into additive modes; the absence of both means the passed string "
            + "replaces the shape's content (empty string clears).")
        .parameter(SLIDE)
        .parameter(SPID)
        .parameter(TEXT)
        .parameter(PREPEND)
        .parameter(APPEND)
        .example("content-edit 1 2 \"New text content\"")
        .example("content-edit 1 2 \"\"           # clear the shape's text")
        .example("content-edit --slide 1 --spid 2 --text \" more\" --append")
        .build();

    public static Command fromParameters(CommandParameters p, CommandContext ctx) {
        boolean prepend = p.get(PREPEND);
        boolean append = p.get(APPEND);
        if (prepend && append) {
            throw new IllegalArgumentException(
                "content-edit: --prepend and --append are mutually exclusive");
        }
        Mode mode = prepend ? Mode.PREPEND : append ? Mode.APPEND : Mode.REPLACE;
        return new ContentEditCommand(p.get(SLIDE), p.get(SPID), p.get(TEXT),
            mode, ctx.orchestrator(), ctx.displayAdapter());
    }

    /**
     * How the passed text interacts with the shape's existing content.
     * REPLACE (default): the passed string (including empty) replaces
     * the shape's content. Empty string clears. PREPEND: the passed
     * string is inserted before existing content. APPEND: the passed
     * string is appended after existing content. Prepend / append with
     * empty string are no-ops.
     */
    public enum Mode { REPLACE, PREPEND, APPEND }

    private final int slideNumber;
    private final int spid;
    private final String newText;
    private final Mode mode;
    private final PPTXOrchestrator orchestrator;
    private final Object displayAdapter;
    private boolean executed = false;
    private String originalText = null;
    private String appliedText = null;
    private static final Logger logger = Logger.getLogger(ContentEditCommand.class.getName());
    
    // Debug information captured during execution
    private SlideShape targetShape = null;
    private boolean isPlaceholder = false;
    private ShapeGeometry originalGeometry = null;
    
    /**
     * Create a ContentEditCommand.
     * 
     * @param slideNumber the slide number containing the shape
     * @param spid the SPID of the shape to edit
     * @param newText the new text content
     * @param orchestrator the PPTX orchestrator for execution
     */
    public ContentEditCommand(int slideNumber, int spid, String newText, PPTXOrchestrator orchestrator) {
        this(slideNumber, spid, newText, Mode.REPLACE, orchestrator, null);
    }

    public ContentEditCommand(int slideNumber, int spid, String newText, Mode mode,
            PPTXOrchestrator orchestrator, Object displayAdapter) {
        if (orchestrator == null) {
            throw new IllegalArgumentException("PPTXOrchestrator cannot be null");
        }
        if (newText == null) {
            throw new IllegalArgumentException("New text cannot be null");
        }
        if (mode == null) {
            throw new IllegalArgumentException("Mode cannot be null");
        }

        this.slideNumber = slideNumber;
        this.spid = spid;
        this.newText = newText;
        this.mode = mode;
        this.orchestrator = orchestrator;
        this.displayAdapter = displayAdapter;
    }
    
    /**
     * Execute the content edit command with enhanced debugging and validation.
     * 
     * @throws CommandExecutionException if the operation fails
     */
    @Override
    public void execute() {
        if (executed) {
            throw new CommandExecutionException(getDescription(), "execute", "Command has already been executed");
        }
        
        try {
            // Phase 3 Enhancement: Pre-execution validation and debugging
            validateAndCaptureShapeContext();

            // Capture the flat text for undo + a length-delta hint in the
            // feedback message. Undo is approximate for rich shapes (see
            // note in the undo path); the length delta comes from the
            // TextBody paragraph count + total run char count after edit
            // when we go through the TextBody path.
            originalText = orchestrator.getShapeText(slideNumber, spid);

            logContentEditOperation();

            ExecutionResult<Void> result = switch (mode) {
                case REPLACE -> {
                    // Flat-string replace goes through the markdown-parsing
                    // path in updateShapeText, which is correct for the
                    // "user passes markdown body, it becomes the shape's
                    // new content" intent.
                    appliedText = newText;
                    yield orchestrator.editShapeText(slideNumber, spid, appliedText);
                }
                case PREPEND, APPEND -> {
                    // Prepend/append MUST preserve existing paragraph
                    // structure (bullets, numbered lists, multi-paragraph
                    // layouts, run-level formatting). Flat-string
                    // concatenation would downgrade rich shapes to plain
                    // text because getShapeText returns only the first
                    // run's text. Instead:
                    //   1) Extract the full existing TextBody via
                    //      TextBodyExtractor.extractFromShape (preserves
                    //      every paragraph + bodyProperties + runs).
                    //   2) Parse newText as its own TextBody via
                    //      TextBody.fromPlainText (markdown-aware).
                    //   3) Concat paragraph lists -- existing body wins
                    //      on bodyProperties so shape styling survives.
                    //   4) Write back via setTextBody (typed path).
                    // Empty newText is an explicit no-op that still
                    // emits feedback below.
                    if (newText.isEmpty()) {
                        appliedText = originalText != null ? originalText : "";
                        yield ExecutionResult.success("EditShapeText", null);
                    }
                    TextBody existing = targetShape != null
                        ? com.excudo.core.metrics.TextBodyExtractor.extractFromShape(targetShape.getXmlElement())
                        : null;
                    // Derive isPlaceholder from the shape itself, not
                    // from the extracted TextBody's flag -- the extractor
                    // doesn't propagate that flag, and deriving from the
                    // XML is authoritative anyway (presence of <p:ph>).
                    boolean shapeIsPlaceholder = isPlaceholder
                        || (targetShape != null
                            && targetShape.getType() == SlideShape.ShapeType.PLACEHOLDER);
                    TextBody incoming = TextBody.fromPlainText(newText, shapeIsPlaceholder);
                    TextBody combined = (mode == Mode.PREPEND)
                        ? TextBody.concat(incoming, existing)
                        : TextBody.concat(existing, incoming);
                    // Compute appliedText for feedback length-delta.
                    StringBuilder flat = new StringBuilder();
                    for (TextParagraph p : combined.getParagraphs()) {
                        for (TextRun r : p.getRuns()) flat.append(r.getText());
                        flat.append('\n');
                    }
                    if (flat.length() > 0) flat.setLength(flat.length() - 1);
                    appliedText = flat.toString();
                    yield orchestrator.setTextBody(slideNumber, spid, combined);
                }
            };

            if (result.isSuccess()) {
                executed = true;
                logger.fine(String.format("Successfully edited content for SPID %d on slide %d (shape type: %s, mode=%s)",
                    spid, slideNumber, targetShape != null ? targetShape.getType() : "unknown", mode));
                emitSuccessFeedback();
            } else {
                throw new CommandExecutionException(
                    getDescription(),
                    "execute",
                    "Failed to edit content: " + result.getMessage()
                );
            }

        } catch (CommandExecutionException e) {
            throw e;
        } catch (Exception e) {
            throw new CommandExecutionException(
                getDescription(),
                "execute",
                "Failed to edit content: " + e.getMessage(),
                e
            );
        }
    }

    /**
     * Emit a success message through the display adapter if one was
     * provided and implements {@link CommandDisplay}. Describes what
     * actually happened (replaced / cleared / prepended / appended +
     * length delta) so silent success no longer looks like silent
     * failure. The adapter is typed as Object at this layer because
     * ShapeCommandFactory.createFromParameters still uses that
     * generic signature; we cast via instanceof to stay properly typed.
     */
    private void emitSuccessFeedback() {
        if (!(displayAdapter instanceof CommandDisplay display)) return;
        String action = switch (mode) {
            case REPLACE -> newText.isEmpty() ? "Cleared" : "Replaced";
            case PREPEND -> newText.isEmpty() ? "No-op prepend (empty text) on" : "Prepended to";
            case APPEND  -> newText.isEmpty() ? "No-op append (empty text) on" : "Appended to";
        };
        int before = originalText == null ? 0 : originalText.length();
        int after = appliedText == null ? 0 : appliedText.length();
        display.displaySuccess(String.format(
            "%s text on slide %d SPID %d (%d -> %d chars)",
            action, slideNumber, spid, before, after));
    }
    
    /**
     * Undo the content edit by restoring original text.
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
            // Restore original text
            ExecutionResult<Void> result = orchestrator.editShapeText(slideNumber, spid, originalText);
            
            if (result.isSuccess()) {
                executed = false;
            } else {
                throw new CommandExecutionException(
                    getDescription(), 
                    "undo", 
                    "Failed to undo content edit: " + result.getMessage()
                );
            }
            
        } catch (Exception e) {
            throw new CommandExecutionException(
                getDescription(), 
                "undo", 
                "Failed to undo content edit: " + e.getMessage(),
                e
            );
        }
    }
    
    /**
     * Check if this command can be undone.
     * Content editing can be undone by restoring the original text.
     * 
     * @return true if the command can be undone
     */
    @Override
    public boolean canUndo() {
        // Content editing will be undoable once implemented
        return executed && originalText != null;
    }
    
    /**
     * Get the description of this command.
     * 
     * @return description of the content edit operation
     */
    @Override
    public String getDescription() {
        return String.format("Edit content of SPID %d on slide %d to: '%s'", spid, slideNumber, newText);
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
     * Get the slide number.
     * 
     * @return the slide number
     */
    public int getSlideNumber() {
        return slideNumber;
    }
    
    /**
     * Get the SPID.
     * 
     * @return the shape SPID
     */
    public int getSpid() {
        return spid;
    }
    
    /**
     * Get the new text content.
     * 
     * @return the new text
     */
    public String getNewText() {
        return newText;
    }
    
    /**
     * Get the original text (available after execution).
     * 
     * @return the original text, or null if not executed
     */
    public String getOriginalText() {
        return originalText;
    }
    
    // ========== PHASE 3 ENHANCEMENTS ==========
    
    /**
     * Validate shape exists and capture context for debugging.
     * This helps identify geometry inheritance issues with placeholders.
     */
    private void validateAndCaptureShapeContext() {
        try {
            // Use orchestrator's interface method to get parsed slide data
            com.excudo.core.results.ExecutionResult<ParsedSlideData> slideResult =
                orchestrator.getSlideData(slideNumber);

            if (!slideResult.isSuccess() || slideResult.getData().isEmpty()) {
                throw new CommandExecutionException(getDescription(), "validate",
                    String.format("Slide %d not found", slideNumber));
            }

            ParsedSlideData slideData = slideResult.getData().get();
            if (slideData != null) {
                targetShape = slideData.getShapeRegistry().getShape(spid);
                
                if (targetShape == null) {
                    throw new CommandExecutionException(getDescription(), "validate", 
                        String.format("Shape with SPID %d not found on slide %d. Available SPIDs: %s",
                            spid, slideNumber, slideData.getShapeRegistry().getAllSpids()));
                }
                
                // Capture geometry for validation
                originalGeometry = targetShape.getGeometry();
                
                // Detect if this is a placeholder based on shape type and name
                isPlaceholder = targetShape.getType() == SlideShape.ShapeType.PLACEHOLDER ||
                               targetShape.getName().toLowerCase().contains("placeholder") ||
                               targetShape.getName().toLowerCase().contains("content");
                
                // Validate geometry exists (critical for placeholder inheritance)
                if (originalGeometry == null) {
                    logger.warning(String.format("Shape SPID %d on slide %d has null geometry - this may indicate layout inheritance issues",
                        spid, slideNumber));
                } else if (originalGeometry.getWidth() <= 0 || originalGeometry.getHeight() <= 0) {
                    logger.warning(String.format("Shape SPID %d on slide %d has invalid geometry (%s) - this may cause rendering issues",
                        spid, slideNumber, originalGeometry));
                }
                
                logger.fine(String.format("Shape validation completed: SPID %d, type %s, isPlaceholder %s, geometry %s",
                    spid, targetShape.getType(), isPlaceholder, originalGeometry));
            }
        } catch (CommandExecutionException e) {
            throw e;
        } catch (Exception e) {
            // Don't fail the operation for validation issues, but log them
            logger.warning("Failed to validate shape context: " + e.getMessage());
        }
    }
    
    /**
     * Log detailed information about the content edit operation for debugging.
     */
    private void logContentEditOperation() {
        StringBuilder logMessage = new StringBuilder();
        logMessage.append(String.format("Content edit operation on slide %d, SPID %d:", slideNumber, spid));
        
        if (targetShape != null) {
            logMessage.append(String.format("\n  Shape: %s (type: %s)", targetShape.getName(), targetShape.getType()));
            logMessage.append(String.format("\n  Is placeholder: %s", isPlaceholder));
            logMessage.append(String.format("\n  Has text: %s", targetShape.hasText()));
            
            if (originalGeometry != null) {
                logMessage.append(String.format("\n  Geometry: Position: (%d, %d), Size: %d x %d", 
                    originalGeometry.getX(), originalGeometry.getY(),
                    originalGeometry.getWidth(), originalGeometry.getHeight()));
            } else {
                logMessage.append("\n  Geometry: NULL (potential layout inheritance issue)");
            }
            
            if (originalText != null) {
                logMessage.append(String.format("\n  Original text length: %d characters", originalText.length()));
            }
            
            logMessage.append(String.format("\n  New text length: %d characters", newText.length()));
        } else {
            logMessage.append("\n  WARNING: Could not retrieve shape information");
        }
        
        logger.fine(logMessage.toString());
    }
    
    /**
     * Get debug information about the target shape (available after execution).
     * 
     * @return debug information string
     */
    public String getShapeDebugInfo() {
        if (targetShape == null) {
            return "No shape debug information available";
        }
        
        return String.format("Shape: %s, Type: %s, IsPlaceholder: %s, Geometry: %s",
            targetShape.getName(), targetShape.getType(), isPlaceholder, 
            originalGeometry != null ? originalGeometry.toString() : "null");
    }
    
    /**
     * Check if the target shape is a placeholder (available after execution).
     * 
     * @return true if the shape appears to be a placeholder
     */
    public boolean isTargetPlaceholder() {
        return isPlaceholder;
    }
}