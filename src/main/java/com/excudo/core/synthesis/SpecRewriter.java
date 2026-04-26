package com.excudo.core.synthesis;

import com.excudo.core.model.AnimationBinding;
import com.excudo.core.synthesis.spec.CommandSpec;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Rewrites source-slide SPIDs in a {@link CommandSpec} to the actual
 * SPIDs allocated on the target slide. Applied between each spec
 * execution in {@link ScriptRunner}, so specs that reference a shape
 * which was just added get their SPID references updated before
 * execution.
 *
 * <p>Identity rule: if the spec's SPID / target / child-SPID doesn't
 * appear in the map, the spec is returned unchanged (no allocation).
 * Otherwise a new immutable spec is constructed with the rewritten
 * fields. Records + structural equality make this cheap and safe.
 */
public final class SpecRewriter {

    private SpecRewriter() {}

    /**
     * Rewrite every SPID reference in {@code spec} via {@code spidMap}.
     * Returns {@code spec} unchanged when no rewriting is needed (hot
     * path optimization: the map is empty on the first spec, etc.).
     */
    public static CommandSpec rewrite(CommandSpec spec, Map<Integer, Integer> spidMap) {
        if (spec == null || spidMap == null || spidMap.isEmpty()) return spec;

        return switch (spec) {
            case CommandSpec.AddShapeSpec s      -> s; // creates a SPID, doesn't reference one
            case CommandSpec.RemoveShapeSpec s   -> mapOrSame(s, spidMap,
                                                        id -> new CommandSpec.RemoveShapeSpec(s.slideNumber(), id),
                                                        s.spid());
            case CommandSpec.MoveSpec s          -> mapOrSame(s, spidMap,
                                                        id -> new CommandSpec.MoveSpec(s.slideNumber(), id, s.newX(), s.newY()),
                                                        s.spid());
            case CommandSpec.ResizeSpec s        -> mapOrSame(s, spidMap,
                                                        id -> new CommandSpec.ResizeSpec(s.slideNumber(), id, s.newWidth(), s.newHeight()),
                                                        s.spid());
            case CommandSpec.RotateSpec s        -> mapOrSame(s, spidMap,
                                                        id -> new CommandSpec.RotateSpec(s.slideNumber(), id, s.newRotationDegrees()),
                                                        s.spid());
            case CommandSpec.RenameShapeSpec s   -> mapOrSame(s, spidMap,
                                                        id -> new CommandSpec.RenameShapeSpec(s.slideNumber(), id, s.newName()),
                                                        s.spid());
            case CommandSpec.SetTextBoxFlagSpec s -> mapOrSame(s, spidMap,
                                                        id -> new CommandSpec.SetTextBoxFlagSpec(s.slideNumber(), id, s.flag()),
                                                        s.spid());
            case CommandSpec.SetRunFormatSpec s  -> mapOrSame(s, spidMap,
                                                        id -> new CommandSpec.SetRunFormatSpec(s.slideNumber(), id,
                                                            s.paragraphIdx(), s.runIdx(), s.newRun()),
                                                        s.spid());
            case CommandSpec.SetTextSpec s       -> mapOrSame(s, spidMap,
                                                        id -> new CommandSpec.SetTextSpec(s.slideNumber(), id, s.textBody()),
                                                        s.spid());
            case CommandSpec.SetShapeStyleSpec s -> mapOrSame(s, spidMap,
                                                        id -> new CommandSpec.SetShapeStyleSpec(s.slideNumber(), id, s.style()),
                                                        s.spid());
            case CommandSpec.ReorderSpec s       -> mapOrSame(s, spidMap,
                                                        id -> new CommandSpec.ReorderSpec(s.slideNumber(), id, s.direction()),
                                                        s.spid());
            case CommandSpec.AddAnimationSpec s  -> rewriteAnimation(s, spidMap);
            case CommandSpec.RemoveAnimationSpec s -> s; // references a timing-node id, not a SPID
            case CommandSpec.SetAnimationTimingSpec s -> s; // same: no SPID reference
            case CommandSpec.SetTransitionSpec s -> s;
            case CommandSpec.ClearTransitionSpec s -> s;
            case CommandSpec.CreateGroupSpec s   -> rewriteCreateGroup(s, spidMap);
            case CommandSpec.UngroupSpec s       -> mapOrSame(s, spidMap,
                                                        id -> new CommandSpec.UngroupSpec(s.slideNumber(), id),
                                                        s.groupSpid());
            case CommandSpec.AddToGroupSpec s    -> rewriteAddToGroup(s, spidMap);
            case CommandSpec.DetachFromGroupSpec s -> mapOrSame(s, spidMap,
                                                        id -> new CommandSpec.DetachFromGroupSpec(s.slideNumber(), id),
                                                        s.childSpid());
            case CommandSpec.CreateCodeBoxSpec s -> s; // creates SPIDs, doesn't reference any
            case CommandSpec.CreateDiagramSpec s -> s; // creates SPIDs, doesn't reference any
        };
    }

    /** If {@code oldSpid} is in the map, apply the factory to produce a
     *  new spec with the mapped SPID; otherwise return the original. */
    private static CommandSpec mapOrSame(CommandSpec orig, Map<Integer, Integer> spidMap,
            java.util.function.IntFunction<CommandSpec> factory, int oldSpid) {
        Integer mapped = spidMap.get(oldSpid);
        if (mapped == null) return orig;
        return factory.apply(mapped);
    }

    private static CommandSpec rewriteAnimation(CommandSpec.AddAnimationSpec s,
            Map<Integer, Integer> spidMap) {
        AnimationBinding b = s.binding();
        Integer mappedTarget = spidMap.get(b.getTargetSpid());
        if (mappedTarget == null) return s;
        // Reconstruct the binding with the new target SPID via the
        // Builder surface. The Builder has no copy constructor, so we
        // replay every setter that has a corresponding getter to ensure
        // nothing is silently dropped.
        AnimationBinding.Builder bld = AnimationBinding.builder()
            .target(mappedTarget)
            .type(b.getAnimationType())
            .clickTrigger(b.getClickTrigger());
        if (b.isEntranceAnimation())      bld.entrance();
        else if (b.isExitAnimation())     bld.exit();
        else if (b.isEmphasisAnimation()) bld.emphasis();

        if (b.getDuration() != null) {
            try { bld.durationMs(Integer.parseInt(b.getDuration())); }
            catch (NumberFormatException ignored) { bld.duration(b.getDuration()); }
        }
        if (b.getDelay() != null && !b.getDelay().isBlank()) {
            bld.delay(b.getDelay());
        }
        if (b.getAnimationGroup() != null && !b.getAnimationGroup().isBlank()) {
            bld.animationGroup(b.getAnimationGroup());
        }
        if (b.getMotionPath() != null) bld.motionPath(b.getMotionPath());
        if (b.getAcceleration() != 0 || b.getDeceleration() != 0) {
            bld.easing(b.getAcceleration(), b.getDeceleration());
        }
        if (b.getParagraphStart() != null && b.getParagraphEnd() != null) {
            bld.paragraphRange(b.getParagraphStart(), b.getParagraphEnd());
        }
        if (b.getEffectParams() != null) {
            for (var e : b.getEffectParams().entrySet()) {
                bld.effectParam(e.getKey(), e.getValue());
            }
        }
        return new CommandSpec.AddAnimationSpec(s.slideNumber(), bld.build());
    }

    private static CommandSpec rewriteAddToGroup(CommandSpec.AddToGroupSpec s,
            Map<Integer, Integer> spidMap) {
        Integer mappedGroup = spidMap.get(s.groupSpid());
        Integer mappedChild = spidMap.get(s.childSpid());
        if (mappedGroup == null && mappedChild == null) return s;
        return new CommandSpec.AddToGroupSpec(s.slideNumber(),
            mappedGroup != null ? mappedGroup : s.groupSpid(),
            mappedChild != null ? mappedChild : s.childSpid());
    }

    private static CommandSpec rewriteCreateGroup(CommandSpec.CreateGroupSpec s,
            Map<Integer, Integer> spidMap) {
        List<Integer> newChildren = new ArrayList<>(s.childSpids().size());
        boolean anyChanged = false;
        for (int child : s.childSpids()) {
            Integer mapped = spidMap.get(child);
            if (mapped != null) {
                newChildren.add(mapped);
                anyChanged = true;
            } else {
                newChildren.add(child);
            }
        }
        if (!anyChanged) return s;
        return new CommandSpec.CreateGroupSpec(s.slideNumber(), newChildren, s.groupName());
    }
}
