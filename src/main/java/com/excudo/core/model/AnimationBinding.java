package com.excudo.core.model;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Represents the binding between an animation effect and its target shape or paragraph range.
 *
 * Enhanced with AnimationType enum support, advanced parameters, and builder pattern
 * following GoF design principles. Supports both simple animations and complex
 * choreographed sequences with full PowerPoint compatibility.
 */
public class AnimationBinding {

  // ========== EFFECT PARAMETER KEYS ==========

  public static final String PARAM_ROTATION_DEGREES = "rotationDegrees";
  public static final String PARAM_SCALE_PERCENT = "scalePercent";
  public static final String PARAM_COLOR = "color";
  public static final String PARAM_OPACITY = "opacity";
  public static final String PARAM_INTENSITY = "intensity";
  public static final String PARAM_MOTION_PATH = "motionPath";
  /** Delay in milliseconds before the animation starts. */
  public static final String PARAM_DELAY_MS = "delayMs";
  /** Override duration in milliseconds (default 500). */
  public static final String PARAM_DURATION_MS = "durationMs";
  // ========== OOXML IDENTITY ==========

  /**
   * The cTn id attribute from the OOXML timing tree.
   * Used by remove-animation and update-animation to identify specific animations.
   * -1 indicates not set (e.g., for programmatically created bindings).
   */
  private int timingNodeId = -1;

  // ========== CORE ANIMATION PROPERTIES ==========

  private final int targetSpid;
  private final AnimationType animationType;
  private final String transition; // "in", "out", or "emphasis"
  private final String duration;
  private final String delay;
  
  // ========== TARGETING PROPERTIES ==========
  
  // Paragraph range support - null indicates shape-level targeting
  private final Integer paragraphStart;
  private final Integer paragraphEnd;
  
  // ========== TIMING AND SEQUENCING ==========
  
  /**
   * Click trigger for animation sequencing.
   * 1+ = click number, 0 = with previous, -1 = after previous
   */
  private final int clickTrigger;
  
  /**
   * Animation grouping for coordination.
   * Common values: "on-click", "with-previous", "after-previous"
   */
  private final String animationGroup;
  
  // ========== ADVANCED EFFECTS ==========
  
  /**
   * Motion path for movement animations (PowerPoint path syntax).
   */
  private final String motionPath;
  
  /**
   * Acceleration percentage (0-100) for easing.
   */
  private final int acceleration;
  
  /**
   * Deceleration percentage (0-100) for easing.
   */
  private final int deceleration;
  
  /**
   * Whether animation should auto-reverse.
   */
  private final boolean autoReverse;
  
  /**
   * Number of repetitions (1 = once, -1 = infinite).
   */
  private final int repeatCount;

  /**
   * Effect-specific parameters (rotation degrees, scale %, color, opacity, etc.).
   * Extensible key-value pairs avoiding N specialized fields per animation type.
   */
  private final Map<String, String> effectParams;
  
  // ========== DEFAULT VALUES ==========
  
  private static final String DEFAULT_DURATION = "500";
  private static final String DEFAULT_DELAY = "0";
  private static final int DEFAULT_CLICK_TRIGGER = 1;
  private static final String DEFAULT_ANIMATION_GROUP = "on-click";
  private static final int DEFAULT_ACCELERATION = 0;
  private static final int DEFAULT_DECELERATION = 0;
  private static final boolean DEFAULT_AUTO_REVERSE = false;
  private static final int DEFAULT_REPEAT_COUNT = 1;

  // ========== CONSTRUCTORS ==========

  
  /**
   * Enhanced constructor with AnimationType enum.
   */
  public AnimationBinding(int targetSpid, AnimationType animationType, String transition,
      String duration, String delay) {
    this(targetSpid, animationType, transition, duration, delay, null, null,
         DEFAULT_CLICK_TRIGGER, DEFAULT_ANIMATION_GROUP, null,
         DEFAULT_ACCELERATION, DEFAULT_DECELERATION, DEFAULT_AUTO_REVERSE, DEFAULT_REPEAT_COUNT,
         Collections.emptyMap());
  }

  /**
   * Enhanced constructor with paragraph support.
   */
  public AnimationBinding(int targetSpid, AnimationType animationType, String transition,
      String duration, String delay, Integer paragraphStart, Integer paragraphEnd) {
    this(targetSpid, animationType, transition, duration, delay, paragraphStart, paragraphEnd,
         DEFAULT_CLICK_TRIGGER, DEFAULT_ANIMATION_GROUP, null,
         DEFAULT_ACCELERATION, DEFAULT_DECELERATION, DEFAULT_AUTO_REVERSE, DEFAULT_REPEAT_COUNT,
         Collections.emptyMap());
  }

  /**
   * Full constructor with all parameters (backward compatible -- no effectParams).
   */
  public AnimationBinding(int targetSpid, AnimationType animationType, String transition,
      String duration, String delay, Integer paragraphStart, Integer paragraphEnd,
      int clickTrigger, String animationGroup, String motionPath,
      int acceleration, int deceleration, boolean autoReverse, int repeatCount) {
    this(targetSpid, animationType, transition, duration, delay, paragraphStart, paragraphEnd,
         clickTrigger, animationGroup, motionPath, acceleration, deceleration, autoReverse,
         repeatCount, Collections.emptyMap());
  }

  /**
   * Full constructor with all parameters including effectParams.
   */
  public AnimationBinding(int targetSpid, AnimationType animationType, String transition,
      String duration, String delay, Integer paragraphStart, Integer paragraphEnd,
      int clickTrigger, String animationGroup, String motionPath,
      int acceleration, int deceleration, boolean autoReverse, int repeatCount,
      Map<String, String> effectParams) {
    this.targetSpid = targetSpid;
    this.animationType = animationType;
    this.transition = transition;
    this.duration = duration != null ? duration : DEFAULT_DURATION;
    this.delay = delay != null ? delay : DEFAULT_DELAY;
    this.paragraphStart = paragraphStart;
    this.paragraphEnd = paragraphEnd;
    this.clickTrigger = clickTrigger;
    this.animationGroup = animationGroup != null ? animationGroup : DEFAULT_ANIMATION_GROUP;
    this.motionPath = motionPath;
    int clampedAccel = Math.max(0, Math.min(100, acceleration));
    int clampedDecel = Math.max(0, Math.min(100, deceleration));
    if (clampedAccel + clampedDecel > 100) {
      // ECMA-376: accel + decel must not exceed 100%. Scale proportionally.
      double total = clampedAccel + clampedDecel;
      clampedAccel = (int) Math.round(clampedAccel * 100.0 / total);
      clampedDecel = 100 - clampedAccel;
    }
    this.acceleration = clampedAccel;
    this.deceleration = clampedDecel;
    this.autoReverse = autoReverse;
    this.repeatCount = Math.max(-1, repeatCount);
    this.effectParams = effectParams != null
        ? Collections.unmodifiableMap(new HashMap<>(effectParams))
        : Collections.emptyMap();
  }


  // ========== GETTERS ==========
  
  public int getTimingNodeId() { return timingNodeId; }
  public void setTimingNodeId(int timingNodeId) { this.timingNodeId = timingNodeId; }
  public int getTargetSpid() { return targetSpid; }
  public AnimationType getAnimationType() { return animationType; }
  public String getTransition() { return transition; }
  public String getDuration() { return duration; }
  public String getDelay() { return delay; }
  public Integer getParagraphStart() { return paragraphStart; }
  public Integer getParagraphEnd() { return paragraphEnd; }
  public int getClickTrigger() { return clickTrigger; }
  public String getAnimationGroup() { return animationGroup; }
  public String getMotionPath() { return motionPath; }
  public int getAcceleration() { return acceleration; }
  public int getDeceleration() { return deceleration; }
  public boolean isAutoReverse() { return autoReverse; }
  public int getRepeatCount() { return repeatCount; }
  public Map<String, String> getEffectParams() { return effectParams; }

  /**
   * Get a single effect parameter by key, or null if not set.
   */
  public String getEffectParam(String key) {
    return effectParams.get(key);
  }

  /**
   * Get a single effect parameter by key with a default fallback.
   */
  public String getEffectParam(String key, String defaultValue) {
    return effectParams.getOrDefault(key, defaultValue);
  }

  /**
   * Get PowerPoint filter string for this animation.
   * Backward compatibility method - uses AnimationType to get filter.
   */
  public String getFilter() {
    return animationType.getPowerPointFilter();
  }
  
  /**
   * Get animation type name as string.
   * Backward compatibility method.
   */
  public String getAnimationTypeName() {
    return animationType.getUserFriendlyName();
  }

  // ========== ANIMATION TYPE DETECTION ==========

  public boolean isEntranceAnimation() {
    return "in".equals(transition);
  }

  public boolean isExitAnimation() {
    return "out".equals(transition);
  }
  
  public boolean isEmphasisAnimation() {
    return "emphasis".equals(transition) || animationType.isEmphasis();
  }
  
  public boolean isParagraphLevelAnimation() {
    return paragraphStart != null && paragraphEnd != null;
  }
  
  public boolean isShapeLevelAnimation() {
    return paragraphStart == null && paragraphEnd == null;
  }
  
  public boolean hasMotionPath() {
    return motionPath != null && !motionPath.trim().isEmpty();
  }
  
  public boolean hasCustomEasing() {
    return acceleration > 0 || deceleration > 0;
  }
  
  public boolean repeats() {
    return repeatCount > 1 || repeatCount == -1;
  }
  
  public boolean isWithPrevious() {
    return clickTrigger == 0;
  }
  
  public boolean isAfterPrevious() {
    return clickTrigger == -1;
  }

  // ========== BUILDER PATTERN ==========
  
  /**
   * Create a builder for constructing AnimationBinding instances.
   * 
   * @return new builder instance
   */
  public static Builder builder() {
    return new Builder();
  }
  
  /**
   * Create a builder starting with existing binding.
   * 
   * @param existing existing binding to copy
   * @return new builder with copied values
   */
  public static Builder builder(AnimationBinding existing) {
    return new Builder(existing);
  }
  
  /**
   * Builder class for constructing AnimationBinding with fluent interface.
   * Follows GoF Builder pattern for complex object construction.
   */
  public static class Builder {
    private int timingNodeId = -1;
    private int targetSpid;
    private AnimationType animationType = AnimationType.FADE;
    private String transition = "in";
    private String duration = DEFAULT_DURATION;
    private String delay = DEFAULT_DELAY;
    private Integer paragraphStart = null;
    private Integer paragraphEnd = null;
    private int clickTrigger = DEFAULT_CLICK_TRIGGER;
    private String animationGroup = DEFAULT_ANIMATION_GROUP;
    private String motionPath = null;
    private int acceleration = DEFAULT_ACCELERATION;
    private int deceleration = DEFAULT_DECELERATION;
    private boolean autoReverse = DEFAULT_AUTO_REVERSE;
    private int repeatCount = DEFAULT_REPEAT_COUNT;
    private String previousAnimationDuration = null;
    private Map<String, String> effectParams = new HashMap<>();

    public Builder() {}

    public Builder(AnimationBinding existing) {
      this.timingNodeId = existing.timingNodeId;
      this.targetSpid = existing.targetSpid;
      this.animationType = existing.animationType;
      this.transition = existing.transition;
      this.duration = existing.duration;
      this.delay = existing.delay;
      this.paragraphStart = existing.paragraphStart;
      this.paragraphEnd = existing.paragraphEnd;
      this.clickTrigger = existing.clickTrigger;
      this.animationGroup = existing.animationGroup;
      this.motionPath = existing.motionPath;
      this.acceleration = existing.acceleration;
      this.deceleration = existing.deceleration;
      this.autoReverse = existing.autoReverse;
      this.repeatCount = existing.repeatCount;
      this.previousAnimationDuration = null; // Not preserved from existing object
      this.effectParams = new HashMap<>(existing.effectParams);
    }
    
    public Builder target(int spid) {
      this.targetSpid = spid;
      return this;
    }

    public Builder timingNodeId(int id) {
      this.timingNodeId = id;
      return this;
    }

    public Builder type(AnimationType type) {
      this.animationType = type;
      return this;
    }
    
    public Builder type(String typeName) {
      this.animationType = AnimationType.parseType(typeName);
      return this;
    }
    
    public Builder entrance() {
      this.transition = "in";
      return this;
    }
    
    public Builder exit() {
      this.transition = "out";
      return this;
    }
    
    public Builder emphasis() {
      this.transition = "emphasis";
      return this;
    }
    
    public Builder duration(String duration) {
      this.duration = duration;
      return this;
    }
    
    public Builder durationMs(int milliseconds) {
      this.duration = String.valueOf(milliseconds);
      return this;
    }
    
    public Builder durationSeconds(double seconds) {
      this.duration = String.valueOf((int)(seconds * 1000));
      return this;
    }
    
    public Builder delay(String delay) {
      this.delay = delay;
      return this;
    }
    
    public Builder delayMs(int milliseconds) {
      this.delay = String.valueOf(milliseconds);
      return this;
    }
    
    public Builder clickTrigger(int trigger) {
      this.clickTrigger = trigger;
      return this;
    }
    
    public Builder withPrevious() {
      this.clickTrigger = 0;
      this.animationGroup = "with-previous";
      return this;
    }
    
    public Builder afterPrevious() {
      this.clickTrigger = -1;
      this.animationGroup = "after-previous";
      return this;
    }
    
    public Builder afterPrevious(String previousDuration) {
      this.clickTrigger = -1;
      this.animationGroup = "after-previous";
      this.previousAnimationDuration = previousDuration;
      return this;
    }
    
    public Builder paragraphRange(int start, int end) {
      this.paragraphStart = start;
      this.paragraphEnd = end;
      return this;
    }
    
    public Builder paragraph(int index) {
      this.paragraphStart = index;
      this.paragraphEnd = index;
      return this;
    }
    
    public Builder motionPath(String path) {
      this.motionPath = path;
      return this;
    }
    
    public Builder linearMotion(double startX, double startY, double endX, double endY) {
      this.motionPath = String.format("M %.3f %.3f L %.3f %.3f E", startX, startY, endX, endY);
      return this;
    }
    
    public Builder easing(int accel, int decel) {
      if (accel + decel > 100) {
        throw new IllegalArgumentException(
            "accel + decel must not exceed 100 (got " + accel + " + " + decel + " = " + (accel + decel) + ")");
      }
      this.acceleration = accel;
      this.deceleration = decel;
      return this;
    }
    
    public Builder autoReverse(boolean reverse) {
      this.autoReverse = reverse;
      return this;
    }
    
    public Builder repeat(int count) {
      this.repeatCount = count;
      return this;
    }
    
    public Builder repeatForever() {
      this.repeatCount = -1;
      return this;
    }
    
    public Builder animationGroup(String group) {
      this.animationGroup = group;
      return this;
    }

    public Builder effectParam(String key, String value) {
      this.effectParams.put(key, value);
      return this;
    }

    public Builder rotationDegrees(int degrees) {
      return effectParam(PARAM_ROTATION_DEGREES, String.valueOf(degrees));
    }

    public Builder scalePercent(int percent) {
      return effectParam(PARAM_SCALE_PERCENT, String.valueOf(percent));
    }

    public Builder color(String hexColor) {
      return effectParam(PARAM_COLOR, hexColor);
    }

    public Builder opacity(int percent) {
      return effectParam(PARAM_OPACITY, String.valueOf(percent));
    }

    public Builder intensity(int percent) {
      return effectParam(PARAM_INTENSITY, String.valueOf(percent));
    }

    public AnimationBinding build() {
      // Calculate context-aware delay based on animation group
      String contextDelay = calculateDelayFromAnimationGroup(animationGroup, delay);

      AnimationBinding binding = new AnimationBinding(targetSpid, animationType, transition, duration, contextDelay,
                                 paragraphStart, paragraphEnd, clickTrigger, animationGroup, motionPath,
                                 acceleration, deceleration, autoReverse, repeatCount, effectParams);
      if (timingNodeId >= 0) {
        binding.setTimingNodeId(timingNodeId);
      }
      return binding;
    }
    
    /**
     * Calculate timing delay based on animation group context.
     * 
     * PowerPoint timing rules:
     * - on-click: delay="indefinite" (creates new click trigger)
     * - with-previous: delay="0" (starts immediately with click)
     * - after-previous: delay="0" for now (TODO: calculate based on previous animation duration)
     * 
     * @param animationGroup The animation group ("on-click", "with-previous", "after-previous")
     * @param builderDelay The delay value set by the builder (if explicitly set)
     * @return The calculated delay value
     */
    private String calculateDelayFromAnimationGroup(String animationGroup, String builderDelay) {
      // If delay was explicitly set by builder methods, respect that
      if (!DEFAULT_DELAY.equals(builderDelay)) {
        return builderDelay;
      }

      // Otherwise, calculate based on animation group context.
      // The "indefinite" delay for on-click is handled at the click trigger container level
      // (AnimationInjector.createClickTriggerElement), NOT at the animation level.
      // The animation itself always starts with delay="0" once its parent click fires.
      switch (animationGroup) {
        case "on-click":
          return "0";
        case "with-previous":
          return "0";
        case "after-previous":
          if (previousAnimationDuration != null) {
            return previousAnimationDuration;
          } else {
            return "0";
          }
        default:
          return "0";
      }
    }
  }
  
  // ========== FACTORY METHODS ==========
  
  /**
   * Create a simple fade entrance animation.
   * 
   * @param spid target shape SPID
   * @param clickTrigger click trigger number
   * @return AnimationBinding for fade entrance
   */
  public static AnimationBinding fadeIn(int spid, int clickTrigger) {
    return builder().target(spid).type(AnimationType.FADE).entrance().clickTrigger(clickTrigger).build();
  }
  
  /**
   * Create a simple fade exit animation.
   * 
   * @param spid target shape SPID
   * @param clickTrigger click trigger number
   * @return AnimationBinding for fade exit
   */
  public static AnimationBinding fadeOut(int spid, int clickTrigger) {
    return builder().target(spid).type(AnimationType.FADE).exit().clickTrigger(clickTrigger).build();
  }
  
  /**
   * Create a fly-in animation from specified direction.
   * 
   * @param spid target shape SPID
   * @param direction direction to fly from (left, right, top, bottom)
   * @param clickTrigger click trigger number
   * @return AnimationBinding for fly-in animation
   */
  public static AnimationBinding flyIn(int spid, String direction, int clickTrigger) {
    AnimationType flyType = switch (direction.toLowerCase()) {
      case "left" -> AnimationType.FLY_IN_LEFT;
      case "right" -> AnimationType.FLY_IN_RIGHT;
      case "top" -> AnimationType.FLY_IN_TOP;
      case "bottom" -> AnimationType.FLY_IN_BOTTOM;
      default -> AnimationType.FLY_IN_LEFT;
    };
    return builder().target(spid).type(flyType).entrance().clickTrigger(clickTrigger).build();
  }
  
  /**
   * Create a bullet point animation sequence.
   * 
   * @param spid target shape SPID
   * @param paragraphIndex paragraph to animate (0-based)
   * @param clickTrigger click trigger number
   * @return AnimationBinding for bullet point animation
   */
  public static AnimationBinding bulletPoint(int spid, int paragraphIndex, int clickTrigger) {
    return builder().target(spid).type(AnimationType.FLY_IN_LEFT).entrance()
                   .paragraph(paragraphIndex).clickTrigger(clickTrigger).build();
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder("AnimationBinding{");
    sb.append("spid=").append(targetSpid);
    sb.append(", type=").append(animationType.getUserFriendlyName());
    sb.append(", transition=").append(transition);
    sb.append(", duration=").append(duration).append("ms");
    if (!DEFAULT_DELAY.equals(delay)) sb.append(", delay=").append(delay).append("ms");
    sb.append(", trigger=").append(clickTrigger);
    
    if (isParagraphLevelAnimation()) {
      sb.append(", paragraphs=").append(paragraphStart);
      if (!paragraphStart.equals(paragraphEnd)) {
        sb.append("-").append(paragraphEnd);
      }
    }
    
    if (hasMotionPath()) sb.append(", motionPath");
    if (hasCustomEasing()) sb.append(", easing=").append(acceleration).append("/").append(deceleration);
    if (autoReverse) sb.append(", autoReverse");
    if (repeats()) sb.append(", repeat=").append(repeatCount == -1 ? "inf" : repeatCount);
    if (!effectParams.isEmpty()) sb.append(", effectParams=").append(effectParams);

    sb.append("}");
    return sb.toString();
  }
}
