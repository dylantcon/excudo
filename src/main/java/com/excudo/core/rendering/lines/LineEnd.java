package com.excudo.core.rendering.lines;

/**
 * One parsed {@code a:headEnd} / {@code a:tailEnd} (ECMA-376 20.1.8.33 /
 * 20.1.8.57): decoration type plus width/length size classes. Spec
 * defaults: type {@code none}, w/len {@code med}.
 */
public record LineEnd(Type type, Size width, Size length) {

    public enum Type {
        NONE, TRIANGLE, STEALTH, DIAMOND, OVAL, ARROW;

        /** Parse ST_LineEndType; empty/null means none, unknown throws. */
        public static Type fromXml(String value) {
            if (value == null || value.isEmpty()) return NONE;
            return switch (value) {
                case "none"     -> NONE;
                case "triangle" -> TRIANGLE;
                case "stealth"  -> STEALTH;
                case "diamond"  -> DIAMOND;
                case "oval"     -> OVAL;
                case "arrow"    -> ARROW;
                default -> throw new IllegalArgumentException(
                    "Unknown line end type '" + value + "'");
            };
        }
    }

    public enum Size {
        SM, MED, LG;

        /** Parse ST_LineEndWidth/ST_LineEndLength; empty/null means med. */
        public static Size fromXml(String value) {
            if (value == null || value.isEmpty()) return MED;
            return switch (value) {
                case "sm"  -> SM;
                case "med" -> MED;
                case "lg"  -> LG;
                default -> throw new IllegalArgumentException(
                    "Unknown line end size '" + value + "'");
            };
        }
    }

    public static final LineEnd NONE = new LineEnd(Type.NONE, Size.MED, Size.MED);

    public static LineEnd fromXml(String type, String w, String len) {
        Type t = Type.fromXml(type);
        if (t == Type.NONE) return NONE;
        return new LineEnd(t, Size.fromXml(w), Size.fromXml(len));
    }

    public boolean isNone() {
        return type == Type.NONE;
    }
}
