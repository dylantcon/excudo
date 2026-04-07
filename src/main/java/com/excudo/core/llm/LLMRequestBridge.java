package com.excudo.core.llm;

import com.excudo.core.commands.RequestSchema;
import com.excudo.core.parsing.CommandRegistry;
import com.excudo.core.parsing.CommandSchema;
import com.excudo.core.parsing.ParsedCommand;

import java.util.*;

/**
 * Bridges LLM ActionRequests to ParsedCommands using CommandSchema as the single source of truth.
 *
 * This class eliminates the dual dispatch pattern (createFromParsedCommand vs createFromActionRequest)
 * by converting LLM requests into the same ParsedCommand format used by the console.
 *
 * Handles:
 * - Action type name mapping (old LLM names -> canonical console names)
 * - Parameter name mapping (e.g. "slideNumber" -> "slide", "targetSpid" -> "spid")
 * - Nested object flattening (e.g. animationData.animationType -> type)
 * - Type coercion (Number -> String for ParsedCommand compatibility)
 */
public class LLMRequestBridge {

    // Legacy action type -> canonical command name mappings
    // Built dynamically from CommandRegistry's llmAlias fields
    private final Map<String, String> actionTypeToCommandName;

    // Per-command: LLM param name -> canonical param name
    // Built dynamically from CommandSchema's parameter llmName fields
    private final Map<String, Map<String, String>> commandParamMappings;

    // Nested object flattening rules: actionType -> { nestedKey -> list of sub-params to extract }
    private final Map<String, Map<String, List<String>>> flatteningRules;

    public LLMRequestBridge() {
        this.actionTypeToCommandName = buildActionTypeMap();
        this.commandParamMappings = buildParamMappings();
        this.flatteningRules = buildFlatteningRules();
    }

    /**
     * Convert an LLM ActionRequest into a ParsedCommand.
     *
     * @param actionRequest the LLM action request
     * @return ParsedCommand ready for createFromParsedCommand()
     * @throws IllegalArgumentException if the action type is unknown
     */
    public ParsedCommand bridge(RequestSchema.ActionRequest actionRequest) {
        String actionType = actionRequest.getType();
        Map<String, Object> params = actionRequest.getParameters();
        if (params == null) params = Collections.emptyMap();

        // Resolve canonical command name
        String commandName = resolveCommandName(actionType);

        // Flatten nested objects first
        Map<String, Object> flatParams = flattenParams(actionType, params);

        // Map LLM parameter names to canonical names
        Map<String, String> paramMapping = commandParamMappings.getOrDefault(commandName, Collections.emptyMap());
        Map<String, String> canonicalParams = new HashMap<>();

        for (Map.Entry<String, Object> entry : flatParams.entrySet()) {
            String llmKey = entry.getKey();
            Object value = entry.getValue();
            if (value == null) continue;

            // Find canonical parameter name
            String canonicalKey = paramMapping.getOrDefault(llmKey, llmKey);
            canonicalParams.put(canonicalKey, String.valueOf(value));
        }

        return new ParsedCommand(commandName, canonicalParams);
    }

    /**
     * Convert an entire LLM request into a list of ParsedCommands.
     */
    public List<ParsedCommand> bridgeAll(RequestSchema.LLMRequest request) {
        if (request == null || request.getActions() == null) {
            return Collections.emptyList();
        }
        List<ParsedCommand> commands = new ArrayList<>();
        for (RequestSchema.ActionRequest action : request.getActions()) {
            commands.add(bridge(action));
        }
        return commands;
    }

    /**
     * Resolve a (possibly legacy) action type to its canonical command name.
     */
    public String resolveCommandName(String actionType) {
        // Direct match: action type IS a registered command name
        if (CommandRegistry.getSchema(actionType) != null) {
            return actionType;
        }
        // Legacy alias match
        String mapped = actionTypeToCommandName.get(actionType);
        if (mapped != null) {
            return mapped;
        }
        throw new IllegalArgumentException(
            "Unknown LLM action type: '" + actionType + "'. " +
            "Known commands: " + String.join(", ", getLLMEnabledCommandNames()));
    }

    /**
     * Check if a given action type (or its alias) is recognized.
     */
    public boolean isRecognizedActionType(String actionType) {
        return CommandRegistry.getSchema(actionType) != null
            || actionTypeToCommandName.containsKey(actionType);
    }

    /**
     * Get all LLM-enabled command names (for system prompt generation).
     */
    public List<String> getLLMEnabledCommandNames() {
        List<String> names = new ArrayList<>();
        for (CommandSchema schema : CommandRegistry.getAllSchemas().values()) {
            if (schema.isLlmEnabled()) {
                names.add(schema.getName());
            }
        }
        Collections.sort(names);
        return names;
    }

    // ========== PRIVATE HELPERS ==========

    /**
     * Build action type -> command name mapping from CommandRegistry.
     */
    private static Map<String, String> buildActionTypeMap() {
        Map<String, String> map = new HashMap<>();
        for (CommandSchema schema : CommandRegistry.getAllSchemas().values()) {
            if (schema.getLlmAlias() != null) {
                map.put(schema.getLlmAlias(), schema.getName());
            }
        }
        return map;
    }

    /**
     * Build per-command parameter name mappings from CommandSchema.
     */
    private static Map<String, Map<String, String>> buildParamMappings() {
        Map<String, Map<String, String>> result = new HashMap<>();
        for (CommandSchema schema : CommandRegistry.getAllSchemas().values()) {
            if (schema.isLlmEnabled()) {
                Map<String, String> paramMap = schema.buildLlmToCanonicalParamMap();
                result.put(schema.getName(), paramMap);
            }
        }
        return result;
    }

    /**
     * Define rules for flattening nested objects in legacy LLM requests.
     * Key = legacy action type, value = map of nested object key -> sub-param names.
     */
    private static Map<String, Map<String, List<String>>> buildFlatteningRules() {
        Map<String, Map<String, List<String>>> rules = new HashMap<>();

        // animation-edit: flatten animationData -> top-level params
        Map<String, List<String>> animRules = new HashMap<>();
        animRules.put("animationData", List.of(
            "animationType", "direction", "clickTrigger", "trigger",
            "animationGroup", "duration", "path", "accel", "decel",
            "rotationDegrees", "scalePercent", "color", "opacity", "intensity"
        ));
        rules.put("animation-edit", animRules);

        // shape-addition: flatten shapeData and shapeData.geometry -> top-level
        Map<String, List<String>> shapeRules = new HashMap<>();
        shapeRules.put("shapeData", List.of("name", "text"));
        shapeRules.put("shapeData.geometry", List.of("x", "y", "width", "height"));
        rules.put("shape-addition", shapeRules);

        // content-edit: flatten shapeData.geometry if present
        Map<String, List<String>> contentRules = new HashMap<>();
        contentRules.put("shapeData", List.of("name", "text"));
        contentRules.put("shapeData.geometry", List.of("x", "y", "width", "height"));
        rules.put("content-edit", contentRules);

        // enhanced-content: flatten geometry
        Map<String, List<String>> enhanceRules = new HashMap<>();
        enhanceRules.put("geometry", List.of("x", "y", "width", "height"));
        rules.put("enhanced-content", enhanceRules);

        // bullet-point-edit: flatten animationData
        Map<String, List<String>> bulletRules = new HashMap<>();
        bulletRules.put("animationData", List.of("animationType", "transition", "clickTrigger", "duration"));
        rules.put("bullet-point-edit", bulletRules);

        return rules;
    }

    /**
     * Flatten nested objects in the parameter map according to flattening rules.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> flattenParams(String actionType, Map<String, Object> params) {
        Map<String, Object> result = new LinkedHashMap<>(params);
        Map<String, List<String>> rules = flatteningRules.get(actionType);
        if (rules == null) return result;

        for (Map.Entry<String, List<String>> rule : rules.entrySet()) {
            String path = rule.getKey();
            List<String> subKeys = rule.getValue();

            // Handle dotted paths (e.g. "shapeData.geometry")
            String[] parts = path.split("\\.");
            Object nested = result.get(parts[0]);
            for (int i = 1; i < parts.length && nested instanceof Map; i++) {
                nested = ((Map<String, Object>) nested).get(parts[i]);
            }

            if (nested instanceof Map) {
                Map<String, Object> nestedMap = (Map<String, Object>) nested;
                for (String subKey : subKeys) {
                    Object val = nestedMap.get(subKey);
                    if (val != null && !result.containsKey(subKey)) {
                        result.put(subKey, val);
                    }
                }
            }
        }

        // Remove the nested objects themselves (they've been flattened)
        for (String path : rules.keySet()) {
            String topKey = path.split("\\.")[0];
            result.remove(topKey);
        }

        // Handle clickTrigger -> trigger mapping (animation-specific normalization)
        if (result.containsKey("clickTrigger") && !result.containsKey("trigger")) {
            result.put("trigger", String.valueOf(result.get("clickTrigger")));
            result.remove("clickTrigger");
        }

        // Handle animationType -> type mapping
        if (result.containsKey("animationType") && !result.containsKey("type")) {
            result.put("type", result.get("animationType"));
            result.remove("animationType");
        }

        return result;
    }

    /**
     * Generate the full LLM tools schema from all LLM-enabled commands.
     * Output is a JSON array of tool definitions.
     */
    public static String generateLLMToolsSchema() {
        StringBuilder sb = new StringBuilder();
        sb.append("[\n");

        boolean first = true;
        List<CommandSchema> sorted = new ArrayList<>(CommandRegistry.getAllSchemas().values());
        sorted.sort(Comparator.comparing(CommandSchema::getName));

        for (CommandSchema schema : sorted) {
            if (!schema.isLlmEnabled()) continue;
            if (!first) sb.append(",\n");
            sb.append(schema.toLLMToolSchema());
            first = false;
        }

        sb.append("\n]");
        return sb.toString();
    }

    /**
     * Generate a human-readable command reference for the LLM system prompt.
     * Lists each LLM-enabled command with its parameters and description.
     */
    public static String generateLLMCommandReference() {
        StringBuilder sb = new StringBuilder();
        sb.append("COMMANDS:\n");

        List<CommandSchema> sorted = new ArrayList<>(CommandRegistry.getAllSchemas().values());
        sorted.sort(Comparator.comparing(CommandSchema::getName));

        for (CommandSchema schema : sorted) {
            if (!schema.isLlmEnabled()) continue;

            sb.append("- ").append(schema.getName()).append(": ");

            // Compact param list: name(type) with constraints inline
            boolean firstParam = true;
            for (com.excudo.core.parsing.Parameter p : schema.getParameters()) {
                if (!firstParam) sb.append(", ");
                firstParam = false;

                String llmName = p.getEffectiveLlmName();
                sb.append(llmName).append("(").append(mapTypeForPrompt(p.getType())).append(")");

                if (p.getValidValues() != null && !p.getValidValues().isEmpty()) {
                    List<String> vals = new ArrayList<>(p.getValidValues());
                    Collections.sort(vals);
                    sb.append("[").append(String.join("|", vals)).append("]");
                } else if (p.getDefaultValue() != null) {
                    sb.append("=").append(p.getDefaultValue());
                }
                if (!p.isRequired()) sb.append("?");
            }

            // Append LLM description if available (provides usage guidance)
            String llmDesc = schema.getLlmDescription();
            if (llmDesc != null && !llmDesc.isEmpty()) {
                sb.append(" -- ").append(llmDesc);
            }
            sb.append("\n");
        }

        return sb.toString();
    }

    private static String mapTypeForPrompt(com.excudo.core.parsing.Parameter.ParameterType type) {
        return switch (type) {
            case INTEGER, SLIDE_NUMBER, SPID -> "integer";
            case DOUBLE -> "number";
            case BOOLEAN -> "boolean";
            default -> "string";
        };
    }
}
