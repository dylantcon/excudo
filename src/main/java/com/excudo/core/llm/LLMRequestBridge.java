package com.excudo.core.llm;

import com.excudo.core.commands.RequestSchema;
import com.excudo.core.parsing.CommandRegistry;
import com.excudo.core.parsing.CommandSchema;
import com.excudo.core.parsing.ParsedCommand;

import java.util.*;

/**
 * Adapts LLM {@link RequestSchema.ActionRequest}s to {@link ParsedCommand}s
 * using {@link CommandSchema} as the single source of truth.
 *
 * <p>Handles only what the schema can't express implicitly:
 * <ul>
 *   <li>Per-command parameter name mapping ({@code llmName} → canonical),
 *       built once from each schema's parameter list.</li>
 *   <li>Type coercion (the JSON-side Number/Boolean values are stringified
 *       to fit {@link ParsedCommand}'s string-only payload).</li>
 * </ul>
 *
 * <p>Everything else used to live here — a parallel {@code llmAlias}
 * registry that mapped legacy action-type names ({@code animation-edit},
 * {@code shape-addition}, ...) to canonical commands, hardcoded
 * {@code clickTrigger}/{@code animationType} renames, and 200 lines of
 * nested-object flattening rules for old LLM payload shapes. None of it
 * was earning its keep: the alias names duplicated the canonical names
 * the schema already declared, and the flatten rules existed only because
 * the legacy aliases used different parameter shapes. Deleted in the
 * 2026-04-24 seam-collapse pass.
 */
public class LLMRequestBridge {

    // Per-command: LLM param name -> canonical param name. Built once from
    // each schema's parameter list at construction time.
    private final Map<String, Map<String, String>> commandParamMappings;

    public LLMRequestBridge() {
        this.commandParamMappings = buildParamMappings();
    }

    /**
     * Convert an LLM ActionRequest into a ParsedCommand.
     *
     * @param actionRequest the LLM action request
     * @return ParsedCommand ready for the command factory
     * @throws IllegalArgumentException if the action type is unknown
     */
    public ParsedCommand bridge(RequestSchema.ActionRequest actionRequest) {
        String actionType = actionRequest.getType();
        Map<String, Object> params = actionRequest.getParameters();
        if (params == null) params = Collections.emptyMap();

        String commandName = resolveCommandName(actionType);

        Map<String, String> paramMapping = commandParamMappings.getOrDefault(
            commandName, Collections.emptyMap());
        Map<String, String> canonicalParams = new HashMap<>();
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            Object value = entry.getValue();
            if (value == null) continue;
            String canonicalKey = paramMapping.getOrDefault(entry.getKey(), entry.getKey());
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
     * Resolve an action type to its canonical command name. Direct lookup
     * only — there is no longer any legacy-alias machinery.
     */
    public String resolveCommandName(String actionType) {
        if (CommandRegistry.getSchema(actionType) != null) {
            return actionType;
        }
        throw new IllegalArgumentException(
            "Unknown LLM action type: '" + actionType + "'. " +
            "Known commands: " + String.join(", ", getLLMEnabledCommandNames()));
    }

    /** Check if an action type matches a registered LLM-enabled command. */
    public boolean isRecognizedActionType(String actionType) {
        return CommandRegistry.getSchema(actionType) != null;
    }

    /** All LLM-enabled command names, sorted (for system-prompt + error msgs). */
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
