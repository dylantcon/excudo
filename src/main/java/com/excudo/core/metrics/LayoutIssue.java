package com.excudo.core.metrics;

/**
 * A layout issue detected during slide validation.
 */
public final class LayoutIssue {

    public enum Type {
        TEXT_OVERFLOW,
        SHAPE_OVERLAP,
        OFF_SLIDE,
        TEXT_TOO_SMALL
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
