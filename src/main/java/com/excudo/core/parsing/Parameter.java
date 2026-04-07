package com.excudo.core.parsing;

import java.util.*;
import java.util.function.Predicate;

/**
 * Defines a command parameter with validation, type information, and documentation.
 * Supports both positional and named parameters with flexible validation rules.
 */
public class Parameter {
    private final String name;
    private final String description;
    private final ParameterType type;
    private final boolean required;
    private final String defaultValue;
    private final Set<String> validValues;
    private final Predicate<String> validator;
    private final boolean variableLength;
    private final String llmName;

    private Parameter(Builder builder) {
        this.name = builder.name;
        this.description = builder.description;
        this.type = builder.type;
        this.required = builder.required;
        this.defaultValue = builder.defaultValue;
        this.validValues = builder.validValues != null ?
            Collections.unmodifiableSet(builder.validValues) : null;
        this.validator = builder.validator;
        this.variableLength = builder.variableLength;
        this.llmName = builder.llmName;
    }
    
    public String getName() {
        return name;
    }
    
    public boolean isRequired() {
        return required;
    }
    
    public String getDefaultValue() {
        return defaultValue;
    }
    
    public boolean isVariableLength() {
        return variableLength;
    }

    /**
     * Get the LLM parameter name alias. When the LLM sends a request using
     * a different parameter name (e.g. "slideNumber" instead of "slide"),
     * this alias enables the bridge to map it correctly.
     *
     * @return the LLM name alias, or null if it matches the canonical name
     */
    public String getLlmName() {
        return llmName;
    }

    /**
     * Get the effective LLM parameter name (llmName if set, otherwise canonical name).
     */
    public String getEffectiveLlmName() {
        return llmName != null ? llmName : name;
    }

    public ParameterType getType() {
        return type;
    }

    public String getDescription() {
        return description;
    }

    public Set<String> getValidValues() {
        return validValues;
    }
    
    public boolean isValidValue(String value) {
        if (value == null) {
            return !required;
        }
        
        // Check type validation
        if (!type.isValid(value)) {
            return false;
        }
        
        // Check enumerated values if specified
        if (validValues != null && !validValues.contains(value)) {
            return false;
        }
        
        // Check custom validator if specified
        if (validator != null && !validator.test(value)) {
            return false;
        }
        
        return true;
    }
    
    public String getTypeDescription() {
        if (validValues != null) {
            return "one of: " + String.join(", ", validValues);
        }
        return type.getDescription();
    }
    
    public String getValidationHelp() {
        StringBuilder help = new StringBuilder();
        help.append("Expected: ").append(getTypeDescription());
        
        if (!required && defaultValue != null) {
            help.append(" (default: ").append(defaultValue).append(")");
        }
        
        return help.toString();
    }
    
    public String generateHelp() {
        StringBuilder help = new StringBuilder();
        help.append(String.format("%-15s %s", name, description));
        
        if (!required) {
            help.append(" (optional");
            if (defaultValue != null) {
                help.append(", default: ").append(defaultValue);
            }
            help.append(")");
        }
        
        if (validValues != null) {
            help.append("\n                 Valid values: ").append(String.join(", ", validValues));
        } else if (type != ParameterType.STRING) {
            help.append("\n                 Type: ").append(type.getDescription());
        }
        
        return help.toString();
    }
    
    public static Builder builder(String name) {
        return new Builder(name);
    }
    
    public enum ParameterType {
        STRING("text") {
            @Override
            public boolean isValid(String value) {
                return value != null;
            }
        },
        INTEGER("integer") {
            @Override
            public boolean isValid(String value) {
                try {
                    Integer.parseInt(value);
                    return true;
                } catch (NumberFormatException e) {
                    return false;
                }
            }
        },
        DOUBLE("decimal number") {
            @Override
            public boolean isValid(String value) {
                try {
                    Double.parseDouble(value);
                    return true;
                } catch (NumberFormatException e) {
                    return false;
                }
            }
        },
        BOOLEAN("true/false") {
            @Override
            public boolean isValid(String value) {
                return "true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value);
            }
        },
        SLIDE_NUMBER("slide number (1-based)") {
            @Override
            public boolean isValid(String value) {
                try {
                    int num = Integer.parseInt(value);
                    return num > 0;
                } catch (NumberFormatException e) {
                    return false;
                }
            }
        },
        SPID("shape ID") {
            @Override
            public boolean isValid(String value) {
                try {
                    int spid = Integer.parseInt(value);
                    return spid > 0;
                } catch (NumberFormatException e) {
                    return false;
                }
            }
        },
        ANIMATION_TYPE("animation type") {
            @Override
            public boolean isValid(String value) {
                try {
                    com.excudo.core.model.AnimationType.parseType(value);
                    return true;
                } catch (Exception e) {
                    return false;
                }
            }
        };
        
        private final String description;
        
        ParameterType(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
        
        public abstract boolean isValid(String value);
    }
    
    public static class Builder {
        private final String name;
        private String description = "";
        private ParameterType type = ParameterType.STRING;
        private boolean required = true;
        private String defaultValue;
        private Set<String> validValues;
        private Predicate<String> validator;
        private boolean variableLength = false;
        private String llmName;
        
        public Builder(String name) {
            this.name = name;
        }
        
        public Builder description(String description) {
            this.description = description;
            return this;
        }
        
        public Builder type(ParameterType type) {
            this.type = type;
            return this;
        }
        
        public Builder required(boolean required) {
            this.required = required;
            return this;
        }
        
        public Builder defaultValue(String defaultValue) {
            this.defaultValue = defaultValue;
            this.required = false; // Having a default makes it optional
            return this;
        }
        
        public Builder validValues(String... values) {
            this.validValues = new HashSet<>(Arrays.asList(values));
            return this;
        }
        
        public Builder validator(Predicate<String> validator) {
            this.validator = validator;
            return this;
        }
        
        public Builder variableLength(boolean variableLength) {
            this.variableLength = variableLength;
            return this;
        }

        /**
         * Set the LLM parameter name alias. Use when the LLM uses a different
         * name for this parameter (e.g. "slideNumber" instead of "slide").
         */
        public Builder llmName(String llmName) {
            this.llmName = llmName;
            return this;
        }

        public Parameter build() {
            return new Parameter(this);
        }
    }
}