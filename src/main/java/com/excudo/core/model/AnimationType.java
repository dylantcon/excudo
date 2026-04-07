package com.excudo.core.model;

/**
 * Comprehensive enum for PowerPoint animation types with proper filter mappings.
 * 
 * Based on real PPTX examples and Microsoft OpenXML documentation, this enum
 * provides intelligent mapping from user-friendly animation names to the exact
 * filter strings PowerPoint expects in p:animEffect elements.
 * 
 * Each animation type includes LLM-friendly descriptions to enable intelligent
 * animation sequence construction from natural language prompts.
 */
public enum AnimationType {
    
    // ========== ENTRANCE ANIMATIONS ==========
    
    /**
     * Instant appear effect - most basic entrance animation
     * PowerPoint filter: "" (uses visibility set only, no p:animEffect)
     * LLM Context: Instant appearance without visual transition.
     * Use when: Simple reveal without animation effect.
     */
    APPEAR("appear", "", "Instant appear effect", 
           "Instant appearance without visual transition. Most basic entrance animation. Use when content should appear immediately without any visual effect."),
    
    /**
     * Simple fade in effect - most basic entrance animation
     * PowerPoint filter: "fade"
     * LLM Context: Universal, subtle entrance. Good for any content that should appear smoothly without distraction.
     * Use when: Introducing text, revealing bullet points progressively, or when content should appear naturally.
     */
    FADE("fade", "fade", "Basic fade in/out effect", 
         "Universal subtle entrance/exit. Smooth, non-distracting appearance. Perfect for progressive text reveals, professional presentations, or when content should appear naturally without drawing attention to the animation itself."),
    
    /**
     * Fly in from the left side of the slide
     * PowerPoint filter: "fly(fromLeft)" 
     * LLM Context: Dynamic entrance from off-screen. Creates sense of content moving into position.
     * Use when: Introducing new topics, bullet points from a list, or content that represents progression/flow.
     */
    FLY_IN_LEFT("fly-in-left", "fly(fromLeft)", "Shape flies in from the left edge",
                "Dynamic entrance from left edge. Suggests forward progression, left-to-right reading flow, or chronological advancement. Excellent for bullet points, timeline items, or content representing progress. More engaging than fade but still professional."),
    
    /**
     * Fly in from the right side of the slide
     * PowerPoint filter: "fly(fromRight)"
     * LLM Context: Reverse flow direction, can suggest opposing viewpoints or alternative paths.
     * Use when: Presenting counterpoints, alternative solutions, or content that contrasts with left-side content.
     */
    FLY_IN_RIGHT("fly-in-right", "fly(fromRight)", "Shape flies in from the right edge",
                 "Dynamic entrance from right edge. Suggests reverse flow, alternative perspectives, or opposing viewpoints. Useful for contrasting information, counterarguments, or content that represents different approaches. Creates visual balance when used with left-flying elements."),
    
    /**
     * Fly in from the top of the slide
     * PowerPoint filter: "fly(fromTop)"
     * LLM Context: Suggests hierarchy, importance, or content 'dropping down' from above.
     * Use when: Introducing titles, headers, important announcements, or hierarchical content.
     */
    FLY_IN_TOP("fly-in-top", "fly(fromTop)", "Shape flies in from the top edge",
               "Dynamic entrance from top edge. Suggests hierarchy, importance, authority, or content 'descending' from above. Perfect for titles, headers, important announcements, executive summaries, or key findings. Creates strong visual impact."),
    
    /**
     * Fly in from the bottom of the slide
     * PowerPoint filter: "fly(fromBottom)"
     * LLM Context: Suggests foundation, support, or content 'rising up' from below.
     * Use when: Introducing supporting details, foundational concepts, or conclusions.
     */
    FLY_IN_BOTTOM("fly-in-bottom", "fly(fromBottom)", "Shape flies in from the bottom edge",
                  "Dynamic entrance from bottom edge. Suggests foundation, growth, emerging ideas, or supporting details 'rising up'. Excellent for supporting evidence, foundational concepts, conclusions, or growth metrics. Creates upward momentum feeling."),
    
    /**
     * Fly in from the top-left corner of the slide
     * PowerPoint uses coordinate animations, not filter strings - preset subtype 9
     * LLM Context: Diagonal entrance combining hierarchy and progression elements.
     * Use when: Introducing content that represents both importance and forward movement.
     */
    FLY_IN_TOP_LEFT("fly-in-top-left", null, "Shape flies in from the top-left corner",
                    "Dynamic diagonal entrance from top-left corner. Combines hierarchical importance with forward progression. Perfect for introducing key points in sequential presentations, important agenda items, or content representing both authority and advancement."),
    
    /**
     * Fly in from the top-right corner of the slide
     * PowerPoint uses coordinate animations, not filter strings - preset subtype 3
     * LLM Context: Diagonal entrance suggesting alternative high-priority perspectives.
     * Use when: Presenting important alternative viewpoints or contrasting high-level concepts.
     */
    FLY_IN_TOP_RIGHT("fly-in-top-right", null, "Shape flies in from the top-right corner",
                     "Dynamic diagonal entrance from top-right corner. Suggests important alternative perspectives or opposing high-level viewpoints. Excellent for contrasting strategies, alternative solutions, or competing priorities with hierarchical importance."),
    
    /**
     * Fly in from the bottom-left corner of the slide
     * PowerPoint uses coordinate animations, not filter strings - preset subtype 12
     * LLM Context: Diagonal entrance combining foundational support with forward progression.
     * Use when: Introducing supporting details that build toward main points.
     */
    FLY_IN_BOTTOM_LEFT("fly-in-bottom-left", null, "Shape flies in from the bottom-left corner",
                       "Dynamic diagonal entrance from bottom-left corner. Combines foundational support with forward progression. Perfect for supporting evidence that builds toward main conclusions, preliminary findings leading to results, or foundational concepts advancing to complex ideas."),
    
    /**
     * Fly in from the bottom-right corner of the slide
     * PowerPoint uses coordinate animations, not filter strings - preset subtype 6
     * LLM Context: Diagonal entrance suggesting alternative foundational perspectives.
     * Use when: Presenting alternative supporting evidence or contrasting foundational concepts.
     */
    FLY_IN_BOTTOM_RIGHT("fly-in-bottom-right", null, "Shape flies in from the bottom-right corner",
                        "Dynamic diagonal entrance from bottom-right corner. Suggests alternative foundational perspectives or contrasting supporting evidence. Excellent for alternative approaches, competing methodologies, or contrasting supporting data that challenges main assumptions."),
    
    /**
     * Horizontal blinds effect - reveals content like opening blinds
     * PowerPoint filter: "blinds(horizontal)"
     * LLM Context: Mechanical, structured reveal. Suggests unveiling or disclosure.
     * Use when: Revealing data, uncovering insights, or showing before/after comparisons.
     */
    BLINDS_HORIZONTAL("blinds-horizontal", "blinds(horizontal)", "Horizontal blinds reveal effect",
                      "Mechanical horizontal reveal like opening window blinds. Suggests unveiling, disclosure, or systematic revelation. Perfect for revealing data, research findings, survey results, or confidential information. Creates anticipation and structured discovery."),
    
    /**
     * Vertical blinds effect
     * PowerPoint filter: "blinds(vertical)"
     * LLM Context: Similar to horizontal but with vertical orientation for different visual flow.
     */
    BLINDS_VERTICAL("blinds-vertical", "blinds(vertical)", "Vertical blinds reveal effect",
                    "Mechanical vertical reveal like opening curtains. Suggests grand unveiling or theatrical disclosure. More dramatic than horizontal blinds. Use for major announcements, big reveals, product launches, or significant findings."),
    
    /**
     * Wipe effect from left to right
     * PowerPoint filter: "wipe(fromLeft)"
     * LLM Context: Clean, directional reveal following reading patterns.
     * Use when: Progressive disclosure of information, step-by-step processes.
     */
    WIPE_LEFT("wipe-left", "wipe(fromLeft)", "Wipe reveal from left to right",
              "Clean directional reveal following natural reading pattern. Suggests progression, completion, or step-by-step process. Excellent for revealing text line by line, showing process steps, or progressive disclosure of complex information."),
    
    /**
     * Wipe effect from right to left
     * PowerPoint filter: "wipe(fromRight)"
     */
    WIPE_RIGHT("wipe-right", "wipe(fromRight)", "Wipe reveal from right to left",
               "Clean directional reveal against reading pattern. Creates reverse emphasis or alternative perspective. Use for showing corrections, revisions, alternative approaches, or when contrasting with left-to-right content."),
    
    /**
     * Wipe effect from top to bottom
     * PowerPoint filter: "wipe(fromTop)"
     */
    WIPE_DOWN("wipe-down", "wipe(fromTop)", "Wipe reveal from top to bottom",
              "Clean downward reveal suggesting hierarchy or cascading information. Perfect for organizational charts, waterfall processes, hierarchical data, or content that flows from general to specific."),
    
    /**
     * Wipe effect from bottom to top
     * PowerPoint filter: "wipe(fromBottom)"
     */
    WIPE_UP("wipe-up", "wipe(fromBottom)", "Wipe reveal from bottom to top",
            "Clean upward reveal suggesting growth or building foundation. Excellent for growth charts, building concepts layer by layer, or revealing conclusions that build from supporting evidence."),
    
    /**
     * Split effect - reveals from center outward horizontally
     * PowerPoint filter: "split(horizontal)"
     */
    SPLIT_HORIZONTAL("split-horizontal", "barn(inHorizontal)", "Split reveal horizontally from center",
                     "Dramatic center-out horizontal reveal. Creates strong visual impact and suggests breaking barriers or opening to reveal important content. Use for major announcements, key findings, or central concepts."),
    
    /**
     * Split effect - reveals from center outward vertically
     * PowerPoint filter: "split(vertical)"
     */
    SPLIT_VERTICAL("split-vertical", "barn(inVertical)", "Split reveal vertically from center",
                   "Dramatic center-out vertical reveal. Even more impactful than horizontal split. Perfect for biggest announcements, breakthrough discoveries, or the most important slide content."),
    
    /**
     * Checkerboard pattern reveal effect
     * PowerPoint filter: "checkerboard(across)"
     */
    CHECKERBOARD("checkerboard", "checkerboard(across)", "Checkerboard pattern reveal",
                 "Playful, dynamic pattern reveal. Creates visual interest and energy. Use for creative content, brainstorming results, diverse options, or when presentation needs visual variety. Avoid in formal business contexts."),
    
    /**
     * Box effect - reveals from center outward
     * PowerPoint filter: "box(in)"
     */
    BOX_IN("box-in", "box(in)", "Box reveal from center outward",
           "Geometric center-out reveal suggesting focus or zooming in on important content. Perfect for highlighting key metrics, focusing attention on central concepts, or revealing the 'core' of complex information."),
    
    /**
     * Diamond effect - reveals in diamond pattern
     * PowerPoint filter: "diamond(in)"
     */
    DIAMOND_IN("diamond-in", "diamond(in)", "Diamond pattern reveal",
               "Elegant geometric reveal with sophisticated feel. More refined than basic box. Use for premium content, luxury presentations, or when sophisticated visual appeal is important."),
    
    /**
     * Wheel effect with 4 spokes
     * PowerPoint filter: "wheel(4)"
     */
    WHEEL_4("wheel-4", "wheel(4)", "4-spoke wheel reveal effect",
            "Rotational reveal suggesting cycles, processes, or systematic approaches. Perfect for cyclical content, process flows, seasonal data, or quarterly reviews. The 4-spoke version works well for quarterly or seasonal concepts."),
    
    /**
     * Wheel effect with 8 spokes
     * PowerPoint filter: "wheel(8)"
     */
    WHEEL_8("wheel-8", "wheel(8)", "8-spoke wheel reveal effect",
            "More complex rotational reveal with finer segments. Use for detailed processes, multi-faceted analyses, or complex systems with many components."),
    
    /**
     * Random bars effect
     * PowerPoint filter: "randomBars(horizontal)"
     */
    RANDOM_BARS_HORIZONTAL("random-bars-horizontal", "randombar(horizontal)", "Random horizontal bars reveal",
                           "Energetic, unpredictable reveal pattern. Creates excitement and visual interest. Use for creative content, brainstorming outcomes, innovative solutions, or when energy and dynamism are desired."),
    
    /**
     * Random bars effect (vertical)
     * PowerPoint filter: "randomBars(vertical)"
     */
    RANDOM_BARS_VERTICAL("random-bars-vertical", "randombar(vertical)", "Random vertical bars reveal",
                         "Vertical variant of random bars. Similar energy but different visual flow. Use when vertical emphasis or orientation is preferred."),
    
    /**
     * Dissolve effect - pixels appear randomly
     * PowerPoint filter: "dissolve"
     */
    DISSOLVE("dissolve", "dissolve", "Random pixel dissolve effect",
             "Organic, gradual appearance like a photograph developing. Creates sophisticated, subtle entrance with visual interest. Perfect for images, photographs, artistic content, or when sophisticated subtlety is needed."),
    
    /**
     * Grow and turn effect
     * PowerPoint filter: "growTurn"
     */
    GROW_TURN("grow-turn", null, "Grow and rotate entrance",
              "Dynamic combination of scaling and rotation. Creates strong visual impact suggesting transformation or evolution. Use for new products, transformed processes, innovative solutions, or breakthrough concepts."),
    
    /**
     * Zoom effect - grows from center
     * PowerPoint filter: "zoom"
     */
    ZOOM("zoom", "zoom", "Zoom in from center",
         "Dramatic scaling entrance suggesting importance, focus, or magnification. Perfect for highlighting key points, important metrics, major announcements, or when content needs strong emphasis and attention."),
    
    /**
     * Swivel effect - rotates into view
     * PowerPoint filter: "swivel"
     */
    SWIVEL("swivel", null, "Swivel rotation entrance",
           "Smooth rotational entrance suggesting pivot, change of direction, or new perspective. Excellent for presenting alternative approaches, strategic pivots, new directions, or perspective shifts."),
    
    // ========== EMPHASIS ANIMATIONS ==========
    
    /**
     * Pulse effect - briefly changes size
     * PowerPoint filter: "pulse"
     */
    PULSE("pulse", "pulse", "Pulse size change emphasis",
          "Rhythmic size change drawing attention without permanent modification. Perfect for highlighting important points during presentation, emphasizing key metrics, or drawing attention to critical information temporarily."),
    
    /**
     * Color pulse effect
     * PowerPoint filter: "colorPulse"
     */
    COLOR_PULSE("color-pulse", "colorPulse", "Color change pulse emphasis",
                "Brief color change for emphasis while maintaining original appearance. Use to highlight important data points, emphasize key terms, or draw attention to critical information without permanent visual change."),

    /**
     * Font color change effect - permanent color change
     * PowerPoint presetID=19. Same OOXML structure as COLOR_PULSE but with fill="hold" and no autoRev.
     */
    FONT_COLOR_CHANGE("font-color-change", "fontColorChange", "Permanent font color change emphasis",
                      "Permanently changes text color. Unlike color pulse which flashes temporarily, this creates a lasting color change. Use when text should remain the new color after the animation completes."),

    /**
     * Teeter effect - rocks back and forth
     * PowerPoint filter: "teeter"
     */
    TEETER("teeter", "teeter", "Rocking teeter emphasis",
           "Playful rocking motion suggesting instability, uncertainty, or playful attention-getting. Use for uncertain data, preliminary results, or in casual presentations where playful emphasis is appropriate."),
    
    /**
     * Spin effect - rotates around center
     * PowerPoint filter: "spin"
     */
    SPIN("spin", "spin", "Spin rotation emphasis",
         "Full rotation emphasis suggesting completion, cycles, or dynamic action. Perfect for emphasizing cyclical processes, completed projects, or dynamic concepts. More energetic than pulse."),
    
    /**
     * Grow/shrink effect
     * PowerPoint filter: "growShrink"
     */
    GROW_SHRINK("grow-shrink", "growShrink", "Grow and shrink emphasis",
                "Size-based emphasis returning to original scale. Creates strong attention-grabbing effect. Use for very important points, critical warnings, or when maximum emphasis is needed."),
    
    /**
     * Desaturate effect - removes color saturation
     * PowerPoint filter: "desaturate"
     */
    DESATURATE("desaturate", "desaturate", "Color desaturation emphasis",
               "Removes color saturation temporarily for subtle emphasis. Professional way to de-emphasize content or show contrast. Use for comparative analysis or when subduing certain elements."),
    
    /**
     * Darken effect
     * PowerPoint filter: "darken"
     */
    DARKEN("darken", "darken", "Darken color emphasis",
           "Temporarily darkens content for emphasis. Can suggest seriousness, problems, or areas needing attention. Use for highlighting issues, concerns, or serious topics."),
    
    /**
     * Lighten effect
     * PowerPoint filter: "lighten"
     */
    LIGHTEN("lighten", "lighten", "Lighten color emphasis",
            "Temporarily lightens content for emphasis. Suggests positivity, solutions, or highlighting. Use for positive outcomes, solutions, or optimistic content."),
    
    /**
     * Transparency effect
     * PowerPoint filter: "transparency"
     */
    TRANSPARENCY("transparency", "transparency", "Transparency change emphasis",
                 "Temporarily changes opacity for subtle emphasis. Professional way to create depth or layered visual effects. Use for background information or layered content presentation."),
    
    // ========== MOTION PATH ANIMATIONS ==========
    
    /**
     * Linear motion path - move in straight line
     * Special case: uses p:animMotion instead of p:animEffect
     */
    MOTION_LINEAR("motion-linear", null, "Linear motion path animation",
                  "Straight-line movement across slide. Perfect for showing direct relationships, cause-and-effect, process flows, or physical movement. Combines well with other animations for complex choreography."),
    
    /**
     * Arc motion path - move in curved arc
     * Special case: uses p:animMotion instead of p:animEffect
     */
    MOTION_ARC("motion-arc", null, "Arc motion path animation",
               "Curved movement suggesting natural motion or orbital relationships. Use for showing indirect relationships, natural processes, or when organic motion is preferred over mechanical movement."),
    
    /**
     * Custom motion path - user-defined path
     * Special case: uses p:animMotion instead of p:animEffect
     */
    MOTION_CUSTOM("motion-custom", null, "Custom motion path animation",
                  "User-defined movement path for complex choreography. Allows precise control over object movement for sophisticated presentations, detailed process flows, or artistic effects.");
    
    // ========== ENUM IMPLEMENTATION ==========
    
    private final String userFriendlyName;
    private final String powerPointFilter;
    private final String description;
    private final String llmGuidance;
    
    /**
     * Create an animation type with its PowerPoint filter mapping and LLM guidance.
     * 
     * @param userFriendlyName The name users will use in commands (e.g., "fade", "fly-in-left")
     * @param powerPointFilter The exact filter string PowerPoint expects (e.g., "fade", "fly(fromLeft)")
     * @param description Human-readable description of the effect
     * @param llmGuidance Detailed guidance for LLM to understand when and how to use this animation
     */
    AnimationType(String userFriendlyName, String powerPointFilter, String description, String llmGuidance) {
        this.userFriendlyName = userFriendlyName;
        this.powerPointFilter = powerPointFilter;
        this.description = description;
        this.llmGuidance = llmGuidance;
    }
    
    /**
     * Get the user-friendly name for this animation type.
     * This is what users type in commands.
     * 
     * @return user-friendly name (e.g., "fly-in-left")
     */
    public String getUserFriendlyName() {
        return userFriendlyName;
    }
    
    /**
     * Get the PowerPoint filter string for this animation type.
     * This is what goes in the filter attribute of p:animEffect.
     * 
     * @return PowerPoint filter string (e.g., "fly(fromLeft)"), or null for motion animations
     */
    public String getPowerPointFilter() {
        return powerPointFilter;
    }
    
    /**
     * Get a human-readable description of this animation type.
     * 
     * @return description of the animation effect
     */
    public String getDescription() {
        return description;
    }
    
    /**
     * Get detailed LLM guidance for this animation type.
     * This helps the LLM understand when and how to use this animation effectively.
     * 
     * @return detailed guidance for LLM decision-making
     */
    public String getLlmGuidance() {
        return llmGuidance;
    }
    
    /**
     * Check if this animation type uses motion paths (p:animMotion).
     * Motion path animations are handled differently from filter-based animations.
     * 
     * @return true if this is a motion path animation
     */
    public boolean isMotionPath() {
        switch (this) {
            case MOTION_LINEAR:
            case MOTION_ARC:
            case MOTION_CUSTOM:
                return true;
            default:
                return false;
        }
    }
    
    /**
     * Check if this animation type uses standard filters (p:animEffect).
     * 
     * @return true if this uses p:animEffect with filter attribute
     */
    public boolean isFilterBased() {
        return powerPointFilter != null && !powerPointFilter.isEmpty();
    }
    
    /**
     * Check if this animation type uses only visibility changes (p:set only).
     * These animations don't use p:animEffect, p:animMotion, or other complex elements.
     * 
     * @return true if this is a visibility-only animation
     */
    public boolean isVisibilityOnly() {
        return powerPointFilter != null && powerPointFilter.isEmpty();
    }
    
    /**
     * Get the PowerPoint preset ID for this animation type.
     * These IDs match Microsoft's internal animation type identifiers.
     * 
     * @return PowerPoint preset ID
     */
    public int getPresetId() {
        switch (this) {
            // Appear animations
            case APPEAR:
                return 1; // Appear preset
            case FADE:
                return 10; // Fade preset
                
            // Fly animations
            case FLY_IN_LEFT:
            case FLY_IN_RIGHT:
            case FLY_IN_TOP:
            case FLY_IN_BOTTOM:
                return 2; // Fly preset
                
            // Wipe animations
            case WIPE_LEFT:
            case WIPE_RIGHT:
            case WIPE_DOWN:
            case WIPE_UP:
                return 22; // Wipe preset
                
            // Split animations
            case SPLIT_HORIZONTAL:
            case SPLIT_VERTICAL:
                return 16; // Split preset
                
            // Blinds animations
            case BLINDS_HORIZONTAL:
            case BLINDS_VERTICAL:
                return 3; // Blinds preset
                
            // Box animations (oracle: presetID 4)
            case BOX_IN:
                return 4; // Box preset (corrected from 5 to match oracle)

            // Diamond animations (oracle: presetID 8)
            case DIAMOND_IN:
                return 8; // Diamond preset (corrected from 6 to match oracle)

            // Wheel animations (oracle: presetID 21)
            case WHEEL_4:
            case WHEEL_8:
                return 21; // Wheel preset (corrected from 4 to match oracle)
                
            // Checkerboard
            case CHECKERBOARD:
                return 9; // Checkerboard preset
                
            // Random bars (oracle: presetID 14)
            case RANDOM_BARS_HORIZONTAL:
            case RANDOM_BARS_VERTICAL:
                return 14; // Random bars preset (corrected from 8 to match oracle)
                
            // Dissolve
            case DISSOLVE:
                return 12; // Dissolve preset
                
            // Grow and turn (oracle: presetID 31)
            case GROW_TURN:
                return 31; // Grow turn preset (corrected from 13 to match oracle)
                
            // Zoom - CORRECTED: Native PowerPoint uses presetID="53", not "14"
            case ZOOM:
                return 53; // Zoom preset (corrected from 14 to match native PowerPoint)
                
            // Swivel (oracle: presetID 45)
            case SWIVEL:
                return 45; // Swivel preset (corrected from 15 to match oracle)
                
            // Emphasis animations (oracle-corrected presetIDs)
            case PULSE:
                return 26; // Pulse preset (corrected from 18 to match oracle)
            case COLOR_PULSE:
                return 27; // Color pulse preset (corrected from 19 to match oracle)
            case FONT_COLOR_CHANGE:
                return 19; // Font color change preset
            case TEETER:
                return 32; // Teeter preset (corrected from 20 to match oracle)
            case SPIN:
                return 8; // Spin preset (corrected from 21 to match oracle)
            case GROW_SHRINK:
                return 6; // Grow shrink preset (corrected from 17 to match oracle)
            case DESATURATE:
                return 25; // Desaturate preset (corrected from 23 to match oracle)
            case DARKEN:
                return 24; // Darken preset (correct per oracle)
            case LIGHTEN:
                return 30; // Lighten preset (corrected from 25 to match oracle)
            case TRANSPARENCY:
                return 9; // Transparency preset (corrected from 26 to match oracle)
            // Motion path animations (oracle-verified presetIDs)
            case MOTION_LINEAR:
                return 42; // Line Down preset (oracle slide 129)
            case MOTION_ARC:
                return 37; // Arc Down preset (oracle slide 133)
            case MOTION_CUSTOM:
                return 0; // Custom path (oracle slide 154)
                
            // Default case - use Appear as safe fallback
            default:
                return 1; // Appear preset as fallback
        }
    }
    
    /**
     * Get the PowerPoint preset subtype for this animation type.
     * Subtypes specify direction or variation within a preset type.
     * 
     * @return PowerPoint preset subtype
     */
    public int getPresetSubtype() {
        switch (this) {
            // Basic animations without direction
            case APPEAR:
            case FADE:
                return 0; // No subtype
                
            // Fly animations with direction subtypes (verified from native PowerPoint timing dumps)
            case FLY_IN_LEFT:
                return 8; // Left direction
            case FLY_IN_RIGHT:
                return 2; // Right direction  
            case FLY_IN_TOP:
                return 1; // Top direction
            case FLY_IN_BOTTOM:
                return 4; // Bottom direction
            case FLY_IN_TOP_LEFT:
                return 9; // Top-left corner
            case FLY_IN_TOP_RIGHT:
                return 3; // Top-right corner
            case FLY_IN_BOTTOM_LEFT:
                return 12; // Bottom-left corner
            case FLY_IN_BOTTOM_RIGHT:
                return 6; // Bottom-right corner
                
            // Wipe animations with direction subtypes
            case WIPE_LEFT:
                return 8; // Left direction
            case WIPE_RIGHT:
                return 2; // Right direction
            case WIPE_DOWN:
                return 4; // Down direction
            case WIPE_UP:
                return 1; // Up direction
                
            // Split animations (oracle-corrected subtypes)
            case SPLIT_HORIZONTAL:
                return 26; // Horizontal in (oracle confirms 26 for inH)
            case SPLIT_VERTICAL:
                return 21; // Vertical in (corrected from 25 to match oracle inV)
                
            // Blinds animations
            case BLINDS_HORIZONTAL:
                return 1; // Horizontal
            case BLINDS_VERTICAL:
                return 2; // Vertical
                
            // Box and Diamond (oracle: subtype 16 for "in" direction)
            case BOX_IN:
            case DIAMOND_IN:
                return 16; // In direction (corrected from 0 to match oracle)
                
            // Wheel animations
            case WHEEL_4:
                return 4; // 4 spokes
            case WHEEL_8:
                return 8; // 8 spokes
                
            // Checkerboard
            case CHECKERBOARD:
                return 1; // Across variant
                
            // Random bars (oracle-corrected subtypes)
            case RANDOM_BARS_HORIZONTAL:
                return 10; // Horizontal (corrected from 1 to match oracle)
            case RANDOM_BARS_VERTICAL:
                return 5; // Vertical (corrected from 2 to match oracle)
                
            // Zoom animations with slide center subtype (verified from native PowerPoint timing dumps)
            case ZOOM:
                return 528; // Slide center zoom (matches native PowerPoint slide_042_Zoom_In_Slide_Center.xml)
                
            // No subtype for these animations
            case DISSOLVE:
            case GROW_TURN:
            case SWIVEL:
            case PULSE:
            case COLOR_PULSE:
            case FONT_COLOR_CHANGE:
            case TEETER:
            case SPIN:
            case GROW_SHRINK:
            case DESATURATE:
            case DARKEN:
            case LIGHTEN:
            case TRANSPARENCY:
            case MOTION_LINEAR:
            case MOTION_ARC:
            case MOTION_CUSTOM:
                return 0; // No subtype
                
            default:
                return 0; // Safe fallback
        }
    }
    
    /**
     * Check if this animation type is suitable for entrance effects.
     * 
     * @return true if this can be used as an entrance animation
     */
    public boolean canBeEntrance() {
        // Motion paths and most filter effects can be entrance animations
        // Only pure emphasis effects cannot be entrance
        return !isEmphasis();
    }
    
    /**
     * Check if this animation type is suitable for exit effects.
     * 
     * @return true if this can be used as an exit animation
     */
    public boolean canBeExit() {
        // Most effects can be exit animations, but emphasis effects cannot
        return !isEmphasis();
    }
    
    /**
     * Check if this animation type is an emphasis effect.
     * 
     * @return true if this is an emphasis animation
     */
    public boolean isEmphasis() {
        switch (this) {
            case PULSE:
            case COLOR_PULSE:
            case FONT_COLOR_CHANGE:
            case TEETER:
            case SPIN:
            case GROW_SHRINK:
            case DESATURATE:
            case DARKEN:
            case LIGHTEN:
            case TRANSPARENCY:
                return true;
            default:
                return false;
        }
    }
    
    /**
     * Parse a user-friendly animation type name to the corresponding enum value.
     * This method is case-insensitive and supports various name formats.
     * 
     * @param typeName the animation type name from user input
     * @return the corresponding AnimationType, or FADE as default
     */
    public static AnimationType parseType(String typeName) {
        if (typeName == null || typeName.trim().isEmpty()) {
            return FADE; // Safe default
        }
        
        String normalized = typeName.toLowerCase().trim();
        
        // Try exact match first
        for (AnimationType type : values()) {
            if (type.getUserFriendlyName().equals(normalized)) {
                return type;
            }
        }
        
        // Try alternative name patterns
        switch (normalized) {
            case "appear":
                return APPEAR;
            case "fade":
                return FADE;
            case "fly":
            case "flyin":
            case "fly-in":
                return FLY_IN_LEFT; // Default direction
            case "flyinleft":
            case "fly_in_left":
                return FLY_IN_LEFT;
            case "flyinright":
            case "fly_in_right":
                return FLY_IN_RIGHT;
            case "flyintop":
            case "fly_in_top":
                return FLY_IN_TOP;
            case "flyinbottom":
            case "fly_in_bottom":
                return FLY_IN_BOTTOM;
            case "flyintopleft":
            case "fly_in_top_left":
            case "fly-in-top-left":
                return FLY_IN_TOP_LEFT;
            case "flyintopright":
            case "fly_in_top_right":
            case "fly-in-top-right":
                return FLY_IN_TOP_RIGHT;
            case "flyinbottomleft":
            case "fly_in_bottom_left":
            case "fly-in-bottom-left":
                return FLY_IN_BOTTOM_LEFT;
            case "flyinbottomright":
            case "fly_in_bottom_right":
            case "fly-in-bottom-right":
                return FLY_IN_BOTTOM_RIGHT;
            case "blinds":
                return BLINDS_HORIZONTAL; // Default orientation
            case "wipe":
                return WIPE_LEFT; // Default direction
            case "split":
                return SPLIT_HORIZONTAL; // Default orientation
            case "box":
                return BOX_IN;
            case "diamond":
                return DIAMOND_IN;
            case "wheel":
                return WHEEL_4; // Default spoke count
            case "pulse":
                return PULSE;
            case "fontcolorchange":
            case "font_color_change":
                return FONT_COLOR_CHANGE;
            case "spin":
                return SPIN;
            case "zoom":
                return ZOOM;
            case "dissolve":
                return DISSOLVE;
            case "motion":
            case "path":
            case "custom":
            case "motion-path":
            case "motioncustom":
            case "motion_custom":
                return MOTION_CUSTOM; // LLM-facing default motion type
            case "motionlinear":
            case "motion_linear":
                return MOTION_LINEAR;
            case "motionarc":
            case "motion_arc":
                return MOTION_ARC;
            default:
                return FADE; // Safe fallback
        }
    }
    
    /**
     * Get all available animation type names for help/autocomplete.
     * 
     * @return array of all user-friendly animation names
     */
    public static String[] getAllTypeNames() {
        AnimationType[] types = values();
        String[] names = new String[types.length];
        for (int i = 0; i < types.length; i++) {
            names[i] = types[i].getUserFriendlyName();
        }
        return names;
    }
    
    /**
     * Get all entrance animation types.
     * 
     * @return array of animation types suitable for entrance effects
     */
    public static AnimationType[] getEntranceTypes() {
        return java.util.Arrays.stream(values())
                .filter(AnimationType::canBeEntrance)
                .toArray(AnimationType[]::new);
    }
    
    /**
     * Get all exit animation types.
     * 
     * @return array of animation types suitable for exit effects
     */
    public static AnimationType[] getExitTypes() {
        return java.util.Arrays.stream(values())
                .filter(AnimationType::canBeExit)
                .toArray(AnimationType[]::new);
    }
    
    /**
     * Get all emphasis animation types.
     * 
     * @return array of emphasis animation types
     */
    public static AnimationType[] getEmphasisTypes() {
        return java.util.Arrays.stream(values())
                .filter(AnimationType::isEmphasis)
                .toArray(AnimationType[]::new);
    }
    
    /**
     * Get LLM-friendly summary of all animation types for context injection.
     * This provides the LLM with comprehensive understanding of available animations.
     * 
     * @return formatted string describing all animation types for LLM context
     */
    public static String getLlmAnimationGuide() {
        StringBuilder guide = new StringBuilder();
        guide.append("Available PowerPoint Animation Types:\n\n");
        
        guide.append("ENTRANCE ANIMATIONS (for revealing content):\n");
        for (AnimationType type : getEntranceTypes()) {
            guide.append(String.format("- %s: %s\n", type.getUserFriendlyName(), type.getLlmGuidance()));
        }
        
        guide.append("\nEMPHASIS ANIMATIONS (for highlighting existing content):\n");
        for (AnimationType type : getEmphasisTypes()) {
            guide.append(String.format("- %s: %s\n", type.getUserFriendlyName(), type.getLlmGuidance()));
        }
        
        guide.append("\nEXIT ANIMATIONS (for removing content):\n");
        guide.append("Most entrance animations can also be used as exit animations by changing the direction parameter.\n");
        
        return guide.toString();
    }
}