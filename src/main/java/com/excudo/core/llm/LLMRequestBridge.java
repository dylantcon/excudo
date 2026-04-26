package com.excudo.core.llm;

import com.excudo.core.commands.RequestSchema;
import com.excudo.core.parsing.CommandRegistry;
import com.excudo.core.parsing.CommandSchema;
import com.excudo.core.parsing.CommandParameters;

import java.util.*;

/**
 * Adapts LLM {@link RequestSchema.ActionRequest}s to {@link CommandParameters}s
 * using {@link CommandSchema} as the single source of truth.
 *
 * <p>Static utility class (formerly an instance type with a fake instance
 * cache). All state — the per-command llmName → canonical map — is now a
 * lazy static built once from {@link CommandRegistry}, since the registry
 * itself is static. The 4 callers that used to {@code new LLMRequestBridge()}
 * now just call the static methods directly; no allocation, no map rebuilds.
 *
 * <p>Two things still live here, both irreducible:
 * <ul>
 *   <li>Per-command parameter name mapping ({@code llmName} → canonical).</li>
 *   <li>Type coercion (the JSON-side Number/Boolean values are stringified
 *       to fit {@link CommandParameters}'s string-only payload).</li>
 * </ul>
 *
 * <p>Everything else got deleted in the 2026-04-24 seam-collapse pass —
 * the {@code llmAlias} registry, hardcoded {@code clickTrigger}/{@code animationType}
 * renames, 200 lines of nested-object flattening rules. None of it was
 * earning its keep.
 */
public final class LLMRequestBridge {

    private LLMRequestBridge() {}

    // Per-command: LLM param name -> canonical param name. Built lazily on
    // first use from each schema's parameter list. CommandRegistry is static
    // and immutable post-init, so this map is too -- there's no reason for
    // it to be per-instance state.
    private static final java.util.concurrent.atomic.AtomicReference<Map<String, Map<String, String>>>
        PARAM_MAPPINGS_CACHE = new java.util.concurrent.atomic.AtomicReference<>();

    /**
     * Convert an LLM ActionRequest into a CommandParameters.
     *
     * @param actionRequest the LLM action request
     * @return CommandParameters ready for the command factory
     * @throws IllegalArgumentException if the action type is unknown
     */
    public static CommandParameters bridge(RequestSchema.ActionRequest actionRequest) {
        String actionType = actionRequest.getType();
        Map<String, Object> params = actionRequest.getParameters();
        if (params == null) params = Collections.emptyMap();

        String commandName = resolveCommandName(actionType);

        Map<String, String> paramMapping = paramMappings().getOrDefault(
            commandName, Collections.emptyMap());
        Map<String, String> canonicalParams = new HashMap<>();
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            Object value = entry.getValue();
            if (value == null) continue;
            String canonicalKey = paramMapping.getOrDefault(entry.getKey(), entry.getKey());
            canonicalParams.put(canonicalKey, String.valueOf(value));
        }
        return new CommandParameters(commandName, canonicalParams);
    }

    /** Convert an entire LLM request into a list of CommandParameters. */
    public static List<CommandParameters> bridgeAll(RequestSchema.LLMRequest request) {
        if (request == null || request.getActions() == null) {
            return Collections.emptyList();
        }
        List<CommandParameters> commands = new ArrayList<>();
        for (RequestSchema.ActionRequest action : request.getActions()) {
            commands.add(bridge(action));
        }
        return commands;
    }

    /**
     * Resolve an action type to its canonical command name. Direct lookup
     * only — there is no longer any legacy-alias machinery.
     */
    public static String resolveCommandName(String actionType) {
        if (CommandRegistry.getSchema(actionType) != null) {
            return actionType;
        }
        throw new IllegalArgumentException(
            "Unknown LLM action type: '" + actionType + "'. " +
            "Known commands: " + String.join(", ", getLLMEnabledCommandNames()));
    }

    /** Check if an action type matches a registered LLM-enabled command. */
    public static boolean isRecognizedActionType(String actionType) {
        return CommandRegistry.getSchema(actionType) != null;
    }

    /** All LLM-enabled command names, sorted (for system-prompt + error msgs). */
    public static List<String> getLLMEnabledCommandNames() {
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

    /** Lazy-init the per-command param mapping cache. Atomic-CAS ensures
     *  the map is built at most once across threads. */
    private static Map<String, Map<String, String>> paramMappings() {
        Map<String, Map<String, String>> cached = PARAM_MAPPINGS_CACHE.get();
        if (cached != null) return cached;
        Map<String, Map<String, String>> built = new HashMap<>();
        for (CommandSchema schema : CommandRegistry.getAllSchemas().values()) {
            if (schema.isLlmEnabled()) {
                built.put(schema.getName(), schema.buildLlmToCanonicalParamMap());
            }
        }
        // CAS-or-discard: if another thread won, use theirs.
        PARAM_MAPPINGS_CACHE.compareAndSet(null, built);
        return PARAM_MAPPINGS_CACHE.get();
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
