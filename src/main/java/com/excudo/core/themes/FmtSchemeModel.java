package com.excudo.core.themes;

import java.util.List;

/**
 * Typed model of an OOXML {@code a:fmtScheme} element. Produced by
 * {@link FmtSchemeParser} from a theme {@code org.w3c.dom.Document};
 * consumed by {@link FmtSchemeResolver} to produce {@link ResolvedFill}
 * and {@link FmtSchemeResolver.ResolvedLineStyle} given a placeholder
 * color. Zero DOM references — parser is the only layer that touches
 * {@code w3c.dom}.
 *
 * <p>Per ECMA-376:
 * <ul>
 *   <li>{@code fillStyleLst} / {@code lnStyleLst} / {@code effectStyleLst}
 *       are 1-based, three entries each, idx 1-3.</li>
 *   <li>{@code bgFillStyleLst} is also 1-based but referenced via idx
 *       1001-1003 elsewhere in the spec (1000 + position).</li>
 * </ul>
 */
public record FmtSchemeModel(
        List<FillDef> fillStyles,
        List<FillDef> bgFillStyles,
        List<LineDef> lineStyles,
        List<EffectDef> effectStyles) {

    public FmtSchemeModel {
        fillStyles   = List.copyOf(fillStyles);
        bgFillStyles = List.copyOf(bgFillStyles);
        lineStyles   = List.copyOf(lineStyles);
        effectStyles = List.copyOf(effectStyles);
    }

    // ====================================================================
    // Fill definitions (sealed — every legal a:fmtScheme fill child)
    // ====================================================================

    public sealed interface FillDef permits
            SolidFillDef, GradientFillDef, NoFillDef,
            BlipFillDef, PatternFillDef, GroupFillDef {}

    /** a:solidFill — single color (may be phClr). */
    public record SolidFillDef(ColorSpec color) implements FillDef {}

    /** a:gradFill — list of color stops + geometry metadata. */
    public record GradientFillDef(
            List<GradientStopDef> stops,
            double angleDegrees,
            GradientKind kind) implements FillDef {
        public GradientFillDef {
            stops = List.copyOf(stops);
        }
    }

    /** a:noFill — explicit "render nothing." */
    public record NoFillDef() implements FillDef {}

    /** a:blipFill — raster/image fill. {@code embedRelId} is set when the
     *  blip carries {@code r:embed}; {@code linkRelId} when it carries
     *  {@code r:link}. Both nullable. {@code duotoneColors} is empty for
     *  straight image fills and two-entry when the blip applies a duotone
     *  recolor (shadow color first, highlight color second, per ECMA-376
     *  §20.1.8.23). Resolvers that can't render images fall back to the
     *  caller's placeholder color. */
    public record BlipFillDef(
            String embedRelId,
            String linkRelId,
            List<ColorSpec> duotoneColors) implements FillDef {
        public BlipFillDef {
            duotoneColors = List.copyOf(duotoneColors);
        }

        /** Back-compat constructor for callers that don't care about duotone. */
        public BlipFillDef(String embedRelId, String linkRelId) {
            this(embedRelId, linkRelId, List.of());
        }
    }

    /** a:pattFill — preset two-color pattern (hash, dot, etc.). Stored
     *  as a raw preset name + foreground/background colors. Uncommon in
     *  fmtScheme entries but legal per the spec. */
    public record PatternFillDef(
            String preset,
            ColorSpec foreground,
            ColorSpec background) implements FillDef {}

    /** a:grpFill — inherits from the parent group. No data. */
    public record GroupFillDef() implements FillDef {}

    public record GradientStopDef(double position, ColorSpec color) {}

    public enum GradientKind { LINEAR, PATH }

    // ====================================================================
    // Line definitions
    // ====================================================================

    /** Theme line style — width + dash + optional fill (typically solid). */
    public record LineDef(
            double widthEmu,
            String dashStyle,
            FillDef fill) {}

    // ====================================================================
    // Effect definitions (v1 — opaque container; richer typing deferred)
    // ====================================================================

    /** Theme effect style. v1 keeps this as a marker record; when an
     *  effect consumer lands in the resolver path it can add fields
     *  without breaking the model's shape. */
    public record EffectDef() {}

    // ====================================================================
    // Color specifications
    // ====================================================================

    /** Every color reference inside the fmtScheme resolves to one of
     *  these at parse time. Substitution of the placeholder color (phClr)
     *  happens at resolve time, not parse time. */
    public sealed interface ColorSpec permits
            SrgbColor, SchemeColor, PlaceholderColor {}

    /** a:srgbClr — explicit hex. */
    public record SrgbColor(
            String hex,
            List<ColorModifierUtils.ColorModifier> modifiers) implements ColorSpec {
        public SrgbColor {
            modifiers = List.copyOf(modifiers);
        }
    }

    /** a:schemeClr with any {@code val} other than {@code phClr}
     *  (e.g. {@code accent1}, {@code tx1}). */
    public record SchemeColor(
            String val,
            List<ColorModifierUtils.ColorModifier> modifiers) implements ColorSpec {
        public SchemeColor {
            modifiers = List.copyOf(modifiers);
        }
    }

    /** a:schemeClr val="phClr" — to be substituted at resolve time. */
    public record PlaceholderColor(
            List<ColorModifierUtils.ColorModifier> modifiers) implements ColorSpec {
        public PlaceholderColor {
            modifiers = List.copyOf(modifiers);
        }
    }
}
