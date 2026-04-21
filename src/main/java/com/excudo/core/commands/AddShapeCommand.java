package com.excudo.core.commands;

import com.excudo.core.orchestration.PPTXOrchestrator;
import com.excudo.core.model.SlideShape;
import com.excudo.core.model.ShapeGeometry;
import com.excudo.core.model.ShapeStyle;
import com.excudo.core.results.ExecutionResult;

/**
 * GoF Command for adding new shapes to slides.
 * 
 * This command leverages the ShapeFactory system to create shapes with proper
 * shape types, geometry, and text content with full undo capability.
 */
public class AddShapeCommand implements Command {
    
    private final int slideNumber;
    private final SlideShape.ShapeType shapeType;
    private final ShapeGeometry geometry;
    private final String text;
    private final String shapeName;
    private final ShapeStyle style;
    private final String alignment;
    private final PPTXOrchestrator orchestrator;
    private boolean executed = false;
    private Integer createdSpid = null;

    public AddShapeCommand(int slideNumber, SlideShape.ShapeType shapeType, ShapeGeometry geometry,
                          String text, String shapeName, PPTXOrchestrator orchestrator) {
        this(slideNumber, shapeType, geometry, text, shapeName, null, null, orchestrator);
    }

    public AddShapeCommand(int slideNumber, SlideShape.ShapeType shapeType, ShapeGeometry geometry,
                          String text, String shapeName, ShapeStyle style, PPTXOrchestrator orchestrator) {
        this(slideNumber, shapeType, geometry, text, shapeName, style, null, orchestrator);
    }

    public AddShapeCommand(int slideNumber, SlideShape.ShapeType shapeType, ShapeGeometry geometry,
                          String text, String shapeName, ShapeStyle style, String alignment,
                          PPTXOrchestrator orchestrator) {
        if (orchestrator == null) {
            throw new IllegalArgumentException("PPTXOrchestrator cannot be null");
        }
        if (shapeType == null) {
            throw new IllegalArgumentException("ShapeType cannot be null");
        }
        if (geometry == null) {
            throw new IllegalArgumentException("ShapeGeometry cannot be null");
        }

        this.slideNumber = slideNumber;
        this.shapeType = shapeType;
        this.geometry = geometry;
        this.text = text;
        this.shapeName = shapeName != null ? shapeName : "Shape_" + System.currentTimeMillis();
        this.style = style;
        this.alignment = alignment;
        this.orchestrator = orchestrator;
    }
    
    /**
     * Execute the add shape command.
     * 
     * @throws CommandExecutionException if the operation fails
     */
    @Override
    public void execute() {
        if (executed) {
            throw new CommandExecutionException(getDescription(), "execute", "Command has already been executed");
        }
        
        try {
            // Add shape using orchestrator - this will delegate to SlideXMLWriter which uses ShapeFactory
            ExecutionResult<Integer> result = orchestrator
                .addShape(slideNumber, shapeType, geometry, text, shapeName, style);
            
            if (result.isSuccess()) {
                createdSpid = result.getData().orElse(null);
                if (createdSpid == null) {
                    throw new CommandExecutionException(
                        getDescription(),
                        "execute",
                        "Shape creation succeeded but no SPID was returned"
                    );
                }
                executed = true;

                // If the caller specified explicit paragraph alignment,
                // override the shape's default (typically "ctr") by
                // extracting its TextBody, rewriting each paragraph's
                // alignment, and writing back via setTextBody. Done as a
                // post-step so we don't have to thread the alignment
                // parameter through the entire shape-factory call chain.
                // No-op when alignment is null (default behavior).
                if (alignment != null) {
                    applyAlignmentOverride();
                }
                // Slide-modified notification is fired centrally by
                // ShapeOrchestrationManager.performShapeXMLOperation so
                // every shape mutation (add/remove/edit/resize/group/etc)
                // consistently reaches the GUI listener chain. No
                // per-command fire needed here.
            } else {
                throw new CommandExecutionException(
                    getDescription(), 
                    "execute", 
                    "Failed to add shape: " + result.getMessage()
                );
            }
            
        } catch (CommandExecutionException e) {
            throw e;
        } catch (Exception e) {
            throw new CommandExecutionException(
                getDescription(), 
                "execute", 
                "Failed to add shape: " + e.getMessage(),
                e
            );
        }
    }
    
    /**
     * Undo the add shape command by removing the created shape.
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
            // Remove the shape using orchestrator
            ExecutionResult<Void> result = orchestrator
                .removeShape(slideNumber, createdSpid);
            
            if (result.isSuccess()) {
                executed = false;
                createdSpid = null;
            } else {
                throw new CommandExecutionException(
                    getDescription(), 
                    "undo", 
                    "Failed to undo add shape: " + result.getMessage()
                );
            }
            
        } catch (Exception e) {
            throw new CommandExecutionException(
                getDescription(), 
                "undo", 
                "Failed to undo add shape: " + e.getMessage(),
                e
            );
        }
    }
    
    /**
     * Check if this command can be undone.
     * Shape addition can be undone by removing the created shape.
     * 
     * @return true if the command can be undone
     */
    @Override
    public boolean canUndo() {
        return executed && createdSpid != null;
    }
    
    /**
     * Get the description of this command.
     * 
     * @return description of the add shape operation
     */
    @Override
    public String getDescription() {
        return String.format("Add %s shape '%s' to slide %d at (%d,%d) size %dx%d", 
                           shapeType.name(), shapeName, slideNumber, 
                           geometry.getX(), geometry.getY(), 
                           geometry.getWidth(), geometry.getHeight());
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
     * Get the shape type.
     * 
     * @return the shape type
     */
    public SlideShape.ShapeType getShapeType() {
        return shapeType;
    }
    
    /**
     * Get the shape geometry.
     * 
     * @return the shape geometry
     */
    public ShapeGeometry getGeometry() {
        return geometry;
    }
    
    /**
     * Get the text content.
     * 
     * @return the text content
     */
    public String getText() {
        return text;
    }
    
    /**
     * Get the shape name.
     * 
     * @return the shape name
     */
    public String getShapeName() {
        return shapeName;
    }
    
    /**
     * Get the created SPID (available after execution).
     * 
     * @return the created SPID, or null if not executed
     */
    public Integer getCreatedSpid() {
        return createdSpid;
    }

    /**
     * Read the shape's TextBody, rewrite each paragraph's alignment to
     * the requested value, and write the modified body back via the
     * orchestrator's setTextBody path. Done as a post-creation step so
     * the alignment parameter doesn't have to be threaded through the
     * full shape-factory call chain (5 layers).
     *
     * Both empty-text and content-bearing shapes get the override --
     * an empty shape's lone end-paragraph still has a pPr that we
     * rewrite so subsequent text typed into the shape inherits the
     * right alignment.
     *
     * Failures here are logged but don't fail the overall add-shape
     * operation; the shape exists and is usable, just with the default
     * alignment instead of the requested one.
     */
    private void applyAlignmentOverride() {
        try {
            var slideDataResult = orchestrator.getSlideData(slideNumber);
            if (!slideDataResult.isSuccess() || slideDataResult.getData().isEmpty()) return;
            SlideShape shape = slideDataResult.getData().get()
                .getShapeRegistry().getShape(createdSpid);
            if (shape == null || shape.getXmlElement() == null) return;

            com.excudo.core.model.TextBody existing =
                com.excudo.core.metrics.TextBodyExtractor.extractFromShape(shape.getXmlElement());
            if (existing == null) return;

            // Rebuild each paragraph with the requested alignment.
            com.excudo.core.model.TextBody.Builder rebuilt =
                com.excudo.core.model.TextBody.builder()
                    .bodyProperties(existing.getBodyProperties())
                    .placeholder(existing.isPlaceholder());
            for (com.excudo.core.model.TextParagraph p : existing.getParagraphs()) {
                com.excudo.core.model.TextParagraph.Builder pb =
                    com.excudo.core.model.TextParagraph.builder()
                        .level(p.getLevel())
                        .alignment(alignment);
                if (p.getMarginLeft() != null) pb.marginLeft(p.getMarginLeft());
                if (p.getIndent() != null) pb.indent(p.getIndent());
                if (p.getLineSpacing() != null) pb.lineSpacing(p.getLineSpacing());
                if (p.getSpaceBefore() != null) pb.spaceBefore(p.getSpaceBefore());
                if (p.getSpaceAfter() != null) pb.spaceAfter(p.getSpaceAfter());
                if (p.getBulletType() == com.excudo.core.model.BulletType.AUTONUMBER
                        && p.getAutonumType() != null) {
                    pb.autonumber(p.getAutonumType());
                } else if (p.getBulletType() == com.excudo.core.model.BulletType.CHARACTER) {
                    pb.characterBullet(p.getBulletChar(), p.getBulletFont(),
                        p.getBulletFontPanose(), p.getBulletFontPitchFamily(), p.getBulletFontCharset());
                }
                for (com.excudo.core.model.TextRun r : p.getRuns()) {
                    pb.addRun(r);
                }
                rebuilt.addParagraph(pb.build());
            }
            orchestrator.setTextBody(slideNumber, createdSpid, rebuilt.build());
        } catch (Exception e) {
            // Non-fatal: shape was created successfully, only the
            // alignment override failed. Log so the user can investigate.
            java.util.logging.Logger.getLogger(AddShapeCommand.class.getName())
                .warning("Failed to apply alignment override on SPID " + createdSpid
                    + " (shape was created successfully): " + e.getMessage());
        }
    }
}