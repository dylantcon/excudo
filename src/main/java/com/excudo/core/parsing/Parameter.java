package com.excudo.core.parsing;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Defines a command parameter with validation, type information, documentation,
 * and typed self-parsing extraction.
 *
 * <p>A {@code Parameter<T>} is the single source of truth for one command
 * parameter: its canonical name, wire type ({@link ParameterType}, used for
 * JSON-schema generation and parse-time validation), default value, LLM alias,
 * and -- via the embedded {@code parser} -- how to turn the raw string value
 * into a typed Java value. Self-describing Commands declare each parameter once
 * as a {@code static final Parameter<T>} constant and reference that same
 * object from both their {@code SCHEMA} and their {@code fromParameters} body
 * (through {@link CommandParameters#get(Parameter)} /
 * {@link CommandParameters#opt(Parameter)}), so the parameter key exists exactly
 * once with no string-literal drift between declaration and extraction.
 *
 * <p>Typed construction uses the {@code ofInt} / {@code ofDouble} /
 * {@code ofString} / {@code ofEnum} factories. The legacy {@link #builder(String)}
 * entrypoint remains for string-keyed schema declarations that read values back
 * through the legacy {@link CommandParameters} getters.
 */
public class Parameter<T> {
    private final String name;
    private final String description;
    private final ParameterType type;
    private final boolean required;
    private final String defaultValue;
    private final Set<String> validValues;
    private final Predicate<String> validator;
    private final boolean variableLength;
    private final String llmName;
    private final Function<String, T> parser;

    private Parameter(Builder<T> builder) {
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
        this.parser = builder.parser;
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

    /**
     * Parse a raw string value into this parameter's typed value. Wraps any
     * parser failure with the parameter name so the error names the offending
     * key. Used by {@link CommandParameters#get(Parameter)} /
     * {@link CommandParameters#opt(Parameter)}.
     */
    public T parse(String raw) {
        try {
            return parser.apply(raw);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException(
                "Parameter '" + name + "' could not be parsed from '" + raw + "': "
                    + e.getMessage(), e);
        }
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

    // ========== FACTORIES ==========

    /**
     * Legacy string-keyed builder. Produces a {@code Parameter<String>} with an
     * identity parser. Retained for schema declarations whose values are read
     * back through the legacy {@link CommandParameters} string getters.
     */
    public static Builder<String> builder(String name) {
        return new Builder<>(name, Function.identity());
    }

    /** Typed string parameter (identity parser). */
    public static Builder<String> ofString(String name) {
        return new Builder<>(name, Function.identity());
    }

    /**
     * Typed string parameter with a normalizing parser. The normalizer runs at
     * extraction time, folding value canonicalization (e.g. alignment token
     * normalization) into the parameter definition itself.
     */
    public static Builder<String> ofString(String name, Function<String, String> normalizer) {
        return new Builder<>(name, normalizer);
    }

    /** Typed integer parameter ({@link ParameterType#INTEGER}). */
    public static Builder<Integer> ofInt(String name) {
        return new Builder<>(name, Integer::parseInt).type(ParameterType.INTEGER);
    }

    /** Typed double parameter ({@link ParameterType#DOUBLE}). */
    public static Builder<Double> ofDouble(String name) {
        return new Builder<>(name, Double::parseDouble).type(ParameterType.DOUBLE);
    }

    /** Typed long parameter (wire type {@link ParameterType#INTEGER}). */
    public static Builder<Long> ofLong(String name) {
        return new Builder<>(name, Long::parseLong).type(ParameterType.INTEGER);
    }

    /** Typed boolean parameter ({@link ParameterType#BOOLEAN}). */
    public static Builder<Boolean> ofBool(String name) {
        return new Builder<>(name, Boolean::parseBoolean).type(ParameterType.BOOLEAN);
    }

    /**
     * Typed enum parameter; the parser is {@code Enum.valueOf(enumType, raw)}.
     * The schema's {@code validValues} are auto-populated from the enum
     * constants (for LLM JSON-schema generation and parse-time validation)
     * unless set explicitly -- so the accepted value set is never declared
     * twice. Use {@link #of(String, Function)} instead for enums with a custom
     * parse (e.g. {@code TransitionType.parseType}) whose accepted tokens
     * differ from the constant names.
     */
    public static <E extends Enum<E>> Builder<E> ofEnum(String name, Class<E> enumType) {
        Builder<E> b = new Builder<>(name, s -> Enum.valueOf(enumType, s));
        b.enumSource = enumType;
        return b;
    }

    /**
     * Generic typed parameter: fold any existing parse helper into the key.
     * The workhorse for unit→EMU, JSON blobs, comma-separated lists, and
     * custom enum parses -- e.g. {@code Parameter.of("type", TransitionType::parseType)}
     * or {@code Parameter.of("json", TextBodyJsonParser::parse)}.
     */
    public static <T> Builder<T> of(String name, Function<String, T> parser) {
        return new Builder<>(name, parser);
    }

    /**
     * Unit-string coordinate parameter parsed to EMU via
     * {@link com.excudo.core.geometry.UnitParser#parseToEmu(String)} -- accepts
     * "100pt", "1.5in", "914400emu", or a bare number. Wire type stays STRING:
     * the raw value is a unit-bearing string, not a plain number, so parse-time
     * validation (which runs before the parser) must not reject "100pt".
     */
    public static Builder<Long> ofUnit(String name) {
        return new Builder<>(name, com.excudo.core.geometry.UnitParser::parseToEmu);
    }

    /**
     * Comma-separated SPID list parsed to a {@code List<Integer>} (e.g. the
     * group / arrange / copy-style targets). Blank tokens are skipped; each
     * remaining token must be an integer.
     */
    public static Builder<List<Integer>> ofSpidList(String name) {
        return new Builder<>(name, raw -> {
            List<Integer> ids = new ArrayList<>();
            for (String part : raw.split(",")) {
                String t = part.trim();
                if (!t.isEmpty()) ids.add(Integer.parseInt(t));
            }
            return ids;
        });
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

    public static class Builder<T> {
        private final String name;
        private final Function<String, T> parser;
        private String description = "";
        private ParameterType type = ParameterType.STRING;
        private boolean required = true;
        private String defaultValue;
        private Set<String> validValues;
        private Predicate<String> validator;
        private boolean variableLength = false;
        private String llmName;
        private Class<? extends Enum<?>> enumSource;

        /** Legacy constructor: string-keyed parameter with identity parser. */
        @SuppressWarnings("unchecked")
        public Builder(String name) {
            this(name, (Function<String, T>) Function.<String>identity());
        }

        private Builder(String name, Function<String, T> parser) {
            this.name = name;
            this.parser = parser;
        }

        public Builder<T> description(String description) {
            this.description = description;
            return this;
        }

        public Builder<T> type(ParameterType type) {
            this.type = type;
            return this;
        }

        public Builder<T> required(boolean required) {
            this.required = required;
            return this;
        }

        /** Mark this parameter as required (fluent no-arg form). */
        public Builder<T> required() {
            this.required = true;
            return this;
        }

        /** Convenience: wire type {@link ParameterType#SLIDE_NUMBER}. */
        public Builder<T> slideNumber() {
            this.type = ParameterType.SLIDE_NUMBER;
            return this;
        }

        /** Convenience: wire type {@link ParameterType#SPID}. */
        public Builder<T> spid() {
            this.type = ParameterType.SPID;
            return this;
        }

        public Builder<T> defaultValue(String defaultValue) {
            this.defaultValue = defaultValue;
            this.required = false; // Having a default makes it optional
            return this;
        }

        /** Alias for {@link #defaultValue(String)} reading more naturally on typed keys. */
        public Builder<T> def(String defaultValue) {
            return defaultValue(defaultValue);
        }

        public Builder<T> validValues(String... values) {
            this.validValues = new HashSet<>(Arrays.asList(values));
            return this;
        }

        public Builder<T> validator(Predicate<String> validator) {
            this.validator = validator;
            return this;
        }

        public Builder<T> variableLength(boolean variableLength) {
            this.variableLength = variableLength;
            return this;
        }

        /**
         * Set the LLM parameter name alias. Use when the LLM uses a different
         * name for this parameter (e.g. "slideNumber" instead of "slide").
         */
        public Builder<T> llmName(String llmName) {
            this.llmName = llmName;
            return this;
        }

        public Parameter<T> build() {
            if (validValues == null && enumSource != null) {
                Set<String> vs = new LinkedHashSet<>();
                for (Object c : enumSource.getEnumConstants()) {
                    vs.add(((Enum<?>) c).name());
                }
                this.validValues = vs;
            }
            return new Parameter<>(this);
        }
    }
}
