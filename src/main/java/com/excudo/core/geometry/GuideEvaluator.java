package com.excudo.core.geometry;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Evaluates DrawingML guide formulas (ECMA-376 Part 1 &sect;20.1.9.11)
 * over a context of built-in guides + adjust values + previously defined
 * guides, in document order, in double precision.
 *
 * <h2>Units</h2>
 * Lengths are whatever unit the caller passes as {@code w}/{@code h}
 * (the preset formulas are all proportional, so pixels and EMUs give
 * identical shapes after scaling). <b>Angles are in 60000ths of a
 * degree</b> everywhere: as the second argument to {@code sin}/{@code
 * cos}/{@code tan}, and as the result of {@code at2}. A full turn is
 * 21600000.
 *
 * <h2>The 17 formula ops</h2>
 * <pre>
 *   val a           a
 *   &#42;&#47; a b c        a * b / c
 *   +- a b c        a + b - c
 *   +/ a b c        (a + b) / c
 *   ?: a b c        a &gt; 0 ? b : c
 *   abs a           |a|
 *   min a b         min
 *   max a b         max
 *   pin a b c       clamp(b, a, c)
 *   mod a b c       sqrt(a&sup2; + b&sup2; + c&sup2;)
 *   sqrt a          sqrt(a)
 *   sin a b         a * sin(b)          (b in 60000ths-deg)
 *   cos a b         a * cos(b)
 *   tan a b         a * tan(b)
 *   at2 a b         atan2(b, a)         (result in 60000ths-deg)
 *   cat2 a b c      a * cos(atan2(c, b))
 *   sat2 a b c      a * sin(atan2(c, b))
 * </pre>
 *
 * Arguments are integer literals or guide names. Guide names may start
 * with a digit ({@code 3cd4}, {@code 2a1} in the spec definitions), so a
 * token is a literal only when it is entirely numeric.
 *
 * <p>Unknown guide references and unknown ops throw: a formula that
 * cannot be evaluated means the definition (or this evaluator) is wrong,
 * and silently substituting 0 would hide it. Division by zero yields 0
 * (PowerPoint renders degenerate zero-extent shapes rather than failing).
 */
public final class GuideEvaluator {

    private GuideEvaluator() {}

    /** 60000ths-of-a-degree per radian. */
    private static final double ANGLE_UNITS_PER_RADIAN = 60000.0 * 180.0 / Math.PI;

    /**
     * Evaluate a definition's adjust values + guides for a shape box of
     * {@code w} x {@code h}. {@code avOverrides} carries the shape's own
     * {@code avLst} values (name &rarr; raw integer), replacing the
     * definition defaults name-by-name. Returns the full guide context
     * (built-ins included) for path-coordinate resolution.
     */
    public static Map<String, Double> evaluate(GeometryDefinition def,
                                               Map<String, Integer> avOverrides,
                                               double w, double h) {
        Map<String, Double> env = builtins(w, h);

        // avLst: definition defaults, overridden name-by-name by the
        // shape's own adjust values.
        for (GeometryDefinition.Guide adj : def.getAdjustDefaults()) {
            Integer override = avOverrides == null ? null : avOverrides.get(adj.name());
            env.put(adj.name(), override != null
                ? override.doubleValue()
                : evaluate(adj.fmla(), env));
        }
        // A shape may carry adjust values the definition's avLst does not
        // name (authoring-tool quirks); they become plain guides so any
        // formula referencing them still resolves.
        if (avOverrides != null) {
            for (Map.Entry<String, Integer> e : avOverrides.entrySet()) {
                env.putIfAbsent(e.getKey(), e.getValue().doubleValue());
            }
        }

        // gdLst in document order; each guide may reference all before it.
        for (GeometryDefinition.Guide g : def.getGuides()) {
            env.put(g.name(), evaluate(g.fmla(), env));
        }
        return env;
    }

    /**
     * Evaluate one formula string ({@code "op a b c"}) against a guide
     * context. Public for direct use in tests.
     */
    public static double evaluate(String fmla, Map<String, Double> env) {
        String[] tok = fmla.trim().split("\\s+");
        String op = tok[0];
        return switch (op) {
            case "val" -> arg(tok, 1, env);
            case "*/" -> divide(arg(tok, 1, env) * arg(tok, 2, env), arg(tok, 3, env));
            case "+-" -> arg(tok, 1, env) + arg(tok, 2, env) - arg(tok, 3, env);
            case "+/" -> divide(arg(tok, 1, env) + arg(tok, 2, env), arg(tok, 3, env));
            case "?:" -> arg(tok, 1, env) > 0 ? arg(tok, 2, env) : arg(tok, 3, env);
            case "abs" -> Math.abs(arg(tok, 1, env));
            case "min" -> Math.min(arg(tok, 1, env), arg(tok, 2, env));
            case "max" -> Math.max(arg(tok, 1, env), arg(tok, 2, env));
            case "pin" -> {
                double lo = arg(tok, 1, env), v = arg(tok, 2, env), hi = arg(tok, 3, env);
                yield v < lo ? lo : (v > hi ? hi : v);
            }
            case "mod" -> {
                double a = arg(tok, 1, env), b = arg(tok, 2, env), c = arg(tok, 3, env);
                yield Math.sqrt(a * a + b * b + c * c);
            }
            case "sqrt" -> Math.sqrt(Math.max(0, arg(tok, 1, env)));
            case "sin" -> arg(tok, 1, env) * Math.sin(toRadians(arg(tok, 2, env)));
            case "cos" -> arg(tok, 1, env) * Math.cos(toRadians(arg(tok, 2, env)));
            case "tan" -> arg(tok, 1, env) * Math.tan(toRadians(arg(tok, 2, env)));
            case "at2" -> Math.atan2(arg(tok, 2, env), arg(tok, 1, env)) * ANGLE_UNITS_PER_RADIAN;
            case "cat2" -> arg(tok, 1, env)
                * Math.cos(Math.atan2(arg(tok, 3, env), arg(tok, 2, env)));
            case "sat2" -> arg(tok, 1, env)
                * Math.sin(Math.atan2(arg(tok, 3, env), arg(tok, 2, env)));
            default -> throw new IllegalArgumentException(
                "Unknown guide formula op '" + op + "' in \"" + fmla + "\"");
        };
    }

    /**
     * Resolve one guide-name-or-literal token against a context.
     * Path coordinates and formula arguments share this rule.
     */
    public static double resolve(String token, Map<String, Double> env) {
        if (isNumeric(token)) {
            return Double.parseDouble(token);
        }
        Double v = env.get(token);
        if (v == null) {
            throw new IllegalArgumentException("Unknown guide reference '" + token + "'");
        }
        return v;
    }

    /**
     * The predefined guides of &sect;20.1.9.11 for a {@code w} x {@code h}
     * box. {@code ss} = short side, {@code ls} = long side; {@code cd*}
     * are circle fractions of the 21600000 full turn.
     */
    public static Map<String, Double> builtins(double w, double h) {
        Map<String, Double> env = new HashMap<>(64);
        double ss = Math.min(w, h);
        env.put("w", w);
        env.put("h", h);
        env.put("ss", ss);
        env.put("ls", Math.max(w, h));
        env.put("t", 0.0);
        env.put("l", 0.0);
        env.put("b", h);
        env.put("r", w);
        env.put("hc", w / 2);
        env.put("vc", h / 2);
        for (int d : new int[]{2, 3, 4, 5, 6, 8, 10, 12, 32}) {
            env.put("wd" + d, w / d);
            env.put("hd" + d, h / d);
        }
        for (int d : new int[]{2, 4, 6, 8, 16, 32}) {
            env.put("ssd" + d, ss / d);
        }
        env.put("cd2", 10800000.0);
        env.put("cd4", 5400000.0);
        env.put("cd8", 2700000.0);
        env.put("3cd4", 16200000.0);
        env.put("3cd8", 8100000.0);
        env.put("5cd8", 13500000.0);
        env.put("7cd8", 18900000.0);
        return env;
    }

    private static double arg(String[] tok, int i, Map<String, Double> env) {
        if (i >= tok.length) {
            throw new IllegalArgumentException(
                "Guide formula '" + String.join(" ", tok) + "' is missing argument " + i);
        }
        return resolve(tok[i], env);
    }

    private static double divide(double numerator, double denominator) {
        // PowerPoint renders zero-extent (degenerate) shapes instead of
        // failing; 0/0-style guides collapse to 0 there.
        return denominator == 0 ? 0 : numerator / denominator;
    }

    private static double toRadians(double angle60000ths) {
        return angle60000ths / ANGLE_UNITS_PER_RADIAN;
    }

    /** Entirely-numeric tokens are literals; anything else is a guide name. */
    private static boolean isNumeric(String token) {
        if (token.isEmpty()) return false;
        int start = (token.charAt(0) == '-' || token.charAt(0) == '+') ? 1 : 0;
        if (start == token.length()) return false;
        for (int i = start; i < token.length(); i++) {
            char c = token.charAt(i);
            if (c < '0' || c > '9') return false;
        }
        return true;
    }
}
