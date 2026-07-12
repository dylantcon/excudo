package com.excudo.core.metrics;

import com.excudo.core.model.TextColor;

/**
 * Effective per-indent-level text style defaults for one text body,
 * already resolved through the OOXML list-style inheritance chain:
 *
 * <pre>
 *   run rPr  &gt;  paragraph pPr/defRPr
 *            &gt;  shape txBody a:lstStyle
 *            &gt;  slide layout placeholder a:lstStyle     (placeholders)
 *            &gt;  slide master p:txStyles by ph type      (placeholders)
 *            &gt;  presentation p:defaultTextStyle          (plain text boxes)
 *            &gt;  theme defaults
 * </pre>
 *
 * The run/paragraph levels stay with the {@code TextRun}/{@code TextParagraph}
 * model; everything below them is collapsed into one {@link LevelStyle}
 * per indent level by the resolver that builds this source (see
 * {@code com.excudo.view.rendering.text.LstStyleResolver}). {@link TextMeasurer}
 * and {@code TextPainter} consult the same source so measurement and
 * painting can never disagree about effective font sizes or margins.
 *
 * <p>All {@link LevelStyle} fields are nullable; null means "no level in
 * the chain specified this" and the consumer applies its documented
 * hard default (18 pt, left-aligned, zero margins, no bullet).
 */
public interface TextStyleSource {

    /**
     * Effective style for the given indent level (0-8). Never null;
     * return {@link LevelStyle#EMPTY} when nothing is known.
     */
    LevelStyle levelStyle(int level);

    /** Source that knows nothing — every consumer default applies. */
    TextStyleSource EMPTY = level -> LevelStyle.EMPTY;

    /**
     * One resolved indent level. Nullable fields = "unspecified".
     *
     * @param fontSizeCentiPt     default run font size, hundredths of a point
     * @param bold                default run bold
     * @param italic              default run italic
     * @param fontFamily          default run latin typeface (theme-symbolic
     *                            {@code +mn-lt}/{@code +mj-lt} already resolved)
     * @param color               default run color
     * @param marginLeftEmu       paragraph left margin
     * @param marginRightEmu      paragraph right margin
     * @param indentEmu           first-line indent relative to marL (negative = hanging)
     * @param alignment           "l", "ctr", "r", "just"
     * @param lineSpacingPct      thousandths of a percent (100000 = single)
     * @param spaceBeforeCentiPt  spcBef in hundredths of a point
     * @param spaceAfterCentiPt   spcAft in hundredths of a point
     * @param bulletNone          explicit a:buNone at this level
     * @param bulletChar          a:buChar character
     * @param bulletFont          a:buFont typeface
     * @param autonumType         a:buAutoNum type
     * @param bulletSizePct       a:buSzPct in thousandths of a percent
     * @param bulletColor         a:buClr
     */
    record LevelStyle(
            Integer fontSizeCentiPt,
            Boolean bold,
            Boolean italic,
            String fontFamily,
            TextColor color,
            Integer marginLeftEmu,
            Integer marginRightEmu,
            Integer indentEmu,
            String alignment,
            Integer lineSpacingPct,
            Integer spaceBeforeCentiPt,
            Integer spaceAfterCentiPt,
            Boolean bulletNone,
            String bulletChar,
            String bulletFont,
            String autonumType,
            Integer bulletSizePct,
            TextColor bulletColor) {

        public static final LevelStyle EMPTY = new LevelStyle(
            null, null, null, null, null, null, null, null, null,
            null, null, null, null, null, null, null, null, null);

        /**
         * Overlay {@code o} on top of this style: any field {@code o}
         * specifies wins. Bullet KIND (buNone / buChar / buAutoNum) is
         * overridden as a group — a level that says {@code buNone}
         * suppresses an inherited {@code buChar} and vice versa — while
         * bullet decorations (size, color, font) override field-by-field
         * per CT_TextParagraphProperties semantics.
         */
        public LevelStyle overlaidBy(LevelStyle o) {
            if (o == null || o == EMPTY) return this;
            boolean oDefinesBulletKind = Boolean.TRUE.equals(o.bulletNone)
                || o.bulletChar != null || o.autonumType != null;
            return new LevelStyle(
                o.fontSizeCentiPt != null ? o.fontSizeCentiPt : fontSizeCentiPt,
                o.bold != null ? o.bold : bold,
                o.italic != null ? o.italic : italic,
                o.fontFamily != null ? o.fontFamily : fontFamily,
                o.color != null ? o.color : color,
                o.marginLeftEmu != null ? o.marginLeftEmu : marginLeftEmu,
                o.marginRightEmu != null ? o.marginRightEmu : marginRightEmu,
                o.indentEmu != null ? o.indentEmu : indentEmu,
                o.alignment != null ? o.alignment : alignment,
                o.lineSpacingPct != null ? o.lineSpacingPct : lineSpacingPct,
                o.spaceBeforeCentiPt != null ? o.spaceBeforeCentiPt : spaceBeforeCentiPt,
                o.spaceAfterCentiPt != null ? o.spaceAfterCentiPt : spaceAfterCentiPt,
                oDefinesBulletKind ? o.bulletNone : bulletNone,
                oDefinesBulletKind ? o.bulletChar : bulletChar,
                o.bulletFont != null ? o.bulletFont : bulletFont,
                oDefinesBulletKind ? o.autonumType : autonumType,
                o.bulletSizePct != null ? o.bulletSizePct : bulletSizePct,
                o.bulletColor != null ? o.bulletColor : bulletColor);
        }
    }
}
