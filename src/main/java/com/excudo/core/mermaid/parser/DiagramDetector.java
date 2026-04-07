package com.excudo.core.mermaid.parser;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Detects diagram type from the first line of mermaid source text.
 */
public class DiagramDetector {

    private static final Pattern FLOWCHART_PATTERN =
        Pattern.compile("^\\s*(?:graph|flowchart)\\s+(?:TB|TD|BT|LR|RL)", Pattern.CASE_INSENSITIVE);
    private static final Pattern SEQUENCE_PATTERN =
        Pattern.compile("^\\s*sequenceDiagram", Pattern.CASE_INSENSITIVE);
    private static final Pattern CLASS_PATTERN =
        Pattern.compile("^\\s*classDiagram", Pattern.CASE_INSENSITIVE);
    private static final Pattern STATE_PATTERN =
        Pattern.compile("^\\s*stateDiagram", Pattern.CASE_INSENSITIVE);
    private static final Pattern MINDMAP_PATTERN =
        Pattern.compile("^\\s*mindmap", Pattern.CASE_INSENSITIVE);

    public static DiagramType detect(String source) {
        String firstLine = source.strip().split("\\n")[0].strip();
        if (FLOWCHART_PATTERN.matcher(firstLine).find()) return DiagramType.FLOWCHART;
        if (SEQUENCE_PATTERN.matcher(firstLine).find()) return DiagramType.SEQUENCE;
        if (CLASS_PATTERN.matcher(firstLine).find()) return DiagramType.CLASS_DIAGRAM;
        if (STATE_PATTERN.matcher(firstLine).find()) return DiagramType.STATE;
        if (MINDMAP_PATTERN.matcher(firstLine).find()) return DiagramType.MINDMAP;
        throw new IllegalArgumentException("Cannot detect diagram type from: " + firstLine);
    }
}
