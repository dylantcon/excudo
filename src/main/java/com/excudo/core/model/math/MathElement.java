package com.excudo.core.model.math;

import java.util.List;
import java.util.Objects;

/**
 * One OOXML math element -- a leaf {@link Run} or one of the structured
 * math constructors that take {@link MathBody} arguments (fractions,
 * radicals, super/subscripts, n-ary operators, delimiters, accents,
 * bars, limits, function applications, group-characters).
 *
 * <p>The sealed permits list mirrors the OOXML element vocabulary
 * verbatim, one record per {@code m:*} element. Sub-element wrappers
 * that just carry a {@link MathBody} ({@code m:e}, {@code m:num},
 * {@code m:den}, {@code m:sub}, ...) aren't separate variants because
 * they appear only as fields on their parent constructor.
 *
 * <p>Element coverage is the load-bearing 80% from the Tier C scope
 * note: matrices ({@code m:m}), equation arrays ({@code m:eqArr}),
 * boxed arguments ({@code m:box}), phantoms ({@code m:phant}) are
 * deferred -- the parser will emit a flat-text {@link Run} fallback
 * for unsupported constructs so they read correctly even without
 * native layout support yet.
 */
public sealed interface MathElement permits
        MathElement.Run,
        MathElement.Fraction,
        MathElement.Radical,
        MathElement.Superscript,
        MathElement.Subscript,
        MathElement.SubSuperscript,
        MathElement.Prescript,
        MathElement.Nary,
        MathElement.Delimiter,
        MathElement.Bar,
        MathElement.Accent,
        MathElement.LimitLower,
        MathElement.LimitUpper,
        MathElement.Function,
        MathElement.GroupChar {

    // ============================================================
    // Leaf
    // ============================================================

    /**
     * A math run: text plus math-level styling.
     *
     * <p>{@code text} is the joined content of every {@code <m:t>}
     * inside an {@code <m:r>}. {@code style} encodes OMML-level
     * properties from {@code <m:rPr>} (italic / bold / script font
     * variant); the surrounding presentation properties from
     * {@code <a:rPr>} are not modelled here -- the layout engine pulls
     * font face from the math font and uses {@code style} to override.
     */
    record Run(String text, MathStyle style) implements MathElement {
        public Run {
            text = text != null ? text : "";
            style = style != null ? style : MathStyle.DEFAULT;
        }
    }

    // ============================================================
    // Structured constructors
    // ============================================================

    /** Fraction: numerator over denominator with an optional bar
     *  ({@code m:f}). */
    record Fraction(MathBody numerator, MathBody denominator, FractionType type)
            implements MathElement {
        public Fraction {
            Objects.requireNonNull(numerator, "numerator");
            Objects.requireNonNull(denominator, "denominator");
            if (type == null) type = FractionType.BAR;
        }
    }

    /** Radical (sqrt or nth-root): {@code m:rad}. {@code degree} is
     *  null for plain square roots. */
    record Radical(MathBody base, MathBody degree) implements MathElement {
        public Radical {
            Objects.requireNonNull(base, "base");
        }
    }

    /** Superscript only: {@code m:sSup} -- base with a superscript. */
    record Superscript(MathBody base, MathBody sup) implements MathElement {
        public Superscript {
            Objects.requireNonNull(base, "base");
            Objects.requireNonNull(sup, "sup");
        }
    }

    /** Subscript only: {@code m:sSub} -- base with a subscript. */
    record Subscript(MathBody base, MathBody sub) implements MathElement {
        public Subscript {
            Objects.requireNonNull(base, "base");
            Objects.requireNonNull(sub, "sub");
        }
    }

    /** Both sub- and superscript on the same base: {@code m:sSubSup}. */
    record SubSuperscript(MathBody base, MathBody sub, MathBody sup) implements MathElement {
        public SubSuperscript {
            Objects.requireNonNull(base, "base");
            Objects.requireNonNull(sub, "sub");
            Objects.requireNonNull(sup, "sup");
        }
    }

    /** Pre-script: {@code m:sPre} -- left-side sub/superscripts on a
     *  base, used in chemistry isotopes ({@code ^14_6 C}) etc. */
    record Prescript(MathBody preSub, MathBody preSup, MathBody base) implements MathElement {
        public Prescript {
            Objects.requireNonNull(preSub, "preSub");
            Objects.requireNonNull(preSup, "preSup");
            Objects.requireNonNull(base, "base");
        }
    }

    /** N-ary operator with optional limits: {@code m:nary}.
     *  {@code op} is the operator character (∑, ∏, ∫, etc).
     *  {@code sub} / {@code sup} are nullable when the corresponding
     *  hide flag is set. */
    record Nary(String op, MathBody sub, MathBody sup, MathBody base,
                NaryProperties properties) implements MathElement {
        public Nary {
            if (op == null || op.isEmpty()) op = "∑"; // ∑ default per spec
            Objects.requireNonNull(base, "base");
            if (properties == null) properties = NaryProperties.DEFAULT;
        }
    }

    /** Delimiter: {@code m:d} -- a left/right pair with optional
     *  separators between multiple element children (e.g. matrices
     *  with auto-sizing parens). */
    record Delimiter(String beginChar, String endChar, String separatorChar,
                     boolean grow, List<MathBody> elements) implements MathElement {
        public Delimiter {
            if (beginChar == null) beginChar = "(";
            if (endChar == null) endChar = ")";
            if (separatorChar == null) separatorChar = "|";
            elements = elements != null ? List.copyOf(elements) : List.of();
        }
    }

    /** Over- or underbar: {@code m:bar}. */
    record Bar(MathBody base, BarPosition position) implements MathElement {
        public Bar {
            Objects.requireNonNull(base, "base");
            if (position == null) position = BarPosition.TOP;
        }
    }

    /** Accent: {@code m:acc} -- an accent character placed over the
     *  base (hat, tilde, dot, double-dot, etc.). */
    record Accent(MathBody base, String accentChar) implements MathElement {
        public Accent {
            Objects.requireNonNull(base, "base");
            if (accentChar == null || accentChar.isEmpty()) accentChar = "̂"; // ̂ default
        }
    }

    /** Limit-lower: {@code m:limLow} -- a base with a limit underneath
     *  (typical use: {@code lim_{x→0}}). Not the same as a subscript
     *  -- the limit is centred on the base, not anchored to the
     *  bottom-right corner. */
    record LimitLower(MathBody base, MathBody limit) implements MathElement {
        public LimitLower {
            Objects.requireNonNull(base, "base");
            Objects.requireNonNull(limit, "limit");
        }
    }

    /** Limit-upper: {@code m:limUpp} -- limit centred over the base. */
    record LimitUpper(MathBody base, MathBody limit) implements MathElement {
        public LimitUpper {
            Objects.requireNonNull(base, "base");
            Objects.requireNonNull(limit, "limit");
        }
    }

    /** Function application: {@code m:func} -- a function name
     *  followed by an argument (e.g. {@code sin(x)}). The name is
     *  rendered in upright (non-italic) per math typography
     *  convention; the argument flows after with a thin space. */
    record Function(MathBody name, MathBody argument) implements MathElement {
        public Function {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(argument, "argument");
        }
    }

    /** Group character: {@code m:groupChr} -- a stretchy character
     *  (typically a brace) under or over the base, drawing attention
     *  to the entire base as one group. {@code pos} chooses which side. */
    record GroupChar(MathBody base, String character, GroupCharPosition position)
            implements MathElement {
        public GroupChar {
            Objects.requireNonNull(base, "base");
            if (character == null || character.isEmpty()) character = "⏟"; // ⏟
            if (position == null) position = GroupCharPosition.BOTTOM;
        }
    }

    // ============================================================
    // Property-record types
    // ============================================================

    enum FractionType {
        /** Standard horizontal-bar fraction (default). */ BAR,
        /** Skewed (slanted) fraction. */                  SKEWED,
        /** Linear (slash) fraction. */                    LINEAR,
        /** Stack (numerator over denominator, no bar). */ NO_BAR
    }

    enum BarPosition {
        TOP,    // overbar
        BOTTOM  // underbar
    }

    enum GroupCharPosition {
        TOP,
        BOTTOM
    }

    /**
     * Properties on an {@link Nary}: limit position (above/below or
     * to the side as super/sub), grow behaviour, and per-corner hide
     * flags. Defaults match the OOXML spec.
     */
    record NaryProperties(
            LimitLocation limitLocation,
            boolean grow,
            boolean hideSub,
            boolean hideSup) {
        public static final NaryProperties DEFAULT = new NaryProperties(
            LimitLocation.SUB_SUP, true, false, false);

        public NaryProperties {
            if (limitLocation == null) limitLocation = LimitLocation.SUB_SUP;
        }
    }

    /** Where an n-ary operator's limits are drawn relative to the
     *  operator glyph. */
    enum LimitLocation {
        /** Limits drawn beneath/above the operator (display style). */ UNDER_OVER,
        /** Limits drawn as standard sub/superscripts (inline style). */ SUB_SUP
    }

    /**
     * OMML-level math run styling.
     *
     * <p>Math runs default to italicised letters per math convention;
     * an explicit {@code <m:nor/>} or {@code <m:sty val="p"/>} opts
     * out for upright text (function names, units, etc.).
     */
    record MathStyle(boolean italic, boolean bold, ScriptVariant variant) {
        public static final MathStyle DEFAULT = new MathStyle(true, false, ScriptVariant.ROMAN);

        public MathStyle {
            if (variant == null) variant = ScriptVariant.ROMAN;
        }

        public MathStyle withItalic(boolean italic) {
            return new MathStyle(italic, bold, variant);
        }

        public MathStyle withBold(boolean bold) {
            return new MathStyle(italic, bold, variant);
        }

        public MathStyle withVariant(ScriptVariant variant) {
            return new MathStyle(italic, bold, variant);
        }
    }

    /** Math script font variant from {@code m:scr}: the alternate
     *  alphabets (script, fraktur, double-struck, etc) carved out of
     *  the math Unicode block. */
    enum ScriptVariant {
        /** Default math typography. */                ROMAN,
        /** Script ({𝒜𝒷𝒞𝒹...}). */                 SCRIPT,
        /** Fraktur (𝔄𝔟𝔅𝔡...). */                  FRAKTUR,
        /** Double-struck (ℕ ℝ ℂ ℤ). */               DOUBLE_STRUCK,
        /** Sans-serif math. */                       SANS_SERIF,
        /** Monospace math. */                        MONOSPACE
    }
}
