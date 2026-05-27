package com.excudo.core.parsing;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Schema definition for console commands with support for both positional and named parameters.
 * Provides parse-time validation, automatic help generation, and flexible argument handling.
 * 
 * This framework addresses systematic console parsing issues:
 * - Fragile positional arguments
 * - Poor error messages
 * - Inconsistent command patterns
 * - Lack of self-documentation
 */
public class CommandSchema {
    private String name;
    private final String description;
    private final List<Parameter<?>> parameters;
    private final Map<String, Parameter<?>> parametersByName;
    private final List<String> examples;
    private final boolean allowsNamedParameters;
    private final boolean llmEnabled;
    private final String llmDescription;

    private CommandSchema(Builder builder) {
        this.name = builder.name;
        this.description = builder.description;
        this.parameters = Collections.unmodifiableList(builder.parameters);
        this.parametersByName = builder.parameters.stream()
            .collect(Collectors.toMap(Parameter::getName, Function.identity()));
        this.examples = Collections.unmodifiableList(builder.examples);
        this.allowsNamedParameters = builder.allowsNamedParameters;
        this.llmEnabled = builder.llmEnabled;
        this.llmDescription = builder.llmDescription;
    }
    
    /**
     * Parse command arguments according to this schema
     */
    public CommandParameters parse(String[] args) throws CommandParseException {
        Map<String, String> values = new HashMap<>();
        
        if (allowsNamedParameters && hasNamedArgs(args)) {
            parseNamedArguments(args, values);
        } else {
            parsePositionalArguments(args, values);
        }
        
        // Validate all required parameters are present
        validateRequiredParameters(values);
        
        // Apply default values
        applyDefaults(values);
        
        // Validate parameter values
        validateParameterValues(values);
        
        return new CommandParameters(name, values);
    }
    
    /**
     * Validate a map of parameters against this schema.
     * This is useful for validating programmatically created commands.
     * 
     * @param parameters the parameters to validate
     * @throws CommandParseException if validation fails
     */
    public void validate(Map<String, String> parameters) throws CommandParseException {
        Map<String, String> values = new HashMap<>(parameters);
        
        // Validate all required parameters are present
        validateRequiredParameters(values);
        
        // Apply default values (modifies the map)
        applyDefaults(values);
        
        // Validate parameter values
        validateParameterValues(values);
    }
    
    /**
     * Generate help text for this command
     */
    public String generateHelp() {
        StringBuilder help = new StringBuilder();
        
        // Command name and description
        help.append(name.toUpperCase()).append(" - ").append(description).append("\n\n");
        
        // Usage patterns
        help.append("Usage:\n");
        help.append("  ").append(generatePositionalUsage()).append("\n");
        if (allowsNamedParameters) {
            help.append("  ").append(generateNamedUsage()).append("\n");
        }
        help.append("\n");
        
        // Parameters
        help.append("Parameters:\n");
        for (Parameter<?> param : parameters) {
            help.append("  ").append(param.generateHelp()).append("\n");
        }
        
        // Examples
        if (!examples.isEmpty()) {
            help.append("\nExamples:\n");
            for (String example : examples) {
                help.append("  ").append(example).append("\n");
            }
        }
        
        return help.toString();
    }
    
    // ========== LLM SCHEMA GENERATION ==========

    public boolean isLlmEnabled() {
        return llmEnabled;
    }

    public String getLlmDescription() {
        return llmDescription != null ? llmDescription : description;
    }

    public String getName() {
        return name;
    }

    /**
     * Assign the canonical command name derived from the owning Command class.
     *
     * <p>Class-registered commands declare a nameless {@link #builder()} schema:
     * the single source of truth for the command name is the class itself
     * ({@code AddShapeCommand} → {@code add-shape}), stamped here at registration
     * time. This keeps the name from being duplicated as a magic string in
     * {@code CommandSchema.builder("...")}.
     *
     * <p>Idempotent for the same name (tolerates re-registration); throws if a
     * different name was already present, which signals a stray hardcoded name
     * fighting the class-derived one.
     */
    public void assignName(String derivedName) {
        if (derivedName == null || derivedName.isBlank()) {
            throw new IllegalArgumentException("derivedName must be non-blank");
        }
        if (this.name != null) {
            if (this.name.equals(derivedName)) return;
            throw new IllegalStateException(
                "Schema already named '" + this.name + "' but the owning class derives '"
                + derivedName + "'. Class-registered commands must use a nameless "
                + "CommandSchema.builder() so the class is the only source of truth "
                + "for the command name.");
        }
        this.name = derivedName;
    }

    public String getDescription() {
        return description;
    }

    public List<Parameter<?>> getParameters() {
        return parameters;
    }

    public Parameter<?> getParameter(String name) {
        return parametersByName.get(name);
    }

    /**
     * Build a reverse mapping from LLM parameter names to canonical parameter names.
     * Used by {@link #bridgeLlmParams(Map)} to canonicalize incoming LLM action
     * parameter keys.
     */
    public Map<String, String> buildLlmToCanonicalParamMap() {
        Map<String, String> map = new HashMap<>();
        for (Parameter<?> p : parameters) {
            // Map canonical name to itself
            map.put(p.getName(), p.getName());
            // Map LLM alias to canonical name
            if (p.getLlmName() != null && !p.getLlmName().equals(p.getName())) {
                map.put(p.getLlmName(), p.getName());
            }
        }
        return map;
    }

    /**
     * Convert a raw LLM parameter map (JSON-decoded, value types are {@code
     * Object}) into a {@link CommandParameters} keyed by this schema's
     * canonical parameter names.
     *
     * <p>LLM-aliased keys are resolved through {@link
     * #buildLlmToCanonicalParamMap()}; values are coerced to {@code String}
     * to fit CommandParameters' string-only payload. Null values are dropped.
     *
     * <p>This is the LLM-side inverse of {@link #parse(String[])} (which works
     * from a tokenized CLI line). The result is ready to feed into
     * {@code CommandFactory.createCommand}.
     */
    public CommandParameters bridgeLlmParams(Map<String, Object> llmParams) {
        if (llmParams == null) llmParams = Collections.emptyMap();
        Map<String, String> mapping = buildLlmToCanonicalParamMap();
        Map<String, String> canonical = new HashMap<>();
        for (Map.Entry<String, Object> e : llmParams.entrySet()) {
            Object value = e.getValue();
            if (value == null) continue;
            String canonicalKey = mapping.getOrDefault(e.getKey(), e.getKey());
            canonical.put(canonicalKey, String.valueOf(value));
        }
        return new CommandParameters(name, canonical);
    }

    /**
     * Generate a JSON Schema "tool" definition for this command.
     * The output follows JSON Schema draft-07 suitable for LLM tool_use.
     */
    public String toLLMToolSchema() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"name\": \"").append(name).append("\",\n");
        sb.append("  \"description\": \"").append(escapeJson(getLlmDescription())).append("\",\n");
        sb.append("  \"parameters\": {\n");
        sb.append("    \"type\": \"object\",\n");
        sb.append("    \"properties\": {\n");

        List<String> requiredParams = new ArrayList<>();
        for (int i = 0; i < parameters.size(); i++) {
            Parameter<?> p = parameters.get(i);
            String llmParamName = p.getEffectiveLlmName();

            if (i > 0) sb.append(",\n");
            sb.append("      \"").append(llmParamName).append("\": ");
            sb.append(parameterToJsonSchema(p));

            if (p.isRequired()) {
                requiredParams.add(llmParamName);
            }
        }
        sb.append("\n    }");

        if (!requiredParams.isEmpty()) {
            sb.append(",\n    \"required\": [");
            sb.append(requiredParams.stream()
                .map(n -> "\"" + n + "\"")
                .collect(Collectors.joining(", ")));
            sb.append("]");
        }

        sb.append("\n  }\n}");
        return sb.toString();
    }

    private String parameterToJsonSchema(Parameter<?> p) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");

        // Type mapping
        String jsonType = switch (p.getType()) {
            case INTEGER, SLIDE_NUMBER, SPID -> "integer";
            case DOUBLE -> "number";
            case BOOLEAN -> "boolean";
            default -> "string";
        };
        sb.append("\"type\": \"").append(jsonType).append("\"");

        // Description
        if (p.getDescription() != null && !p.getDescription().isEmpty()) {
            sb.append(", \"description\": \"").append(escapeJson(p.getDescription())).append("\"");
        }

        // Enum values
        if (p.getValidValues() != null && !p.getValidValues().isEmpty()) {
            sb.append(", \"enum\": [");
            sb.append(p.getValidValues().stream()
                .sorted()
                .map(v -> "\"" + escapeJson(v) + "\"")
                .collect(Collectors.joining(", ")));
            sb.append("]");
        }

        // Default
        if (p.getDefaultValue() != null) {
            if ("integer".equals(jsonType) || "number".equals(jsonType)) {
                sb.append(", \"default\": ").append(p.getDefaultValue());
            } else if ("boolean".equals(jsonType)) {
                sb.append(", \"default\": ").append(p.getDefaultValue());
            } else {
                sb.append(", \"default\": \"").append(escapeJson(p.getDefaultValue())).append("\"");
            }
        }

        // Minimum constraints for numeric types
        if (p.getType() == Parameter.ParameterType.SLIDE_NUMBER ||
            p.getType() == Parameter.ParameterType.SPID) {
            sb.append(", \"minimum\": 1");
        }

        sb.append("}");
        return sb.toString();
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    /**
     * Generate "did you mean?" suggestions for invalid input
     */
    public String generateSuggestion(String[] args) {
        // Analyze what the user might have been trying to do
        List<String> suggestions = new ArrayList<>();
        
        // Check if they mixed up positional order
        if (!hasNamedArgs(args) && args.length >= parameters.size()) {
            suggestions.add(generatePositionalUsage());
        }
        
        // Check if they should use named parameters
        if (!hasNamedArgs(args) && allowsNamedParameters) {
            suggestions.add("Try using named parameters: " + generateNamedUsage());
        }
        
        // Check for common parameter mistakes
        for (int i = 0; i < args.length && i < parameters.size(); i++) {
            Parameter<?> param = parameters.get(i);
            String value = args[i];
            if (!param.isValidValue(value)) {
                suggestions.add(String.format("%s should be %s", 
                    param.getName(), param.getTypeDescription()));
            }
        }
        
        if (!suggestions.isEmpty()) {
            return "Did you mean:\n  " + String.join("\n  ", suggestions);
        }
        
        return "";
    }
    
    private boolean hasNamedArgs(String[] args) {
        return Arrays.stream(args).anyMatch(arg -> arg.startsWith("--"));
    }
    
    private void parseNamedArguments(String[] args, Map<String, String> values)
            throws CommandParseException {
        // First pass: assign leading positional arguments (before the first --)
        int positionalIndex = 0;
        int firstNamedIndex = 0;
        for (int i = 0; i < args.length; i++) {
            if (args[i].startsWith("--")) {
                firstNamedIndex = i;
                break;
            }
            // Assign to parameter by position order
            if (positionalIndex < parameters.size()) {
                values.put(parameters.get(positionalIndex).getName(), args[i]);
                positionalIndex++;
            }
            firstNamedIndex = i + 1;
        }

        // Second pass: process --key value pairs. Boolean parameters may
        // be used value-less (--flag alone means true) OR take an
        // explicit value (--flag true / --flag false). Non-boolean
        // parameters still require a value.
        for (int i = firstNamedIndex; i < args.length; i++) {
            if (args[i].startsWith("--")) {
                String paramName = args[i].substring(2);
                Parameter<?> param = parametersByName.get(paramName);

                if (param == null) {
                    throw new CommandParseException(
                        String.format("Unknown parameter: --%s\nAvailable parameters: %s",
                            paramName, parametersByName.keySet()));
                }

                boolean isBool = param.getType() == Parameter.ParameterType.BOOLEAN;
                boolean hasNext = i + 1 < args.length;
                boolean nextLooksLikeValue = hasNext && !args[i + 1].startsWith("--");

                if (isBool) {
                    if (nextLooksLikeValue) {
                        // Explicit value ("--flag true" or "--flag false").
                        values.put(paramName, args[++i]);
                    } else {
                        // Bare flag -- presence means true.
                        values.put(paramName, "true");
                    }
                } else {
                    if (!hasNext || args[i + 1].startsWith("--")) {
                        throw new CommandParseException(
                            String.format("Parameter --%s requires a value", paramName));
                    }
                    values.put(paramName, args[++i]);
                }
            }
        }
    }
    
    private void parsePositionalArguments(String[] args, Map<String, String> values) 
            throws CommandParseException {
        // Support variable argument lists for the last parameter
        int requiredParamCount = (int) parameters.stream()
            .filter(p -> p.isRequired() && !p.isVariableLength())
            .count();
        
        if (args.length < requiredParamCount) {
            throw new CommandParseException(
                String.format("Expected at least %d arguments, got %d\nUsage: %s",
                    requiredParamCount, args.length, generatePositionalUsage()));
        }
        
        int argIndex = 0;
        for (Parameter<?> param : parameters) {
            if (param.isVariableLength()) {
                // Collect remaining arguments
                List<String> varArgs = new ArrayList<>();
                while (argIndex < args.length) {
                    varArgs.add(args[argIndex++]);
                }
                values.put(param.getName(), String.join(" ", varArgs));
            } else if (argIndex < args.length) {
                values.put(param.getName(), args[argIndex++]);
            }
        }
    }
    
    private void validateRequiredParameters(Map<String, String> values) 
            throws CommandParseException {
        List<String> missing = parameters.stream()
            .filter(Parameter::isRequired)
            .filter(p -> !values.containsKey(p.getName()))
            .map(Parameter::getName)
            .collect(Collectors.toList());
        
        if (!missing.isEmpty()) {
            throw new CommandParseException(
                "Missing required parameters: " + String.join(", ", missing));
        }
    }
    
    private void applyDefaults(Map<String, String> values) {
        for (Parameter<?> param : parameters) {
            if (!values.containsKey(param.getName()) && param.getDefaultValue() != null) {
                values.put(param.getName(), param.getDefaultValue());
            }
        }
    }
    
    private void validateParameterValues(Map<String, String> values) 
            throws CommandParseException {
        for (Map.Entry<String, String> entry : values.entrySet()) {
            Parameter<?> param = parametersByName.get(entry.getKey());
            if (param != null && !param.isValidValue(entry.getValue())) {
                throw new CommandParseException(
                    String.format("Invalid value for %s: '%s'\n%s",
                        param.getName(), entry.getValue(), param.getValidationHelp()));
            }
        }
    }
    
    private String generatePositionalUsage() {
        return name + " " + parameters.stream()
            .map(p -> p.isRequired() ? "<" + p.getName() + ">" : "[" + p.getName() + "]")
            .collect(Collectors.joining(" "));
    }
    
    private String generateNamedUsage() {
        return name + " " + parameters.stream()
            .map(p -> {
                String param = "--" + p.getName() + " <value>";
                return p.isRequired() ? param : "[" + param + "]";
            })
            .collect(Collectors.joining(" "));
    }
    
    public static Builder builder(String name) {
        return new Builder(name);
    }

    /**
     * Nameless schema builder for class-registered commands. The canonical
     * command name is derived from the owning Command class and stamped on at
     * registration via {@link #assignName(String)} -- so the name is never
     * hardcoded here. Legacy commands registered through {@code CommandRegistry}
     * still use {@link #builder(String)} until migrated.
     */
    public static Builder builder() {
        return new Builder(null);
    }
    
    public static class Builder {
        private final String name;
        private String description = "";
        private final List<Parameter<?>> parameters = new ArrayList<>();
        private final List<String> examples = new ArrayList<>();
        private boolean allowsNamedParameters = true;
        private boolean llmEnabled = false;
        private String llmDescription;

        public Builder(String name) {
            this.name = name;
        }
        
        public Builder description(String description) {
            this.description = description;
            return this;
        }
        
        public Builder parameter(Parameter<?> parameter) {
            this.parameters.add(parameter);
            return this;
        }
        
        public Builder example(String example) {
            this.examples.add(example);
            return this;
        }
        
        public Builder allowsNamedParameters(boolean allow) {
            this.allowsNamedParameters = allow;
            return this;
        }

        /**
         * Mark this command as available to the LLM agent.
         */
        public Builder llmEnabled(boolean enabled) {
            this.llmEnabled = enabled;
            return this;
        }

        /**
         * Set an LLM-specific description (more detailed than the console help).
         */
        public Builder llmDescription(String desc) {
            this.llmDescription = desc;
            return this;
        }

        public CommandSchema build() {
            return new CommandSchema(this);
        }
    }
}