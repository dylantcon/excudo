package com.excudo.core.parsing;

import java.util.*;

/**
 * Result of parsing a command with validated parameters.
 * Provides type-safe access to parameter values.
 * 
 * Can be created programmatically using the Builder pattern:
 * ParsedCommand cmd = ParsedCommand.builder("edit-content")
 *     .addParam("slide", 1)
 *     .addParam("spid", 123)
 *     .addParam("text", "New content")
 *     .build();
 */
public class ParsedCommand {
    private final String commandName;
    private final Map<String, String> parameters;
    
    public ParsedCommand(String commandName, Map<String, String> parameters) {
        this.commandName = commandName;
        this.parameters = Collections.unmodifiableMap(new HashMap<>(parameters));
    }
    
    /**
     * Create a new ParsedCommand builder for programmatic command creation.
     * 
     * @param commandName the name of the command
     * @return a new Builder instance
     */
    public static Builder builder(String commandName) {
        return new Builder(commandName);
    }
    
    /**
     * Builder for creating ParsedCommand objects programmatically.
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
         * Build the ParsedCommand.
         * Can optionally validate against CommandSchema if needed.
         */
        public ParsedCommand build() {
            return new ParsedCommand(commandName, parameters);
        }
        
        /**
         * Build and validate the ParsedCommand against its registered schema.
         * 
         * @throws CommandParseException if validation fails
         */
        public ParsedCommand buildAndValidate() throws CommandParseException {
            CommandSchema schema = CommandRegistry.getSchema(commandName);
            if (schema == null) {
                throw new CommandParseException("No schema registered for command: " + commandName);
            }
            
            // Validate the parameters against the schema
            schema.validate(parameters);
            
            // If validation passes, create and return the command
            return new ParsedCommand(commandName, parameters);
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
    
    @Override
    public String toString() {
        return commandName + " " + parameters;
    }
}