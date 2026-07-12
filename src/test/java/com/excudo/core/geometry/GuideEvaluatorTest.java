package com.excudo.core.geometry;

import org.junit.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * Pins the ECMA-376 &sect;20.1.9.11 guide-formula semantics: all 17 ops
 * with hand-computed expectations, 60000ths-of-a-degree trig, pin/mod
 * edges, document-order guide chaining, and avLst overrides.
 */
public class GuideEvaluatorTest {

    private static final double EPS = 1e-9;

    private static Map<String, Double> env(Object... kv) {
        Map<String, Double> m = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], ((Number) kv[i + 1]).doubleValue());
        }
        return m;
    }

    // ========== the 17 ops ==========

    @Test
    public void valReturnsArgument() {
        assertEquals(25000.0, GuideEvaluator.evaluate("val 25000", env()), EPS);
        assertEquals(-8333.0, GuideEvaluator.evaluate("val -8333", env()), EPS);
    }

    @Test
    public void multiplyDivide() {
        // */ a b c = a*b/c : 100 * 16667 / 100000 = 16.667
        assertEquals(16.667, GuideEvaluator.evaluate("*/ 100 16667 100000", env()), EPS);
        // division by zero collapses to 0 (degenerate shape, not a crash)
        assertEquals(0.0, GuideEvaluator.evaluate("*/ 5 7 0", env()), EPS);
    }

    @Test
    public void addSubtract() {
        // +- a b c = a+b-c
        assertEquals(120.0, GuideEvaluator.evaluate("+- 100 50 30", env()), EPS);
    }

    @Test
    public void addDivide() {
        // +/ a b c = (a+b)/c
        assertEquals(75.0, GuideEvaluator.evaluate("+/ 100 50 2", env()), EPS);
        assertEquals(0.0, GuideEvaluator.evaluate("+/ 100 50 0", env()), EPS);
    }

    @Test
    public void ifElse() {
        // ?: a b c = a>0 ? b : c  (strictly greater: 0 selects c)
        assertEquals(7.0, GuideEvaluator.evaluate("?: 1 7 9", env()), EPS);
        assertEquals(9.0, GuideEvaluator.evaluate("?: 0 7 9", env()), EPS);
        assertEquals(9.0, GuideEvaluator.evaluate("?: -3 7 9", env()), EPS);
    }

    @Test
    public void absMinMax() {
        assertEquals(42.0, GuideEvaluator.evaluate("abs -42", env()), EPS);
        assertEquals(3.0, GuideEvaluator.evaluate("min 3 8", env()), EPS);
        assertEquals(8.0, GuideEvaluator.evaluate("max 3 8", env()), EPS);
    }

    @Test
    public void pinClampsBothEnds() {
        // pin a b c = clamp(b, a, c)
        assertEquals(5.0, GuideEvaluator.evaluate("pin 0 5 10", env()), EPS);   // inside
        assertEquals(0.0, GuideEvaluator.evaluate("pin 0 -3 10", env()), EPS);  // below -> lo
        assertEquals(10.0, GuideEvaluator.evaluate("pin 0 15 10", env()), EPS); // above -> hi
        assertEquals(0.0, GuideEvaluator.evaluate("pin 0 0 10", env()), EPS);   // boundary
    }

    @Test
    public void modIsThreeTermEuclideanNorm() {
        // mod a b c = sqrt(a^2 + b^2 + c^2): 3-4-12 -> 13
        assertEquals(13.0, GuideEvaluator.evaluate("mod 3 4 12", env()), EPS);
        assertEquals(5.0, GuideEvaluator.evaluate("mod 3 4 0", env()), EPS);
        assertEquals(0.0, GuideEvaluator.evaluate("mod 0 0 0", env()), EPS);
        // sign-insensitive (squares)
        assertEquals(5.0, GuideEvaluator.evaluate("mod -3 -4 0", env()), EPS);
    }

    @Test
    public void sqrtOp() {
        assertEquals(12.0, GuideEvaluator.evaluate("sqrt 144", env()), EPS);
        assertEquals(0.0, GuideEvaluator.evaluate("sqrt 0", env()), EPS);
    }

    // ========== 60000ths-of-a-degree trig ==========

    @Test
    public void sinIsScaledAndTakes60000thsDegrees() {
        // sin a b = a*sin(b): sin(180 deg) = 0
        assertEquals(0.0, GuideEvaluator.evaluate("sin 1 10800000", env()), 1e-12);
        // 90 deg = 5400000 -> a * 1
        assertEquals(50.0, GuideEvaluator.evaluate("sin 50 5400000", env()), EPS);
        // 30 deg = 1800000 -> a * 0.5
        assertEquals(50.0, GuideEvaluator.evaluate("sin 100 1800000", env()), EPS);
        // negative angle
        assertEquals(-50.0, GuideEvaluator.evaluate("sin 100 -1800000", env()), EPS);
    }

    @Test
    public void cosAndTanTake60000thsDegrees() {
        // cos(60 deg = 3600000) = 0.5
        assertEquals(50.0, GuideEvaluator.evaluate("cos 100 3600000", env()), EPS);
        // cos(180 deg) = -1
        assertEquals(-100.0, GuideEvaluator.evaluate("cos 100 10800000", env()), EPS);
        // tan(45 deg = 2700000) = 1
        assertEquals(100.0, GuideEvaluator.evaluate("tan 100 2700000", env()), 1e-6);
    }

    @Test
    public void at2ReturnsAngleIn60000thsDegrees() {
        // at2 a b = atan2(b, a): atan2(1, 1) = 45 deg = 2700000
        assertEquals(2700000.0, GuideEvaluator.evaluate("at2 1 1", env()), 1e-3);
        // atan2(1, 0) = 90 deg
        assertEquals(5400000.0, GuideEvaluator.evaluate("at2 0 1", env()), 1e-3);
        // atan2(-1, 0) = -90 deg
        assertEquals(-5400000.0, GuideEvaluator.evaluate("at2 0 -1", env()), 1e-3);
    }

    @Test
    public void cat2AndSat2ProjectOntoEllipseParametricAngle() {
        // cat2 a b c = a*cos(atan2(c, b)); 3-4-5 triangle: cos=0.6, sin=0.8
        assertEquals(60.0, GuideEvaluator.evaluate("cat2 100 3 4", env()), EPS);
        assertEquals(80.0, GuideEvaluator.evaluate("sat2 100 3 4", env()), EPS);
        // pie start-point derivation for a 200x100 box, stAng=45 deg:
        // wt1 = wd2*sin(45) = 70.71068, ht1 = hd2*cos(45) = 35.35534
        // dx1 = cat2(wd2, ht1, wt1) = 100*cos(atan2(70.71068, 35.35534))
        //     = 100*cos(63.43495 deg) = 44.72136
        Map<String, Double> e = env("wd2", 100.0, "hd2", 50.0);
        double wt1 = GuideEvaluator.evaluate("sin wd2 2700000", e);
        double ht1 = GuideEvaluator.evaluate("cos hd2 2700000", e);
        e.put("wt1", wt1);
        e.put("ht1", ht1);
        assertEquals(70.710678, wt1, 1e-5);
        assertEquals(35.355339, ht1, 1e-5);
        assertEquals(44.721360, GuideEvaluator.evaluate("cat2 wd2 ht1 wt1", e), 1e-5);
        assertEquals(44.721360, GuideEvaluator.evaluate("sat2 hd2 ht1 wt1", e), 1e-5);
    }

    // ========== token resolution ==========

    @Test
    public void guideNamesMayStartWithDigits() {
        // the spec's own definitions use guides named 3cd4, 2a1, 2da
        Map<String, Double> e = env("2a1", 7.0);
        assertEquals(14.0, GuideEvaluator.evaluate("*/ 2a1 2 1", e), EPS);
    }

    @Test(expected = IllegalArgumentException.class)
    public void unknownGuideReferenceThrows() {
        GuideEvaluator.evaluate("val nosuchguide", env());
    }

    @Test(expected = IllegalArgumentException.class)
    public void unknownOpThrows() {
        GuideEvaluator.evaluate("frobnicate 1 2 3", env());
    }

    // ========== built-ins ==========

    @Test
    public void builtinsForA200x100Box() {
        Map<String, Double> b = GuideEvaluator.builtins(200, 100);
        assertEquals(200.0, b.get("w"), EPS);
        assertEquals(100.0, b.get("h"), EPS);
        assertEquals(100.0, b.get("ss"), EPS);   // min side
        assertEquals(200.0, b.get("ls"), EPS);   // max side
        assertEquals(0.0, b.get("t"), EPS);
        assertEquals(0.0, b.get("l"), EPS);
        assertEquals(100.0, b.get("b"), EPS);
        assertEquals(200.0, b.get("r"), EPS);
        assertEquals(100.0, b.get("hc"), EPS);
        assertEquals(50.0, b.get("vc"), EPS);
        assertEquals(100.0, b.get("wd2"), EPS);
        assertEquals(50.0, b.get("hd2"), EPS);
        assertEquals(200.0 / 3, b.get("wd3"), EPS);
        assertEquals(100.0 / 6, b.get("hd6"), EPS);
        assertEquals(200.0 / 32, b.get("wd32"), EPS);
        assertEquals(100.0 / 16, b.get("ssd16"), EPS);
        assertEquals(100.0 / 32, b.get("ssd32"), EPS);
        // circle-fraction constants (60000ths of a degree)
        assertEquals(10800000.0, b.get("cd2"), EPS);  // 180
        assertEquals(5400000.0, b.get("cd4"), EPS);   // 90
        assertEquals(2700000.0, b.get("cd8"), EPS);   // 45
        assertEquals(16200000.0, b.get("3cd4"), EPS); // 270
        assertEquals(8100000.0, b.get("3cd8"), EPS);  // 135
        assertEquals(13500000.0, b.get("5cd8"), EPS); // 225
        assertEquals(18900000.0, b.get("7cd8"), EPS); // 315
    }

    // ========== full-definition evaluation: chaining + overrides ==========

    private static GeometryDefinition roundRectDef() {
        // The real roundRect definition's avLst + first guides, verbatim
        // from the spec: adj defaults to 16667; a = pin(0, adj, 50000);
        // x1 = ss*a/100000; x2 = r - x1.
        return new GeometryDefinition("roundRect",
            List.of(new GeometryDefinition.Guide("adj", "val 16667")),
            List.of(
                new GeometryDefinition.Guide("a", "pin 0 adj 50000"),
                new GeometryDefinition.Guide("x1", "*/ ss a 100000"),
                new GeometryDefinition.Guide("x2", "+- r 0 x1")),
            List.of(),
            null);
    }

    @Test
    public void guidesChainInDocumentOrder() {
        Map<String, Double> env = GuideEvaluator.evaluate(roundRectDef(), Map.of(), 200, 100);
        // adj=16667 -> a=16667 -> x1 = 100*16667/100000 = 16.667 -> x2 = 183.333
        assertEquals(16667.0, env.get("adj"), EPS);
        assertEquals(16667.0, env.get("a"), EPS);
        assertEquals(16.667, env.get("x1"), 1e-9);
        assertEquals(183.333, env.get("x2"), 1e-9);
    }

    @Test
    public void avListOverrideReplacesDefault() {
        Map<String, Double> env = GuideEvaluator.evaluate(
            roundRectDef(), Map.of("adj", 25000), 200, 100);
        assertEquals(25000.0, env.get("adj"), EPS);
        assertEquals(25.0, env.get("x1"), EPS);   // ss*25000/100000 = 25
        assertEquals(175.0, env.get("x2"), EPS);
    }

    @Test
    public void avListOverrideIsClampedByGuideFormulasNotByTheOverride() {
        // pin still applies to out-of-range adjust values
        Map<String, Double> env = GuideEvaluator.evaluate(
            roundRectDef(), Map.of("adj", 99999), 200, 100);
        assertEquals(99999.0, env.get("adj"), EPS); // raw override kept
        assertEquals(50000.0, env.get("a"), EPS);   // pinned to 50000
        assertEquals(50.0, env.get("x1"), EPS);
    }

    @Test
    public void overrideForNameAbsentFromAvLstBecomesAGuide() {
        Map<String, Double> env = GuideEvaluator.evaluate(
            roundRectDef(), Map.of("adj", 20000, "stray", 123), 200, 100);
        assertEquals(123.0, env.get("stray"), EPS);
    }
}
