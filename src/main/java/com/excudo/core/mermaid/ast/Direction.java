package com.excudo.core.mermaid.ast;

/**
 * Flow direction for diagrams.
 */
public enum Direction {
    TB("TB"),  // top to bottom
    BT("BT"),  // bottom to top
    LR("LR"),  // left to right
    RL("RL");  // right to left

    private final String code;

    Direction(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static Direction fromCode(String code) {
        for (Direction d : values()) {
            if (d.code.equalsIgnoreCase(code)) return d;
        }
        // Mermaid aliases
        return switch (code.toUpperCase()) {
            case "TD" -> TB;
            default -> throw new IllegalArgumentException("Unknown direction: " + code);
        };
    }
}
