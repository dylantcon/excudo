package com.excudo.core.synthesis;

import com.excudo.core.introspection.TransitionDescriptor;
import com.excudo.core.model.AnimationBinding;
import com.excudo.core.model.BodyProperties;
import com.excudo.core.model.ShapeFill;
import com.excudo.core.model.ShapeGeometry;
import com.excudo.core.model.ShapeLine;
import com.excudo.core.model.ShapeStyle;
import com.excudo.core.model.TextBody;
import com.excudo.core.model.TextColor;
import com.excudo.core.model.TextParagraph;
import com.excudo.core.model.TextRun;
import com.excudo.core.model.ThemeStyleRef;

import java.util.List;
import java.util.Objects;

/**
 * Structural equality helpers for the model value types used by the
 * synthesizer. The underlying Excudo model classes don't override
 * {@code equals/hashCode}, and rather than patch all of them (risking
 * breakage for identity-comparison callers), the synthesizer declares
 * its own equality semantics in one place.
 *
 * <p>Rules of thumb:
 * <ul>
 *   <li><b>Both null</b> &rarr; equal.</li>
 *   <li><b>One null, one non-null</b> &rarr; not equal. (A null fill
 *       and a concrete fill are different <em>from the synthesizer's
 *       perspective</em> even though the rendered output may converge.)</li>
 *   <li><b>Both present</b> &rarr; deep field comparison.</li>
 * </ul>
 *
 * <p>All methods are static and pure.
 */
public final class StateEquality {

    private StateEquality() {}

    // ========== Geometry ==========

    public static boolean sameGeometry(ShapeGeometry a, ShapeGeometry b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        return a.getX() == b.getX()
            && a.getY() == b.getY()
            && a.getWidth() == b.getWidth()
            && a.getHeight() == b.getHeight()
            && a.getRotationDegrees() == b.getRotationDegrees();
    }

    public static boolean samePosition(ShapeGeometry a, ShapeGeometry b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        return a.getX() == b.getX() && a.getY() == b.getY();
    }

    public static boolean sameSize(ShapeGeometry a, ShapeGeometry b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        return a.getWidth() == b.getWidth() && a.getHeight() == b.getHeight();
    }

    public static boolean sameRotation(ShapeGeometry a, ShapeGeometry b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        return a.getRotationDegrees() == b.getRotationDegrees();
    }

    // ========== Color ==========

    public static boolean sameColor(TextColor a, TextColor b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        if (a.isScheme() != b.isScheme()) return false;
        if (a.isScheme()) return Objects.equals(a.getSchemeVal(), b.getSchemeVal());
        return Objects.equals(a.getHexVal(), b.getHexVal());
    }

    // ========== Fill ==========

    public static boolean sameFill(ShapeFill a, ShapeFill b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        if (a.getType() != b.getType()) return false;
        // alphaPercent is a user-visible fill property; without comparing it
        // an opacity-only edit never diffs and the synthesizer emits no spec.
        return sameColor(a.getColor(), b.getColor())
            && Objects.equals(a.getAlphaPercent(), b.getAlphaPercent());
    }

    // ========== Line ==========

    public static boolean sameLine(ShapeLine a, ShapeLine b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        return Objects.equals(a.getWidthEMU(), b.getWidthEMU())
            && sameColor(a.getColor(), b.getColor())
            && Objects.equals(a.getDashStyle(), b.getDashStyle());
    }

    // ========== ThemeStyleRef ==========

    public static boolean sameThemeStyleRef(ThemeStyleRef a, ThemeStyleRef b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        // Treat NONE by identity: the writer branches on it explicitly.
        if ((a == ThemeStyleRef.NONE) != (b == ThemeStyleRef.NONE)) return false;
        return a.getLineRefIdx() == b.getLineRefIdx()
            && Objects.equals(a.getLineColor(), b.getLineColor())
            && Objects.equals(a.getLineShadeVal(), b.getLineShadeVal())
            && a.getFillRefIdx() == b.getFillRefIdx()
            && Objects.equals(a.getFillColor(), b.getFillColor())
            && a.getEffectRefIdx() == b.getEffectRefIdx()
            && Objects.equals(a.getEffectColor(), b.getEffectColor())
            && Objects.equals(a.getFontRefIdx(), b.getFontRefIdx())
            && Objects.equals(a.getFontColor(), b.getFontColor());
    }

    // ========== ShapeStyle ==========

    public static boolean sameStyle(ShapeStyle a, ShapeStyle b) {
        if (a == b) return true;
        // A hollow style (all fields null) carries zero information --
        // semantically identical to no style at all. The introspector
        // returns a hollow ShapeStyle for shapes without an explicit
        // p:style while PlaceholderProjector projects null; without this
        // rule every untouched placeholder diffs as STYLE-changed and
        // the synthesizer emits junk SetShapeStyleSpecs. Fixed-point
        // contract pinned by SynthesisFixedPointTest.
        if (isHollowStyle(a) && isHollowStyle(b)) return true;
        if (a == null || b == null) return false;
        return sameFill(a.getFill(), b.getFill())
            && sameLine(a.getLine(), b.getLine())
            && sameThemeStyleRef(a.getThemeStyle(), b.getThemeStyle());
    }

    private static boolean isHollowStyle(ShapeStyle s) {
        return s == null
            || (s.getFill() == null && s.getLine() == null && s.getThemeStyle() == null);
    }

    // ========== TextBody ==========

    public static boolean sameTextBody(TextBody a, TextBody b) {
        if (a == b) return true;
        // A body with zero authored characters is "no text" -- equal to
        // null. Fresh placeholders carry an empty paragraph (PowerPoint
        // stores prompt text on the layout, not the slide) while the
        // projector projects null; without this rule every untouched
        // placeholder diffs as TEXT-changed. A body WITH text still
        // compares strictly (so authored title text emits its
        // SetTextSpec). Known tradeoff: bodyProperties-only changes on
        // an EMPTY shape don't diff -- that's the deferred
        // SetBodyPropsSpec vocabulary gap, tracked in
        // project_synthesis_gaps.md.
        if (hasNoAuthoredText(a) && hasNoAuthoredText(b)) return true;
        if (a == null || b == null) return false;
        if (!sameBodyProperties(a.getBodyProperties(), b.getBodyProperties())) return false;
        return sameParagraphs(a.getParagraphs(), b.getParagraphs());
    }

    private static boolean hasNoAuthoredText(TextBody t) {
        if (t == null) return true;
        if (t.getParagraphs() == null) return true;
        for (TextParagraph p : t.getParagraphs()) {
            for (var r : p.getRuns()) {
                if (r.getText() != null && !r.getText().isEmpty()) return false;
            }
        }
        return true;
    }

    private static boolean sameBodyProperties(BodyProperties a, BodyProperties b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        // BodyProperties is a heavy bag; for v1 compare the user-facing
        // fields the writer emits. Anchor, autofit, left/right/top/bottom
        // insets, and wrap are the ones callers can tweak.
        return Objects.equals(a.getVerticalAlignment(), b.getVerticalAlignment())
            && a.getAutofit() == b.getAutofit()
            && Objects.equals(a.getLeftInset(), b.getLeftInset())
            && Objects.equals(a.getRightInset(), b.getRightInset())
            && Objects.equals(a.getTopInset(), b.getTopInset())
            && Objects.equals(a.getBottomInset(), b.getBottomInset())
            && Objects.equals(a.getWrap(), b.getWrap());
    }

    private static boolean sameParagraphs(List<TextParagraph> a, List<TextParagraph> b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) {
            if (!sameParagraph(a.get(i), b.get(i))) return false;
        }
        return true;
    }

    private static boolean sameParagraph(TextParagraph a, TextParagraph b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        if (!Objects.equals(a.getAlignment(), b.getAlignment())) return false;
        if (a.getLevel() != b.getLevel()) return false;
        if (a.getBulletType() != b.getBulletType()) return false;
        if (!Objects.equals(a.getBulletChar(), b.getBulletChar())) return false;
        List<TextRun> ra = a.getRuns();
        List<TextRun> rb = b.getRuns();
        if (ra.size() != rb.size()) return false;
        for (int i = 0; i < ra.size(); i++) {
            if (!sameRun(ra.get(i), rb.get(i))) return false;
        }
        return true;
    }

    private static boolean sameRun(TextRun a, TextRun b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        return Objects.equals(a.getText(), b.getText())
            && Objects.equals(a.getFontFamily(), b.getFontFamily())
            && Objects.equals(a.getFontSize(), b.getFontSize())
            && Objects.equals(a.getBold(), b.getBold())
            && Objects.equals(a.getItalic(), b.getItalic())
            && Objects.equals(a.getUnderline(), b.getUnderline())
            && sameColor(a.getColor(), b.getColor())
            && sameColor(a.getHighlight(), b.getHighlight());
    }

    // ========== AnimationBinding ==========

    /**
     * Animations are identified by the tuple (target SPID, type,
     * click trigger, paragraph start, paragraph end). Two bindings
     * with the same identity can still differ in <em>timing</em>
     * (duration, delay) -- the synthesizer expresses that difference
     * as a {@code SetAnimationTimingSpec} via
     * {@link #sameAnimationTiming(AnimationBinding, AnimationBinding)}.
     */
    public static boolean sameAnimationIdentity(AnimationBinding a, AnimationBinding b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        return a.getTargetSpid() == b.getTargetSpid()
            && a.getAnimationType() == b.getAnimationType()
            && a.getClickTrigger() == b.getClickTrigger()
            && Objects.equals(a.getParagraphStart(), b.getParagraphStart())
            && Objects.equals(a.getParagraphEnd(), b.getParagraphEnd());
    }

    /** Duration, delay, and timing-only fields comparison. Identity is
     *  NOT included here (that's {@link #sameAnimationIdentity}). Use
     *  these two together: identity-match + timing-match &rarr; the
     *  binding is unchanged; identity-match + timing-mismatch &rarr;
     *  emit {@code SetAnimationTimingSpec}. */
    public static boolean sameAnimationTiming(AnimationBinding a, AnimationBinding b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        return Objects.equals(a.getDuration(), b.getDuration())
            && Objects.equals(a.getDelay(), b.getDelay());
    }

    // ========== TransitionDescriptor ==========

    /**
     * Transitions are equal when their type + attributes match. Source
     * field is NOT part of equality -- a slide that inherits FADE from
     * its master and another that declares FADE directly have the same
     * effective transition; the synthesizer cares about that equality
     * so it doesn't emit a redundant SetTransitionSpec.
     */
    public static boolean sameTransition(TransitionDescriptor a, TransitionDescriptor b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        return a.type() == b.type()
            && Objects.equals(a.speed(), b.speed())
            && Objects.equals(a.durationMs(), b.durationMs())
            && Objects.equals(a.autoAdvanceMs(), b.autoAdvanceMs());
    }
}
