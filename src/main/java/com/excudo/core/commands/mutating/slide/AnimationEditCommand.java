package com.excudo.core.commands.mutating.slide;

import com.excudo.core.commands.Command;
import com.excudo.core.commands.CommandExecutionException;

import com.excudo.core.orchestration.PPTXOrchestrator;
import com.excudo.core.results.ExecutionResult;
import com.excudo.core.model.AnimationBinding;
import com.excudo.core.model.AnimationType;
import com.excudo.core.utils.ComponentLogger;
import com.excudo.core.utils.Logger;

import com.excudo.utils.FuzzyMatcher;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * GoF Command for adding animations to shapes.
 * 
 * This command allows adding entrance/exit/emphasis animations to shapes with
 * undo capability by removing the added animation.
 */
public class AnimationEditCommand implements Command {

    private static final ComponentLogger logger = Logger.llm();

    private static final List<String> CANONICAL_TRIGGERS = Arrays.asList("on-click", "with-previous", "after-previous");

    private static final Map<String, String> ALIAS_MAP;
    static {
        ALIAS_MAP = new LinkedHashMap<>();
        ALIAS_MAP.put("onclick", "on-click");
        ALIAS_MAP.put("onclck", "on-click");
        ALIAS_MAP.put("click", "on-click");
        ALIAS_MAP.put("clck", "on-click");
        ALIAS_MAP.put("withprevious", "with-previous");
        ALIAS_MAP.put("withprev", "with-previous");
        ALIAS_MAP.put("with", "with-previous");
        ALIAS_MAP.put("afterprevious", "after-previous");
        ALIAS_MAP.put("afterprev", "after-previous");
        ALIAS_MAP.put("after", "after-previous");
    }
    
    private final int slideNumber;
    private final int spid;
    private final String animationType;
    private final String direction;
    private final String trigger;
    private final String animationGroup;
    private final Map<String, String> effectParams;
    private final Integer paragraphStart;
    private final Integer paragraphEnd;
    private final PPTXOrchestrator orchestrator;
    private final com.excudo.xml.writers.animations.GroupIdManager groupIdManager;
    private boolean executed = false;
    private String addedAnimationId = null;

    /** DOM id of the factory-created cTn that serves as the handle for
     *  {@code removeAnimation} on undo. Captured in {@link #execute()} by
     *  diffing the set of {@code <p:cTn>} ids present before vs. after
     *  injection and picking the new one carrying a {@code presetID}
     *  attribute (the signal that identifies the animation preset root,
     *  not the intermediate container or bookkeeping nodes). */
    private Integer undoTimingNodeId = null;

    /**
     * Create an AnimationEditCommand with animation group and effectParams.
     *
     * @param slideNumber the slide number containing the shape
     * @param spid the SPID of the shape to animate
     * @param animationType the type of animation (fade, fly, etc.)
     * @param direction the animation direction (in, out, emphasis)
     * @param trigger the animation trigger (click, with_previous, after_previous)
     * @param animationGroup the animation group for coordination
     * @param orchestrator the PPTX orchestrator for execution
     * @param groupIdManager optional group ID manager
     * @param effectParams effect-specific parameters (rotation, scale, color, etc.)
     */
    public AnimationEditCommand(int slideNumber, int spid, String animationType, String direction,
                               String trigger, String animationGroup, PPTXOrchestrator orchestrator,
                               com.excudo.xml.writers.animations.GroupIdManager groupIdManager,
                               Map<String, String> effectParams,
                               Integer paragraphStart, Integer paragraphEnd) {
        if (orchestrator == null) {
            throw new IllegalArgumentException("PPTXOrchestrator cannot be null");
        }
        if (animationType == null || animationType.trim().isEmpty()) {
            throw new IllegalArgumentException("Animation type cannot be null or empty");
        }
        if (spid < 1) {
            throw new IllegalArgumentException("SPID must be positive");
        }
        if (slideNumber < 1) {
            throw new IllegalArgumentException("Slide number must be positive");
        }

        this.slideNumber = slideNumber;
        this.spid = spid;
        this.animationType = animationType.toLowerCase().trim();
        this.direction = direction != null ? direction.toLowerCase().trim() : "in";
        this.trigger = trigger != null ? trigger.toLowerCase().trim() : "click";
        this.animationGroup = animationGroup;
        this.effectParams = effectParams != null ? effectParams : Collections.emptyMap();
        this.paragraphStart = paragraphStart;
        this.paragraphEnd = paragraphEnd;
        this.orchestrator = orchestrator;
        this.groupIdManager = groupIdManager;

        com.excudo.core.utils.Logger.animation().debug("AnimationEditCommand: constructed with trigger='" + this.trigger + "', animationGroup='" + this.animationGroup + "'");
    }

    /**
     * Create an AnimationEditCommand with all params except paragraph targeting.
     */
    public AnimationEditCommand(int slideNumber, int spid, String animationType, String direction,
                               String trigger, String animationGroup, PPTXOrchestrator orchestrator,
                               com.excudo.xml.writers.animations.GroupIdManager groupIdManager,
                               Map<String, String> effectParams) {
        this(slideNumber, spid, animationType, direction, trigger, animationGroup, orchestrator, groupIdManager, effectParams, null, null);
    }

    /**
     * Create an AnimationEditCommand with animation group (no effectParams).
     */
    public AnimationEditCommand(int slideNumber, int spid, String animationType, String direction,
                               String trigger, String animationGroup, PPTXOrchestrator orchestrator,
                               com.excudo.xml.writers.animations.GroupIdManager groupIdManager) {
        this(slideNumber, spid, animationType, direction, trigger, animationGroup, orchestrator, groupIdManager, Collections.emptyMap(), null, null);
    }

    /**
     * Create an AnimationEditCommand without animation group.
     */
    public AnimationEditCommand(int slideNumber, int spid, String animationType,
                              String direction, String trigger, PPTXOrchestrator orchestrator) {
        this(slideNumber, spid, animationType, direction, trigger, deriveAnimationGroup(trigger), orchestrator, null, Collections.emptyMap(), null, null);
    }

    /**
     * Create an AnimationEditCommand directly from a pre-built
     * {@link AnimationBinding}. Used by the synthesizer's
     * {@code AddAnimationSpec} path where the binding has already been
     * composed and doesn't need to be reassembled from broken-down
     * strings. Trigger derivation matches the string-param constructor:
     * {@code clickTrigger} > 0 &rarr; on-click,
     * {@code clickTrigger} == 0 &rarr; with-previous,
     * {@code clickTrigger} == -1 &rarr; after-previous.
     */
    public static AnimationEditCommand fromBinding(int slideNumber, AnimationBinding binding,
            PPTXOrchestrator orchestrator,
            com.excudo.xml.writers.animations.GroupIdManager groupIdManager) {
        if (binding == null) {
            throw new IllegalArgumentException("AnimationBinding cannot be null");
        }
        String type = binding.getAnimationType().getUserFriendlyName();
        String direction = binding.isEmphasisAnimation() ? "emphasis"
            : (binding.isExitAnimation() ? "out" : "in");
        int ct = binding.getClickTrigger();
        String trigger = ct == 0 ? "with-previous" : (ct < 0 ? "after-previous" : "on-click");
        String animGroup = binding.getAnimationGroup() != null ? binding.getAnimationGroup() : trigger;
        Map<String, String> params = binding.getEffectParams() != null
            ? binding.getEffectParams() : Collections.emptyMap();
        Integer pStart = binding.getParagraphStart();
        Integer pEnd   = binding.getParagraphEnd();
        return new AnimationEditCommand(slideNumber, binding.getTargetSpid(), type, direction,
            trigger, animGroup, orchestrator, groupIdManager, params, pStart, pEnd);
    }
    
    /**
     * Derive the animation group from the trigger value.
     * Maps CLI trigger values to proper animation group values.
     *
     * @param trigger the trigger value from CLI
     * @return the corresponding animation group
     */
    private static String deriveAnimationGroup(String trigger) {
        return resolveCanonicalTrigger(trigger);
    }
    
    /**
     * Execute the animation addition command.
     * 
     * @throws CommandExecutionException if the operation fails
     */
    @Override
    public void execute() {
        if (executed) {
            throw new CommandExecutionException(getDescription(), "execute", "Command has already been executed");
        }
        
        try {
            logger.debug("Adding animation to slide " + slideNumber + ", SPID " + spid + 
                        ", type: " + animationType + ", direction: " + direction + ", trigger: " + trigger);
            
            // Parse trigger using improved parsing logic
            int triggerValue = parseAnimationTrigger(trigger);
            
            // Build AnimationBinding using builder pattern
            AnimationBinding.Builder builder = AnimationBinding.builder()
                .target(spid)
                .type(animationType)
                .clickTrigger(triggerValue)
                .durationMs(500); // 500ms default
            
            // Set animation direction/type
            switch (direction) {
                case "out":
                case "exit":
                    builder.exit();
                    break;
                case "emphasis":
                case "effect":
                    builder.emphasis();
                    break;
                case "in":
                case "entrance":
                default:
                    builder.entrance();
                    break;
            }
            
            // Set animation group if provided
            if (animationGroup != null && !animationGroup.trim().isEmpty()) {
                builder.animationGroup(animationGroup);
                logger.debug("DEBUG AnimationEditCommand: Set animationGroup on builder: '" + animationGroup + "'");
            } else {
                logger.debug("DEBUG AnimationEditCommand: No animationGroup provided (null or empty)");
            }

            // Forward effectParams into the binding
            for (Map.Entry<String, String> entry : effectParams.entrySet()) {
                builder.effectParam(entry.getKey(), entry.getValue());
            }

            // Extract and apply motionPath from effectParams to the builder's dedicated field
            String motionPathValue = effectParams.get(AnimationBinding.PARAM_MOTION_PATH);
            if (motionPathValue != null && !motionPathValue.isBlank()) {
                builder.motionPath(motionPathValue);
            }

            // Extract and apply accel/decel from effectParams
            String accelValue = effectParams.get("accel");
            String decelValue = effectParams.get("decel");
            if (accelValue != null || decelValue != null) {
                int accel = parseIntOrDefault(accelValue, 0);
                int decel = parseIntOrDefault(decelValue, 0);
                if (accel + decel > 100) {
                    throw new CommandExecutionException(getDescription(), "execute",
                        "accel(" + accel + ") + decel(" + decel + ") exceeds 100%");
                }
                builder.easing(accel, decel);
            }

            // Set paragraph-level targeting if specified
            if (paragraphStart != null && paragraphEnd != null) {
                builder.paragraphRange(paragraphStart, paragraphEnd);
            }

            AnimationBinding binding = builder.build();
            com.excudo.core.utils.Logger.animation().debug("AnimationEditCommand: Built binding with animationGroup: '" + binding.getAnimationGroup() + "'");

            // Snapshot the set of <p:cTn id=...> ids present BEFORE the
            // injection so we can identify the new preset-root cTn
            // afterwards. This is the handle used for undo; the tracking
            // string from addAnimation is opaque and can't drive removal.
            java.util.Set<Integer> preIds = captureTimingNodeIds(slideNumber);

            // Call the modern orchestrator method with session GroupIdManager
            ExecutionResult<String> result = orchestrator.addAnimation(slideNumber, binding, groupIdManager);

            if (result.isSuccess()) {
                addedAnimationId = result.getData().orElse(null);
                undoTimingNodeId = findNewPresetCTnId(slideNumber, preIds);
                executed = true;
                logger.debug("Successfully added animation with ID: " + addedAnimationId
                    + " (undo handle cTn id=" + undoTimingNodeId + ")");
            } else {
                logger.error("Animation addition failed: " + result.getMessage());
                throw new CommandExecutionException(
                    getDescription(), 
                    "execute", 
                    "Failed to add animation: " + result.getMessage()
                );
            }
            
        } catch (CommandExecutionException e) {
            throw e;
        } catch (Exception e) {
            throw new CommandExecutionException(
                getDescription(), 
                "execute", 
                "Failed to add animation: " + e.getMessage(),
                e
            );
        }
    }
    
    /**
     * Undo the animation addition by removing the added animation.
     * 
     * @throws CommandExecutionException if the undo operation fails
     */
    @Override
    public void undo() {
        if (!executed) {
            throw new CommandExecutionException(getDescription(), "undo", "Command has not been executed");
        }

        if (!canUndo()) {
            throw new CommandExecutionException(getDescription(), "undo", "Command cannot be undone");
        }

        try {
            ExecutionResult<Void> result = orchestrator.removeAnimation(slideNumber, undoTimingNodeId);
            if (!result.isSuccess()) {
                throw new CommandExecutionException(getDescription(), "undo",
                    "removeAnimation failed: " + result.getMessage());
            }
            logger.debug("Undid animation {} on slide {} (cTn id={})",
                addedAnimationId, slideNumber, undoTimingNodeId);

            executed = false;
            addedAnimationId = null;
            undoTimingNodeId = null;

        } catch (CommandExecutionException e) {
            throw e;
        } catch (Exception e) {
            throw new CommandExecutionException(
                getDescription(),
                "undo",
                "Failed to undo animation addition: " + e.getMessage(),
                e
            );
        }
    }

    /**
     * Check if this command can be undone.
     * Animation addition is undoable once the preset-root cTn id has
     * been captured from the post-injection DOM diff.
     *
     * @return true if the command can be undone
     */
    @Override
    public boolean canUndo() {
        return executed && undoTimingNodeId != null;
    }

    /** Collect every {@code <p:cTn id="N"/>} id currently present in the
     *  slide's timing tree. Used as a pre-injection baseline so the
     *  single new preset-root cTn can be identified after injection. */
    private java.util.Set<Integer> captureTimingNodeIds(int slide) {
        java.util.Set<Integer> ids = new java.util.HashSet<>();
        try {
            org.w3c.dom.Document dom = getSlideDom(slide);
            if (dom == null) return ids;
            org.w3c.dom.NodeList all = dom.getElementsByTagNameNS(
                com.excudo.core.utils.XMLConstants.Namespaces.PML,
                com.excudo.core.utils.XMLConstants.Tags.Timing.C_TN);
            for (int i = 0; i < all.getLength(); i++) {
                org.w3c.dom.Element e = (org.w3c.dom.Element) all.item(i);
                String idStr = e.getAttribute(com.excudo.core.utils.XMLConstants.Attrs.Timing.ID);
                if (idStr != null && !idStr.isEmpty()) {
                    try { ids.add(Integer.parseInt(idStr)); } catch (NumberFormatException ignored) {}
                }
            }
        } catch (Exception e) {
            logger.debug("captureTimingNodeIds failed on slide " + slide + ": " + e.getMessage());
        }
        return ids;
    }

    /** Find the single cTn id that is (a) present in the slide's timing
     *  tree now, (b) was not present before injection, and (c) carries a
     *  {@code presetID} attribute (distinguishes the animation preset
     *  root from the intermediate container and bookkeeping cTn nodes).
     *  Returns {@code null} if no such node is found -- caller falls back
     *  to a no-op undo and sets {@link #canUndo()} to false. */
    private Integer findNewPresetCTnId(int slide, java.util.Set<Integer> preIds) {
        try {
            org.w3c.dom.Document dom = getSlideDom(slide);
            if (dom == null) return null;
            org.w3c.dom.NodeList all = dom.getElementsByTagNameNS(
                com.excudo.core.utils.XMLConstants.Namespaces.PML,
                com.excudo.core.utils.XMLConstants.Tags.Timing.C_TN);
            for (int i = 0; i < all.getLength(); i++) {
                org.w3c.dom.Element e = (org.w3c.dom.Element) all.item(i);
                String idStr = e.getAttribute(com.excudo.core.utils.XMLConstants.Attrs.Timing.ID);
                if (idStr == null || idStr.isEmpty()) continue;
                int id;
                try { id = Integer.parseInt(idStr); } catch (NumberFormatException ignored) { continue; }
                if (preIds.contains(id)) continue;
                if (!e.hasAttribute(com.excudo.core.utils.XMLConstants.Attrs.Timing.PRESET_ID)) continue;
                return id;
            }
        } catch (Exception e) {
            logger.debug("findNewPresetCTnId failed on slide " + slide + ": " + e.getMessage());
        }
        return null;
    }

    /** Fetch the current slide DOM through the orchestrator's document
     *  handle. Null if the orchestrator has no context or the slide does
     *  not exist in memory. */
    private org.w3c.dom.Document getSlideDom(int slide) {
        var ctxOpt = orchestrator.getContext();
        if (ctxOpt.isEmpty()) return null;
        com.excudo.core.model.PPTXDocument doc = ctxOpt.get().getDocument();
        if (doc == null) return null;
        return doc.getSlideDocument(slide);
    }
    
    /**
     * Get the description of this command.
     * 
     * @return description of the animation addition operation
     */
    @Override
    public String getDescription() {
        return String.format("Add %s animation (%s %s) to SPID %d on slide %d with trigger: %s", 
                           animationType, direction != null ? direction : "default", 
                           animationType, spid, slideNumber, trigger != null ? trigger : "click");
    }
    
    /**
     * Check if this command has been executed.
     * 
     * @return true if execute() has been called successfully
     */
    @Override
    public boolean isExecuted() {
        return executed;
    }
    
    private static int parseIntOrDefault(String value, int defaultValue) {
        if (value == null || value.isBlank()) return defaultValue;
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * Parse animation trigger string to click sequence number.
     *
     * @param trigger the trigger string
     * @return click sequence number (1-based, 0=with-previous, -1=after-previous)
     */
    private int parseAnimationTrigger(String trigger) {
        String canonical = resolveCanonicalTrigger(trigger);

        switch (canonical) {
            case "with-previous":
                return 0;
            case "after-previous":
                return -1;
            case "on-click":
            default:
                break;
        }

        // Try to parse as click number from the raw input
        if (trigger != null) {
            String digits = trigger.replaceAll("[^0-9]", "");
            if (!digits.isEmpty()) {
                try {
                    int clickNum = Integer.parseInt(digits);
                    return Math.max(1, clickNum);
                } catch (NumberFormatException e) {
                    // fall through
                }
            }
        }

        return 1;
    }

    /**
     * Resolve a raw trigger string to one of the three canonical trigger values:
     * "on-click", "with-previous", or "after-previous".
     *
     * Uses exact alias lookup first, then Levenshtein fuzzy matching (max distance 3).
     * Defaults to "on-click" with a log warning if no match is found.
     *
     * @param trigger the raw trigger string
     * @return one of the canonical trigger strings
     */
    static String resolveCanonicalTrigger(String trigger) {
        if (trigger == null || trigger.trim().isEmpty()) {
            return "on-click";
        }

        String stripped = trigger.toLowerCase().trim().replaceAll("[^a-z]", "");

        // Check exact canonical match (stripped forms)
        for (String canonical : CANONICAL_TRIGGERS) {
            if (canonical.replaceAll("[^a-z]", "").equals(stripped)) {
                return canonical;
            }
        }

        // Check alias map
        String alias = ALIAS_MAP.get(stripped);
        if (alias != null) {
            return alias;
        }

        // Fuzzy match against stripped canonical forms + aliases
        Map<String, String> fuzzyPool = new LinkedHashMap<>();
        for (String canonical : CANONICAL_TRIGGERS) {
            fuzzyPool.put(canonical.replaceAll("[^a-z]", ""), canonical);
        }
        for (Map.Entry<String, String> entry : ALIAS_MAP.entrySet()) {
            fuzzyPool.put(entry.getKey(), entry.getValue());
        }

        String closestKey = FuzzyMatcher.findClosestMatch(stripped, fuzzyPool.keySet(), 3);
        if (closestKey != null) {
            return fuzzyPool.get(closestKey);
        }

        logger.warn("Unrecognized trigger '" + trigger + "', defaulting to 'on-click'");
        return "on-click";
    }
    
    /**
     * Get the slide number.
     * 
     * @return the slide number
     */
    public int getSlideNumber() {
        return slideNumber;
    }
    
    /**
     * Get the SPID.
     * 
     * @return the shape SPID
     */
    public int getSpid() {
        return spid;
    }
    
    /**
     * Get the animation type.
     * 
     * @return the animation type
     */
    public String getAnimationType() {
        return animationType;
    }
    
    /**
     * Get the animation direction.
     * 
     * @return the animation direction
     */
    public String getDirection() {
        return direction;
    }
    
    /**
     * Get the animation trigger.
     * 
     * @return the animation trigger
     */
    public String getTrigger() {
        return trigger;
    }
    
    /**
     * Get the animation group.
     * 
     * @return the animation group
     */
    public String getAnimationGroup() {
        return animationGroup;
    }
    
    /**
     * Get the added animation ID (available after execution).
     * 
     * @return the animation ID, or null if not executed
     */
    public String getAddedAnimationId() {
        return addedAnimationId;
    }
}