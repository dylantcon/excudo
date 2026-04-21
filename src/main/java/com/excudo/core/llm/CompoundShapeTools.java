package com.excudo.core.llm;

import com.excudo.core.llm.prism.ExcudoGrammarLocator;
import com.excudo.core.metrics.FontData;
import com.excudo.core.metrics.FontResolver;
import com.excudo.core.metrics.TrueTypeFontParser;
import com.excudo.core.model.*;
import com.excudo.core.orchestration.PPTXOrchestrator;
import com.excudo.core.results.ExecutionResult;
import com.excudo.core.utils.Logger;
import com.excudo.core.utils.ComponentLogger;
import io.noties.prism4j.Prism4j;

import java.nio.file.Path;

import java.util.*;
import com.excudo.core.utils.JsonHelper;
import com.google.gson.JsonObject;

/**
 * High-level compound shape tools for the agentic LLM pipeline.
 * Each method produces multi-shape compositions via direct orchestrator calls.
 */
public class CompoundShapeTools {

    private static final ComponentLogger logger = Logger.llm();

    // Syntax coloring palette (Zenburn-inspired dark theme)
    private static final String COLOR_KEYWORD    = "DFC47D"; // yellow
    private static final String COLOR_STRING     = "DCA3A3"; // salmon
    private static final String COLOR_COMMENT    = "7F9F7F"; // green-gray
    private static final String COLOR_DEFAULT    = "DCDCCC"; // warm white
    private static final String COLOR_LINE_NUM   = "858585"; // dim gray
    private static final String COLOR_BG         = "3F3F3F"; // dark background
    private static final String COLOR_NUMBER     = "8CD0D3"; // cyan
    private static final String COLOR_FUNCTION   = "93E0E3"; // bright cyan
    private static final String COLOR_OPERATOR   = "F0DFAF"; // gold
    private static final String COLOR_BOOLEAN    = "DFC47D"; // yellow (same as keyword)
    private static final String COLOR_BUILTIN    = "EFEF8F"; // bright yellow
    private static final String COLOR_ANNOTATION = "DFAF8F"; // orange
    private static final String COLOR_CLASSNAME  = "7CB8BB"; // teal
    private static final String COLOR_PUNCTUATION = "9F9F9F"; // gray
    private static final String COLOR_TAG        = "93E0E3"; // bright cyan (markup tags)
    private static final String COLOR_ATTR_NAME  = "DFAF8F"; // orange (same as annotation)
    private static final String COLOR_ATTR_VALUE = "DCA3A3"; // salmon (same as string)
    private static final String COLOR_NAMESPACE  = "7CB8BB"; // teal (same as class-name)

    private static final String FONT_MONO = "Consolas";
    private static final int FONT_SIZE_CODE = 1200; // 12pt in hundredths

    private final PPTXOrchestrator orchestrator;

    public CompoundShapeTools(PPTXOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    /**
     * Create a code box: line number panel (left) + code panel (right).
     * Width AND height are computed from font metrics + line count so the
     * box fits its content exactly. Callers can still pass explicit x/y/width/height
     * if they want manual placement or sizing.
     * Input JSON: {"slideNumber":N, "code":"...", "language":"python", "x":EMU, "y":EMU, "width":EMU, "height":EMU}
     *
     * Width auto-sizes from the longest code line + the line-number gutter at
     * the configured monospace font size. Pass an explicit `width` when the
     * code is short but you want the box to fill a layout column. The
     * line-number gutter still autosizes from the line count; the remainder
     * of the requested width goes to the code panel.
     */
    public String createCodeBox(String toolInput) {
        try {
            int slideNumber = extractInt(toolInput, "slideNumber");
            String code = extractString(toolInput, "code");
            String language = extractString(toolInput, "language");
            if (code == null || code.isEmpty()) {
                return "Error: 'code' is required";
            }
            if (language == null) language = "text";

            long x = extractLong(toolInput, "x", 838200);
            long y = extractLong(toolInput, "y", 1825625);

            String[] lines = code.split("\n");

            int inset = 45720; // ~0.05 inch padding per side

            // Measure exact widths using font metrics
            FontData monoFont = resolveMonoFont();
            String widestLineNum = String.valueOf(lines.length);
            long lineNumTextWidth = monoFont != null
                ? monoFont.measureStringWidthEmu(widestLineNum, FONT_SIZE_CODE)
                : widestLineNum.length() * 76200L; // fallback ~0.6" per char at 12pt
            long lineNumWidth = lineNumTextWidth + 2 * inset;

            long longestCodeLine = 0;
            for (String line : lines) {
                long lineWidth = monoFont != null
                    ? monoFont.measureStringWidthEmu(line, FONT_SIZE_CODE)
                    : line.length() * 76200L;
                longestCodeLine = Math.max(longestCodeLine, lineWidth);
            }
            long autoCodeWidth = longestCodeLine + 2 * inset;
            long autoTotalWidth = lineNumWidth + autoCodeWidth;

            // Honor explicit width when caller provides one (typical when
            // they want the code box to fill a layout column rather than
            // shrink-wrap content). The line-number gutter stays at its
            // measured size; the remainder goes to the code panel. If the
            // requested width is too small to fit even the line-number
            // column plus minimal padding, fall back to the auto width
            // so the box doesn't render with negative-width children.
            long requestedTotalWidth = extractLong(toolInput, "width", autoTotalWidth);
            long minTotalWidth = lineNumWidth + (long) inset * 2;
            long totalWidth = requestedTotalWidth >= minTotalWidth
                ? requestedTotalWidth
                : autoTotalWidth;
            long codeWidth = totalWidth - lineNumWidth;

            // Auto-compute height from line count. The previous fixed default
            // (3.2M EMU ~= 3.5 inches) made short snippets look broken by
            // reserving a tall empty box below the code. Callers can still
            // override 'height' explicitly if they want extra room.
            long lineHeightEmu = monoFont != null
                ? monoFont.calculateLineHeightEmu(FONT_SIZE_CODE)
                : 182880L; // fallback: 12pt * 1.2 spacing
            long autoHeight = (long) lines.length * lineHeightEmu + 2L * inset;
            long height = extractLong(toolInput, "height", autoHeight);

            // Dark fill style, no theme p:style
            ShapeStyle darkStyle = ShapeStyle.of(
                ShapeFill.solid(COLOR_BG), null, ThemeStyleRef.NONE);

            BodyProperties codeBodyProps = BodyProperties.builder()
                .wrap("square")
                .autofit(AutofitType.NONE)
                .leftInset(inset)
                .topInset(inset)
                .rightInset(inset)
                .bottomInset(inset)
                .build();

            // -- Line number panel --
            TextBody lineNumBody = buildLineNumberBody(lines.length, codeBodyProps);
            ShapeGeometry lineNumGeom = new ShapeGeometry(x, y, lineNumWidth, height);

            ExecutionResult<Integer> lineNumResult = orchestrator.addShape(
                slideNumber, SlideShape.ShapeType.RECTANGLE,
                lineNumGeom, "", "LineNumbers", darkStyle);

            if (lineNumResult == null || lineNumResult.getData().isEmpty()) {
                return "Error: Failed to create line number panel";
            }
            int lineNumSpid = lineNumResult.getData().get();
            orchestrator.setTextBody(slideNumber, lineNumSpid, lineNumBody);

            // -- Code panel --
            TextBody codeBody = buildCodeBody(lines, language, codeBodyProps);
            ShapeGeometry codeGeom = new ShapeGeometry(x + lineNumWidth, y, codeWidth, height);

            ExecutionResult<Integer> codeResult = orchestrator.addShape(
                slideNumber, SlideShape.ShapeType.RECTANGLE,
                codeGeom, "", "Code", darkStyle);

            if (codeResult == null || codeResult.getData().isEmpty()) {
                return "Error: Failed to create code panel";
            }
            int codeSpid = codeResult.getData().get();
            orchestrator.setTextBody(slideNumber, codeSpid, codeBody);

            // Group the two panels so the LLM can move/resize the code box as one unit
            ExecutionResult<Integer> groupResult = orchestrator.groupShapes(
                slideNumber, List.of(lineNumSpid, codeSpid));
            if (groupResult != null && groupResult.getData().isPresent()) {
                int groupSpid = groupResult.getData().get();
                return "Created code box on slide " + slideNumber
                    + " (group SPID " + groupSpid + ")."
                    + " Language: " + language + ", " + lines.length + " lines."
                    + " Use SPID " + groupSpid + " to move or resize the entire code box.";
            }

            return "Created code box on slide " + slideNumber
                + ": line numbers (SPID " + lineNumSpid + ") + code (SPID " + codeSpid + ")."
                + " Language: " + language + ", " + lines.length + " lines.";

        } catch (Exception e) {
            return "Error creating code box: " + e.getMessage();
        }
    }

    // ------------------------------------------------------------------
    // Code body builders
    // ------------------------------------------------------------------

    private TextBody buildLineNumberBody(int lineCount, BodyProperties bodyProps) {
        TextBody.Builder builder = TextBody.builder().bodyProperties(bodyProps);
        for (int i = 1; i <= lineCount; i++) {
            builder.addParagraph(TextParagraph.builder()
                .alignment("r")
                .addRun(TextRun.builder(String.valueOf(i))
                    .fontSize(FONT_SIZE_CODE)
                    .fontFamily(FONT_MONO)
                    .hexColor(COLOR_LINE_NUM)
                    .build())
                .build());
        }
        return builder.build();
    }

    private TextBody buildCodeBody(String[] lines, String language, BodyProperties bodyProps) {
        TextBody.Builder builder = TextBody.builder().bodyProperties(bodyProps);
        List<List<Token>> tokenizedLines = CodeTokenizer.tokenize(
            String.join("\n", lines), language);
        for (List<Token> lineTokens : tokenizedLines) {
            TextParagraph.Builder paraBuilder = TextParagraph.builder();
            if (lineTokens.isEmpty()) {
                paraBuilder.addRun(TextRun.builder(" ")
                    .fontSize(FONT_SIZE_CODE)
                    .fontFamily(FONT_MONO)
                    .hexColor(COLOR_DEFAULT)
                    .build());
            } else {
                for (Token token : lineTokens) {
                    paraBuilder.addRun(TextRun.builder(token.text())
                        .fontSize(FONT_SIZE_CODE)
                        .fontFamily(FONT_MONO)
                        .hexColor(tokenColor(token.type()))
                        .build());
                }
            }
            builder.addParagraph(paraBuilder.build());
        }
        return builder.build();
    }

    static String tokenColor(String type) {
        if (type == null) return COLOR_DEFAULT;
        return switch (type) {
            case "keyword"            -> COLOR_KEYWORD;
            case "string",
                 "triple-quoted-string" -> COLOR_STRING;
            case "comment"            -> COLOR_COMMENT;
            case "number"             -> COLOR_NUMBER;
            case "function"           -> COLOR_FUNCTION;
            case "operator"           -> COLOR_OPERATOR;
            case "boolean"            -> COLOR_BOOLEAN;
            case "builtin"            -> COLOR_BUILTIN;
            case "annotation"         -> COLOR_ANNOTATION;
            case "class-name",
                 "generics"           -> COLOR_CLASSNAME;
            case "punctuation"        -> COLOR_PUNCTUATION;
            case "tag"                -> COLOR_TAG;
            case "attr-name"          -> COLOR_ATTR_NAME;
            case "attr-value"         -> COLOR_ATTR_VALUE;
            case "namespace"          -> COLOR_NAMESPACE;
            default                   -> COLOR_DEFAULT;
        };
    }

    // ------------------------------------------------------------------
    // Syntax tokenizer (Prism4j-backed)
    // ------------------------------------------------------------------

    public record Token(String text, String type) {}

    public static final class CodeTokenizer {

        private static final Prism4j PRISM = new Prism4j(new ExcudoGrammarLocator());

        /**
         * Tokenize source code and return per-line token lists.
         * Falls back to plain DEFAULT tokens if the language has no grammar.
         */
        public static List<List<Token>> tokenize(String code, String language) {
            String lang = language != null ? language.toLowerCase() : "text";
            Prism4j.Grammar grammar = PRISM.grammar(lang);

            if (grammar == null) {
                // Unknown language -- split into lines, each as a single DEFAULT token
                return plainTokenize(code);
            }

            List<Prism4j.Node> nodes = PRISM.tokenize(code, grammar);
            List<List<Token>> lines = new ArrayList<>();
            lines.add(new ArrayList<>());
            flattenNodes(nodes, null, lines);
            return lines;
        }

        /**
         * Tokenize a single line (convenience for tests).
         */
        public static List<Token> tokenizeLine(String line, String language) {
            List<List<Token>> result = tokenize(line, language);
            return result.isEmpty() ? List.of() : result.get(0);
        }

        private static void flattenNodes(List<? extends Prism4j.Node> nodes,
                                          String parentType,
                                          List<List<Token>> lines) {
            for (Prism4j.Node node : nodes) {
                if (node instanceof Prism4j.Text text) {
                    appendText(text.literal(), parentType, lines);
                } else if (node instanceof Prism4j.Syntax syntax) {
                    // Use the syntax's own type, or alias if present
                    String type = syntax.alias() != null ? syntax.alias() : syntax.type();
                    flattenNodes(syntax.children(), type, lines);
                }
            }
        }

        private static void appendText(String text, String type, List<List<Token>> lines) {
            // Split on newlines to maintain per-line structure
            String[] parts = text.split("\n", -1);
            for (int i = 0; i < parts.length; i++) {
                if (i > 0) {
                    lines.add(new ArrayList<>()); // new line
                }
                String part = parts[i];
                if (!part.isEmpty()) {
                    lines.get(lines.size() - 1).add(new Token(part, type));
                }
            }
        }

        private static List<List<Token>> plainTokenize(String code) {
            List<List<Token>> lines = new ArrayList<>();
            for (String line : code.split("\n", -1)) {
                List<Token> tokens = new ArrayList<>();
                if (!line.isEmpty()) {
                    tokens.add(new Token(line, null));
                }
                lines.add(tokens);
            }
            return lines;
        }
    }

    // ------------------------------------------------------------------
    // Font metrics
    // ------------------------------------------------------------------

    private static FontData resolveMonoFont() {
        try {
            Path fontPath = FontResolver.resolve(FONT_MONO, false, false);
            if (fontPath != null) {
                return TrueTypeFontParser.parse(fontPath);
            }
        } catch (Exception ignored) {}
        return null;
    }

    // ------------------------------------------------------------------
    // JSON parsing helpers
    // ------------------------------------------------------------------

    int extractInt(String json, String key) {
        try {
            JsonObject obj = JsonHelper.parseObject(json);
            return JsonHelper.getInt(obj, key, 1);
        } catch (Exception e) { return 1; }
    }

    long extractLong(String json, String key, long defaultValue) {
        try {
            JsonObject obj = JsonHelper.parseObject(json);
            return JsonHelper.getLong(obj, key, defaultValue);
        } catch (Exception e) { return defaultValue; }
    }

    String extractString(String json, String key) {
        try {
            JsonObject obj = JsonHelper.parseObject(json);
            return JsonHelper.getString(obj, key);
        } catch (Exception e) { return null; }
    }
}
