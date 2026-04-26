package com.excudo.core.synthesis;

import com.excudo.core.model.AnimationBinding;
import com.excudo.core.model.TextBody;
import com.excudo.core.synthesis.spec.CommandSpec;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Turns a {@link SlideStateDiff} into the minimum-DAG
 * {@link CommandScript} whose execution against the baseline produces
 * the observed current state.
 *
 * <p>The synthesizer is structurally a dispatch table from delta kind
 * to spec emission + dependency-edge registration. "Minimum" means: no
 * redundant specs (one spec per field per shape, never two that cancel
 * out) and no unnecessary edges (independent additions stay parallel).
 *
 * <p><b>v1 scope note:</b> not every delta has a corresponding spec yet.
 * Deltas the synthesizer can't express currently emit a warning via
 * {@link #warnings()} and are skipped -- never silently dropped. Items
 * tracked for a follow-up tier:
 * <ul>
 *   <li>Shape NAME field change: no dedicated command today (workaround is
 *       remove+add, which isn't minimum).</li>
 *   <li>Shape TEXT_BOX_FLAG change: structural, requires remove+add.</li>
 *   <li>PARENT_GROUP change: handled partially via
 *       {@link SlideStateDiff.GroupDelta} -- cross-group moves deferred
 *       pending add-to-group / detach-from-group primitives (Tier 5).</li>
 *   <li>Animation REMOVAL: {@code RemoveAnimationSpec} needs a timing
 *       node id; {@link AnimationBinding} doesn't carry one, so the
 *       synthesizer skips baseline-present animations that the current
 *       state drops. Revisit once the binding tracks its cTn id.</li>
 * </ul>
 */
public final class ScriptSynthesizer {

    private ScriptSynthesizer() {}

    /** Output of {@link #synthesize(SlideStateDiff, int)}: the script
     *  plus a list of warnings describing skipped deltas. Never drop
     *  data silently. */
    public record Result(CommandScript script, java.util.List<String> warnings) {}

    /**
     * Synthesize the minimum DAG of specs for the given diff on the
     * given slide. The slide number is explicit so specs targeting the
     * right slide get emitted even though the diff is slide-agnostic.
     */
    public static Result synthesize(SlideStateDiff diff, int slideNumber) {
        CommandScript.Builder builder = CommandScript.builder();
        java.util.List<String> warnings = new java.util.ArrayList<>();

        // Spec registry: maps "stable keys" (SPIDs for shapes, binding
        // identity tuples for animations) to the specs that create
        // them. The synthesizer uses this to wire DAG edges when a
        // downstream spec references an upstream creation.
        Map<Integer, CommandSpec> addShapeBySpid = new HashMap<>();

        // Compound primitives (code boxes etc.) are detected up front so
        // their constituent shapes / groups don't show up in the regular
        // emitShapeAdds + emitGroupDeltas paths. The set of "consumed"
        // SPIDs is the union of (a) the tagged group itself and
        // (b) every child of that group in the added set.
        Set<Integer> consumedSpids = new HashSet<>();
        emitCompoundPrimitives(diff, slideNumber, builder, consumedSpids, warnings);

        emitShapeAdds(diff, slideNumber, builder, addShapeBySpid, consumedSpids);
        emitShapeRemovals(diff, slideNumber, builder, warnings);
        emitShapeModifications(diff, slideNumber, builder, warnings);
        emitAnimationAdds(diff, slideNumber, builder, addShapeBySpid);
        emitAnimationRemovals(diff, slideNumber, builder, warnings);
        emitAnimationTimingChanges(diff, slideNumber, builder, warnings);
        emitTransitionChange(diff, slideNumber, builder);
        emitGroupDeltas(diff, slideNumber, builder, addShapeBySpid, consumedSpids);

        return new Result(builder.build(), java.util.List.copyOf(warnings));
    }

    // ========== Compound primitives ==========

    /**
     * Detect tagged compound primitives (code boxes etc.) in the added
     * set and emit a single high-level spec per compound, marking its
     * constituent SPIDs as consumed so subsequent passes skip them.
     */
    private static void emitCompoundPrimitives(SlideStateDiff diff, int slideNumber,
            CommandScript.Builder builder, Set<Integer> consumedSpids,
            java.util.List<String> warnings) {
        // Index added snapshots by SPID for parent-child lookup.
        Map<Integer, ShapeSnapshot> addedBySpid = new HashMap<>();
        for (SlideStateDiff.ShapeAdded added : diff.added()) {
            addedBySpid.put(added.shape().spid(), added.shape());
        }

        for (SlideStateDiff.ShapeAdded added : diff.added()) {
            ShapeSnapshot s = added.shape();
            if (s.type() != com.excudo.core.model.SlideShape.ShapeType.GROUP) continue;

            // Code box?
            String language = com.excudo.core.commands.mutating.slide.CreateCodeBoxCommand
                .languageFromTag(s.name());
            if (language != null) {
                emitCodeBoxFromTag(s, language, slideNumber, diff,
                    addedBySpid, builder, consumedSpids, warnings);
                continue;
            }

            // Mermaid diagram?
            String mermaidSource = com.excudo.core.commands.mutating.slide.CreateMermaidDiagramCommand
                .sourceFromTag(s.name());
            if (mermaidSource != null) {
                emitDiagramFromTag(s, mermaidSource, slideNumber, diff,
                    builder, consumedSpids);
                continue;
            }
        }
    }

    private static void emitCodeBoxFromTag(ShapeSnapshot s, String language, int slideNumber,
            SlideStateDiff diff, Map<Integer, ShapeSnapshot> addedBySpid,
            CommandScript.Builder builder, Set<Integer> consumedSpids,
            java.util.List<String> warnings) {
        ShapeSnapshot codePanel = null;
        java.util.List<Integer> childSpids = new java.util.ArrayList<>();
        for (SlideStateDiff.ShapeAdded a : diff.added()) {
            ShapeSnapshot child = a.shape();
            if (child.parentGroupSpid() != null && child.parentGroupSpid() == s.spid()) {
                childSpids.add(child.spid());
                if ("Code".equals(child.name())) codePanel = child;
            }
        }

        if (codePanel == null || codePanel.textBody() == null) {
            warnings.add("Code box group SPID " + s.spid()
                + " has tag '" + s.name() + "' but no Code child with a text body; "
                + "falling back to AddShape decomposition.");
            return;
        }

        String code = extractPlainCode(codePanel.textBody());
        String lineNumberColor = extractLineNumberColor(addedBySpid, childSpids);

        CommandSpec.CreateCodeBoxSpec spec = new CommandSpec.CreateCodeBoxSpec(
            slideNumber, language, code,
            s.geometry().getX(), s.geometry().getY(),
            s.geometry().getWidth(), s.geometry().getHeight(),
            lineNumberColor, s.spid());
        builder.add(spec);

        // Consume the group and every child of the group; emitGroupDeltas
        // also skips the GroupDelta keyed on this group's SPID.
        consumedSpids.add(s.spid());
        consumedSpids.addAll(childSpids);
    }

    private static void emitDiagramFromTag(ShapeSnapshot s, String mermaidSource, int slideNumber,
            SlideStateDiff diff, CommandScript.Builder builder, Set<Integer> consumedSpids) {
        // Diagrams have many descendant shapes (nodes, connectors, labels);
        // consume every shape whose parent chain includes this group.
        java.util.List<Integer> descendants = new java.util.ArrayList<>();
        for (SlideStateDiff.ShapeAdded a : diff.added()) {
            ShapeSnapshot child = a.shape();
            Integer parent = child.parentGroupSpid();
            // Walk up the parent chain via the addedBySpid index.
            while (parent != null) {
                if (parent == s.spid()) {
                    descendants.add(child.spid());
                    break;
                }
                ShapeSnapshot ancestor = findInDiff(diff, parent);
                parent = ancestor != null ? ancestor.parentGroupSpid() : null;
            }
        }

        CommandSpec.CreateDiagramSpec spec = new CommandSpec.CreateDiagramSpec(
            slideNumber, mermaidSource,
            s.geometry().getX(), s.geometry().getY(),
            s.geometry().getWidth(), s.geometry().getHeight(),
            s.spid());
        builder.add(spec);

        consumedSpids.add(s.spid());
        consumedSpids.addAll(descendants);
    }

    private static ShapeSnapshot findInDiff(SlideStateDiff diff, int spid) {
        for (SlideStateDiff.ShapeAdded a : diff.added()) {
            if (a.shape().spid() == spid) return a.shape();
        }
        return null;
    }

    /** Concatenate every run's text in document order, splitting paragraphs
     *  with newlines. Round-trips the source code stored as syntax-highlighted
     *  runs back into a flat string for re-tokenization. */
    private static String extractPlainCode(TextBody body) {
        StringBuilder sb = new StringBuilder();
        java.util.List<com.excudo.core.model.TextParagraph> paragraphs = body.getParagraphs();
        for (int i = 0; i < paragraphs.size(); i++) {
            for (com.excudo.core.model.TextRun run : paragraphs.get(i).getRuns()) {
                String t = run.getText();
                if (t != null) sb.append(t);
            }
            if (i < paragraphs.size() - 1) sb.append('\n');
        }
        return sb.toString();
    }

    /** Pull the line-number gutter color off the LineNumbers child if it
     *  exists and carries an explicit hex color on its first run.
     *  Returns null when the color matches the default (no override needed). */
    private static String extractLineNumberColor(Map<Integer, ShapeSnapshot> addedBySpid,
            java.util.List<Integer> childSpids) {
        for (int childSpid : childSpids) {
            ShapeSnapshot child = addedBySpid.get(childSpid);
            if (child == null || !"LineNumbers".equals(child.name())) continue;
            if (child.textBody() == null) return null;
            for (com.excudo.core.model.TextParagraph p : child.textBody().getParagraphs()) {
                for (com.excudo.core.model.TextRun r : p.getRuns()) {
                    com.excudo.core.model.TextColor c = r.getColor();
                    if (c != null && !c.isScheme()) {
                        String hex = c.getHexVal();
                        // Default dim gray is 858585; only emit overrides.
                        if (hex != null && !"858585".equalsIgnoreCase(hex)) return hex;
                    }
                    return null;
                }
            }
        }
        return null;
    }

    // ========== Shape adds ==========

    private static void emitShapeAdds(SlideStateDiff diff, int slideNumber,
            CommandScript.Builder builder, Map<Integer, CommandSpec> addShapeBySpid,
            Set<Integer> consumedSpids) {
        for (SlideStateDiff.ShapeAdded added : diff.added()) {
            ShapeSnapshot s = added.shape();
            // Skip shapes already covered by a compound primitive.
            if (consumedSpids.contains(s.spid())) continue;
            // AddShapeSpec carries only plain text (matching AddShapeCommand's
            // surface). If the target TextBody has multi-paragraph / formatted
            // content, a follow-up SetTextSpec with a DAG edge will apply it.
            String plainText = flattenPlain(s.textBody());
            CommandSpec.AddShapeSpec addSpec = new CommandSpec.AddShapeSpec(
                slideNumber, s.type(), s.geometry(),
                plainText, s.name(), s.style(),
                // alignment isn't part of ShapeSnapshot; paragraph-level
                // alignment is captured inside TextBody and applied via
                // SetTextSpec when the body is rich. Add-time alignment
                // only matters for *empty* shapes, which the synthesizer
                // never emits (every ShapeAdded carries the desired body).
                null,
                s.isTextBox(),
                // Thread the source-slide SPID through so the runner can
                // record it in the spidMap when this add executes, which
                // lets downstream specs reference this shape's eventual
                // post-add SPID via rewriting.
                s.spid());
            builder.add(addSpec);
            addShapeBySpid.put(s.spid(), addSpec);

            // Emit SetTextSpec iff the text body carries structure that
            // the plain-text add can't represent: multiple paragraphs,
            // bullets, or formatting runs with properties beyond plain.
            if (hasNonPlainContent(s.textBody())) {
                CommandSpec.SetTextSpec setText = new CommandSpec.SetTextSpec(
                    slideNumber, s.spid(), s.textBody());
                builder.add(setText);
                builder.dependsOn(setText, addSpec);
            }
        }
    }

    // ========== Shape removals ==========

    private static void emitShapeRemovals(SlideStateDiff diff, int slideNumber,
            CommandScript.Builder builder, java.util.List<String> warnings) {
        for (SlideStateDiff.ShapeRemoved removed : diff.removed()) {
            ShapeSnapshot s = removed.shape();
            // Remove is unconditional; the differ already scoped baseline
            // to the slide, so any "removed" shape was present in baseline.
            // Placeholders can also be removed via RemoveShapeCommand -- whether
            // that's "intentional" is a user-level concern, not a synthesizer one.
            builder.add(new CommandSpec.RemoveShapeSpec(slideNumber, s.spid()));
        }
    }

    // ========== Shape modifications ==========

    private static void emitShapeModifications(SlideStateDiff diff, int slideNumber,
            CommandScript.Builder builder, java.util.List<String> warnings) {
        for (SlideStateDiff.ShapeModified mod : diff.modified()) {
            int spid = mod.current().spid();
            for (SlideStateDiff.ShapeField field : mod.changedFields()) {
                switch (field) {
                    case GEOMETRY_POSITION:
                        builder.add(new CommandSpec.MoveSpec(slideNumber, spid,
                            mod.current().geometry().getX(),
                            mod.current().geometry().getY()));
                        break;
                    case GEOMETRY_SIZE:
                        builder.add(new CommandSpec.ResizeSpec(slideNumber, spid,
                            mod.current().geometry().getWidth(),
                            mod.current().geometry().getHeight()));
                        break;
                    case GEOMETRY_ROTATION:
                        builder.add(new CommandSpec.RotateSpec(slideNumber, spid,
                            mod.current().geometry().getRotationDegrees()));
                        break;
                    case STYLE:
                        if (mod.current().style() != null) {
                            builder.add(new CommandSpec.SetShapeStyleSpec(slideNumber, spid,
                                mod.current().style()));
                        } else {
                            warnings.add("SPID " + spid + " style became null (inherit) -- "
                                + "no command to clear an explicit style today; skipped.");
                        }
                        break;
                    case TEXT:
                        if (mod.current().textBody() == null) {
                            warnings.add("SPID " + spid + " textBody became null -- "
                                + "no command to clear text body today; skipped.");
                            break;
                        }
                        // If baseline and current differ ONLY in per-run
                        // formatting (same paragraph structure, same text),
                        // emit per-run SetRunFormatSpecs rather than a
                        // full-body SetTextSpec. Otherwise fall back to
                        // SetTextSpec.
                        java.util.List<CommandSpec> runSpecs = emitRunFormatSpecsIfApplicable(
                            slideNumber, spid, mod.baseline().textBody(), mod.current().textBody());
                        if (runSpecs != null) {
                            for (CommandSpec rs : runSpecs) builder.add(rs);
                        } else {
                            builder.add(new CommandSpec.SetTextSpec(slideNumber, spid,
                                mod.current().textBody()));
                        }
                        break;
                    case NAME:
                        builder.add(new CommandSpec.RenameShapeSpec(slideNumber, spid,
                            mod.current().name() != null ? mod.current().name() : ""));
                        break;
                    case TEXT_BOX_FLAG:
                        builder.add(new CommandSpec.SetTextBoxFlagSpec(slideNumber, spid,
                            mod.current().isTextBox()));
                        break;
                    case PARENT_GROUP:
                        Integer before = mod.baseline().parentGroupSpid();
                        Integer after  = mod.current().parentGroupSpid();
                        if (before == null && after != null) {
                            builder.add(new CommandSpec.AddToGroupSpec(slideNumber, after, spid));
                        } else if (before != null && after == null) {
                            builder.add(new CommandSpec.DetachFromGroupSpec(slideNumber, spid));
                        } else if (before != null && after != null && !before.equals(after)) {
                            // Cross-group move: detach first, then re-add.
                            // v1 limitation: coordinate transform doesn't
                            // compose across both groups' ext/chExt ratios,
                            // so visual position may drift if the two groups
                            // have different scales. Warning flags the risk.
                            builder.add(new CommandSpec.DetachFromGroupSpec(slideNumber, spid));
                            builder.add(new CommandSpec.AddToGroupSpec(slideNumber, after, spid));
                            warnings.add("SPID " + spid + " moved between groups " + before
                                + " -> " + after + ": visual position may drift if the groups "
                                + "have different coordinate-system scales.");
                        }
                        break;
                }
            }
        }
    }

    // ========== TEXT field decomposition ==========

    /**
     * If {@code baseline} and {@code current} TextBodies have the same
     * paragraph structure (same paragraph count, same run count per
     * paragraph, same run.text for every run), emit one
     * {@link CommandSpec.SetRunFormatSpec} for each run whose
     * formatting differs -- minimum-DAG run-level editing. Otherwise
     * returns null, signaling the caller to fall back to a full
     * {@link CommandSpec.SetTextSpec}.
     */
    private static java.util.List<CommandSpec> emitRunFormatSpecsIfApplicable(
            int slideNumber, int spid,
            com.excudo.core.model.TextBody baseline,
            com.excudo.core.model.TextBody current) {
        if (baseline == null || current == null) return null;
        var bp = baseline.getParagraphs();
        var cp = current.getParagraphs();
        if (bp.size() != cp.size()) return null;

        // Body-level properties (anchor, autofit, insets...) must also
        // match; if they differ, use SetTextSpec so those get rewritten.
        if (!StateEquality_sameBodyProps(baseline, current)) return null;

        java.util.List<CommandSpec> specs = new java.util.ArrayList<>();
        for (int pi = 0; pi < bp.size(); pi++) {
            var br = bp.get(pi).getRuns();
            var cr = cp.get(pi).getRuns();
            if (br.size() != cr.size()) return null;
            // Paragraph-level properties (alignment, bullet, level) must
            // also match for the run-only path to be safe.
            if (!samePPr(bp.get(pi), cp.get(pi))) return null;
            for (int ri = 0; ri < br.size(); ri++) {
                var bRun = br.get(ri);
                var cRun = cr.get(ri);
                if (!java.util.Objects.equals(bRun.getText(), cRun.getText())) return null;
                if (sameRunFormat(bRun, cRun)) continue;
                specs.add(new CommandSpec.SetRunFormatSpec(slideNumber, spid, pi, ri, cRun));
            }
        }
        return specs;
    }

    private static boolean StateEquality_sameBodyProps(com.excudo.core.model.TextBody a,
                                                       com.excudo.core.model.TextBody b) {
        var ap = a.getBodyProperties();
        var bp = b.getBodyProperties();
        if (ap == bp) return true;
        if (ap == null || bp == null) return false;
        return java.util.Objects.equals(ap.getVerticalAlignment(), bp.getVerticalAlignment())
            && ap.getAutofit() == bp.getAutofit()
            && java.util.Objects.equals(ap.getLeftInset(), bp.getLeftInset())
            && java.util.Objects.equals(ap.getRightInset(), bp.getRightInset())
            && java.util.Objects.equals(ap.getTopInset(), bp.getTopInset())
            && java.util.Objects.equals(ap.getBottomInset(), bp.getBottomInset())
            && java.util.Objects.equals(ap.getWrap(), bp.getWrap());
    }

    private static boolean samePPr(com.excudo.core.model.TextParagraph a,
                                   com.excudo.core.model.TextParagraph b) {
        return java.util.Objects.equals(a.getAlignment(), b.getAlignment())
            && a.getLevel() == b.getLevel()
            && a.getBulletType() == b.getBulletType()
            && java.util.Objects.equals(a.getBulletChar(), b.getBulletChar());
    }

    private static boolean sameRunFormat(com.excudo.core.model.TextRun a,
                                         com.excudo.core.model.TextRun b) {
        return java.util.Objects.equals(a.getFontFamily(), b.getFontFamily())
            && java.util.Objects.equals(a.getFontSize(), b.getFontSize())
            && java.util.Objects.equals(a.getBold(), b.getBold())
            && java.util.Objects.equals(a.getItalic(), b.getItalic())
            && java.util.Objects.equals(a.getUnderline(), b.getUnderline())
            && sameColor(a.getColor(), b.getColor())
            && sameColor(a.getHighlight(), b.getHighlight());
    }

    private static boolean sameColor(com.excudo.core.model.TextColor a,
                                     com.excudo.core.model.TextColor b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        if (a.isScheme() != b.isScheme()) return false;
        return a.isScheme()
            ? java.util.Objects.equals(a.getSchemeVal(), b.getSchemeVal())
            : java.util.Objects.equals(a.getHexVal(), b.getHexVal());
    }

    // ========== Animation adds ==========

    private static void emitAnimationAdds(SlideStateDiff diff, int slideNumber,
            CommandScript.Builder builder, Map<Integer, CommandSpec> addShapeBySpid) {
        for (SlideStateDiff.AnimationAdded added : diff.animationsAdded()) {
            AnimationBinding b = added.binding();
            CommandSpec.AddAnimationSpec spec = new CommandSpec.AddAnimationSpec(slideNumber, b);
            builder.add(spec);
            // Edge: if the animation targets a newly-added shape, it
            // must run after that shape's AddShapeSpec. Baseline shapes
            // are already present, so no edge needed for them.
            CommandSpec addSpec = addShapeBySpid.get(b.getTargetSpid());
            if (addSpec != null) builder.dependsOn(spec, addSpec);
        }
    }

    // ========== Animation removals (deferred) ==========

    private static void emitAnimationRemovals(SlideStateDiff diff, int slideNumber,
            CommandScript.Builder builder, java.util.List<String> warnings) {
        for (SlideStateDiff.AnimationRemoved removed : diff.animationsRemoved()) {
            AnimationBinding b = removed.binding();
            int nodeId = b.getTimingNodeId();
            if (nodeId <= 0) {
                // Parser failed to capture the cTn id for this binding (an
                // animation authored without an id, or constructed
                // manually from a builder that didn't set one). Warn
                // rather than emit a spec with a bogus id -- a clean
                // signal that the synthesized script will be incomplete.
                warnings.add("Animation removal skipped for target SPID " + b.getTargetSpid()
                    + " (type " + b.getAnimationType()
                    + "): binding has no timingNodeId, removal can't be scoped to a "
                    + "specific animation. Skipped.");
                continue;
            }
            builder.add(new CommandSpec.RemoveAnimationSpec(slideNumber, nodeId));
        }
    }

    // ========== Animation timing updates ==========

    private static void emitAnimationTimingChanges(SlideStateDiff diff, int slideNumber,
            CommandScript.Builder builder, java.util.List<String> warnings) {
        for (SlideStateDiff.AnimationTimingChanged t : diff.animationsTimingChanged()) {
            int nodeId = t.before().getTimingNodeId();
            if (nodeId <= 0) {
                warnings.add("Animation timing change skipped for target SPID "
                    + t.before().getTargetSpid() + ": baseline binding has no timingNodeId, "
                    + "can't scope the in-place update. Falls back to remove+add only if both "
                    + "are expressible, otherwise this change is lost.");
                continue;
            }
            // Only emit fields that actually differ, so the spec is
            // minimal. The command handles nulls as "leave unchanged".
            String newDur = Objects.equals(t.before().getDuration(), t.after().getDuration())
                ? null : t.after().getDuration();
            String newDelay = Objects.equals(t.before().getDelay(), t.after().getDelay())
                ? null : t.after().getDelay();
            builder.add(new CommandSpec.SetAnimationTimingSpec(slideNumber, nodeId, newDur, newDelay));
        }
    }

    // ========== Transition ==========

    private static void emitTransitionChange(SlideStateDiff diff, int slideNumber,
            CommandScript.Builder builder) {
        SlideStateDiff.TransitionChange tc = diff.transitionChange();
        if (tc == null) return;
        if (tc.after() == null) {
            // "after" null means the explicit transition was cleared.
            builder.add(new CommandSpec.ClearTransitionSpec(slideNumber));
        } else {
            builder.add(new CommandSpec.SetTransitionSpec(slideNumber,
                tc.after().type(), tc.after().speed(), tc.after().autoAdvanceMs()));
        }
    }

    // ========== Groups ==========

    private static void emitGroupDeltas(SlideStateDiff diff, int slideNumber,
            CommandScript.Builder builder, Map<Integer, CommandSpec> addShapeBySpid,
            Set<Integer> consumedSpids) {
        for (SlideStateDiff.GroupDelta gd : diff.groupDeltas()) {
            // Skip the GroupDelta when its group is part of a compound
            // primitive that already emitted its own high-level spec.
            if (consumedSpids.contains(gd.groupSpid())) continue;
            if (gd.groupAdded()) {
                // New group with its initial children. Emit CreateGroupSpec
                // and wire edges to each child's AddShapeSpec (children must
                // exist before they can be grouped).
                CommandSpec.CreateGroupSpec create = new CommandSpec.CreateGroupSpec(
                    slideNumber, gd.childrenAdded(), null);
                builder.add(create);
                for (int childSpid : gd.childrenAdded()) {
                    CommandSpec childAdd = addShapeBySpid.get(childSpid);
                    if (childAdd != null) builder.dependsOn(create, childAdd);
                }
            } else if (gd.groupRemoved()) {
                builder.add(new CommandSpec.UngroupSpec(slideNumber, gd.groupSpid()));
            }
            // Children-only changes on an existing group: deferred
            // (add-to-group / detach-from-group are Tier 5).
        }
    }

    // ========== Helpers ==========

    private static String flattenPlain(TextBody body) {
        if (body == null) return "";
        StringBuilder sb = new StringBuilder();
        var paragraphs = body.getParagraphs();
        for (int i = 0; i < paragraphs.size(); i++) {
            if (i > 0) sb.append('\n');
            for (var run : paragraphs.get(i).getRuns()) {
                String t = run.getText();
                if (t != null) sb.append(t);
            }
        }
        return sb.toString();
    }

    /**
     * True iff the body has properties the add-shape plain-text path
     * can't represent: more than one paragraph, any bullet, any
     * non-default run formatting, or non-default body properties.
     */
    private static boolean hasNonPlainContent(TextBody body) {
        if (body == null) return false;
        var paragraphs = body.getParagraphs();
        if (paragraphs.size() > 1) return true;
        for (var p : paragraphs) {
            if (p.getBulletType() != null
                    && p.getBulletType() != com.excudo.core.model.BulletType.NONE) return true;
            if (p.getLevel() > 0) return true;
            if (p.getAlignment() != null && !p.getAlignment().isEmpty()
                    && !"l".equals(p.getAlignment())) return true;
            for (var r : p.getRuns()) {
                if (Boolean.TRUE.equals(r.getBold())) return true;
                if (Boolean.TRUE.equals(r.getItalic())) return true;
                if (r.getUnderline() != null && !r.getUnderline().isEmpty()) return true;
                if (r.getColor() != null) return true;
                if (r.getHighlight() != null) return true;
                if (r.getFontSize() != null) return true;
                if (r.getFontFamily() != null) return true;
            }
        }
        return false;
    }

    // Silence unused-keys warning while the map matters structurally.
    @SuppressWarnings("unused")
    private static final Set<Integer> SPID_TYPE_SANITY = new HashSet<>();
}
