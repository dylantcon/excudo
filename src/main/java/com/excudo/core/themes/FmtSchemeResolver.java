package com.excudo.core.themes;

import org.w3c.dom.Document;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Resolves theme fill / line / effect styles by index with placeholder
 * color ({@code phClr}) substitution. Consumes a {@link FmtSchemeModel}
 * produced by {@link FmtSchemeParser}; does zero DOM walking itself.
 *
 * <p>OOXML index mapping:
 * <ul>
 *   <li>{@code fillStyleLst} / {@code lnStyleLst} / {@code effectStyleLst}:
 *       idx 1-3 (1-based).</li>
 *   <li>{@code bgFillStyleLst}: idx 1001-1003 (1000 + position).</li>
 *   <li>idx 0 = no style from theme matrix.</li>
 * </ul>
 */
public class FmtSchemeResolver {

    private FmtSchemeModel model;

    // ====================================================================
    // Construction
    // ====================================================================

    /** Entry point that accepts a theme DOM — delegates parsing to
     *  {@link FmtSchemeParser} and stores the typed model. Kept for
     *  binary-compatible migration of existing callers. */
    public boolean parseFmtScheme(Document themeDocument) {
        Optional<FmtSchemeModel> parsed = FmtSchemeParser.parse(themeDocument);
        if (parsed.isEmpty()) return false;
        this.model = parsed.get();
        return true;
    }

    /** Direct-injection entry point for callers that already have a
     *  parsed model (tests, pre-computed pipelines). */
    public void setModel(FmtSchemeModel model) {
        this.model = model;
    }

    public boolean isParsed() { return model != null; }

    // ====================================================================
    // Fill resolution
    // ====================================================================

    /**
     * Resolve a fill style by index with phClr substitution.
     * @param idx fillRef/@idx (1-3) or bgRef/@idx (1001-1003). 0 = no fill.
     * @param phColorHex resolved scheme color hex to substitute for phClr.
     */
    public ResolvedFill resolveFillByIndex(int idx, String phColorHex) {
        if (idx == 0) return new ResolvedFill.NoFill();
        requireParsed();

        List<FmtSchemeModel.FillDef> list;
        int listIdx;
        if (idx >= 1001) {
            list = model.bgFillStyles();
            listIdx = idx - 1001;
        } else {
            list = model.fillStyles();
            listIdx = idx - 1;
        }

        if (listIdx < 0 || listIdx >= list.size()) {
            throw new IllegalArgumentException("Fill style index " + idx
                + " out of range (list has " + list.size() + " entries)");
        }

        return resolveFill(list.get(listIdx), phColorHex);
    }

    /** Dispatch a typed FillDef to its ResolvedFill counterpart. */
    private ResolvedFill resolveFill(FmtSchemeModel.FillDef fill, String phColorHex) {
        return switch (fill) {
            case FmtSchemeModel.SolidFillDef s -> {
                ColorModifierUtils.ColorWithAlpha c = resolveColor(s.color(), phColorHex);
                yield new ResolvedFill.SolidFill(c.hex(), c.alpha());
            }
            case FmtSchemeModel.GradientFillDef g -> resolveGradient(g, phColorHex);
            case FmtSchemeModel.NoFillDef n -> new ResolvedFill.NoFill();
            case FmtSchemeModel.BlipFillDef b -> {
                String relId = b.embedRelId() != null ? b.embedRelId() : b.linkRelId();
                List<String> duotone = new ArrayList<>(b.duotoneColors().size());
                for (FmtSchemeModel.ColorSpec c : b.duotoneColors()) {
                    duotone.add(resolveColor(c, phColorHex).hex());
                }
                yield new ResolvedFill.BlipFill(relId, duotone);
            }
            // Pattern fills and group fills aren't yet rendered; expose
            // them as NoFill so downstream rendering falls back to the
            // caller's phClr or the shape's own default rather than
            // crashing. When rendering support lands, add dedicated
            // ResolvedFill variants and flip these cases over.
            case FmtSchemeModel.PatternFillDef p -> new ResolvedFill.NoFill();
            case FmtSchemeModel.GroupFillDef g -> new ResolvedFill.NoFill();
        };
    }

    private ResolvedFill.GradientFill resolveGradient(
            FmtSchemeModel.GradientFillDef grad, String phColorHex) {
        List<ResolvedFill.GradientStop> stops = new ArrayList<>(grad.stops().size());
        for (FmtSchemeModel.GradientStopDef stop : grad.stops()) {
            ColorModifierUtils.ColorWithAlpha c = resolveColor(stop.color(), phColorHex);
            stops.add(new ResolvedFill.GradientStop(stop.position(), c.hex(), c.alpha()));
        }
        ResolvedFill.GradientType type = switch (grad.kind()) {
            case LINEAR -> ResolvedFill.GradientType.LINEAR;
            case PATH   -> ResolvedFill.GradientType.PATH;
        };
        return new ResolvedFill.GradientFill(stops, grad.angleDegrees(), type);
    }

    // ====================================================================
    // Line resolution
    // ====================================================================

    /**
     * Resolve a line style by index with phClr substitution.
     * @param idx lnRef/@idx (1-3). 0 = no line style.
     * @param phColorHex resolved scheme color hex to substitute for phClr.
     */
    public ResolvedLineStyle resolveLineByIndex(int idx, String phColorHex) {
        if (idx == 0) return ResolvedLineStyle.NONE;
        requireParsed();

        int listIdx = idx - 1;
        List<FmtSchemeModel.LineDef> list = model.lineStyles();
        if (listIdx < 0 || listIdx >= list.size()) {
            throw new IllegalArgumentException("Line style index " + idx
                + " out of range (list has " + list.size() + " entries)");
        }

        FmtSchemeModel.LineDef ln = list.get(listIdx);

        // The theme line's fill is typically a:solidFill, but any of
        // the fill variants is legal. We resolve to the fill's color
        // component when available, and fall back to the caller's
        // phClr when the fill is NoFill / unsupported.
        String colorHex;
        double alpha = 1.0;
        ResolvedFill resolvedFill = resolveFill(ln.fill(), phColorHex);
        if (resolvedFill instanceof ResolvedFill.SolidFill s) {
            colorHex = s.hex();
            alpha = s.alpha();
        } else if (resolvedFill instanceof ResolvedFill.GradientFill g && !g.stops().isEmpty()) {
            ResolvedFill.GradientStop first = g.stops().get(0);
            colorHex = first.hex();
            alpha = first.alpha();
        } else {
            colorHex = phColorHex.startsWith("#") ? phColorHex : "#" + phColorHex;
        }

        return new ResolvedLineStyle(colorHex, alpha, ln.widthEmu(), ln.dashStyle());
    }

    public record ResolvedLineStyle(String colorHex, double alpha, double widthEmu, String dashStyle) {
        public static final ResolvedLineStyle NONE = new ResolvedLineStyle("#000000", 1.0, 0, "solid");
    }

    // ====================================================================
    // Effect resolution
    // ====================================================================

    /**
     * Resolve an effect style's outer shadow by index with phClr
     * substitution.
     * @param idx effectRef/@idx (1-3). 0 = no effect style.
     * @return the resolved shadow, or null when the style declares no
     *         outer shadow (or idx is 0).
     */
    public ResolvedShadow resolveEffectByIndex(int idx, String phColorHex) {
        if (idx == 0) return null;
        requireParsed();

        int listIdx = idx - 1;
        List<FmtSchemeModel.EffectDef> list = model.effectStyles();
        if (listIdx < 0 || listIdx >= list.size()) {
            throw new IllegalArgumentException("Effect style index " + idx
                + " out of range (list has " + list.size() + " entries)");
        }

        FmtSchemeModel.OuterShadowDef shdw = list.get(listIdx).outerShadow();
        if (shdw == null) return null;

        ColorModifierUtils.ColorWithAlpha c = resolveColor(shdw.color(), phColorHex);
        return new ResolvedShadow(c.hex(), c.alpha(),
            shdw.blurRadEmu(), shdw.distEmu(), shdw.directionDegrees());
    }

    /** Outer shadow of a theme effect style, phClr-substituted. */
    public record ResolvedShadow(String colorHex, double alpha,
                                 double blurRadEmu, double distEmu,
                                 double directionDegrees) {}

    // ====================================================================
    // Color resolution (typed ColorSpec -> hex+alpha, phClr-substituted)
    // ====================================================================

    private ColorModifierUtils.ColorWithAlpha resolveColor(
            FmtSchemeModel.ColorSpec color, String phColorHex) {
        return switch (color) {
            case FmtSchemeModel.SrgbColor s -> applyMods(s.hex(), s.modifiers());
            case FmtSchemeModel.SchemeColor sc ->
                // Non-phClr scheme colors resolve through ThemeManager.
                // HexColor's .withHash() is the explicit adapter — no
                // String-concat ambiguity.
                applyMods(ThemeManager.getThemeColor(sc.val()).withHash(), sc.modifiers());
            case FmtSchemeModel.PlaceholderColor p ->
                applyMods(
                    phColorHex.startsWith("#") ? phColorHex : "#" + phColorHex,
                    p.modifiers());
        };
    }

    private ColorModifierUtils.ColorWithAlpha applyMods(
            String baseHex, List<ColorModifierUtils.ColorModifier> mods) {
        return mods.isEmpty()
            ? new ColorModifierUtils.ColorWithAlpha(baseHex)
            : ColorModifierUtils.applyModifiers(baseHex, mods);
    }

    // ====================================================================
    // Guards
    // ====================================================================

    private void requireParsed() {
        if (model == null) {
            throw new IllegalStateException(
                "FmtSchemeResolver has no model — call parseFmtScheme or setModel first.");
        }
    }
}
