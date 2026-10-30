package com.excudo.core.metrics;

/**
 * A layout issue detected during slide validation.
 */
public final class LayoutIssue {

    public enum Type {
        TEXT_OVERFLOW,
        SHAPE_OVERLAP,
        OFF_SLIDE,
        TEXT_TOO_SMALL,
        /** Text nearly invisible against what's behind it (under 2:1): fix it. */
        LOW_CONTRAST,
        /** Below WCAG AA but readable (2:1 up to 4.5:1, or 3:1 for large text), or
         *  not checkable (picture behind the text). Often fine for big bold labels. */
        CONTRAST_ADVISORY
    }

    private final Type type;
    private final int[] spids;
    private final String description;
    private final String suggestion;

    public LayoutIssue(Type type, int[] spids, String description, String suggestion) {
        this.type = type;
        this.spids = spids;
        this.description = description;
        this.suggestion = suggestion;
    }

    public Type getType() { return type; }
    public int[] getSpids() { return spids; }
    public String getDescription() { return description; }
    public String getSuggestion() { return suggestion; }

    @Override
    public String toString() {
        return type + " [SPID " + formatSpids() + "]: " + description;
    }

    private String formatSpids() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < spids.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(spids[i]);
        }
        return sb.toString();
    }
}
