package com.excudo.xml.writers.animations;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import com.excudo.core.model.AnimationType;
import com.excudo.core.model.AnimationBinding;
import com.excudo.core.model.ShapeGeometry;
import com.excudo.core.utils.XMLConstants;

import java.util.List;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

/**
 * Abstract base implementation of AnimationWriter using Template Method pattern.
 * Provides common OOXML timing structure creation and delegates animation-specific logic
 * to concrete subclasses.
 * 
 * Based on timing dump analysis, all 154 animations use identical timing container structure:
 * - 4×p:par elements (parallel containers)
 * - 1×p:seq element (sequential container)
 * 
 * This class implements the invariant parts of animation creation:
 * - Timing container hierarchy (4×p:par + 1×p:seq)
 * - Target element structure (p:tgtEl + p:spTgt + p:txEl)
 * - Build list entry generation
 * 
 * Subclasses implement the variant parts:
 * - Animation element creation (p:set, p:anim, p:animEffect, etc.)
 * - Animation-specific parameters
 * - Supported animation types
 * 
 * Refactored to implement AnimationWriter interface following SRP compliance
 * from code review feedback for clean separation of read/write concerns.
 */
public abstract class AbstractAnimationFactory implements com.excudo.core.animations.AnimationWriter {
    
    protected static final String PRESENTATION_NS = XMLConstants.PRESENTATION_NS;
    protected static final String DRAWING_NS = XMLConstants.DRAWING_NS;
    
    /**
     * Group ID manager for proper sequential allocation.
     * Injected by SlideXMLWriter to ensure slide-scoped group ID management.
     */
    protected GroupIdManager groupIdManager;
    
    /**
     * Cache for animation group IDs to prevent double allocation.
     * Maps AnimationBinding to assigned group ID to ensure consistent IDs
     * across createTimingContainer() and createBuildListEntry() calls.
     */
    private final java.util.Map<AnimationBinding, Integer> groupIdCache = new java.util.concurrent.ConcurrentHashMap<>();
    
    /**
     * Template method for creating timing container.
     * Follows the OOXML structure discovered in timing dumps:
     * p:par → p:cTn → p:stCondLst + p:childTnLst → animation elements
     */
    @Override
    public final Element createTimingContainer(Document document, AnimationBinding binding) {
        // Create main timing container (p:par)
        Element par = document.createElementNS(PRESENTATION_NS, "p:par");
        
        // Create timing node (p:cTn)
        Element cTn = document.createElementNS(PRESENTATION_NS, "p:cTn");
        
        // Set timing attributes based on animation type and binding
        AnimationType animationType = binding.getAnimationType();
        cTn.setAttribute("id", String.valueOf(getNextTimingNodeId()));
        cTn.setAttribute("presetID", String.valueOf(animationType.getPresetId()));
        cTn.setAttribute("presetClass", getPresetClass(binding.getTransition()));
        cTn.setAttribute("presetSubtype", String.valueOf(animationType.getPresetSubtype()));
        String outerFill = getOuterFill(binding);
        if (outerFill != null) {
            cTn.setAttribute("fill", outerFill);
        }
        // grpId is omitted only for explicit per-paragraph animations
        // (no bldP entry; grpId requires bldLst match).
        // Emphasis animations DO have grpId per oracle (slides 89, 90, 115-118).
        if (!binding.isParagraphLevelAnimation()) {
            cTn.setAttribute("grpId", String.valueOf(getAnimationGroupId(binding)));
        }
        cTn.setAttribute("nodeType", determineNodeType(binding));

        // Allow subclasses to add extra attributes (e.g., accel/decel for motion paths)
        addExtraTimingAttributes(cTn, binding);

        par.appendChild(cTn);
        
        // Create start conditions (p:stCondLst)
        Element stCondLst = document.createElementNS(PRESENTATION_NS, "p:stCondLst");
        cTn.appendChild(stCondLst);
        
        Element cond = document.createElementNS(PRESENTATION_NS, "p:cond");
        cond.setAttribute("delay", binding.getDelay());
        stCondLst.appendChild(cond);
        
        // Create child timing list (p:childTnLst)
        Element childTnLst = document.createElementNS(PRESENTATION_NS, "p:childTnLst");
        cTn.appendChild(childTnLst);
        
        return par;
    }
    
    /**
     * Common implementation for target element creation.
     * Structure is identical for all animation types.
     */
    @Override
    public final Element createTargetElement(Document document, AnimationBinding binding) {
        Element tgtEl = document.createElementNS(PRESENTATION_NS, "p:tgtEl");

        Element spTgt = document.createElementNS(PRESENTATION_NS, "p:spTgt");
        spTgt.setAttribute("spid", String.valueOf(binding.getTargetSpid()));
        tgtEl.appendChild(spTgt);

        // MS-OI29500 19.5.16(c): txEl/pRg only for paragraph-level animations.
        // Shape-level animations use bare <p:spTgt spid="X"/>.
        if (binding.isParagraphLevelAnimation()) {
            Element txEl = document.createElementNS(PRESENTATION_NS, "p:txEl");
            spTgt.appendChild(txEl);

            Element pRg = document.createElementNS(PRESENTATION_NS, "p:pRg");
            pRg.setAttribute("st", String.valueOf(binding.getParagraphStart()));
            pRg.setAttribute("end", String.valueOf(binding.getParagraphEnd()));
            txEl.appendChild(pRg);
        }

        return tgtEl;
    }
    
    /**
     * Common implementation for build list entry creation.
     */
    @Override
    public final Element createBuildListEntry(Document document, AnimationBinding binding) {
        Element bldP = document.createElementNS(PRESENTATION_NS, "p:bldP");
        // MS-OI29500 19.5.16(c): build="p" only for paragraph-level animations
        if (binding.isParagraphLevelAnimation()) {
            bldP.setAttribute("build", "p");
        }
        // PowerPoint requires animBg="1" for shapes with a visible fill/line
        // (non-placeholder, non-picture). The flag is set upstream by
        // AnimationOrchestrationManager after inspecting the shape type.
        String animBg = binding.getEffectParam("animBg");
        if ("1".equals(animBg)) {
            bldP.setAttribute("animBg", "1");
        }
        bldP.setAttribute("grpId", String.valueOf(getAnimationGroupId(binding)));
        bldP.setAttribute("spid", String.valueOf(binding.getTargetSpid()));
        return bldP;
    }
    
    /**
     * Create common behavior element (p:cBhvr) with timing and targeting.
     * Used by most animation element types. Includes fill="hold" on inner cTn.
     * For animation effects (p:animEffect) that should NOT have fill="hold", use
     * {@link #createCommonBehaviorNoFill(Document, AnimationBinding, String, String)}.
     */
    protected final Element createCommonBehavior(Document document, AnimationBinding binding, String delay, String duration) {
        return createCommonBehaviorInternal(document, binding, delay, duration, true, false);
    }

    /**
     * Create common behavior element without fill="hold" on inner cTn.
     * Used by p:animEffect elements which omit fill per oracle patterns.
     */
    protected final Element createCommonBehaviorNoFill(Document document, AnimationBinding binding, String delay, String duration) {
        return createCommonBehaviorInternal(document, binding, delay, duration, false, false);
    }

    /**
     * Create common behavior element for visibility p:set elements.
     * Includes fill="hold" AND always emits stCondLst (even for delay="0") per oracle.
     */
    protected final Element createCommonBehaviorForSet(Document document, AnimationBinding binding, String delay, String duration) {
        return createCommonBehaviorInternal(document, binding, delay, duration, true, true);
    }

    private Element createCommonBehaviorInternal(Document document, AnimationBinding binding,
            String delay, String duration, boolean includeFill, boolean alwaysIncludeStCondLst) {
        Element cBhvr = document.createElementNS(PRESENTATION_NS, "p:cBhvr");

        // Create timing node
        Element cTn = document.createElementNS(PRESENTATION_NS, "p:cTn");
        cTn.setAttribute("id", String.valueOf(getNextTimingNodeId()));
        cTn.setAttribute("dur", duration);
        if (includeFill) {
            cTn.setAttribute("fill", "hold");
        }
        cBhvr.appendChild(cTn);

        // Add start conditions -- always for p:set, conditionally for others
        boolean shouldAddStCondLst = alwaysIncludeStCondLst || (delay != null && !delay.equals("0"));
        if (shouldAddStCondLst) {
            Element stCondLst = document.createElementNS(PRESENTATION_NS, "p:stCondLst");
            cTn.appendChild(stCondLst);

            Element cond = document.createElementNS(PRESENTATION_NS, "p:cond");
            cond.setAttribute("delay", delay != null ? delay : "0");
            stCondLst.appendChild(cond);
        }

        // Create target element
        Element tgtEl = createTargetElement(document, binding);
        cBhvr.appendChild(tgtEl);

        return cBhvr;
    }
    
    /**
     * Create timing value element (p:tav) for coordinate animations.
     */
    protected final Element createTimingValue(Document document, String time, String value) {
        Element tav = document.createElementNS(PRESENTATION_NS, "p:tav");
        tav.setAttribute("tm", time);
        
        Element val = document.createElementNS(PRESENTATION_NS, "p:val");
        tav.appendChild(val);
        
        Element strVal = document.createElementNS(PRESENTATION_NS, "p:strVal");
        strVal.setAttribute("val", value);
        val.appendChild(strVal);
        
        return tav;
    }
    
    /**
     * Create p:set element for visibility control.
     * Required for proper entrance/exit animation behavior in PowerPoint.
     * Shared by all animation factories that need visibility toggling.
     *
     * @param document The XML document
     * @param binding The animation binding
     * @param visibility The visibility value ("visible" or "hidden")
     * @return The p:set element
     */
    protected final Element createVisibilitySet(Document document, AnimationBinding binding, String visibility) {
        Element set = document.createElementNS(PRESENTATION_NS, "p:set");

        // Exit animations: delay visibility:hidden until near the end of the effect
        // so the fade/wipe plays visibly before the shape disappears.
        // Oracle pattern: delay = duration - 1  (e.g. 499 for a 500ms animation).
        // Entrance animations: visibility:visible fires immediately (delay=0).
        String delay = "0";
        if ("hidden".equals(visibility)) {
            try {
                int dur = Integer.parseInt(binding.getDuration());
                if (dur > 1) {
                    delay = String.valueOf(dur - 1);
                }
            } catch (NumberFormatException ignored) {
                // Non-numeric duration (e.g. "indefinite") -- keep delay=0
            }
        }

        // Create common behavior with minimal duration for visibility change.
        // Uses ForSet variant which always includes stCondLst per oracle pattern.
        Element cBhvr = createCommonBehaviorForSet(document, binding, delay, "1");
        set.appendChild(cBhvr);

        // Add attribute name list for visibility
        Element attrNameLst = document.createElementNS(PRESENTATION_NS, "p:attrNameLst");
        cBhvr.appendChild(attrNameLst);

        Element attrName = document.createElementNS(PRESENTATION_NS, "p:attrName");
        attrName.setTextContent("style.visibility");
        attrNameLst.appendChild(attrName);

        // Add target value
        Element to = document.createElementNS(PRESENTATION_NS, "p:to");
        set.appendChild(to);

        Element strVal = document.createElementNS(PRESENTATION_NS, "p:strVal");
        strVal.setAttribute("val", visibility);
        to.appendChild(strVal);

        return set;
    }

    // ========== UTILITY METHODS ==========

    /**
     * Calculate half of a duration string (in milliseconds).
     * Used by factories that employ autoRev, which doubles the effective animation time,
     * requiring the inner duration to be halved to achieve the intended total duration.
     *
     * @param duration duration string in milliseconds
     * @return half the duration as a string, or the original if not parseable
     */
    protected static String calculateHalfDuration(String duration) {
        try {
            int ms = Integer.parseInt(duration);
            return String.valueOf(ms / 2);
        } catch (NumberFormatException e) {
            return duration;
        }
    }

    // ========== EXTENSION HOOKS ==========

    /**
     * Determine the fill attribute for the outer cTn (preset-level timing node).
     * Default: "hold" for entrance/exit animations.
     * Subclasses override for emphasis animations that auto-reverse (e.g., COLOR_PULSE
     * uses fill="remove" because its inner elements revert via autoRev="1").
     *
     * Oracle evidence: COLOR_PULSE outer cTn has fill="remove"; TEETER has fill="hold".
     * PowerPoint deletes the entire timing tree if this is wrong.
     */
    protected String getOuterFill(AnimationBinding binding) {
        return "hold";
    }

    /**
     * Hook for subclasses to add extra attributes to the outer cTn element.
     * Called from createTimingContainer() after standard attributes are set.
     * Default: no-op. Override for animation-specific attributes (e.g., accel/decel).
     */
    protected void addExtraTimingAttributes(Element cTn, AnimationBinding binding) {
        // Default: no extra attributes
    }

    // ========== HELPER METHODS ==========

    /**
     * Get preset class based on transition direction.
     * Subclasses can override for non-standard preset classes (e.g., "path" for motion paths).
     */
    protected String getPresetClass(String transition) {
        return switch (transition) {
            case "in" -> "entr";
            case "out" -> "exit";
            default -> "emph";
        };
    }
    
    /**
     * Get animation group ID using proper PowerPoint grouping logic with memoization.
     * 
     * Uses a cache to ensure the same AnimationBinding always gets the same group ID,
     * preventing double allocation when both createTimingContainer() and 
     * createBuildListEntry() are called for the same animation.
     * 
     * Native PowerPoint group ID rules:
     * - on-click: Creates NEW group, increments group ID counter
     * - with-previous: Joins CURRENT group (same grpId as last on-click)
     * - after-previous: Joins CURRENT group (same grpId as last on-click)
     * 
     * @param binding The animation binding containing trigger information
     * @return The group ID for this animation
     */
    private int getAnimationGroupId(AnimationBinding binding) {
        // Check cache first to prevent double allocation
        Integer cachedGroupId = groupIdCache.get(binding);
        if (cachedGroupId != null) {
            return cachedGroupId;
        }

        if (groupIdManager == null) {
            // Fallback for factories not yet configured with group ID manager
            groupIdCache.put(binding, 0);
            return 0;
        }

        String animationGroup = binding.getAnimationGroup();

        int groupId;
        if ("on-click".equals(animationGroup)) {
            groupId = groupIdManager.getNextGroupId();
        } else {
            // with-previous and after-previous join current group.
            // If no group allocated yet (e.g., emphasis after paragraph-level animation
            // which skips grpId), allocate a new one as fallback.
            try {
                groupId = groupIdManager.getCurrentGroupId();
            } catch (IllegalStateException e) {
                groupId = groupIdManager.getNextGroupId();
            }
        }

        // Cache the result for subsequent calls
        groupIdCache.put(binding, groupId);

        return groupId;
    }
    
    /**
     * Set the group ID manager for this factory.
     * Called by SlideXMLWriter during animation processing.
     * 
     * @param groupIdManager the group ID manager to use
     */
    public void setGroupIdManager(GroupIdManager groupIdManager) {
        this.groupIdManager = groupIdManager;
    }
    
    /**
     * Clear the group ID cache.
     * Should be called when switching slides or resetting animation state.
     */
    public void clearGroupIdCache() {
        groupIdCache.clear();
    }
    
    
    /**
     * Determine nodeType based on animation coordination rules.
     */
    private String determineNodeType(AnimationBinding binding) {
        String animationGroup = binding.getAnimationGroup();
        return switch (animationGroup) {
            case "with-previous" -> "withEffect";
            case "after-previous" -> "afterEffect";
            default -> "clickEffect"; // "on-click"
        };
    }
    
    // ========== ABSTRACT METHODS ==========
    
    /**
     * Abstract method for creating animation-specific elements.
     * Subclasses implement this to provide their OOXML element pattern.
     * 
     * @param document The XML document
     * @param binding The animation binding
     * @param geometry The target shape geometry
     * @return List of animation elements specific to this factory type
     */
    @Override
    public abstract List<Element> createAnimationElements(Document document, AnimationBinding binding, ShapeGeometry geometry);
    
    /**
     * Abstract method for supported animation types.
     * Subclasses implement this to declare their capabilities.
     * 
     * @return Array of supported animation types
     */
    @Override
    public abstract AnimationType[] getSupportedAnimationTypes();
    
    /**
     * Abstract method for OOXML pattern description.
     * Subclasses implement this for debugging and validation.
     * 
     * @return Human-readable pattern description
     */
    @Override
    public abstract String getOoxmlPattern();
    
    // ========== TIMING NODE ID GENERATION ==========

    private TimingNodeIdGenerator timingNodeIdGenerator;

    /**
     * Set the timing node ID generator for this factory.
     * Called by AnimationInjector before creating animation elements
     * to ensure IDs are sequential within the slide's timing tree.
     */
    public void setTimingNodeIdGenerator(TimingNodeIdGenerator generator) {
        this.timingNodeIdGenerator = generator;
    }

    /**
     * Generate next timing node ID using the injected generator.
     * Requires setTimingNodeIdGenerator() to have been called first.
     */
    protected int getNextTimingNodeId() {
        if (timingNodeIdGenerator == null) {
            throw new IllegalStateException(
                "TimingNodeIdGenerator not set -- call setTimingNodeIdGenerator() before creating animation elements");
        }
        return timingNodeIdGenerator.getNextId();
    }
    
    /**
     * Default implementation checks if animation type is in supported types.
     * Subclasses can override for more complex logic.
     * 
     * @param animationType The animation type to check
     * @return true if supported
     */
    @Override
    public boolean supportsAnimationType(AnimationType animationType) {
        for (AnimationType supportedType : getSupportedAnimationTypes()) {
            if (supportedType == animationType) {
                return true;
            }
        }
        return false;
    }
    
}