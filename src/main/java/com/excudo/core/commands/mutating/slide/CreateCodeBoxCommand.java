package com.excudo.core.commands.mutating.slide;

import com.excudo.core.commands.meta.UndoCommand;
import com.excudo.core.commands.Command;
import com.excudo.core.commands.CommandExecutionException;
import com.excudo.core.llm.CompoundShapeTools;
import com.excudo.core.metrics.FontData;
import com.excudo.core.metrics.FontResolver;
import com.excudo.core.metrics.TrueTypeFontParser;
import com.excudo.core.model.AutofitType;
import com.excudo.core.model.BodyProperties;
import com.excudo.core.model.ShapeFill;
import com.excudo.core.model.ShapeGeometry;
import com.excudo.core.model.ShapeStyle;
import com.excudo.core.model.SlideShape;
import com.excudo.core.model.TextBody;
import com.excudo.core.model.ThemeStyleRef;
import com.excudo.core.orchestration.PPTXOrchestrator;
import com.excudo.core.results.ExecutionResult;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * GoF Command for creating a syntax-highlighted code box on a slide.
 *
 * <p>A code box is a compound primitive: a left line-number panel + a
 * right code panel grouped together so the LLM can move/resize the box
 * as one unit. Previously this lived in {@link CompoundShapeTools} as
 * an instance method that bypassed the Command pattern entirely and
 * hand-rolled rollback via tracked SPID lists. Recasting it as a
 * Command:
 *
 * <ul>
 *   <li>Gets atomic rollback on partial-failure for free via the
 *       captured SPID list + {@link #undo()} -- no hand-rolled cleanup
 *       in the orchestration body.</li>
 *   <li>Gets user-initiated undo/redo for free -- {@code undo} after a
 *       successful create_code_box now removes the entire compound, the
 *       same way {@code undo} reverses any other Command.</li>
 *   <li>Lives in the same registry as every other Command, so the MCP
 *       {@code create_code_box} tool and the REPL share one dispatch
 *       path instead of CompoundShapeTools' separate one.</li>
 * </ul>
 *
 * <p>Pure rendering helpers (Prism4j tokenization, font measurement,
 * line/code text-body builders) stay where they were on
 * {@link CompoundShapeTools} as static utilities; this Command only
 * orchestrates the orchestrator calls and tracks state for undo.
 */
public class CreateCodeBoxCommand implements Command {

    private static final String FONT_MONO       = "Consolas";
    private static final int    FONT_SIZE_CODE  = 1200;          // 12pt in hundredths
    private static final int    INSET_EMU       = 45720;         // ~0.05" padding
    private static final long   FALLBACK_CHAR_W = 76200L;        // ~0.6" / char at 12pt
    private static final long   FALLBACK_LINE_H = 182880L;       // 12pt * 1.2 spacing
    private static final String COLOR_BG        = "3F3F3F";

    /**
     * Sentinel prefix for the group's {@code cNvPr/@name}. Lets the
     * {@link com.excudo.core.synthesis.ScriptSynthesizer} detect a
     * compound primitive on parse-back and emit a single
     * {@code CreateCodeBoxSpec} instead of decomposing into AddShape +
     * SetText × N. The language follows the prefix as part of the name
     * so re-emission can re-tokenize at run time without consulting a
     * side table.
     */
    public static final String GROUP_TAG_PREFIX = "excudo:code_box_v1:";

    /** Build the group-tag name for a given language. */
    public static String tagFor(String language) {
        return GROUP_TAG_PREFIX + (language != null ? language : "text");
    }

    /** Extract the language back out of a tag-shaped name, or null if
     *  the name doesn't carry the prefix. */
    public static String languageFromTag(String name) {
        if (name == null || !name.startsWith(GROUP_TAG_PREFIX)) return null;
        return name.substring(GROUP_TAG_PREFIX.length());
    }

    private final int slideNumber;
    private final String code;
    private final String language;
    private final long requestedX;
    private final long requestedY;
    private final Long requestedWidthOrNull;
    private final Long requestedHeightOrNull;
    /** Hex color (no leading #) for the line-number gutter. Null = default dim gray. */
    private final String lineNumberColor;
    private final PPTXOrchestrator orchestrator;

    // State captured during execute() for both result-shape reporting
    // and undo. Children are tracked in allocation order; undo()
    // removes them in reverse so the spTree stays well-formed at
    // every intermediate step.
    private boolean executed = false;
    private final List<Integer> allocatedSpids = new ArrayList<>();
    private Integer groupSpid = null;
    private int lineCount = 0;

    public CreateCodeBoxCommand(int slideNumber, String code, String language,
                                long x, long y,
                                Long widthOrNull, Long heightOrNull,
                                PPTXOrchestrator orchestrator) {
        this(slideNumber, code, language, x, y, widthOrNull, heightOrNull, null, orchestrator);
    }

    public CreateCodeBoxCommand(int slideNumber, String code, String language,
                                long x, long y,
                                Long widthOrNull, Long heightOrNull,
                                String lineNumberColor,
                                PPTXOrchestrator orchestrator) {
        if (orchestrator == null) throw new IllegalArgumentException("PPTXOrchestrator cannot be null");
        if (slideNumber <= 0) throw new IllegalArgumentException("Slide number must be positive");
        if (code == null || code.isEmpty()) throw new IllegalArgumentException("code cannot be empty");
        this.slideNumber = slideNumber;
        this.code = code;
        this.language = language != null ? language : "text";
        this.requestedX = x;
        this.requestedY = y;
        this.requestedWidthOrNull = widthOrNull;
        this.requestedHeightOrNull = heightOrNull;
        this.lineNumberColor = lineNumberColor;
        this.orchestrator = orchestrator;
    }

    @Override
    public void execute() {
        if (executed) {
            throw new CommandExecutionException(getDescription(), "execute",
                "Command has already been executed");
        }

        try {
            String[] lines = code.split("\n");
            this.lineCount = lines.length;

            FontData monoFont = resolveMonoFont();

            // Line-number gutter: width sized to the longest line label.
            String widestLineNum = String.valueOf(lines.length);
            long lineNumTextWidth = monoFont != null
                ? monoFont.measureStringWidthEmu(widestLineNum, FONT_SIZE_CODE)
                : widestLineNum.length() * FALLBACK_CHAR_W;
            long lineNumWidth = lineNumTextWidth + 2L * INSET_EMU;

            // Code panel: width sized to the longest source line.
            long longestCodeLine = 0;
            for (String line : lines) {
                long w = monoFont != null
                    ? monoFont.measureStringWidthEmu(line, FONT_SIZE_CODE)
                    : line.length() * FALLBACK_CHAR_W;
                longestCodeLine = Math.max(longestCodeLine, w);
            }
            long autoCodeWidth   = longestCodeLine + 2L * INSET_EMU;
            long autoTotalWidth  = lineNumWidth + autoCodeWidth;

            // Honor explicit width when caller supplies one. Fall back
            // to auto if the request is too narrow to even fit the
            // line-number gutter -- keeps children from rendering with
            // negative-width interiors.
            long requestedTotalWidth = requestedWidthOrNull != null
                ? requestedWidthOrNull : autoTotalWidth;
            long minTotalWidth = lineNumWidth + 2L * INSET_EMU;
            long totalWidth = requestedTotalWidth >= minTotalWidth
                ? requestedTotalWidth
                : autoTotalWidth;
            long codeWidth = totalWidth - lineNumWidth;

            long lineHeightEmu = monoFont != null
                ? monoFont.calculateLineHeightEmu(FONT_SIZE_CODE)
                : FALLBACK_LINE_H;
            long autoHeight = (long) lines.length * lineHeightEmu + 2L * INSET_EMU;
            long height = requestedHeightOrNull != null ? requestedHeightOrNull : autoHeight;

            ShapeStyle darkStyle = ShapeStyle.of(
                ShapeFill.solid(COLOR_BG), null, ThemeStyleRef.NONE);
            BodyProperties bodyProps = BodyProperties.builder()
                .wrap("square")
                .autofit(AutofitType.NONE)
                .leftInset(INSET_EMU)
                .topInset(INSET_EMU)
                .rightInset(INSET_EMU)
                .bottomInset(INSET_EMU)
                .build();

            // ---- line-number panel
            TextBody lineNumBody = CompoundShapeTools.buildLineNumberBody(
                lines.length, bodyProps, lineNumberColor);
            ShapeGeometry lineNumGeom = new ShapeGeometry(
                requestedX, requestedY, lineNumWidth, height);
            int lineNumSpid = addShapeOrFail(lineNumGeom, "LineNumbers", darkStyle);
            setTextBodyOrFail(lineNumSpid, lineNumBody, "line-number");

            // ---- code panel
            TextBody codeBody = CompoundShapeTools.buildCodeBody(
                lines, language, bodyProps);
            ShapeGeometry codeGeom = new ShapeGeometry(
                requestedX + lineNumWidth, requestedY, codeWidth, height);
            int codeSpid = addShapeOrFail(codeGeom, "Code", darkStyle);
            setTextBodyOrFail(codeSpid, codeBody, "code");

            // ---- group -- partial-success acceptable here, see comment below
            ExecutionResult<Integer> groupResult = orchestrator.groupShapes(
                slideNumber, List.of(lineNumSpid, codeSpid));
            if (groupResult != null && groupResult.getData().isPresent()) {
                this.groupSpid = groupResult.getData().get();
                // Tag the group so synthesize_slide_script can recognise
                // this compound primitive on parse-back and emit a single
                // CreateCodeBoxSpec instead of decomposing into AddShape +
                // SetText for each panel. Failure is non-fatal: an
                // un-tagged group still works as a code box, it just
                // won't round-trip through the synthesizer.
                try {
                    orchestrator.updateShapeName(slideNumber, groupSpid, tagFor(language));
                } catch (Exception e) {
                    java.util.logging.Logger.getLogger(CreateCodeBoxCommand.class.getName())
                        .warning("Failed to tag code box group " + groupSpid
                            + " (compound shape will not round-trip via synthesizer): "
                            + e.getMessage());
                }
            }
            // If grouping fails the panels are still useful as siblings;
            // groupSpid stays null and undo just removes the children.

            executed = true;
        } catch (RuntimeException e) {
            // Roll back any SPIDs we already allocated. Errors during
            // rollback are swallowed (best-effort) so the caller sees
            // the original failure, not a confusing rollback error.
            rollbackChildren();
            allocatedSpids.clear();
            throw e;
        }
    }

    private int addShapeOrFail(ShapeGeometry geom, String name, ShapeStyle style) {
        ExecutionResult<Integer> r = orchestrator.addShape(
            slideNumber, SlideShape.ShapeType.RECTANGLE, geom, "", name, style);
        if (r == null || r.getData().isEmpty()) {
            throw new CommandExecutionException(getDescription(), "execute",
                "Failed to create " + name + " panel"
                + (r != null ? ": " + r.getMessage() : ""));
        }
        int spid = r.getData().get();
        allocatedSpids.add(spid);
        return spid;
    }

    private void setTextBodyOrFail(int spid, TextBody body, String label) {
        ExecutionResult<Void> r = orchestrator.setTextBody(slideNumber, spid, body);
        if (r == null || !r.isSuccess()) {
            throw new CommandExecutionException(getDescription(), "execute",
                "Failed to set " + label + " text body"
                + (r != null ? ": " + r.getMessage() : ""));
        }
    }

    @Override
    public void undo() {
        if (!executed) {
            throw new CommandExecutionException(getDescription(), UndoCommand.NAME,
                "Command has not been executed");
        }
        // Remove the group first so the spTree stays well-formed --
        // ungrouping happens implicitly when the group is removed.
        if (groupSpid != null) {
            orchestrator.removeShape(slideNumber, groupSpid);
            groupSpid = null;
        }
        rollbackChildren();
        executed = false;
    }

    @Override
    public boolean canUndo() {
        return executed;
    }

    @Override
    public boolean isExecuted() {
        return executed;
    }

    @Override
    public String getDescription() {
        return "Create code box on slide " + slideNumber
            + " (" + language + ", " + lineCount + " lines)";
    }

    /** Public accessor for {@link com.excudo.core.llm.ToolDispatcher}'s
     *  {@code create_code_box} tool result string. Null when not yet
     *  executed or when grouping failed and the panels are siblings. */
    public Integer getGroupSpid() {
        return groupSpid;
    }

    /** SPIDs the command allocated, in creation order. Useful for the
     *  partial-success reporting path when grouping fails. */
    public List<Integer> getAllocatedSpids() {
        return List.copyOf(allocatedSpids);
    }

    public int getLineCount() {
        return lineCount;
    }

    public String getLanguage() {
        return language;
    }

    /** Best-effort rollback: remove every child SPID we allocated. */
    private void rollbackChildren() {
        for (int i = allocatedSpids.size() - 1; i >= 0; i--) {
            int spid = allocatedSpids.get(i);
            try {
                orchestrator.removeShape(slideNumber, spid);
            } catch (RuntimeException rollbackEx) {
                // Swallow -- the original failure (or the user's undo)
                // is what the caller should see, not a cleanup error.
            }
        }
        allocatedSpids.clear();
    }

    private static FontData resolveMonoFont() {
        try {
            Path fontPath = FontResolver.resolve(FONT_MONO, false, false);
            if (fontPath != null) {
                return TrueTypeFontParser.parse(fontPath);
            }
        } catch (Exception ignored) {
            // Fall back to character-count estimation
        }
        return null;
    }
}
