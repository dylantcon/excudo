package com.excudo.core.parsing;

import java.util.*;

/**
 * Result of parsing a command with validated parameters.
 * Provides type-safe access to parameter values.
 * 
 * Can be created programmatically using the Builder pattern:
 * CommandParameters cmd = CommandParameters.builder("edit-content")
 *     .addParam("slide", 1)
 *     .addParam("spid", 123)
 *     .addParam("text", "New content")
 *     .build();
 */
public class CommandParameters {
    private final String commandName;
    private final Map<String, String> parameters;
    
    public CommandParameters(String commandName, Map<String, String> parameters) {
        this.commandName = commandName;
        this.parameters = Collections.unmodifiableMap(new HashMap<>(parameters));
    }
    
    /**
     * Create a new CommandParameters builder for programmatic command creation.
     * 
     * @param commandName the name of the command
     * @return a new Builder instance
     */
    public static Builder builder(String commandName) {
        return new Builder(commandName);
    }
    
    /**
     * Builder for creating CommandParameters objects programmatically.
     * Supports fluent interface for chaining parameter additions.
     */
    public static class Builder {
        private final String commandName;
        private final Map<String, String> parameters = new HashMap<>();
        
        private Builder(String commandName) {
            if (commandName == null || commandName.trim().isEmpty()) {
                throw new IllegalArgumentException("Command name cannot be null or empty");
            }
            this.commandName = commandName;
        }
        
        /**
         * Add a string parameter.
         */
        public Builder addParam(String name, String value) {
            if (name == null || name.trim().isEmpty()) {
                throw new IllegalArgumentException("Parameter name cannot be null or empty");
            }
            if (value != null) {
                parameters.put(name, value);
            }
            return this;
        }
        
        /**
         * Add an integer parameter.
         */
        public Builder addParam(String name, int value) {
            return addParam(name, String.valueOf(value));
        }
        
        /**
         * Add a double parameter.
         */
        public Builder addParam(String name, double value) {
            return addParam(name, String.valueOf(value));
        }
        
        /**
         * Add a boolean parameter.
         */
        public Builder addParam(String name, boolean value) {
            return addParam(name, String.valueOf(value));
        }
        
        /**
         * Add a long parameter (useful for SPIDs).
         */
        public Builder addParam(String name, long value) {
            return addParam(name, String.valueOf(value));
        }
        
        /**
         * Add an object parameter (calls toString).
         */
        public Builder addParam(String name, Object value) {
            if (value != null) {
                return addParam(name, value.toString());
            }
            return this;
        }
        
        /**
         * Build the CommandParameters.
         * Can optionally validate against CommandSchema if needed.
         */
        public CommandParameters build() {
            return new CommandParameters(commandName, parameters);
        }
        
        /**
         * Build and validate the CommandParameters against its registered schema.
         * 
         * @throws CommandParseException if validation fails
         */
        public CommandParameters buildAndValidate() throws CommandParseException {
            CommandSchema schema = CommandRegistry.getSchema(commandName);
            if (schema == null) {
                throw new CommandParseException("No schema registered for command: " + commandName);
            }
            
            // Validate the parameters against the schema
            schema.validate(parameters);
            
            // If validation passes, create and return the command
            return new CommandParameters(commandName, parameters);
        }
    }
    
    public String getCommandName() {
        return commandName;
    }
    
    public String getString(String parameterName) {
        return parameters.get(parameterName);
    }
    
    /**
     * Get a string parameter with a default value.
     */
    public String getString(String parameterName, String defaultValue) {
        String value = parameters.get(parameterName);
        return value != null ? value : defaultValue;
    }
    
    public Integer getInteger(String parameterName) {
        String value = parameters.get(parameterName);
        return value != null ? Integer.parseInt(value) : null;
    }
    
    /**
     * Get an integer parameter with a default value.
     */
    public Integer getInteger(String parameterName, Integer defaultValue) {
        String value = parameters.get(parameterName);
        if (value != null) {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                // Return default if parsing fails
                return defaultValue;
            }
        }
        return defaultValue;
    }
    
    public Double getDouble(String parameterName) {
        String value = parameters.get(parameterName);
        return value != null ? Double.parseDouble(value) : null;
    }
    
    /**
     * Get a double parameter with a default value.
     */
    public Double getDouble(String parameterName, Double defaultValue) {
        String value = parameters.get(parameterName);
        if (value != null) {
            try {
                return Double.parseDouble(value);
            } catch (NumberFormatException e) {
                // Return default if parsing fails
                return defaultValue;
            }
        }
        return defaultValue;
    }
    
    public Boolean getBoolean(String parameterName) {
        String value = parameters.get(parameterName);
        return value != null ? Boolean.parseBoolean(value) : null;
    }
    
    /**
     * Get a boolean parameter with a default value.
     */
    public Boolean getBoolean(String parameterName, Boolean defaultValue) {
        String value = parameters.get(parameterName);
        return value != null ? Boolean.parseBoolean(value) : defaultValue;
    }
    
    public boolean hasParameter(String parameterName) {
        return parameters.containsKey(parameterName);
    }

    public Set<String> getParameterNames() {
        return parameters.keySet();
    }

    // ========== TYPED REQUIRE/OPT GETTERS ==========
    //
    // The fromParameters() factory methods on each Command class use these
    // shorter, more declarative getters. Patterns:
    //   p.requireInt("slide")            -- required, throws if missing/malformed
    //   p.optString("name")              -- Optional<String>, present iff set
    //   p.optEnum("align", Align.class)  -- Optional<Align>, valueOf-mapped
    //
    // The legacy getString()/getInteger()/getDouble()/getBoolean() above stay
    // for old call sites; new code uses these.

    /** Required int parameter. Throws CommandParseException with the
     *  parameter name if missing or not parseable. */
    public int requireInt(String name) {
        String raw = parameters.get(name);
        if (raw == null) {
            throw new IllegalArgumentException("Required int parameter '" + name + "' is missing");
        }
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                "Parameter '" + name + "' must be an integer, got: '" + raw + "'", e);
        }
    }

    /** Required string parameter. Throws if missing or empty. */
    public String requireString(String name) {
        String raw = parameters.get(name);
        if (raw == null || raw.isEmpty()) {
            throw new IllegalArgumentException("Required string parameter '" + name + "' is missing");
        }
        return raw;
    }

    /** Required double parameter. Throws if missing or not parseable.
     *  Use this for ParameterType.DOUBLE schema fields where a fractional
     *  value (e.g. EMU coordinates) must be accepted. */
    public double requireDouble(String name) {
        String raw = parameters.get(name);
        if (raw == null) {
            throw new IllegalArgumentException("Required double parameter '" + name + "' is missing");
        }
        try {
            return Double.parseDouble(raw);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                "Parameter '" + name + "' must be a number, got: '" + raw + "'", e);
        }
    }

    /** Required long parameter. Throws if missing or not parseable. */
    public long requireLong(String name) {
        String raw = parameters.get(name);
        if (raw == null) {
            throw new IllegalArgumentException("Required long parameter '" + name + "' is missing");
        }
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                "Parameter '" + name + "' must be a long, got: '" + raw + "'", e);
        }
    }

    /** Optional string. Empty when the parameter is absent or empty. */
    public Optional<String> optString(String name) {
        String raw = parameters.get(name);
        return (raw == null || raw.isEmpty()) ? Optional.empty() : Optional.of(raw);
    }

    /** Optional int. Empty when the parameter is absent. Throws if present-but-malformed. */
    public OptionalInt optInt(String name) {
        String raw = parameters.get(name);
        if (raw == null || raw.isEmpty()) return OptionalInt.empty();
        try {
            return OptionalInt.of(Integer.parseInt(raw));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                "Parameter '" + name + "' must be an integer, got: '" + raw + "'", e);
        }
    }

    /** Optional long. Empty when absent. Throws if present-but-malformed. */
    public OptionalLong optLong(String name) {
        String raw = parameters.get(name);
        if (raw == null || raw.isEmpty()) return OptionalLong.empty();
        try {
            return OptionalLong.of(Long.parseLong(raw));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                "Parameter '" + name + "' must be a long, got: '" + raw + "'", e);
        }
    }

    /** Optional boolean. Empty when absent. */
    public Optional<Boolean> optBoolean(String name) {
        String raw = parameters.get(name);
        return (raw == null || raw.isEmpty()) ? Optional.empty() : Optional.of(Boolean.parseBoolean(raw));
    }

    // ========== TYPED PARAMETER-KEY ACCESSORS ==========
    //
    // These read through a Parameter<T> constant rather than a string literal,
    // so the parameter key is declared exactly once (on the Parameter) and
    // shared between the command's SCHEMA and its fromParameters body. The
    // Parameter carries its own parser, default, and required flag:
    //   int slide   = p.get(SLIDE);          -- required; throws if missing
    //   String type = p.get(SHAPE_TYPE);     -- default falls out of the key
    //   var align   = p.opt(ALIGN);          -- Optional<T>, normalizer applied

    /**
     * Resolve a parameter's typed value via its own {@link Parameter} key.
     * When the raw value is absent, the parameter's declared default is parsed
     * and returned; if there is no default and the parameter is required, an
     * {@link IllegalArgumentException} is thrown; otherwise {@code null}.
     */
    public <T> T get(Parameter<T> param) {
        String raw = parameters.get(param.getName());
        if (raw == null || raw.isEmpty()) {
            String def = param.getDefaultValue();
            if (def != null) return param.parse(def);
            if (param.isRequired()) {
                throw new IllegalArgumentException(
                    "Required parameter '" + param.getName() + "' is missing");
            }
            return null;
        }
        return param.parse(raw);
    }

    /**
     * Optional typed value via a {@link Parameter} key. Present when the value
     * is set, or when the parameter declares a default; empty otherwise. The
     * parameter's parser (including any normalizer) is applied in both cases.
     */
    public <T> Optional<T> opt(Parameter<T> param) {
        String raw = parameters.get(param.getName());
        if (raw == null || raw.isEmpty()) {
            String def = param.getDefaultValue();
            return def != null ? Optional.ofNullable(param.parse(def)) : Optional.empty();
        }
        return Optional.ofNullable(param.parse(raw));
    }

    /** Optional enum. valueOf() against the enum class; empty when absent.
     *  Throws if present-but-not-a-valid-enum-constant. */
    public <T extends Enum<T>> Optional<T> optEnum(String name, Class<T> enumType) {
        String raw = parameters.get(name);
        if (raw == null || raw.isEmpty()) return Optional.empty();
        try {
            return Optional.of(Enum.valueOf(enumType, raw));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                "Parameter '" + name + "' must be one of "
                + Arrays.toString(enumType.getEnumConstants())
                + ", got: '" + raw + "'", e);
        }
    }

    @Override
    public String toString() {
        return commandName + " " + parameters;
    }
}