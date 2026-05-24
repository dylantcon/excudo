package com.excudo.core.commands;

import com.excudo.core.commands.mutating.slide.AddAnimationCommand;

import com.excudo.core.model.AnimationType;
import java.util.*;

/**
 * Defines parameter requirements for all animation types.
 * Auto-populated from AnimationType enum -- every type gets requirements.
 *
 * Enables dynamic console command parameter validation and help generation
 * based on animation type classification (entrance/exit, emphasis, motion path).
 */
public class AnimationParameterRequirement {

    // ========== TRIGGER CONSTANTS ==========

    public static final String TRIGGER_ON_CLICK = "on-click";
    public static final String TRIGGER_WITH_PREVIOUS = "with-previous";
    public static final String TRIGGER_AFTER_PREVIOUS = "after-previous";

    /**
     * Parameter definition for animation commands.
     */
    public static class ParameterDefinition {
        private final String name;
        private final String description;
        private final boolean required;
        private final String defaultValue;
        private final List<String> validValues;

        public ParameterDefinition(String name, String description, boolean required) {
            this(name, description, required, null, null);
        }

        public ParameterDefinition(String name, String description, boolean required, String defaultValue) {
            this(name, description, required, defaultValue, null);
        }

        public ParameterDefinition(String name, String description, boolean required, String defaultValue, List<String> validValues) {
            this.name = name;
            this.description = description;
            this.required = required;
            this.defaultValue = defaultValue;
            this.validValues = validValues != null ? new ArrayList<>(validValues) : null;
        }

        public String getName() { return name; }
        public String getDescription() { return description; }
        public boolean isRequired() { return required; }
        public String getDefaultValue() { return defaultValue; }
        public List<String> getValidValues() { return validValues; }

        public boolean isValidValue(String value) {
            if (validValues == null) return true;
            return validValues.contains(value);
        }
    }

    /**
     * Animation requirement specification.
     */
    public static class AnimationRequirement {
        private final AnimationType animationType;
        private final List<ParameterDefinition> parameters;
        private final String usageExample;

        public AnimationRequirement(AnimationType animationType, List<ParameterDefinition> parameters, String usageExample) {
            this.animationType = animationType;
            this.parameters = new ArrayList<>(parameters);
            this.usageExample = usageExample;
        }

        public AnimationType getAnimationType() { return animationType; }
        public List<ParameterDefinition> getParameters() { return parameters; }
        public String getUsageExample() { return usageExample; }

        public int getRequiredParameterCount() {
            return (int) parameters.stream().filter(ParameterDefinition::isRequired).count();
        }

        public String generateUsageString(String baseCommand) {
            StringBuilder usage = new StringBuilder(baseCommand);
            for (ParameterDefinition param : parameters) {
                if (param.isRequired()) {
                    usage.append(" <").append(param.getName()).append(">");
                } else {
                    usage.append(" [").append(param.getName()).append("]");
                }
            }
            return usage.toString();
        }
    }

    // ========== STANDARD PARAMETER DEFINITIONS ==========

    private static final ParameterDefinition SLIDE_PARAM =
        new ParameterDefinition("slide#", "Slide number", true);
    private static final ParameterDefinition SPID_PARAM =
        new ParameterDefinition("spid", "Shape ID", true);
    private static final ParameterDefinition DIRECTION_PARAM =
        new ParameterDefinition("direction", "Animation direction (in, out)", true, "in", Arrays.asList("in", "out"));
    private static final ParameterDefinition TRIGGER_PARAM =
        new ParameterDefinition("trigger", "Animation trigger", true, TRIGGER_ON_CLICK,
            Arrays.asList(TRIGGER_ON_CLICK, TRIGGER_WITH_PREVIOUS, TRIGGER_AFTER_PREVIOUS));
    private static final ParameterDefinition DURATION_PARAM =
        new ParameterDefinition("duration", "Animation duration in ms", false, "500");

    // Motion path parameter
    private static final ParameterDefinition PATH_PARAM =
        new ParameterDefinition("path", "Motion path expression (e.g., 'M 0 0 L 0.25 0 E')", false);

    // Effect-specific optional parameters
    private static final ParameterDefinition ROTATION_PARAM =
        new ParameterDefinition("rotation", "Rotation degrees (e.g., '360', '720')", false, "360");
    private static final ParameterDefinition SCALE_PARAM =
        new ParameterDefinition("scale", "Scale percentage (e.g., '150' for 150%)", false, "150");
    private static final ParameterDefinition COLOR_PARAM =
        new ParameterDefinition("color", "Target color hex (e.g., 'FF0000')", false, "181818");
    private static final ParameterDefinition OPACITY_PARAM =
        new ParameterDefinition("opacity", "Opacity percentage 0-100", false, "75");
    private static final ParameterDefinition INTENSITY_PARAM =
        new ParameterDefinition("intensity", "Effect intensity percentage 0-100", false, "100");

    // ========== REGISTRY ==========

    private static final Map<AnimationType, AnimationRequirement> REQUIREMENTS = new HashMap<>();

    static {
        initializeAllRequirements();
    }

    /**
     * Auto-populate requirements for ALL AnimationType values based on classification.
     */
    private static void initializeAllRequirements() {
        for (AnimationType type : AnimationType.values()) {
            List<ParameterDefinition> params = new ArrayList<>();
            params.add(SLIDE_PARAM);
            params.add(SPID_PARAM);

            if (type.isEmphasis()) {
                // Emphasis: no direction param, add trigger + duration + type-specific
                params.add(TRIGGER_PARAM);
                params.add(DURATION_PARAM);
                addEmphasisParams(type, params);
            } else if (type.isMotionPath()) {
                // Motion path: direction + trigger + path + duration
                params.add(DIRECTION_PARAM);
                params.add(TRIGGER_PARAM);
                params.add(PATH_PARAM);
                params.add(DURATION_PARAM);
            } else {
                // Entrance/exit: direction + trigger + duration
                params.add(DIRECTION_PARAM);
                params.add(TRIGGER_PARAM);
                params.add(DURATION_PARAM);
            }

            String example = generateExample(type);
            REQUIREMENTS.put(type, new AnimationRequirement(type, params, example));
        }
    }

    /**
     * Add type-specific optional parameters for emphasis animations.
     */
    private static void addEmphasisParams(AnimationType type, List<ParameterDefinition> params) {
        switch (type) {
            case SPIN:
            case TEETER:
                params.add(ROTATION_PARAM);
                break;
            case GROW_SHRINK:
                params.add(SCALE_PARAM);
                break;
            case PULSE:
                params.add(SCALE_PARAM);
                break;
            case COLOR_PULSE:
                params.add(COLOR_PARAM);
                break;
            case TRANSPARENCY:
                params.add(OPACITY_PARAM);
                break;
            case DARKEN:
            case LIGHTEN:
            case DESATURATE:
                params.add(INTENSITY_PARAM);
                break;
            default:
                break;
        }
    }

    /**
     * Generate a usage example for an animation type using hyphenated names.
     */
    private static String generateExample(AnimationType type) {
        String name = type.getUserFriendlyName();
        if (type.isEmphasis()) {
            return "add-animation 1 3 " + name + " " + TRIGGER_ON_CLICK + " 500";
        } else if (type.isMotionPath()) {
            return "add-animation 1 3 " + name + " in " + TRIGGER_ON_CLICK + " 'M 0 0 L 0.25 0 E' 1000";
        } else {
            return "add-animation 1 3 " + name + " in " + TRIGGER_ON_CLICK + " 500";
        }
    }

    // ========== PUBLIC API ==========

    /**
     * Get parameter requirements for a specific animation type.
     */
    public static AnimationRequirement getRequirements(AnimationType animationType) {
        return REQUIREMENTS.get(animationType);
    }

    /**
     * Get parameter requirements by animation type string.
     */
    public static AnimationRequirement getRequirements(String animationTypeString) {
        try {
            AnimationType animationType = AnimationType.parseType(animationTypeString);
            return getRequirements(animationType);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Check if the provided arguments match the requirements for an animation type.
     */
    public static ValidationResult validateArguments(AnimationType animationType, String[] args) {
        AnimationRequirement requirement = getRequirements(animationType);
        if (requirement == null) {
            return ValidationResult.error("Unknown animation type: " + animationType);
        }

        List<ParameterDefinition> params = requirement.getParameters();
        int requiredCount = requirement.getRequiredParameterCount();

        if (args.length < requiredCount) {
            return ValidationResult.error("Insufficient arguments. Required: " + requiredCount + ", provided: " + args.length +
                                        "\nUsage: " + requirement.generateUsageString(AddAnimationCommand.NAME) +
                                        "\nExample: " + requirement.getUsageExample());
        }

        for (int i = 0; i < Math.min(args.length, params.size()); i++) {
            ParameterDefinition param = params.get(i);
            String value = args[i];

            if (!param.isValidValue(value)) {
                return ValidationResult.error("Invalid value for " + param.getName() + ": " + value +
                                            "\nValid values: " + param.getValidValues());
            }
        }

        return ValidationResult.success();
    }

    /**
     * Generate help text for a specific animation type.
     */
    public static String generateHelpText(AnimationType animationType) {
        AnimationRequirement requirement = getRequirements(animationType);
        if (requirement == null) {
            return "No help available for animation type: " + animationType;
        }

        StringBuilder help = new StringBuilder();
        help.append("Animation Type: ").append(animationType.getUserFriendlyName()).append("\n");
        help.append("Usage: ").append(requirement.generateUsageString(AddAnimationCommand.NAME)).append("\n");
        help.append("Example: ").append(requirement.getUsageExample()).append("\n\n");
        help.append("Parameters:\n");

        for (ParameterDefinition param : requirement.getParameters()) {
            help.append("  ").append(param.getName()).append(" - ").append(param.getDescription());
            if (!param.isRequired()) {
                help.append(" (optional, default: ").append(param.getDefaultValue()).append(")");
            }
            if (param.getValidValues() != null) {
                help.append(" [").append(String.join(", ", param.getValidValues())).append("]");
            }
            help.append("\n");
        }

        return help.toString();
    }

    /**
     * Get all supported animation types with their requirements.
     */
    public static Map<AnimationType, AnimationRequirement> getAllRequirements() {
        return new HashMap<>(REQUIREMENTS);
    }

    /**
     * Validation result class.
     */
    public static class ValidationResult {
        private final boolean success;
        private final String errorMessage;

        private ValidationResult(boolean success, String errorMessage) {
            this.success = success;
            this.errorMessage = errorMessage;
        }

        public static ValidationResult success() {
            return new ValidationResult(true, null);
        }

        public static ValidationResult error(String message) {
            return new ValidationResult(false, message);
        }

        public boolean isSuccess() { return success; }
        public String getErrorMessage() { return errorMessage; }
    }
}
