package com.excudo.core.commands;

import com.excudo.core.model.*;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.*;

/**
 * Parses inline JSON into TextBody for the set-text command.
 * Handles the specific schema: array of paragraph objects with runs.
 */
public final class TextBodyJsonParser {

    private TextBodyJsonParser() {}

    private static final Gson GSON = new Gson();
    private static final Type PARAGRAPH_LIST_TYPE =
        new TypeToken<List<Map<String, Object>>>(){}.getType();

    public static TextBody parse(String json) {
        if (json == null || json.trim().isEmpty()) {
            throw new IllegalArgumentException("JSON text body cannot be empty");
        }

        List<Map<String, Object>> paragraphs = GSON.fromJson(json.trim(), PARAGRAPH_LIST_TYPE);
        if (paragraphs == null) {
            throw new IllegalArgumentException("Expected JSON array at top level");
        }

        TextBody.Builder bodyBuilder = TextBody.builder();
        for (Map<String, Object> paraMap : paragraphs) {
            bodyBuilder.addParagraph(buildParagraph(paraMap));
        }

        return bodyBuilder.build();
    }

    @SuppressWarnings("unchecked")
    private static TextParagraph buildParagraph(Map<String, Object> map) {
        TextParagraph.Builder builder = TextParagraph.builder();

        // Paragraph properties
        if (map.containsKey("algn")) builder.alignment((String) map.get("algn"));
        if (map.containsKey("lvl")) builder.level(((Number) map.get("lvl")).intValue());
        if (map.containsKey("spcBef")) builder.spaceBefore(((Number) map.get("spcBef")).intValue());
        if (map.containsKey("spcAft")) builder.spaceAfter(((Number) map.get("spcAft")).intValue());
        if (map.containsKey("marL")) builder.marginLeft(((Number) map.get("marL")).intValue());
        if (map.containsKey("indent")) builder.indent(((Number) map.get("indent")).intValue());
        if (map.containsKey("lnSpc")) builder.lineSpacing(((Number) map.get("lnSpc")).intValue());

        // Bullet properties
        if (map.containsKey("bullet")) {
            String bulletVal = String.valueOf(map.get("bullet"));
            if ("none".equalsIgnoreCase(bulletVal)) {
                builder.bulletType(BulletType.NONE);
            } else if ("inherited".equalsIgnoreCase(bulletVal)) {
                builder.bulletType(BulletType.INHERITED);
            } else {
                // Character bullet
                builder.bulletType(BulletType.CHARACTER);
                builder.bulletChar(bulletVal);
                if (map.containsKey("bulletFont")) builder.bulletFont((String) map.get("bulletFont"));
            }
        }
        if (map.containsKey("autonumType")) {
            builder.autonumber((String) map.get("autonumType"));
        }

        // Runs
        if (map.containsKey("runs")) {
            List<Map<String, Object>> runs = (List<Map<String, Object>>) map.get("runs");
            for (Map<String, Object> runMap : runs) {
                builder.addRun(buildRun(runMap));
            }
        } else if (map.containsKey("text")) {
            // Shorthand: single run from "text" key
            builder.addRun(TextRun.builder((String) map.get("text")).build());
        }

        return builder.build();
    }

    private static TextRun buildRun(Map<String, Object> map) {
        String text = map.containsKey("text") ? String.valueOf(map.get("text")) : "";
        TextRun.Builder builder = TextRun.builder(text);

        if (map.containsKey("b") && isTrue(map.get("b"))) builder.bold(true);
        if (map.containsKey("i") && isTrue(map.get("i"))) builder.italic(true);
        if (map.containsKey("u")) builder.underline(String.valueOf(map.get("u")));
        if (map.containsKey("sz")) builder.fontSize(((Number) map.get("sz")).intValue());
        if (map.containsKey("color")) builder.hexColor(String.valueOf(map.get("color")));
        if (map.containsKey("schemeColor")) builder.schemeColor(String.valueOf(map.get("schemeColor")));
        if (map.containsKey("font")) builder.fontFamily(String.valueOf(map.get("font")));
        if (map.containsKey("highlight")) {
            String hlVal = String.valueOf(map.get("highlight"));
            builder.highlight(isSchemeColor(hlVal) ? TextColor.scheme(hlVal) : TextColor.hex(hlVal));
        }
        if (map.containsKey("lang")) builder.language(String.valueOf(map.get("lang")));

        return builder.build();
    }

    private static boolean isTrue(Object val) {
        if (val instanceof Boolean) return (Boolean) val;
        return "true".equalsIgnoreCase(String.valueOf(val));
    }

    private static boolean isSchemeColor(String val) {
        String lower = val.toLowerCase();
        return lower.startsWith("accent") || lower.startsWith("dk") || lower.startsWith("lt")
            || "hlink".equals(lower) || "folhlink".equals(lower);
    }
}
